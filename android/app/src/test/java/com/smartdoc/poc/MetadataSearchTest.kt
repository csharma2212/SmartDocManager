package com.smartdoc.poc

import org.junit.Assert.*
import org.junit.Test

class MetadataSearchTest {

    @Test
    fun matchesSearchAcrossMetadataFields() {
        val doc = Doc(
            uri = "content://docs/1",
            name = "scan_001.pdf",
            type = "PDF",
            modified = 1731650000000L,
            size = 1024L,
            category = "Medical",
            proposed = "MED_BloodReport_15Nov26.pdf",
            metadata = """{"doctor_name":"Dr. Sharma","patient_name":"Rahul Verma","hospital_name":"Apollo Hospital","lab_name":"Dr Lal PathLabs","test_name":"CBC Test","issued_date":"15 Nov 2026"}"""
        )

        // Matches doctor name
        assertTrue(DateSearchHelper.matchesSearch(doc, "Sharma", "ANY"))
        // Matches patient name
        assertTrue(DateSearchHelper.matchesSearch(doc, "Rahul", "ANY"))
        // Matches hospital name
        assertTrue(DateSearchHelper.matchesSearch(doc, "Apollo", "ANY"))
        // Matches lab name
        assertTrue(DateSearchHelper.matchesSearch(doc, "Lal PathLabs", "ANY"))
        // Matches test name
        assertTrue(DateSearchHelper.matchesSearch(doc, "CBC", "ANY"))
        // Does not match random query
        assertFalse(DateSearchHelper.matchesSearch(doc, "NonExistentEntity", "ANY"))
    }

    @Test
    fun searchSuggestionsIncludeMetadataEntities() {
        val doc = Doc(
            uri = "content://docs/2",
            name = "rent_agree.pdf",
            type = "PDF",
            modified = 1731650000000L,
            size = 2048L,
            category = "Property",
            proposed = "PRO_RentAgrmt_15Nov26.pdf",
            metadata = """{"lessor_name":"Vikram Singhania","lessee_name":"Amit Gupta","title":"Residential Lease Agreement"}"""
        )

        val suggestions = DateSearchHelper.getSearchSuggestions("vik", listOf(doc))
        assertTrue(suggestions.contains("Vikram Singhania"))

        val lesseeSuggestions = DateSearchHelper.getSearchSuggestions("amit", listOf(doc))
        assertTrue(lesseeSuggestions.contains("Amit Gupta"))
    }
}
