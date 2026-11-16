package com.smartdoc.poc

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ParsedDate(val year: Int, val month: Int, val day: Int) {
    fun toIso(): String = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
}

object DateSearchHelper {

    private val MONTH_MAP = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    /**
     * Parses a date string entered by the user in any human or technical format.
     * Supports:
     * - Day-Month-Year (01-05-2026, 01/05/2026, 01.05.2026, 1/5/26)
     * - Month-Day-Year (05-01-2026, 05/01/2026, 5/1/26)
     * - Year-Month-Day (2026-05-01, 2026/05/01, 2026.05.01, 20260501)
     * - Alphanumeric & Written (1 May 2026, 1st May 2026, May 1 2026, 01-May-2026, 01 May '26, Friday 1 May 2026)
     */
    fun parseUserDate(input: String): List<ParsedDate> {
        val clean = input.trim()
            .replace(Regex("""^(monday|tuesday|wednesday|thursday|friday|saturday|sunday),?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (clean.length < 4) return emptyList()

        val results = mutableListOf<ParsedDate>()

        // 1. ISO format: 20260501
        val continuousMatch = Regex("""^\b(20\d{2})(\d{2})(\d{2})\b$""").find(clean)
        if (continuousMatch != null) {
            val y = continuousMatch.groupValues[1].toInt()
            val m = continuousMatch.groupValues[2].toInt()
            val d = continuousMatch.groupValues[3].toInt()
            if (m in 1..12 && d in 1..31) {
                results.add(ParsedDate(y, m, d))
                return results
            }
        }

        // 2. Alphanumeric written format: "1 May 2026", "1st May 2026", "01-May-2026", "01 May '26", "15Nov26"
        val alphaDayFirst = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[-/\s]*([a-zA-Z]{3,})[-/\s,'"]*(\d{2,4})\b""").find(clean)
        if (alphaDayFirst != null) {
            val d = alphaDayFirst.groupValues[1].toIntOrNull() ?: 0
            val mStr = alphaDayFirst.groupValues[2].lowercase()
            val yRaw = alphaDayFirst.groupValues[3].toIntOrNull() ?: 0
            val y = if (yRaw < 100) 2000 + yRaw else yRaw
            val m = MONTH_MAP[mStr] ?: MONTH_MAP[mStr.take(3)] ?: 0
            if (m in 1..12 && d in 1..31 && y in 1950..2050) {
                results.add(ParsedDate(y, m, d))
                return results
            }
        }

        // 3. Alphanumeric Month first: "May 1, 2026", "May 1st 2026"
        val alphaMonthFirst = Regex("""\b([a-zA-Z]{3,})[-/\s]+(\d{1,2})(?:st|nd|rd|th)?[-/\s,'"]+(\d{2,4})\b""").find(clean)
        if (alphaMonthFirst != null) {
            val mStr = alphaMonthFirst.groupValues[1].lowercase()
            val d = alphaMonthFirst.groupValues[2].toIntOrNull() ?: 0
            val yRaw = alphaMonthFirst.groupValues[3].toIntOrNull() ?: 0
            val y = if (yRaw < 100) 2000 + yRaw else yRaw
            val m = MONTH_MAP[mStr] ?: MONTH_MAP[mStr.take(3)] ?: 0
            if (m in 1..12 && d in 1..31 && y in 1950..2050) {
                results.add(ParsedDate(y, m, d))
                return results
            }
        }

        // 4. ISO Year-first with separators: 2026-05-01, 2026/05/01, 2026.05.01
        val isoSeparated = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""").find(clean)
        if (isoSeparated != null) {
            val y = isoSeparated.groupValues[1].toInt()
            val m = isoSeparated.groupValues[2].toInt()
            val d = isoSeparated.groupValues[3].toInt()
            if (m in 1..12 && d in 1..31) {
                results.add(ParsedDate(y, m, d))
                return results
            }
        }

        // 5. Numeric Day-first or Month-first: 01-05-2026, 01/05/2026, 01.05.2026, 1/5/26
        val numericMatch = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""").find(clean)
        if (numericMatch != null) {
            val num1 = numericMatch.groupValues[1].toInt()
            val num2 = numericMatch.groupValues[2].toInt()
            val yRaw = numericMatch.groupValues[3].toInt()
            val y = if (yRaw < 100) 2000 + yRaw else yRaw

            if (num1 > 12 && num2 in 1..12) {
                // Definitely DD-MM-YYYY
                results.add(ParsedDate(y, num2, num1))
            } else if (num2 > 12 && num1 in 1..12) {
                // Definitely MM-DD-YYYY
                results.add(ParsedDate(y, num1, num2))
            } else if (num1 in 1..12 && num2 in 1..12) {
                // Could be either DD-MM-YYYY or MM-DD-YYYY: add both so user's query succeeds!
                results.add(ParsedDate(y, num2, num1))
                if (num1 != num2) {
                    results.add(ParsedDate(y, num1, num2))
                }
            }
        }

        return results
    }

