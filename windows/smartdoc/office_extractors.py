"""Office Document and Plain Text Extractors.
Extracts clean plain text on-device from .docx, .xlsx, .pptx, .doc, .xls, .ppt, and .txt.
Zero heavy dependencies, 100% local, resilient fallback.
"""
import io
import re
import xml.etree.ElementTree as ET
import zipfile

OFFICE_EXTENSIONS = ('.docx', '.xlsx', '.doc', '.xls', '.pptx', '.ppt', '.txt')


def extract_txt(data: bytes) -> str:
    """Extract plain text from bytes with multi-encoding fallback."""
    for enc in ('utf-8', 'utf-16', 'latin-1', 'cp1252'):
        try:
            return data.decode(enc)
        except Exception:
            continue
    return data.decode('utf-8', errors='ignore')


def _strip_xml_tags(xml_str: str) -> str:
    """Fast regex-based XML text extractor that handles namespace tags cleanly."""
    # Find all text inside <...:t>...</...:t> or <t>...</t>
    t_matches = re.findall(r'<[^:>]*:?t\b[^>]*>(.*?)</[^:>]*:?t>', xml_str, flags=re.DOTALL)
    if t_matches:
        # Unescape basic XML entities
        result = ''.join(t_matches)
        result = result.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>').replace('&quot;', '"').replace('&apos;', "'")
        return result

    # Generic XML strip fallback
    clean = re.sub(r'<[^>]+>', ' ', xml_str)
    clean = re.sub(r'\s+', ' ', clean)
    return clean.strip()


def extract_docx(data: bytes) -> str:
    """Extract text from Word (.docx) OpenXML zip."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            if 'word/document.xml' in z.namelist():
                content = z.read('word/document.xml').decode('utf-8', errors='ignore')
                text = _strip_xml_tags(content)
                if text.strip():
                    return text.strip()
    except Exception:
        pass
    return extract_binary_strings(data)


def extract_pptx(data: bytes) -> str:
    """Extract text from PowerPoint (.pptx) OpenXML zip."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            slide_names = sorted([n for n in z.namelist() if n.startswith('ppt/slides/slide') and n.endswith('.xml')])
            slide_texts = []
            for s in slide_names:
                content = z.read(s).decode('utf-8', errors='ignore')
                t = _strip_xml_tags(content)
                if t:
                    slide_texts.append(t)
            if slide_texts:
                return '\n'.join(slide_texts).strip()
    except Exception:
        pass
    return extract_binary_strings(data)


def extract_xlsx(data: bytes) -> str:
    """Extract text from Excel (.xlsx) OpenXML zip."""
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as z:
            parts = []
            if 'xl/sharedStrings.xml' in z.namelist():
                content = z.read('xl/sharedStrings.xml').decode('utf-8', errors='ignore')
                parts.append(_strip_xml_tags(content))
            for n in z.namelist():
                if n.startswith('xl/worksheets/sheet') and n.endswith('.xml'):
                    sheet_content = z.read(n).decode('utf-8', errors='ignore')
                    # Extract cell values <v>...</v>
                    v_vals = re.findall(r'<v\b[^>]*>(.*?)</v>', sheet_content, flags=re.DOTALL)
                    if v_vals:
                        parts.append(' '.join(v_vals[:50]))
            result = ' '.join(p for p in parts if p.strip()).strip()
            if result:
                return result
    except Exception:
        pass
    return extract_binary_strings(data)


def extract_binary_strings(data: bytes, min_len: int = 4) -> str:
    """Extract readable ASCII and UTF-16LE text blocks from legacy binary formats (.doc, .xls, .ppt)."""
    # ASCII strings
    ascii_matches = re.findall(rb'[\x20-\x7E]{4,}', data)
    ascii_texts = [m.decode('ascii', errors='ignore') for m in ascii_matches]

    # UTF-16LE strings (common in Microsoft OLE2 compounds)
    utf16_matches = re.findall(rb'(?:[\x20-\x7E]\x00){4,}', data)
    utf16_texts = [m.decode('utf-16le', errors='ignore') for m in utf16_matches]

    # Filter out noise/binary signatures
    skip_prefixes = ('Microsoft', 'CompObj', 'WordDocument', 'SummaryInformation', 'DocumentSummaryInformation', 'Root Entry', 'Current User')
    cleaned = []
    for s in ascii_texts + utf16_texts:
        st = s.strip()
        if len(st) >= min_len and not any(st.startswith(sp) for sp in skip_prefixes):
            cleaned.append(st)
    return ' '.join(cleaned).strip()


def extract_office_text(filename: str, data: bytes) -> str:
    """Main entrypoint for office documents and text files."""
    fn = filename.lower()
    if fn.endswith('.txt'):
        return extract_txt(data)
    elif fn.endswith('.docx'):
        return extract_docx(data)
    elif fn.endswith('.pptx'):
        return extract_pptx(data)
    elif fn.endswith('.xlsx'):
        return extract_xlsx(data)
    elif fn.endswith(('.doc', '.xls', '.ppt')):
        return extract_binary_strings(data)
    return ''
