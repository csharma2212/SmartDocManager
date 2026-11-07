package com.smartdoc.poc

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { Screen() } }
}

private fun prepareImage(context:Context, uri:android.net.Uri):ByteArray {
    val type=context.contentResolver.getType(uri) ?: ""
    val bitmap:Bitmap = if(type=="application/pdf" || uri.toString().lowercase().endsWith(".pdf")) {
        val descriptor=context.contentResolver.openFileDescriptor(uri,"r") ?: error("Cannot open PDF")
        descriptor.use { fd -> PdfRenderer(fd).use { pdf ->
            require(pdf.pageCount>0)
            pdf.openPage(0).use { page ->
                val scale=minOf(1.5f,1400f/maxOf(page.width,page.height))
                val image=Bitmap.createBitmap(maxOf(1,(page.width*scale).toInt()),maxOf(1,(page.height*scale).toInt()),Bitmap.Config.ARGB_8888)
                image.eraseColor(Color.WHITE);page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image
            }
        }}
    } else {
        val input=context.contentResolver.openInputStream(uri) ?: error("Cannot open image")
        input.use { BitmapFactory.decodeStream(it) ?: error("Unsupported image") }
    }
    val factor=minOf(1f,1400f/maxOf(bitmap.width,bitmap.height))
    val resized=if(factor<1f) Bitmap.createScaledBitmap(bitmap,(bitmap.width*factor).toInt(),(bitmap.height*factor).toInt(),true) else bitmap
    val stream=ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,78,stream)
    return stream.toByteArray()
}

@Composable fun Screen() {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("smartdocmanager",Context.MODE_PRIVATE) }
    var acknowledged by remember { mutableStateOf(prefs.getBoolean("ack",false)) }
    var key by remember { mutableStateOf("") }
    var useHosted by remember { mutableStateOf(false) }
    var backendUrl by remember { mutableStateOf("") }
    var backendToken by remember { mutableStateOf("") } // never store plaintext key on disk
    var model by remember { mutableStateOf("gemini-3-flash-preview") }
    var status by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Classifier.Result>()) }
    var progress by remember { mutableIntStateOf(0) }
    var lowResources by remember { mutableStateOf(false) }
    var offlineRequested by remember { mutableStateOf(prefs.getBoolean("offline_requested",false)) }
    var offlineProfile by remember { mutableStateOf("E2B") }
    var availableRamGb by remember { mutableStateOf(0.0) }
    var availableStorageGb by remember { mutableStateOf(0.0) }
    val scope= rememberCoroutineScope()
    val checkResources: () -> Unit = {
    val info = ActivityManager.MemoryInfo()
    
    val activityManager = 
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
    activityManager.getMemoryInfo(info)
    
    val storage = StatFs(context.filesDir.path).availableBytes
    
    availableRamGb = info.totalMem.toDouble() / (1024.0*1024*1024)
    availableStorageGb = storage.toDouble() / (1024.0*1024*1024)
    lowResources = info.lowMemory || 
        storage < 150L * 1024 * 1024
        
    status = "Available memory: ${info.availMem / 1024 / 1024} MB; " + 
        "free app storage: ${storage / 1024 / 1024} MB"
}

    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if(uris.isNotEmpty()) scope.launch {
            results=emptyList();progress=0
            for((index,uri) in uris.withIndex()) {
                val name=uri.lastPathSegment?.substringAfterLast('/') ?: "document"
                status="Processing ${index+1}/${uris.size}: $name"
                val result=withContext(Dispatchers.IO) { try { if(useHosted) HostedClassifier.classify(name,prepareImage(context,uri),backendUrl,backendToken) else Classifier.classify(name,prepareImage(context,uri),key,model) } catch(e:Exception) { Classifier.Result(name,specifics="Cannot read document",status="INPUT_ERROR") } }
                results=results+result;progress=index+1
            }
            status="Completed ${uris.size} files"
        }
    }
    MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("SmartDocManager",style=MaterialTheme.typography.headlineMedium)
        Text("Cloud mode: documents are sent to Google Gemini. Only use approved, synthetic or redacted documents.")
        if(!acknowledged) {
            Text("Free-tier data may be used by the provider. Do not upload sensitive or company-controlled files without authorization.")
            Button(onClick={prefs.edit().putBoolean("ack",true).apply().also { acknowledged=true }}) { Text("I understand") }
        } else {
            Row { Checkbox(checked=useHosted,onCheckedChange={useHosted=it});Text("Use hosted AI (no Gemini account)") }
            if(useHosted) {
                Text("OCR runs on this phone; extracted text is sent to your backend and Groq. Use only documents authorized for cloud processing.")
                OutlinedTextField(value=backendUrl,onValueChange={backendUrl=it},label={Text("Backend HTTPS URL")},singleLine=true)
                OutlinedTextField(value=backendToken,onValueChange={backendToken=it},label={Text("Backend access token (not saved)")},singleLine=true)
            } else {
                OutlinedTextField(value=key,onValueChange={key=it},label={Text("Gemini API key (not saved)")},singleLine=true)
            }
            OutlinedTextField(value=model,onValueChange={model=it},label={Text("Gemini model ID")},singleLine=true)
            Button(onClick = checkResources) {
    Text("Check resources / Recheck")
}
            if(lowResources) Text("Low resources for processing. Close unused apps or free storage and recheck.")
            HorizontalDivider()
            Text("Optional offline vision AI (installation planning)",style=MaterialTheme.typography.titleMedium)
            Text("Offline LLM inference is NOT installed or implemented in this build. Enabling this preference does not download a model or classify offline.")
            Row { Checkbox(checked=offlineRequested,onCheckedChange={ offlineRequested=it; prefs.edit().putBoolean("offline_requested",it).apply() }); Text("I want optional offline model support") }
            if(offlineRequested) {
                Text("Gemma 3n E2B: ~3.14 GB model; ~5.91 GB estimated peak memory. E4B: ~4.41 GB model; ~6.98 GB estimated peak memory (Google AI Edge Gallery model manifest). Actual compatibility depends on hardware and runtime.")
                Row { RadioButton(selected=offlineProfile=="E2B",onClick={offlineProfile="E2B"});Text("E2B")
                    RadioButton(selected=offlineProfile=="E4B",onClick={offlineProfile="E4B"});Text("E4B") }
                Text("Device total RAM: %.1f GB; available app storage: %.1f GB".format(availableRamGb,availableStorageGb))
                Text(if(availableStorageGb < (if(offlineProfile=="E2B") 4.5 else 6.0)) "Storage likely insufficient for selected model + temporary files" else "Storage estimate passes; device compatibility and free RAM still need validation")
                Text("Cloud classification remains the only available AI backend. No model download is triggered.")
            }
            Button(onClick = {picker.launch(arrayOf("application/pdf","image/png","image/jpeg","image/webp"))},enabled=if(useHosted) backendUrl.startsWith("https://") && backendToken.isNotBlank() else key.isNotBlank()) { Text("Select and classify documents") }
            Text(status)
            Text("Processed: $progress")
            for(r in results) { HorizontalDivider();Text("${r.name} — ${r.category}");Text("${r.status}: ${r.specifics}") }
        }
    }}
}
