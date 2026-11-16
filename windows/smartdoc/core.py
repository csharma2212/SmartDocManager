import io, json, re, time
from dataclasses import dataclass, asdict
from PIL import Image, ImageOps
import pypdfium2 as pdfium

CATEGORIES = ('Medical Record', 'Government-issued Document', 'Organization-issued ID Card', 'Legal Document', 'Educational Document', 'Tax Document', 'Investment Statement', 'Bank Statement', 'Credit Card Statement', 'Bill or Invoice', 'Payment Receipt', 'Insurance Document', 'Vehicle Document', 'Employment Document', 'Property or Rental Document', 'Utility or Telecom Document', 'Travel Document', 'Other Document', 'uncategorizable input')

# Complete Indian Taxonomy (519 Granular Categories)
INDIAN_CATEGORIES = ('Aadhaar Card', 'Permanent Account Number Card', 'Voter Identity Card', 'Passport', 'Driving License', 'Ration Card', 'Birth Certificate', 'Death Certificate', 'Marriage Certificate', 'Domicile Certificate', 'Income Certificate', 'Caste Certificate', 'Other Backward Class Certificate', 'Economically Weaker Section Certificate', 'Legal Heir Certificate', 'Encumbrance Certificate', 'Transfer Certificate', 'Migration Certificate', 'Character Certificate', 'Bonafide Certificate', 'Overseas Citizen of India Card', 'Person of Indian Origin Card', 'Visa Document', 'Residence Certificate', 'Class 10 Marksheet', 'Class 12 Marksheet', 'Undergraduate Degree Certificate', 'Postgraduate Degree Certificate', 'Provisional Degree Certificate', 'School Leaving Certificate', 'Semester Marksheet', 'Grade Card', 'Hall Ticket', 'Convocation Letter', 'Academic Transcript', 'Course Completion Certificate', 'Extracurricular Activity Certificate', 'Sports Merit Certificate', 'National Cadet Corps Certificate', 'National Service Scheme Certificate', 'Scout and Guide Certificate', 'Entrance Exam Admit Card', 'Entrance Exam Scorecard', 'Counseling Allotment Letter', 'Seat Confirmation Letter', 'Tuition Fee Receipt', 'Thesis Submission Certificate', 'PhD Degree Certificate', 'Engineering Degree Certificate', 'Diploma Certificate', 'Industrial Training Institute Certificate', 'National Skill Development Corporation Certificate', 'Apprenticeship Certificate', 'Internship Completion Certificate', 'Fellowship Award Letter', 'Scholarship Sanction Letter', 'Research Grant Award Letter', 'Language Proficiency Certificate', 'IELTS Scorecard', 'TOEFL Scorecard', 'GRE Scorecard', 'GMAT Scorecard', 'Study Abroad Offer Letter', 'Confirmation of Acceptance for Studies Letter', 'I-20 Form', 'DS-160 Form', 'Schengen Visa Form', 'Disability Certificate', 'Ayushman Bharat Health Account Card', 'Ayushman Card', 'Central Government Health Scheme Card', 'Ex-Servicemen Contributory Health Scheme Card', 'Employees State Insurance Corporation Card', 'Medical Fitness Certificate', 'Hospital Discharge Summary', 'Diagnostic Laboratory Report', 'Medical Prescription', 'Organ Donor Certificate', 'Blood Group Certificate', 'Vaccination Certificate', 'COVID-19 Vaccination Certificate', 'MRI Report', 'X-Ray Report', 'CT Scan Report', 'Ultrasound Report', 'Blood Test Report', 'Pathology Report', 'Biopsy Report', 'Disability Medical Certificate', 'Severe Disability Certificate', 'Specified Disease Treatment Receipt', 'Audiometry Report', 'Vision Certificate', 'Color Blindness Certificate', 'Physical Fitness Certificate', 'Income Tax Return Verification Form', 'Form 16', 'Form 16A', 'Form 26AS', 'Annual Information Statement', 'Taxpayer Information Summary', 'Form 15G', 'Form 15H', 'Lower Deduction Certificate', 'Advance Tax Challan', 'Self-Assessment Tax Challan', 'Regular Assessment Tax Challan', 'Wealth Tax Return', 'Form 12BB', 'Form 49A', 'Form 49B', 'Form 10E', 'Income Tax Assessment Order', 'Tax Deduction at Source Certificate', 'Tax Collection at Source Certificate', 'Bank Passbook', 'Cheque Book', 'Fixed Deposit Receipt', 'Recurring Deposit Receipt', 'Demat Account Statement', 'Mutual Fund Account Statement', 'Consolidated Account Statement', 'Systematic Investment Plan Statement', 'Capital Gains Statement', 'Sovereign Gold Bond Certificate', 'Employees Provident Fund Statement', 'Public Provident Fund Passbook', 'National Pension System Permanent Retirement Account Number Card', 'Savings Account Statement', 'Current Account Statement', 'Non-Resident External Account Statement', 'Non-Resident Ordinary Account Statement', 'Loan Account Statement', 'Credit Card Statement', 'Credit Card Bill', 'Universal Account Number Card', 'Pension Payment Order', 'PF Transfer Claim Form', 'PF Withdrawal Form', 'Form 19', 'Form 10C', 'Form 31', 'Form 10D', 'Pension Claim Form', 'Monthly Pension Form', 'EPS Nomination Form', 'Gratuity Nomination Form', 'Shram Card', 'Mahatma Gandhi National Rural Employment Guarantee Act Job Card', 'Employee Offer Letter', 'Employee Appointment Letter', 'Employee Relieving Letter', 'Employee Experience Certificate', 'Employee Salary Slip', 'Employee Appraisal Letter', 'Bonus Letter', 'Increment Letter', 'Arrears Statement', 'Non-Disclosure Agreement', 'Employment Contract', 'Maternity Benefit Claim Form', 'Dependent Identity Card', 'Leave Travel Concession Bill', 'Medical Reimbursement Claim Form', 'Travelling Allowance Bill', 'Dearness Allowance Document', 'House Rent Allowance Receipt', 'Leave Encashment Statement', 'Voluntary Retirement Scheme Offer Document', 'Notice Pay Recovery Receipt', 'Full and Final Settlement Statement', 'Universal Land Parcel Identification Number', 'Khatauni', 'Khasra', 'Record of Rights', 'Sale Deed', 'Lease Deed', 'Rent Agreement', 'Sub-letting Agreement', 'Commercial Lease Deed', 'Mutation Certificate', 'Property Tax Receipt', 'Patta Document', 'Search Report', 'Property Assessment Order', 'Building Plan Approval Certificate', 'Occupancy Certificate', 'Completion Certificate', 'Commencement Certificate', 'Real Estate Regulatory Authority Registration Certificate', 'Allotment Letter', 'Builder-Buyer Agreement', 'Possession Letter', 'Society No Dues Certificate', 'Cooperative Housing Society Share Certificate', 'Non-Agricultural Order', 'Land Conversion Certificate', 'Rent Receipt', 'Life Insurance Policy Document', 'Health Insurance Policy Document', 'Motor Vehicle Insurance Policy', 'Two Wheeler Insurance', 'Commercial Vehicle Insurance', 'Marine Insurance Policy', 'Transit Insurance Policy', 'Burglary Insurance Policy', 'Fire Insurance Policy', 'Fidelity Guarantee Insurance Policy', 'Directors and Officers Liability Insurance', 'Professional Indemnity Insurance', 'Public Liability Insurance', 'Workmen Compensation Insurance Policy', 'Group Term Life Insurance Policy', 'Group Health Insurance Policy', 'Travel Insurance Policy', 'Medical Insurance Premium Receipt', 'Electricity Bill', 'Water Bill', 'Piped Gas Bill', 'Liquefied Petroleum Gas Passbook', 'Landline Telephone Bill', 'Broadband Internet Bill', 'Postpaid Mobile Bill', 'Municipal Corporation Tax Receipt', 'Vehicle Registration Certificate', 'Pollution Under Control Certificate', 'Vehicle Fitness Certificate', 'Commercial Vehicle Permit', 'Learner Driving License', 'International Driving Permit', 'FASTag Registration Document', 'Toll Tax Receipt', 'Traffic Challan Document', 'Notice of Transfer of Ownership of Motor Vehicle', 'Report of Transfer of Ownership of Motor Vehicle', 'Application for Transfer of Ownership in case of Death', 'Application for Making Entry of an Agreement of Hire Purchase', 'Notice of Termination of an Agreement of Hire Purchase', 'Vehicle Sale Receipt', 'Surveyor Motor Claim Report', 'Total Loss Settlement Letter', 'Salvage Value Document', 'Udyam Registration Certificate', 'Goods and Services Tax Registration Certificate', 'Certificate of Incorporation', 'Memorandum of Association', 'Articles of Association', 'Partnership Deed', 'Limited Liability Partnership Agreement', 'Trade License', 'Food Safety and Standards Authority of India License', 'Import Export Code Certificate', 'Fire No Objection Certificate', 'State Pollution Control Board NOC', 'Shops and Establishments Act Registration Certificate', 'Customs Duty Receipt', 'Bill of Lading', 'Air Waybill', 'Letter of Credit', 'Bank Guarantee Document', 'Promissory Note', 'Bill of Exchange', 'Auditor Report', 'Balance Sheet Document', 'Profit and Loss Statement', 'Cash Flow Statement', 'Statement of Changes in Equity', 'Directors Report', 'Annual Return', 'Financial Statement', 'Director Identification Number Allotment Letter', 'Cryptographic Signature Token Document', 'Chartered Accountant Certificate', 'Company Secretary Certificate', 'Cost Accountant Certificate', 'Net Worth Certificate', 'Solvency Certificate', 'Valuation Report', 'Actuarial Valuation Report', 'Startup India Recognition Certificate', 'DPIIT Registration Document', 'MSME Samadhaan Filing Document', 'Kisan Credit Card Passbook', 'Soil Health Card', 'Crop Insurance Policy', 'Agricultural Land Passbook', 'Education Loan Sanction Letter', 'Education Loan Disbursement Letter', 'Home Loan Sanction Letter', 'Home Loan Agreement', 'Personal Loan Agreement', 'Vehicle Loan Agreement', 'Business Loan Agreement', 'Overdraft Facility Agreement', 'Cash Credit Limit Sanction Letter', 'Loan Pre-closure Statement', 'Loan Closure No Objection Certificate', 'Amortization Schedule', 'CIBIL Credit Report', 'Experian Credit Report', 'Equifax Credit Report', 'CRIF High Mark Credit Report', 'Home Loan Interest Certificate', 'Education Loan Interest Certificate', 'Affidavit', 'Power of Attorney', 'Will Document', 'Gift Deed', 'Relinquishment Deed', 'Trust Deed', 'Indemnity Bond', 'Surety Bond', 'Bail Bond', 'Court Summons', 'Court Judgment Document', 'Decree Certificate', 'Caveat Petition', 'Habeas Corpus Petition', 'Notarized Document', 'Stamp Paper', 'Judicial Stamp Paper', 'Franked Document', 'Non-compete Agreement', 'Divorce Decree Document', 'Separation Agreement', 'Maintenance Order', 'Adoption Deed', 'Guardianship Certificate', 'Custody Order', 'Name Change Gazette Notification', 'Gender Change Gazette Notification', 'Religion Change Document', 'Signature Verification Certificate', 'Photograph Verification Certificate', 'Legal Notice', 'Reply to Legal Notice', 'Cease and Desist Notice', 'Arbitral Award Document', 'Mediation Settlement Agreement', 'Lok Adalat Award', 'FIR Cancellation Report', 'Charge Sheet Document', 'Autopsy Report', 'Police Clearance Certificate', 'First Information Report', 'Motor Accident Claims Tribunal Petition', 'Consumer Court Complaint Document', 'Joint Venture Agreement', 'Memorandum of Understanding', 'Service Level Agreement', 'Vendor EmpEmpanelment Letter', 'Tender Document', 'Earnest Money Deposit Receipt', 'Performance Bank Guarantee', 'Letter of Intent', 'Work Order', 'Measurement Book Document', 'Running Account Bill', 'Completion Report', 'Defect Liability Certificate', 'Handing Over Certificate', 'Wayleave Agreement', 'Right of Way Document', 'Easement Right Document', 'Hypothecation Agreement', 'Pledge Agreement', 'Mortgage Deed', 'Deed of Assignment', 'Conveyance Deed', 'Exchange Deed', 'Partition Deed', 'Family Settlement Agreement', 'Surrender Deed', 'Rectification Deed', 'Cancellation Deed', 'Revocation of Power of Attorney', 'Sub-Lease Agreement', 'Leave and License Agreement', 'Rent Control Act Registration', 'Paying Guest Agreement', 'Hostel Accommodation Allotment Letter', 'Arms License', 'Liquor Permit', 'Broadcast License', 'Amateur Radio License', 'Drone Registration Certificate', 'Pilot License', 'Seafarer Identification Document', 'Merchant Navy Continuous Discharge Certificate', 'Emigration Check Required Document', 'Emigration Clearance Document', 'Pravasi Bharatiya Bima Yojana Policy', 'Yellow Fever Vaccination Certificate', 'Polio Vaccination Certificate', 'International Certificate of Vaccination', 'Export Promotion Capital Goods Authorization', 'Advance Authorization Certificate', 'Duty Free Import Authorization', 'Certificate of Origin', 'Phytosanitary Certificate', 'Health Certificate for Export', 'Directorate General of Foreign Trade Certificate', 'Registration Cum Membership Certificate', 'ISO Certification Document', 'BIS Hallmark Certificate', 'Agmark Certificate', 'ISI Mark Certificate', 'Trademark Registration Certificate', 'Patent Certificate', 'Copyright Registration Certificate', 'Geographical Indication Certificate', 'Design Registration Certificate', 'Khadi Mark Certificate', 'Handloom Mark Certificate', 'Silk Mark Certificate', 'Trademark Search Report', 'Copyright Assignment Deed', 'Patent Licensing Agreement', 'Franchise Agreement', 'Professional Tax Enrolment Certificate', 'Professional Tax Registration Certificate', 'Municipal Trade License', 'Contract Labour License', 'Provident Fund Registration Certificate', 'ESIC Registration Certificate', 'Factory License', 'Boiler Registration Certificate', 'Lift Inspector License', 'Weighing and Measuring Instruments Stamping Certificate', 'Entertainment Tax Registration', 'Luxury Tax Registration', 'Cinema License', 'Hotel License', 'Restaurant License', 'Liquor Serving License', 'Private Security Agency Regulation Act License', 'Telecom Regulatory Authority of India Registration', 'Other Service Provider License', 'Internet Service Provider License', 'Virtual Network Operator License', 'Authorized Dealer License', 'Money Changer License', 'Non-Banking Financial Company Registration Certificate', 'Microfinance Institution Registration', 'Chit Fund Registration Certificate', 'Nidhi Company Registration', 'Section 8 Company License', 'NGO Darpan Registration Document', 'Foreign Contribution Regulation Act Registration', 'Society Registration Certificate', 'Trust Registration Certificate', 'Section 12A Registration Certificate', 'Section 80G Registration Certificate', 'Corporate Social Responsibility Project Registration', 'Securities and Exchange Board of India Registration Certificate', 'Stock Broker License', 'Depository Participant License', 'Portfolio Manager Registration', 'Investment Adviser Registration', 'Mutual Fund Distributor Certificate', 'Insurance Regulatory and Development Authority of India License', 'Insurance Agent License', 'Surveyor and Loss Assessor License', 'Third Party Administrator License', 'Custom House Agent License', 'Freight Forwarder Registration', 'Multimodal Transport Operator Registration', 'Port Trust Registration Certificate', 'Dock Entry Permit', 'Airport Entry Permit', 'Bureau of Civil Aviation Security Clearance', 'Transit Way Bill', 'Lorry Receipt', 'Goods Receipt Note', 'Delivery Challan', 'Purchase Order', 'Proforma Invoice', 'Commercial Invoice', 'Debit Note', 'Credit Note', 'Receipt Voucher', 'Payment Voucher', 'Journal Voucher', 'Petty Cash Voucher', 'Boarding Pass', 'Flight Ticket', 'Train Ticket', 'Bus Ticket', 'Hotel Booking Confirmation', 'Forex Purchase Receipt', 'Customs Declaration Form', 'Police Verification Report', 'Background Verification Report', 'Credit Reference Letter', 'Income Proof Document', 'Address Proof Document', 'Identity Proof Document', 'Age Proof Document', 'Relationship Proof Document', 'Survival Certificate', 'Life Certificate', 'Unmarried Certificate', 'Bachelorhood Certificate', 'Trade Test Certificate', 'Proficiency Certificate', 'TPA ID Card', 'Cashless Pre-Authorization Form', 'Cashless Approval Letter', 'Cashless Denial Letter', 'Claim Settlement Letter', 'Claim Denial Letter', 'TPA Query Letter', 'Explanation of Benefits', 'Detailed Hospital Bill', 'Final Hospital Bill', 'Breakup of Hospital Bill', 'Implant Invoice', 'Surgeon Certificate', 'Treating Doctor Certificate')

