package org.koitharu.kotatsu.pornhwadb

import android.util.Log
import org.json.JSONObject
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.exception.NotFoundException
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable

private const val TAG = "PornhwaDbRepository"

/**
 * Manga repository for Pornhwa DB source.
 * Implements the Kotatsu MangaRepository interface using Pornhwa DB API.
 */
class PornhwaDbRepository(
    private val source: MangaSource,
    private val apiClient: PornhwaDbApiClient,
    cache: MemoryContentCache,
) : CachingMangaRepository(cache) {

    override val sortOrders: Set<SortOrder> = setOf(
        SortOrder.NEWEST,
        SortOrder.POPULAR,
        SortOrder.ALPHABETICAL,
    )

    override var defaultSortOrder: SortOrder = SortOrder.NEWEST

    override val filterCapabilities: MangaListFilterCapabilities
        get() = MangaListFilterCapabilities(
            isSearchSupported = true,
            isMultipleTagsSupported = true,
            isTagsExclusionSupported = false,
        )

    override suspend fun getList(
        offset: Int,
        order: SortOrder?,
        filter: MangaListFilter?,
    ): List<Manga> = runCatchingCancellable {
        val page = (offset / 20) + 1 // Default 20 items per page
        val params = mutableMapOf(
            "page" to page.toString(),
            "limit" to "20",
        )

        // Add sort order
        when (order ?: defaultSortOrder) {
            SortOrder.NEWEST -> params["order[updatedAt]"] = "desc"
            SortOrder.POPULAR -> params["order[averageRating]"] = "desc"
            SortOrder.ALPHABETICAL -> params["order[title]"] = "asc"
            else -> {}
        }

        // Add search filter
        filter?.query?.takeIf { it.isNotEmpty() }?.let {
            params["search"] = it
        }

        val response = apiClient.get("/pornhwa", params)
        PornhwaDbParser.parseSearchResults(response, source)
    }.getOrElse { error ->
        Log.e(TAG, "Failed to get list: ${error.message}")
        throw error
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga = runCatchingCancellable {
        val identifier = manga.url // Can be slug or numeric ID
        val params = mapOf(
            "includes[]" to "chapters",
            "limit" to "100", // Max chapters per request
        )

        val response = apiClient.get("/pornhwa/$identifier", params)
        val detail = PornhwaDbParser.parseManga(response, source)

        // Add chapters if available
        val chapters = PornhwaDbParser.parseChapters(response, source)
        if (chapters.isNotEmpty()) {
            detail.copy(chapters = chapters)
        } else {
            detail
        }
    }.getOrElse { error ->
        Log.e(TAG, "Failed to get details for ${manga.url}: ${error.message}")
        throw error
    }

    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = runCatchingCancellable {
        // API limitation: Pornhwa DB does not provide per-page image URLs
        Log.w(TAG, "Pornhwa DB API does not provide page-level details for reading")
        emptyList<MangaPage>()
    }.getOrElse { error ->
        Log.e(TAG, "Failed to get pages: ${error.message}")
        emptyList()
    }

    override suspend fun getPageUrl(page: MangaPage): String {
        // Not applicable for this source due to API limitations
        throw UnsupportedOperationException("Pornhwa DB API does not support direct page URLs")
    }

    override suspend fun getFilterOptions(): MangaListFilterOptions {
        return MangaListFilterOptions()
    }

    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = runCatchingCancellable {
        val identifier = seed.url
        val params = mapOf("limit" to "10")
        val response = apiClient.get("/pornhwa/$identifier/similar", params)
        PornhwaDbParser.parseSearchResults(response, source)
    }.getOrElse { error ->
        Log.e(TAG, "Failed to get related manga: ${error.message}")
        emptyList()
    }
}
