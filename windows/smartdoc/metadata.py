"""SmartDocManager Document Metadata Extraction & Search Engine.
Extracts rich searchable metadata (labs, tests, vendors, entities, dates, identifiers)
and provides intelligent typeahead suggestions based on document content.
"""
import re, json
from pathlib import Path
import pypdfium2 as pdfium
from .core import MAX_BYTES

# Well-known entity dictionaries for high-precision metadata tag discovery
LABS_AND_CLINICS = [
    ("Dr. Lal PathLabs", ["lalpath", "lal path", "dr lal", "lalpathlabs", "dr. lal pathlabs"]),
    ("SRL Diagnostics", ["srl", "srl diagnostics", "srl lab"]),
    ("Metropolis Healthcare", ["metropolis", "metropolis healthcare"]),
    ("Thyrocare", ["thyrocare"]),
    ("Apollo Diagnostics / Hospital", ["apollo", "apollo diagnostics", "apollo clinic", "apollo hospitals"]),
    ("Max Healthcare / Lab", ["max lab", "max healthcare", "max hospital"]),
    ("Medanta", ["medanta"]),
    ("Fortis Healthcare", ["fortis"]),
    ("Manipal Hospital", ["manipal"]),
    ("AIIMS", ["aiims"]),
    ("PathCare Labs", ["pathcare"]),
    ("Suburban Diagnostics", ["suburban diagnostics", "suburban"]),
    ("Quest Diagnostics", ["quest diagnostics"])
]

MEDICAL_TESTS = [
    ("LFT (Liver Function Test)", ["lft", "liver function", "bilirubin", "sgot", "sgpt", "alkaline phosphatase", "total protein", "albumin"]),
    ("CBC (Complete Blood Count)", ["cbc", "complete blood count", "hemoglobin", "wbc", "rbc", "platelet", "hematocrit", "leukocyte", "neutrophils", "lymphocytes"]),
    ("KFT / RFT (Kidney Function Test)", ["kft", "rft", "kidney function", "renal function", "creatinine", "urea", "blood urea nitrogen", "uric acid"]),
    ("Lipid Profile", ["lipid profile", "cholesterol", "triglycerides", "hdl", "ldl", "vldl"]),
    ("HbA1c (Diabetes / Glycated Hb)", ["hba1c", "glycated hemoglobin", "blood glucose", "fasting glucose", "post prandial", "sugar fasting"]),
    ("Thyroid Profile (T3, T4, TSH)", ["thyroid", "tsh", "total t3", "total t4", "free t3", "free t4"]),
    ("Urine Examination", ["urine routine", "urine examination", "pus cells", "epithelial cells"]),
    ("Vitamin Profile", ["vitamin d", "vitamin b12", "25-hydroxy"]),
    ("Radiology / Scan", ["x-ray", "mri", "ct scan", "ultrasound", "usg", "radiology"]),
    ("Cardiology", ["ecg", "ekg", "2d echo", "echocardiography", "cardiology"])
]

FINANCIAL_ENTITIES = [
    ("HDFC Bank", ["hdfc", "hdfc bank"]),
    ("SBI (State Bank of India)", ["sbi", "state bank of india"]),
    ("ICICI Bank", ["icici", "icici bank"]),
    ("Axis Bank", ["axis", "axis bank"]),
    ("Kotak Mahindra Bank", ["kotak", "kotak mahindra"]),
    ("Punjab National Bank (PNB)", ["pnb", "punjab national bank"]),
    ("Bank of Baroda", ["bank of baroda", "bob"]),
    ("Canara Bank", ["canara bank"])
]

COMMERCIAL_AND_RETAIL = [
    ("Amazon", ["amazon", "amazon.in"]),
    ("Flipkart", ["flipkart", "flipkart internet"]),
    ("Reliance Digital", ["reliance digital", "reliancedigital"]),
    ("Croma", ["croma", "infiniti retail"]),
    ("Apple", ["apple", "apple india"]),
    ("Samsung", ["samsung"]),
    ("LG", ["lg electronics", "lg"]),
    ("Dell", ["dell"]),
    ("HP", ["hp india", "hewlett packard"])
]

