from pathlib import Path
from smartdoc.manager import inventory,safe_filename,rename_preview,save_index

def test_inventory_duplicates(tmp_path):
    (tmp_path/'a.pdf').write_bytes(b'same')
    (tmp_path/'b.pdf').write_bytes(b'same')
    records=inventory(tmp_path)
    assert len(records)==2 and records[1]['duplicate_of']==str(tmp_path/'a.pdf')

def test_preview_does_not_modify(tmp_path):
    f=tmp_path/'untitled.pdf';f.write_bytes(b'data')
    entries=inventory(tmp_path)
    preview=rename_preview(entries,{str(f):'Tax Document'})
    assert len(preview)==1 and f.exists() and not Path(preview[0]['target']).exists()

def test_safe_filename():
    assert safe_filename('Bank Statement','../bad name.pdf')=='Bank_Statement_bad_name.pdf'

def test_index(tmp_path):
    target=save_index(tmp_path/'index.json',[],{})
    assert 'classifications' in target.read_text()
