from pathlib import Path
from datetime import datetime, timedelta
from smartdoc.library import connect, scan, rows, update, short_name, date_match, rename_and_trash

def test_rescan_retain_and_fresh(tmp_path, monkeypatch):
    monkeypatch.setenv('HOME',str(tmp_path))
    monkeypatch.setenv('USERPROFILE',str(tmp_path))
    root=tmp_path/'docs';root.mkdir()
    f=root/'test.pdf';f.write_bytes(b'abc')
    conn=connect(root)
    scan(conn,root)
    update(conn,f,status='done',category='Tax Document',proposed='Form16.pdf',override='Mine.pdf')
    scan(conn,root,True)
    assert rows(conn,root)[0]['override']=='Mine.pdf'
    scan(conn,root,False)
    assert rows(conn,root)[0]['status']=='pending'
    f.unlink();scan(conn,root)
    assert not rows(conn,root)
    conn.close()

def test_safe_name_and_rename(tmp_path,monkeypatch):
    monkeypatch.setenv('HOME',str(tmp_path));monkeypatch.setenv('USERPROFILE',str(tmp_path));root=tmp_path/'docs';root.mkdir()
    f=root/'scan.pdf';f.write_bytes(b'abc')
    conn=connect(root);scan(conn,root);update(conn,f,status='done',proposed='Pan.pdf')
    changed,deleted=rename_and_trash(conn,root)
    assert len(changed)==1 and not deleted and (root/'Pan.pdf').exists()
    assert rows(conn,root)[0]['name']=='Pan.pdf'
    conn.close()

def test_dates():
    now=datetime(2026,11,8,12)
    assert date_match(int(now.timestamp()*1e9),'Since Today',now)
    assert not date_match(int((now-timedelta(days=400)).timestamp()*1e9),'Since Last 1 Year',now)
    assert short_name('Pan',' .pdf'.strip())=='Pan.pdf'

def test_single_file_scan(tmp_path, monkeypatch):
    monkeypatch.setenv('HOME', str(tmp_path))
    monkeypatch.setenv('USERPROFILE', str(tmp_path))
    single_file = tmp_path / 'invoice.pdf'
    single_file.write_bytes(b'%PDF-1.4 test invoice content')
    conn = connect(single_file)
    recs = scan(conn, single_file)
    assert len(recs) == 1
    assert recs[0]['name'] == 'invoice.pdf'
    assert recs[0]['kind'] == 'PDF'
    assert recs[0]['status'] == 'pending'
    update(conn, single_file, status='done', category='Invoice', proposed='2024_Invoice.pdf')
    assert rows(conn, single_file)[0]['category'] == 'Invoice'
    conn.close()

