package com.smartdoc.poc

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

object HostedClassifier {
    suspend fun classify(name: String, jpeg: ByteArray, endpoint: String, token: String, preferredProvider: String = "gemini"): Classifier.Result =
        classifyPages(name, listOf(jpeg), endpoint, token, preferredProvider)

    suspend fun extractText(pages: List<ByteArray>): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val texts = mutableListOf<String>()
            for (jpeg in pages) {
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
                try {
                    val pageText = withTimeoutOrNull(20000L) {
                        suspendCancellableCoroutine<String> { continuation ->
                            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
                                .addOnFailureListener { if (continuation.isActive) continuation.resume("") }
                        }
                    } ?: ""
                    if (pageText.isNotBlank()) texts.add(pageText)
                } finally {
                    bitmap.recycle()
                }
            }
            texts.joinToString("\n")
        } finally {
            recognizer.close()
        }
    }

    suspend fun classifyPages(name: String, pages: List<ByteArray>, endpoint: String, token: String, preferredProvider: String = "gemini"): Classifier.Result {
        val extracted = extractText(pages)
        return classifyText(name, extracted, endpoint, token, preferredProvider)
    }

    suspend fun classifyText(name: String, extracted: String, endpoint: String, token: String, preferredProvider: String = "gemini"): Classifier.Result {
        if (!endpoint.startsWith("https://") || token.isBlank()) {
            return Classifier.Result(name, specifics = "HTTPS backend and token required", status = "INPUT_ERROR")
        }
        val promptSent = "POST ${endpoint.trimEnd('/')}/v1/classify\nPrimary AI: ${preferredProvider.uppercase()}\nPayload Text (${extracted.length} chars):\n${extracted.take(400)}"
        if (extracted.isBlank()) {
            return Classifier.Result(name, specifics = "No readable text found", status = "UNCATEGORIZABLE", promptSent = promptSent, rawResponse = "Empty document text")
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(endpoint.trimEnd('/') + "/v1/classify").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 25000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Provider", preferredProvider)
            connection.doOutput = true
            connection.outputStream.use {
                val reqJson = JSONObject()
                    .put("text", extracted)
                    .put("provider", preferredProvider)
                it.write(reqJson.toString().toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode != 200) {
                val errBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) { "" }
                val specifics = if (errBody.isNotBlank()) {
                    try {
                        val j = JSONObject(errBody)
                        val activeProvider = preferredProvider.lowercase()
                        val errMsg = if (activeProvider == "groq") {
                            j.optString("groq_message").ifBlank { j.optString("gemini_message").ifBlank { j.optString("message") } }
                        } else {
                            j.optString("gemini_message").ifBlank { j.optString("groq_message").ifBlank { j.optString("message") } }
                        }
                        val errStatus = if (activeProvider == "groq") {
                            j.optString("groq_status").ifBlank { j.optString("gemini_status") }
                        } else {
                            j.optString("gemini_status").ifBlank { j.optString("groq_status") }
                        }
                        val provTitle = if (activeProvider == "groq") "Groq" else "Gemini"
                        if (errMsg.isNotBlank()) "$provTitle HTTP ${connection.responseCode}: $errMsg" + (if (errStatus.isNotBlank()) " ($errStatus)" else "")
                        else "Hosted backend HTTP ${connection.responseCode}"
                    } catch (_: Exception) { "Hosted backend HTTP ${connection.responseCode}: $errBody" }
                } else "Hosted backend HTTP ${connection.responseCode}"

                Classifier.Result(
                    name,
                    specifics = specifics,
                    status = if (connection.responseCode == 429) "QUOTA_EXHAUSTED" else "API_ERROR",
                    promptSent = promptSent,
                    rawResponse = if (errBody.isNotBlank()) errBody else "HTTP ${connection.responseCode}"
                )
            } else {
                val rawResp = connection.inputStream.bufferedReader().use { it.readText() }
                val response = JSONObject(rawResp)
                val rawCategory = response.optString("category").trim()
                val docType = response.optString("document_type").ifBlank { response.optString("document_kind") }.trim()
                val metaObj = response.optJSONObject("metadata")
                val issuedDate = response.optString("issued_date").ifBlank { metaObj?.optString("issued_date", "") ?: "" }.trim()
                val specifics = response.optString("specifics").take(2000)
                var suggested = response.optString("suggested_name").ifBlank { response.optString("proposed_name") }.trim()
                val metaStr = metaObj?.toString() ?: ""

                if (rawCategory.isBlank()) {
                    Classifier.Result(name, specifics = "Invalid classification response: empty category", status = "INVALID_RESPONSE", promptSent = promptSent, rawResponse = rawResp, metadata = metaStr)
                } else {
                    val category = Classifier.normalizeCategory(rawCategory)
                    if (suggested.isBlank() || suggested.length > 40) {
                        suggested = Classifier.generateCompliantProposedName(category, docType, issuedDate, name, extracted)
                    }
                    Classifier.Result(
                        name,
                        category,
                        specifics,
                        if (category == Classifier.FALLBACK) "UNCATEGORIZABLE" else "OK",
                        suggested,
                        promptSent = promptSent,
                        rawResponse = rawResp,
                        metadata = metaStr
                    )
                }
            }
        } catch (e: Exception) {
            Classifier.Result(name, specifics = "OCR or hosted service unavailable: ${e.message}", status = "PROCESSING_ERROR", promptSent = promptSent, rawResponse = "Exception: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }
}
