/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

class ArchiveSearchTest {
    @Test fun resultsAreItemPlaylistsWithUnknownTrackCount() {
        val result = ArchiveSearch.parse(response(1, 1), "nature", 1, 99)
        val item = result.items.single() as PlaylistInfoItem
        assertEquals("https://archive.org/details/item-0", item.url)
        assertEquals("Item 0", item.name)
        assertEquals(-1L, item.streamCount)
        assertEquals(99, item.serviceId)
        assertFalse(result.hasNextPage())
        assertTrue(result.errors.isEmpty())
    }

    @Test fun fullPagesAdvanceWithinTheSameQuery() {
        val result = ArchiveSearch.parse(response(50, 51), "nature", 1, 99)
        assertEquals(2, ArchiveSearch.pageNumber("nature", result.nextPage))
        assertEquals(ArchiveSearch.pageUrl("nature", 2), result.nextPage.url)
        val final = ArchiveSearch.parse(response(1, 51, 50), "nature", 2, 99)
        assertFalse(final.hasNextPage())
    }

    @Test fun doesNotPageBeyondProviderWindow() {
        val result = ArchiveSearch.parse(response(50, 20_000, 9950), "nature", 200, 99)
        assertFalse(result.hasNextPage())
        assertThrows(ParsingException::class.java) { ArchiveSearch.pageUrl("nature", 201) }
    }

    @Test fun shortOrEmptyResponsesStopDespiteStaleTotal() {
        assertFalse(ArchiveSearch.parse(response(3, 1000), "nature", 1, 99).hasNextPage())
        assertFalse(ArchiveSearch.parse(response(0, 1000), "nature", 1, 99).hasNextPage())
    }

    @Test fun repeatedPagePositionIsRejected() {
        assertThrows(ParsingException::class.java) { ArchiveSearch.parse(response(50, 1000, 0), "nature", 2, 99) }
    }

    @Test fun rejectsChangedQueryHostOrMissingPosition() {
        listOf(
            Page(ArchiveSearch.pageUrl("other", 2), "2"),
            Page(ArchiveSearch.pageUrl("nature", 2).replace("archive.org", "evil.invalid"), "2"),
            Page(ArchiveSearch.pageUrl("nature", 1), "1"),
            Page(ArchiveSearch.pageUrl("nature", 2)),
            Page(ArchiveSearch.pageUrl("nature", 2), "3")
        ).forEach { page ->
            assertThrows(ParsingException::class.java) { ArchiveSearch.pageNumber("nature", page) }
        }
    }

    @Test fun searchTextCannotEscapeThePublicMediaScope() {
        val decoded = java.net.URLDecoder.decode(ArchiveSearch.pageUrl("birds OR mediatype:texts", 1), "UTF-8")
        assertTrue(decoded.contains("(\"birds\" AND \"OR\" AND \"mediatype:texts\") AND mediatype:(audio OR movies OR etree)"))
        assertTrue(decoded.contains("AND NOT access-restricted-item:true"))
        assertTrue(decoded.contains("sort[]=downloads desc"))
        assertTrue(decoded.contains("sort[]=identifier asc"))
    }

    @Test fun unsupportedRestrictedAndInvalidEntriesAreSkipped() {
        val data = response(5, 5)
        val docs = (data["response"] as JsonObject)["docs"] as JsonArray
        (docs[1] as JsonObject)["access-restricted-item"] = true
        (docs[2] as JsonObject)["mediatype"] = "texts"
        (docs[3] as JsonObject)["identifier"] = "../invalid"
        (docs[4] as JsonObject)["identifier"] = "item-0"
        val result = ArchiveSearch.parse(data, "nature", 1, 99)
        assertEquals(1, result.items.size)
    }

    @Test fun malformedSearchResponsesFailClearly() {
        listOf(
            "{}",
            """{"response":{"docs":[],"start":0}}""",
            """{"response":{"docs":"wrong","start":0,"numFound":0}}""",
            """{"responseHeader":{"status":400},"response":{"docs":[],"start":0,"numFound":0}}"""
        ).forEach { input ->
            assertThrows(ParsingException::class.java) { ArchiveSearch.parse(json(input), "nature", 1, 99) }
        }
    }

    @Test fun rejectsUnboundedQueryOrPageValues() {
        assertThrows(ParsingException::class.java) { ArchiveSearch.pageUrl("x".repeat(1025), 1) }
        assertThrows(ParsingException::class.java) { ArchiveSearch.pageUrl("nature", 0) }
    }

    companion object {
        internal fun response(count: Int, total: Int, start: Int = 0): JsonObject = JsonObject().apply {
            this["responseHeader"] = JsonObject().apply { this["status"] = 0 }
            this["response"] = JsonObject().apply {
                this["numFound"] = total
                this["start"] = start
                this["docs"] = JsonArray().apply {
                    repeat(count) { index ->
                        add(JsonObject().apply {
                            this["identifier"] = "item-${start + index}"
                            this["title"] = "Item ${start + index}"
                            this["creator"] = "Public Library"
                            this["mediatype"] = "audio"
                        })
                    }
                }
            }
        }
    }
}
