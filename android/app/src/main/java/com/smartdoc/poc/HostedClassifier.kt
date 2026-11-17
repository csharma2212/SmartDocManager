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
        val reqJson = JSONObject()
            .put("filename", name)
            .put("provider", preferredProvider)
            .put("text", extracted)
        val payloadStr = reqJson.toString(2)

        if (!endpoint.startsWith("https://") || token.isBlank()) {
            val errPayload = JSONObject()
                .put("filename", name)
                .put("provider", preferredProvider)
                .put("auth_token", if (token.isBlank()) "MISSING" else "PRESENT")
                .put("endpoint", endpoint)
                .toString(2)
            val errResp = JSONObject()
                .put("error", "Unauthorized / Configuration Missing")
                .put("message", if (token.isBlank()) "Google Sign-In identity token is missing or expired. Please sign in again." else "Valid HTTPS endpoint URL is required.")
                .toString(2)
            return Classifier.Result(name, specifics = "HTTPS backend and token required", status = "UNAUTHORIZED", promptSent = errPayload, rawResponse = errResp)
        }
        if (extracted.isBlank()) {
            val emptyResp = JSONObject()
                .put("status", "UNCATEGORIZABLE")
                .put("specifics", "No readable text found in document")
                .put("action", "Cloud AI request omitted to conserve daily quota")
                .toString(2)
            return Classifier.Result(name, specifics = "No readable text found", status = "UNCATEGORIZABLE", promptSent = payloadStr, rawResponse = emptyResp)
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
                it.write(reqJson.toString().toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode != 200) {
                val errBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (_: Exception) { "" }
                val formattedErr = try {
                    if (errBody.trim().startsWith("{")) JSONObject(errBody).toString(2)
                    else if (errBody.trim().startsWith("[")) org.json.JSONArray(errBody).toString(2)
                    else errBody
                } catch (_: Exception) { errBody }

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
                    status = if (connection.responseCode == 429) "QUOTA_EXHAUSTED" else if (connection.responseCode == 401) "UNAUTHORIZED" else "API_ERROR",
                    promptSent = payloadStr,
                    rawResponse = if (formattedErr.isNotBlank()) formattedErr else "HTTP ${connection.responseCode}"
                )
            } else {
                val rawResp = connection.inputStream.bufferedReader().use { it.readText() }
                val formattedResp = try {
                    if (rawResp.trim().startsWith("{")) JSONObject(rawResp).toString(2)
                    else if (rawResp.trim().startsWith("[")) org.json.JSONArray(rawResp).toString(2)
                    else rawResp
                } catch (_: Exception) { rawResp }

                val response = JSONObject(rawResp)
                val rawCategory = response.optString("category").trim()
                val docType = response.optString("document_type").ifBlank { response.optString("document_kind") }.trim()
                val metaObj = response.optJSONObject("metadata")
                val issuedDate = response.optString("issued_date").ifBlank { metaObj?.optString("issued_date", "") ?: "" }.trim()
                val specifics = response.optString("specifics").take(2000)
                var suggested = response.optString("suggested_name").ifBlank { response.optString("proposed_name") }.trim()
                val metaStr = metaObj?.toString() ?: ""

                if (rawCategory.isBlank()) {
                    Classifier.Result(name, specifics = "Invalid classification response: empty category", status = "INVALID_RESPONSE", promptSent = payloadStr, rawResponse = formattedResp, metadata = metaStr)
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
                        promptSent = payloadStr,
                        rawResponse = formattedResp,
                        metadata = metaStr
                    )
                }
            }
        } catch (e: Exception) {
            val errJson = JSONObject()
                .put("error", "Exception connecting to hosted service")
                .put("message", e.message ?: "Unknown network exception")
                .put("exception_class", e.javaClass.simpleName)
                .toString(2)
            Classifier.Result(name, specifics = "OCR or hosted service unavailable: ${e.message}", status = "PROCESSING_ERROR", promptSent = payloadStr, rawResponse = errJson)
        } finally {
            connection?.disconnect()
        }
    }
}
