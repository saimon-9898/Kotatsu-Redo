package org.koitharu.kotatsu.pornhwadb

import org.junit.Assert.*
import org.junit.Test

class PornhwaDbConfigTest {

    @Test
    fun testApiBaseUrl() {
        assertEquals("https://pornhwadb.com/api/v1", PornhwaDbConfig.API_BASE_URL)
    }

    @Test
    fun testRateLimitConstant() {
        assertEquals(100, PornhwaDbConfig.RATE_LIMIT_PER_MINUTE)
    }
}
