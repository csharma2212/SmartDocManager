package com.smartdoc.poc

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SCAN_NOTIFICATION_CHANNEL_ID = "smartdoc_scan_channel"

class MainActivity : ComponentActivity() {
    companion object {
        var isAppInForeground: Boolean = false
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        setContent { LibraryScreen() }
    }
}

data class Doc(
    val uri: String,
    val name: String,
    val type: String,
    val modified: Long,
    val size: Long,
    val category: String = "",
    val proposed: String = "",
    val custom: String = "",
    val status: String = "False",
    val digest: String = "",
    val promptSent: String = "",
    val responseReceived: String = "",
    val extractedText: String = "",
    val dimensions: String = "",
    val metadata: String = ""
)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDateTime(ms: Long): String {
    if (ms <= 0) return "Unknown"
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.US)
    return sdf.format(java.util.Date(ms))
}

private fun getImageResolution(context: Context, uri: Uri): String {
    return try {
        openStream(context, uri).use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth} × ${opts.outHeight} px" else ""
        }
    } catch (_: Exception) { "" }
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "SmartDocManager Notifications"
        val descriptionText = "Notifications when document scanning and processing completes"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(SCAN_NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

private fun notifyScanCompleted(context: Context, total: Int) {
    createNotificationChannel(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val builder = NotificationCompat.Builder(context, SCAN_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("SmartDocManager")
        .setContentText("Scan & processing completed: All $total files processed and categorized.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Scan & processing complete: All $total documents discovered across your device storage have been analyzed and virtually renamed."))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(1001, builder.build())
}

private fun openStream(context: Context, uri: Uri): InputStream {
    return if (uri.scheme == "file" && uri.path != null) {
        val f = File(uri.path!!)
        if (f.exists()) {
            FileInputStream(f)
        } else {
            context.contentResolver.openInputStream(uri) ?: error("Cannot open stream for $uri")
        }
    } else {
        try {
            context.contentResolver.openInputStream(uri) ?: error("Cannot open stream for $uri")
        } catch (e: Exception) {
            if (uri.path != null) {
                val f = File(uri.path!!)
                if (f.exists()) FileInputStream(f) else throw e
            } else throw e
        }
    }
}

private fun openFd(context: Context, uri: Uri): ParcelFileDescriptor {
    return if (uri.scheme == "file" && uri.path != null) {
        ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
    } else {
        context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open descriptor for $uri")
    }
}

private fun documentPages(context: Context, uri: Uri): List<ByteArray> {
    val type = context.contentResolver.getType(uri) ?: ""
    val isPdf = type == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")
    val images = mutableListOf<Bitmap>()
    if (isPdf) {
        openFd(context, uri).use { fd ->
            PdfRenderer(fd).use { pdf ->
                val pagesToRender = minOf(pdf.pageCount, 2)
                for (i in 0 until pagesToRender) {
                    pdf.openPage(i).use { page ->
                        val scale = minOf(1.2f, 1000f / maxOf(page.width, page.height))
                        val b = Bitmap.createBitmap(
                            maxOf(1, (page.width * scale).toInt()),
                            maxOf(1, (page.height * scale).toInt()),
                            Bitmap.Config.ARGB_8888
                        )
                        b.eraseColor(android.graphics.Color.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        images.add(b)
                    }
                }
            }
        }
    } else {
        openStream(context, uri).use { stream ->
            val opts = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            val bmp = BitmapFactory.decodeStream(stream, null, opts) ?: error("Invalid image file")
            images.add(bmp)
        }
    }
    return images.map { bmp ->
        val factor = minOf(1f, 1000f / maxOf(bmp.width, bmp.height))
        val scaled = if (factor < 1) Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * factor).toInt()), maxOf(1, (bmp.height * factor).toInt()), true) else bmp
        val bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 78, it) }.toByteArray()
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
        bytes
    }
}

private suspend fun saveAsync(context: Context, items: List<Doc>) {
    PersistentMetadataManager.save(context, items)
}

val SUPPORTED_DOC_EXTS = setOf("pdf", "jpg", "jpeg", "png", "webp", "docx", "xlsx", "doc", "xls", "pptx", "ppt", "txt")

fun getDocTypeFromExt(ext: String): String {
    return when (ext.lowercase()) {
        "pdf" -> "PDF"
        "png", "jpg", "jpeg", "webp" -> "Image"
        "docx", "doc" -> "Word"
        "xlsx", "xls" -> "Excel"
        "pptx", "ppt" -> "PowerPoint"
        "txt" -> "Text"
        else -> "Document"
    }
}

fun parseTokenExpiryMillis(idToken: String): Long {
    if (idToken.isBlank()) return 0L
    try {
        val parts = idToken.split(".")
        if (parts.size >= 2) {
            val payloadBytes = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
            val exp = json.optLong("exp", 0L)
            if (exp > 0L) {
                return exp * 1000L
            }
        }
    } catch (e: Exception) {
        Log.w("SmartDoc", "Could not parse JWT exp claim: ${e.message}")
    }
    return System.currentTimeMillis() + (3600L * 1000L)
}

private fun restore(context: Context): List<Doc> {
    return PersistentMetadataManager.loadAndVerify(context).first
}