def extract_raw_text(file_path_or_bytes, filename="document.pdf", max_pages=40) -> str:
    """Safely extracts plain text from PDF bytes or files via pypdfium2."""
    try:
        if isinstance(file_path_or_bytes, (str, Path)):
            p = Path(file_path_or_bytes)
            if not p.exists() or p.stat().st_size == 0 or p.stat().st_size > MAX_BYTES:
                return ""
            data = p.read_bytes()
            filename = p.name
        else:
            data = file_path_or_bytes

        if not data or len(data) == 0:
            return ""

        ext = Path(filename).suffix.lower()
        if ext in ('.docx', '.xlsx', '.doc', '.xls', '.pptx', '.ppt', '.txt'):
            from .office_extractors import extract_office_text
            return extract_office_text(filename, data)

        parts = []
        if filename.lower().endswith('.pdf'):
            try:
                pdf = pdfium.PdfDocument(data)
                try:
                    page_count = min(len(pdf), max_pages)
                    for i in range(page_count):
                        try:
                            text_page = pdf[i].get_textpage()
                            t = text_page.get_text_range().strip()
                            if t:
                                parts.append(t)
                        except Exception:
                            pass
                finally:
                    try:
                        pdf.close()
                    except Exception:
                        pass
            except Exception:
                try:
                    decoded = data.decode('utf-8', errors='ignore').strip()
                    if decoded and not decoded.startswith('%PDF'):
                        parts.append(decoded)
                except Exception:
                    pass
        else:
            try:
                parts.append(data.decode('utf-8', errors='ignore'))
            except Exception:
                pass

        return "\n".join(parts).strip()
    except Exception:
        return ""

def extract_dates(text: str) -> list:
    """Extract standard dates from text."""
    date_patterns = [
        r'\b\d{4}[-/.]\d{1,2}[-/.]\d{1,2}\b', # YYYY-MM-DD
        r'\b\d{1,2}[-/.]\d{1,2}[-/.]\d{4}\b', # DD-MM-YYYY
        r'\b\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{4}\b',
        r'\b(?:FY|AY)\s*20\d{2}[-–]\d{2,4}\b'
    ]
    found = []
    for pat in date_patterns:
        for m in re.finditer(pat, text, re.IGNORECASE):
            d = m.group(0).strip()
            if d not in found:
                found.append(d)
    return found[:5]

def extract_identifiers(text: str) -> dict:
    """Extract official identifiers (PAN, DL, GSTIN, Aadhaar last-4, etc.)."""
    ids = {}
    pan = re.search(r'\b[A-Z]{5}[0-9]{4}[A-Z]\b', text)
    if pan:
        ids['PAN'] = pan.group(0)

    gstin = re.search(r'\b[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9A-Z]Z[0-9A-Z]\b', text)
    if gstin:
        ids['GSTIN'] = gstin.group(0)

    dl = re.search(r'\b[A-Z]{2}[-\s/]?[0-9]{2}[-\s/]?[0-9]{4}[-\s/]?[0-9]{7}\b', text)
    if not dl:
        dl = re.search(r'\bDL[-\s]?[0-9]{2,15}\b', text, re.IGNORECASE)
    if dl:
        ids['DL'] = dl.group(0)

    return ids

