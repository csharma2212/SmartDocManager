package com.smartdoc.poc

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

fun isCategorizedDoc(d: Doc): Boolean {
    if (d.status.equals("Duplicate", ignoreCase = true)) return false
    val cat = d.category.trim()
    if (cat.isBlank()) return false
    if (cat.equals("Other Document", ignoreCase = true)) return false
    if (cat.equals("Uncategorized", ignoreCase = true)) return false
    if (cat.equals("Unclassified", ignoreCase = true)) return false
    if (cat.equals("uncategorizable input", ignoreCase = true)) return false
    if (d.proposed.trim().equals("N/A", ignoreCase = true)) return false
    if (d.status.equals("Error", ignoreCase = true)) return false
    return true
}

fun getMimeTypeForDoc(name: String, type: String = ""): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "doc" -> "application/msword"
        "xls" -> "application/vnd.ms-excel"
        "ppt" -> "application/vnd.ms-powerpoint"
        "txt" -> "text/plain"
        else -> if (type.equals("PDF", ignoreCase = true)) "application/pdf" else "*/*"
    }
}

fun getEffectiveExportName(doc: Doc): String {
    val origName = doc.name.ifBlank { "document" }
    val origExt = if (origName.contains('.')) {
        origName.substring(origName.lastIndexOf('.'))
    } else {
        if (doc.type.equals("PDF", ignoreCase = true)) ".pdf" else ".jpg"
    }

    val chosen = if (doc.custom.isNotBlank()) {
        doc.custom.trim()
    } else if (doc.proposed.isNotBlank() && !doc.proposed.equals("N/A", ignoreCase = true) && !doc.proposed.equals("...", ignoreCase = true)) {
        doc.proposed.trim()
    } else {
        origName
    }

    return if (chosen.endsWith(origExt, ignoreCase = true)) {
        chosen
    } else {
        val clean = chosen.replace(Regex("""\.[a-zA-Z0-9]{2,4}$"""), "")
        "$clean$origExt"
    }
}

fun prepareExportUri(context: Context, doc: Doc): Uri {
    val exportName = getEffectiveExportName(doc)
    val exportDir = File(context.cacheDir, "shared_docs")
    if (!exportDir.exists()) {
        exportDir.mkdirs()
    }
    val exportFile = File(exportDir, exportName)

    var copied = false
    val srcUri = Uri.parse(doc.uri)
    try {
        if (srcUri.scheme == "content" || srcUri.scheme == "file") {
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                exportFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            copied = true
        }
    } catch (_: Exception) {}

    if (!copied) {
        try {
            val srcPath = srcUri.path ?: doc.uri
            val f = File(srcPath)
            if (f.exists()) {
                f.copyTo(exportFile, overwrite = true)
                copied = true
            }
        } catch (_: Exception) {}
    }

    return FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)
}

fun returnSingleFile(activity: Activity, doc: Doc) {
    try {
        val uri = prepareExportUri(activity, doc)
        val name = getEffectiveExportName(doc)
        val callingPkg = activity.callingPackage
        if (!callingPkg.isNullOrBlank()) {
            try {
                activity.grantUriPermission(callingPkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
        }
        val resultIntent = Intent().apply {
            data = uri
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            clipData = ClipData.newUri(activity.contentResolver, name, uri)
        }
        activity.setResult(Activity.RESULT_OK, resultIntent)
        Toast.makeText(activity, "Selected: $name", Toast.LENGTH_SHORT).show()
        activity.finish()
    } catch (e: Exception) {
        Log.e("SmartDoc", "Error returning single file: ${e.message}", e)
        Toast.makeText(activity, "Failed to select file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun returnMultipleFiles(activity: Activity, selectedDocs: List<Doc>) {
    if (selectedDocs.isEmpty()) return
    if (selectedDocs.size == 1) {
        returnSingleFile(activity, selectedDocs[0])
        return
    }
    try {
        val pairs = selectedDocs.map { prepareExportUri(activity, it) to getEffectiveExportName(it) }
        val callingPkg = activity.callingPackage
        if (!callingPkg.isNullOrBlank()) {
            pairs.forEach { (u, _) ->
                try {
                    activity.grantUriPermission(callingPkg, u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
        }
        val first = pairs[0]
        val clipData = ClipData.newUri(activity.contentResolver, first.second, first.first)
        for (i in 1 until pairs.size) {
            clipData.addItem(ClipData.Item(pairs[i].first))
        }
        val resultIntent = Intent().apply {
            data = first.first
            this.clipData = clipData
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        activity.setResult(Activity.RESULT_OK, resultIntent)
        Toast.makeText(activity, "Selected ${selectedDocs.size} files", Toast.LENGTH_SHORT).show()
        activity.finish()
    } catch (e: Exception) {
        Log.e("SmartDoc", "Error returning multiple files: ${e.message}", e)
        Toast.makeText(activity, "Failed to select files: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareMultipleFiles(context: Context, selectedDocs: List<Doc>) {
    if (selectedDocs.isEmpty()) return
    if (selectedDocs.size > 20) {
        Toast.makeText(context, "At a time only 20 files can be exported.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        if (selectedDocs.size == 1) {
            val doc = selectedDocs[0]
            val uri = prepareExportUri(context, doc)
            val name = getEffectiveExportName(doc)
            val mime = getMimeTypeForDoc(name, doc.type)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Document").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } else {
            val uris = ArrayList<Uri>()
            val mimes = mutableSetOf<String>()
            for (doc in selectedDocs) {
                uris.add(prepareExportUri(context, doc))
                mimes.add(getMimeTypeForDoc(getEffectiveExportName(doc), doc.type))
            }
            val mime = if (mimes.size == 1) mimes.first() else "*/*"
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${selectedDocs.size} Documents").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        Log.e("SmartDoc", "Error sharing documents: ${e.message}", e)
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun restoreLibraryDocs(context: Context): List<Doc> {
    return try {
        val a = JSONArray(context.getSharedPreferences("smartdoc_library", Context.MODE_PRIVATE).getString("items", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val st = o.optString("status", "False")
            val safeStatus = if (st == "Processing") "False" else st
            Doc(
                uri = o.getString("uri"),
                name = o.getString("name"),
                type = o.getString("type"),
                modified = o.optLong("modified"),
                size = o.optLong("size"),
                category = o.optString("category"),
                proposed = o.optString("proposed"),
                custom = o.optString("custom"),
                status = safeStatus,
                digest = o.optString("digest"),
                promptSent = o.optString("promptSent"),
                responseReceived = o.optString("responseReceived"),
                extractedText = o.optString("extractedText"),
                dimensions = o.optString("dimensions"),
                metadata = o.optString("metadata", "")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun openFdSafe(context: Context, uri: Uri): ParcelFileDescriptor {
    return if (uri.scheme == "file" && uri.path != null) {
        ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
    } else if (uri.scheme == "content") {
        context.contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException("Cannot open descriptor for $uri")
    } else {
        val f = File(uri.path ?: uri.toString())
        if (f.exists()) {
            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            throw FileNotFoundException("File not found on storage: $uri")
        }
    }
}
