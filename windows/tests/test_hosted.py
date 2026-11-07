import io,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from PIL import Image
from smartdoc.hosted import classify_hosted

def sample():
    b=io.BytesIO();Image.new('RGB',(60,40),'white').save(b,'PNG');return b.getvalue()

def test_hosted_success():
    class Resp:
        status_code=200
        def json(self):return {'category':'Tax Document','specifics':'Form 16 heading'}
    class Client:
        def post(self,url,**kwargs):
            assert url=='https://example.org/v1/classify'
            assert kwargs['json']['text']=='Form 16'
            return Resp()
    result=classify_hosted('doc.png',sample(),'https://example.org','token',session=Client(),ocr=lambda img:'Form 16')
    assert result['Status']=='OK' and result['Category']=='Tax Document'

def test_hosted_rejects_insecure_endpoint():
    result=classify_hosted('doc.png',sample(),'http://example.org','token',ocr=lambda img:'Form 16')
    assert result['Status']=='INPUT_ERROR'

def test_hosted_no_text():
    result=classify_hosted('doc.png',sample(),'https://example.org','token',ocr=lambda img:' ')
    assert result['Status']=='UNCATEGORIZABLE'
