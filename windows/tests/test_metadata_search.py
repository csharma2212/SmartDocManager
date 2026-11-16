import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from smartdoc.metadata import build_document_metadata, matches_query, get_search_suggestions

sample_text = """
Dr. Lal PathLabs Ltd.
National Reference Laboratory, Rohini, New Delhi
Patient: Rahul Sharma  Age: 34 Y / Male  Date: 12/07/2024
DEPARTMENT OF BIOCHEMISTRY
TEST: LFT (LIVER FUNCTION TEST)
Total Bilirubin: 0.9 mg/dL (0.2 - 1.2)
Direct Bilirubin: 0.2 mg/dL (0.0 - 0.3)
SGOT / AST: 28 U/L (< 40)
SGPT / ALT: 32 U/L (< 45)
"""

def test_metadata_extraction_and_search():
    meta = build_document_metadata(sample_text.encode('utf-8'), 'raw_scan_001.pdf', 'Medical Diagnostic / Lab Report', 'Medical_Lab_Report_2024-07-12.pdf')
    assert 'Dr. Lal PathLabs' in meta['labs']
    assert 'LFT (Liver Function Test)' in meta['tests']
    assert any('12/07/2024' in d for d in meta['dates'])

    rec = {
        'path': 'C:/docs/raw_scan_001.pdf',
        'name': 'raw_scan_001.pdf',
        'virtual_name': 'Medical_Lab_Report_2024-07-12.pdf',
        'proposed': 'Medical_Lab_Report_2024-07-12.pdf',
        'category': 'Medical Diagnostic / Lab Report',
        'metadata': meta
    }

    # Matches user query examples
    assert matches_query(rec, 'lalpath') is True
    assert matches_query(rec, 'LFT') is True
    assert matches_query(rec, 'bilirubin') is True
    assert matches_query(rec, 'sgot') is True
    assert matches_query(rec, 'hdfc') is False

    # Guessing suggestions
    sugg_lal = get_search_suggestions([rec], 'lal')
    assert any('Lal' in s for s in sugg_lal)

    sugg_lf = get_search_suggestions([rec], 'lf')
    assert any('LFT' in s for s in sugg_lf)

def test_multi_document_filtering_lalpath_lft():
    doc1_text = "Dr. Lal PathLabs National Laboratory Patient: Rahul Sharma Test: LFT (Liver Function Test) Bilirubin: 0.8"
    doc2_text = "Apollo Diagnostics Patient: Priya Verma Test: CBC (Complete Blood Count) Hemoglobin: 13.5"
    doc3_text = "HDFC Bank Account Statement Account No: 501002348912 Period: 01-Sep-2024 to 30-Sep-2024"

    meta1 = build_document_metadata(doc1_text.encode('utf-8'), 'scan_001.pdf', 'Medical Diagnostic / Lab Report', 'Medical_Lab_Report_2024-07-12.pdf')
    meta2 = build_document_metadata(doc2_text.encode('utf-8'), 'scan_002.pdf', 'Medical Diagnostic / Lab Report', 'Medical_Lab_Report_2024-08-01.pdf')
    meta3 = build_document_metadata(doc3_text.encode('utf-8'), 'stmt_003.pdf', 'Bank Statement', 'HDFC_Statement_2024-09.pdf')

    r1 = {'path': '/docs/scan_001.pdf', 'name': 'scan_001.pdf', 'virtual_name': 'Medical_Lab_Report_2024-07-12.pdf', 'proposed': 'Medical_Lab_Report_2024-07-12.pdf', 'category': 'Medical Diagnostic / Lab Report', 'metadata': meta1}
    r2 = {'path': '/docs/scan_002.pdf', 'name': 'scan_002.pdf', 'virtual_name': 'Medical_Lab_Report_2024-08-01.pdf', 'proposed': 'Medical_Lab_Report_2024-08-01.pdf', 'category': 'Medical Diagnostic / Lab Report', 'metadata': meta2}
    r3 = {'path': '/docs/stmt_003.pdf', 'name': 'stmt_003.pdf', 'virtual_name': 'HDFC_Statement_2024-09.pdf', 'proposed': 'HDFC_Statement_2024-09.pdf', 'category': 'Bank Statement', 'metadata': meta3}

    library = [r1, r2, r3]

    # 1. Searching for lalpath: only doc1 matches
    res_lalpath = [r for r in library if matches_query(r, 'lalpath')]
    assert len(res_lalpath) == 1
    assert res_lalpath[0]['path'] == '/docs/scan_001.pdf'

    # 2. Searching for LFT: only doc1 matches
    res_lft = [r for r in library if matches_query(r, 'LFT')]
    assert len(res_lft) == 1
    assert res_lft[0]['path'] == '/docs/scan_001.pdf'

    # 3. Searching for apollo: only doc2 matches
    res_apollo = [r for r in library if matches_query(r, 'apollo')]
    assert len(res_apollo) == 1
    assert res_apollo[0]['path'] == '/docs/scan_002.pdf'

    # 4. Searching for hdfc: only doc3 matches
    res_hdfc = [r for r in library if matches_query(r, 'hdfc')]
    assert len(res_hdfc) == 1
    assert res_hdfc[0]['path'] == '/docs/stmt_003.pdf'

    # 5. Typeahead suggestions guessing what user is typing
    sugg_lal = get_search_suggestions(library, 'lal')
    assert any('Lal' in s for s in sugg_lal)

    sugg_lf = get_search_suggestions(library, 'lf')
    assert any('LFT' in s for s in sugg_lf)

    sugg_apo = get_search_suggestions(library, 'apo')
    assert any('Apollo' in s for s in sugg_apo)

def test_structured_entities_extraction_and_query():
    extra_meta = {
        'doctor_name': 'Dr. K. S. Sharma',
        'patient_name': 'Meera Kapoor',
        'hospital_name': 'Max Super Speciality Hospital',
        'lessee_name': 'John Doe',
        'lessor_name': 'Jane Smith',
        'issued_date': '15 Nov 2026'
    }
    meta = build_document_metadata(b"Prescription details", "prescription.pdf", "Medical", "MED_Rx_15Nov26.pdf", extra_metadata=extra_meta)
    assert meta['entities']['doctor_name'] == 'Dr. K. S. Sharma'
    assert meta['entities']['patient_name'] == 'Meera Kapoor'
    
    rec = {
        'path': 'C:/docs/prescription.pdf',
        'name': 'prescription.pdf',
        'virtual_name': 'MED_Rx_15Nov26.pdf',
        'proposed': 'MED_Rx_15Nov26.pdf',
        'category': 'Medical',
        'metadata': meta
    }
    assert matches_query(rec, 'Meera') is True
    assert matches_query(rec, 'Sharma') is True
    assert matches_query(rec, 'Max') is True
    assert matches_query(rec, 'NonExistent') is False

    sugg = get_search_suggestions([rec], 'meer')
    assert 'Meera Kapoor' in sugg

