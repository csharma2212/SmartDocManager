import io
import zipfile
import pytest
from smartdoc.office_extractors import extract_office_text, OFFICE_EXTENSIONS


def test_txt_extraction():
    data = "Income Tax Return Verification Form 16 AY 2026-27".encode("utf-8")
    result = extract_office_text("sample.txt", data)
    assert "Income Tax Return" in result
    assert "Form 16" in result


def test_docx_extraction():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(
            "word/document.xml",
            "<w:document><w:p><w:r><w:t>Aadhaar Card Enrollment Slip 1234 5678</w:t></w:r></w:p></w:document>",
        )
    result = extract_office_text("sample.docx", buf.getvalue())
    assert "Aadhaar Card" in result
    assert "1234 5678" in result


def test_pptx_extraction():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(
            "ppt/slides/slide1.xml",
            "<p:spTree><a:p><a:r><a:t>Project Proposal Presentation</a:t></a:r></a:p></p:spTree>",
        )
    result = extract_office_text("deck.pptx", buf.getvalue())
    assert "Project Proposal" in result


def test_xlsx_extraction():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr(
            "xl/sharedStrings.xml",
            "<sst><si><t>Salary Statement October 2026</t></si></sst>",
        )
    result = extract_office_text("sheet.xlsx", buf.getvalue())
    assert "Salary Statement" in result


def test_legacy_binary_extraction():
    # Legacy binary with embedded ASCII
    bin_data = b"\xd0\xcf\x11\xe0\xa1\xb1\x1a\xe1\x00\x00Rent Agreement Flat 101 Mumbai\x00\x00"
    result = extract_office_text("contract.doc", bin_data)
    assert "Rent Agreement" in result


def test_core_render_and_supported():
    from smartdoc.core import render, SUPPORTED
    for ext in ('.docx', '.xlsx', '.doc', '.xls', '.pptx', '.ppt', '.txt'):
        assert ext in SUPPORTED
    # Render docx
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("word/document.xml", "<w:p><w:t>Medical Prescription Dr Sharma</w:t></w:p>")
    rendered = render(buf.getvalue(), "prescription.docx")
    assert rendered[:2] == b'\xff\xd8'  # Valid JPEG
    # Render txt
    txt_rendered = render(b"Electricity Bill November 2026", "bill.txt")
    assert txt_rendered[:2] == b'\xff\xd8'


def test_library_doc_kind():
    from smartdoc.library import doc_kind
    assert doc_kind(".docx") == "Word"
    assert doc_kind(".doc") == "Word"
    assert doc_kind(".xlsx") == "Excel"
    assert doc_kind(".xls") == "Excel"
    assert doc_kind(".pptx") == "PowerPoint"
    assert doc_kind(".ppt") == "PowerPoint"
    assert doc_kind(".txt") == "Text"
    assert doc_kind(".pdf") == "PDF"
    assert doc_kind(".png") == "Image"


def test_metadata_and_hosted_extraction():
    from smartdoc.metadata import extract_raw_text
    from smartdoc.hosted import extract_text
    txt = b"Hospital Discharge Summary Fortis Patient John"
    assert "Discharge Summary" in extract_raw_text(txt, "summary.txt")
    assert "Discharge Summary" in extract_text("summary.txt", txt)

