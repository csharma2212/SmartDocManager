package com.smartdoc.poc

import org.junit.Assert.*
import org.junit.Test

class RateLimiterTest {
    @Test
    fun rateLimiterConstants() {
        assertEquals(10, RateLimiter.MAX_REQUESTS_PER_MINUTE)
        assertEquals(1000, RateLimiter.MAX_REQUESTS_PER_DAY)
        assertEquals(60_000L, RateLimiter.WINDOW_MINUTE_MS)
        assertEquals(24 * 60 * 60 * 1000L, RateLimiter.WINDOW_DAY_MS)
        assertEquals(
            "1000 request per day limit is reached. To process further continue after 24 hours.",
            RateLimiter.DAILY_LIMIT_MESSAGE
        )
    }
}
