package com.smartdoc.poc

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object RateLimiter {
    const val MAX_REQUESTS_PER_MINUTE = 10
    const val WINDOW_MINUTE_MS = 60_000L
    const val MAX_REQUESTS_PER_DAY = 1000
    const val WINDOW_DAY_MS = 24 * 60 * 60 * 1000L // 24 hours

    const val DAILY_LIMIT_MESSAGE = "1000 request per day limit is reached. To process further continue after 24 hours."

    private val timestampsLock = Mutex()
    private val requestTimestamps = ArrayDeque<Long>()

    /**
     * Checks if the 1,000 requests per 24-hour daily quota has been exceeded.
     */
    fun checkDailyLimit(context: Context): Pair<Boolean, String> {
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val windowStart = prefs.getLong("daily_window_start", 0L)
        val count = prefs.getInt("daily_request_count", 0)

        if (windowStart == 0L || (now - windowStart) >= WINDOW_DAY_MS) {
            // New 24-hour cycle begins
            prefs.edit()
                .putLong("daily_window_start", now)
                .putInt("daily_request_count", 0)
                .apply()
            return Pair(true, "")
        }

        if (count >= MAX_REQUESTS_PER_DAY) {
            return Pair(false, DAILY_LIMIT_MESSAGE)
        }

        return Pair(true, "")
    }

    /**
     * Self-throttling to ensure the app NEVER dispatches more than 10 requests per 60 seconds.
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
     * Records a dispatched cloud request into both the 60s sliding window and persistent daily counter.
     */
    suspend fun recordRequest(context: Context) {
        val now = System.currentTimeMillis()
        timestampsLock.withLock {
            requestTimestamps.addLast(now)
        }
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val windowStart = prefs.getLong("daily_window_start", 0L)
        val currentCount = prefs.getInt("daily_request_count", 0)
        val edit = prefs.edit()
        if (windowStart == 0L || (now - windowStart) >= WINDOW_DAY_MS) {
            edit.putLong("daily_window_start", now)
            edit.putInt("daily_request_count", 1)
        } else {
            edit.putInt("daily_request_count", currentCount + 1)
        }
        edit.apply()
    }

    fun getDailyCount(context: Context): Int {
        val prefs = context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val windowStart = prefs.getLong("daily_window_start", 0L)
        if (windowStart == 0L || (now - windowStart) >= WINDOW_DAY_MS) {
            return 0
        }
        return prefs.getInt("daily_request_count", 0)
    }

    fun resetForTesting(context: Context) {
        requestTimestamps.clear()
        context.getSharedPreferences("smartdoc_rate_limits", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
