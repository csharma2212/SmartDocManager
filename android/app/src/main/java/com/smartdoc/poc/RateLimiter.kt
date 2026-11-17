package com.smartdoc.poc

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object RateLimiter {
    const val MAX_REQUESTS_PER_MINUTE = 15
    const val WINDOW_MINUTE_MS = 60_000L
    const val MAX_REQUESTS_PER_DAY = 1500
    const val WINDOW_DAY_MS = 24 * 60 * 60 * 1000L // 24 hours

    const val DAILY_LIMIT_MESSAGE = "1500 request per day limit is reached. To process further continue after 24 hours."

    fun getGeminiDayKey(): String {
        return try {
            val laZone = java.time.ZoneId.of("America/Los_Angeles")
            java.time.LocalDate.now(laZone).toString()
        } catch (_: Exception) {
            val offset = java.time.ZoneOffset.ofHours(-8)
            java.time.LocalDate.now(offset).toString()
        }
    }

    fun getGroqDayKey(): String {
        val utcZone = java.time.ZoneOffset.UTC
        return java.time.LocalDate.now(utcZone).toString()
    }

    fun getGeminiResetInfo(): Pair<String, String> {
        val laZone = try {
            java.time.ZoneId.of("America/Los_Angeles")
        } catch (_: Exception) {
            java.time.ZoneOffset.ofHours(-8)
        }
        val systemZone = java.time.ZoneId.systemDefault()
        val laNow = java.time.ZonedDateTime.now(laZone)
        val nextMidnightLa = laNow.toLocalDate().plusDays(1).atStartOfDay(laZone)
        val localReset = nextMidnightLa.withZoneSameInstant(systemZone)
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US)
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.US)
        return Pair(localReset.format(timeFormatter), localReset.format(dateFormatter))
    }

    fun getGroqResetInfo(): Pair<String, String> {
        val utcZone = java.time.ZoneOffset.UTC
        val systemZone = java.time.ZoneId.systemDefault()
        val utcNow = java.time.ZonedDateTime.now(utcZone)
        val nextMidnightUtc = utcNow.toLocalDate().plusDays(1).atStartOfDay(utcZone)
        val localReset = nextMidnightUtc.withZoneSameInstant(systemZone)
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US)
        val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.US)
        return Pair(localReset.format(timeFormatter), localReset.format(dateFormatter))
    }

    fun getGeminiResetEpochMillis(): Long {
        val laZone = try {
            java.time.ZoneId.of("America/Los_Angeles")
        } catch (_: Exception) {
            java.time.ZoneOffset.ofHours(-8)
        }
        val laNow = java.time.ZonedDateTime.now(laZone)
        val nextMidnightLa = laNow.toLocalDate().plusDays(1).atStartOfDay(laZone)
        return nextMidnightLa.toInstant().toEpochMilli()
    }

    fun getGroqResetEpochMillis(): Long {
        val utcZone = java.time.ZoneOffset.UTC
        val utcNow = java.time.ZonedDateTime.now(utcZone)
        val nextMidnightUtc = utcNow.toLocalDate().plusDays(1).atStartOfDay(utcZone)
        return nextMidnightUtc.toInstant().toEpochMilli()
    }

    private val timestampsLock = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()

    /**
     * Checks if the 1,500 requests per day quota for the given provider has been exceeded.
     * Quota resets automatically on official provider calendar day boundaries:
     * - Gemini: Midnight US Pacific Time (00:00 PT)
     * - Groq: Midnight UTC (00:00 UTC)
     */
    fun checkDailyLimit(context: Context, provider: String = "gemini"): Pair<Boolean, String> {
        if (isQuotaExhausted(context, provider)) {
            return Pair(false, DAILY_LIMIT_MESSAGE)
        }

        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val isGroq = provider.lowercase() == "groq"
        val dayKey = if (isGroq) getGroqDayKey() else getGeminiDayKey()
        val prefDayKeyProp = if (isGroq) "groq_day_key" else "gemini_day_key"
        val prefCountProp = if (isGroq) "groq_daily_request_count" else "gemini_daily_request_count"

        val storedDayKey = prefs.getString(prefDayKeyProp, "") ?: ""
        val count = if (storedDayKey == dayKey) prefs.getInt(prefCountProp, 0) else 0

        if (storedDayKey != dayKey) {
            val prefExhausted = if (isGroq) "groq_quota_exhausted" else "gemini_quota_exhausted"
            val prefEpoch = if (isGroq) "groq_quota_reset_epoch" else "gemini_quota_reset_epoch"
            prefs.edit()
                .putString(prefDayKeyProp, dayKey)
                .putInt(prefCountProp, 0)
                .putBoolean(prefExhausted, false)
                .remove(prefEpoch)
                .apply()
            return Pair(true, "")
        }

        if (count >= MAX_REQUESTS_PER_DAY) {
            return Pair(false, DAILY_LIMIT_MESSAGE)
        }

        return Pair(true, "")
    }

    /**
     * Self-throttling to ensure the app NEVER dispatches more than 15 requests per 60 seconds.
     * Suspends coroutine until a slot in the 60-second sliding window is available.
     */
    suspend fun waitForSlot() {
        while (true) {
            val waitTime = timestampsLock.withLock {
                val now = System.currentTimeMillis()
                while (requestTimestamps.isNotEmpty() && now - requestTimestamps.first() >= WINDOW_MINUTE_MS) {
                    requestTimestamps.removeFirst()
                }
                if (requestTimestamps.size < MAX_REQUESTS_PER_MINUTE) {
                    0L
                } else {
                    val oldest = requestTimestamps.first()
                    (WINDOW_MINUTE_MS - (now - oldest)).coerceAtLeast(100L)
                }
            }
            if (waitTime <= 0L) break
            delay(waitTime)
        }
    }

    /**
     * Records a dispatched cloud request into both the 60s sliding window and persistent daily counter for the provider.
     */
    suspend fun recordRequest(context: Context, provider: String = "gemini") {
        val now = System.currentTimeMillis()
        timestampsLock.withLock {
            requestTimestamps.addLast(now)
        }
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val isGroq = provider.lowercase() == "groq"
        val dayKey = if (isGroq) getGroqDayKey() else getGeminiDayKey()
        val prefDayKeyProp = if (isGroq) "groq_day_key" else "gemini_day_key"
        val prefCountProp = if (isGroq) "groq_daily_request_count" else "gemini_daily_request_count"

        val storedDayKey = prefs.getString(prefDayKeyProp, "") ?: ""
        val currentCount = if (storedDayKey == dayKey) prefs.getInt(prefCountProp, 0) else 0

        prefs.edit()
            .putString(prefDayKeyProp, dayKey)
            .putInt(prefCountProp, currentCount + 1)
            .apply()
    }

    fun getDailyCount(context: Context, provider: String = "gemini"): Int {
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val isGroq = provider.lowercase() == "groq"
        val dayKey = if (isGroq) getGroqDayKey() else getGeminiDayKey()
        val prefDayKeyProp = if (isGroq) "groq_day_key" else "gemini_day_key"
        val prefCountProp = if (isGroq) "groq_daily_request_count" else "gemini_daily_request_count"

        val storedDayKey = prefs.getString(prefDayKeyProp, "") ?: ""
        if (storedDayKey != dayKey) {
            return 0
        }
        return prefs.getInt(prefCountProp, 0)
    }

    fun getRemainingDailyQuota(context: Context, provider: String = "gemini"): Int {
        if (isQuotaExhausted(context, provider)) return 0
        return (MAX_REQUESTS_PER_DAY - getDailyCount(context, provider)).coerceAtLeast(0)
    }

    fun formatQuotaDisplay(context: Context, provider: String = "gemini"): String {
        val count = getDailyCount(context, provider)
        val isGroq = provider.lowercase() == "groq"
        return if (isGroq) "($count/~1500)" else "($count/1500)"
    }

    /**
     * For Gemini: No artificial wait (triggers immediately on completion).
     * For Groq: Ensures a minimum 2000 ms cycle by waiting the remaining (2000 - elapsedMs) ms.
     */
    suspend fun applyPostRequestDelay(provider: String, elapsedMs: Long) {
        if (provider.lowercase() == "groq") {
            if (elapsedMs in 0 until 2000L) {
                delay(2000L - elapsedMs)
            }
        }
    }

    /**
     * Resets the daily count for the given provider so that switching or quota reset starts from the beginning (0 used / 1500 remaining).
     */
    fun resetProviderCount(context: Context, provider: String) {
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val isGroq = provider.lowercase() == "groq"
        val prefDayKeyProp = if (isGroq) "groq_day_key" else "gemini_day_key"
        val prefCountProp = if (isGroq) "groq_daily_request_count" else "gemini_daily_request_count"
        val dayKey = if (isGroq) getGroqDayKey() else getGeminiDayKey()
        prefs.edit()
            .putString(prefDayKeyProp, dayKey)
            .putInt(prefCountProp, 0)
            .apply()
    }

    fun markQuotaExhausted(context: Context, provider: String) {
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val isGroq = provider.lowercase() == "groq"
        val prefExhausted = if (isGroq) "groq_quota_exhausted" else "gemini_quota_exhausted"
        val prefEpoch = if (isGroq) "groq_quota_reset_epoch" else "gemini_quota_reset_epoch"
        val epoch = if (isGroq) getGroqResetEpochMillis() else getGeminiResetEpochMillis()
        prefs.edit()
            .putBoolean(prefExhausted, true)
            .putLong(prefEpoch, epoch)
            .apply()
    }

    fun isQuotaExhausted(context: Context, provider: String): Boolean {
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val isGroq = provider.lowercase() == "groq"
        val dayKey = if (isGroq) getGroqDayKey() else getGeminiDayKey()
        val prefDayKeyProp = if (isGroq) "groq_day_key" else "gemini_day_key"
        val storedDayKey = prefs.getString(prefDayKeyProp, "") ?: ""
        val prefExhausted = if (isGroq) "groq_quota_exhausted" else "gemini_quota_exhausted"
        val prefEpoch = if (isGroq) "groq_quota_reset_epoch" else "gemini_quota_reset_epoch"

        if (storedDayKey.isNotBlank() && storedDayKey != dayKey) {
            prefs.edit()
                .putString(prefDayKeyProp, dayKey)
                .putInt(if (isGroq) "groq_daily_request_count" else "gemini_daily_request_count", 0)
                .putBoolean(prefExhausted, false)
                .remove(prefEpoch)
                .apply()
            return false
        }

        val exhausted = prefs.getBoolean(prefExhausted, false)
        val epoch = prefs.getLong(prefEpoch, 0L)
        val now = System.currentTimeMillis()
        if (exhausted) {
            if (epoch == 0L || now < epoch) {
                return true
            } else {
                prefs.edit()
                    .putBoolean(prefExhausted, false)
                    .remove(prefEpoch)
                    .apply()
                return false
            }
        }
        return getDailyCount(context, provider) >= MAX_REQUESTS_PER_DAY
    }

    fun checkAndClearQuotaRestored(context: Context, provider: String): Boolean {
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val isGroq = provider.lowercase() == "groq"
        val prefExhausted = if (isGroq) "groq_quota_exhausted" else "gemini_quota_exhausted"
        val prefEpoch = if (isGroq) "groq_quota_reset_epoch" else "gemini_quota_reset_epoch"
        val isExhausted = prefs.getBoolean(prefExhausted, false)
        val epoch = prefs.getLong(prefEpoch, 0L)
        val now = System.currentTimeMillis()

        val dayKey = if (isGroq) getGroqDayKey() else getGeminiDayKey()
        val prefDayKeyProp = if (isGroq) "groq_day_key" else "gemini_day_key"
        val storedDayKey = prefs.getString(prefDayKeyProp, "") ?: ""
        val dayChanged = storedDayKey.isNotBlank() && storedDayKey != dayKey

        if (isExhausted && (now >= epoch || dayChanged)) {
            prefs.edit()
                .putBoolean(prefExhausted, false)
                .remove(prefEpoch)
                .apply()
            resetProviderCount(context, provider)
            return true
        }
        return false
    }

    fun clearMinuteWindow() {
        requestTimestamps.clear()
    }

    fun resetForTesting(context: Context) {
        requestTimestamps.clear()
        context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