private fun scanDeviceStorage(context: Context): List<Doc> {
    val results = mutableListOf<Doc>()
    val seenPaths = HashSet<String>()
    val supportedExts = SUPPORTED_DOC_EXTS

    val roots = mutableListOf<File>()
    val primary = Environment.getExternalStorageDirectory()
    if (primary != null && primary.exists() && primary.canRead()) {
        roots.add(primary)
    }

    val extDirs = context.getExternalFilesDirs(null)
    for (d in extDirs) {
        if (d != null) {
            val path = d.absolutePath
            val idx = path.indexOf("/Android")
            if (idx > 0) {
                val sdRoot = File(path.substring(0, idx))
                if (sdRoot.exists() && sdRoot.canRead() && sdRoot != primary && sdRoot !in roots) {
                    roots.add(sdRoot)
                }
            }
        }
    }

    fun shouldSkip(fileOrDir: File): Boolean {
        val name = fileOrDir.name
        if (name.startsWith(".")) return true
        if (name.equals("Android", ignoreCase = true)) return true
        if (name.equals(".trash", ignoreCase = true)) return true
        if (name.equals(".thumbnails", ignoreCase = true)) return true
        if (name.equals(".nomedia", ignoreCase = true)) return true
        if (fileOrDir.isDirectory && (name.equals("Duplicates", ignoreCase = true) || name.equals("SmartDocsTransferd", ignoreCase = true)) && fileOrDir.parentFile?.name?.equals("Download", ignoreCase = true) == true) {
            return true
        }
        return false
    }

    fun traverse(dir: File, depth: Int) {
        if (depth > 30) return
        val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        for (f in files) {
            try {
                if (f.isDirectory) {
                    if (!shouldSkip(f)) {
                        traverse(f, depth + 1)
                    }
                } else if (f.isFile) {
                    val n = f.name
                    if (n.startsWith(".")) continue
                    val ext = n.substringAfterLast('.', "").lowercase()
                    if (ext in supportedExts) {
                        val abs = f.absolutePath
                        if (seenPaths.add(abs)) {
                            results.add(
                                Doc(
                                    uri = Uri.fromFile(f).toString(),
                                    name = n,
                                    type = getDocTypeFromExt(ext),
                                    modified = f.lastModified(),
                                    size = f.length()
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    for (r in roots) {
        traverse(r, 0)
    }

    val mediaImages = discoverMediaImages(context)
    for (img in mediaImages) {
        if (seenPaths.add(img.name + "_" + img.size)) {
            results.add(img)
        }
    }

    return results
}

private fun discoverMediaImages(context: Context): List<Doc> {
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val found = mutableListOf<Doc>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_MODIFIED
    )
    try {
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                if (name.substringAfterLast('.', "").lowercase() !in setOf("jpg", "jpeg", "png", "webp")) continue
                val uri = android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idIndex))
                found.add(Doc(uri.toString(), name, "Image", cursor.getLong(dateIndex) * 1000, cursor.getLong(sizeIndex)))
            }
        }
    } catch (_: SecurityException) {}
    return found
}

private fun getFolderNameFromUri(context: Context, uri: Uri): String {
    return try {
        if (uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            if (f.name.isNotBlank()) return f.name
        }
        val docFile = DocumentFile.fromTreeUri(context, uri)
        val name = docFile?.name
        if (!name.isNullOrBlank()) return name
        val docIdRaw = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { uri.lastPathSegment ?: "" }
        val docId = URLDecoder.decode(docIdRaw, "UTF-8")
        if (docId.contains(":")) {
            val part = docId.substringAfterLast("/")
            if (part.isNotBlank()) part else docId.substringAfter(":")
        } else {
            uri.lastPathSegment ?: "Selected Folder"
        }
    } catch (_: Exception) {
        "Selected Folder"
    }
}

private fun getFileFromTreeUri(uri: Uri): File? {
    try {
        if (uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            try { if (!f.exists()) f.mkdirs() } catch (_: Exception) {}
            return f
        }
        val docIdRaw = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            try {
                DocumentsContract.getDocumentId(uri)
            } catch (_: Exception) {
                uri.path ?: ""
            }
        }
        val docId = URLDecoder.decode(docIdRaw, "UTF-8")
        if (docId.startsWith("primary:", ignoreCase = true) || docId.equals("primary", ignoreCase = true)) {
            val rel = if (docId.contains(":")) docId.substringAfter(":").trimStart('/', '\\') else ""
            val f = if (rel.isEmpty()) Environment.getExternalStorageDirectory() else File(Environment.getExternalStorageDirectory(), rel)
            try { if (!f.exists()) f.mkdirs() } catch (_: Exception) {}
            return f
        } else if (docId.startsWith("raw:", ignoreCase = true)) {
            val raw = docId.substringAfter("raw:")
            val f = File(raw)
            try { if (!f.exists()) f.mkdirs() } catch (_: Exception) {}
            return f
        } else if (docId.contains(":")) {
            val parts = docId.split(":", limit = 2)
            val storageId = parts[0]
            val rel = parts[1].trimStart('/', '\\')
            val storageDir = File("/storage/$storageId")
            if (storageDir.exists()) {
                val f = if (rel.isEmpty()) storageDir else File(storageDir, rel)
                try { if (!f.exists()) f.mkdirs() } catch (_: Exception) {}
                return f
            }
        } else if (docId.startsWith("/")) {
            val f = File(docId)
            try { if (!f.exists()) f.mkdirs() } catch (_: Exception) {}
            return f
        }
    } catch (_: Exception) {}
    return null
}

private fun scanFolderRecursively(dir: File): List<Doc> {
    val results = mutableListOf<Doc>()
    val seenPaths = HashSet<String>()
    val supportedExts = SUPPORTED_DOC_EXTS

    fun shouldSkip(fileOrDir: File): Boolean {
        val name = fileOrDir.name
        if (name.startsWith(".")) return true
        if (name.equals("Android", ignoreCase = true)) return true
        if (name.equals(".trash", ignoreCase = true)) return true
        if (name.equals(".thumbnails", ignoreCase = true)) return true
        if (name.equals(".nomedia", ignoreCase = true)) return true
        if (fileOrDir.isDirectory && (name.equals("Duplicates", ignoreCase = true) || name.equals("SmartDocsTransferd", ignoreCase = true)) && fileOrDir.parentFile?.name?.equals("Download", ignoreCase = true) == true) {
            return true
        }
        return false
    }

    fun traverse(d: File, depth: Int) {
        if (depth > 30) return
        val files = try { d.listFiles() } catch (_: Exception) { null } ?: return
        for (f in files) {
            try {
                if (f.isDirectory) {
                    if (!shouldSkip(f)) {
                        traverse(f, depth + 1)
                    }
                } else if (f.isFile) {
                    val n = f.name
                    if (n.startsWith(".")) continue
                    val ext = n.substringAfterLast('.', "").lowercase()
                    if (ext in supportedExts) {
                        val abs = f.absolutePath
                        if (seenPaths.add(abs)) {
                            results.add(
                                Doc(
                                    uri = Uri.fromFile(f).toString(),
                                    name = n,
                                    type = getDocTypeFromExt(ext),
                                    modified = f.lastModified(),
                                    size = f.length()
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    traverse(dir, 0)
    return results
}

private fun scanDocumentFileRecursively(
    docFile: DocumentFile,
    results: MutableList<Doc>,
    seenUris: HashSet<String>,
    depth: Int = 0
) {
    if (depth > 30) return
    val supportedExts = SUPPORTED_DOC_EXTS
    val files = try { docFile.listFiles() } catch (_: Exception) { emptyArray() }
    for (f in files) {
        try {
            val name = f.name ?: continue
            if (name.startsWith(".")) continue
            if (f.isDirectory) {
                if (!name.equals("Android", ignoreCase = true) && !name.equals(".trash", ignoreCase = true) && !name.equals(".nomedia", ignoreCase = true)) {
                    scanDocumentFileRecursively(f, results, seenUris, depth + 1)
                }
            } else if (f.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supportedExts) {
                    val uriStr = f.uri.toString()
                    if (seenUris.add(uriStr)) {
                        results.add(
                            Doc(
                                uri = uriStr,
                                name = name,
                                type = getDocTypeFromExt(ext),
                                modified = f.lastModified(),
                                size = f.length()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

private fun scanFolderUri(context: Context, treeUri: Uri): List<Doc> {
    val file = getFileFromTreeUri(treeUri)
    if (file != null && file.exists() && file.isDirectory) {
        return scanFolderRecursively(file)
    }
    val results = mutableListOf<Doc>()
    val seen = HashSet<String>()
    val root = DocumentFile.fromTreeUri(context, treeUri)
    if (root != null && root.isDirectory) {
        scanDocumentFileRecursively(root, results, seen, 0)
    }
    return results
}

private fun extractDateTimestamp(virtualName: String, fallback: Long): Long {
    // 1. Check ddMMMyy pattern: e.g. 15Nov26 or 16Nov26
    val ddmmmyyRegex = Regex("""\b(0[1-9]|[12]\d|3[01])([A-Za-z]{3})(\d{2})\b""")
    val ddmmmyyMatch = ddmmmyyRegex.find(virtualName)
    if (ddmmmyyMatch != null) {
        try {
            val sdf = SimpleDateFormat("ddMMMyy", Locale.US)
            sdf.isLenient = true
            val parsedDate = sdf.parse(ddmmmyyMatch.value)
            if (parsedDate != null) {
                return parsedDate.time
            }
        } catch (_: Exception) {}
    }

    // 2. Check ISO pattern: YYYY-MM-DD or YYYY_MM_DD or YYYY.MM.DD
    val isoRegex = Regex("""\b(19\d\d|20\d\d)[-_/.](0[1-9]|1[0-2])[-_/.](0[1-9]|[12]\d|3[01])\b""")
    val isoMatch = isoRegex.find(virtualName)
    if (isoMatch != null) {
        val y = isoMatch.groupValues[1].toInt()
        val m = isoMatch.groupValues[2].toInt() - 1
        val d = isoMatch.groupValues[3].toInt()
        val cal = java.util.Calendar.getInstance()
        cal.set(y, m, d, 12, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // 3. Check Indian pattern: DD-MM-YYYY or DD_MM_YYYY or DD.MM.YYYY
    val ddmmyyyy = Regex("""\b(0[1-9]|[12]\d|3[01])[-_/.](0[1-9]|1[0-2])[-_/.](19\d\d|20\d\d)\b""").find(virtualName)
    if (ddmmyyyy != null) {
        val d = ddmmyyyy.groupValues[1].toInt()
        val m = ddmmyyyy.groupValues[2].toInt() - 1
        val y = ddmmyyyy.groupValues[3].toInt()
        val cal = java.util.Calendar.getInstance()
        cal.set(y, m, d, 12, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    return if (fallback > 0L) fallback else System.currentTimeMillis()
}

private data class MoveResult(
    val success: Boolean,
    val movedCount: Int,
    val errorMessage: String = "",
    val updatedDocs: List<Doc> = emptyList()
)

private fun getSmartDocsTransferBaseDir(): File {
    val candidates = listOf(
        File("/storage/emulated/0/Download/SmartDocsTransferd"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SmartDocsTransferd"),
        File(Environment.getExternalStorageDirectory(), "Download/SmartDocsTransferd"),
        File(Environment.getExternalStorageDirectory(), "Downloads/SmartDocsTransferd")
    )
    for (candidate in candidates) {
        try {
            if (!candidate.exists()) candidate.mkdirs()
            if (candidate.exists()) return candidate
        } catch (_: Exception) {}
    }
    val fallback = File("/storage/emulated/0/Download/SmartDocsTransferd")
    try { fallback.mkdirs() } catch (_: Exception) {}
    return fallback
}

private fun sanitizePathComponent(name: String): String {
    return name.replace(':', '-').replace(Regex("""[\\*?"<>|]"""), "-")
}

private fun resolveDestinationDirectory(baseDir: File, inputSubfolder: String): File {
    val trimmed = inputSubfolder.trim()
    if (trimmed.isBlank()) {
        return baseDir
    }

    // 1. Absolute path: e.g. /storage/emulated/0/Download/... or /sdcard/Download/... or /...
    if (trimmed.startsWith("/storage/emulated/0", ignoreCase = true) ||
        trimmed.startsWith("/sdcard", ignoreCase = true) ||
        trimmed.startsWith("/")
    ) {
        val pathFile = File(trimmed)
        val parent = pathFile.parentFile ?: baseDir
        val safeName = sanitizePathComponent(pathFile.name)
        return File(parent, safeName)
    }

    // 2. Relative path: e.g. "161126_01:40:52" or "SmartDocsTransferd/161126_01:40:52"
    val segments = trimmed.split('/', '\\').filter { it.isNotBlank() }
    var currentDir = baseDir
    for (seg in segments) {
        val safeSeg = sanitizePathComponent(seg)
        currentDir = File(currentDir, safeSeg)
    }
    return currentDir
}

private fun safeDeleteOriginal(context: Context, doc: Doc) {
    try {
        val srcUri = Uri.parse(doc.uri)
        if (srcUri.scheme == "file" && srcUri.path != null) {
            val f = File(srcUri.path!!)
            if (f.exists()) f.delete()
        } else if (srcUri.scheme == "content") {
            try {
                DocumentsContract.deleteDocument(context.contentResolver, srcUri)
            } catch (_: Exception) {
                try {
                    context.contentResolver.delete(srcUri, null, null)
                } catch (_: Exception) {
                    val f = File(srcUri.path ?: "")
                    if (f.exists()) f.delete()
                }
            }
        } else {
            val f = File(doc.uri)
            if (f.exists()) f.delete()
        }
    } catch (e: Exception) {
        Log.w("SmartDoc", "Could not delete original file ${doc.name}: ${e.message}")
    }
}

private fun performMoveViaMediaStore(
    context: Context,
    selectedDocs: List<Doc>,
    relativeFolderPath: String
): MoveResult {
    val cleanRelative = relativeFolderPath.trim().trim('/', '\\')
    val finalRelPath = if (cleanRelative.startsWith("Download", ignoreCase = true)) {
        "$cleanRelative/"
    } else {
        "Download/$cleanRelative/"
    }

    val resolver = context.contentResolver
    val stagedEntries = mutableListOf<Triple<Doc, Uri, String>>()
    val uniqueTargetNames = mutableSetOf<String>()

    try {
        for (doc in selectedDocs) {
            var targetName = getEffectiveExportName(doc)
            var dedupeIndex = 1
            val baseName = targetName.substringBeforeLast('.', targetName)
            val ext = targetName.substringAfterLast('.', "")
            val extDot = if (ext.isNotBlank()) ".$ext" else ""
            while (uniqueTargetNames.contains(targetName)) {
                targetName = "${baseName}_$dedupeIndex$extDot"
                dedupeIndex++
            }
            uniqueTargetNames.add(targetName)

            val mimeType = getMimeTypeForDoc(targetName, doc.type)

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, finalRelPath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val itemUri = resolver.insert(targetCollection, values)
                ?: throw IllegalStateException("Could not create MediaStore entry for $targetName in $finalRelPath")

            val srcUri = Uri.parse(doc.uri)
            openStream(context, srcUri).use { inStream ->
                resolver.openOutputStream(itemUri)?.use { outStream ->
                    inStream.copyTo(outStream)
                } ?: throw IllegalStateException("Could not write to destination: $targetName")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    val dateTimestamp = extractDateTimestamp(targetName, doc.modified)
                    if (dateTimestamp > 0L) {
                        put(MediaStore.MediaColumns.DATE_MODIFIED, dateTimestamp / 1000)
                    }
                }
                resolver.update(itemUri, completeValues, null, null)
            }

            stagedEntries.add(Triple(doc, itemUri, targetName))
        }

        // Delete originals now that all transfers succeeded
        for ((doc, _, _) in stagedEntries) {
            safeDeleteOriginal(context, doc)
        }

        val movedDocsMap = stagedEntries.associate { (doc, itemUri, targetName) ->
            doc.uri to doc.copy(
                uri = itemUri.toString(),
                name = targetName,
                proposed = targetName,
                custom = "",
                modified = extractDateTimestamp(targetName, doc.modified),
                size = doc.size
            )
        }

        return MoveResult(
            success = true,
            movedCount = stagedEntries.size,
            updatedDocs = selectedDocs.mapNotNull { movedDocsMap[it.uri] }
        )

    } catch (e: Exception) {
        Log.e("SmartDoc", "MediaStore move failed: ${e.message}", e)
        for ((_, itemUri, _) in stagedEntries) {
            try { resolver.delete(itemUri, null, null) } catch (_: Exception) {}
        }
        return MoveResult(false, 0, "Move failed: ${e.message ?: "Unknown error"}. Originals are untouched.")
    }
}

private fun performAtomicMoveToFileDir(
    context: Context,
    selectedDocs: List<Doc>,
    baseDir: File,
    subfolderName: String
): MoveResult {
    val destDir = resolveDestinationDirectory(baseDir, subfolderName)
    val destDirCreated = try { destDir.exists() || destDir.mkdirs() } catch (_: Exception) { false }

    // If destination directory could not be created via raw File and is under Download, fall back to MediaStore
    if (!destDirCreated || !destDir.exists()) {
        val relPath = when {
            destDir.absolutePath.contains("/Download/SmartDocsTransferd", ignoreCase = true) ->
                "Download/SmartDocsTransferd/${destDir.name}"
            destDir.absolutePath.contains("/Download", ignoreCase = true) ->
                "Download/${destDir.name}"
            else ->
                "Download/SmartDocsTransferd/${destDir.name}"
        }
        Log.i("SmartDoc", "Direct directory creation failed on $destDir. Attempting MediaStore.Downloads with relative path: $relPath")
        val msResult = performMoveViaMediaStore(context, selectedDocs, relPath)
        if (msResult.success) return msResult
    }

    val stagedFiles = mutableListOf<Triple<Doc, File, File>>() // (doc, tempFile, finalFile)
    val uniqueTargetNames = mutableSetOf<String>()

    try {
        // PHASE 1: Copy each selected doc to temporary files directly in destDir and verify integrity
        for (doc in selectedDocs) {
            var targetName = getEffectiveExportName(doc)
            var dedupeIndex = 1
            val baseName = targetName.substringBeforeLast('.', targetName)
            val ext = targetName.substringAfterLast('.', "")
            val extDot = if (ext.isNotBlank()) ".$ext" else ""
            while (uniqueTargetNames.contains(targetName) || File(destDir, targetName).exists()) {
                targetName = "${baseName}_$dedupeIndex$extDot"
                dedupeIndex++
            }
            uniqueTargetNames.add(targetName)

            val finalFile = File(destDir, targetName)
            val tempFile = File(destDir, "$targetName.transfer_${System.currentTimeMillis()}_$dedupeIndex.tmp")
            val srcUri = Uri.parse(doc.uri)

            openStream(context, srcUri).use { inStream ->
                tempFile.outputStream().use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (tempFile.length() == 0L && doc.size > 0L) {
                throw IllegalStateException("0 bytes transferred for ${doc.name}")
            }

            val dateTimestamp = extractDateTimestamp(targetName, doc.modified)
            try { tempFile.setLastModified(dateTimestamp) } catch (_: Exception) {}

            stagedFiles.add(Triple(doc, tempFile, finalFile))
        }

        // PHASE 2: Atomically rename all temporary files to final names
        val finalMovedFiles = mutableListOf<Triple<Doc, File, File>>()
        for ((doc, tempFile, finalFile) in stagedFiles) {
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
            val dateTimestamp = extractDateTimestamp(finalFile.name, doc.modified)
            try { finalFile.setLastModified(dateTimestamp) } catch (_: Exception) {}
            finalMovedFiles.add(Triple(doc, tempFile, finalFile))
        }

        // PHASE 3: Delete original source files now that destination is 100% written & verified
        for ((doc, _, _) in finalMovedFiles) {
            safeDeleteOriginal(context, doc)
        }

        // PHASE 4: Return updated Doc objects with new file paths, names, and timestamps
        val movedDocsMap = finalMovedFiles.associate { (doc, _, finalFile) ->
            doc.uri to doc.copy(
                uri = Uri.fromFile(finalFile).toString(),
                name = finalFile.name,
                proposed = finalFile.name,
                custom = "",
                modified = finalFile.lastModified(),
                size = finalFile.length()
            )
        }

        return MoveResult(
            success = true,
            movedCount = finalMovedFiles.size,
            updatedDocs = selectedDocs.mapNotNull { movedDocsMap[it.uri] }
        )

    } catch (e: Exception) {
        Log.e("SmartDoc", "Direct File move failed: ${e.message}. Testing MediaStore.Downloads fallback...", e)
        for ((_, tempFile, finalFile) in stagedFiles) {
            try { if (tempFile.exists()) tempFile.delete() } catch (_: Exception) {}
            try { if (finalFile.exists()) finalFile.delete() } catch (_: Exception) {}
        }

        // Fallback to MediaStore.Downloads
        val relPath = when {
            destDir.absolutePath.contains("/Download/SmartDocsTransferd", ignoreCase = true) ->
                "Download/SmartDocsTransferd/${destDir.name}"
            destDir.absolutePath.contains("/Download", ignoreCase = true) ->
                "Download/${destDir.name}"
            else ->
                "Download/SmartDocsTransferd/${destDir.name}"
        }
        val msResult = performMoveViaMediaStore(context, selectedDocs, relPath)
        if (msResult.success) {
            return msResult
        }

        return MoveResult(false, 0, "Move failed: ${e.message ?: "Unknown error"}. Originals are untouched.")
    }
}

private fun performAtomicMoveToDocumentFile(
    context: Context,
    selectedDocs: List<Doc>,
    targetTreeUri: Uri,
    subfolderName: String
): MoveResult {
    val rootDoc = try {
        DocumentFile.fromTreeUri(context, targetTreeUri)
    } catch (_: Exception) { null } ?: return MoveResult(false, 0, "Cannot access target destination")

    val destDoc = if (subfolderName.isNotBlank()) {
        val trimmed = subfolderName.trim()
        val safeName = sanitizePathComponent(if (trimmed.startsWith("/")) File(trimmed).name else trimmed)
        var created = rootDoc.findFile(safeName) ?: rootDoc.createDirectory(safeName)
        created ?: rootDoc
    } else {
        rootDoc
    }

    val stagedDocs = mutableListOf<Triple<Doc, DocumentFile, String>>()
    val uniqueNames = mutableSetOf<String>()

    try {
        for (doc in selectedDocs) {
            var targetName = getEffectiveExportName(doc)
            var idx = 1
            val base = targetName.substringBeforeLast('.', targetName)
            val ext = targetName.substringAfterLast('.', "")
            val extDot = if (ext.isNotBlank()) ".$ext" else ""
            while (uniqueNames.contains(targetName)) {
                targetName = "${base}_$idx$extDot"
                idx++
            }
            uniqueNames.add(targetName)

            val mime = getMimeTypeForDoc(targetName, doc.type)
            val finalDocFile = destDoc.createFile(mime, targetName)
                ?: throw IllegalStateException("Cannot create destination file: $targetName")

            openStream(context, Uri.parse(doc.uri)).use { inStream ->
                context.contentResolver.openOutputStream(finalDocFile.uri)?.use { outStream ->
                    inStream.copyTo(outStream)
                } ?: throw IllegalStateException("Cannot write to destination: $targetName")
            }

            stagedDocs.add(Triple(doc, finalDocFile, targetName))
        }

        // Delete originals
        for ((doc, _) in stagedDocs) {
            safeDeleteOriginal(context, doc)
        }

        val movedDocsMap = stagedDocs.associate { (doc, finalDocFile) ->
            doc.uri to doc.copy(
                uri = finalDocFile.uri.toString(),
                name = finalDocFile.name ?: doc.name,
                proposed = finalDocFile.name ?: doc.name,
                custom = "",
                modified = finalDocFile.lastModified(),
                size = finalDocFile.length()
            )
        }

        return MoveResult(
            success = true,
            movedCount = stagedDocs.size,
            updatedDocs = selectedDocs.mapNotNull { movedDocsMap[it.uri] }
        )
    } catch (e: Exception) {
        Log.e("SmartDoc", "Atomic DocumentFile move failed: ${e.message}", e)
        for ((_, finalDocFile, _) in stagedDocs) {
            try { finalDocFile.delete() } catch (_: Exception) {}
        }
        return MoveResult(false, 0, "Move failed: ${e.message ?: "Unknown error"}. Originals are untouched.")
    }
}

private suspend fun performAtomicMove(
    context: Context,
    selectedDocs: List<Doc>,
    targetTreeUri: Uri,
    subfolderName: String
): MoveResult = withContext(Dispatchers.IO) {
    if (selectedDocs.isEmpty()) return@withContext MoveResult(false, 0, "No documents selected")

    val targetFileDir = getFileFromTreeUri(targetTreeUri)
    if (targetFileDir != null) {
        val fileResult = performAtomicMoveToFileDir(context, selectedDocs, targetFileDir, subfolderName)
        if (fileResult.success) {
            return@withContext fileResult
        }
        // Fallback: try MediaStore.Downloads
        val safeSub = sanitizePathComponent(if (subfolderName.trim().startsWith("/")) File(subfolderName.trim()).name else subfolderName.trim())
        val relPath = when {
            targetFileDir.absolutePath.contains("SmartDocsTransferd", ignoreCase = true) ->
                "Download/SmartDocsTransferd/$safeSub"
            targetFileDir.name.equals("Download", ignoreCase = true) ->
                "Download/$safeSub"
            else ->
                "Download/SmartDocsTransferd/$safeSub"
        }
        val msResult = performMoveViaMediaStore(context, selectedDocs, relPath)
        if (msResult.success) {
            return@withContext msResult
        }
        if (targetTreeUri.scheme == "file") {
            return@withContext fileResult
        }
    }
    performAtomicMoveToDocumentFile(context, selectedDocs, targetTreeUri, subfolderName)
}

private suspend fun performDeleteDocuments(context: Context, docsToDelete: List<Doc>): Int = withContext(Dispatchers.IO) {
    var deletedCount = 0
    for (doc in docsToDelete) {
        try {
            val uri = Uri.parse(doc.uri)
            if (uri.scheme == "file" && uri.path != null) {
                val f = File(uri.path!!)
                if (f.exists()) {
                    if (f.delete()) deletedCount++
                } else {
                    deletedCount++
                }
            } else if (uri.scheme == "content") {
                val df = DocumentFile.fromSingleUri(context, uri)
                if (df != null && df.exists()) {
                    if (df.delete()) deletedCount++
                } else {
                    val count = context.contentResolver.delete(uri, null, null)
                    if (count > 0) deletedCount++ else deletedCount++
                }
            } else {
                val f = File(doc.uri)
                if (f.exists()) {
                    if (f.delete()) deletedCount++
                } else {
                    deletedCount++
                }
            }
        } catch (e: Exception) {
            Log.e("SmartDoc", "Failed to delete file ${doc.name}: ${e.message}", e)
        }
    }
    deletedCount
}

private fun merge(old: List<Doc>, found: List<Doc>, retain: Boolean): List<Doc> {
    if (!retain) return found
    val previous = old.associateBy { it.uri }
    return found.map { f ->
        val p = previous[f.uri]
        if (p != null && p.size == f.size && p.modified == f.modified) p else f
    }
}

private fun sha256(context: Context, uri: Uri): String {
    val hash = MessageDigest.getInstance("SHA-256")
    openStream(context, uri).use { stream ->
        val buf = ByteArray(65536)
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            hash.update(buf, 0, n)
        }
    }
    return hash.digest().joinToString("") { "%02x".format(it) }
}

private fun safeName(raw: String, original: String): String {
    val ext = original.substringAfterLast('.', "").lowercase()
    val stem = raw.substringBeforeLast('.', raw).replace(Regex("[\\/:*?\"<>|\\p{Cntrl}]"), "").trim(' ', '.').take(85).ifBlank { "Document" }
    return if (ext.isBlank()) stem else "$stem.$ext"
}

private fun openDocument(context: Context, d: Doc) {
    try {
        val uri = Uri.parse(d.uri)
        val viewUri = if (uri.scheme == "file" && uri.path != null) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", File(uri.path!!))
        } else {
            uri
        }
        val mime = getMimeTypeForDoc(d.name, d.type)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(viewUri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun autoDeduplicate(context: Context, docs: List<Doc>): List<Doc> {
    return withContext(Dispatchers.IO) {
        val seenBinaryHashes = mutableSetOf<String>()
        val seenTextSignatures = mutableSetOf<String>()
        val seenProposedNames = mutableSetOf<String>()

        docs.map { d ->
            val hash = d.digest.ifBlank {
                try { sha256(context, Uri.parse(d.uri)) } catch (_: Exception) { "" }
            }
            val withHash = if (d.digest.isBlank() && hash.isNotBlank()) d.copy(digest = hash) else d

            if (d.status == "Duplicate") {
                if (hash.isNotBlank()) seenBinaryHashes.add(hash)
                return@map withHash
            }

            var isDuplicate = false

            // Tier 1: Binary SHA-256 Check (ignoring empty files)
            if (hash.isNotBlank() && hash != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") {
                if (!seenBinaryHashes.add(hash)) {
                    isDuplicate = true
                }
            }

            // Tier 2a: Content / Text Check (catches PDFs with identical text but different binary metadata/creation dates)
            if (!isDuplicate && d.extractedText.isNotBlank()) {
                val normalizedText = d.extractedText.lowercase().replace(Regex("""[^a-z0-9]"""), "")
                if (normalizedText.length >= 40) {
                    val textSig = normalizedText.take(600)
                    if (!seenTextSignatures.add(textSig)) {
                        isDuplicate = true
                    }
                }
            }

            // Tier 2b: Proposed name + category check (if classified and proposed name is specific)
            if (!isDuplicate && d.proposed.isNotBlank() && d.proposed != "N/A" && d.proposed != d.name && (d.status == "True" || d.status == "OK")) {
                val propSig = "${d.category.lowercase()}::${d.proposed.lowercase()}"
                if (!seenProposedNames.add(propSig)) {
                    isDuplicate = true
                }
            }

            if (isDuplicate) {
                withHash.copy(status = "Duplicate")
            } else {
                withHash
            }
        }
    }
}

private suspend fun moveDuplicatesToDuplicatesFolder(
    context: Context,
    duplicateDocs: List<Doc>
): Map<String, Doc> = withContext(Dispatchers.IO) {
    if (duplicateDocs.isEmpty()) return@withContext emptyMap()
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ?: File(Environment.getExternalStorageDirectory(), "Download")
    val duplicatesDir = File(downloadsDir, "Duplicates")
    if (!duplicatesDir.exists()) {
        try { duplicatesDir.mkdirs() } catch (_: Exception) {}
    }
    if (!duplicatesDir.exists() || !duplicatesDir.canWrite()) {
        Log.e("SmartDoc", "Cannot access or create Duplicates directory: ${duplicatesDir.absolutePath}")
        return@withContext emptyMap()
    }

    val updatedDocsMap = mutableMapOf<String, Doc>()
    val existingNamesInDest = (duplicatesDir.list() ?: emptyArray()).toMutableSet()

    for (doc in duplicateDocs) {
        try {
            val srcUri = Uri.parse(doc.uri)
            val srcPath = if (srcUri.scheme == "file") srcUri.path ?: "" else srcUri.path ?: ""
            // Skip if already in duplicates directory
            if (srcPath.startsWith(duplicatesDir.absolutePath)) {
                continue
            }

            val origName = doc.name.ifBlank { "duplicate_file" }
            var targetName = origName
            var idx = 1
            val base = origName.substringBeforeLast('.', origName)
            val ext = origName.substringAfterLast('.', "")
            val extDot = if (ext.isNotBlank()) ".$ext" else ""

            while (existingNamesInDest.contains(targetName) || File(duplicatesDir, targetName).exists()) {
                targetName = "${base}_$idx$extDot"
                idx++
            }
            existingNamesInDest.add(targetName)

            val targetFile = File(duplicatesDir, targetName)

            openStream(context, srcUri).use { inStream ->
                targetFile.outputStream().use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (doc.size > 0L && targetFile.length() != doc.size) {
                targetFile.delete()
                Log.w("SmartDoc", "Integrity check failed while moving duplicate ${doc.name}")
                continue
            }

            if (doc.modified > 0L) {
                targetFile.setLastModified(doc.modified)
            }

            // Remove original file
            if (srcUri.scheme == "file" && srcUri.path != null) {
                val f = File(srcUri.path!!)
                if (f.exists()) f.delete()
            } else if (srcUri.scheme == "content") {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, srcUri)
                } catch (_: Exception) {
                    val f = File(srcUri.path ?: "")
                    if (f.exists()) f.delete()
                }
            } else {
                val f = File(doc.uri)
                if (f.exists()) f.delete()
            }

            val updatedDoc = doc.copy(
                uri = Uri.fromFile(targetFile).toString(),
                name = targetFile.name,
                status = "Duplicate",
                modified = targetFile.lastModified(),
                size = targetFile.length()
            )
            updatedDocsMap[doc.uri] = updatedDoc
            Log.i("SmartDoc", "Moved physical duplicate ${doc.name} to ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("SmartDoc", "Failed to move duplicate ${doc.name}: ${e.message}")
        }
    }
    updatedDocsMap
}

fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

fun requestStoragePermission(context: Context, onLegacyRequest: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(fallback)
        }
    } else {
        onLegacyRequest()
    }
}

@Composable
private fun FolderPickerDialog(
    title: String = "Select Folder to Scan",
    initialDir: File? = null,
    confirmButtonText: (File) -> String = { "Scan '${it.name.ifBlank { "Folder" }}' (Recursive)" },
    onFolderSelected: (File) -> Unit,
    onEntirePhoneSelected: (() -> Unit)? = null,
    onOpenSystemPicker: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val primaryRoot = remember { Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0") }
    val defaultDir = remember(initialDir) {
        initialDir ?: File(primaryRoot, "Download").takeIf { it.exists() && it.canRead() } ?: primaryRoot
    }
    var currentDir by remember { mutableStateOf(defaultDir) }

    val quickFolders = remember(primaryRoot) {
        val list = mutableListOf<Pair<String, File>>()
        val download = File(primaryRoot, "Download")
        if (download.exists() && download.canRead()) list.add("📥 Download" to download)
        val documents = File(primaryRoot, "Documents")
        if (documents.exists() && documents.canRead()) list.add("📄 Documents" to documents)
        val dcim = File(primaryRoot, "DCIM")
        if (dcim.exists() && dcim.canRead()) list.add("📷 DCIM" to dcim)
        val pictures = File(primaryRoot, "Pictures")
        if (pictures.exists() && pictures.canRead()) list.add("🖼️ Pictures" to pictures)
        val whatsapp1 = File(primaryRoot, "Android/media/com.whatsapp/WhatsApp/Media")
        val whatsapp2 = File(primaryRoot, "WhatsApp/Media")
        if (whatsapp1.exists() && whatsapp1.canRead()) {
            list.add("💬 WhatsApp" to whatsapp1)
        } else if (whatsapp2.exists() && whatsapp2.canRead()) {
            list.add("💬 WhatsApp" to whatsapp2)
        }
        list.add("📱 Root" to primaryRoot)

        try {
            val extDirs = context.getExternalFilesDirs(null)
            for (d in extDirs) {
                if (d != null) {
                    val p = d.absolutePath
                    val idx = p.indexOf("/Android")
                    if (idx > 0) {
                        val sd = File(p.substring(0, idx))
                        if (sd.exists() && sd.canRead() && sd != primaryRoot) {
                            list.add("💾 SD Card" to sd)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        list
    }

    val subDirs: List<File> = remember(currentDir) {
        try {
            val files = currentDir.listFiles { f: File ->
                f.isDirectory && !f.name.startsWith(".") &&
                        !f.name.equals("Android", ignoreCase = true) &&
                        !f.name.equals(".trash", ignoreCase = true) &&
                        !f.name.equals(".thumbnails", ignoreCase = true)
            }
            files?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val canGoUp = remember(currentDir, primaryRoot) {
        val parent = currentDir.parentFile
        parent != null && parent.canRead() && (currentDir.absolutePath.length > primaryRoot.absolutePath.length || parent.absolutePath.startsWith("/storage"))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📁", fontSize = 20.sp)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismissRequest, modifier = Modifier.size(24.dp)) {
                    Text("✕", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Select any folder in phone memory (Downloads, Documents, etc.):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick access chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickFolders.forEach { (label, folder) ->
                        val isSelected = currentDir.absolutePath == folder.absolutePath
                        Surface(
                            onClick = { currentDir = folder },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Current folder navigation bar
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val p = currentDir.parentFile
                                if (p != null && p.canRead()) currentDir = p
                            },
                            enabled = canGoUp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("⬆️", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
                            Text(
                                text = currentDir.name.ifBlank { "Internal Storage" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentDir.absolutePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Subdirectory list
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                ) {
                    if (subDirs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No subfolders inside '${currentDir.name}'.\nTap button below to select this folder.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(subDirs) { dir ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentDir = dir }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📁", fontSize = 15.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = dir.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "›",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFolderSelected(currentDir) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmButtonText(currentDir), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onEntirePhoneSelected != null) {
                    OutlinedButton(
                        onClick = onEntirePhoneSelected,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("📱 Entire Phone", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (onOpenSystemPicker != null) {
                    TextButton(
                        onClick = onOpenSystemPicker,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("System SAF", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(
                    onClick = onDismissRequest,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    )
}

@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val authPrefs = remember { context.getSharedPreferences("smartdoc_auth", Context.MODE_PRIVATE) }
    val libPrefs = remember { context.getSharedPreferences("smartdoc_library", Context.MODE_PRIVATE) }

    // Persistent Login & Session Management
    val wasLoggedIn = authPrefs.getBoolean("is_logged_in", false)
    val savedMode = authPrefs.getString("auth_mode", "") ?: ""
    val savedToken = authPrefs.getString("token", "") ?: ""
    val savedExpiresAt = authPrefs.getLong("expires_at", 0L)
    val isSessionValid = wasLoggedIn && (savedExpiresAt == 0L || System.currentTimeMillis() < (savedExpiresAt - 60_000L))

    var signedIn by remember { mutableStateOf(isSessionValid) }
    var isOfflineMode by remember { mutableStateOf(isSessionValid && savedMode == "offline") }
    var token by remember { mutableStateOf(if (isSessionValid) savedToken else "") }

    var items by remember { mutableStateOf(restore(context)) }
    var backend by remember { mutableStateOf(libPrefs.getString("backend", AppConstants.getBackendUrl(context)) ?: AppConstants.getBackendUrl(context)) }

    val aiProviderPrefs = context.getSharedPreferences("smartdoc_ai_engine", Context.MODE_PRIVATE)
    var selectedAiProvider by remember { mutableStateOf(aiProviderPrefs.getString("primary_provider", "gemini") ?: "gemini") }
    var remainingDailyQuota by remember(selectedAiProvider) { mutableStateOf(RateLimiter.getRemainingDailyQuota(context, selectedAiProvider)) }
    var quotaDisplayStr by remember(selectedAiProvider) { mutableStateOf(RateLimiter.formatQuotaDisplay(context, selectedAiProvider)) }

    var showGeminiQuotaDialog by remember { mutableStateOf(false) }
    var geminiResetHour by remember { mutableStateOf("") }
    var geminiResetDate by remember { mutableStateOf("") }

    var showGroqQuotaDialog by remember { mutableStateOf(false) }
    var groqResetHour by remember { mutableStateOf("") }
    var groqResetDate by remember { mutableStateOf("") }

    var showQuotaRestoredDialog by remember { mutableStateOf(false) }
    var quotaRestoredMessage by remember { mutableStateOf("") }

    var showReauthDialog by remember { mutableStateOf(false) }

    var isScanning by remember { mutableStateOf(false) }
    var isQueuePaused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val geminiRestored = RateLimiter.checkAndClearQuotaRestored(context, "gemini")
            val groqRestored = RateLimiter.checkAndClearQuotaRestored(context, "groq")
            if (geminiRestored && groqRestored) {
                selectedAiProvider = "gemini"
                aiProviderPrefs.edit().putString("primary_provider", "gemini").apply()
                remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, "gemini")
                quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "gemini")
                quotaRestoredMessage = "Both Google Gemini and Groq quotas have been restored! You can resume processing."
                showQuotaRestoredDialog = true
                isQueuePaused = false
            } else if (geminiRestored) {
                selectedAiProvider = "gemini"
                aiProviderPrefs.edit().putString("primary_provider", "gemini").apply()
                remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, "gemini")
                quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "gemini")
                quotaRestoredMessage = "Google Gemini quota has been restored! Daily limit is reset to 1500 requests."
                showQuotaRestoredDialog = true
                isQueuePaused = false
            } else if (groqRestored) {
                remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, selectedAiProvider)
                quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, selectedAiProvider)
                quotaRestoredMessage = "Groq quota has been restored! Limit has been reset."
                showQuotaRestoredDialog = true
            }
            delay(3000L)
        }
    }

    var hasPermission by remember { mutableStateOf(hasStoragePermission(context)) }

    var activeProcessingFile by remember { mutableStateOf("") }
    var activeProcessingPath by remember { mutableStateOf("") }
    var skipRequestedUri by remember { mutableStateOf("") }

    var hasNotifiedCompletion by remember { mutableStateOf(false) }
    var completionBannerMessage by remember { mutableStateOf("") }
    var dailyLimitNotice by remember { mutableStateOf("") }
    val allProcessed = items.isNotEmpty() && !isScanning && (isQueuePaused || items.none { it.status == "False" || it.status == "Processing" })
    val isActivelyProcessing = isScanning || (items.isNotEmpty() && !isQueuePaused && items.any { it.status == "False" || it.status == "Processing" })

    var typeFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var filterDropdownExpanded by remember { mutableStateOf(false) }
    var dateFilterMode by remember { mutableStateOf("ANY") } // "ANY", "ISSUED", "CREATED"
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }

    // Hidden Keepalive WebView for Cloudflare connection
    var hiddenWebView: WebView? by remember { mutableStateOf(null) }

    // Refresh hidden web view every 10 minutes while app is in use
    LaunchedEffect(signedIn, token) {
        if (signedIn) {
            while (isActive) {
                delay(10 * 60 * 1000L) // 10 minutes
                try {
                    val headers = if (token.isNotBlank()) mapOf("Authorization" to "Bearer $token") else emptyMap()
                    withContext(Dispatchers.Main) {
                        hiddenWebView?.loadUrl("${backend.trimEnd('/')}/health", headers)
                    }
                    Log.d("SmartDocManager", "Hidden keepalive WebView refreshed Cloudflare connection")
                } catch (e: Exception) {
                    Log.e("SmartDocManager", "Keepalive refresh failed: ${e.message}")
                }
            }
        }
    }

    val activity = context as? Activity
    val pickerIntent = activity?.intent
    val isPickerMode = remember(pickerIntent) {
        val a = pickerIntent?.action
        a == Intent.ACTION_GET_CONTENT || a == Intent.ACTION_OPEN_DOCUMENT || a == Intent.ACTION_PICK
    }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(pickerIntent) {
        val t = pickerIntent?.type?.lowercase() ?: ""
        val rawExtra = pickerIntent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        val extraTypes: List<String> = rawExtra?.map { it.lowercase() } ?: emptyList()
        if (t.startsWith("image/") || (extraTypes.isNotEmpty() && extraTypes.all { it.startsWith("image/") })) {
            typeFilter = "IMAGE"
        } else if (t == "application/pdf" || (extraTypes.isNotEmpty() && extraTypes.all { it == "application/pdf" })) {
            typeFilter = "PDF"
        } else if (t.contains("officedocument") || t.contains("msword") || t.contains("ms-excel") || t.contains("ms-powerpoint") || t.startsWith("text/")) {
            typeFilter = "DOCS"
        }
    }

    BackHandler(enabled = isPickerMode) {
        activity?.setResult(Activity.RESULT_CANCELED)
        activity?.finish()
    }

    var showExpansionFeature by remember { mutableStateOf(false) }
    var headerTapCount by remember { mutableStateOf(0) }
    var lastHeaderTapTime by remember { mutableStateOf(0L) }

    var expandedDocUri by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val path = uri.path ?: uri.toString()
            PersistentMetadataManager.setCustomDirectory(context, path)
            scope.launch {
                PersistentMetadataManager.save(context, items)
            }
            Toast.makeText(context, "Metadata folder set to: $path", Toast.LENGTH_LONG).show()
        }
    }

    fun refresh(newItems: List<Doc>) {
        items = newItems
        scope.launch {
            saveAsync(context, newItems)
        }
    }

    val performGoogleSignIn: () -> Unit = {
        scope.launch {
            val webClientId = AppConstants.getGoogleWebClientId(context)
            if (webClientId.startsWith("REPLACE_")) {
                notice = "Configure google_web_client_id in shared-spec/app-config.json before building"
                return@launch
            }
            try {
                val manager = CredentialManager.create(context)
                val option = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = manager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                    token = googleId.idToken
                    signedIn = true
                    isOfflineMode = false
                    showReauthDialog = false
                    isQueuePaused = false
                    notice = "Signed in as ${googleId.id}"

                    val expiresAt = parseTokenExpiryMillis(googleId.idToken)
                    authPrefs.edit()
                        .putBoolean("is_logged_in", true)
                        .putString("auth_mode", "google")
                        .putString("token", googleId.idToken)
                        .putString("email", googleId.id)
                        .putLong("expires_at", expiresAt)
                        .apply()

                    val needsRequeue = items.any { it.status == "Error" && (it.responseReceived.contains("Unauthorized") || it.responseReceived.contains("AUTH_REQUIRED") || it.responseReceived.contains("missing or expired")) }
                    if (needsRequeue) {
                        val resetDocs = items.map { item ->
                            if (item.status == "Error" && (item.responseReceived.contains("Unauthorized") || item.responseReceived.contains("AUTH_REQUIRED") || item.responseReceived.contains("missing or expired"))) {
                                item.copy(status = "False", category = "Pending Analysis")
                            } else {
                                item
                            }
                        }
                        refresh(resetDocs)
                    }

                    Log.i("SmartDoc", "Google Sign-In successful: ${googleId.id}")
                    Toast.makeText(context, "Signed in as ${googleId.id}", Toast.LENGTH_SHORT).show()
                } else {
                    notice = "Google Sign-In did not return an identity token. Please ensure your Google account is configured."
                }
            } catch (e: GetCredentialCancellationException) {
                Log.i("SmartDoc", "Sign-in cancelled by user")
            } catch (e: Throwable) {
                Log.e("SmartDoc", "Google Sign-In error: ${e.message}", e)
                notice = "Google Sign-In error: ${e.message ?: "Authentication failed"}. Please retry with an authorized Google account."
                Toast.makeText(context, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var selectedScanFolderName by remember {
        mutableStateOf(libPrefs.getString("scanned_folder_name", "Entire Device") ?: "Entire Device")
    }
    var hasPromptedInitialFolderScan by remember { mutableStateOf(false) }
    var showScanFolderPromptDialog by remember { mutableStateOf(false) }
    var showRescanOptionsDialog by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startFolderScan(uri: Uri, retainExisting: Boolean = false) {
        scanJob?.cancel()
        scanJob = scope.launch {
            isScanning = true
            hasNotifiedCompletion = false
            selectedUris = emptySet()
            activeProcessingFile = ""
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
            }
            val folderName = getFolderNameFromUri(context, uri)
            val prevFolderUri = libPrefs.getString("scanned_folder_uri", "") ?: ""
            val isSameFolder = prevFolderUri == uri.toString()
            val hasPendingDocs = items.any { it.status != "True" && it.status != "Error" && it.status != "Skipped" && it.status != "Duplicate" }
            val effectiveRetain = if (isSameFolder && hasPendingDocs) true else retainExisting
            selectedScanFolderName = folderName
            libPrefs.edit()
                .putString("scanned_folder_name", folderName)
                .putString("scanned_folder_uri", uri.toString())
                .apply()
            activeProcessingPath = "Scanning folder: $folderName (recursive)..."
            Toast.makeText(context, "Scanning folder: $folderName...", Toast.LENGTH_SHORT).show()
            Log.i("SmartDoc", "Starting recursive folder scan for: $folderName ($uri)")
            val found = withContext(Dispatchers.IO) {
                scanFolderUri(context, uri)
            }
            Log.i("SmartDoc", "Folder scan complete: found ${found.size} files in $folderName")
            if (found.isEmpty()) {
                Toast.makeText(context, "No documents found in $folderName", Toast.LENGTH_LONG).show()
            }
            // Fast pre-pass: compute binary hashes and move any duplicates to Download/Duplicates/ upfront
            val withHashes = withContext(Dispatchers.IO) {
                found.map { doc ->
                    val h = try { sha256(context, Uri.parse(doc.uri)) } catch (_: Exception) { "" }
                    if (h.isNotBlank()) doc.copy(digest = h) else doc
                }
            }
            val deduplicatedInitial = autoDeduplicate(context, withHashes)
            val dupsInitial = deduplicatedInitial.filter { it.status == "Duplicate" }
            val movedInitialMap = if (dupsInitial.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    moveDuplicatesToDuplicatesFolder(context, dupsInitial)
                }
            } else emptyMap()
            val finalFound = deduplicatedInitial.map { movedInitialMap[it.uri] ?: it }
            refresh(merge(items, finalFound, effectiveRetain))
            isScanning = false
        }
    }

    fun startFullDeviceScan(retainExisting: Boolean = false) {
        scanJob?.cancel()
        scanJob = scope.launch {
            isScanning = true
            hasNotifiedCompletion = false
            selectedUris = emptySet()
            activeProcessingFile = ""
            val prevScope = libPrefs.getString("scanned_folder_name", "") ?: ""
            val isSameScope = prevScope == "Entire Device"
            val hasPendingDocs = items.any { it.status != "True" && it.status != "Error" && it.status != "Skipped" && it.status != "Duplicate" }
            val effectiveRetain = if (isSameScope && hasPendingDocs) true else retainExisting
            selectedScanFolderName = "Entire Device"
            libPrefs.edit()
                .putString("scanned_folder_name", "Entire Device")
                .putString("scanned_folder_uri", "")
                .apply()
            activeProcessingPath = "Scanning entire device storage..."
            Toast.makeText(context, "Scanning entire device storage...", Toast.LENGTH_SHORT).show()
            Log.i("SmartDoc", "Starting full device storage scan...")
            val found = withContext(Dispatchers.IO) {
                scanDeviceStorage(context)
            }
            Log.i("SmartDoc", "Scan complete: found ${found.size} files")
            // Fast pre-pass: compute binary hashes and move any duplicates to Download/Duplicates/ upfront
            val withHashes = withContext(Dispatchers.IO) {
                found.map { doc ->
                    val h = try { sha256(context, Uri.parse(doc.uri)) } catch (_: Exception) { "" }
                    if (h.isNotBlank()) doc.copy(digest = h) else doc
                }
            }
            val deduplicatedInitial = autoDeduplicate(context, withHashes)
            val dupsInitial = deduplicatedInitial.filter { it.status == "Duplicate" }
            val movedInitialMap = if (dupsInitial.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    moveDuplicatesToDuplicatesFolder(context, dupsInitial)
                }
            } else emptyMap()
            val finalFound = deduplicatedInitial.map { movedInitialMap[it.uri] ?: it }
            refresh(merge(items, finalFound, effectiveRetain))
            isScanning = false
        }
    }

    fun triggerRescan(retainExisting: Boolean) {
        val savedFolderUriStr = libPrefs.getString("scanned_folder_uri", "") ?: ""
        if (savedFolderUriStr.isNotBlank() && selectedScanFolderName != "Entire Device") {
            try {
                startFolderScan(Uri.parse(savedFolderUriStr), retainExisting = retainExisting)
            } catch (_: Exception) {
                startFullDeviceScan(retainExisting = retainExisting)
            }
        } else {
            startFullDeviceScan(retainExisting = retainExisting)
        }
    }

    val folderScanPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val prevUri = libPrefs.getString("scanned_folder_uri", "") ?: ""
            val isDifferent = prevUri.isNotBlank() && prevUri != uri.toString()
            startFolderScan(uri, retainExisting = !isDifferent)
        } else {
            // User cancelled folder selection: fall back to complete phone scan as per default behavior
            Toast.makeText(context, "Folder selection cancelled. Starting entire device scan...", Toast.LENGTH_SHORT).show()
            startFullDeviceScan()
        }
    }

    var showMoveDialog by remember { mutableStateOf(false) }
    var showMoveFolderPicker by remember { mutableStateOf(false) }
    var moveSubfolderName by remember { mutableStateOf("") }
    var isMovingFiles by remember { mutableStateOf(false) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeletingFiles by remember { mutableStateOf(false) }

    val moveTargetFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val selectedDocs = items.filter { selectedUris.contains(it.uri) }
            if (selectedDocs.isEmpty()) return@rememberLauncherForActivityResult

            scope.launch {
                isMovingFiles = true
                Toast.makeText(context, "Moving ${selectedDocs.size} documents...", Toast.LENGTH_SHORT).show()
                val result = performAtomicMove(context, selectedDocs, treeUri, moveSubfolderName)
                isMovingFiles = false
                if (result.success) {
                    val oldUriToNewDoc = mutableMapOf<String, Doc>()
                    for (i in selectedDocs.indices) {
                        val oldDoc = selectedDocs[i]
                        val newDoc = result.updatedDocs.getOrNull(i)
                        if (newDoc != null) {
                            oldUriToNewDoc[oldDoc.uri] = newDoc
                        }
                    }

                    val newItems = items.map { item ->
                        oldUriToNewDoc[item.uri] ?: item
                    }

                    refresh(newItems)
                    selectedUris = emptySet()
                    val destFolderName = if (moveSubfolderName.isNotBlank()) moveSubfolderName.trim() else getFolderNameFromUri(context, treeUri)
                    moveSubfolderName = ""
                    Toast.makeText(context, "🎉 Successfully moved ${result.movedCount} documents to '$destFolderName'!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "❌ ${result.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(items.size, allProcessed) {
        if (allProcessed && items.isNotEmpty()) {
            val deduplicated = autoDeduplicate(context, items)
            val dups = deduplicated.filter { it.status == "Duplicate" }
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File(Environment.getExternalStorageDirectory(), "Download")
            val duplicatesDir = File(downloadsDir, "Duplicates")
            val unlocatedDups = dups.filter { d ->
                val p = try {
                    val u = Uri.parse(d.uri)
                    if (u.scheme == "file") u.path ?: "" else u.path ?: ""
                } catch (_: Exception) { "" }
                !p.startsWith(duplicatesDir.absolutePath)
            }
            if (unlocatedDups.isNotEmpty() || deduplicated.count { it.status == "Duplicate" } != items.count { it.status == "Duplicate" }) {
                val movedMap = if (dups.isNotEmpty()) {
                    moveDuplicatesToDuplicatesFolder(context, dups)
                } else emptyMap()
                val finalItems = deduplicated.map { movedMap[it.uri] ?: it }
                refresh(finalItems)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasStoragePermission(context)
                if (granted != hasPermission) {
                    hasPermission = granted
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
        hasPermission = granted || hasStoragePermission(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perms = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (perms.isNotEmpty()) {
                permissionLauncher.launch(perms.toTypedArray())
            }
        }
    }

    // Scan Initiation: Prompt user to choose folder (or cancel to scan entire device)
    LaunchedEffect(signedIn, hasPermission) {
        if (signedIn && hasPermission && !isScanning && items.isEmpty() && !hasPromptedInitialFolderScan) {
            hasPromptedInitialFolderScan = true
            showScanFolderPromptDialog = true
        }
    }

    // Background Processing Worker
    LaunchedEffect(signedIn, isQueuePaused, selectedAiProvider, token) {
        if (!signedIn || isQueuePaused) return@LaunchedEffect
        Log.i("SmartDoc", "Processing worker active. Paused: $isQueuePaused")
        while (isActive) {
            if (isScanning) {
                delay(500)
                continue
            }

            val tokenExp = parseTokenExpiryMillis(token)
            val isTokenExpired = token.isBlank() || (tokenExp > 0L && System.currentTimeMillis() >= (tokenExp - 60_000L))
            if (isTokenExpired) {
                Log.w("SmartDoc", "Token is expired or missing. Pausing worker.")
                withContext(Dispatchers.Main) {
                    showReauthDialog = true
                    isQueuePaused = true
                }
                delay(1000)
                break
            }

            val pending = items.firstOrNull {
                it.status != "True" && it.status != "Error" && it.status != "Skipped" && it.status != "Duplicate" && it.uri != skipRequestedUri
            }

            if (pending == null) {
                activeProcessingFile = ""
                activeProcessingPath = ""

                if (!hasNotifiedCompletion && items.isNotEmpty()) {
                    hasNotifiedCompletion = true
                    Log.i("SmartDoc", "All files processed. Auto-triggering duplicate removal & moving to Download/Duplicates/...")
                    val deduplicated = autoDeduplicate(context, items)
                    val dups = deduplicated.filter { it.status == "Duplicate" }
                    val movedMap = if (dups.isNotEmpty()) {
                        moveDuplicatesToDuplicatesFolder(context, dups)
                    } else emptyMap()
                    val finalItems = deduplicated.map { movedMap[it.uri] ?: it }
                    refresh(finalItems)

                    val processedCount = finalItems.count { it.status == "True" || it.status == "Error" || it.status == "Skipped" || it.status == "Duplicate" }
                    val dupsCount = finalItems.count { it.status == "Duplicate" }
                    val movedCount = movedMap.size

                    val msg = if (movedCount > 0) {
                        "All $processedCount documents processed! $movedCount duplicates moved to Download/Duplicates/."
                    } else {
                        "All $processedCount documents processed! $dupsCount duplicates hidden."
                    }
                    if (MainActivity.isAppInForeground) {
                        completionBannerMessage = msg
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    } else {
                        notifyScanCompleted(context, processedCount)
                    }
                }
                delay(1200)
                continue
            }

            activeProcessingFile = pending.name
            val parsedPath = try { Uri.parse(pending.uri).path ?: pending.name } catch (_: Exception) { pending.name }
            activeProcessingPath = parsedPath

            val stepData = withContext(Dispatchers.IO) {
                try {
                    val pUri = Uri.parse(pending.uri)
                    // STEP 1: Compute binary hash first
                    val docHash = try { sha256(context, pUri) } catch (_: Exception) { "" }

                    // Check for binary duplicate BEFORE doing OCR or contacting backend
                    val isBinaryDup = docHash.isNotBlank() &&
                        docHash != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" &&
                        items.any { it.uri != pending.uri && it.digest.isNotBlank() && it.digest == docHash }

                    if (isBinaryDup) {
                        Log.i("SmartDoc", "Pre-backend check: ${pending.name} is a binary duplicate. Relocating to Download/Duplicates/...")
                        val movedMap = moveDuplicatesToDuplicatesFolder(context, listOf(pending.copy(digest = docHash, status = "Duplicate")))
                        val relocated = movedMap[pending.uri]
                        val dupRes = Classifier.Result(
                            name = pending.name,
                            category = Classifier.FALLBACK,
                            specifics = "Binary duplicate detected. Relocated to Download/Duplicates/.",
                            status = "DUPLICATE_RELOCATED",
                            suggestedName = "N/A",
                            promptSent = "Binary check: SHA-256 matches an existing document in library. Cloud request skipped.",
                            rawResponse = "Duplicate file relocated to Download/Duplicates/. Zero backend requests sent."
                        )
                        return@withContext ClassifierResultData(dupRes, "", "", docHash, isDuplicate = true, relocatedDoc = relocated)
                    }

                    // STEP 2: Extract text (OCR / Office Parser) locally on-device
                    val pendingExt = pending.name.substringAfterLast('.', "").lowercase()
                    val isOfficeDoc = OfficeTextExtractor.isOfficeDocument(pending.name)
                    val pages = if (!isOfficeDoc) documentPages(context, pUri) else emptyList()
                    val extractedText = if (isOfficeDoc) {
                        try {
                            openStream(context, pUri).use { stream ->
                                OfficeTextExtractor.extractText(pending.name, stream)
                            }
                        } catch (_: Exception) { "" }
                    } else {
                        HostedClassifier.extractText(pages)
                    }
                    val dimensions = when {
                        isOfficeDoc -> "${getDocTypeFromExt(pendingExt)} Document"
                        pending.type == "Image" -> getImageResolution(context, pUri)
                        else -> "PDF (${pages.size} pages)"
                    }

                    // STEP 3: Drop files which do not contain valid textual content (< 5 alphanumeric chars)
                    val cleanText = extractedText.replace(Regex("""[^a-zA-Z0-9]"""), "")
                    if (cleanText.length < 5) {
                        Log.i("SmartDoc", "Pre-backend check: ${pending.name} contains no valid textual content (${cleanText.length} chars). Dropping cloud request...")
                        val dropPayload = JSONObject()
                            .put("filename", pending.name)
                            .put("provider", selectedAiProvider)
                            .put("extracted_chars", cleanText.length)
                            .put("reason", "Document contains no readable alphanumeric text (< 5 chars)")
                            .toString(2)
                        val dropResp = JSONObject()
                            .put("status", "UNCATEGORIZABLE")
                            .put("action", "Cloud request dropped to conserve AI quota")
                            .put("category", "Other Document")
                            .put("note", "File has no readable text")
                            .toString(2)
                        val noTextRes = Classifier.Result(
                            name = pending.name,
                            category = Classifier.FALLBACK,
                            specifics = "No readable text found (dropped from cloud requests)",
                            status = "UNCATEGORIZABLE",
                            suggestedName = Classifier.generateCompliantProposedName("Other Document", "Document", null, pending.name, extractedText),
                            promptSent = dropPayload,
                            rawResponse = dropResp
                        )
                        return@withContext ClassifierResultData(noTextRes, extractedText, dimensions, docHash, isDuplicate = false, relocatedDoc = null)
                    }

                    // STEP 4: Content-wise / text duplicate check
                    val textSig = cleanText.lowercase().take(600)
                    val isContentDup = cleanText.length >= 40 && items.any { other ->
                        other.uri != pending.uri && other.extractedText.isNotBlank() &&
                        other.extractedText.replace(Regex("""[^a-zA-Z0-9]"""), "").lowercase().take(600) == textSig
                    }

                    if (isContentDup) {
                        Log.i("SmartDoc", "Pre-backend check: ${pending.name} is a content duplicate. Relocating to Download/Duplicates/...")
                        val movedMap = moveDuplicatesToDuplicatesFolder(context, listOf(pending.copy(digest = docHash, extractedText = extractedText, status = "Duplicate")))
                        val relocated = movedMap[pending.uri]
                        val dupPayload = JSONObject()
                            .put("filename", pending.name)
                            .put("sha256", docHash)
                            .put("reason", "Content duplicate signature matches an existing document in library")
                            .toString(2)
                        val dupResp = JSONObject()
                            .put("status", "DUPLICATE_RELOCATED")
                            .put("destination", "Download/Duplicates/")
                            .put("note", "Duplicate file relocated. Zero cloud AI requests consumed.")
                            .toString(2)
                        val dupRes = Classifier.Result(
                            name = pending.name,
                            category = Classifier.FALLBACK,
                            specifics = "Content duplicate detected. Relocated to Download/Duplicates/.",
                            status = "DUPLICATE_RELOCATED",
                            suggestedName = "N/A",
                            promptSent = dupPayload,
                            rawResponse = dupResp
                        )
                        return@withContext ClassifierResultData(dupRes, extractedText, dimensions, docHash, isDuplicate = true, relocatedDoc = relocated)
                    }

                    // STEP 5: Only unique files with valid textual content proceed to Cloud AI Backend!
                    val classifierRes = if (token.isNotBlank()) {
                        val (canRequest, limitMessage) = RateLimiter.checkDailyLimit(context, selectedAiProvider)
                        val isGemini = selectedAiProvider.lowercase() == "gemini"
                        if (!canRequest) {
                            if (isGemini) {
                                val (h, d) = RateLimiter.getGeminiResetInfo()
                                RateLimiter.markQuotaExhausted(context, "gemini")
                                withContext(Dispatchers.Main) {
                                    geminiResetHour = h
                                    geminiResetDate = d
                                    showGeminiQuotaDialog = true
                                    isQueuePaused = true
                                    remainingDailyQuota = 0
                                    quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "gemini")
                                }
                                val qPayload = JSONObject()
                                    .put("filename", pending.name)
                                    .put("provider", selectedAiProvider)
                                    .put("status", "QUOTA_EXHAUSTED")
                                    .toString(2)
                                val qResp = JSONObject()
                                    .put("error", "AI quota exhausted")
                                    .put("message", "Gemini quota is completely utilized! It will be reset at $h hours on $d date.")
                                    .put("reset_clock", "$h hours")
                                    .put("reset_date", d)
                                    .toString(2)
                                Classifier.Result(
                                    pending.name,
                                    specifics = "Gemini quota is completely utilized! It will be reset at $h hours on $d date.",
                                    status = "QUOTA_EXHAUSTED",
                                    promptSent = qPayload,
                                    rawResponse = qResp
                                )
                            } else {
                                val (h, d) = RateLimiter.getGroqResetInfo()
                                RateLimiter.markQuotaExhausted(context, "groq")
                                withContext(Dispatchers.Main) {
                                    groqResetHour = h
                                    groqResetDate = d
                                    showGroqQuotaDialog = true
                                    isQueuePaused = true
                                    remainingDailyQuota = 0
                                    quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "groq")
                                }
                                val qPayload = JSONObject()
                                    .put("filename", pending.name)
                                    .put("provider", selectedAiProvider)
                                    .put("status", "QUOTA_EXHAUSTED")
                                    .toString(2)
                                val qResp = JSONObject()
                                    .put("error", "AI quota exhausted")
                                    .put("message", "Groq quota is completely utilized! It will be reset at $h hours on $d date.")
                                    .put("reset_clock", "$h hours")
                                    .put("reset_date", d)
                                    .toString(2)
                                Classifier.Result(
                                    pending.name,
                                    specifics = "Groq quota is completely utilized! It will be reset at $h hours on $d date.",
                                    status = "QUOTA_EXHAUSTED",
                                    promptSent = qPayload,
                                    rawResponse = qResp
                                )
                            }
                        } else {
                            val reqStartTime = System.currentTimeMillis()
                            RateLimiter.waitForSlot()
                            RateLimiter.recordRequest(context, selectedAiProvider)
                            withContext(Dispatchers.Main) {
                                remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, selectedAiProvider)
                                quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, selectedAiProvider)
                            }

                            val cloudRes = HostedClassifier.classifyText(pending.name, extractedText, backend, token, preferredProvider = selectedAiProvider)
                            val elapsedMs = System.currentTimeMillis() - reqStartTime
                            RateLimiter.applyPostRequestDelay(selectedAiProvider, elapsedMs)

                            if (cloudRes.status == "OK" || cloudRes.status == "UNCATEGORIZABLE") {
                                cloudRes
                            } else {
                                val isQuota = cloudRes.status == "QUOTA_EXHAUSTED" || cloudRes.rawResponse.contains("429") || cloudRes.rawResponse.contains("quota")
                                val isAuthError = cloudRes.status == "UNAUTHORIZED" || cloudRes.rawResponse.contains("Unauthorized") || cloudRes.specifics.contains("401") || cloudRes.rawResponse.contains("TOKEN_EXPIRED") || cloudRes.rawResponse.contains("AUDIENCE_MISMATCH")
                                if (isQuota) {
                                    if (isGemini) {
                                        val (h, d) = RateLimiter.getGeminiResetInfo()
                                        RateLimiter.markQuotaExhausted(context, "gemini")
                                        withContext(Dispatchers.Main) {
                                            geminiResetHour = h
                                            geminiResetDate = d
                                            showGeminiQuotaDialog = true
                                            isQueuePaused = true
                                            remainingDailyQuota = 0
                                            quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "gemini")
                                        }
                                        cloudRes.copy(
                                            specifics = "Gemini quota is completely utilized! It will be reset at $h hours on $d date.",
                                            status = "QUOTA_EXHAUSTED"
                                        )
                                    } else {
                                        val (h, d) = RateLimiter.getGroqResetInfo()
                                        RateLimiter.markQuotaExhausted(context, "groq")
                                        withContext(Dispatchers.Main) {
                                            groqResetHour = h
                                            groqResetDate = d
                                            showGroqQuotaDialog = true
                                            isQueuePaused = true
                                            remainingDailyQuota = 0
                                            quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "groq")
                                        }
                                        cloudRes.copy(
                                            specifics = "Groq quota is completely utilized! It will be reset at $h hours on $d date.",
                                            status = "QUOTA_EXHAUSTED"
                                        )
                                    }
                                } else if (isAuthError) {
                                    withContext(Dispatchers.Main) {
                                        showReauthDialog = true
                                        isQueuePaused = true
                                    }
                                    cloudRes.copy(
                                        specifics = "Google session expired or unauthorized. Please re-authenticate.",
                                        status = "AUTH_REQUIRED"
                                    )
                                } else {
                                    cloudRes
                                }
                            }
                        }
                    } else {
                        val authPayload = JSONObject()
                            .put("filename", pending.name)
                            .put("provider", selectedAiProvider)
                            .put("auth_token", "MISSING")
                            .toString(2)
                        val authResp = JSONObject()
                            .put("error", "Unauthorized")
                            .put("message", "Google Sign-In identity token is missing or session expired. Please sign in again.")
                            .toString(2)
                        Classifier.Result(
                            pending.name,
                            specifics = "Signed out / Authentication token missing",
                            status = "AUTH_REQUIRED",
                            promptSent = authPayload,
                            rawResponse = authResp
                        )
                    }
                    ClassifierResultData(classifierRes, extractedText, dimensions, docHash, isDuplicate = false, relocatedDoc = null)
                } catch (e: Exception) {
                    Log.e("SmartDoc", "Exception processing ${pending.name}: ${e.message}", e)
                    val exPayload = JSONObject()
                        .put("filename", pending.name)
                        .put("provider", selectedAiProvider)
                        .put("step", "Page extraction or local OCR")
                        .toString(2)
                    val exResp = JSONObject()
                        .put("error", "Processing exception")
                        .put("exception_message", e.message ?: "Unknown exception")
                        .put("exception_class", e.javaClass.simpleName)
                        .toString(2)
                    val errRes = Classifier.Result(
                        name = pending.name,
                        specifics = e.message ?: "Processing exception",
                        status = "PROCESSING_ERROR",
                        promptSent = exPayload,
                        rawResponse = exResp
                    )
                    ClassifierResultData(errRes, "", "", "", isDuplicate = false, relocatedDoc = null)
                }
            }

            val updated = items.map { item ->
                if (item.uri == pending.uri) {
                    if (stepData.isDuplicate && stepData.relocatedDoc != null) {
                        stepData.relocatedDoc
                    } else if (stepData.isDuplicate) {
                        item.copy(
                            status = "Duplicate",
                            category = stepData.result.category,
                            proposed = "N/A",
                            promptSent = stepData.result.promptSent,
                            responseReceived = stepData.result.rawResponse.ifBlank { stepData.result.specifics },
                            extractedText = stepData.extractedText,
                            dimensions = stepData.dimensions,
                            digest = stepData.hash.ifBlank { item.digest },
                            metadata = stepData.result.metadata
                        )
                    } else if (stepData.result.status == "OK" || stepData.result.status == "UNCATEGORIZABLE") {
                        val proposal = if (stepData.result.status == "UNCATEGORIZABLE") {
                            safeName(stepData.result.suggestedName.ifBlank { Classifier.generateCompliantProposedName("Other Document", "Document", null, item.name, stepData.extractedText) }, item.name)
                        } else {
                            safeName(stepData.result.suggestedName.ifBlank { Classifier.generateCompliantProposedName(stepData.result.category, null, null, item.name, stepData.extractedText) }, item.name)
                        }
                        item.copy(
                            status = "True",
                            category = stepData.result.category,
                            proposed = proposal,
                            promptSent = stepData.result.promptSent,
                            responseReceived = stepData.result.rawResponse.ifBlank { stepData.result.specifics },
                            extractedText = stepData.extractedText,
                            dimensions = stepData.dimensions,
                            digest = stepData.hash.ifBlank { item.digest },
                            metadata = stepData.result.metadata
                        )
                    } else {
                        val isQuota = stepData.result.status == "QUOTA_EXHAUSTED"
                        val isAuth = stepData.result.status == "AUTH_REQUIRED" || stepData.result.status == "UNAUTHORIZED"
                        val fallbackProposal = safeName(Classifier.generateCompliantProposedName("Other Document", "Document", null, item.name, stepData.extractedText), item.name)
                        item.copy(
                            status = if (isQuota || isAuth) "False" else "Error",
                            category = if (isQuota || isAuth) "Pending Analysis" else "Unclassified",
                            proposed = if (isQuota || isAuth) fallbackProposal else item.name,
                            promptSent = stepData.result.promptSent,
                            responseReceived = stepData.result.rawResponse.ifBlank { stepData.result.specifics },
                            extractedText = stepData.extractedText,
                            dimensions = stepData.dimensions,
                            digest = stepData.hash.ifBlank { item.digest },
                            metadata = stepData.result.metadata
                        )
                    }
                } else {
                    item
                }
            }
            refresh(updated)
            remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, selectedAiProvider)
            delay(150)
        }
    }

    MaterialTheme {
        // SCREEN 1: LOGIN GATE
        if (!signedIn && !isPickerMode) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📄 SmartDocManager", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Intelligent Document Classification & Virtual Management\n(Indian Standards)",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { performGoogleSignIn() },
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Text("Sign in with Google")
                }

                if (notice.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(notice, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Original documents on disk are protected and never modified.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            return@MaterialTheme
        }

        // SCREEN 2: STORAGE PERMISSION GATE
        if (!hasPermission) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("📁 Storage Access Permission Required", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "SmartDocManager automatically scans your device memory to discover and categorize documents (excluding internal Android and system directories).\n\nPlease grant storage access to begin automatic scanning.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                requestStoragePermission(context) {
                                    val perms = if (Build.VERSION.SDK_INT >= 33) {
                                        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                    permissionLauncher.launch(perms)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant All Files Storage Permission")
                        }
                    }
                }
            }
            return@MaterialTheme
        }

        // SCREEN 3: MAIN APP SCREEN
        if (showStorageDialog) {
            val currentFile = PersistentMetadataManager.getMetadataFile(context)
            AlertDialog(
                onDismissRequest = { showStorageDialog = false },
                title = { Text("Metadata Storage Location", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Scanned document metadata is automatically preserved in public mobile storage so it survives app uninstallation.\n\nCurrent location:\n${currentFile.absolutePath}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "On next launch or re-installation, SmartDocManager automatically cross-checks these files on disk and restores valid documents without re-scanning.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showStorageDialog = false
                        try {
                            folderPickerLauncher.launch(null)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open folder picker: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Change Folder")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStorageDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showGeminiQuotaDialog) {
            AlertDialog(
                onDismissRequest = { /* user must select Yes or No */ },
                title = { Text("Gemini Quota Notice", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Gemini quota is completely utilized! It will be reset at $geminiResetHour hours on $geminiResetDate date. Do you want me to proceed with Groq?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showGeminiQuotaDialog = false
                        selectedAiProvider = "groq"
                        aiProviderPrefs.edit().putString("primary_provider", "groq").apply()
                        if (RateLimiter.isQuotaExhausted(context, "groq")) {
                            val (h, d) = RateLimiter.getGroqResetInfo()
                            groqResetHour = h
                            groqResetDate = d
                            showGroqQuotaDialog = true
                            isQueuePaused = true
                            remainingDailyQuota = 0
                            quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "groq")
                        } else {
                            RateLimiter.clearMinuteWindow()
                            remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, "groq")
                            quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "groq")
                            isQueuePaused = false
                        }
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showGeminiQuotaDialog = false
                        isQueuePaused = true
                    }) {
                        Text("No")
                    }
                }
            )
        }

        if (showGroqQuotaDialog) {
            AlertDialog(
                onDismissRequest = {
                    showGroqQuotaDialog = false
                    selectedAiProvider = "gemini"
                    aiProviderPrefs.edit().putString("primary_provider", "gemini").apply()
                    remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, "gemini")
                    quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "gemini")
                    isQueuePaused = true
                },
                title = { Text("Groq Quota Notice", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Groq quota is completely utilized! It will be reset at $groqResetHour hours on $groqResetDate date.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showGroqQuotaDialog = false
                        selectedAiProvider = "gemini"
                        aiProviderPrefs.edit().putString("primary_provider", "gemini").apply()
                        remainingDailyQuota = RateLimiter.getRemainingDailyQuota(context, "gemini")
                        quotaDisplayStr = RateLimiter.formatQuotaDisplay(context, "gemini")
                        isQueuePaused = true
                    }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showQuotaRestoredDialog) {
            AlertDialog(
                onDismissRequest = { showQuotaRestoredDialog = false },
                title = { Text("Quota Restored", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        quotaRestoredMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(onClick = { showQuotaRestoredDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showReauthDialog) {
            AlertDialog(
                onDismissRequest = {
                    showReauthDialog = false
                    isQueuePaused = true
                },
                title = { Text("Sign-In Expired", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Your Google Sign-In session has expired or requires renewal. Please sign in again to continue document classification.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(onClick = { performGoogleSignIn() }) {
                        Text("Sign in with Google")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showReauthDialog = false
                        isQueuePaused = true
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showMoveDialog) {
            val selectedCount = selectedUris.size
            if (moveSubfolderName.isBlank()) {
                moveSubfolderName = Classifier.formatTransferTimestamp()
            }
            val defaultTransferBaseDir = getSmartDocsTransferBaseDir()
            val targetFullPath = if (moveSubfolderName.trim().startsWith("/")) {
                moveSubfolderName.trim()
            } else {
                "${defaultTransferBaseDir.absolutePath}/$moveSubfolderName"
            }

            AlertDialog(
                onDismissRequest = { showMoveDialog = false },
                title = { Text("📦 Move $selectedCount Documents", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Selected documents will be moved atomically under their updated virtual names, with file timestamps set to their extracted date stamps.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Target Destination Path:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    targetFullPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedTextField(
                            value = moveSubfolderName,
                            onValueChange = { moveSubfolderName = it },
                            label = { Text("Subfolder [Date +_+ Time stamp]") },
                            placeholder = { Text("e.g. 151126_22:50:00") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Format: DDMMYY_hh:mm:ss. Path will be created if not already present.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            showMoveDialog = false
                            showMoveFolderPicker = true
                        }) {
                            Text("Other Folder...")
                        }
                        Button(onClick = {
                            showMoveDialog = false
                            val selectedDocs = items.filter { selectedUris.contains(it.uri) }
                            if (selectedDocs.isEmpty()) return@Button
                            scope.launch {
                                isMovingFiles = true
                                Toast.makeText(context, "Moving ${selectedDocs.size} documents...", Toast.LENGTH_SHORT).show()
                                val result = performAtomicMove(
                                    context,
                                    selectedDocs,
                                    Uri.fromFile(defaultTransferBaseDir),
                                    moveSubfolderName
                                )
                                isMovingFiles = false
                                if (result.success) {
                                    val oldUriToNewDoc = mutableMapOf<String, Doc>()
                                    for (i in selectedDocs.indices) {
                                        val oldDoc = selectedDocs[i]
                                        val newDoc = result.updatedDocs.getOrNull(i)
                                        if (newDoc != null) {
                                            oldUriToNewDoc[oldDoc.uri] = newDoc
                                        }
                                    }
                                    val newItems = items.map { item ->
                                        oldUriToNewDoc[item.uri] ?: item
                                    }
                                    refresh(newItems)
                                    selectedUris = emptySet()
                                    val destFolderName = when {
                                        moveSubfolderName.trim().startsWith("/") -> File(moveSubfolderName.trim()).name
                                        moveSubfolderName.isNotBlank() -> moveSubfolderName.trim()
                                        else -> defaultTransferBaseDir.name
                                    }
                                    moveSubfolderName = ""
                                    Toast.makeText(context, "🎉 Successfully moved ${result.movedCount} documents to '$destFolderName'!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "❌ ${result.errorMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Text("Move Now")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMoveDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showMoveFolderPicker) {
            FolderPickerDialog(
                title = "Choose Destination Folder",
                confirmButtonText = { "Move to '${it.name.ifBlank { "Folder" }}'" },
                onFolderSelected = { targetDir ->
                    showMoveFolderPicker = false
                    val selectedDocs = items.filter { selectedUris.contains(it.uri) }
                    if (selectedDocs.isEmpty()) return@FolderPickerDialog
                    scope.launch {
                        isMovingFiles = true
                        Toast.makeText(context, "Moving ${selectedDocs.size} documents...", Toast.LENGTH_SHORT).show()
                        val result = performAtomicMove(context, selectedDocs, Uri.fromFile(targetDir), moveSubfolderName)
                        isMovingFiles = false
                        if (result.success) {
                            val oldUriToNewDoc = mutableMapOf<String, Doc>()
                            for (i in selectedDocs.indices) {
                                val oldDoc = selectedDocs[i]
                                val newDoc = result.updatedDocs.getOrNull(i)
                                if (newDoc != null) {
                                    oldUriToNewDoc[oldDoc.uri] = newDoc
                                }
                            }
                            val newItems = items.map { item ->
                                oldUriToNewDoc[item.uri] ?: item
                            }
                            refresh(newItems)
                            selectedUris = emptySet()
                            val destFolderName = if (moveSubfolderName.isNotBlank()) moveSubfolderName.trim() else targetDir.name
                            moveSubfolderName = ""
                            Toast.makeText(context, "🎉 Successfully moved ${result.movedCount} documents to '$destFolderName'!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "❌ ${result.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onOpenSystemPicker = {
                    showMoveFolderPicker = false
                    try {
                        moveTargetFolderPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open folder picker: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismissRequest = {
                    showMoveFolderPicker = false
                }
            )
        }

        if (showScanFolderPromptDialog) {
            FolderPickerDialog(
                title = "Select Folder to Scan",
                confirmButtonText = { "Scan '${it.name.ifBlank { "Folder" }}' (Recursive)" },
                onFolderSelected = { targetDir ->
                    showScanFolderPromptDialog = false
                    val targetUri = Uri.fromFile(targetDir)
                    val prevUri = libPrefs.getString("scanned_folder_uri", "") ?: ""
                    val isDifferent = prevUri.isNotBlank() && prevUri != targetUri.toString()
                    startFolderScan(targetUri, retainExisting = !isDifferent)
                },
                onEntirePhoneSelected = {
                    showScanFolderPromptDialog = false
                    val prevScope = libPrefs.getString("scanned_folder_name", "") ?: ""
                    val isDifferent = prevScope != "Entire Device"
                    startFullDeviceScan(retainExisting = !isDifferent)
                },
                onOpenSystemPicker = {
                    showScanFolderPromptDialog = false
                    try {
                        folderScanPickerLauncher.launch(null)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open folder picker: ${e.message}", Toast.LENGTH_SHORT).show()
                        startFullDeviceScan()
                    }
                },
                onDismissRequest = {
                    showScanFolderPromptDialog = false
                    if (items.isEmpty()) {
                        startFullDeviceScan()
                    }
                }
            )
        }

        if (showRescanOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showRescanOptionsDialog = false },
                title = {
                    Text(
                        "Rescan Documents",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Rescanning '$selectedScanFolderName'.\n\n" +
                        "Choose scan mode to proceed:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showRescanOptionsDialog = false
                                Toast.makeText(context, "Merging new files into existing scan data...", Toast.LENGTH_SHORT).show()
                                triggerRescan(retainExisting = true)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("1. Merge With EarlierScans")
                        }
                        OutlinedButton(
                            onClick = {
                                showRescanOptionsDialog = false
                                Toast.makeText(context, "Starting fresh scan...", Toast.LENGTH_SHORT).show()
                                triggerRescan(retainExisting = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("2. Fresh Scan")
                        }
                    }
                },
                dismissButton = null
            )
        }

        if (showDeleteConfirmDialog) {
            val selectedCount = selectedUris.size
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🗑️", fontSize = 20.sp)
                        Text("Delete Documents", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Selected items will be permanently deleted.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Are you sure you want to permanently delete $selectedCount document(s) from your device storage? This operation cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            val toDelete = items.filter { selectedUris.contains(it.uri) }
                            if (toDelete.isEmpty()) return@Button
                            scope.launch {
                                isDeletingFiles = true
                                Toast.makeText(context, "Deleting ${toDelete.size} document(s)...", Toast.LENGTH_SHORT).show()
                                val count = performDeleteDocuments(context, toDelete)
                                isDeletingFiles = false
                                val deletedUriSet = toDelete.map { it.uri }.toSet()
                                val newItems = items.filterNot { deletedUriSet.contains(it.uri) }
                                refresh(newItems)
                                selectedUris = emptySet()
                                Toast.makeText(context, "🗑️ Permanently deleted $count document(s)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete Permanently", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        var showAllUncategorized by remember { mutableStateOf(false) }
        var showNoTextDocs by remember { mutableStateOf(false) }

        fun isNoTextDoc(d: Doc): Boolean {
            val clean = d.extractedText.replace(Regex("""[^a-zA-Z0-9]"""), "")
            if (d.status == "True" || d.status == "OK" || d.status == "Error" || d.status == "UNCATEGORIZABLE") {
                if (clean.length < 5) return true
                if (d.responseReceived.contains("No readable text", ignoreCase = true)) return true
            }
            return false
        }

        val hasCategorized = items.any { isCategorizedDoc(it) }
        val uncategorizedCount = items.count { !isCategorizedDoc(it) && it.status != "Duplicate" }
        val noTextCount = items.count { isNoTextDoc(it) && it.status != "Duplicate" }

        val shown = remember(items, allProcessed, typeFilter, searchQuery, dateFilterMode, showAllUncategorized, showNoTextDocs) {
            val seenShownHashes = mutableSetOf<String>()
            val seenShownTexts = mutableSetOf<String>()
            val seenShownProposals = mutableSetOf<String>()

            items.filter { d ->
                if (d.status == "Duplicate") return@filter false
                // Files with no textual contents are hidden by default
                if (!showNoTextDocs && isNoTextDoc(d)) return@filter false

                // If there are categorized documents and user hasn't toggled showAll, hide uncategorized
                // If there are NO categorized documents at all, show all non-duplicates so cards are never invisible
                if (allProcessed && hasCategorized && !showAllUncategorized && !isCategorizedDoc(d)) return@filter false

                // Real-time content & binary duplicate filtering
                if (d.digest.isNotBlank() && d.digest != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") {
                    if (!seenShownHashes.add(d.digest)) return@filter false
                }
                if (d.extractedText.isNotBlank()) {
                    val normText = d.extractedText.lowercase().replace(Regex("""[^a-z0-9]"""), "")
                    if (normText.length >= 40) {
                        if (!seenShownTexts.add(normText.take(600))) return@filter false
                    }
                }
                if (d.proposed.isNotBlank() && d.proposed != "N/A" && d.proposed != d.name && (d.status == "True" || d.status == "OK")) {
                    val propKey = "${d.category.lowercase()}::${d.proposed.lowercase()}"
                    if (!seenShownProposals.add(propKey)) return@filter false
                }

                val matchesType = when (typeFilter) {
                    "PDF" -> d.type == "PDF"
                    "IMAGE" -> d.type == "Image"
                    "DOCS" -> d.type in setOf("Word", "Excel", "PowerPoint", "Text")
                    else -> true
                }
                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    DateSearchHelper.matchesSearch(d, searchQuery, dateFilterMode)
                }
                matchesType && matchesSearch
            }
        }

        val processedCount = items.count { it.status == "True" || it.status == "Error" || it.status == "Skipped" || it.status == "Duplicate" }
        val totalCount = items.size
        val pdfCount = items.count { it.type == "PDF" && it.status != "Duplicate" }
        val imageCount = items.count { it.type == "Image" && it.status != "Duplicate" }
        val docsCount = items.count { it.type in setOf("Word", "Excel", "PowerPoint", "Text") && it.status != "Duplicate" }
        val dupsCount = items.count { it.status == "Duplicate" }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Hidden Keepalive WebView for Cloudflare connection (hidden from end user)
            if (signedIn) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            visibility = View.GONE
                            layoutParams = ViewGroup.LayoutParams(0, 0)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {}
                            val headers = if (token.isNotBlank()) mapOf("Authorization" to "Bearer $token") else emptyMap()
                            loadUrl("${backend.trimEnd('/')}/health", headers)
                            hiddenWebView = this
                        }
                    },
                    modifier = Modifier.size(0.dp)
                )
            }

            // FIXED HEADER AND CONTROLS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Header Bar (Title & Sign out / Done) - Compact to avoid notch/overlap and save vertical space
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (isPickerMode) "Select File" else "SmartDocManager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isPickerMode) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedAiProvider == "gemini") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val aiName = if (selectedAiProvider == "gemini") "✨ Gemini" else "⚡ Groq"
                                    Text(
                                        text = "$aiName $quotaDisplayStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (selectedAiProvider == "gemini") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        } else {
                            Text(
                                "• Pick to upload",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (isPickerMode) {
                        if (selectedUris.isNotEmpty()) {
                            Button(
                                onClick = {
                                    if (activity != null) {
                                        val selectedDocs = items.filter { selectedUris.contains(it.uri) }
                                        returnMultipleFiles(activity, selectedDocs)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Done (${selectedUris.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    activity?.setResult(Activity.RESULT_CANCELED)
                                    activity?.finish()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                authPrefs.edit().clear().apply()
                                token = ""
                                signedIn = false
                                isOfflineMode = false
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Sign out", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Daily Cloud Quota Notice Banner
                if (dailyLimitNotice.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⚠️ $dailyLimitNotice",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { dailyLimitNotice = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("✕", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // Cancellable banner message
                if (completionBannerMessage.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🎉 $completionBannerMessage",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { completionBannerMessage = "" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("✕", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Grey Bar: Group 1 (Folder + Count vertically) and Group 2 (Equispaced action icons with left padding)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Group 1: Left aligned with selected folder and [processed count/total count] arranged vertically, count at bottom
                        Column(
                            modifier = Modifier
                                .widthIn(max = 140.dp)
                                .clickable { showScanFolderPromptDialog = true }
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("📁", fontSize = 11.sp)
                                Text(
                                    text = selectedScanFolderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "[$processedCount/$totalCount]",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                if (isActivelyProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Group 2: Spanned to remaining width and icons arranged horizontally equispaced, with extra padding on left
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Folder Selection Option: Opens Folder Browser Dialog
                            IconButton(
                                onClick = { showScanFolderPromptDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("📁", fontSize = 16.sp)
                            }

                            // Rescan / Refresh icon: Prompts Fresh Scan vs. Merge With EarlierScans
                            IconButton(
                                onClick = {
                                    val isGemini = selectedAiProvider.lowercase() == "gemini"
                                    val isCurrentQuotaExhausted = RateLimiter.isQuotaExhausted(context, selectedAiProvider) || remainingDailyQuota <= 0
                                    if (isCurrentQuotaExhausted) {
                                        // Intercept: Do not remove already processed files cards from list.
                                        // Show popup message for current api until clock finishes.
                                        if (isGemini) {
                                            val (h, d) = RateLimiter.getGeminiResetInfo()
                                            geminiResetHour = h
                                            geminiResetDate = d
                                            showGeminiQuotaDialog = true
                                        } else {
                                            val (h, d) = RateLimiter.getGroqResetInfo()
                                            groqResetHour = h
                                            groqResetDate = d
                                            showGroqQuotaDialog = true
                                        }
                                    } else {
                                        hasNotifiedCompletion = false
                                        showRescanOptionsDialog = true
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("🔄", fontSize = 16.sp)
                            }

                            // Play / Pause icon (or Done button when multiple files selected in picker mode)
                            if (isPickerMode && selectedUris.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        if (activity != null) {
                                            val selectedDocs = items.filter { selectedUris.contains(it.uri) }
                                            returnMultipleFiles(activity, selectedDocs)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(
                                        "Done (${selectedUris.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                val isRunning = activeProcessingFile.isNotBlank() && !isQueuePaused
                                IconButton(
                                    onClick = {
                                        if (isRunning) {
                                            isQueuePaused = true
                                            Toast.makeText(context, "Processing paused", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val isExhausted = RateLimiter.isQuotaExhausted(context, selectedAiProvider) || remainingDailyQuota <= 0
                                            if (isExhausted) {
                                                val isGemini = selectedAiProvider.lowercase() == "gemini"
                                                if (isGemini) {
                                                    val (h, d) = RateLimiter.getGeminiResetInfo()
                                                    geminiResetHour = h
                                                    geminiResetDate = d
                                                    showGeminiQuotaDialog = true
                                                } else {
                                                    val (h, d) = RateLimiter.getGroqResetInfo()
                                                    groqResetHour = h
                                                    groqResetDate = d
                                                    showGroqQuotaDialog = true
                                                }
                                            } else {
                                                isQueuePaused = false
                                                Toast.makeText(context, "Processing resumed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(if (isRunning) "⏸️" else "▶️", fontSize = 16.sp)
                                }
                            }

                            // Interactive Funnel icon trigger with dropdown menu
                            Box(contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { filterDropdownExpanded = !filterDropdownExpanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_filter),
                                        contentDescription = "Filter Documents",
                                        tint = if (typeFilter != "ALL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = filterDropdownExpanded,
                                    onDismissRequest = { filterDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "ALL (${items.count { it.status != "Duplicate" }})",
                                                fontWeight = if (typeFilter == "ALL") FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_all),
                                                contentDescription = null,
                                                tint = if (typeFilter == "ALL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            typeFilter = "ALL"
                                            filterDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "IMAGES ($imageCount)",
                                                fontWeight = if (typeFilter == "IMAGE") FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_image),
                                                contentDescription = null,
                                                tint = if (typeFilter == "IMAGE") Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            typeFilter = "IMAGE"
                                            filterDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "PDFs ($pdfCount)",
                                                fontWeight = if (typeFilter == "PDF") FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_pdf),
                                                contentDescription = null,
                                                tint = if (typeFilter == "PDF") Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            typeFilter = "PDF"
                                            filterDropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "OFFICE / TEXT ($docsCount)",
                                                fontWeight = if (typeFilter == "DOCS") FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_all),
                                                contentDescription = null,
                                                tint = if (typeFilter == "DOCS") Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            typeFilter = "DOCS"
                                            filterDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Search field: displayed once all files are processed
                if (allProcessed) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    suggestionsExpanded = it.isNotBlank()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search name, LFT/CBC, lab, or date (01-05-2026, 1 May)...", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Text("🔍") },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            suggestionsExpanded = false
                                        }) {
                                            Text("✕")
                                        }
                                    }
                                },
                                singleLine = true
                            )

                            val suggestions = remember(searchQuery, items) {
                                if (searchQuery.isNotBlank()) DateSearchHelper.getSearchSuggestions(searchQuery, items) else emptyList()
                            }

                            if (suggestionsExpanded && suggestions.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = suggestionsExpanded,
                                    onDismissRequest = { suggestionsExpanded = false },
                                    properties = PopupProperties(focusable = false)
                                ) {
                                    suggestions.forEach { suggestion ->
                                        DropdownMenuItem(
                                            text = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                                            onClick = {
                                                searchQuery = suggestion
                                                suggestionsExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Date Filter Selection Chips: All Dates, Date Issued, Date Created
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Date Filter:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf("ANY" to "All Dates", "ISSUED" to "Date Issued", "CREATED" to "Date Created").forEach { (mode, label) ->
                                Surface(
                                    onClick = { dateFilterMode = mode },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (dateFilterMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (dateFilterMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (dateFilterMode == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (dateFilterMode == mode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            if (!allProcessed) {
                if (items.isEmpty() && !isScanning) {
                    // Empty state when folder has no documents
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("📂", fontSize = 42.sp)
                            Text(
                                "No Supported Documents Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "No supported document or image files (PDF, Word, Excel, PowerPoint, Text, Image) were found in '$selectedScanFolderName'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(onClick = { showScanFolderPromptDialog = true }) {
                                    Text("📁 Choose Another Folder")
                                }
                                OutlinedButton(onClick = { startFullDeviceScan() }) {
                                    Text("📱 Scan Entire Phone")
                                }
                            }
                        }
                    }
                } else {
                    // In-progress scanning status bar with prominent Change Folder button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val activeAiLabel = if (selectedAiProvider.lowercase() == "groq") "Groq" else "Gemini"
                            Text(
                                text = if (isScanning) "⏳ Scanning: $selectedScanFolderName..." else "⚡ Processing: $selectedScanFolderName ($activeAiLabel)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { showScanFolderPromptDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("📁 Change Folder", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider()

                    // SCANNING IN PROGRESS: Fixed Table Header & Row progress
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPickerMode) {
                            Box(
                                modifier = Modifier.width(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Checkbox(
                                    checked = shown.isNotEmpty() && shown.all { selectedUris.contains(it.uri) },
                                    onCheckedChange = { checkAll ->
                                        selectedUris = if (checkAll) shown.map { it.uri }.toSet() else emptySet()
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text("File", Modifier.weight(3.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Type", Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Status", Modifier.weight(1.8f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Virtual Name", Modifier.weight(3.2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider()

                    // SCROLLABLE LIST OF SCANNING ITEMS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 80.dp)
                    ) {
                        items(
                            items = shown,
                            key = { it.uri }
                        ) { d ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPickerMode) {
                                        Box(
                                            modifier = Modifier.width(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Checkbox(
                                                checked = selectedUris.contains(d.uri),
                                                onCheckedChange = { isChecked ->
                                                    selectedUris = if (isChecked) selectedUris + d.uri else selectedUris - d.uri
                                                },
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    val rowDisplayName = d.custom.ifBlank { if (d.status == "True" && d.proposed.isNotBlank() && d.proposed != "N/A") d.proposed else d.name }
                                    Column(Modifier.weight(3.2f)) {
                                        Text(
                                            rowDisplayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (rowDisplayName != d.name) {
                                            Text(
                                                "Orig: ${d.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    val typeText = when (d.type) {
                                        "Word" -> "DOC"
                                        "Excel" -> "XLS"
                                        "PowerPoint" -> "PPT"
                                        "Text" -> "TXT"
                                        "Image", "IMG" -> "IMG"
                                        else -> "PDF"
                                    }
                                    Text(typeText, Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall)
                                    Box(Modifier.weight(1.8f)) {
                                        when (d.status) {
                                            "True" -> Text("✅ Done", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            "Error" -> Text("⚠️ Error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                            "Skipped" -> Text("⏭️ Skipped", style = MaterialTheme.typography.bodySmall)
                                            else -> {
                                                if (d.name == activeProcessingFile) {
                                                    LinearProgressIndicator(Modifier.fillMaxWidth(0.85f))
                                                } else {
                                                    Text("⏳ Queued", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        d.custom.ifBlank { d.proposed.ifBlank { "..." } },
                                        Modifier.weight(3.2f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            } else {
                // POST-SCAN: CLEAN CARD-BASED LAYOUT WITH MULTI-DOCUMENT SHARING
                // 1. Action Bar: Select All / Deselect All + Share Button
                val allSelected = shown.isNotEmpty() && shown.all { selectedUris.contains(it.uri) }
                val canShare = selectedUris.isNotEmpty() && selectedUris.size <= 20

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Select All / Deselect All control
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    selectedUris = if (allSelected) emptySet() else shown.map { it.uri }.toSet()
                                }
                            ) {
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = { checkAll ->
                                        selectedUris = if (checkAll) shown.map { it.uri }.toSet() else emptySet()
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (allSelected) "Deselect All" else "Select All",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (hasCategorized && uncategorizedCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    onClick = { showAllUncategorized = !showAllUncategorized },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (showAllUncategorized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (showAllUncategorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(
                                            text = if (showAllUncategorized) "Categorized Only" else "Show All (${items.count { it.status != "Duplicate" }})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (showAllUncategorized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (noTextCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    onClick = { showNoTextDocs = !showNoTextDocs },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (showNoTextDocs) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, if (showNoTextDocs) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                        Text(
                                            text = if (showNoTextDocs) "Hide Empty Text ($noTextCount)" else "Empty Text ($noTextCount)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (showNoTextDocs) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Share, Move & Delete Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Move Action Button
                            Button(
                                onClick = {
                                    moveSubfolderName = Classifier.formatTransferTimestamp()
                                    showMoveDialog = true
                                },
                                enabled = selectedUris.isNotEmpty() && !isMovingFiles,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (isMovingFiles) "⏳..." else "📦 Move (${selectedUris.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Red-colored Delete Action Button / Icon
                            Button(
                                onClick = {
                                    showDeleteConfirmDialog = true
                                },
                                enabled = selectedUris.isNotEmpty() && !isDeletingFiles,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                                    disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text("🗑️", fontSize = 13.sp)
                                    Text(
                                        text = if (isDeletingFiles) "..." else "${selectedUris.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val selectedDocs = shown.filter { selectedUris.contains(it.uri) }
                                    shareMultipleFiles(context, selectedDocs)
                                },
                                enabled = canShare,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "📤 Share (${selectedUris.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Explicit notice near share icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "ℹ️ At a time only 20 files can be exported.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedUris.size > 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // 2. SCROLLABLE LIST OF CARDS
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (shown.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("📄", fontSize = 36.sp)
                                    Text(
                                        "No Documents Visible in View",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (uncategorizedCount > 0 && !showAllUncategorized) {
                                        Text(
                                            "$uncategorizedCount documents are uncategorized, pending, or had processing errors.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Button(onClick = { showAllUncategorized = true }) {
                                            Text("Show All Documents ($uncategorizedCount)")
                                        }
                                    } else {
                                        Text(
                                            "No documents matched the active filter or search query.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        OutlinedButton(onClick = {
                                            typeFilter = "ALL"
                                            searchQuery = ""
                                            showAllUncategorized = true
                                        }) {
                                            Text("Reset Filters")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = shown,
                        key = { it.uri }
                    ) { d ->
                        val isExpanded = expandedDocUri == d.uri

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Row 1: Left Checkbox, Name Link in Center, Right Expand Icon
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Dedicated Checkbox on left side
                                    Checkbox(
                                        checked = selectedUris.contains(d.uri),
                                        onCheckedChange = { isChecked ->
                                            selectedUris = if (isChecked) selectedUris + d.uri else selectedUris - d.uri
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )

                                    // Name: <original name as link to open actual document>
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Name:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            val badgeBg = when (d.type) {
                                                "Word" -> Color(0xFF2B579A)
                                                "Excel" -> Color(0xFF217346)
                                                "PowerPoint" -> Color(0xFFD24726)
                                                "Text" -> Color(0xFF5C6BC0)
                                                "Image" -> Color(0xFF1976D2)
                                                else -> Color(0xFFD32F2F)
                                            }
                                            val badgeText = when (d.type) {
                                                "Word" -> "DOC"
                                                "Excel" -> "XLS"
                                                "PowerPoint" -> "PPT"
                                                "Text" -> "TXT"
                                                "Image" -> "IMG"
                                                else -> "PDF"
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = badgeBg
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }

                                            // Status / Category indicator pill (Clickable to toggle expansion)
                                            val statusBg = when {
                                                d.status == "True" -> MaterialTheme.colorScheme.primaryContainer
                                                d.status == "Error" -> MaterialTheme.colorScheme.errorContainer
                                                d.status == "False" -> MaterialTheme.colorScheme.tertiaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                            val statusColor = when {
                                                d.status == "True" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                d.status == "Error" -> MaterialTheme.colorScheme.onErrorContainer
                                                d.status == "False" -> MaterialTheme.colorScheme.onTertiaryContainer
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                            val statusLabel = when {
                                                d.status == "True" -> d.category.ifBlank { "Categorized" }
                                                d.status == "Error" -> "⚠️ Error"
                                                d.status == "False" -> "⏳ Pending"
                                                else -> d.status
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = statusBg,
                                                modifier = Modifier.clickable {
                                                    expandedDocUri = if (expandedDocUri == d.uri) null else d.uri
                                                }
                                            ) {
                                                Text(
                                                    text = statusLabel,
                                                    color = statusColor,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        val effectiveName = d.custom.ifBlank { if (d.proposed.isNotBlank() && d.proposed != "N/A") d.proposed else d.name }
                                        Text(
                                            text = effectiveName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                textDecoration = TextDecoration.Underline,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable {
                                                if (isPickerMode && selectedUris.isNotEmpty()) {
                                                    selectedUris = if (selectedUris.contains(d.uri)) selectedUris - d.uri else selectedUris + d.uri
                                                } else if (isPickerMode && activity != null) {
                                                    returnSingleFile(activity, d)
                                                } else {
                                                    openDocument(context, d)
                                                }
                                            }
                                        )
                                        if (effectiveName != d.name) {
                                            Text(
                                                text = "Original: ${d.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Dedicated Expand Icon on right side (Accordion toggle)
                                    IconButton(
                                        onClick = {
                                            expandedDocUri = if (expandedDocUri == d.uri) null else d.uri
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_dropdown),
                                            contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Row 2: Virtual Name: <proposed name as editable field for user to provide unique name of his choice. Saved on blur>
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Virtual Name:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    var textState by remember(d.uri, d.custom, d.proposed) {
                                        mutableStateOf(d.custom.ifBlank { d.proposed })
                                    }
                                    OutlinedTextField(
                                        value = textState,
                                        onValueChange = { textState = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { focusState ->
                                                if (!focusState.isFocused) {
                                                    val trimmed = textState.trim()
                                                    val newCustom = if (trimmed == d.proposed) "" else trimmed
                                                    if (newCustom != d.custom) {
                                                        val updated = items.map {
                                                            if (it.uri == d.uri) it.copy(custom = newCustom) else it
                                                        }
                                                        refresh(updated)
                                                    }
                                                }
                                            },
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        singleLine = true,
                                        placeholder = { Text("Enter unique virtual name...") }
                                    )
                                }

                                // Row 3: Dedicated Payload & Response Inspector
                                if (isExpanded) {
                                    ExpandedCardPayloadResponseView(d)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedCardPayloadResponseView(d: Doc) {
    val targetProvider = when {
        d.promptSent.contains("\"provider\": \"groq\"", ignoreCase = true) || d.promptSent.contains("Primary AI: GROQ", ignoreCase = true) -> "Groq"
        d.promptSent.contains("\"provider\": \"gemini\"", ignoreCase = true) || d.promptSent.contains("Primary AI: GEMINI", ignoreCase = true) -> "Google Gemini"
        d.responseReceived.contains("Groq", ignoreCase = true) -> "Groq"
        d.responseReceived.contains("Gemini", ignoreCase = true) -> "Google Gemini"
        else -> "AI Engine"
    }

    val formattedPayload = remember(d.promptSent) {
        val trimmed = d.promptSent.trim()
        try {
            if (trimmed.startsWith("{")) JSONObject(trimmed).toString(2)
            else if (trimmed.startsWith("[")) org.json.JSONArray(trimmed).toString(2)
            else trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    val formattedResponse = remember(d.responseReceived) {
        val trimmed = d.responseReceived.trim()
        try {
            if (trimmed.startsWith("{")) JSONObject(trimmed).toString(2)
            else if (trimmed.startsWith("[")) org.json.JSONArray(trimmed).toString(2)
            else trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    val isErrorResponse = d.status == "Error" ||
        d.responseReceived.contains("\"error\"", ignoreCase = true) ||
        d.responseReceived.contains("HTTP 4", ignoreCase = true) ||
        d.responseReceived.contains("HTTP 5", ignoreCase = true) ||
        d.responseReceived.contains("Exception", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Block 1: Payload Sent to Gemini/Groq
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "📤 Payload Sent to $targetProvider",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = formattedPayload.ifBlank { "No payload recorded." },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }
            }

            // Block 2: Response Received from Gemini/Groq
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isErrorResponse) "📥 Response / Error from $targetProvider" else "📥 Response Received from $targetProvider",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isErrorResponse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isErrorResponse) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = formattedResponse.ifBlank { "No response recorded." },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = if (isErrorResponse) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class ClassifierResultData(
    val result: Classifier.Result,
    val extractedText: String,
    val dimensions: String,
    val hash: String,
    val isDuplicate: Boolean = false,
    val relocatedDoc: Doc? = null
)