ALL_CATEGORIES = CATEGORIES + tuple(c for c in INDIAN_CATEGORIES if c not in CATEGORIES)
FALLBACK = CATEGORIES[-1]
MAX_BYTES = 20 * 1024 * 1024
SUPPORTED = ('.pdf', '.png', '.jpg', '.jpeg', '.webp', '.docx', '.xlsx', '.doc', '.xls', '.pptx', '.ppt', '.txt')

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
            raise ValueError(f'Unable to parse PDF data: {exc}')
        try:
            if len(pdf) == 0: raise ValueError('Empty PDF')
            page = pdf[0]
            try:
                bitmap = page.render(scale=1.5)
                img = bitmap.to_pil()
            finally: page.close()
        finally: pdf.close()
    elif filename.lower().endswith(('.docx', '.xlsx', '.doc', '.xls', '.pptx', '.ppt', '.txt')):
        from PIL import ImageDraw
        from .office_extractors import extract_office_text
        extracted = ""
        try:
            extracted = extract_office_text(filename, data)
        except Exception:
            pass
        img = Image.new('RGB', (800, 1000), color=(250, 250, 252))
        draw = ImageDraw.Draw(img)
        ext = filename.rsplit('.', 1)[-1].upper() if '.' in filename else 'DOC'
        draw.rectangle([(0, 0), (800, 70)], fill=(37, 99, 235))
        draw.text((20, 24), f"[{ext}]  {filename}", fill=(255, 255, 255))
        preview_text = extracted[:1500] if extracted else "(No text content extracted)"
        import textwrap
        lines = []
        for line in preview_text.splitlines()[:35]:
            lines.extend(textwrap.wrap(line, width=70)[:3])
        draw.text((25, 90), "\n".join(lines[:35]), fill=(30, 41, 59))
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
            raise ValueError(f'Invalid category: {category}')
        # Accept descriptive category directly
        pass
    specifics = raw.get('specifics', '')
    if not isinstance(specifics, str):
        specifics = str(specifics)
    return category, specifics[:2000]

