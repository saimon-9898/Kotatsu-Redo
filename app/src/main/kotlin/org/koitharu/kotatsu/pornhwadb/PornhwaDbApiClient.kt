package org.koitharu.kotatsu.pornhwadb

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PornhwaDbApiClient"

/**
 * HTTP client for Pornhwa DB API.
 * Handles authentication, rate limiting, error handling, and retries.
 * Never logs or embeds the API key.
 */
@Singleton
class PornhwaDbApiClient @Inject constructor(
    private val config: PornhwaDbConfig,
    private val httpClient: OkHttpClient,
) {
    private var rateLimitRemaining = config.RATE_LIMIT_PER_MINUTE
    private var rateLimitResetTime = 0L

    /**
     * Performs a GET request to the Pornhwa DB API.
     * @param path API endpoint path (e.g., "/pornhwa/example-slug")
     * @param params Query parameters as map
     * @return Response as JSONObject or JSONArray
     * @throws IOException on network errors
     * @throws NotFoundException if resource not found
     */
    suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        if (!config.isConfigured()) {
            throw IllegalStateException("Pornhwa DB API key not configured")
        }

        val url = buildUrl(path, params)
        var attempt = 0
        val maxRetries = 3

        while (attempt < maxRetries) {
            attempt++
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("X-API-Key", config.getApiKey())
                    .addHeader("User-Agent", "Kotatsu/Android")
                    .build()

                val response = httpClient.newCall(request).execute()
                handleRateLimitHeaders(response)

                when (response.code) {
                    200 -> {
                        val body = response.body?.string() ?: return JSONObject()
                        return try {
                            JSONObject(body)
                        } catch (e: JSONException) {
                            // Try parsing as array
                            JSONObject().put("data", JSONArray(body))
                        }
                    }
                    304 -> {
                        // Not Modified - cached
                        Log.d(TAG, "Resource not modified (cached)")
                        return JSONObject()
                    }
                    401 -> {
                        Log.e(TAG, "Unauthorized - invalid or missing API key")
                        throw IOException("Unauthorized: invalid API key")
                    }
                    403 -> {
                        Log.e(TAG, "Forbidden - account suspended or insufficient permissions")
                        throw IOException("Forbidden: ${response.message}")
                    }
                    404 -> {
                        throw NotFoundException("Resource not found: $path")
                    }
                    429 -> {
                        val retryAfter = response.header("Retry-After")?.toIntOrNull() ?: 60
                        Log.w(TAG, "Rate limited. Retry after $retryAfter seconds")
                        if (attempt < maxRetries) {
                            delay((retryAfter * 1000).toLong())
                            continue
                        }
                        throw IOException("Rate limit exceeded")
                    }
                    500, 502, 503 -> {
                        if (attempt < maxRetries) {
                            val backoff = (1000L * attempt * attempt) // exponential backoff
                            Log.w(TAG, "Server error (${response.code}). Retry in ${backoff}ms")
                            delay(backoff)
                            continue
                        }
                        throw IOException("Server error: ${response.code}")
                    }
                    else -> {
                        throw IOException("Unexpected response: ${response.code} ${response.message}")
                    }
                }
            } catch (e: IOException) {
                if (attempt == maxRetries) throw
                Log.w(TAG, "Network error on attempt $attempt/$maxRetries: ${e.message}")
                delay((1000L * attempt).coerceAtMost(5000L))
            }
        }

        throw IOException("Max retries exceeded")
    }

    private fun buildUrl(path: String, params: Map<String, String>): String {
        val baseUrl = if (path.startsWith("/")) {
            PornhwaDbConfig.API_BASE_URL + path
        } else {
            PornhwaDbConfig.API_BASE_URL + "/" + path
        }

        return if (params.isEmpty()) {
            baseUrl
        } else {
            val queryString = params.entries.joinToString("&") { (k, v) ->
                "$k=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
            "$baseUrl?$queryString"
        }
    }

    private fun handleRateLimitHeaders(response: Response) {
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull()
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
        val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()

        if (remaining != null) {
            rateLimitRemaining = remaining
            if (remaining < 10) {
                Log.w(TAG, "Approaching rate limit: $remaining requests remaining")
            }
        }

        if (reset != null) {
            rateLimitResetTime = reset
        }
    }
}
