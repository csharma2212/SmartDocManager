package com.smartdoc.poc

import org.junit.Assert.*
import org.junit.Test

class RateLimiterTest {
    @Test
    fun rateLimiterConstants() {
        assertEquals(15, RateLimiter.MAX_REQUESTS_PER_MINUTE)
        assertEquals(1500, RateLimiter.MAX_REQUESTS_PER_DAY)
        assertEquals(60_000L, RateLimiter.WINDOW_MINUTE_MS)
        assertEquals(24 * 60 * 60 * 1000L, RateLimiter.WINDOW_DAY_MS)
        assertEquals(
            "1500 request per day limit is reached. To process further continue after 24 hours.",
            RateLimiter.DAILY_LIMIT_MESSAGE
        )
        val (gemHour, gemDate) = RateLimiter.getGeminiResetInfo()
        assertNotNull(gemHour)
        assertNotNull(gemDate)
        assertTrue(gemHour.contains(":"))
        val (groqHour, groqDate) = RateLimiter.getGroqResetInfo()
        assertNotNull(groqHour)
        assertNotNull(groqDate)
        assertTrue(groqHour.contains(":"))

        val gemEpoch = RateLimiter.getGeminiResetEpochMillis()
        val groqEpoch = RateLimiter.getGroqResetEpochMillis()
        assertTrue(gemEpoch > System.currentTimeMillis())
        assertTrue(groqEpoch > System.currentTimeMillis())
    }
}