def parse(text: str) -> dict:
    cleaned = re.sub(r'```(?:json)?', '', text).strip()
    start = cleaned.find('{')
    end = cleaned.rfind('}')
    if start == -1 or end == -1 or end <= start:
        raise ValueError('No JSON structure found')
    try:
        data = json.loads(cleaned[start:end+1])
    except Exception as exc:
        raise ValueError(f'JSON parsing failed: {exc}')
    return data

def classify(filename: str, data: bytes, key: str, model: str, session) -> dict:
    url = f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}'
    t0 = time.time()
    try:
        image_bytes = render(data, filename)
    except Exception as exc:
        return asdict(Result(Filename=filename, Specifics=str(exc), Status='INPUT_ERROR', Seconds=round(time.time()-t0, 3)))
    import base64
    b64 = base64.b64encode(image_bytes).decode('ascii')
    payload = {
        'contents': [{
            'parts': [
                {'text': 'Which kind of document is it, what could be the logical category for this document, propose a short meaningful name for it and why do you think it belongs to the category which you have suggested?\n\nReturn JSON schema: {"category": "<category>", "specifics": "<reasoning>"}'},
                {'inline_data': {'mime_type': 'image/jpeg', 'data': b64}}
            ]
        }]
    }
    try:
        resp = session.post(url, json=payload, timeout=30)
    except Exception as exc:
        return asdict(Result(Filename=filename, Specifics=str(exc), Status='NETWORK_ERROR', Seconds=round(time.time()-t0, 3)))
    if resp.status_code == 429:
        return asdict(Result(Filename=filename, Specifics='API rate limit or quota exceeded (HTTP 429)', Status='API_ERROR', Seconds=round(time.time()-t0, 3)))
    if resp.status_code != 200:
        return asdict(Result(Filename=filename, Specifics=f'HTTP error {resp.status_code}', Status='API_ERROR', Seconds=round(time.time()-t0, 3)))
    try:
        body = resp.json()
        raw_text = body['candidates'][0]['content']['parts'][0]['text']
        parsed = parse(raw_text)
        cat, specifics = validate(parsed)
        status = 'UNCATEGORIZABLE' if cat == FALLBACK else 'OK'
        return asdict(Result(Filename=filename, Category=cat, Specifics=specifics, Status=status, Seconds=round(time.time()-t0, 3)))
    except Exception as exc:
        return asdict(Result(Filename=filename, Specifics=str(exc), Status='INVALID_RESPONSE', Seconds=round(time.time()-t0, 3)))
