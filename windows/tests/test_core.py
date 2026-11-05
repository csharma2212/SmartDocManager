import io,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from PIL import Image
from smartdoc.core import validate,render,classify,FALLBACK

def test_taxonomy():
    assert validate({'category':'Medical Record','specifics':'Visible prescription heading'})[0]=='Medical Record'
def test_reject_unknown():
    try: validate({'category':'banana','specifics':''})
    except ValueError: pass
    else: assert False

def test_image():
    b=io.BytesIO();Image.new('RGB',(120,60),'white').save(b,'PNG')
    assert render(b.getvalue(),'test.png')[:2]==b'\xff\xd8'

def test_mock_provider():
    b=io.BytesIO();Image.new('RGB',(120,60),'white').save(b,'PNG')
    class Response:
        status_code=200
        def json(self):return {'candidates':[{'content':{'parts':[{'text':'{"category":"uncategorizable input","specifics":"Blank image"}'}]}}]}
    class Session:
        def post(self,*a,**kw):return Response()
    result=classify('blank.png',b.getvalue(),'test-key','test-model',Session())
    assert result['Category']==FALLBACK and result['Status']=='UNCATEGORIZABLE'

def test_api_failure_distinct():
    b=io.BytesIO();Image.new('RGB',(120,60),'white').save(b,'PNG')
    class Response:status_code=429
    class Session:
        def post(self,*a,**kw):return Response()
    result=classify('blank.png',b.getvalue(),'test-key','test-model',Session())
    assert result['Category']==FALLBACK and result['Status']=='API_ERROR'
