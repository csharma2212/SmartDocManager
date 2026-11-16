package com.smartdoc.poc

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

object Classifier {
    const val FALLBACK = "uncategorizable input"
    val categories = linkedSetOf(
        "Medical Record", "Government-issued Document", "Organization-issued ID Card", "Legal Document",
        "Educational Document", "Tax Document", "Investment Statement", "Bank Statement",
        "Credit Card Statement", "Bill or Invoice", "Payment Receipt", "Insurance Document",
        "Vehicle Document", "Employment Document", "Property or Rental Document",
        "Utility or Telecom Document", "Travel Document", "Other Document", "uncategorizable input"
    )
    val indianCategories = linkedSetOf(
        "Aadhaar Card",
        "Permanent Account Number Card",
        "Voter Identity Card",
        "Passport",
        "Driving License",
        "Ration Card",
        "Birth Certificate",
        "Death Certificate",
        "Marriage Certificate",
        "Domicile Certificate",
        "Income Certificate",
        "Caste Certificate",
        "Other Backward Class Certificate",
        "Economically Weaker Section Certificate",
        "Legal Heir Certificate",
        "Encumbrance Certificate",
        "Transfer Certificate",
        "Migration Certificate",
        "Character Certificate",
        "Bonafide Certificate",
        "Overseas Citizen of India Card",
        "Person of Indian Origin Card",
        "Visa Document",
        "Residence Certificate",
        "Class 10 Marksheet",
        "Class 12 Marksheet",
        "Undergraduate Degree Certificate",
        "Postgraduate Degree Certificate",
        "Provisional Degree Certificate",
        "School Leaving Certificate",
        "Semester Marksheet",
        "Grade Card",
        "Hall Ticket",
        "Convocation Letter",
        "Academic Transcript",
        "Course Completion Certificate",
        "Extracurricular Activity Certificate",
        "Sports Merit Certificate",
        "National Cadet Corps Certificate",
        "National Service Scheme Certificate",
        "Scout and Guide Certificate",
        "Entrance Exam Admit Card",
        "Entrance Exam Scorecard",
        "Counseling Allotment Letter",
        "Seat Confirmation Letter",
        "Tuition Fee Receipt",
        "Thesis Submission Certificate",
        "PhD Degree Certificate",
        "Engineering Degree Certificate",
        "Diploma Certificate",
        "Industrial Training Institute Certificate",
        "National Skill Development Corporation Certificate",
        "Apprenticeship Certificate",
        "Internship Completion Certificate",
        "Fellowship Award Letter",
        "Scholarship Sanction Letter",
        "Research Grant Award Letter",
        "Language Proficiency Certificate",
        "IELTS Scorecard",
        "TOEFL Scorecard",
        "GRE Scorecard",
        "GMAT Scorecard",
        "Study Abroad Offer Letter",
        "Confirmation of Acceptance for Studies Letter",
        "I-20 Form",
        "DS-160 Form",
        "Schengen Visa Form",
        "Disability Certificate",
        "Ayushman Bharat Health Account Card",
        "Ayushman Card",
        "Central Government Health Scheme Card",
        "Ex-Servicemen Contributory Health Scheme Card",
        "Employees State Insurance Corporation Card",
        "Medical Fitness Certificate",
        "Hospital Discharge Summary",
        "Diagnostic Laboratory Report",
        "Medical Prescription",
        "Organ Donor Certificate",
        "Blood Group Certificate",
        "Vaccination Certificate",
        "COVID-19 Vaccination Certificate",
        "MRI Report",
        "X-Ray Report",
        "CT Scan Report",
        "Ultrasound Report",
        "Blood Test Report",
        "Pathology Report",
        "Biopsy Report",
        "Disability Medical Certificate",
        "Severe Disability Certificate",
        "Specified Disease Treatment Receipt",
        "Audiometry Report",
        "Vision Certificate",
        "Color Blindness Certificate",
        "Physical Fitness Certificate",
        "Income Tax Return Verification Form",
        "Form 16",
        "Form 16A",
        "Form 26AS",
        "Annual Information Statement",
        "Taxpayer Information Summary",
        "Form 15G",
        "Form 15H",
        "Lower Deduction Certificate",
        "Advance Tax Challan",
        "Self-Assessment Tax Challan",
        "Regular Assessment Tax Challan",
        "Wealth Tax Return",
        "Form 12BB",
        "Form 49A",
        "Form 49B",
        "Form 10E",
        "Income Tax Assessment Order",
        "Tax Deduction at Source Certificate",
        "Tax Collection at Source Certificate",
        "Bank Passbook",
        "Cheque Book",
        "Fixed Deposit Receipt",
        "Recurring Deposit Receipt",
        "Demat Account Statement",
        "Mutual Fund Account Statement",
        "Consolidated Account Statement",
        "Systematic Investment Plan Statement",
        "Capital Gains Statement",
        "Sovereign Gold Bond Certificate",
        "Employees Provident Fund Statement",
        "Public Provident Fund Passbook",
        "National Pension System Permanent Retirement Account Number Card",
        "Savings Account Statement",
        "Current Account Statement",
        "Non-Resident External Account Statement",
        "Non-Resident Ordinary Account Statement",
        "Loan Account Statement",
        "Credit Card Statement",
        "Credit Card Bill",
        "Universal Account Number Card",
        "Pension Payment Order",
        "PF Transfer Claim Form",
        "PF Withdrawal Form",
        "Form 19",
        "Form 10C",
        "Form 31",
        "Form 10D",
        "Pension Claim Form",
        "Monthly Pension Form",
        "EPS Nomination Form",
        "Gratuity Nomination Form",
        "Shram Card",
        "Mahatma Gandhi National Rural Employment Guarantee Act Job Card",
        "Employee Offer Letter",
        "Employee Appointment Letter",
        "Employee Relieving Letter",
        "Employee Experience Certificate",
        "Employee Salary Slip",
        "Employee Appraisal Letter",
        "Bonus Letter",
        "Increment Letter",
        "Arrears Statement",
        "Non-Disclosure Agreement",
        "Employment Contract",
        "Maternity Benefit Claim Form",
        "Dependent Identity Card",
        "Leave Travel Concession Bill",
        "Medical Reimbursement Claim Form",
        "Travelling Allowance Bill",
        "Dearness Allowance Document",
        "House Rent Allowance Receipt",
        "Leave Encashment Statement",
        "Voluntary Retirement Scheme Offer Document",
        "Notice Pay Recovery Receipt",
        "Full and Final Settlement Statement",
        "Universal Land Parcel Identification Number",
        "Khatauni",
        "Khasra",
        "Record of Rights",
        "Sale Deed",
        "Lease Deed",
        "Rent Agreement",
        "Sub-letting Agreement",
        "Commercial Lease Deed",
        "Mutation Certificate",
        "Property Tax Receipt",
        "Patta Document",
        "Search Report",
        "Property Assessment Order",
        "Building Plan Approval Certificate",
        "Occupancy Certificate",
        "Completion Certificate",
        "Commencement Certificate",
        "Real Estate Regulatory Authority Registration Certificate",
        "Allotment Letter",
        "Builder-Buyer Agreement",
        "Possession Letter",
        "Society No Dues Certificate",
        "Cooperative Housing Society Share Certificate",
        "Non-Agricultural Order",
        "Land Conversion Certificate",
        "Rent Receipt",
        "Life Insurance Policy Document",
        "Health Insurance Policy Document",
        "Motor Vehicle Insurance Policy",
        "Two Wheeler Insurance",
        "Commercial Vehicle Insurance",
        "Marine Insurance Policy",
        "Transit Insurance Policy",
        "Burglary Insurance Policy",
        "Fire Insurance Policy",
        "Fidelity Guarantee Insurance Policy",
        "Directors and Officers Liability Insurance",
        "Professional Indemnity Insurance",
        "Public Liability Insurance",
        "Workmen Compensation Insurance Policy",
        "Group Term Life Insurance Policy",
        "Group Health Insurance Policy",
        "Travel Insurance Policy",
        "Medical Insurance Premium Receipt",
        "Electricity Bill",
        "Water Bill",
        "Piped Gas Bill",
        "Liquefied Petroleum Gas Passbook",
        "Landline Telephone Bill",
        "Broadband Internet Bill",
        "Postpaid Mobile Bill",
        "Municipal Corporation Tax Receipt",
        "Vehicle Registration Certificate",
        "Pollution Under Control Certificate",
        "Vehicle Fitness Certificate",
        "Commercial Vehicle Permit",
        "Learner Driving License",
        "International Driving Permit",
        "FASTag Registration Document",
        "Toll Tax Receipt",
        "Traffic Challan Document",
        "Notice of Transfer of Ownership of Motor Vehicle",
        "Report of Transfer of Ownership of Motor Vehicle",
        "Application for Transfer of Ownership in case of Death",
        "Application for Making Entry of an Agreement of Hire Purchase",
        "Notice of Termination of an Agreement of Hire Purchase",
        "Vehicle Sale Receipt",
        "Surveyor Motor Claim Report",
        "Total Loss Settlement Letter",
        "Salvage Value Document",
        "Udyam Registration Certificate",
        "Goods and Services Tax Registration Certificate",
        "Certificate of Incorporation",
        "Memorandum of Association",
        "Articles of Association",
        "Partnership Deed",
        "Limited Liability Partnership Agreement",
        "Trade License",
        "Food Safety and Standards Authority of India License",
        "Import Export Code Certificate",
        "Fire No Objection Certificate",
        "State Pollution Control Board NOC",
        "Shops and Establishments Act Registration Certificate",
        "Customs Duty Receipt",
        "Bill of Lading",
        "Air Waybill",
        "Letter of Credit",
        "Bank Guarantee Document",
        "Promissory Note",
        "Bill of Exchange",
        "Auditor Report",
        "Balance Sheet Document",
        "Profit and Loss Statement",
        "Cash Flow Statement",
        "Statement of Changes in Equity",
        "Directors Report",
        "Annual Return",
        "Financial Statement",
        "Director Identification Number Allotment Letter",
        "Cryptographic Signature Token Document",
        "Chartered Accountant Certificate",
        "Company Secretary Certificate",
        "Cost Accountant Certificate",
        "Net Worth Certificate",
        "Solvency Certificate",
        "Valuation Report",
        "Actuarial Valuation Report",
        "Startup India Recognition Certificate",
        "DPIIT Registration Document",
        "MSME Samadhaan Filing Document",
        "Kisan Credit Card Passbook",
        "Soil Health Card",
        "Crop Insurance Policy",
        "Agricultural Land Passbook",
        "Education Loan Sanction Letter",
        "Education Loan Disbursement Letter",
        "Home Loan Sanction Letter",
        "Home Loan Agreement",
        "Personal Loan Agreement",
        "Vehicle Loan Agreement",
        "Business Loan Agreement",
        "Overdraft Facility Agreement",
        "Cash Credit Limit Sanction Letter",
        "Loan Pre-closure Statement",
        "Loan Closure No Objection Certificate",
        "Amortization Schedule",
        "CIBIL Credit Report",
        "Experian Credit Report",
        "Equifax Credit Report",
        "CRIF High Mark Credit Report",
        "Home Loan Interest Certificate",
        "Education Loan Interest Certificate",
        "Affidavit",
        "Power of Attorney",
        "Will Document",
        "Gift Deed",
        "Relinquishment Deed",
        "Trust Deed",
        "Indemnity Bond",
        "Surety Bond",
        "Bail Bond",
        "Court Summons",
        "Court Judgment Document",
        "Decree Certificate",
        "Caveat Petition",
        "Habeas Corpus Petition",
        "Notarized Document",
        "Stamp Paper",
        "Judicial Stamp Paper",
        "Franked Document",
        "Non-compete Agreement",
        "Divorce Decree Document",
        "Separation Agreement",
        "Maintenance Order",
        "Adoption Deed",
        "Guardianship Certificate",
        "Custody Order",
        "Name Change Gazette Notification",
        "Gender Change Gazette Notification",
        "Religion Change Document",
        "Signature Verification Certificate",
        "Photograph Verification Certificate",
        "Legal Notice",
        "Reply to Legal Notice",
        "Cease and Desist Notice",
        "Arbitral Award Document",
        "Mediation Settlement Agreement",
        "Lok Adalat Award",
        "FIR Cancellation Report",
        "Charge Sheet Document",
        "Autopsy Report",
        "Police Clearance Certificate",
        "First Information Report",
        "Motor Accident Claims Tribunal Petition",
        "Consumer Court Complaint Document",
        "Joint Venture Agreement",
        "Memorandum of Understanding",
        "Service Level Agreement",
        "Vendor EmpEmpanelment Letter",
        "Tender Document",
        "Earnest Money Deposit Receipt",
        "Performance Bank Guarantee",
        "Letter of Intent",
        "Work Order",
        "Measurement Book Document",
        "Running Account Bill",
        "Completion Report",
        "Defect Liability Certificate",
        "Handing Over Certificate",
        "Wayleave Agreement",
        "Right of Way Document",
        "Easement Right Document",
        "Hypothecation Agreement",
        "Pledge Agreement",
        "Mortgage Deed",
        "Deed of Assignment",
        "Conveyance Deed",
        "Exchange Deed",
        "Partition Deed",
        "Family Settlement Agreement",
        "Surrender Deed",
        "Rectification Deed",
        "Cancellation Deed",
        "Revocation of Power of Attorney",
        "Sub-Lease Agreement",
        "Leave and License Agreement",
        "Rent Control Act Registration",
        "Paying Guest Agreement",
        "Hostel Accommodation Allotment Letter",
        "Arms License",
        "Liquor Permit",
        "Broadcast License",
        "Amateur Radio License",
        "Drone Registration Certificate",
        "Pilot License",
        "Seafarer Identification Document",
        "Merchant Navy Continuous Discharge Certificate",
        "Emigration Check Required Document",
        "Emigration Clearance Document",
        "Pravasi Bharatiya Bima Yojana Policy",
        "Yellow Fever Vaccination Certificate",
        "Polio Vaccination Certificate",
        "International Certificate of Vaccination",
        "Export Promotion Capital Goods Authorization",
        "Advance Authorization Certificate",
        "Duty Free Import Authorization",
        "Certificate of Origin",
        "Phytosanitary Certificate",
        "Health Certificate for Export",
        "Directorate General of Foreign Trade Certificate",
        "Registration Cum Membership Certificate",
        "ISO Certification Document",
        "BIS Hallmark Certificate",
        "Agmark Certificate",
        "ISI Mark Certificate",
        "Trademark Registration Certificate",
        "Patent Certificate",
        "Copyright Registration Certificate",
        "Geographical Indication Certificate",
        "Design Registration Certificate",
        "Khadi Mark Certificate",
        "Handloom Mark Certificate",
        "Silk Mark Certificate",
        "Trademark Search Report",
        "Copyright Assignment Deed",
        "Patent Licensing Agreement",
        "Franchise Agreement",
        "Professional Tax Enrolment Certificate",
        "Professional Tax Registration Certificate",
        "Municipal Trade License",
        "Contract Labour License",
        "Provident Fund Registration Certificate",
        "ESIC Registration Certificate",
        "Factory License",
        "Boiler Registration Certificate",
        "Lift Inspector License",
        "Weighing and Measuring Instruments Stamping Certificate",
        "Entertainment Tax Registration",
        "Luxury Tax Registration",
        "Cinema License",
        "Hotel License",
        "Restaurant License",
        "Liquor Serving License",
        "Private Security Agency Regulation Act License",
        "Telecom Regulatory Authority of India Registration",
        "Other Service Provider License",
        "Internet Service Provider License",
        "Virtual Network Operator License",
        "Authorized Dealer License",
        "Money Changer License",
        "Non-Banking Financial Company Registration Certificate",
        "Microfinance Institution Registration",
        "Chit Fund Registration Certificate",
        "Nidhi Company Registration",
        "Section 8 Company License",
        "NGO Darpan Registration Document",
        "Foreign Contribution Regulation Act Registration",
        "Society Registration Certificate",
        "Trust Registration Certificate",
        "Section 12A Registration Certificate",
        "Section 80G Registration Certificate",
        "Corporate Social Responsibility Project Registration",
        "Securities and Exchange Board of India Registration Certificate",
        "Stock Broker License",
        "Depository Participant License",
        "Portfolio Manager Registration",
        "Investment Adviser Registration",
        "Mutual Fund Distributor Certificate",
        "Insurance Regulatory and Development Authority of India License",
        "Insurance Agent License",
        "Surveyor and Loss Assessor License",
        "Third Party Administrator License",
        "Custom House Agent License",
        "Freight Forwarder Registration",
        "Multimodal Transport Operator Registration",
        "Port Trust Registration Certificate",
        "Dock Entry Permit",
        "Airport Entry Permit",
        "Bureau of Civil Aviation Security Clearance",
        "Transit Way Bill",
        "Lorry Receipt",
        "Goods Receipt Note",
        "Delivery Challan",
        "Purchase Order",
        "Proforma Invoice",
        "Commercial Invoice",
        "Debit Note",
        "Credit Note",
        "Receipt Voucher",
        "Payment Voucher",
        "Journal Voucher",
        "Petty Cash Voucher",
        "Boarding Pass",
        "Flight Ticket",
        "Train Ticket",
        "Bus Ticket",
        "Hotel Booking Confirmation",
        "Forex Purchase Receipt",
        "Customs Declaration Form",
        "Police Verification Report",
        "Background Verification Report",
        "Credit Reference Letter",
        "Income Proof Document",
        "Address Proof Document",
        "Identity Proof Document",
        "Age Proof Document",
        "Relationship Proof Document",
        "Survival Certificate",
        "Life Certificate",
        "Unmarried Certificate",
        "Bachelorhood Certificate",
        "Trade Test Certificate",
        "Proficiency Certificate",
        "TPA ID Card",
        "Cashless Pre-Authorization Form",
        "Cashless Approval Letter",
        "Cashless Denial Letter",
        "Claim Settlement Letter",
        "Claim Denial Letter",
        "TPA Query Letter",
        "Explanation of Benefits",
        "Detailed Hospital Bill",
        "Final Hospital Bill",
        "Breakup of Hospital Bill",
        "Implant Invoice",
        "Surgeon Certificate",
        "Treating Doctor Certificate"
    )
    val allCategories = linkedSetOf<String>().apply {
        addAll(categories)
        addAll(indianCategories)
    }

