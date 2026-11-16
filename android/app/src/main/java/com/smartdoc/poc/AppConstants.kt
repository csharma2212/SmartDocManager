package com.smartdoc.poc

import android.content.Context
import org.json.JSONObject

object AppConstants {
    const val FALLBACK_BACKEND_URL = "https://smartdocmanager-api.csharma2212.workers.dev"
    const val FALLBACK_WEB_CLIENT_ID = "492541392963-b0mf650fohoas9m6a24fa32enrstjrr6.apps.googleusercontent.com"

    private var cachedBackendUrl: String? = null
    private var cachedWebClientId: String? = null

    private fun loadConfig(context: Context) {
        if (cachedBackendUrl != null && cachedWebClientId != null) return
        try {
            val jsonText = context.assets.open("app-config.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonText)
            val backend = obj.optString("backend_url", "")
            val webId = obj.optString("google_web_client_id", "")
            cachedBackendUrl = if (backend.isNotBlank()) backend else FALLBACK_BACKEND_URL
            cachedWebClientId = if (webId.isNotBlank() && !webId.startsWith("REPLACE_")) webId else FALLBACK_WEB_CLIENT_ID
        } catch (_: Exception) {
            cachedBackendUrl = FALLBACK_BACKEND_URL
            cachedWebClientId = FALLBACK_WEB_CLIENT_ID
        }
    }

    fun getBackendUrl(context: Context): String {
        loadConfig(context)
        return cachedBackendUrl ?: FALLBACK_BACKEND_URL
    }

    fun getGoogleWebClientId(context: Context): String {
        loadConfig(context)
        return cachedWebClientId ?: FALLBACK_WEB_CLIENT_ID
    }
}
