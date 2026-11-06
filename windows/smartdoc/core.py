import io, json, re, time
from dataclasses import dataclass, asdict
from PIL import Image, ImageOps
import pypdfium2 as pdfium

CATEGORIES = ('Medical Record', 'Government-issued Document', 'Organization-issued ID Card', 'Legal Document', 'uncategorizable input')
FALLBACK = CATEGORIES[-1]
MAX_BYTES = 20 * 1024 * 1024
SUPPORTED = ('.pdf','.png','.jpg','.jpeg','.webp')

@dataclass
class Result:
    Filename: str
    Category: str = FALLBACK
    Specifics: str = ''
    Status: str = 'UNPROCESSED'
    Provider: str = 'Gemini cloud'
    Seconds: float = 0.0

def render(data: bytes, filename: str) -> bytes:
    if len(data) > MAX_BYTES:
        raise ValueError('File exceeds 20 MB limit')
    if not filename.lower().endswith(SUPPORTED):
        raise ValueError('Unsupported file type')
    if filename.lower().endswith('.pdf'):
        pdf = pdfium.PdfDocument(data)
        try:
            if len(pdf) == 0: raise ValueError('Empty PDF')
            page = pdf[0]
            try:
                bitmap = page.render(scale=1.5)
                img = bitmap.to_pil()
            finally: page.close()
        finally: pdf.close()
    else:
        with Image.open(io.BytesIO(data)) as opened:
            img = ImageOps.exif_transpose(opened).copy()
    img = img.convert('RGB')
    img.thumbnail((1400, 1400))
    buf=io.BytesIO(); img.save(buf,format='JPEG',quality=78)
    return buf.getvalue()

def validate(raw):
    if not isinstance(raw,dict): raise ValueError('Response is not an object')
    category=raw.get('category')
    if category not in CATEGORIES: raise ValueError('Unknown category')
    evidence=raw.get('specifics','')
    if not isinstance(evidence,str) or len(evidence)>1200: raise ValueError('Invalid evidence')
    return category,evidence

def classify(filename,data,key,model,session=None):
    start=time.monotonic()
    result=Result(Filename=filename)
    try:
        image=render(data,filename)
        clean_key = str(key).strip()
        if not clean_key: raise ValueError('Gemini API key missing')
        clean_model = model.replace('models/', '').strip()
        if not clean_model: raise ValueError('Gemini model missing')
        import base64, requests
        client=session or requests
        prompt=('Classify the document image into exactly one category: '+', '.join(CATEGORIES)+'. '
                'If unclear, blank, unreadable or out of scope use uncategorizable input. '
                'Government-issued citizen credentials take precedence over organization badges; employer or student IDs are organization-issued. '
                'Do not invent text, identifiers, or evidence. Return ONLY JSON object with keys category and specifics. '
                'Specifics must be a short explanation based on visible evidence, without reproducing personal identifiers.')
        payload={'contents':[{'parts':[{'text':prompt},{'inline_data':{'mime_type':'image/jpeg','data':base64.b64encode(image).decode('ascii')}}]}],
                 'generationConfig':{'temperature':0,'responseMimeType':'application/json'}}
        url = 'https://generativelanguage.googleapis.com/v1beta/models/' + clean_model + ':generateContent?key=' + clean_key
        response = client.post(url, headers={'Content-Type': 'application/json', 'x-goog-api-key': clean_key}, json=payload, timeout=75)
        if response.status_code!=200:
            result.Status='API_ERROR'
            err_msg=''
            try:
                if hasattr(response, 'json'):
                    err_data = response.json()
                    if isinstance(err_data, dict) and 'error' in err_data:
                        err_msg = ': ' + err_data['error'].get('message', '')
                elif hasattr(response, 'text') and response.text:
                    err_msg = ': ' + str(response.text)[:200]
            except Exception:
                pass
            result.Specifics='Gemini request failed (HTTP '+str(response.status_code)+')'+err_msg
        else:
            try:
                resp_json = response.json()
                text = resp_json['candidates'][0]['content']['parts'][0]['text']
                result.Category,result.Specifics=validate(json.loads(text))
                result.Status='UNCATEGORIZABLE' if result.Category==FALLBACK else 'OK'
            except (ValueError,KeyError,TypeError,IndexError,json.JSONDecodeError) as parse_err:
                result.Status='INVALID_RESPONSE';result.Specifics=f'Provider returned an invalid classification ({parse_err}).'
    except ValueError as exc:
        result.Status='INPUT_ERROR';result.Specifics=str(exc)
    except Exception:
        result.Status='PROCESSING_ERROR';result.Specifics='Unable to process this document or contact Gemini.'
    result.Seconds=round(time.monotonic()-start,2)
    return asdict(result)
