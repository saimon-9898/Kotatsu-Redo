package org.koitharu.kotatsu.pornhwadb

import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource as ParserMangaSource
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

/**
 * Factory for creating Pornhwa DB repository instances.
 * Integrates with Kotatsu's MangaRepository architecture.
 */
@Singleton
class PornhwaDbSourceFactory @Inject constructor(
    private val config: PornhwaDbConfig,
    private val apiClient: PornhwaDbApiClient,
    private val cache: MemoryContentCache,
    @ApplicationContext private val context: Context,
) {

    /**
     * Creates a repository for Pornhwa DB source.
     * Returns null if API key is not configured.
     */
    fun createRepository(): PornhwaDbRepository? {
        if (!config.isConfigured()) {
            return null
        }

        val source = getPornhwaDbSource()
        return PornhwaDbRepository(source, apiClient, cache)
    }

    private fun getPornhwaDbSource(): ParserMangaSource {
        // Create a synthetic MangaParserSource for Pornhwa DB
        // This wraps the API client in Kotatsu's source model
        return object : ParserMangaSource {
            override val name = PORNHWA_DB_SOURCE_NAME
            override val title = "Pornhwa DB"
            override val description = "Pornhwa Database - Adult content reader"
            override val contentType = ContentType.HENTAI
            override val isEnabled = config.isConfigured()
            override val locale = "en-US"
        }
    }

    companion object {
        const val PORNHWA_DB_SOURCE_NAME = "PORNHWA_DB"
    }
}
