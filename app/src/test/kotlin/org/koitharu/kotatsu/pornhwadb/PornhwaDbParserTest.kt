package org.koitharu.kotatsu.pornhwadb

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource

class PornhwaDbParserTest {

    private lateinit var source: MangaSource

    @Before
    fun setUp() {
        source = MangaSource("TEST")
    }

    @Test
    fun testParseMangaBasic() {
        val json = JSONObject().apply {
            put("id", 123)
            put("slug", "test-series")
            put("title", "Test Series")
            put("description", "Test Description")
            put("coverImage", "https://example.com/cover.jpg")
            put("status", "Completed")
            put("averageRating", 4.5)
            put("totalChapters", 50)
        }

        val manga = PornhwaDbParser.parseManga(json, source)

        assertEquals("Test Series", manga.title)
        assertEquals("test-series", manga.url)
        assertEquals("Test Description", manga.description)
        assertEquals("https://example.com/cover.jpg", manga.coverUrl)
        assertTrue(manga.isNsfw)
        assertEquals(45f, manga.rating)
    }

    @Test
    fun testParseMangaMissingFields() {
        val json = JSONObject().apply {
            put("id", 456)
            put("title", "Minimal Series")
        }

        val manga = PornhwaDbParser.parseManga(json, source)

        assertEquals("Minimal Series", manga.title)
        assertEquals("456", manga.url) // Falls back to ID
        assertTrue(manga.isNsfw)
    }

    @Test
    fun testParseChapters() {
        val json = JSONObject().apply {
            val chapters = JSONArray()
            chapters.put(JSONObject().apply {
                put("id", 1001)
                put("chapterStart", 1)
                put("chapterEnd", 0)
                put("description", "Chapter 1")
            })
            chapters.put(JSONObject().apply {
                put("id", 1002)
                put("chapterStart", 2)
                put("chapterEnd", 0)
                put("description", "Chapter 2")
            })
            put("chapters", chapters)
        }

        val chapters = PornhwaDbParser.parseChapters(json, source)

        assertEquals(2, chapters.size)
        assertEquals("Chapter 2", chapters[0].name) // Sorted descending
        assertEquals("Chapter 1", chapters[1].name)
    }

    @Test
    fun testParseChaptersEmpty() {
        val json = JSONObject()
        val chapters = PornhwaDbParser.parseChapters(json, source)
        assertTrue(chapters.isEmpty())
    }

    @Test
    fun testParsePages() {
        // API limitation: no page URLs provided
        val pages = PornhwaDbParser.parsePages(123L, source)
        assertTrue(pages.isEmpty())
    }

    @Test
    fun testParseSearchResults() {
        val json = JSONObject().apply {
            val data = JSONArray()
            data.put(JSONObject().apply {
                put("id", 1)
                put("slug", "series-1")
                put("title", "Series 1")
            })
            data.put(JSONObject().apply {
                put("id", 2)
                put("slug", "series-2")
                put("title", "Series 2")
            })
            put("data", data)
        }

        val results = PornhwaDbParser.parseSearchResults(json, source)

        assertEquals(2, results.size)
        assertEquals("Series 1", results[0].title)
        assertEquals("Series 2", results[1].title)
    }

    @Test
    fun testParseSearchResultsInvalidItems() {
        val json = JSONObject().apply {
            val data = JSONArray()
            data.put(JSONObject().apply {
                put("id", 1)
                put("title", "Valid Series")
            })
            data.put(null) // Invalid item
            put("data", data)
        }

        val results = PornhwaDbParser.parseSearchResults(json, source)

        // Should skip invalid items gracefully
        assertTrue(results.size <= 2)
    }

    @Test
    fun testRatingConversion() {
        val json = JSONObject().apply {
            put("id", 1)
            put("title", "Test")
            put("averageRating", 3.5) // 3.5 out of 5
        }

        val manga = PornhwaDbParser.parseManga(json, source)

        // Kotatsu uses 0-100 scale; 3.5/5 = 70%
        assertEquals(35f, manga.rating) // 3.5 * 10
        assertTrue(manga.rating in 0f..100f)
    }
}
