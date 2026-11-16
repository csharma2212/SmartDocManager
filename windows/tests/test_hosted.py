import io, sys, base64
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from PIL import Image
import pypdfium2 as pdfium
from smartdoc.hosted import classify_hosted, extract_text, SYSTEM_PROMPT_TEMPLATE, USER_PROMPT_QUESTION

def sample():
    b = io.BytesIO()
    Image.new('RGB', (60, 40), 'white').save(b, 'PNG')
    return b.getvalue()

def sample_pdf():
    pdf = pdfium.PdfDocument.new()
    pdf.new_page(width=200, height=200)
    buf = io.BytesIO()
    pdf.save(buf)
    pdf.close()
    return buf.getvalue()

def test_hosted_success():
    class Resp:
        status_code = 200
        def json(self): return {'category': 'Tax Document', 'specifics': 'Form 16 heading'}
    class Client:
        def post(self, url, **kwargs):
            assert url == 'https://example.org/v1/classify'
            assert kwargs['json']['text'] == 'Form 16'
            return Resp()
    result = classify_hosted('doc.png', sample(), 'https://example.org', 'token', session=Client(), ocr=lambda img: 'Form 16')
    assert result['Status'] == 'OK' and result['Category'] == 'Tax Document'
    assert 'PromptSent' in result and 'system_instruction' in result['PromptSent']
    assert 'RawResponse' in result

def test_hosted_503_error_clean():
    class Resp503:
        status_code = 503
        def json(self): return {'error': 'Service temporarily overloaded'}
    class Client503:
        def post(self, url, **kwargs): return Resp503()
    result = classify_hosted('doc.png', sample(), 'https://example.org', 'token', session=Client503(), ocr=lambda img: 'Sample Document')
    assert result['Status'] == 'API_ERROR'
    assert 'missing GEMINI_API_KEY' not in result['Specifics']
    assert '503' in result['Specifics']

def test_hosted_rejects_insecure_endpoint():
    result = classify_hosted('doc.png', sample(), 'http://example.org', 'token', ocr=lambda img: 'Form 16')
    assert result['Status'] == 'INPUT_ERROR'

def test_hosted_no_text():
    result = classify_hosted('doc.png', sample(), 'https://example.org', 'token', ocr=lambda img: ' ')
    assert result['Status'] == 'UNCATEGORIZABLE'

def test_prompt_no_taxonomy_and_has_user_questions():
    # Verify no predefined taxonomy list is in prompt
    assert 'Medical Record' not in SYSTEM_PROMPT_TEMPLATE
    assert 'Aadhaar Card' not in SYSTEM_PROMPT_TEMPLATE
    # Verify user's exact 4 questions are present
    assert 'Which kind of document is it' in SYSTEM_PROMPT_TEMPLATE
    assert 'logical category for this document' in SYSTEM_PROMPT_TEMPLATE
    assert 'propose a short meaningful name for it' in SYSTEM_PROMPT_TEMPLATE
    assert 'why do you think it belongs to the category which you have suggested' in SYSTEM_PROMPT_TEMPLATE

def test_multimodal_file_payload_dispatched():
    captured = {}
    class MockResp:
        status_code = 200
        def json(self):
            return {
                'document_kind': 'Bank Statement',
                'category': 'Bank Statement',
                'proposed_name': 'SBI_Statement_2026-11-10',
                'specifics': 'Visible SBI transaction statement header'
            }
    class MockClient:
        def post(self, url, **kwargs):
            captured['url'] = url
            captured['json'] = kwargs.get('json', {})
            return MockResp()

    pdf_bytes = sample_pdf()
    result = classify_hosted('statement.pdf', pdf_bytes, 'https://example.org', 'token', session=MockClient())
    assert result['Status'] == 'OK'
    assert result['Category'] == 'Bank Statement'
    assert result['DocumentKind'] == 'Bank Statement'
    assert result['ProposedName'] == 'SBI_Statement_2026-11-10'
    assert captured['json']['mime_type'] == 'application/pdf'
    assert len(captured['json']['file_data']) > 0
    # Decoded file_data matches original pdf bytes
    assert base64.b64decode(captured['json']['file_data']) == pdf_bytes

def test_extract_text_safe_pdf_closing():
    pdf_bytes = sample_pdf()
    # Should execute cleanly without memory corruption or access violation
    text = extract_text('test.pdf', pdf_bytes)
    assert isinstance(text, str)

def test_client_rate_limiter_daily_limit():
    from smartdoc.hosted import _rate_limiter, DAILY_LIMIT_MESSAGE
    _rate_limiter.reset_for_testing()
    _rate_limiter.day_count = 1000
    res = classify_hosted('test.png', sample(), 'https://example.org', 'token', ocr=lambda img: 'Form 16')
    assert res['Status'] == 'QUOTA_EXHAUSTED'
    assert res['Specifics'] == DAILY_LIMIT_MESSAGE
    _rate_limiter.reset_for_testing()
