package org.koitharu.kotatsu.pornhwadb

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for Pornhwa DB API key and settings.
 * Loads from BuildConfig, environment variables, or local.properties via gradle.
 * Never logs or exposes the key in responses or exceptions.
 */
@Singleton
class PornhwaDbConfig @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings,
) {
    /**
     * Retrieves the API key from BuildConfig or returns empty string if not configured.
     * The key is injected at build time from local.properties or CI environment variables.
     */
    fun getApiKey(): String {
        return try {
            // BuildConfig.PORNHWA_API_KEY is set via build.gradle readSecret pattern
            // If not configured, it will be empty string
            val clazz = Class.forName("org.koitharu.kotatsu.BuildConfig")
            val field = clazz.getField("PORNHWA_API_KEY")
            (field.get(null) as? String) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun isConfigured(): Boolean = getApiKey().isNotEmpty()

    companion object {
        const val API_BASE_URL = "https://pornhwadb.com/api/v1"
        const val RATE_LIMIT_PER_MINUTE = 100
    }
}
