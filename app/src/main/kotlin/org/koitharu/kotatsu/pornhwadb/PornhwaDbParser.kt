package org.koitharu.kotatsu.pornhwadb

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.longHashCode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private const val TAG = "PornhwaDbParser"

/**
 * Parser for converting Pornhwa DB API responses to Kotatsu model objects.
 */
object PornhwaDbParser {

    /**
     * Parses a series object from the API into a Manga.
     */
    fun parseManga(json: JSONObject, source: MangaSource): Manga {
        val id = json.optLong("id", 0L)
        val slug = json.optString("slug", "")
        val title = json.optString("title", "Unknown")
        val description = json.optString("description", "")
        val coverImage = json.optString("coverImage", "")
        val status = json.optString("status", "On Going")
        val authors = json.optJSONArray("authors")?.let { parseStringArray(it) } ?: emptyList()
        val artists = json.optJSONArray("artists")?.let { parseStringArray(it) } ?: emptyList()
        val genreTags = json.optJSONArray("genreTags")?.let { parseStringArray(it) } ?: emptyList()
        val rating = json.optDouble("averageRating", 0.0)
        val totalChapters = json.optInt("totalChapters", json.optInt("chapterCount", 0))

        // Use slug as URL identifier; fall back to numeric ID
        val url = if (slug.isNotEmpty()) slug else id.toString()

        return Manga(
            id = id.longHashCode(),
            title = title,
            altTitle = json.optString("alternativeTitles", null),
            url = url,
            publicUrl = "https://pornhwadb.com/series/$slug",
            rating = rating.toFloat().coerceIn(0f, 10f) * 10f,
            isNsfw = true,
            coverUrl = coverImage,
            largeCoverUrl = coverImage,
            description = description,
            tags = genreTags.toSet(),
            author = authors.joinToString(", "),
            artist = artists.joinToString(", "),
            state = when (status) {
                "Completed" -> org.koitharu.kotatsu.parsers.model.MangaState.FINISHED
                "Hiatus" -> org.koitharu.kotatsu.parsers.model.MangaState.SUSPENDED
                else -> org.koitharu.kotatsu.parsers.model.MangaState.ONGOING
            },
            source = source,
            chapters = null, // Populated separately if needed
        )
    }

    /**
     * Parses chapters from a series detail response.
     */
    fun parseChapters(json: JSONObject, source: MangaSource): List<MangaChapter> {
        val chapters = mutableListOf<MangaChapter>()
        val chaptersArray = json.optJSONArray("chapters") ?: return chapters

        for (i in 0 until chaptersArray.length()) {
            val chapterJson = chaptersArray.getJSONObject(i)
            chapters.add(parseChapter(chapterJson, source))
        }

        return chapters.sortedByDescending { it.number }
    }

    /**
     * Parses a single chapter object.
     */
    private fun parseChapter(json: JSONObject, source: MangaSource): MangaChapter {
        val id = json.optLong("id", 0L)
        val chapterStart = json.optInt("chapterStart", 0)
        val chapterEnd = json.optInt("chapterEnd", chapterStart)
        val description = json.optString("description", "")
        val chapterName = if (chapterEnd > 0 && chapterEnd != chapterStart) {
            "Chapter $chapterStart-$chapterEnd"
        } else {
            "Chapter $chapterStart"
        }

        return MangaChapter(
            id = id.longHashCode(),
            name = chapterName,
            number = chapterStart.toFloat(),
            url = id.toString(),
            source = source,
            uploadDate = 0L,
            branches = emptySet(),
            scanlator = null,
        )
    }

    /**
     * Parses pages for a chapter.
     * Note: The Pornhwa DB API provides chapter metadata but not direct page lists.
     * We create placeholder pages based on chapter structure.
     */
    fun parsePages(chapterId: Long, source: MangaSource): List<MangaPage> {
        // Pornhwa DB does not provide per-page image URLs in the public API.
        // This is a limitation of the API contract.
        // For now, return empty list and log the limitation.
        Log.w(TAG, "Pornhwa DB API does not provide direct page/image URLs for chapter $chapterId")
        return emptyList()
    }

    /**
     * Parses search results from API.
     */
    fun parseSearchResults(json: JSONObject, source: MangaSource): List<Manga> {
        val results = mutableListOf<Manga>()
        val dataArray = json.optJSONArray("data") ?: return results

        for (i in 0 until dataArray.length()) {
            try {
                val item = dataArray.getJSONObject(i)
                results.add(parseManga(item, source))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse search result item $i: ${e.message}")
            }
        }

        return results
    }

    private fun parseStringArray(array: JSONArray): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotEmpty()) result.add(value)
        }
        return result
    }
}