def build_document_metadata(file_path_or_bytes, filename="", category="", proposed_name="", specifics_raw="", extra_metadata=None) -> dict:
    """Builds a rich, queryable metadata structure for a document."""
    text = extract_raw_text(file_path_or_bytes, filename)
    combined = f"{text} {filename} {category} {proposed_name} {specifics_raw}"
    norm_combined = re.sub(r'[\s._\-]', '', combined.lower())

    discovered_keywords = set()
    discovered_labs = []
    discovered_tests = []
    discovered_banks = []
    discovered_vendors = []

    # 1. Match Labs & Diagnostic Clinics
    for label, kws in LABS_AND_CLINICS:
        for kw in kws:
            kw_norm = re.sub(r'[\s._\-]', '', kw.lower())
            if kw.lower() in combined.lower() or kw_norm in norm_combined:
                discovered_labs.append(label)
                discovered_keywords.add(label)
                discovered_keywords.add(kw.title())
                break

    # 2. Match Medical Tests
    for label, kws in MEDICAL_TESTS:
        for kw in kws:
            kw_norm = re.sub(r'[\s._\-]', '', kw.lower())
            # For short acronyms like lft, cbc, kft, rft, use word boundary or normalized check
            if len(kw) <= 4:
                matched = bool(re.search(r'\b' + re.escape(kw) + r'\b', combined, re.IGNORECASE))
            else:
                matched = (kw.lower() in combined.lower() or kw_norm in norm_combined)
            if matched:
                discovered_tests.append(label)
                discovered_keywords.add(label)
                if len(kw) <= 5:
                    discovered_keywords.add(kw.upper())
                else:
                    discovered_keywords.add(kw.title())
                break

    # 3. Match Banks
    for label, kws in FINANCIAL_ENTITIES:
        for kw in kws:
            kw_norm = re.sub(r'[\s._\-]', '', kw.lower())
            if kw.lower() in combined.lower() or kw_norm in norm_combined:
                discovered_banks.append(label)
                discovered_keywords.add(label)
                break

    # 4. Match Vendors
    for label, kws in COMMERCIAL_AND_RETAIL:
        for kw in kws:
            kw_norm = re.sub(r'[\s._\-]', '', kw.lower())
            if kw.lower() in combined.lower() or kw_norm in norm_combined:
                discovered_vendors.append(label)
                discovered_keywords.add(label)
                break

    # 5. Extract Dates & Identifiers
    dates = extract_dates(combined)
    identifiers = extract_identifiers(combined)
    for d in dates:
        discovered_keywords.add(d)
    for id_val in identifiers.values():
        discovered_keywords.add(id_val)

    if category:
        discovered_keywords.add(category)

    # 6. Merge Extra Structured Metadata from Backend (Gemini / Groq)
    extra_entities = {}
    if isinstance(extra_metadata, dict):
        for k, v in extra_metadata.items():
            if v and str(v).strip():
                clean_v = str(v).strip()
                extra_entities[k] = clean_v
                discovered_keywords.add(clean_v)
                if k in ('lab_name', 'hospital_name') and clean_v not in discovered_labs:
                    discovered_labs.append(clean_v)
                if k == 'test_name' and clean_v not in discovered_tests:
                    discovered_tests.append(clean_v)
                if k == 'issued_date' and clean_v not in dates:
                    dates.append(clean_v)
                if k == 'identifier' and clean_v not in identifiers.values():
                    identifiers[k] = clean_v

    # Build search index
    search_tokens = set()
    for kw in discovered_keywords:
        search_tokens.add(kw.lower())
        search_tokens.add(re.sub(r'[\s._\-]', '', kw.lower()))
    
    # Add words from text
    words = re.findall(r'[A-Za-z0-9]{3,}', text[:8000].lower())
    for w in words[:150]:
        search_tokens.add(w)

    metadata_obj = {
        "text_snippet": text[:4000],
        "keywords": sorted(list(discovered_keywords)),
        "labs": discovered_labs,
        "tests": discovered_tests,
        "banks": discovered_banks,
        "vendors": discovered_vendors,
        "dates": dates,
        "identifiers": identifiers,
        "entities": extra_entities,
        "search_index": " ".join(search_tokens)
    }
    return metadata_obj

