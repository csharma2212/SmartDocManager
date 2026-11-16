"""Persistent local document library; does not send files to the cloud on its own."""
import hashlib
import re
import sqlite3
from datetime import datetime, timedelta
from pathlib import Path
from .core import SUPPORTED, MAX_BYTES

SCHEMA='''CREATE TABLE IF NOT EXISTS files(path TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, size INTEGER NOT NULL, mtime_ns INTEGER NOT NULL, digest TEXT NOT NULL, category TEXT NOT NULL DEFAULT '', proposed TEXT NOT NULL DEFAULT '', override TEXT NOT NULL DEFAULT '', virtual_name TEXT NOT NULL DEFAULT '', is_duplicate INTEGER NOT NULL DEFAULT 0, is_hidden INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'pending', specifics TEXT NOT NULL DEFAULT '', provider TEXT NOT NULL DEFAULT '', metadata TEXT NOT NULL DEFAULT '', seen INTEGER NOT NULL DEFAULT 1);'''

def connect(root):
    # DB outside scanned folder so scanning doesn't accidentally index the database.
    db=Path.home()/'.smartdocmanager'/'library.sqlite3';db.parent.mkdir(parents=True,exist_ok=True)
    conn=sqlite3.connect(db);conn.row_factory=sqlite3.Row;conn.execute(SCHEMA)
    for col, col_def in [('virtual_name', "TEXT NOT NULL DEFAULT ''"), ('is_duplicate', "INTEGER NOT NULL DEFAULT 0"), ('is_hidden', "INTEGER NOT NULL DEFAULT 0"), ('metadata', "TEXT NOT NULL DEFAULT ''")]:
        try: conn.execute(f"ALTER TABLE files ADD COLUMN {col} {col_def}")
        except sqlite3.OperationalError: pass
    return conn

def digest_file(path):
    h=hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()

def doc_kind(suffix: str) -> str:
    s = (suffix or '').lower()
    if s == '.pdf':
        return 'PDF'
    elif s in ('.png', '.jpg', '.jpeg', '.webp'):
        return 'Image'
    elif s in ('.docx', '.doc'):
        return 'Word'
    elif s in ('.xlsx', '.xls'):
        return 'Excel'
    elif s in ('.pptx', '.ppt'):
        return 'PowerPoint'
    elif s == '.txt':
        return 'Text'
    return 'Document'

def scan(conn, root, retain=True):
    target = Path(root).expanduser().resolve()
    assert target.exists(), f'File or folder not found: {root}'

    if target.is_file():
        if target.suffix.lower() not in SUPPORTED:
            return []
        try:
            st = target.stat()
            if st.st_size > MAX_BYTES:
                return []
            p = str(target)
            old = conn.execute('SELECT * FROM files WHERE path=?', (p,)).fetchone()
            if old and old['size'] == st.st_size and old['mtime_ns'] == st.st_mtime_ns:
                with conn:
                    if not retain:
                        conn.execute("UPDATE files SET seen=1, status='pending', category='', proposed='', override='', specifics='', provider='' WHERE path=?", (p,))
                    else:
                        conn.execute('UPDATE files SET seen=1 WHERE path=?', (p,))
                return rows(conn, target)
            h = digest_file(target)
            with conn:
                if not retain:
                    conn.execute('''INSERT INTO files(path,name,kind,size,mtime_ns,digest,seen) VALUES(?,?,?,?,?,?,1)
                        ON CONFLICT(path) DO UPDATE SET name=excluded.name,kind=excluded.kind,size=excluded.size,mtime_ns=excluded.mtime_ns,digest=excluded.digest,seen=1,status='pending',category='',proposed='',override='',specifics='',provider='' ''',
                        (p, target.name, doc_kind(target.suffix), st.st_size, st.st_mtime_ns, h))
                else:
                    conn.execute('''INSERT INTO files(path,name,kind,size,mtime_ns,digest,seen) VALUES(?,?,?,?,?,?,1)
                        ON CONFLICT(path) DO UPDATE SET name=excluded.name,kind=excluded.kind,size=excluded.size,mtime_ns=excluded.mtime_ns,digest=excluded.digest,seen=1''',
                        (p, target.name, doc_kind(target.suffix), st.st_size, st.st_mtime_ns, h))
        except (OSError, PermissionError):
            return []
        return rows(conn, target)

    prefix = str(target) + __import__('os').sep
    with conn:
        if not retain:
            conn.execute('DELETE FROM files WHERE path=? OR path LIKE ?', (str(target), prefix + '%'))
        conn.execute('UPDATE files SET seen=0 WHERE path=? OR path LIKE ?', (str(target), prefix + '%'))
        for path in sorted(target.rglob('*')):
            if not path.is_file() or path.suffix.lower() not in SUPPORTED:
                continue
            try:
                st = path.stat()
                if st.st_size > MAX_BYTES:
                    continue
                p = str(path)
                old = conn.execute('SELECT * FROM files WHERE path=?', (p,)).fetchone()
                if old and old['size'] == st.st_size and old['mtime_ns'] == st.st_mtime_ns:
                    conn.execute('UPDATE files SET seen=1 WHERE path=?', (p,))
                    continue
                h = digest_file(path)
                if old and old['digest'] == h:
                    conn.execute('UPDATE files SET size=?,mtime_ns=?,seen=1 WHERE path=?', (st.st_size, st.st_mtime_ns, p))
                    continue
                conn.execute('''INSERT INTO files(path,name,kind,size,mtime_ns,digest,seen) VALUES(?,?,?,?,?,?,1)
                    ON CONFLICT(path) DO UPDATE SET name=excluded.name,kind=excluded.kind,size=excluded.size,mtime_ns=excluded.mtime_ns,digest=excluded.digest,seen=1,status='pending',category='',proposed='',override='',specifics='',provider='' ''',
                    (p, path.name, doc_kind(path.suffix), st.st_size, st.st_mtime_ns, h))
            except (OSError, PermissionError):
                continue
        conn.execute('DELETE FROM files WHERE seen=0 AND (path=? OR path LIKE ?)', (str(target), prefix + '%'))
    return rows(conn, target)