    /**
     * Extracts date components from a file's modified timestamp (Date Created/Modified).
     */
    fun getDateCreated(modifiedEpoch: Long): ParsedDate? {
        if (modifiedEpoch <= 0L) return null
        val cal = Calendar.getInstance().apply { timeInMillis = modifiedEpoch }
        return ParsedDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * Extracts all dates present in document metadata (Date Issued).
     * Looks in proposed filename, original filename, and extracted text/response.
     */
    fun extractIssuedDates(doc: Doc): List<ParsedDate> {
        val targets = listOf(doc.proposed, doc.name, doc.responseReceived, doc.extractedText, doc.metadata)
        val found = mutableListOf<ParsedDate>()

        for (t in targets) {
            if (t.isBlank()) continue

            // Match YYYY-MM-DD
            val mIso = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""").findAll(t)
            for (m in mIso) {
                val y = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val d = m.groupValues[3].toInt()
                if (month in 1..12 && d in 1..31) {
                    found.add(ParsedDate(y, month, d))
                }
            }

            // Match DD-MM-YYYY
            val mDmy = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](20\d{2})\b""").findAll(t)
            for (m in mDmy) {
                val d = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val y = m.groupValues[3].toInt()
                if (month in 1..12 && d in 1..31) {
                    found.add(ParsedDate(y, month, d))
                }
            }

            // Match Alphanumeric: DD-Mon-YYYY or DD Mon YYYY or DDMonYY
            val mAlpha = Regex("""\b(\d{1,2})[-/\s]*([a-zA-Z]{3,})[-/\s]*(20\d{2}|\d{2})\b""").findAll(t)
            for (m in mAlpha) {
                val d = m.groupValues[1].toInt()
                val mStr = m.groupValues[2].lowercase()
                var y = m.groupValues[3].toInt()
                if (y < 100) y += 2000
                val month = MONTH_MAP[mStr] ?: MONTH_MAP[mStr.take(3)] ?: 0
                if (month in 1..12 && d in 1..31) {
                    found.add(ParsedDate(y, month, d))
                }
            }
        }

        return found.distinct()
    }

    /**
     * Checks if a document matches the query via substring/word matching OR date matching.
     */
    fun matchesSearch(
        doc: Doc,
        query: String,
        dateMode: String // "ANY", "ISSUED", "CREATED"
    ): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true

        // 1. Check Date matching if query parses as a date
        val candidateDates = parseUserDate(q)
        if (candidateDates.isNotEmpty()) {
            val checkIssued = dateMode == "ANY" || dateMode == "ISSUED"
            val checkCreated = dateMode == "ANY" || dateMode == "CREATED"

            if (checkIssued) {
                val issuedDates = extractIssuedDates(doc)
                if (candidateDates.any { c -> issuedDates.any { it.year == c.year && it.month == c.month && it.day == c.day } }) {
                    return true
                }
            }

            if (checkCreated) {
                val createdDate = getDateCreated(doc.modified)
                if (createdDate != null && candidateDates.any { it.year == createdDate.year && it.month == createdDate.month && it.day == createdDate.day }) {
                    return true
                }
            }
        }

        // 2. Substring & word matching across metadata
        val lowerQ = q.lowercase()
        if (doc.name.lowercase().contains(lowerQ)) return true
        if (doc.category.lowercase().contains(lowerQ)) return true
        if (doc.proposed.lowercase().contains(lowerQ)) return true
        if (doc.custom.lowercase().contains(lowerQ)) return true
        if (doc.extractedText.lowercase().contains(lowerQ)) return true
        if (doc.responseReceived.lowercase().contains(lowerQ)) return true
        if (doc.promptSent.lowercase().contains(lowerQ)) return true
        if (doc.metadata.lowercase().contains(lowerQ)) return true

        return false
    }

    /**
     * Returns matching search auto-suggestions as user types.
     * Sourced strictly and directly from the metadata of received documents.
     */
    fun getSearchSuggestions(query: String, docs: List<Doc>): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val candidates = LinkedHashSet<String>()

        // Sourced exclusively from received document metadata (no static taxonomy)
        for (d in docs) {
            // 1. Categories received for scanned documents
            if (d.category.isNotBlank() && d.category.lowercase().contains(q) && !d.category.equals(q, ignoreCase = true)) {
                candidates.add(d.category)
            }
            // 2. Proposed names in scanned documents
            if (d.proposed.isNotBlank() && d.proposed != "N/A" && d.proposed.lowercase().contains(q) && !d.proposed.equals(q, ignoreCase = true)) {
                candidates.add(d.proposed.substringBeforeLast('.'))
            }
            // 3. Original file names
            if (d.name.isNotBlank() && d.name.lowercase().contains(q) && !d.name.equals(q, ignoreCase = true)) {
                candidates.add(d.name.substringBeforeLast('.'))
            }
            // 4. Custom renamed labels if set
            if (d.custom.isNotBlank() && d.custom.lowercase().contains(q) && !d.custom.equals(q, ignoreCase = true)) {
                candidates.add(d.custom)
            }
            // 5. Extracted document metadata & key entities (Doctor, Patient, Hospital, Lab, Test, Lessee, Lessor, Issuer, etc.)
            if (d.metadata.isNotBlank()) {
                val matches = Regex(""""[a-zA-Z0-9_-]+"\s*:\s*"([^"]+)"""").findAll(d.metadata)
                for (m in matches) {
                    val value = m.groupValues[1].trim()
                    if (value.isNotBlank() && value.length in 2..50 && value.lowercase().contains(q) && !value.equals(q, ignoreCase = true)) {
                        candidates.add(value)
                    }
                }
            }
            // 6. Distinct keywords/tokens from extracted document text
            if (d.extractedText.isNotBlank()) {
                val words = d.extractedText.split(Regex("""[\s,;:|]+"""))
                for (w in words) {
                    val cleanW = w.trim().trim('.', '(', ')', '[', ']', '"', '\'', '-', '_')
                    if (cleanW.length in 3..35 && cleanW.lowercase().startsWith(q) && !cleanW.equals(q, ignoreCase = true)) {
                        candidates.add(cleanW)
                        if (candidates.size >= 12) break
                    }
                }
            }
        }

        return candidates.take(6).toList()
    }
}
