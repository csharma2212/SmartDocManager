"""Conservative document-specific naming, never fabricate missing evidence."""
import re
from pathlib import Path

def proposed_name(category, specifics='', suggested='', original=''):
    ext=Path(original).suffix.lower()
    if category=='uncategorizable input': return 'N/A'
    if category=='Government-issued Document':
        evidence=(specifics+' '+suggested).lower()
        if re.search(r'\bpan\b|permanent account number',evidence): suggested='Pan'
        elif 'aadhaar' in evidence or 'aadhar' in evidence: suggested='Aadhaar'
        elif 'passport' in evidence: suggested='Passport'
    name=suggested.strip() or category.replace(' Document','').replace(' Statement','')
    name=name.rsplit(".",1)[0] if name.lower().endswith((".pdf",".jpg",".jpeg",".png",".webp",".docx",".xlsx",".doc",".xls",".pptx",".ppt",".txt")) else name
    name=re.sub(r'[\\/:*?"<>|\x00-\x1f]','',name)
    name=re.sub(r'\s+','_',name).strip(' ._-')[:85]
    return (name or 'Document')+ext
