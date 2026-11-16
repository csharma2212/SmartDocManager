import io
from PIL import Image
from smartdoc.hosted import extract_text

def test_image_ocr():
    image=Image.new('RGB',(30,30),'white');buf=io.BytesIO();image.save(buf,format='PNG')
    assert extract_text('sample.png',buf.getvalue(),lambda _: 'FORM 16')=='FORM 16'

def test_file_limit():
    import pytest
    with pytest.raises(ValueError):extract_text('big.png',b'0'*(20*1024*1024+1),lambda _: '')
