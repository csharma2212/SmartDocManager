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
    var key by remember { mutableStateOf("") } // never store plaintext key on disk
    var model by remember { mutableStateOf("gemini-3-flash-preview") }
    var status by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<Classifier.Result>()) }
    var progress by remember { mutableIntStateOf(0) }
    var lowResources by remember { mutableStateOf(false) }
    val scope= rememberCoroutineScope()
    val checkResources: () -> Unit = {
    val info = ActivityManager.MemoryInfo()
    
    val activityManager = 
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
    activityManager.getMemoryInfo(info)
    
    val storage = StatFs(context.filesDir.path).availableBytes
    
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
                val result=withContext(Dispatchers.IO) { try { Classifier.classify(name,prepareImage(context,uri),key,model) } catch(e:Exception) { Classifier.Result(name,specifics="Cannot read document",status="INPUT_ERROR") } }
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
            OutlinedTextField(value=key,onValueChange={key=it},label={Text("Gemini API key (not saved)")},singleLine=true)
            OutlinedTextField(value=model,onValueChange={model=it},label={Text("Gemini model ID")},singleLine=true)
            Button(onClick = checkResources) {
    Text("Check resources / Recheck")
}
            if(lowResources) Text("This app requires minimum space and memory for limited use. Close unused apps or free storage and Recheck.")
            Button(onClick = {picker.launch(arrayOf("application/pdf","image/png","image/jpeg","image/webp"))},enabled=key.isNotBlank()) { Text("Select and classify documents") }
            Text(status)
            Text("Processed: $progress")
            for(r in results) { HorizontalDivider();Text("${r.name} — ${r.category}");Text("${r.status}: ${r.specifics}") }
        }
    }}
}
