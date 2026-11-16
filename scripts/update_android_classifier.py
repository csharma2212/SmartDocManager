"""Update Android Classifier.kt with the 519 standard Indian document categories.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TAXONOMY_JSON = ROOT / "shared-spec" / "taxonomy.json"
CLASSIFIER_KT = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "smartdoc" / "poc" / "Classifier.kt"

with open(TAXONOMY_JSON, "r", encoding="utf-8") as f:
    ALL_519_CATEGORIES = json.load(f)["categories"]

print(f"Loaded {len(ALL_519_CATEGORIES)} categories for Android.")

# Build Kotlin set representation
categories_kotlin_code = ",\n        ".join(f'"{c}"' for c in ALL_519_CATEGORIES)

with open(CLASSIFIER_KT, "r", encoding="utf-8") as f:
    content = f.read()

# Replace indianCategories block
pattern_indian = r"val indianCategories = linkedSetOf\([\s\S]*?\)\s*val allCategories"
replacement_indian = f"""val indianCategories = linkedSetOf(
        {categories_kotlin_code}
    )
    val allCategories"""

content, count1 = re.subn(pattern_indian, replacement_indian, content)
print(f"Substituted indianCategories: {count1}")

# Update cleanCategoryPrefix to include new category names
clean_prefix_old = r'fun cleanCategoryPrefix\(category: String\): String \{\s*return when \(category\) \{'
clean_prefix_new = """fun cleanCategoryPrefix(category: String): String {
        return when (category) {
            "Permanent Account Number Card", "PAN Card" -> "PAN_Card"
            "Voter Identity Card", "Voter ID Card" -> "Voter_ID"
            "Driving License", "Driving Licence" -> "Driving_License"
            "Aadhaar Card" -> "Aadhaar_Card"
            "Passport" -> "Passport"
            "Form 16" -> "Form_16"
            "Form 16A" -> "Form_16A"
            "Form 26AS" -> "Form_26AS"
            "Employee Salary Slip", "Salary Slip / Payslip" -> "Salary_Slip"
            "Medical Prescription" -> "Prescription"
            "Diagnostic Laboratory Report" -> "Medical_Report"
            "Blood Test Report" -> "Blood_Test_Report"
            "Pathology Report" -> "Pathology_Report"
            "Electricity Bill" -> "Electricity_Bill"
            "Water Bill" -> "Water_Bill"
            "Piped Gas Bill" -> "Gas_Bill"
            "Liquefied Petroleum Gas Passbook" -> "LPG_Passbook"
            "Rent Agreement" -> "Rent_Agreement"
            "Lease Deed" -> "Lease_Deed"
            "Savings Account Statement" -> "Savings_Statement"
            "Current Account Statement" -> "Current_Statement"
            "Bank Passbook" -> "Bank_Passbook"
            "Credit Card Statement" -> "Credit_Card_Statement"
            "Universal Account Number Card" -> "UAN_Card"
            "PF Transfer Claim Form" -> "PF_Transfer_Claim"
            "Employees Provident Fund Statement" -> "EPF_Statement"
            "Vehicle Registration Certificate" -> "Vehicle_RC"
            "Motor Vehicle Insurance Policy" -> "Motor_Insurance" """

content, count2 = re.subn(clean_prefix_old, clean_prefix_new, content)
print(f"Substituted cleanCategoryPrefix: {count2}")

# Update normalizeCategory
normalize_old = r'val lower = trimmed\.lowercase\(\)\s*return when \{'
normalize_new = """val lower = trimmed.lowercase()
        return when {
            lower.contains("permanent account number") || lower.contains("pan card") || lower == "pan" -> "Permanent Account Number Card"
            lower.contains("voter") -> "Voter Identity Card"
            lower.contains("driving") && (lower.contains("licence") || lower.contains("license")) -> "Driving License"
            lower.contains("salary") || lower.contains("payslip") -> "Employee Salary Slip"
            lower.contains("rent agreement") || lower.contains("rental agreement") -> "Rent Agreement"
            lower.contains("lease") -> "Lease Deed"
            lower.contains("form 16a") -> "Form 16A"
            lower.contains("form 16") -> "Form 16"
            lower.contains("form 26") -> "Form 26AS"
            lower.contains("savings") -> "Savings Account Statement"
            lower.contains("current account") -> "Current Account Statement"
            lower.contains("passbook") -> "Bank Passbook"
            lower.contains("credit card") -> "Credit Card Statement"
            lower.contains("water bill") -> "Water Bill"
            lower.contains("gas bill") || lower.contains("lpg") -> "Liquefied Petroleum Gas Passbook"
            lower.contains("blood test") -> "Blood Test Report"
            lower.contains("pathology") -> "Pathology Report"
            lower.contains("epf") || lower.contains("provident") -> "Employees Provident Fund Statement"
            lower.contains("uan") -> "Universal Account Number Card" """

content, count3 = re.subn(normalize_old, normalize_new, content)
print(f"Substituted normalizeCategory: {count3}")

# Update categoryRules in Classifier.kt
category_rules_old = r'CategoryRule\(\s*"PAN Card",'
category_rules_new = """CategoryRule(
            "Permanent Account Number Card",
            listOf(
                \"\"\"\\bpermanent account number\\b\"\"\",
                \"\"\"\\bincome tax department\\b\"\"\",
                \"\"\"\\bpan card\\b\"\"\",
                \"\"\"\\b[a-z]{5}\\d{4}[a-z]\\b\"\"\",
                \"\"\"\\bfather'?s name\\b\"\"\"
            )
        ),
        CategoryRule(
            "PAN Card","""

dl_new = """CategoryRule(
            "Driving License",
            listOf(
                \"\"\"\\bdriving licen[sc]e\\b\"\"\",
                \"\"\"\\bdriver licen[sc]e\\b\"\"\",
                \"\"\"\\bsarathi\\b\"\"\",
                \"\"\"\\bmorth\\b\"\"\",
                \"\"\"\\blicensing authority\\b\"\"\",
                \"\"\"\\bauthoris(ation|ed) to drive\\b\"\"\",
                \"\"\"\\b(lmv|mcwg|mcwog|hgv)\\b\"\"\",
                \"\"\"\\bdl\\s*(no|number)\\b\"\"\",
                \"\"\"\\b[a-z]{2}[-\\s/]?[0-9]{2}[-\\s/]?[0-9]{4}[-\\s/]?[0-9]{7}\\b\"\"\"
            )
        ),
        CategoryRule(
            "Driving Licence","""

salary_new = """CategoryRule(
            "Employee Salary Slip",
            listOf(
                \"\"\"\\b(salary slip|payslip|pay slip)\\b\"\"\",
                \"\"\"\\bgross (pay|salary)\\b\"\"\",
                \"\"\"\\bnet (pay|salary)\\b\"\"\",
                \"\"\"\\bbasic (pay|salary)\\b\"\"\",
                \"\"\"\\bhra\\b\"\"\",
                \"\"\"\\bearnings\\b\"\"\",
                \"\"\"\\bdeductions\\b\"\"\",
                \"\"\"\\bemployee (code|id)\\b\"\"\",
                \"\"\"\\btake home pay\\b\"\"\"
            )
        ),
        CategoryRule(
            "Salary Slip / Payslip","""

content = content.replace(
    'CategoryRule(\n            "PAN Card",',
    category_rules_new
)
content = content.replace(
    'CategoryRule(\n            "Driving Licence",',
    dl_new
)
content = content.replace(
    'CategoryRule(\n            "Salary Slip / Payslip",',
    salary_new
)

with open(CLASSIFIER_KT, "w", encoding="utf-8") as f:
    f.write(content)
print(f"Updated {CLASSIFIER_KT}")
