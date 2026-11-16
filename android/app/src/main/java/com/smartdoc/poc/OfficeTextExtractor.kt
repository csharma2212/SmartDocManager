package com.smartdoc.poc

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

/**
 * On-device text extractor for Microsoft Office documents and text files:
 * .docx, .xlsx, .pptx, .doc, .xls, .ppt, and .txt.
 *
 * 100% local, zero heavy third-party library dependencies (no Apache POI).
 * Uses standard java.util.zip and stream parsing with robust binary fallback.
 */
object OfficeTextExtractor {

    val OFFICE_EXTENSIONS = setOf("docx", "xlsx", "doc", "xls", "pptx", "ppt", "txt")

    private val XML_TAG_PATTERN = Pattern.compile("<[^>]+>")
    private val WHITESPACE_PATTERN = Pattern.compile("[ \\t\\f]+")

    fun isOfficeDocument(filenameOrExt: String): Boolean {
        val ext = filenameOrExt.substringAfterLast('.', filenameOrExt).lowercase()
        return ext in OFFICE_EXTENSIONS
    }

    fun extractText(filename: String, inputStream: InputStream): String {
        return try {
            val bytes = inputStream.readBytes()
            extractText(filename, bytes)
        } catch (_: Exception) {
            ""
        }
    }

    fun extractText(filename: String, data: ByteArray): String {
        if (data.isEmpty()) return ""
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt" -> extractTxt(data)
            "docx" -> extractDocx(data)
            "pptx" -> extractPptx(data)
            "xlsx" -> extractXlsx(data)
            "doc", "xls", "ppt" -> extractLegacyBinary(data)
            else -> extractTxt(data)
        }
    }

    fun extractTxt(data: ByteArray): String {
        val utf8 = try {
            String(data, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            String(data, StandardCharsets.ISO_8859_1)
        }
        return utf8.trim()
    }

    fun extractDocx(data: ByteArray): String {
        return try {
            val parts = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(data)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.equals("word/document.xml", ignoreCase = true) ||
                        entry.name.startsWith("word/header", ignoreCase = true) ||
                        entry.name.startsWith("word/footer", ignoreCase = true)
                    ) {
                        val content = zis.readBytes().toString(StandardCharsets.UTF_8)
                        val text = cleanXml(content)
                        if (text.isNotBlank()) parts.add(text)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (parts.isNotEmpty()) parts.joinToString("\n") else extractLegacyBinary(data)
        } catch (_: Exception) {
            extractLegacyBinary(data)
        }
    }

    fun extractPptx(data: ByteArray): String {
        return try {
            val slides = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(data)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.matches(Regex("""ppt/slides/slide\d+\.xml""", RegexOption.IGNORE_CASE))) {
                        val content = zis.readBytes().toString(StandardCharsets.UTF_8)
                        val text = cleanXml(content)
                        if (text.isNotBlank()) slides.add(text)
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (slides.isNotEmpty()) slides.joinToString("\n") else extractLegacyBinary(data)
        } catch (_: Exception) {
            extractLegacyBinary(data)
        }
    }

    fun extractXlsx(data: ByteArray): String {
        return try {
            val strings = mutableListOf<String>()
            ZipInputStream(ByteArrayInputStream(data)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name == "xl/sharedstrings.xml" || name.startsWith("xl/worksheets/sheet")) {
                        val content = zis.readBytes().toString(StandardCharsets.UTF_8)
                        // Extract text within <t> tags or <v> tags
                        val tMatcher = Pattern.compile("<t[^>]*>(.*?)</t>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(content)
                        while (tMatcher.find()) {
                            val raw = tMatcher.group(1) ?: ""
                            val unescaped = unescapeXml(raw).trim()
                            if (unescaped.isNotBlank()) strings.add(unescaped)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (strings.isNotEmpty()) strings.joinToString(" ") else extractLegacyBinary(data)
        } catch (_: Exception) {
            extractLegacyBinary(data)
        }
    }

    fun extractLegacyBinary(data: ByteArray): String {
        // Scrapes printable ASCII and UTF-16LE runs (>= 4 characters)
        val extracted = mutableListOf<String>()

        // 1. ASCII scraper
        val asciiSb = StringBuilder()
        for (b in data) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in ' '..'~' || c == '\n' || c == '\r' || c == '\t') {
                asciiSb.append(c)
            } else {
                if (asciiSb.length >= 4) {
                    val str = asciiSb.toString().trim()
                    if (str.length >= 4 && isInformativeToken(str)) {
                        extracted.add(str)
                    }
                }
                asciiSb.setLength(0)
            }
        }
        if (asciiSb.length >= 4) {
            val str = asciiSb.toString().trim()
            if (str.length >= 4 && isInformativeToken(str)) extracted.add(str)
        }

        // 2. UTF-16LE scraper
        var i = 0
        val utf16Sb = StringBuilder()
        while (i < data.size - 1) {
            val low = data[i].toInt() and 0xFF
            val high = data[i + 1].toInt() and 0xFF
            if (high == 0 && (low in 32..126 || low == 10 || low == 13 || low == 9)) {
                utf16Sb.append(low.toChar())
                i += 2
            } else {
                if (utf16Sb.length >= 4) {
                    val str = utf16Sb.toString().trim()
                    if (str.length >= 4 && isInformativeToken(str)) {
                        extracted.add(str)
                    }
                }
                utf16Sb.setLength(0)
                i += 1
            }
        }
        if (utf16Sb.length >= 4) {
            val str = utf16Sb.toString().trim()
            if (str.length >= 4 && isInformativeToken(str)) extracted.add(str)
        }

        return extracted.distinct().joinToString(" ")
    }

    private fun cleanXml(xml: String): String {
        // Replace paragraph and line break elements with newline
        var text = xml.replace(Regex("""<(?:/w:p|w:br|/p:p|/a:p)[^>]*>""", RegexOption.IGNORE_CASE), "\n")
        // Strip remaining XML tags
        text = XML_TAG_PATTERN.matcher(text).replaceAll(" ")
        // Unescape standard XML entities
        text = unescapeXml(text)
        // Normalize whitespace per line
        return text.lines()
            .map { WHITESPACE_PATTERN.matcher(it).replaceAll(" ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun isInformativeToken(s: String): Boolean {
        // Exclude system tokens or binary metadata noise
        val lower = s.lowercase()
        if (lower.startsWith("root entry") || lower.startsWith("worddocument") ||
            lower.startsWith("compobj") || lower.startsWith("summaryinformation")
        ) return false
        val alphanumericCount = s.count { it.isLetterOrDigit() }
        return alphanumericCount >= 3
    }
}
