package com.smartdoc.poc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficeTextExtractorTest {

    @Test
    fun testTxtExtraction() {
        val content = "Aadhaar Card Enrollment Slip 1234 5678 9012"
        val data = content.toByteArray(StandardCharsets.UTF_8)
        val extracted = OfficeTextExtractor.extractText("slip.txt", data)
        assertTrue(extracted.contains("Aadhaar Card"))
        assertTrue(extracted.contains("1234 5678 9012"))
    }

    @Test
    fun testDocxExtraction() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            val xml = """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:p><w:r><w:t>Income Tax Return Verification Form 16</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Assessment Year 2026-27</w:t></w:r></w:p>
                </w:document>
            """.trimIndent()
            zos.write(xml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        val extracted = OfficeTextExtractor.extractText("form16.docx", baos.toByteArray())
        assertTrue(extracted.contains("Income Tax Return"))
        assertTrue(extracted.contains("Form 16"))
        assertTrue(extracted.contains("Assessment Year 2026-27"))
    }

    @Test
    fun testPptxExtraction() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("ppt/slides/slide1.xml"))
            val xml = """
                <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                    <p:spTree>
                        <p:sp>
                            <p:txBody>
                                <a:p><a:r><a:t>Quarterly Financial Results Presentation</a:t></a:r></a:p>
                            </p:txBody>
                        </p:sp>
                    </p:spTree>
                </p:sld>
            """.trimIndent()
            zos.write(xml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        val extracted = OfficeTextExtractor.extractText("presentation.pptx", baos.toByteArray())
        assertTrue(extracted.contains("Quarterly Financial Results"))
    }

    @Test
    fun testXlsxExtraction() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            val xml = """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <si><t>HDFC Bank Account Statement October 2026</t></si>
                    <si><t>Available Balance: 50,000</t></si>
                </sst>
            """.trimIndent()
            zos.write(xml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        val extracted = OfficeTextExtractor.extractText("statement.xlsx", baos.toByteArray())
        assertTrue(extracted.contains("HDFC Bank Account Statement"))
        assertTrue(extracted.contains("Available Balance"))
    }

    @Test
    fun testLegacyBinaryExtraction() {
        val prefix = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0x00, 0x00)
        val textBytes = "Commercial Lease Deed Flat 402 Bangalore".toByteArray(StandardCharsets.US_ASCII)
        val suffix = byteArrayOf(0x00, 0x00, 0x1A, 0x2B)
        val total = prefix + textBytes + suffix
        val extracted = OfficeTextExtractor.extractText("agreement.doc", total)
        assertTrue(extracted.contains("Commercial Lease Deed"))
    }

    @Test
    fun testIsOfficeDocument() {
        assertTrue(OfficeTextExtractor.isOfficeDocument("resume.docx"))
        assertTrue(OfficeTextExtractor.isOfficeDocument("sheet.xlsx"))
        assertTrue(OfficeTextExtractor.isOfficeDocument("slides.pptx"))
        assertTrue(OfficeTextExtractor.isOfficeDocument("legacy.doc"))
        assertTrue(OfficeTextExtractor.isOfficeDocument("data.xls"))
        assertTrue(OfficeTextExtractor.isOfficeDocument("deck.ppt"))
        assertTrue(OfficeTextExtractor.isOfficeDocument("readme.txt"))
        assertEquals(false, OfficeTextExtractor.isOfficeDocument("doc.pdf"))
        assertEquals(false, OfficeTextExtractor.isOfficeDocument("image.png"))
    }
}
