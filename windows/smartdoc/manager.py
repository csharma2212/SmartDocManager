"""Conservative local document inventory. No external services or implicit renames."""
import hashlib
import json
import re
from pathlib import Path
from datetime import datetime, timezone
from .core import SUPPORTED, MAX_BYTES


def inventory(root: Path, recursive=True):
    root = Path(root).expanduser().resolve()
    if not root.is_dir():
        raise ValueError('Folder not found')
    files = root.rglob('*') if recursive else root.iterdir()
    entries = []
    hashes = {}
    for path in sorted(files):
        if not path.is_file() or path.suffix.lower() not in SUPPORTED:
            continue
        stat = path.stat()
        if stat.st_size > MAX_BYTES:
            continue
        sha = hashlib.sha256()
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                sha.update(chunk)
        digest = sha.hexdigest()
        entries.append({'path':str(path),'name':path.name,'size':stat.st_size,
                        'modified_ns':stat.st_mtime_ns,'sha256':digest,
                        'duplicate_of':hashes.get(digest,'')})
        hashes.setdefault(digest,str(path))
    return entries


def safe_filename(category, original, max_length=110):
    """Category-based rename; never incorporates extracted personal data."""
    label = re.sub(r'[^A-Za-z0-9_-]+','_',category).strip('_') or 'Document'
    stem = re.sub(r'[^A-Za-z0-9_-]+','_',Path(original).stem).strip('_') or 'file'
    ext = Path(original).suffix.lower()
    return (label+'_'+stem)[:max_length] + ext


def rename_preview(entries, categories):
    proposals = []
    for entry in entries:
        category = categories.get(entry['path'])
        if not category or category == 'uncategorizable input' or entry['duplicate_of']:
            continue
        src = Path(entry['path'])
        # Already-meaningful filenames are not changed.
        if src.stem.lower().startswith(category.replace(' ','_').lower()):
            continue
        dest = src.with_name(safe_filename(category,src.name))
        proposals.append({'source':str(src),'target':str(dest),'conflict':dest.exists()})
    return proposals


def apply_renames(proposals):
    """Explicit opt-in only; never overwrite or rename a file modified since preview."""
    completed=[]
    for proposal in proposals:
        src=Path(proposal['source']);dst=Path(proposal['target'])
        if not src.is_file() or dst.exists() or src.resolve()==dst.resolve():
            continue
        # Reserve destination exclusively so preexisting files cannot be overwritten.
        try:
            with dst.open('xb') as out, src.open('rb') as inp:
                for chunk in iter(lambda: inp.read(1024*1024),b''):
                    out.write(chunk)
            if hashlib.sha256(src.read_bytes()).digest() != hashlib.sha256(dst.read_bytes()).digest():
                dst.unlink(missing_ok=True)
                continue
            src.unlink()
            completed.append({'source':str(src),'target':str(dst)})
        except Exception:
            dst.unlink(missing_ok=True) if dst.exists() and not proposal.get('conflict') else None
            raise
    return completed


def save_index(path, entries, classifications):
    path=Path(path)
    data={'created_utc':datetime.now(timezone.utc).isoformat(),'files':entries,'classifications':classifications}
    path.write_text(json.dumps(data,indent=2),encoding='utf-8')
    return path