    data class Result(
        val name: String,
        val category: String = FALLBACK,
        val specifics: String = "",
        val status: String = "PROCESSING_ERROR",
        val suggestedName: String = "",
        val promptSent: String = "",
        val rawResponse: String = "",
        val metadata: String = ""
    )

    fun validate(category: String): Boolean = allCategories.contains(category)

    fun normalizeCategory(raw: String): String {
        val trimmed = raw.trim()
        if (validate(trimmed)) return trimmed
        val exactCi = allCategories.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (exactCi != null) return exactCi
        val lower = trimmed.lowercase()
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
            lower.contains("uan") -> "Universal Account Number Card" 
            lower.contains("prescript") -> "Medical Prescription"
            lower.contains("diagnostic") || lower.contains("lab report") || lower.contains("blood") || lower.contains("pathology") -> "Medical Diagnostic / Lab Report"
            lower.contains("invoice") || lower.contains("bill") -> "Bill or Invoice"
            lower.contains("receipt") -> "Payment Receipt"
            lower.contains("aadhaar") -> "Aadhaar Card"
            lower.contains("pan card") || lower == "pan" -> "PAN Card"
            lower.contains("passport") -> "Passport"
            lower.contains("voter") -> "Voter ID Card"
            lower.contains("driving") || lower.contains("licence") || lower.contains("license") -> "Driving Licence"
            lower.contains("bank statement") -> "Bank Statement"
            lower.contains("salary") || lower.contains("payslip") -> "Salary Slip / Payslip"
            lower.contains("electric") -> "Electricity Bill"
            lower.contains("telecom") || lower.contains("internet") || lower.contains("phone bill") || lower.contains("mobile bill") -> "Telecom / Internet Bill"
            lower.contains("gas") || lower.contains("lpg") -> "Gas / LPG Bill"
            lower.contains("form 16") -> "Form 16 / TDS Certificate"
            lower.contains("form 26") -> "Form 26AS / Tax Statement"
            lower.contains("epf") || lower.contains("provident") || lower.contains("uan") -> "EPF / Provident Fund (Form 13 / UAN)"
            lower.contains("rent") || lower.contains("lease") -> "Rental / Lease Agreement"
            lower.contains("insurance") -> "Insurance Document"
            lower.contains("tax") -> "Tax Document"
            trimmed.isNotBlank() -> trimmed
            else -> FALLBACK
        }
    }

    fun cleanCategoryPrefix(category: String): String {
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
            "Motor Vehicle Insurance Policy" -> "Motor_Insurance" 
            "Medical Diagnostic / Lab Report" -> "Medical_Report"
            "Medical Prescription" -> "Prescription"
            "Aadhaar Card" -> "Aadhaar_Card"
            "PAN Card" -> "PAN_Card"
            "Passport" -> "Passport"
            "Voter ID Card" -> "Voter_ID"
            "Driving Licence" -> "Driving_Licence"
            "Bank Statement" -> "Bank_Statement"
            "Salary Slip / Payslip" -> "Salary_Slip"
            "Electricity Bill" -> "Electricity_Bill"
            "Telecom / Internet Bill" -> "Telecom_Bill"
            "Gas / LPG Bill" -> "Gas_Bill"
            "Bill or Invoice" -> "Invoice"
            "Payment Receipt" -> "Receipt"
            "Insurance Document" -> "Insurance"
            "Rental / Lease Agreement" -> "Rental_Agreement"
            "Form 16 / TDS Certificate" -> "Form_16"
            "Form 26AS / Tax Statement" -> "Form_26AS"
            "Tax Form (Form 13 / 15G / Lower TDS)" -> "Tax_Form"
            "EPF / Provident Fund (Form 13 / UAN)" -> "EPF_Statement"
            "Vehicle Registration / Insurance" -> "Vehicle_RC"
            "Credit Card Statement" -> "Credit_Card_Statement"
            "Other Document" -> "Document"
            else -> category.replace(" / ", "_").replace(" ", "_").replace("-", "_")
        }
    }

    fun extractDate(text: String): String {
        val ymd = Regex("""\b(20\d\d)[-/.](0[1-9]|1[0-2])[-/.](0[1-9]|[12]\d|3[01])\b""").find(text)
        if (ymd != null) {
            val (y, m, d) = ymd.destructured
            return "$y-$m-$d"
        }
        val dmy = Regex("""\b(0[1-9]|[12]\d|3[01])[-/.](0[1-9]|1[0-2])[-/.](20\d\d)\b""").find(text)
        if (dmy != null) {
            val (d, m, y) = dmy.destructured
            return "$y-$m-$d"
        }
        val my = Regex("""\b(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[ ,_-]+(20\d\d)\b""", RegexOption.IGNORE_CASE).find(text)
        if (my != null) {
            val (mon, yr) = my.destructured
            val mShort = mon.take(3).replaceFirstChar { it.uppercase() }
            return "${mShort}_$yr"
        }
        return ""
    }

    fun extractEntity(text: String): String {
        val t = text.lowercase()
        fun has(p: String): Boolean = Regex("""\b$p\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)

        return when {
            // Pathology tests
            has("lft") || has("liver function") -> "LFT"
            has("cbc") || has("complete blood") -> "CBC"
            has("kft") || has("kidney function") -> "KFT"
            has("lipid profile") || has("cholesterol") -> "Lipid_Profile"
            has("thyroid") -> "Thyroid"
            has("blood sugar") || has("glucose") || has("hba1c") -> "Blood_Sugar"
            has("urine") -> "Urine_Test"

            // Medical labs
            has("lalpath") || has("lal path") -> "LalPath"
            has("apollo") -> "Apollo"
            has("max lab") -> "Max_Lab"
            has("srl") -> "SRL"
            has("metropolis") -> "Metropolis"
            has("thyrocare") -> "Thyrocare"

            // Banks
            has("hdfc") -> "HDFC"
            has("icici") -> "ICICI"
            has("sbi") || has("state bank") -> "SBI"
            has("axis") -> "Axis"
            has("kotak") -> "Kotak"
            has("pnb") -> "PNB"
            has("canara") -> "Canara"
            has("bank of baroda") || has("bob") -> "BoB"

            // Telecom / Utilities
            has("airtel") -> "Airtel"
            has("jio") -> "Jio"
            has("vodafone") || has("vi") -> "Vi"
            has("bescom") -> "BESCOM"
            has("tata power") -> "Tata_Power"
            has("indane") -> "Indane"
            has("hp gas") -> "HP_Gas"
            has("bharat gas") -> "Bharat_Gas"

            else -> ""
        }
    }

    val CATEGORY_CODES = mapOf(
        "Identity" to "IDN",
        "Education" to "EDU",
        "Medical" to "MED",
        "Finance" to "FIN",
        "Work" to "WRK",
        "Property" to "PRO",
        "Business" to "BIZ",
        "Legal" to "LAW",
        "Transport" to "TPT",
        "Travel" to "TRV",
        "Insurance" to "INS",
        "Utilities" to "UTL",
        "Agriculture" to "AGR"
    )

    fun resolveCategoryCode(category: String): String {
        val trimmed = category.trim()
        val direct = CATEGORY_CODES[trimmed]
        if (direct != null) return direct

        for ((cat, code) in CATEGORY_CODES) {
            if (cat.equals(trimmed, ignoreCase = true)) return code
        }

        val low = trimmed.lowercase()
        return when {
            low.contains("ident") || low.contains("aadhaar") || low.contains("pan") || low.contains("passport") || low.contains("voter") || low.contains("permanent account") || low.contains("ration") || low.contains("citizenship") -> "IDN"
            low.contains("educat") || low.contains("marksheet") || low.contains("degree") || low.contains("school") || low.contains("college") || low.contains("transcript") || low.contains("diploma") -> "EDU"
            low.contains("medic") || low.contains("health") || low.contains("hosp") || low.contains("doctor") || low.contains("patient") || low.contains("lab") || low.contains("patholog") || low.contains("prescription") || low.contains("diagnostic") -> "MED"
            low.contains("finan") || low.contains("bank") || low.contains("tax") || low.contains("income") || low.contains("salary") || low.contains("loan") || low.contains("invest") || low.contains("saving") || low.contains("account") || low.contains("statement") || low.contains("passbook") || low.contains("credit") || low.contains("debit") || low.contains("cheque") || low.contains("demat") || low.contains("mutual fund") || low.contains("form 16") || low.contains("form 26as") || low.contains("itr") -> "FIN"
            low.contains("work") || low.contains("employ") || low.contains("job") || low.contains("offer") || low.contains("reliev") || low.contains("apprais") || low.contains("provident") || low.contains("epf") || low.contains("pf") || low.contains("gratuity") || low.contains("pension") || low.contains("wage") -> "WRK"
            low.contains("prop") || low.contains("land") || low.contains("rent") || low.contains("lease") || low.contains("deed") || low.contains("flat") || low.contains("house") || low.contains("building") || low.contains("khatauni") || low.contains("khasra") || low.contains("mutation") || low.contains("patta") -> "PRO"
            low.contains("busin") || low.contains("gst") || low.contains("trade") || low.contains("tender") || low.contains("incorporat") || low.contains("comp") || low.contains("msme") || low.contains("udyam") -> "BIZ"
            low.contains("law") || low.contains("legal") || low.contains("court") || low.contains("affidavit") || low.contains("notar") || low.contains("notice") || low.contains("agreement") || low.contains("contract") -> "LAW"
            low.contains("transport") || low.contains("vehic") || low.contains("rc") || low.contains("driving") || low.contains("toll") || low.contains("permit") || low.contains("fastag") || low.contains("challan") -> "TPT"
            low.contains("travel") || low.contains("ticket") || low.contains("flight") || low.contains("train") || low.contains("boarding") || low.contains("visa") || low.contains("hotel") -> "TRV"
            low.contains("insur") || low.contains("policy") || low.contains("claim") || low.contains("premium") -> "INS"
            low.contains("utilit") || low.contains("bill") || low.contains("electric") || low.contains("water") || low.contains("gas") || low.contains("broadband") || low.contains("telecom") || low.contains("phone") -> "UTL"
            low.contains("agri") || low.contains("kisan") || low.contains("crop") || low.contains("soil") -> "AGR"
            else -> "OTH"
        }
    }

    private val MONTH_NAMES = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTH_LOOKUP = mapOf(
        "jan" to 0, "january" to 0,
        "feb" to 1, "february" to 1,
        "mar" to 2, "march" to 2,
        "apr" to 3, "april" to 3,
        "may" to 4,
        "jun" to 5, "june" to 5,
        "jul" to 6, "july" to 6,
        "aug" to 7, "august" to 7,
        "sep" to 8, "sept" to 8, "september" to 8,
        "oct" to 9, "october" to 9,
        "nov" to 10, "november" to 10,
        "dec" to 11, "december" to 11
    )

    fun formatDdMMMyy(rawDate: String?): String {
        if (!rawDate.isNullOrBlank()) {
            val s = rawDate.trim()
            // 1. YYYY-MM-DD
            val mYmd = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""").find(s)
            if (mYmd != null) {
                val y = mYmd.groupValues[1].toInt() % 100
                val mo = mYmd.groupValues[2].toInt() - 1
                val d = mYmd.groupValues[3].toInt()
                if (mo in 0..11 && d in 1..31) {
                    return String.format(Locale.US, "%02d%s%02d", d, MONTH_NAMES[mo], y)
                }
            }
            // 2. DD-MM-YYYY or DD/MM/YY
            val mDmy = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2}|\d{2})\b""").find(s)
            if (mDmy != null) {
                val d = mDmy.groupValues[1].toInt()
                val mo = mDmy.groupValues[2].toInt() - 1
                var y = mDmy.groupValues[3].toInt()
                if (y > 1900) y %= 100
                if (mo in 0..11 && d in 1..31) {
                    return String.format(Locale.US, "%02d%s%02d", d, MONTH_NAMES[mo], y)
                }
            }
            // 3. DD-Mon-YYYY or DD Mon YYYY or DDMonYY
            val mAlpha = Regex("""\b(\d{1,2})[-/\s]*([a-zA-Z]{3,})[-/\s]*(20\d{2}|\d{2})\b""").find(s)
            if (mAlpha != null) {
                val d = mAlpha.groupValues[1].toInt()
                val mStr = mAlpha.groupValues[2].lowercase()
                var y = mAlpha.groupValues[3].toInt()
                if (y > 1900) y %= 100
                val mo = MONTH_LOOKUP[mStr] ?: MONTH_LOOKUP[mStr.take(3)]
                if (mo != null && d in 1..31) {
                    return String.format(Locale.US, "%02d%s%02d", d, MONTH_NAMES[mo], y)
                }
            }
            // 4. Mon DD, YYYY
            val mAlphaMonFirst = Regex("""\b([a-zA-Z]{3,})[-/\s]+(\d{1,2})[-/\s,'"]+(20\d{2}|\d{2})\b""").find(s)
            if (mAlphaMonFirst != null) {
                val mStr = mAlphaMonFirst.groupValues[1].lowercase()
                val d = mAlphaMonFirst.groupValues[2].toInt()
                var y = mAlphaMonFirst.groupValues[3].toInt()
                if (y > 1900) y %= 100
                val mo = MONTH_LOOKUP[mStr] ?: MONTH_LOOKUP[mStr.take(3)]
                if (mo != null && d in 1..31) {
                    return String.format(Locale.US, "%02d%s%02d", d, MONTH_NAMES[mo], y)
                }
            }
        }
        val cal = Calendar.getInstance()
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val mo = cal.get(Calendar.MONTH)
        val y = cal.get(Calendar.YEAR) % 100
        return String.format(Locale.US, "%02d%s%02d", d, MONTH_NAMES[mo], y)
    }

    val PHRASE_ABBREVIATIONS = mapOf(
        "permanent account number card" to "PANCard",
        "permanent account number" to "PANCard",
        "pan card" to "PANCard",
        "aadhaar card" to "AadhaarCard",
        "aadhar card" to "AadhaarCard",
        "voter identity card" to "VoterID",
        "voter id card" to "VoterID",
        "driving license" to "DrivingLic",
        "driving licence" to "DrivingLic",
        "ration card" to "RationCard",
        "income tax return verification form" to "ITRVerification",
        "income tax return" to "ITR",
        "income tax form 16" to "IncomeTaxForm16",
        "form 16" to "Form16",
        "form 16a" to "Form16A",
        "form 26as" to "Form26AS",
        "hospital discharge summary" to "DischSumm",
        "diagnostic laboratory report" to "DiagLabRpt",
        "blood test report" to "BloodTestRpt",
        "pathology report" to "PathRpt",
        "medical prescription" to "MedicalRx",
        "builder-buyer agreement" to "BuilderBuyerAgrmt",
        "rent agreement" to "RentAgrmt",
        "rental agreement" to "RentAgrmt",
        "sale deed" to "SaleDeed",
        "lease deed" to "LeaseDeed",
        "electricity bill" to "ElecBill",
        "water bill" to "WaterBill",
        "piped gas bill" to "GasBill",
        "class 10 marksheet" to "Class10MrkSht",
        "class 12 marksheet" to "Class12MrkSht",
        "school leaving certificate" to "SchlLvgCert",
        "birth certificate" to "BirthCert",
        "death certificate" to "DeathCert",
        "marriage certificate" to "MarriageCert",
        "caste certificate" to "CasteCert",
        "domicile certificate" to "DomicileCert",
        "transfer certificate" to "TransferCert",
        "migration certificate" to "MigrationCert",
        "character certificate" to "CharacterCert",
        "bonafide certificate" to "BonafideCert",
        "degree certificate" to "DegreeCert",
        "provisional degree certificate" to "ProvDegreeCert",
        "health insurance policy" to "HealthInsPolicy",
        "life insurance policy" to "LifeInsPolicy",
        "motor vehicle insurance policy" to "MotorInsPolicy",
        "vehicle registration certificate" to "VehicleRC",
        "pollution under control certificate" to "PollutionPUC"
    )

    val WORD_ABBREVIATIONS = mapOf(
        "certificate" to "Cert", "certificates" to "Certs",
        "agreement" to "Agrmt", "agreements" to "Agrmts",
        "statement" to "Stmt", "statements" to "Stmts",
        "registration" to "Reg", "application" to "Appln",
        "declaration" to "Decl", "marksheet" to "MrkSht",
        "provisional" to "Prov", "permanent" to "Perm",
        "account" to "Acct", "number" to "No",
        "verification" to "Verif", "department" to "Dept",
        "authority" to "Auth", "transfer" to "Trfr",
        "migration" to "Migr", "character" to "Char",
        "examination" to "Exam", "secondary" to "Sec",
        "higher" to "Hgr", "undergraduate" to "UG",
        "postgraduate" to "PG", "degree" to "Deg",
        "school" to "Schl", "leaving" to "Lvg",
        "semester" to "Sem", "scorecard" to "Scorecard",
        "confirmation" to "Conf", "allotment" to "Allot",
        "receipt" to "Rcpt", "receipts" to "Rcpts",
        "submission" to "Subm", "engineering" to "Engg",
        "diploma" to "Dipl", "industrial" to "Ind",
        "training" to "Trng", "institute" to "Inst",
        "national" to "Natl", "development" to "Dev",
        "corporation" to "Corp", "apprenticeship" to "Appr",
        "internship" to "Intern", "completion" to "Compl",
        "fellowship" to "Fellow", "scholarship" to "Schol",
        "sanction" to "Sanc", "research" to "Rsch",
        "proficiency" to "Prof", "disability" to "Disab",
        "government" to "Govt", "health" to "Hlth",
        "scheme" to "Schm", "employees" to "Emp",
        "employee" to "Emp", "medical" to "Med",
        "fitness" to "Fit", "hospital" to "Hosp",
        "discharge" to "Disch", "summary" to "Summ",
        "diagnostic" to "Diag", "laboratory" to "Lab",
        "prescription" to "Rx", "vaccination" to "Vacc",
        "report" to "Rpt", "reports" to "Rpts",
        "pathology" to "Path", "treatment" to "Trtmnt",
        "deduction" to "Deduc", "collection" to "Coll",
        "passbook" to "Passbk", "recurring" to "Recur",
        "consolidated" to "Consol", "provident" to "PF",
        "pension" to "Pens", "payment" to "Pymt",
        "savings" to "Sav", "saving" to "Sav",
        "current" to "Curr", "credit" to "Credit",
        "card" to "Card", "universal" to "Univ",
        "withdrawal" to "Wdrwl", "appointment" to "Appt",
        "relieving" to "Reliev", "experience" to "Exp",
        "salary" to "Sal", "slip" to "Slip",
        "appraisal" to "Appr", "increment" to "Incr",
        "contract" to "Cntrct", "settlement" to "Sttlmnt",
        "identification" to "ID", "commercial" to "Comm",
        "residential" to "Res", "mutation" to "Mut",
        "property" to "Prop", "building" to "Bldg",
        "approval" to "Apprv", "occupancy" to "Occup",
        "insurance" to "Ins", "premium" to "Prem",
        "electricity" to "Elec", "telephone" to "Phone",
        "broadband" to "BB", "internet" to "Net",
        "mobile" to "Mob", "municipal" to "Muni",
        "pollution" to "Poll", "driving" to "Drive",
        "license" to "Lic", "licence" to "Lic",
        "vehicle" to "Veh", "incorporation" to "Incorp",
        "association" to "Assn", "limited" to "Ltd",
        "clearance" to "Clr", "invoice" to "Inv",
        "voucher" to "Voucher", "booking" to "Book"
    )

    fun abbreviateDocType(docType: String?, maxLen: Int = 24): String {
        if (docType.isNullOrBlank()) return "Document".take(maxLen)
        val clean = docType.trim()
        val lower = clean.lowercase()

        // 1. Exact phrase match
        val phraseMatch = PHRASE_ABBREVIATIONS[lower]
        if (phraseMatch != null) {
            return phraseMatch.take(maxLen)
        }

        // 2. Tokenize and replace individual words
        val words = clean.split(Regex("""[\s_\-]+""")).filter { it.isNotBlank() }
        val pascalParts = mutableListOf<String>()
        for (w in words) {
            val wLow = w.lowercase()
            val wAbbrev = WORD_ABBREVIATIONS[wLow]
            if (wAbbrev != null) {
                pascalParts.add(wAbbrev)
            } else {
                val cleaned = w.replace(Regex("""[^a-zA-Z0-9]"""), "")
                if (cleaned.isNotBlank()) {
                    pascalParts.add(cleaned.replaceFirstChar { it.uppercase() })
                }
            }
        }
        val result = if (pascalParts.isNotEmpty()) pascalParts.joinToString("") else "Document"
        return result.take(maxLen)
    }

    fun generateCompliantProposedName(
        category: String,
        documentType: String? = null,
        issuedDate: String? = null,
        originalName: String = "",
        extractedText: String = ""
    ): String {
        val catCode = resolveCategoryCode(category)
        val resolvedDate = issuedDate?.ifBlank { null }
            ?: extractDate(extractedText).ifBlank { null }
            ?: extractDate(originalName).ifBlank { null }
        val dateFormatted = formatDdMMMyy(resolvedDate)

        var ext = ".pdf"
        if (originalName.contains(".")) {
            val rawExt = "." + originalName.substringAfterLast(".").lowercase()
            if (rawExt.length in 2..6) {
                ext = rawExt
            }
        }

        // Strict 40-character maximum formula:
        // CAT (3) + _ (1) + DocumentName (max) + _ (1) + Date (7) + ext.length <= 40
        val maxDocLen = Math.max(4, 40 - 3 - 1 - 1 - 7 - ext.length)
        val effectiveDocType = if (!documentType.isNullOrBlank()) documentType else category
        val abbrev = abbreviateDocType(effectiveDocType, maxDocLen)

        val filename = "${catCode}_${abbrev}_${dateFormatted}${ext}"
        if (filename.length > 40) {
            val excess = filename.length - 40
            val truncatedAbbrev = abbrev.take(Math.max(1, abbrev.length - excess))
            return "${catCode}_${truncatedAbbrev}_${dateFormatted}${ext}"
        }
        return filename
    }

    fun generateBestProposedName(category: String, text: String, originalName: String): String {
        return generateCompliantProposedName(category, null, null, originalName, text)
    }

    data class CategoryRule(
        val category: String,
        val identifiers: List<String>
    )

    private val categoryRules = listOf(
        CategoryRule(
            "Aadhaar Card",
            listOf(
                """\buidai\b""",
                """\bunique identification\b""",
                """\baadha?ar\b""",
                """\bmera aadhaar\b""",
                """\benrolment (no|number)\b""",
                """\bvirtual id\b""",
                """\bvid\b""",
                """\b\d{4}\s\d{4}\s\d{4}\b""",
                """\bgovernment of india\b"""
            )
        ),
        CategoryRule(
            "Permanent Account Number Card",
            listOf(
                """\bpermanent account number\b""",
                """\bincome tax department\b""",
                """\bpan card\b""",
                """\b[a-z]{5}\d{4}[a-z]\b""",
                """\bfather'?s name\b"""
            )
        ),
        CategoryRule(
            "PAN Card",
            listOf(
                """\bpermanent account number\b""",
                """\bincome tax department\b""",
                """\bpan card\b""",
                """\b[a-z]{5}\d{4}[a-z]\b""",
                """\bfather'?s name\b"""
            )
        ),
        CategoryRule(
            "Passport",
            listOf(
                """\bpassport\b""",
                """\brepublic of india\b""",
                """\bministry of external affairs\b""",
                """\bpassport (no|number)\b""",
                """\bplace of issue\b""",
                """\bplace of birth\b""",
                """\bnationality indian\b""",
                """\b[a-pr-wya-pr-wy][1-9]\d\s?\d{4}[1-9]\b"""
            )
        ),
        CategoryRule(
            "Voter ID Card",
            listOf(
                """\belection commission\b""",
                """\belectoral photo\b""",
                """\bvoter id\b""",
                """\bvoter card\b""",
                """\bepic (no|number)\b""",
                """\belector'?s name\b""",
                """\bvidhan sabha\b""",
                """\bparliamentary constituency\b""",
                """\b[a-z]{3}\d{7}\b"""
            )
        ),
        CategoryRule(
            "Driving License",
            listOf(
                """\bdriving licen[sc]e\b""",
                """\bdriver licen[sc]e\b""",
                """\bsarathi\b""",
                """\bmorth\b""",
                """\blicensing authority\b""",
                """\bauthoris(ation|ed) to drive\b""",
                """\b(lmv|mcwg|mcwog|hgv)\b""",
                """\bdl\s*(no|number)\b""",
                """\b[a-z]{2}[-\s/]?[0-9]{2}[-\s/]?[0-9]{4}[-\s/]?[0-9]{7}\b"""
            )
        ),
        CategoryRule(
            "Driving Licence",
            listOf(
                """\bdriving licen[sc]e\b""",
                """\bdriver licen[sc]e\b""",
                """\bsarathi\b""",
                """\bmorth\b""",
                """\blicensing authority\b""",
                """\bauthoris(ation|ed) to drive\b""",
                """\b(lmv|mcwg|mcwog|hgv)\b""",
                """\bdl\s*(no|number)\b""",
                """\b[a-z]{2}[-\s/]?[0-9]{2}[-\s/]?[0-9]{4}[-\s/]?[0-9]{7}\b"""
            )
        ),
        CategoryRule(
            "Form 16 / TDS Certificate",
            listOf(
                """\bform\s*(?:no\.?\s*)?16\b""",
                """\bsection\s*203\b""",
                """\btds certificate\b""",
                """\btax deducted at source\b""",
                """\bpart a\b""",
                """\bpart b\b""",
                """\bay\s*20\d\d[-–]\d\d\b""",
                """\btan of deductor\b"""
            )
        ),
        CategoryRule(
            "Form 26AS / Tax Statement",
            listOf(
                """\bform\s*(?:no\.?\s*)?26as\b""",
                """\btax credit statement\b""",
                """\bannual tax statement\b""",
                """\bdetails of tax deducted\b"""
            )
        ),
        CategoryRule(
            "Tax Form (Form 13 / 15G / Lower TDS)",
            listOf(
                """\bform\s*15g\b""",
                """\bform\s*15h\b""",
                """\blower tds\b""",
                """\bsection 197\b""",
                """\bform\s*(?:no\.?\s*)?13\b"""
            )
        ),
        CategoryRule(
            "Income Tax Return",
            listOf(
                """\bincome tax return\b""",
                """\bitr[-\s]?[1-7]\b""",
                """\backnowledgement number\b""",
                """\bgross total income\b""",
                """\btotal taxable income\b""",
                """\be-filing\b"""
            )
        ),
        CategoryRule(
            "EPF / Provident Fund (Form 13 / UAN)",
            listOf(
                """\bprovident fund\b""",
                """\bepfo\b""",
                """\bepf statement\b""",
                """\buan\b""",
                """\buniversal account number\b""",
                """\bmember passbook\b""",
                """\bepf passbook\b"""
            )
        ),
        CategoryRule(
            "Rental / Lease Agreement",
            listOf(
                """\brental? agreement\b""",
                """\brent agreement\b""",
                """\blease (agreement|deed)\b""",
                """\blessor\b""",
                """\blessee\b""",
                """\btenant\b""",
                """\blandlord\b""",
                """\bmonthly rent\b""",
                """\bsecurity deposit\b""",
                """\btenancy\b"""
            )
        ),
        CategoryRule(
            "Legal / Contract Document",
            listOf(
                """\baffidavit\b""",
                """\bsale deed\b""",
                """\bpower of attorney\b""",
                """\bmemorandum of understanding\b""",
                """\bmou\b""",
                """\bnon-disclosure agreement\b""",
                """\bnda\b""",
                """\bindemnity bond\b""",
                """\bnotarized\b""",
                """\bnotary\b""",
                """\bstamp duty\b"""
            )
        ),
        CategoryRule(
            "Medical Diagnostic / Lab Report",
            listOf(
                """\bpathology\b""",
                """\blab(oratory)? report\b""",
                """\bdiagnostic\b""",
                """\bspecimen\b""",
                """\b(biological )?reference range\b""",
                """\b(lalpath|dr lal|thyrocare|metropolis|srl diagnostics|max lab|apollo diagnostics)\b""",
                """\b(lft|liver function test)\b""",
                """\b(cbc|complete blood count)\b""",
                """\b(kft|kidney function test)\b""",
                """\b(lipid profile|cholesterol|triglycerides)\b""",
                """\b(hba1c|blood glucose|blood sugar)\b""",
                """\b(hemoglobin|bilirubin|wbc|rbc|platelet count|platelets)\b""",
                """\burine (routine|examination|test)\b"""
            )
        ),
        CategoryRule(
            "Medical Prescription",
            listOf(
                """\bprescription\b""",
                """\brx\b""",
                """\b(doctor|physician|surgeon|consultant)\b""",
                """\bdr\.\s*[a-z]+""",
                """\b(clinic|dispensary|hospital)\b""",
                """\bpatient (name|age)\b""",
                """\b(tablet|tab\.?)\b""",
                """\b(capsule|cap\.?)\b""",
                """\b(syrup|syp\.?)\b""",
                """\b\d+\s*mg\b""",
                """\bdosage\b""",
                """\b(od|bd|tds|qid|sos)\b""",
                """\bchief complaint\b""",
                """\bopd (slip|ticket)\b""",
                """\bbed rest\b""",
                """\b1-0-1\b|\b0-1-0\b|\b1-0-0\b|\b0-0-1\b"""
            )
        ),
        CategoryRule(
            "Electricity Bill",
            listOf(
                """\belectric(ity)? bill\b""",
                """\bpower distribution\b""",
                """\bconsumer (no|number|id)\b""",
                """\bca no\b""",
                """\bmeter (number|reading)\b""",
                """\bunits consumed\b""",
                """\bkwh\b""",
                """\bdiscom\b""",
                """\b(bses|tata power|bescom|msedcl|uppcl|torrent power)\b"""
            )
        ),
        CategoryRule(
            "Telecom / Internet Bill",
            listOf(
                """\b(telecom|telephone|mobile|broadband|fiber) bill\b""",
                """\bbilling period\b""",
                """\bplan charges\b""",
                """\b(airtel|reliance jio|jio fiber|vodafone|vi|bsnl)\b""",
                """\bdata usage\b"""
            )
        ),
        CategoryRule(
            "Gas / LPG Bill",
            listOf(
                """\b(indane|bharat gas|hp gas)\b""",
                """\blpg( cylinder)?\b""",
                """\bcylinder booking\b""",
                """\bcash memo\b""",
                """\bconsumer number\b""",
                """\bdistributor\b""",
                """\b(png bill|igl|mgl)\b"""
            )
        ),
        CategoryRule(
            "Vehicle Registration / Insurance",
            listOf(
                """\bregistration certificate\b""",
                """\brc (book|copy|card|no)\b""",
                """\bchassis (no|number)\b""",
                """\bengine (no|number)\b""",
                """\bvehicle class\b""",
                """\bmotor insurance\b""",
                """\bpolicy certificate\b""",
                """\bthird party insurance\b""",
                """\bvahan\b"""
            )
        ),
        CategoryRule(
            "Employee Salary Slip",
            listOf(
                """\b(salary slip|payslip|pay slip)\b""",
                """\bgross (pay|salary)\b""",
                """\bnet (pay|salary)\b""",
                """\bbasic (pay|salary)\b""",
                """\bhra\b""",
                """\bearnings\b""",
                """\bdeductions\b""",
                """\bemployee (code|id)\b""",
                """\btake home pay\b"""
            )
        ),
        CategoryRule(
            "Salary Slip / Payslip",
            listOf(
                """\b(salary slip|payslip|pay slip)\b""",
                """\bgross (pay|salary)\b""",
                """\bnet (pay|salary)\b""",
                """\bbasic (pay|salary)\b""",
                """\bhra\b""",
                """\bearnings\b""",
                """\bdeductions\b""",
                """\bemployee (code|id)\b""",
                """\btake home pay\b"""
            )
        ),
        CategoryRule(
            "Bank Statement",
            listOf(
                """\bbank statement\b""",
                """\bstatement of account\b""",
                """\baccount (number|no)\b""",
                """\bifsc(?:\s*code)?\b""",
                """\bopening balance\b""",
                """\bclosing balance\b""",
                """\btransaction date\b""",
                """\b(withdrawal|debit)\b""",
                """\b(deposit|credit)\b""",
                """\b(savings|current) account\b"""
            )
        ),
        CategoryRule(
            "Credit Card Statement",
            listOf(
                """\bcredit card\b""",
                """\btotal amount due\b""",
                """\bminimum amount due\b""",
                """\bcredit limit\b""",
                """\bavailable credit\b""",
                """\breward points\b""",
                """\bpayment due date\b"""
            )
        ),
        CategoryRule(
            "Mutual Fund / Investment Statement",
            listOf(
                """\bconsolidated account statement\b""",
                """\bcas\b""",
                """\bmutual fund\b""",
                """\bfolio (number|no)\b""",
                """\bnet asset value\b""",
                """\bnav\b""",
                """\bsip\b""",
                """\b(cams|kfintech|kfin)\b""",
                """\b(demat|holding statement)\b"""
            )
        ),
        CategoryRule(
            "Bill or Invoice",
            listOf(
                """\btax invoice\b""",
                """\bgst invoice\b""",
                """\binvoice (number|no)\b""",
                """\bgstin\b""",
                """\bbill to\b""",
                """\bship to\b""",
                """\b(hsn|sac) code\b""",
                """\b(cgst|sgst|igst)\b"""
            )
        ),
        CategoryRule(
            "Payment Receipt",
            listOf(
                """\bpayment receipt\b""",
                """\breceipt (no|number|voucher)\b""",
                """\breceived with thanks\b""",
                """\btransaction id\b""",
                """\bamount paid\b"""
            )
        ),
        CategoryRule(
            "Insurance Document",
            listOf(
                """\binsurance policy\b""",
                """\bpolicy (schedule|no|number)\b""",
                """\b(life|health) insurance\b""",
                """\bsum (insured|assured)\b""",
                """\bpremium (amount|receipt)\b""",
                """\bpolicyholder\b""",
                """\bnominee\b"""
            )
        ),
        CategoryRule(
            "Educational Document",
            listOf(
                """\bmarksheet\b""",
                """\bmark sheet\b""",
                """\bgrade sheet\b""",
                """\b(degree|passing|provisional) certificate\b""",
                """\broll (number|no)\b""",
                """\b(university|board of secondary education)\b""",
                """\b(cbse|icse)\b""",
                """\bacademic transcript\b"""
            )
        )
    )

    fun classifyLocally(text: String, originalName: String): Result {
        // Strict principle:
        // 1. Filename is NEVER evaluated for category (user might have given an incorrect filename earlier).
        // 2. Only content metadata (OCR/text) is evaluated.
        // 3. When multiple categories have matching identifiers, the category with the MAXIMUM number
        //    of matched identifiers wins!

        val content = text.trim()
        if (content.length < 15) {
            return Result(
                name = originalName,
                category = "Other Document",
                specifics = "Insufficient content metadata (${content.length} chars). Filename was excluded from evaluation.",
                status = "OK",
                suggestedName = originalName,
                promptSent = "Local heuristic: Insufficient content metadata (${content.length} chars)",
                rawResponse = "Category: Other Document (Filename excluded from category evaluation)"
            )
        }

        data class MatchResult(
            val category: String,
            val matchedCount: Int,
            val matchedIdentifiers: List<String>,
            val totalSpecificityChars: Int
        )

        val matches = categoryRules.mapNotNull { rule ->
            val matched = mutableListOf<String>()
            var specificityChars = 0
            for (pattern in rule.identifiers) {
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                val m = regex.find(content)
                if (m != null) {
                    matched.add(m.value.lowercase())
                    specificityChars += m.value.length
                }
            }
            if (matched.isNotEmpty()) {
                MatchResult(rule.category, matched.size, matched, specificityChars)
            } else {
                null
            }
        }

        // Rank by MAXIMUM number of matched identifiers descending,
        // and in case of tie, by total character length of the matched tokens
        val sortedMatches = matches.sortedWith(
            compareByDescending<MatchResult> { it.matchedCount }
                .thenByDescending { it.totalSpecificityChars }
        )

        val best = sortedMatches.firstOrNull()

        val (cat, evidence) = if (best != null && best.matchedCount > 0) {
            val runnerUpInfo = if (sortedMatches.size > 1) {
                val second = sortedMatches[1]
                " | Runner-up: '${second.category}' (${second.matchedCount} matches)"
            } else ""
            best.category to "Matched ${best.matchedCount} identifier(s) in content: [${best.matchedIdentifiers.take(6).joinToString(", ")}]$runnerUpInfo"
        } else {
            "Other Document" to "No category identifiers matched in content metadata. Filename excluded from evaluation."
        }

        val suggested = if (cat == "Other Document") originalName else generateBestProposedName(cat, text, originalName)
        val prompt = "Local heuristic classifier (Content-only evaluation)\nOCR Text Length: ${text.length} chars\nSnippet: ${text.take(300)}"
        val response = "Category: $cat\nSuggested Name: $suggested\nEvidence: $evidence"

        return Result(
            name = originalName,
            category = cat,
            specifics = evidence,
            status = "OK",
            suggestedName = suggested,
            promptSent = prompt,
            rawResponse = response
        )
    }

    const val SMART_DOCS_TRANSFER_BASE_PATH = "/storage/emulated/0/Download/SmartDocsTransferd"

    fun formatTransferTimestamp(date: Date = Date()): String {
        val datePart = SimpleDateFormat("ddMMyy", Locale.US).format(date)
        val timePart = SimpleDateFormat("HH:mm:ss", Locale.US).format(date)
        return "${datePart}_${timePart}"
    }

    fun buildTransferFullPath(baseDir: String = SMART_DOCS_TRANSFER_BASE_PATH, date: Date = Date()): String {
        val cleanBase = baseDir.trimEnd('/', '\\')
        return "$cleanBase/${formatTransferTimestamp(date)}"
    }

    fun classify(name: String, image: ByteArray, apiKey: String, model: String): Result {
        val cleanApiKey = apiKey.trim()
        val cleanModel = model.removePrefix("models/").trim()
        if (cleanApiKey.isBlank() || cleanModel.isBlank()) return Result(name, specifics = "Missing API key or model", status = "INPUT_ERROR")
        var connection: HttpURLConnection? = null
        return try {
            val prompt = "Analyze document into an accurate category. Return only JSON object with category (concise document category inferred directly from content) and specifics (short visible evidence)."
            val parts = org.json.JSONArray().put(JSONObject().put("text", prompt)).put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(image, Base64.NO_WRAP))))
            val body = JSONObject().put("contents", org.json.JSONArray().put(JSONObject().put("parts", parts))).put("generationConfig", JSONObject().put("temperature", 0).put("responseMimeType", "application/json"))
            connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$cleanApiKey").openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 75000
            connection.setRequestProperty("x-goog-api-key", cleanApiKey); connection.setRequestProperty("Content-Type", "application/json"); connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) {
                val err = try {
                    val stream = connection.errorStream ?: connection.inputStream
                    val errText = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    val errObj = JSONObject(errText)
                    ": " + errObj.optJSONObject("error")?.optString("message", "")
                } catch (e: Exception) { "" }
                Result(name, specifics = "Gemini HTTP ${connection.responseCode}$err", status = "API_ERROR", promptSent = prompt, rawResponse = "HTTP ${connection.responseCode}")
            } else {
                val rawResp = connection.inputStream.bufferedReader().use { it.readText() }
                val response = JSONObject(rawResp)
                val content = response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val obj = JSONObject(content)
                val category = obj.getString("category"); val evidence = obj.optString("specifics", "")
                val suggested = generateBestProposedName(category, evidence, name)
                if (!validate(category) || evidence.length > 1200) Result(name, specifics = "Invalid model response", status = "INVALID_RESPONSE", promptSent = prompt, rawResponse = rawResp)
                else Result(name, category, evidence, if (category == FALLBACK) "UNCATEGORIZABLE" else "OK", suggested, promptSent = prompt, rawResponse = rawResp)
            }
        } catch (e: Exception) {
            Result(name, specifics = "Classification failed: ${e.message}", status = "PROCESSING_ERROR")
        } finally {
            connection?.disconnect()
        }
    }
}