def rows(conn, root):
    target = Path(root).expanduser().resolve()
    if target.is_file():
        return [dict(r) for r in conn.execute('SELECT * FROM files WHERE path=?', (str(target),))]
    return [dict(r) for r in conn.execute('SELECT * FROM files WHERE path=? OR path LIKE ? ORDER BY name', (str(target), str(target) + __import__('os').sep + '%'))]

def update(conn,path,**fields):
    permitted={'status','category','proposed','override','specifics','provider','virtual_name','is_duplicate','is_hidden','metadata'}
    assert set(fields)<=permitted
    if not fields:return
    with conn:conn.execute('UPDATE files SET '+','.join(k+'=?' for k in fields)+' WHERE path=?',(*fields.values(),str(path)))

def short_name(s,ext):
    stem=re.sub(r'[^\w -]','',str(s),flags=re.UNICODE).strip(' ._-')[:90]
    return (stem or 'Document')+ext.lower()

def rename_and_trash(conn,root,confirm_duplicates=False,virtual_only=False):
    """Rename without overwrites; optionally move exact duplicates to OS recycle bin or handle virtually.
    If virtual_only=True, original files on disk are completely untouched, updating only metadata.
    """
    records=rows(conn,root);changed=[];trashed=[];seen={}
    for r in records:
        p=Path(r['path'])
        if not p.exists():continue
        # Do not allow any operations (renaming, deduplicating, trashing) on files that are not done
        if r['status']!='done':continue
        if r['digest'] in seen:
            if confirm_duplicates:
                if virtual_only:
                    with conn:conn.execute('UPDATE files SET is_duplicate=1,is_hidden=1 WHERE path=?',(str(p),))
                    trashed.append(str(p))
                else:
                    try:from send2trash import send2trash
                    except ImportError:raise RuntimeError('Install send2trash to safely recycle duplicates; no files were deleted')
                    send2trash(str(p));trashed.append(str(p))
                    with conn:conn.execute('DELETE FROM files WHERE path=?',(str(p),))
            continue
        seen[r['digest']]=str(p)
        if r['status']!='done':continue
        proposed=r['override'] or r['proposed']
        if not proposed or proposed=='N/A':continue
        if len(proposed) <= 40 and Path(proposed).suffix.lower() == p.suffix.lower():
            target_name = proposed
        else:
            target_name = short_name(Path(proposed).stem, p.suffix)
        if virtual_only:
            with conn:conn.execute('UPDATE files SET virtual_name=? WHERE path=?',(target_name,str(p)))
            changed.append((r['name'],target_name))
        else:
            target=p.with_name(target_name)
            if target==p:continue
            index=2
            while target.exists():
                target=p.with_name(short_name(Path(proposed).stem+f'_{index}',p.suffix));index+=1
            p.rename(target)
            st=target.stat()
            with conn:conn.execute('UPDATE files SET path=?,name=?,size=?,mtime_ns=? WHERE path=?',(str(target),target.name,st.st_size,st.st_mtime_ns,str(p)))
            changed.append((str(p),str(target)))
    return changed,trashed

def date_match(mtime_ns,preset,now=None):
    now=now or datetime.now();d=datetime.fromtimestamp(mtime_ns/1e9)
    limits={'Since Today':0,'Since Yesterday':1,'Since A Week':7,'Since A Month':30,'Since Last 3 Months':90,'Since Last 6 Months':183,'Since Last 1 Year':365}
    if preset=='Older than 1 Year':return d<now-timedelta(days=365)
    if preset=='All dates':return True
    if preset=='Since Today':return d.date()==now.date()
    return now-timedelta(days=limits[preset])<=d<=now
