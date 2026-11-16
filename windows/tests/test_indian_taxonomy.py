import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import pytest
from smartdoc.indian_taxonomy import deduce_category, generate_proposed_name, classify_and_name_locally

def test_pan_card_detection():
    text = "INCOME TAX DEPARTMENT GOVT. OF INDIA PERMANENT ACCOUNT NUMBER ABCDE1234F DATE OF BIRTH 12/04/1990"
    cat, name = classify_and_name_locally(text, "scan.pdf")
    assert cat in ("Permanent Account Number Card", "PAN Card")
    assert name.startswith("IDN_") or name.startswith("FIN_")
    assert "PANCard" in name or "PanCard" in name
    assert len(name) <= 40
    assert name.endswith(".pdf")

def test_aadhaar_card_detection():
    text = "Unique Identification Authority of India Mera Aadhaar Meri Pehchan 1234 5678 9012 15-08-2022"
    cat, name = classify_and_name_locally(text, "id.jpg")
    assert cat == "Aadhaar Card"
    assert name.startswith("IDN_")
    assert "AadhaarCard" in name
    assert len(name) <= 40
    assert name.endswith(".jpg")

def test_form16_detection():
    text = "Certificate under section 203 of the Income-tax Act, 1961 Form No. 16 AY 2024-25 Part A TDS"
    cat, name = classify_and_name_locally(text, "doc.pdf")
    assert cat in ("Form 16", "Form 16 / TDS Certificate")
    assert name.startswith("FIN_")
    assert "Form16" in name
    assert len(name) <= 40
    assert name.endswith(".pdf")

def test_bank_statement_detection():
    text = "State Bank of India Statement of Account IFSC Code SBIN0001234 Oct 2024 Account Number 1234567890"
    cat, name = classify_and_name_locally(text, "sbi.pdf")
    assert cat in ("Savings Account Statement", "Bank Statement")
    assert name.startswith("FIN_")
    assert len(name) <= 40
    assert name.endswith(".pdf")

def test_electricity_bill_detection():
    text = "Tata Power Electricity Bill Consumer No 900012345 Units Consumed 250 kWh 2024-07-10"
    cat, name = classify_and_name_locally(text, "bill.png")
    assert cat == "Electricity Bill"
    assert name.startswith("UTL_")
    assert "ElecBill" in name
    assert len(name) <= 40
    assert name.endswith(".png")

def test_other_document_fallback_naming():
    cat, name = classify_and_name_locally("", "random_contract_page.pdf")
    assert cat == "Other Document"
    assert name.startswith("OTH_")
    assert len(name) <= 40
    assert name.endswith(".pdf")

def test_filename_never_evaluates_category():
    # User requirement: Filename must NOT be considered while evaluating category,
    # as the user may have previously given an incorrect filename.
    cat, name = classify_and_name_locally("", "my_sbi_statement_oct.pdf")
    assert cat == "Other Document"
    assert name.startswith("OTH_")
    assert len(name) <= 40
    assert name.endswith(".pdf")

def test_maximum_identifier_match_resolves_conflict():
    # User requirement: When 2 different kinds of documents share similar identifiers,
    # the category with the maximum number of matched identifiers wins.
    # Here, document mentions PAN card once, but has multiple Salary Slip identifiers.
    text = (
        "ABC Technologies Pvt Ltd Salary Slip for October 2024. "
        "Employee Code: EMP9901. Basic Salary: 45000, HRA: 20000, "
        "Gross Pay: 75000, Deductions: 5000, Net Pay: 70000. "
        "Employee PAN Card: ABCDE1234F."
    )
    cat, name, evidence = classify_and_name_locally(text, "arbitrary_doc.pdf", return_details=True)
    assert cat in ("Employee Salary Slip", "Salary Slip / Payslip")
    assert name.startswith("WRK_") or name.startswith("FIN_")
    assert "SalSlip" in name or "SalarySlip" in name or "EmpSalSlip" in name
    assert len(name) <= 40

def test_all_categories_generate_non_empty_name():
    from smartdoc.indian_taxonomy import INDIAN_CATEGORIES
    for cat in INDIAN_CATEGORIES:
        name = generate_proposed_name(cat, "", "test_file.pdf")
        assert name and name.endswith(".pdf")
        assert " " not in name
        assert len(name) <= 40

def test_medical_prescription_detection():
    text = "Dr. Mehta Rx Tab Azithral 500mg Once daily 05/09/2024 Advice bed rest patient age 32"
    cat, name, evidence = classify_and_name_locally(text, "prescription.pdf", return_details=True)
    assert cat == "Medical Prescription"
    assert name.startswith("MED_")
    assert "MedicalRx" in name or "Prescription" in name or "Rx" in name
    assert "05Sep24" in name
    assert len(name) <= 40
    assert "Matched Keywords" in evidence

def test_driving_licence_sarathi_detection():
    text = "SARATHI PARIVAHAN RTO DELHI DL-0420110012345 S/O RAMESH 15/04/1988 LMV"
    cat, name, evidence = classify_and_name_locally(text, "dl_scan.pdf", return_details=True)
    assert cat in ("Driving License", "Driving Licence")
    assert name.startswith("TPT_") or name.startswith("IDN_")
    assert "DrivingLic" in name
    assert len(name) <= 40
    assert "parivahan" in evidence.lower() or "sarathi" in evidence.lower()

def test_epf_form13_detection():
    text = "Employees' Provident Fund Scheme, 1952 Form 13 (Revised) Application for transfer of EPF account UAN: 100123456789"
    cat, name = classify_and_name_locally(text, "form13.pdf")
    assert cat in ("PF Transfer Claim Form", "Employees Provident Fund Statement", "EPF / Provident Fund (Form 13 / UAN)")
    assert name.startswith("WRK_") or name.startswith("FIN_") or name.startswith("IDN_")
    assert len(name) <= 40

def test_lease_draft_detection():
    text = "LEASE AGREEMENT (DRAFT) between LESSOR and LESSEE for premises at Flat 402, monthly rent Rs. 25000, security deposit Rs. 50000 1st day of October 2024"
    cat, name, evidence = classify_and_name_locally(text, "lease_draft.pdf", return_details=True)
    assert cat in ("Lease Deed", "Rent Agreement", "Rental / Lease Agreement")
    assert name.startswith("PRO_") or name.startswith("LAW_")
    assert "01Oct24" in name
    assert len(name) <= 40
    assert "Draft Document" in evidence
