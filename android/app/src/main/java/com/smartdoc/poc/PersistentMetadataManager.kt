package com.smartdoc.poc

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PersistentMetadataManager {
    private const val TAG = "PersistentMetadata"
    private const val PREFS_NAME = "smartdoc_storage_prefs"
    private const val KEY_CUSTOM_PATH = "custom_metadata_dir"
    private const val DEFAULT_FOLDER = "SmartDocManager"
    const val METADATA_FILENAME = "smartdoc_metadata.json"

    fun getDefaultMetadataDirectory(): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val target = File(docsDir, DEFAULT_FOLDER)
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    fun getMetadataFile(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_CUSTOM_PATH, null)
        val dir = if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() || f.mkdirs()) f else getDefaultMetadataDirectory()
        } else {
            getDefaultMetadataDirectory()
        }
        return File(dir, METADATA_FILENAME)
    }

    fun getCustomDirectory(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CUSTOM_PATH, null)
    }

    fun setCustomDirectory(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_PATH, path).apply()
        Log.i(TAG, "Custom metadata directory set to: $path")
    }

    fun hasPersistentMetadata(context: Context): Boolean {
        val file = getMetadataFile(context)
        if (file.exists() && file.length() > 5L) return true
        val defaultFile = File(getDefaultMetadataDirectory(), METADATA_FILENAME)
        return defaultFile.exists() && defaultFile.length() > 5L
    }

    /**
     * Saves document metadata to both internal SharedPreferences and the external JSON file.
     */
    suspend fun save(context: Context, items: List<Doc>) {
        withContext(Dispatchers.IO) {
            try {
                val array = JSONArray()
                for (d in items) {
                    val obj = JSONObject()
                    obj.put("uri", d.uri)
                    obj.put("name", d.name)
                    obj.put("type", d.type)
                    obj.put("modified", d.modified)
                    obj.put("size", d.size)
                    obj.put("category", d.category)
                    obj.put("proposed", d.proposed)
                    obj.put("custom", d.custom)
                    obj.put("status", d.status)
                    obj.put("digest", d.digest)
                    obj.put("promptSent", d.promptSent)
                    obj.put("responseReceived", d.responseReceived)
                    obj.put("extractedText", d.extractedText)
                    obj.put("dimensions", d.dimensions)
                    obj.put("metadata", d.metadata)
                    array.put(obj)
                }

                val jsonStr = array.toString(2)

                // 1. Save to private app prefs as cache
                context.getSharedPreferences("smartdoc_library", Context.MODE_PRIVATE)
                    .edit()
                    .putString("items", jsonStr)
                    .apply()

                // 2. Save to external persistent file (survives app uninstalls)
                val file = getMetadataFile(context)
                file.parentFile?.mkdirs()
                file.writeText(jsonStr)
                Log.i(TAG, "Successfully saved ${items.size} documents to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving persistent metadata: ${e.message}", e)
            }
        }
    }

    /**
     * Loads saved metadata, cross-checks each document against disk,
     * purges deleted/missing files, updates the metadata file if changes occurred,
     * and returns the verified list of documents along with the count of purged items.
     */
    fun loadAndVerify(context: Context): Pair<List<Doc>, Int> {
        return try {
            val file = getMetadataFile(context)
            var jsonString: String? = null

            if (file.exists() && file.length() > 5L) {
                jsonString = file.readText()
                Log.i(TAG, "Loaded external metadata from ${file.absolutePath}")
            } else {
                val defaultFile = File(getDefaultMetadataDirectory(), METADATA_FILENAME)
                if (defaultFile.exists() && defaultFile.length() > 5L) {
                    jsonString = defaultFile.readText()
                    Log.i(TAG, "Loaded external metadata from default location: ${defaultFile.absolutePath}")
                } else {
                    jsonString = context.getSharedPreferences("smartdoc_library", Context.MODE_PRIVATE)
                        .getString("items", null)
                }
            }

            if (jsonString.isNullOrBlank()) {
                return Pair(emptyList(), 0)
            }

            val array = JSONArray(jsonString)
            val loadedDocs = mutableListOf<Doc>()

            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val st = o.optString("status", "False")
                val safeStatus = if (st == "Processing") "False" else st
                loadedDocs.add(
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
                )
            }

            // Cross-check with disk existence (Requirement 9)
            var purgedCount = 0
            val verifiedDocs = loadedDocs.filter { doc ->
                val exists = checkFileExistsOnDisk(context, doc.uri)
                if (!exists) {
                    purgedCount++
                    Log.w(TAG, "File no longer exists on disk, purged from metadata: ${doc.name} (${doc.uri})")
                }
                exists
            }

            // If any files were purged, update the persistent store immediately
            if (purgedCount > 0) {
                try {
                    val updatedArray = JSONArray()
                    for (d in verifiedDocs) {
                        val obj = JSONObject()
                        obj.put("uri", d.uri)
                        obj.put("name", d.name)
                        obj.put("type", d.type)
                        obj.put("modified", d.modified)
                        obj.put("size", d.size)
                        obj.put("category", d.category)
                        obj.put("proposed", d.proposed)
                        obj.put("custom", d.custom)
                        obj.put("status", d.status)
                        obj.put("digest", d.digest)
                        obj.put("promptSent", d.promptSent)
                        obj.put("responseReceived", d.responseReceived)
                        obj.put("extractedText", d.extractedText)
                        obj.put("dimensions", d.dimensions)
                        obj.put("metadata", d.metadata)
                        updatedArray.put(obj)
                    }
                    val updatedJson = updatedArray.toString(2)
                    file.parentFile?.mkdirs()
                    file.writeText(updatedJson)
                    context.getSharedPreferences("smartdoc_library", Context.MODE_PRIVATE)
                        .edit().putString("items", updatedJson).apply()
                    Log.i(TAG, "Updated metadata after purging $purgedCount missing files. Retained: ${verifiedDocs.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update purged metadata: ${e.message}")
                }
            }

            Pair(verifiedDocs, purgedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading persistent metadata: ${e.message}", e)
            Pair(emptyList(), 0)
        }
    }

    private fun checkFileExistsOnDisk(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path ?: return false
                File(path).exists()
            } else if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } else {
                val f = File(uriString)
                f.exists()
            }
        } catch (_: Exception) {
            false
        }
    }
}