def matches_query(record: dict, query: str) -> bool:
    """Matches a record against a search query across all metadata, text, and names."""
    if not query or not query.strip():
        return True
    
    q = query.strip().lower()
    q_norm = re.sub(r'[\s._\-]', '', q)

    # Check top-level fields
    for field in ['virtual_name', 'proposed', 'override', 'name', 'category', 'specifics', 'provider']:
        val = str(record.get(field) or '').lower()
        if q in val:
            return True
        val_norm = re.sub(r'[\s._\-]', '', val)
        if q_norm and q_norm in val_norm:
            return True

    # Check metadata JSON
    meta_raw = record.get('metadata') or ''
    if meta_raw:
        try:
            if isinstance(meta_raw, str):
                meta = json.loads(meta_raw)
            else:
                meta = meta_raw

            search_index = meta.get('search_index', '')
            if q in search_index or (q_norm and q_norm in re.sub(r'[\s._\-]', '', search_index)):
                return True

            for kw in meta.get('keywords', []):
                if q in kw.lower() or (q_norm and q_norm in re.sub(r'[\s._\-]', '', kw.lower())):
                    return True

            entities = meta.get('entities') or {}
            if isinstance(entities, dict):
                for ek, ev in entities.items():
                    ev_str = str(ev).lower()
                    if q in ev_str or (q_norm and q_norm in re.sub(r'[\s._\-]', '', ev_str)):
                        return True

            text_snippet = meta.get('text_snippet', '').lower()
            if q in text_snippet or (q_norm and q_norm in re.sub(r'[\s._\-]', '', text_snippet)):
                return True
        except Exception:
            if q in meta_raw.lower():
                return True

    return False

def get_search_suggestions(records: list, query: str = "", max_suggestions: int = 12) -> list:
    """Guesses what the user is trying to search based on all library metadata."""
    aggregated_terms = set()

    for r in records:
        if r.get('category'):
            aggregated_terms.add(r['category'])
        if r.get('virtual_name'):
            stem = Path(r['virtual_name']).stem
            clean_stem = re.sub(r'_\d{4}[-_]\d{2}[-_]\d{2}.*', '', stem).replace('_', ' ')
            if len(clean_stem) > 2:
                aggregated_terms.add(clean_stem)

        meta_raw = r.get('metadata')
        if meta_raw:
            try:
                meta = json.loads(meta_raw) if isinstance(meta_raw, str) else meta_raw
                for kw in meta.get('keywords', []):
                    if len(kw) >= 2:
                        aggregated_terms.add(kw)
                for lab in meta.get('labs', []):
                    aggregated_terms.add(lab)
                for test in meta.get('tests', []):
                    aggregated_terms.add(test)
                for bank in meta.get('banks', []):
                    aggregated_terms.add(bank)
                for v in meta.get('vendors', []):
                    aggregated_terms.add(v)
                entities = meta.get('entities') or {}
                if isinstance(entities, dict):
                    for ek, ev in entities.items():
                        if ev and isinstance(ev, str) and len(ev.strip()) >= 2:
                            aggregated_terms.add(ev.strip())
            except Exception:
                pass

    all_terms = sorted(list(aggregated_terms))

    q = (query or '').strip().lower()
    if not q:
        # Return popular/common metadata tags
        return all_terms[:max_suggestions]

    q_norm = re.sub(r'[\s._\-]', '', q)
    scored = []
    for term in all_terms:
        t_low = term.lower()
        t_norm = re.sub(r'[\s._\-]', '', t_low)

        score = 0
        if t_low.startswith(q) or t_norm.startswith(q_norm):
            score = 100 - len(term) # Earlier & shorter match preferred
        elif q in t_low or q_norm in t_norm:
            score = 50 - len(term)

        if score > 0:
            scored.append((score, term))

    scored.sort(key=lambda x: x[0], reverse=True)
    return [t for _, t in scored[:max_suggestions]]
