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
}
