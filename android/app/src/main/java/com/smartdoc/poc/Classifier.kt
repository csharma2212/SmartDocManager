package com.smartdoc.poc

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

object Classifier {
    const val FALLBACK = "uncategorizable input"
    val categories = setOf("Medical Record", "Government-issued Document", "Organization-issued ID Card", "Legal Document", FALLBACK)
    data class Result(val name:String, val category:String=FALLBACK, val specifics:String="", val status:String="PROCESSING_ERROR")
    fun validate(category:String):Boolean = category in categories
    fun classify(name:String, image:ByteArray, apiKey:String, model:String):Result {
        if(apiKey.isBlank() || model.isBlank()) return Result(name,specifics="Missing API key or model",status="INPUT_ERROR")
        var connection:HttpURLConnection? = null
        return try {
            val prompt="Classify document into exactly one category: ${categories.joinToString()}. Government citizen IDs take precedence over organization IDs; employer/student badges are organization-issued. Use uncategorizable input for blank, unclear or unrelated documents. Do not invent details or repeat personal identifiers. Return only JSON object with category and specifics (short visible evidence)."
            val parts=org.json.JSONArray().put(JSONObject().put("text",prompt)).put(JSONObject().put("inline_data",JSONObject().put("mime_type","image/jpeg").put("data",Base64.encodeToString(image,Base64.NO_WRAP))))
            val body=JSONObject().put("contents",org.json.JSONArray().put(JSONObject().put("parts",parts))).put("generationConfig",JSONObject().put("temperature",0).put("responseMimeType","application/json"))
            connection=(URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection)
            connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=75000
            connection.setRequestProperty("x-goog-api-key",apiKey);connection.setRequestProperty("Content-Type","application/json");connection.doOutput=true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if(connection.responseCode!=200) Result(name,specifics="Gemini HTTP ${connection.responseCode}",status="API_ERROR")
            else {
                val response=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val content=response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val obj=JSONObject(content)
                val category=obj.getString("category");val evidence=obj.optString("specifics","")
                if(!validate(category)||evidence.length>1200) Result(name,specifics="Invalid model response",status="INVALID_RESPONSE")
                else Result(name,category,evidence,if(category==FALLBACK) "UNCATEGORIZABLE" else "OK")
            }
        } catch(e:Exception) { Result(name,specifics="Classification failed; check network or input",status="PROCESSING_ERROR") }
        finally { connection?.disconnect() }
    }
}
