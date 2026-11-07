package com.smartdoc.poc

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

object HostedClassifier {
    suspend fun classify(name:String, jpeg:ByteArray, endpoint:String, token:String):Classifier.Result {
        if(!endpoint.startsWith("https://") || token.isBlank()) return Classifier.Result(name,specifics="HTTPS backend and token required",status="INPUT_ERROR")
        val bitmap=BitmapFactory.decodeByteArray(jpeg,0,jpeg.size) ?: return Classifier.Result(name,specifics="Invalid image",status="INPUT_ERROR")
        val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val extracted=try {
            suspendCancellableCoroutine<String> { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap,0))
                    .addOnSuccessListener { if(continuation.isActive) continuation.resume(it.text) }
                    .addOnFailureListener { if(continuation.isActive) continuation.resume("") }
            }
        } finally { recognizer.close(); bitmap.recycle() }
        if(extracted.isBlank()) return Classifier.Result(name,specifics="No readable text found",status="UNCATEGORIZABLE")
        var connection:HttpURLConnection?=null
        return try {
            connection=URL(endpoint.trimEnd('/')+"/v1/classify").openConnection() as HttpURLConnection
            connection.requestMethod="POST";connection.connectTimeout=15000;connection.readTimeout=55000
            connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Content-Type","application/json")
            connection.doOutput=true
            connection.outputStream.use { it.write(JSONObject().put("text",extracted.take(16000)).toString().toByteArray(Charsets.UTF_8)) }
            if(connection.responseCode!=200) Classifier.Result(name,specifics="Hosted backend HTTP ${connection.responseCode}",status="API_ERROR")
            else {
                val response=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val category=response.optString("category");val specifics=response.optString("specifics")
                if(!Classifier.validate(category)||specifics.length>1200) Classifier.Result(name,specifics="Invalid classification response",status="INVALID_RESPONSE")
                else Classifier.Result(name,category,specifics,if(category==Classifier.FALLBACK) "UNCATEGORIZABLE" else "OK")
            }
        } catch(e:Exception) { Classifier.Result(name,specifics="OCR or hosted service unavailable",status="PROCESSING_ERROR") }
        finally { connection?.disconnect() }
    }
}
