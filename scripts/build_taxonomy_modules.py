"""Build and synchronize taxonomy modules across Windows (smartdoc) and Android (Classifier.kt)
using the 519 standard Indian document categories.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TAXONOMY_JSON = ROOT / "shared-spec" / "taxonomy.json"

with open(TAXONOMY_JSON, "r", encoding="utf-8") as f:
    ALL_519_CATEGORIES = json.load(f)["categories"]

print(f"Loaded {len(ALL_519_CATEGORIES)} categories.")

# 1. Update windows/smartdoc/core.py
core_py_path = ROOT / "windows" / "smartdoc" / "core.py"
core_py_content = f'''import io, json, re, time
from dataclasses import dataclass, asdict
from PIL import Image, ImageOps
import pypdfium2 as pdfium

CATEGORIES = ('Medical Record', 'Government-issued Document', 'Organization-issued ID Card', 'Legal Document', 'Educational Document', 'Tax Document', 'Investment Statement', 'Bank Statement', 'Credit Card Statement', 'Bill or Invoice', 'Payment Receipt', 'Insurance Document', 'Vehicle Document', 'Employment Document', 'Property or Rental Document', 'Utility or Telecom Document', 'Travel Document', 'Other Document', 'uncategorizable input')

# Complete Indian Taxonomy (519 Granular Categories)
INDIAN_CATEGORIES = {tuple(ALL_519_CATEGORIES)!r}

ALL_CATEGORIES = CATEGORIES + tuple(c for c in INDIAN_CATEGORIES if c not in CATEGORIES)
FALLBACK = CATEGORIES[-1]
MAX_BYTES = 20 * 1024 * 1024
SUPPORTED = ('.pdf','.png','.jpg','.jpeg','.webp')

@dataclass
class Result:
    Filename: str
    Category: str = FALLBACK
    Specifics: str = ''
    Status: str = 'UNPROCESSED'
    Provider: str = 'Gemini cloud'
    Seconds: float = 0.0

def render(data: bytes, filename: str) -> bytes:
    if not data or len(data) == 0:
        raise ValueError('Document is empty (0 bytes)')
    if len(data) > MAX_BYTES:
        raise ValueError('File exceeds 20 MB limit')
    if not filename.lower().endswith(SUPPORTED):
        raise ValueError('Unsupported file type')
    if filename.lower().endswith('.pdf'):
        try:
            pdf = pdfium.PdfDocument(data)
        except Exception as exc:
            raise ValueError(f'Unable to parse PDF data: {{exc}}')
        try:
            if len(pdf) == 0: raise ValueError('Empty PDF')
            page = pdf[0]
            try:
                bitmap = page.render(scale=1.5)
                img = bitmap.to_pil()
            finally: page.close()
        finally: pdf.close()
    else:
        with Image.open(io.BytesIO(data)) as opened:
            img = ImageOps.exif_transpose(opened).copy()
    img = img.convert('RGB')
    img.thumbnail((1400, 1400))
    buf=io.BytesIO(); img.save(buf,format='JPEG',quality=78)
    return buf.getvalue()

def validate(raw, strict=False):
    if not isinstance(raw, dict): raise ValueError('Response is not an object')
    category = raw.get('category')
    if not category or not isinstance(category, str) or not category.strip():
        raise ValueError('Invalid category format')
    category = category.strip()
    
    # Case-insensitive / prefix matching against ALL_CATEGORIES
    matched = None
    for c in ALL_CATEGORIES:
        if c.lower() == category.lower():
            matched = c
            break
    if not matched:
        # Check if raw category contains or is contained by a recognized category
        for c in ALL_CATEGORIES:
            if category.lower() in c.lower() or c.lower() in category.lower():
                matched = c
                break
    if matched:
        category = matched
    elif category not in ALL_CATEGORIES:
        if strict:
            raise ValueError(f'Invalid category: {{category}}')
        # Accept descriptive category directly
        pass
    specifics = raw.get('specifics', '')
    if not isinstance(specifics, str):
        specifics = str(specifics)
    return category, specifics[:2000]

def parse(text: str) -> dict:
    cleaned = re.sub(r'```(?:json)?', '', text).strip()
    start = cleaned.find('{{')
    end = cleaned.rfind('}}')
    if start == -1 or end == -1 or end <= start:
        raise ValueError('No JSON structure found')
    try:
        data = json.loads(cleaned[start:end+1])
    except Exception as exc:
        raise ValueError(f'JSON parsing failed: {{exc}}')
    return data

def classify(filename: str, data: bytes, key: str, model: str, session) -> dict:
    url = f'https://generativelanguage.googleapis.com/v1beta/models/{{model}}:generateContent?key={{key}}'
    t0 = time.time()
    try:
        image_bytes = render(data, filename)
    except Exception as exc:
        return asdict(Result(Filename=filename, Specifics=str(exc), Status='INPUT_ERROR', Seconds=round(time.time()-t0, 3)))
    import base64
    b64 = base64.b64encode(image_bytes).decode('ascii')
    payload = {{
        'contents': [{{
            'parts': [
                {{'text': 'Which kind of document is it, what could be the logical category for this document, propose a short meaningful name for it and why do you think it belongs to the category which you have suggested?\\n\\nReturn JSON schema: {{"category": "<category>", "specifics": "<reasoning>"}}'}},
                {{'inline_data': {{'mime_type': 'image/jpeg', 'data': b64}}}}
            ]
        }}]
    }}
    try:
        resp = session.post(url, json=payload, timeout=30)
    except Exception as exc:
        return asdict(Result(Filename=filename, Specifics=str(exc), Status='NETWORK_ERROR', Seconds=round(time.time()-t0, 3)))
    if resp.status_code == 429:
        return asdict(Result(Filename=filename, Specifics='API rate limit or quota exceeded (HTTP 429)', Status='API_ERROR', Seconds=round(time.time()-t0, 3)))
    if resp.status_code != 200:
        return asdict(Result(Filename=filename, Specifics=f'HTTP error {{resp.status_code}}', Status='API_ERROR', Seconds=round(time.time()-t0, 3)))
    try:
        body = resp.json()
        raw_text = body['candidates'][0]['content']['parts'][0]['text']
        parsed = parse(raw_text)
        cat, specifics = validate(parsed)
        status = 'UNCATEGORIZABLE' if cat == FALLBACK else 'OK'
        return asdict(Result(Filename=filename, Category=cat, Specifics=specifics, Status=status, Seconds=round(time.time()-t0, 3)))
    except Exception as exc:
        return asdict(Result(Filename=filename, Specifics=str(exc), Status='INVALID_RESPONSE', Seconds=round(time.time()-t0, 3)))
'''

with open(core_py_path, "w", encoding="utf-8") as f:
    f.write(core_py_content)
print(f"Updated {core_py_path}")


# 2. Generate windows/smartdoc/indian_taxonomy.py
taxonomy_py_path = ROOT / "windows" / "smartdoc" / "indian_taxonomy.py"

specialized_rules = {
    "Aadhaar Card": {
        "keywords": [
            "uidai", "unique identification authority of india", "aadhaar", "aadhar",
            "mera aadhaar meri pehchan", "enrolment no", "enrolment number", "vid", "virtual id", "government of india"
        ],
        "regex": [r"\b\d{4}\s\d{4}\s\d{4}\b", r"\baadha?ar\b", r"\buidai\b"],
        "weight": 5,
    },
    "Permanent Account Number Card": {
        "keywords": [
            "permanent account number", "income tax department", "pan card",
            "father's name", "govt. of india", "pan no", "pan number"
        ],
        "regex": [r"\b[A-Z]{5}[0-9]{4}[A-Z]\b", r"\bpermanent\s+account\s+number\b", r"\bpan\s+card\b"],
        "weight": 5,
    },
    "Passport": {
        "keywords": [
            "republic of india", "passport", "ministry of external affairs",
            "passport no", "place of birth", "place of issue", "given name", "surname", "nationality indian"
        ],
        "regex": [r"\b[A-PR-WYa-pr-wy][1-9]\d\s?\d{4}[1-9]\b", r"\bpassport\b"],
        "weight": 5,
    },
    "Voter Identity Card": {
        "keywords": [
            "election commission of india", "electoral photo identity card",
            "voter id", "voter card", "elector's name", "epic no", "epic number", "vidhan sabha", "parliamentary constituency", "elector"
        ],
        "regex": [r"\b[A-Z]{3}[0-9]{7}\b", r"\belector(?:al)?\b", r"\bvoter\s*(?:id|card)\b"],
        "weight": 5,
    },
    "Driving License": {
        "keywords": [
            "driving licence", "driving license", "driver licence", "driver license",
            "driver's licence", "driver's license", "parivahan", "sarathi", "morth",
            "ministry of road transport", "transport department", "motor vehicles department",
            "indian union driving licence", "licensing authority", "form 7", "dl no", "dl.no",
            "dl number", "authorisation to drive", "authorized to drive", "validity (nt)",
            "validity (tr)", "nt validity", "tr validity", "lmv", "mcwg", "mcwog", "hgv", "badge no",
            "learner's licence", "driving permit", "rto", "dto", "motor vehicle"
        ],
        "regex": [
            r"\b[A-Z]{2}[-\s/]?[0-9]{2}[-\s/]?[0-9]{4}[-\s/]?[0-9]{7}\b",
            r"\bDL[-\s]?[0-9]{2,}\b",
            r"\bdriving\s*licen[sc]e\b",
            r"\bdl\s*(?:no|number)?[:\s-]*([A-Z0-9/\-]{7,20})\b",
            r"\bsarathi\b",
            r"\bparivahan\b"
        ],
        "weight": 5,
    },
    "Medical Prescription": {
        "keywords": [
            "prescription", "dr.", "dr ", "doctor", "physician", "surgeon", "consultant",
            "clinic", "hospital", "patient name", "rx", "rx:", "tablet", "tab.", "capsule",
            "cap.", "syrup", "syp.", "dosage", "diagnosis", "chief complaint", "advice",
            "consultation", "opd slip", "opd ticket", "clinical notes", "1-0-1", "0-1-0",
            "1-0-0", "0-0-1", "od", "bd", "tds", "qid", "bed rest", "follow up", "mg"
        ],
        "regex": [
            r"\b(?:tab|cap|syp)\.?\s+[A-Za-z0-9]+\b",
            r"\b(?:rx|r[xX])[:\s]\b",
            r"\bdr\.?\s+[A-Za-z]+\b",
            r"\bpatient\s+(?:name|age)\b",
            r"\bprescri(?:be|ption)\b"
        ],
        "weight": 5,
    },
    "Diagnostic Laboratory Report": {
        "keywords": [
            "diagnostic", "pathology", "laboratory report", "lab report", "test report",
            "reference range", "biological reference", "observed value", "specimen",
            "investigation report", "clinical laboratory"
        ],
        "regex": [
            r"\breference\s+range\b",
            r"\bbiological\s+ref\b",
            r"\bobserved\s+value\b",
            r"\blab(?:oratory)?\s+report\b"
        ],
        "weight": 5,
    },
    "Blood Test Report": {
        "keywords": [
            "blood test", "complete blood count", "cbc", "lipid profile", "hba1c",
            "thyroid profile", "liver function test", "lft", "kidney function test", "kft",
            "hemoglobin", "wbc count", "platelet count", "fasting blood sugar"
        ],
        "regex": [
            r"\bblood\s+test\b",
            r"\blipid\s+profile\b",
            r"\bhba1c\b",
            r"\bcomplete\s+blood\s+count\b"
        ],
        "weight": 5,
    },
    "Pathology Report": {
        "keywords": [
            "pathology report", "histopathology", "cytopathology", "biopsy",
            "microscopic examination", "gross examination", "pathologist"
        ],
        "regex": [
            r"\bpathology\s+report\b",
            r"\bhistopatholog(?:y|ical)\b"
        ],
        "weight": 5,
    },
    "Rent Agreement": {
        "keywords": [
            "rent agreement", "rental agreement", "tenancy agreement", "tenancy contract",
            "landlord", "tenant", "demised premises", "monthly rent", "security deposit",
            "lock-in period", "notice period", "stamp duty", "stamp paper", "maintenance charges",
            "fixtures and fittings", "peaceful possession"
        ],
        "regex": [
            r"\brent(?:al)?\s+agreement\b",
            r"\bmonthly\s+rent\b",
            r"\bsecurity\s+deposit\b",
            r"\b(landlord|tenant)\b"
        ],
        "weight": 5,
    },
    "Lease Deed": {
        "keywords": [
            "lease deed", "lease agreement", "lease draft", "draft lease",
            "commercial lease", "residential lease", "sublease", "sub-lease", "lessor", "lessee",
            "demised premises", "scheduled premises", "agreement of lease", "draft"
        ],
        "regex": [
            r"\blease\s+(?:agreement|deed|draft)\b",
            r"\b(lessor)\b.*?\b(lessee)\b"
        ],
        "weight": 5,
    },
    "Leave and License Agreement": {
        "keywords": [
            "leave and license", "licensor", "licensee", "licensed premises", "leave and license agreement"
        ],
        "regex": [
            r"\bleave\s+and\s+licen[sc]e\b"
        ],
        "weight": 5,
    },
    "Form 16": {
        "keywords": [
            "form no. 16", "form 16", "certificate under section 203", "tax deducted at source",
            "part a", "part b", "assessment year", "tan of employer", "pan of employee"
        ],
        "regex": [r"\bform\s*(?:no\.?\s*)?16\b", r"\bsection\s*203\b"],
        "weight": 5,
    },
    "Form 16A": {
        "keywords": [
            "form no. 16a", "form 16a", "certificate under section 203", "tds certificate",
            "quarter", "tan of deductor"
        ],
        "regex": [r"\bform\s*(?:no\.?\s*)?16a\b"],
        "weight": 5,
    },
    "Form 26AS": {
        "keywords": [
            "form 26as", "annual tax statement", "part a - details of tax deducted",
            "financial year", "assessment year", "traces", "tax credit statement"
        ],
        "regex": [r"\bform\s*26as\b", r"\btax\s+credit\s+statement\b"],
        "weight": 5,
    },
    "Income Tax Return Verification Form": {
        "keywords": [
            "indian income tax return", "itr-v", "income tax return acknowledgement",
            "assessment year", "computation of total income", "e-filing", "incometax.gov.in",
            "itr 1", "itr 2", "itr 3", "itr 4", "itr verification"
        ],
        "regex": [r"\bitr[-\s]?[1-7v]\b", r"\backnowledgement\s+number\b"],
        "weight": 5,
    },
    "Savings Account Statement": {
        "keywords": [
            "savings account statement", "savings bank account", "account statement", "statement of account",
            "ifsc", "ifsc code", "rtgs", "neft", "upi", "imps", "account number", "savings account",
            "state bank of india", "sbi", "hdfc bank", "icici bank", "axis bank", "punjab national bank",
            "pnb", "canara bank", "kotak mahindra", "bank of baroda", "union bank"
        ],
        "regex": [r"\b[A-Z]{4}0[A-Z0-9]{6}\b", r"\bsavings\s+account\b", r"\baccount\s+statement\b"],
        "weight": 5,
    },
    "Current Account Statement": {
        "keywords": [
            "current account statement", "current account", "statement of account", "ifsc",
            "overdraft", "cash credit"
        ],
        "regex": [r"\bcurrent\s+account\b", r"\baccount\s+statement\b"],
        "weight": 5,
    },
    "Bank Passbook": {
        "keywords": [
            "bank passbook", "passbook", "cif no", "cif number", "account no", "branch manager", "ifsc"
        ],
        "regex": [r"\bpassbook\b", r"\bcif\s*no\b"],
        "weight": 5,
    },
    "Credit Card Statement": {
        "keywords": [
            "credit card statement", "card statement", "total amount due",
            "minimum amount due", "payment due date", "credit limit", "available credit",
            "card number", "reward points", "statement period"
        ],
        "regex": [r"\btotal\s+amount\s+due\b", r"\bminimum\s+amount\s+due\b", r"\bcredit\s+card\b"],
        "weight": 5,
    },
    "Employee Salary Slip": {
        "keywords": [
            "salary slip", "payslip", "pay slip", "earnings", "deductions",
            "basic salary", "hra", "dearness allowance", "gross pay", "net pay",
            "employee code", "employee id", "take home pay", "pf deduction"
        ],
        "regex": [r"\bpay\s*slip\b", r"\bsalary\s*slip\b", r"\bgross\s+pay\b", r"\bnet\s+pay\b"],
        "weight": 5,
    },
    "Employees Provident Fund Statement": {
        "keywords": [
            "employees provident fund statement", "epfo", "employees' provident fund",
            "provident fund", "epf", "uan", "universal account number", "member passbook", "pf passbook", "epf statement"
        ],
        "regex": [r"\bepfo\b", r"\buan\b[:\s]*\d{12}", r"\bprovident\s+fund\b"],
        "weight": 5,
    },
    "PF Transfer Claim Form": {
        "keywords": [
            "form 13", "form-13", "form 13 (revised)", "transfer claim",
            "transfer of provident fund", "transfer of pf", "pf transfer"
        ],
        "regex": [r"\bform\s*(?:no\.?\s*)?13\b", r"\btransfer\s+claim\b"],
        "weight": 5,
    },
    "Universal Account Number Card": {
        "keywords": [
            "uan card", "universal account number card", "epfo uan"
        ],
        "regex": [r"\buan\s+card\b"],
        "weight": 5,
    },
    "Electricity Bill": {
        "keywords": [
            "electricity bill", "power distribution", "consumer no", "ca no",
            "meter number", "units consumed", "kwh", "discom", "bses", "tata power",
            "msedcl", "bescom", "uppcl", "torrent power", "bill amount"
        ],
        "regex": [r"\bconsumer\s+(?:no|number|id)\b", r"\bmeter\s+reading\b", r"\belectricity\s+bill\b"],
        "weight": 5,
    },
    "Water Bill": {
        "keywords": [
            "water bill", "jal board", "delhi jal board", "water supply",
            "water sewerage", "meter reading", "water consumption"
        ],
        "regex": [r"\bwater\s+bill\b", r"\bjal\s+board\b"],
        "weight": 5,
    },
    "Piped Gas Bill": {
        "keywords": [
            "piped natural gas", "png bill", "igl", "indraprastha gas", "mgl", "mahanagar gas", "piped gas bill"
        ],
        "regex": [r"\bpng\s+bill\b", r"\bpiped\s+gas\b"],
        "weight": 5,
    },
    "Liquefied Petroleum Gas Passbook": {
        "keywords": [
            "indane", "bharat gas", "hp gas", "lpg", "cylinder booking",
            "consumer number", "distributor name", "cash memo", "petroleum", "indane gas",
            "hindustan petroleum", "bharat petroleum", "lpg passbook"
        ],
        "regex": [r"\blpg\b", r"\bcash\s+memo\b", r"\bgas\s+passbook\b"],
        "weight": 5,
    },
    "Vehicle Registration Certificate": {
        "keywords": [
            "registration certificate", "rc book", "chassis no", "engine no",
            "vehicle class", "maker's name", "vahan", "registration no", "regn no", "parivahan sewa"
        ],
        "regex": [r"\bchassis\s+no\b", r"\bengine\s+no\b", r"\bregistration\s+certificate\b"],
        "weight": 5,
    },
    "Motor Vehicle Insurance Policy": {
        "keywords": [
            "motor insurance", "policy certificate", "third party insurance",
            "comprehensive policy", "two wheeler policy", "private car policy", "idv", "insured declared value"
        ],
        "regex": [r"\bmotor\s+insurance\b", r"\binsured\s+declared\s+value\b"],
        "weight": 5,
    },
}

# Auto-generate rules for the remaining 519 categories
all_taxonomy_rules = {}
for cat in ALL_519_CATEGORIES:
    if cat in specialized_rules:
        all_taxonomy_rules[cat] = specialized_rules[cat]
    else:
        lower_name = cat.lower()
        words = re.findall(r"[a-z0-9]+", lower_name)
        kws = [lower_name]
        if len(words) > 1:
            clean_phrase = " ".join(words)
            if clean_phrase != lower_name:
                kws.append(clean_phrase)
        all_taxonomy_rules[cat] = {
            "keywords": kws,
            "regex": [rf"\b{re.escape(lower_name)}\b"],
            "weight": 3
        }

taxonomy_py_content = f'''"""Indian Document Taxonomy, Keyword Classification, Date Extraction, and Smart Naming.
Supports 519 standard Indian document categories across identity, educational, legal,
tax, financial, employment, property, medical, utility, and commercial sectors.
"""
import re
from datetime import datetime
from pathlib import Path

# Complete Indian Taxonomy (519 Granular Categories)
INDIAN_CATEGORIES = {ALL_519_CATEGORIES!r}

# Classification rules
TAXONOMY_RULES = {all_taxonomy_rules!r}

# Legacy aliases for robust backward compatibility in local heuristics & tests
CATEGORY_ALIASES = {{
    "pan card": "Permanent Account Number Card",
    "voter id card": "Voter Identity Card",
    "driving licence": "Driving License",
    "salary slip / payslip": "Employee Salary Slip",
    "form 16 / tds certificate": "Form 16",
    "form 26as / tax statement": "Form 26AS",
    "rental / lease agreement": "Rent Agreement",
    "epf / provident fund (form 13 / uan)": "PF Transfer Claim Form",
    "bank statement": "Savings Account Statement",
    "medical diagnostic / lab report": "Diagnostic Laboratory Report",
}}

def extract_dates(text):
    """Extract dates from text in Indian formats (DD/MM/YYYY, DD-Mon-YYYY, AY 20XX-YY, etc.)."""
    dates = []
    
    # 1. Assessment / Financial Year: e.g. AY 2024-25 or FY 2023-24
    ay_match = re.search(r"\\b(AY|FY)\\s*[-:]?\\s*(20\\d{{2}}[-–]\\d{{2,4}})\\b", text, re.IGNORECASE)
    if ay_match:
        dates.append(f"{{ay_match.group(1).upper()}}_{{ay_match.group(2).replace('–', '-')}}")
        
    # 2. Written ordinal date: e.g. 1st day of October 2024, 15th August 2024
    ord_match = re.search(
        r"\\b(\\d{{1,2}})(?:st|nd|rd|th)?\\s+(?:day\\s+of\\s+)?(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+(20\\d{{2}}|19\\d{{2}})\\b",
        text, re.IGNORECASE
    )
    if ord_match:
        d, m, y = ord_match.groups()
        months = {{'jan':1,'feb':2,'mar':3,'apr':4,'may':5,'jun':6,'jul':7,'aug':8,'sep':9,'oct':10,'nov':11,'dec':12}}
        m_num = months.get(m[:3].lower(), 1)
        dates.append(f"{{y}}-{{m_num:02d}}-{{int(d):02d}}")
        
    # 3. Standard DD/MM/YYYY or DD-MM-YYYY
    std_dates = re.findall(r"\\b(\\d{{1,2}})[/.-](\\d{{1,2}})[/.-](20\\d{{2}}|19\\d{{2}})\\b", text)
    for d, m, y in std_dates[:3]:
        dates.append(f"{{y}}-{{int(m):02d}}-{{int(d):02d}}")
        
    # 4. Month Year: e.g. Oct 2024, October 2024
    month_year = re.findall(r"\\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+(20\\d{{2}})\\b", text, re.IGNORECASE)
    for m, y in month_year[:2]:
        dates.append(f"{{m[:3].capitalize()}}_{{y}}")
        
    return dates

def deduce_category(text, original_name="", return_details=False):
    """Categorize document based strictly on content metadata text (filename is NEVER evaluated).
    When multiple categories match, the category with the maximum number of matched identifiers wins."""
    content = (text or "").strip()
    if not content:
        if return_details:
            return "Other Document", 0, {{"keywords": [], "patterns": [], "identifiers": [], "matched_count": 0}}
        return "Other Document", 0
        
    lower_text = content.lower()
    normalized = re.sub(r"[\\W_]+", " ", lower_text)
    scores = {{}}
    evidence_breakdown = {{}}
    
    for category, rule in TAXONOMY_RULES.items():
        matched_identifiers = []
        for kw in rule.get("keywords", []):
            if kw in lower_text or kw in normalized:
                matched_identifiers.append(kw)
        for pattern in rule.get("regex", []):
            if re.search(pattern, content, re.IGNORECASE) or re.search(pattern, normalized, re.IGNORECASE):
                matched_identifiers.append(pattern)
                
        if matched_identifiers:
            matched_count = len(matched_identifiers)
            total_specificity = sum(len(m) for m in matched_identifiers)
            scores[category] = (matched_count, total_specificity, rule.get("weight", 3))
            evidence_breakdown[category] = {{
                "keywords": [k for k in matched_identifiers if not k.startswith("\\\\b") and not k.startswith("r\\"")],
                "patterns": [p for p in matched_identifiers if p.startswith("\\\\b") or p.startswith("r\\"")],
                "identifiers": matched_identifiers,
                "matched_count": matched_count,
                "score": matched_count
            }}
            
    if not scores:
        if return_details:
            return "Other Document", 0, {{"keywords": [], "patterns": [], "identifiers": [], "matched_count": 0}}
        return "Other Document", 0
        
    # The category with the MAXIMUM number of matched identifiers wins
    best_category = max(scores.items(), key=lambda x: (x[1][0], x[1][2], x[1][1]))
    details = evidence_breakdown.get(best_category[0], {{"keywords": [], "patterns": [], "identifiers": [], "score": best_category[1][0]}})
    
    if return_details:
        return best_category[0], best_category[1][0], details
    return best_category[0], best_category[1][0]

def generate_proposed_name(category, text, original_name):
    """Generate a short, meaningful proposed name with extracted publication/issue/statement date."""
    ext = original_name.rsplit('.', 1)[-1].lower() if '.' in original_name else 'pdf'
    combined = f"{{text}} {{original_name}}"
    dates = extract_dates(combined)
    primary_date = dates[0] if dates else datetime.now().strftime("%Y-%m-%d")
    
    upper_text = combined.upper()
    is_draft = bool(re.search(r"\\b(draft|incomplete)\\b", combined, re.IGNORECASE))
    
    # Specific standard Indian namings
    if category in ("Permanent Account Number Card", "PAN Card"):
        pan_match = re.search(r"\\b([A-Z]{{5}}[0-9]{{4}}[A-Z])\\b", upper_text)
        tag = pan_match.group(1) if pan_match else ""
        name = f"PAN_Card_{{tag}}_{{primary_date}}" if tag else f"PAN_Card_{{primary_date}}"
        
    elif category == "Aadhaar Card":
        name = f"Aadhaar_Card_{{primary_date}}"
        
    elif category == "Passport":
        name = f"Passport_{{primary_date}}"
        
    elif category in ("Voter Identity Card", "Voter ID Card"):
        epic_match = re.search(r"\\b([A-Z]{{3}}[0-9]{{7}})\\b", upper_text)
        tag = epic_match.group(1) if epic_match else ""
        name = f"Voter_ID_{{tag}}_{{primary_date}}" if tag else f"Voter_ID_{{primary_date}}"
        
    elif category in ("Driving License", "Driving Licence"):
        dl_match = re.search(r"\\b([A-Z]{{2}}[-\\s/]?[0-9]{{2}}[-\\s/]?[0-9]{{4}}[-\\s/]?[0-9]{{7}})\\b", upper_text)
        if not dl_match:
            dl_match = re.search(r"\\b(DL[-\\s]?[0-9]{{2,15}})\\b", upper_text)
        tag = re.sub(r"[^\\w]", "", dl_match.group(1)) if dl_match else ""
        name = f"Driving_Licence_{{tag}}_{{primary_date}}" if tag else f"Driving_Licence_{{primary_date}}"
        
    elif category == "Medical Prescription":
        dr_match = re.search(r"\\b(?:dr\\.?|doctor)\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)?)\\b", combined, re.IGNORECASE)
        clinic_match = None
        for c in ["Apollo", "Fortis", "Max", "Medanta", "Manipal", "AIIMS"]:
            if c.upper() in upper_text:
                clinic_match = c
                break
        doc_tag = f"Dr_{{dr_match.group(1).replace(' ', '_')}}" if dr_match else (clinic_match or "")
        name = f"Prescription_{{doc_tag}}_{{primary_date}}" if doc_tag else f"Prescription_{{primary_date}}"

    elif category in ("Diagnostic Laboratory Report", "Medical Diagnostic / Lab Report", "Blood Test Report", "Pathology Report"):
        test = "Lab_Report"
        for t in ["Blood_Test", "CBC", "Lipid_Profile", "HbA1c", "Thyroid", "X_Ray", "Ultrasound", "Urine_Test", "LFT", "KFT"]:
            if t.replace('_', ' ').upper() in upper_text:
                test = t
                break
        name = f"Medical_{{test}}_{{primary_date}}"
        
    elif category in ("Rent Agreement", "Lease Deed", "Rental / Lease Agreement"):
        prefix = "Lease_Agreement_Draft" if is_draft else "Lease_Agreement"
        name = f"{{prefix}}_{{primary_date}}"
        
    elif category in ("PF Transfer Claim Form", "Employees Provident Fund Statement", "Universal Account Number Card", "EPF / Provident Fund (Form 13 / UAN)"):
        is_f13 = bool(re.search(r"\\bform\\s*(?:no\\.?\\s*)?13\\b", combined, re.IGNORECASE))
        tag = "EPFO_Form13_Transfer" if is_f13 else "EPF_Statement"
        name = f"{{tag}}_{{primary_date}}"
        
    elif category in ("Form 16", "Form 16 / TDS Certificate"):
        ay_match = re.search(r"\\b(AY\\s*[-:]?\\s*20\\d{{2}}[-–]\\d{{2,4}})\\b", text, re.IGNORECASE)
        tag = ay_match.group(1).replace(' ', '_').replace('–', '-') if ay_match else primary_date
        name = f"Form16_{{tag}}"
        
    elif category in ("Form 16A",):
        name = f"Form16A_{{primary_date}}"
        
    elif category in ("Form 26AS", "Form 26AS / Tax Statement"):
        name = f"Form26AS_{{primary_date}}"
        
    elif category in ("Income Tax Return Verification Form", "Income Tax Return"):
        name = f"ITR_{{primary_date}}"
        
    elif category in ("Savings Account Statement", "Current Account Statement", "Bank Statement"):
        bank = "Bank"
        for b in ["SBI", "HDFC", "ICICI", "Axis", "PNB", "Canara", "Kotak", "Baroda"]:
            if b.upper() in upper_text:
                bank = b
                break
        name = f"{{bank}}_Statement_{{primary_date}}"
        
    elif category == "Bank Passbook":
        bank = "Bank"
        for b in ["SBI", "HDFC", "ICICI", "Axis", "PNB", "Canara", "Kotak", "Baroda"]:
            if b.upper() in upper_text:
                bank = b
                break
        name = f"{{bank}}_Passbook_{{primary_date}}"
        
    elif category == "Credit Card Statement":
        bank = "Card"
        for b in ["SBI", "HDFC", "ICICI", "Axis", "Amex", "Kotak", "RBL"]:
            if b.upper() in upper_text:
                bank = b
                break
        name = f"{{bank}}_CreditCard_{{primary_date}}"
        
    elif category == "Electricity Bill":
        provider = "Electricity"
        for p in ["Tata_Power", "BSES", "MSEDCL", "BESCOM", "Torrent", "UPPCL"]:
            if p.replace('_', ' ').upper() in upper_text:
                provider = p
                break
        name = f"{{provider}}_Bill_{{primary_date}}"
        
    elif category in ("Employee Salary Slip", "Salary Slip / Payslip"):
        name = f"Salary_Slip_{{primary_date}}"
        
    elif category == "Other Document":
        stem = Path(original_name).stem if original_name else "Document"
        clean_stem = re.sub(r"[^\\w\\-]", "_", stem).strip('_')[:60] or "Document"
        name = f"{{clean_stem}}_{{primary_date}}"

    else:
        clean_cat = re.sub(r"[^\\w]", "_", category.replace(" Certificate", "_Cert").replace(" Document", "")).strip('_')
        name = f"{{clean_cat}}_{{primary_date}}"
        
    clean_name = re.sub(r"[^\\w\\-.]", "_", name).strip('_')
    return f"{{clean_name}}.{{ext}}"

def classify_and_name_locally(text, original_name="", return_details=False):
    """Run local Indian document rules on text to return category, proposed name, and structured evidence."""
    cat, score, details = deduce_category(text, original_name, return_details=True)
    proposed = generate_proposed_name(cat, text, original_name)
    
    matched_kws = details.get("keywords", [])
    matched_pats = details.get("patterns", [])
    
    basis_parts = []
    if matched_kws:
        clean_kws = ", ".join(matched_kws[:6])
        basis_parts.append(f"Matched Keywords: [{{clean_kws}}]")
    if matched_pats:
        basis_parts.append(f"Triggered Patterns: {{len(matched_pats)}} regex rule(s)")
    if score > 0:
        basis_parts.append(f"Confidence Weight: {{score}}")
    else:
        basis_parts.append("Basis: General document fallback (no specific taxonomy patterns matched)")
        
    dates = extract_dates(f"{{text}} {{original_name}}")
    if dates:
        basis_parts.append(f"Extracted Date: {{dates[0]}}")
        
    if re.search(r"\\b(draft|incomplete)\\b", f"{{text}} {{original_name}}", re.IGNORECASE):
        basis_parts.append("Status: Incomplete / Draft Document Detected")
        
    evidence_summary = " • " + " | ".join(basis_parts)
    
    if return_details:
        return cat, proposed, evidence_summary
    return cat, proposed
'''

with open(taxonomy_py_path, "w", encoding="utf-8") as f:
    f.write(taxonomy_py_content)
print(f"Updated {taxonomy_py_path}")
