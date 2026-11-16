import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pytest
from smartdoc.indian_taxonomy import (
    CATEGORY_CODES,
    resolve_category_code,
    format_dd_mmm_yy,
    abbreviate_doc_type,
    generate_compliant_proposed_name,
    INDIAN_CATEGORIES
)

def test_resolve_category_code_all_13_domains():
    expected = {
        'Identity': 'IDN',
        'Education': 'EDU',
        'Medical': 'MED',
        'Finance': 'FIN',
        'Work': 'WRK',
        'Property': 'PRO',
        'Business': 'BIZ',
        'Legal': 'LAW',
        'Transport': 'TPT',
        'Travel': 'TRV',
        'Insurance': 'INS',
        'Utilities': 'UTL',
        'Agriculture': 'AGR'
    }
    for cat, code in expected.items():
        assert resolve_category_code(cat) == code
        assert resolve_category_code(cat.lower()) == code

    # Fallbacks and heuristics
    assert resolve_category_code('Tax Document') == 'FIN'
    assert resolve_category_code('Bank Statement') == 'FIN'
    assert resolve_category_code('Medical Diagnostic / Lab Report') == 'MED'
    assert resolve_category_code('Something Totally Unknown') == 'OTH'

def test_format_dd_mmm_yy():
    assert format_dd_mmm_yy('15-Nov-2026') == '15Nov26'
    assert format_dd_mmm_yy('2026-11-15') == '15Nov26'
    assert format_dd_mmm_yy('15/11/2026') == '15Nov26'
    assert format_dd_mmm_yy('Nov 15, 2026') == '15Nov26'
    assert format_dd_mmm_yy('01-Jan-2025') == '01Jan25'
    assert format_dd_mmm_yy('31-Dec-2024') == '31Dec24'
    fallback = format_dd_mmm_yy(None)
    assert len(fallback) == 7

def test_abbreviate_doc_type():
    assert abbreviate_doc_type('Income Tax Form 16', 24) == 'IncomeTaxForm16'
    assert abbreviate_doc_type('Permanent Account Number Card', 24) == 'PANCard'
    assert abbreviate_doc_type('Aadhaar Card', 24) == 'AadhaarCard'
    assert abbreviate_doc_type('Hospital Discharge Summary', 24) == 'DischSumm'
    assert abbreviate_doc_type('Rent Agreement', 24) == 'RentAgrmt'

def test_generate_compliant_proposed_name():
    name1 = generate_compliant_proposed_name('Finance', 'Income Tax Form 16', '15-Nov-2026', 'sample.pdf')
    assert name1 == 'FIN_IncomeTaxForm16_15Nov26.pdf'
    assert len(name1) <= 40

    name2 = generate_compliant_proposed_name('Identity', 'Permanent Account Number Card', '2026-11-15', 'pan.pdf')
    assert name2 == 'IDN_PANCard_15Nov26.pdf'
    assert len(name2) <= 40

    long_type = 'Super Extremely Long Non-Agricultural Land Conversion Approval Certificate And Order'
    long_file = generate_compliant_proposed_name('Property', long_type, '15-Nov-2026', 'document.pdf')
    assert len(long_file) <= 40, f"Expected <= 40, got {len(long_file)}: {long_file}"
    assert long_file.startswith('PRO_')
    assert long_file.endswith('_15Nov26.pdf')

    jpeg_file = generate_compliant_proposed_name('Medical', 'Diagnostic Laboratory Report', '15-Nov-2026', 'scan.jpeg')
    assert len(jpeg_file) <= 40, f"Expected <= 40, got {len(jpeg_file)}: {jpeg_file}"
    assert jpeg_file.startswith('MED_')
    assert jpeg_file.endswith('_15Nov26.jpeg')

def test_all_519_categories_strictly_stay_under_40_chars():
    for cat in INDIAN_CATEGORIES:
        fname = generate_compliant_proposed_name(cat, cat, '15-Nov-2026', 'doc.pdf')
        assert len(fname) <= 40, f"Category '{cat}' produced '{fname}' with length {len(fname)} > 40"
        assert fname.count('_') == 2
        assert fname.endswith('_15Nov26.pdf')
