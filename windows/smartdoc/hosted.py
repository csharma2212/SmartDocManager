"""Windows local OCR + hosted text-only classification."""
import io, time
from dataclasses import asdict
from PIL import Image
import requests
from .core import Result, FALLBACK, render, validate

def classify_hosted(filename, data, endpoint, token, session=None, ocr=None):
    start=time.monotonic(); result=Result(Filename=filename,Provider='Hosted text AI')
    try:
        if not endpoint.startswith('https://') or not token.strip():
            raise ValueError('HTTPS backend URL and access token required')
        if ocr is None:
            try: import pytesseract
            except ImportError as exc: raise ValueError('Install pytesseract and Tesseract OCR for hosted mode') from exc
            ocr=lambda image: pytesseract.image_to_string(image)
        text=ocr(Image.open(io.BytesIO(render(data,filename))))
        if not text.strip():
            result.Status='UNCATEGORIZABLE';result.Specifics='No readable text extracted locally'
        else:
            client=session or requests
            response=client.post(endpoint.rstrip('/')+'/v1/classify',json={'text':text[:16000]},
                headers={'Authorization':'Bearer '+token.strip()},timeout=55)
            if response.status_code!=200:
                result.Status='API_ERROR';result.Specifics=f'Hosted classifier HTTP {response.status_code}'
            else:
                result.Category,result.Specifics=validate(response.json())
                result.Status='UNCATEGORIZABLE' if result.Category==FALLBACK else 'OK'
    except ValueError as exc:
        result.Status='INPUT_ERROR';result.Specifics=str(exc)
    except Exception:
        result.Status='PROCESSING_ERROR';result.Specifics='OCR or hosted inference failed'
    result.Seconds=round(time.monotonic()-start,2)
    return asdict(result)
