/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamType

class ArchiveExtractorsTest {
    private val service = InternetArchiveService(99)
    private lateinit var downloader: FixtureDownloader

    @Before fun installOfflineDownloader() {
        useResponses(
            ArchiveUrls.metadataUrl("neutral-audio-fixture") to FixtureResponse(fixture("audio-item")),
            ArchiveUrls.metadataUrl("big-buck-bunny-trailer") to FixtureResponse(fixture("video-item"))
        )
    }

    @Test fun playlistUsesHostDownloaderAndExposesEveryDistinctTrack() {
        val extractor = service.getPlaylistExtractor("https://archive.org/details/neutral-audio-fixture")
        extractor.fetchPage()
        val result = extractor.initialPage
        assertEquals("A public nature recording", extractor.name)
        assertEquals(2L, extractor.streamCount)
        assertEquals(listOf("Forest", "River"), result.items.map { it.name })
        assertEquals(2, result.items.map { it.url }.toSet().size)
        assertEquals("https://archive.org/download/neutral-audio-fixture/01%20Forest.flac", result.items.first().url)
        assertFalse(result.hasNextPage())
        assertTrue(result.errors.isEmpty())
        assertEquals(listOf(ArchiveUrls.metadataUrl("neutral-audio-fixture")), downloader.requests)
    }

    @Test fun streamResolvesAlternateUrlWithoutMixingSeparateRecordings() {
        val extractor = service.getStreamExtractor("https://archive.org/download/neutral-audio-fixture/01%20Forest.ogg")
        extractor.fetchPage()
        assertEquals("neutral-audio-fixture/01 Forest.flac", extractor.id)
        assertEquals(StreamType.AUDIO_STREAM, extractor.streamType)
        assertEquals(61L, extractor.length)
        assertEquals(2, extractor.audioStreams.size)
        assertTrue(extractor.audioStreams.all { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP })
        assertTrue(extractor.audioStreams.all { it.content.contains("/01%20Forest.") })
        assertTrue(extractor.videoStreams.isEmpty())
        assertEquals(2, extractor.partitions.items.size)
        assertEquals("River", requireNotNull(extractor.relatedItems).items.single().name)
        assertEquals("", extractor.uploaderUrl)
        assertFalse(extractor.isSupportComments)
        assertFalse(extractor.isSupportBulletComments)
    }

    @Test fun videoReturnsProgressiveFormatsWithoutInventingLiveOrAdaptiveStreams() {
        val extractor = service.getStreamExtractor("https://archive.org/download/big-buck-bunny-trailer/Big-buck-bunny_trailer.mp4")
        extractor.fetchPage()
        assertEquals(StreamType.VIDEO_STREAM, extractor.streamType)
        assertEquals(2, extractor.videoStreams.size)
        assertTrue(extractor.videoStreams.all { it.resolution == "360p" && !it.isVideoOnly })
        assertTrue(extractor.videoStreams.all { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP })
        assertTrue(extractor.videoOnlyStreams.isEmpty())
        assertEquals("", extractor.hlsUrl)
        assertEquals("", extractor.dashMpdUrl)
        assertTrue(extractor.partitions.items.isEmpty())
    }

    @Test fun unlistedDirectFileCannotBypassMetadataAvailability() {
        val extractor = service.getStreamExtractor("https://archive.org/download/neutral-audio-fixture/not-listed.mp3")
        assertThrows(ContentNotAvailableException::class.java) { extractor.fetchPage() }
        assertEquals(1, downloader.requests.size)
    }

    @Test fun playlistPagesRemainBoundToOneItemAndReuseMetadata() {
        val data = fixtureJson("audio-item")
        data["files"] = JsonArray().apply {
            repeat(101) { index ->
                add(json("""{"name":"track-$index.mp3","format":"VBR MP3","track":"${index + 1}"}"""))
            }
        }
        useResponses(ArchiveUrls.metadataUrl("neutral-audio-fixture") to FixtureResponse(JsonWriter.string(data)))
        val extractor = service.getPlaylistExtractor("https://archive.org/details/neutral-audio-fixture")
        extractor.fetchPage()
        val first = extractor.initialPage
        assertEquals(100, first.items.size)
        val next = extractor.getPage(first.nextPage)
        assertEquals(1, next.items.size)
        assertFalse(next.hasNextPage())
        assertEquals(101, (first.items + next.items).map { it.url }.toSet().size)
        assertEquals(1, downloader.requests.size)
        assertThrows(ParsingException::class.java) { extractor.getPage(Page("https://archive.org/details/other", "100")) }
        assertThrows(ParsingException::class.java) { extractor.getPage(Page(extractor.url, "0")) }
    }

    @Test fun searchDownloadsOnlyRequestedApiPages() {
        useResponses(
            ArchiveSearch.pageUrl("nature", 1) to FixtureResponse(JsonWriter.string(ArchiveSearchTest.response(50, 51))),
            ArchiveSearch.pageUrl("nature", 2) to FixtureResponse(JsonWriter.string(ArchiveSearchTest.response(1, 51, 50)))
        )
        val extractor = service.getSearchExtractor("nature", emptyList(), emptyList())
        extractor.fetchPage()
        val first = extractor.initialPage
        assertEquals(50, first.items.size)
        val second = extractor.getPage(first.nextPage)
        assertEquals(1, second.items.size)
        assertFalse(second.hasNextPage())
        assertEquals(2, downloader.requests.size)
    }

    @Test fun clearHttpFailuresAndUnavailableMetadataAreReported() {
        listOf(401, 403, 404, 410).forEach { status ->
            useResponses(ArchiveUrls.metadataUrl("sample") to FixtureResponse("", status))
            assertThrows(ContentNotAvailableException::class.java) { ArchiveApi.item("sample", downloader) }
        }
        listOf(429, 503).forEach { status ->
            useResponses(ArchiveUrls.metadataUrl("sample") to FixtureResponse("", status))
            assertThrows(ExtractionException::class.java) { ArchiveApi.item("sample", downloader) }
        }
        useResponses(ArchiveUrls.metadataUrl("sample") to FixtureResponse("[]"))
        assertThrows(ContentNotAvailableException::class.java) { ArchiveApi.item("sample", downloader) }
        useResponses(ArchiveUrls.metadataUrl("sample") to FixtureResponse("""{"error":"removed","errcode":104}"""))
        assertTrue(assertThrows(ContentNotAvailableException::class.java) { ArchiveApi.item("sample", downloader) }.message.orEmpty().contains("deleted"))
    }

    @Test fun malformedHtmlOrRedirectToLoginCannotBecomePlayableMedia() {
        val url = ArchiveUrls.metadataUrl("neutral-audio-fixture")
        useResponses(url to FixtureResponse("<html>Sign in</html>"))
        assertThrows(ParsingException::class.java) { ArchiveApi.item("neutral-audio-fixture", downloader) }
        listOf("https://archive.org/account/login", "https://example.invalid/metadata/neutral-audio-fixture", "http://archive.org/metadata/neutral-audio-fixture").forEach { redirected ->
            useResponses(url to FixtureResponse(fixture("audio-item"), latestUrl = redirected))
            assertThrows(ParsingException::class.java) { ArchiveApi.item("neutral-audio-fixture", downloader) }
        }
    }

    private fun useResponses(vararg responses: Pair<String, FixtureResponse>) {
        downloader = FixtureDownloader(mapOf(*responses))
        NewPipe.init(downloader)
    }
}
