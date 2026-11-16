package com.smartdoc.poc

import org.junit.Assert.*
import org.junit.Test

class ClassifierTest {
    @Test
    fun categoriesAreStrict() {
        assertTrue(Classifier.validate("Medical Record"))
        assertTrue(Classifier.validate("Government-issued Document"))
        assertTrue(Classifier.validate("Organization-issued ID Card"))
        assertTrue(Classifier.validate("Legal Document"))
        assertFalse(Classifier.validate("Invalid Category"))
    }

    @Test
    fun fallbackExists() {
        assertEquals("uncategorizable input", Classifier.FALLBACK)
        assertTrue(Classifier.validate(Classifier.FALLBACK))
    }

    @Test
    fun emptyApiKeyReturnsInputError() {
        val result = Classifier.classify("doc.png", byteArrayOf(1, 2, 3), "", "gemini-2.5-flash")
        assertEquals("INPUT_ERROR", result.status)
        assertEquals(Classifier.FALLBACK, result.category)
    }

    @Test
    fun emptyModelReturnsInputError() {
        val result = Classifier.classify("doc.png", byteArrayOf(1, 2, 3), "dummy-key", "")
        assertEquals("INPUT_ERROR", result.status)
        assertEquals(Classifier.FALLBACK, result.category)
    }

    @Test
    fun expandedCategoriesPresent() {
        assertEquals(19, Classifier.categories.size)
        assertTrue(Classifier.categories.containsAll(listOf(
            "Medical Record",
            "Government-issued Document",
            "Organization-issued ID Card",
            "Legal Document",
            "uncategorizable input"
        )))
    }

    @Test
    fun categoryCodesAnd40CharNaming() {
        assertEquals("IDN", Classifier.resolveCategoryCode("Identity"))
        assertEquals("EDU", Classifier.resolveCategoryCode("Education"))
        assertEquals("MED", Classifier.resolveCategoryCode("Medical"))
        assertEquals("FIN", Classifier.resolveCategoryCode("Finance"))
        assertEquals("WRK", Classifier.resolveCategoryCode("Work"))
        assertEquals("PRO", Classifier.resolveCategoryCode("Property"))
        assertEquals("BIZ", Classifier.resolveCategoryCode("Business"))
        assertEquals("LAW", Classifier.resolveCategoryCode("Legal"))
        assertEquals("TPT", Classifier.resolveCategoryCode("Transport"))
        assertEquals("TRV", Classifier.resolveCategoryCode("Travel"))
        assertEquals("INS", Classifier.resolveCategoryCode("Insurance"))
        assertEquals("UTL", Classifier.resolveCategoryCode("Utilities"))
        assertEquals("AGR", Classifier.resolveCategoryCode("Agriculture"))

        // Date formatting
        assertEquals("15Nov26", Classifier.formatDdMMMyy("15-Nov-2026"))
        assertEquals("15Nov26", Classifier.formatDdMMMyy("2026-11-15"))

        // Generation
        val name1 = Classifier.generateCompliantProposedName("Finance", "Income Tax Form 16", "15-Nov-2026", "sample.pdf")
        assertEquals("FIN_IncomeTaxForm16_15Nov26.pdf", name1)
        assertTrue(name1.length <= 40)

        // Super long doc type truncated to <= 40
        val longName = Classifier.generateCompliantProposedName(
            "Property",
            "Super Extremely Long Non-Agricultural Land Conversion Approval Certificate And Order",
            "15-Nov-2026",
            "scan.pdf"
        )
        assertTrue("Expected <= 40 chars, got: ${longName.length} ($longName)", longName.length <= 40)
        assertTrue(longName.startsWith("PRO_"))
        assertTrue(longName.endsWith("_15Nov26.pdf"))
    }

    @Test
    fun smartDocsTransferPathAndTimestampFormat() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.NOVEMBER, 15, 22, 50, 0)
        val testDate = cal.time

        val timestamp = Classifier.formatTransferTimestamp(testDate)
        assertEquals("151126_22:50:00", timestamp)
        assertEquals("/storage/emulated/0/Download/SmartDocsTransferd", Classifier.SMART_DOCS_TRANSFER_BASE_PATH)

        val fullPath = Classifier.buildTransferFullPath(date = testDate)
        assertEquals("/storage/emulated/0/Download/SmartDocsTransferd/151126_22:50:00", fullPath)
    }
}
