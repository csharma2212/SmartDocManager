import sqlite3
from pathlib import Path

db = Path.home() / '.smartdocmanager' / 'library.sqlite3'
if db.exists():
    conn = sqlite3.connect(str(db))
    cur = conn.cursor()
    cur.execute("SELECT count(*) FROM files WHERE specifics LIKE '%smartdoc-classifier%' OR specifics LIKE '%Data format error%' OR specifics LIKE '%Service not configured%'")
    cnt = cur.fetchone()[0]
    print(f"Found {cnt} stale error records.")
    cur.execute("UPDATE files SET status='pending', specifics='' WHERE specifics LIKE '%smartdoc-classifier%' OR specifics LIKE '%Data format error%' OR specifics LIKE '%Service not configured%'")
    conn.commit()
    print("Reset completed successfully.")
else:
    print("Database does not exist.")
