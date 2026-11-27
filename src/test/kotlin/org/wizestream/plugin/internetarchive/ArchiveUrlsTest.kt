/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ParsingException

class ArchiveUrlsTest {
    @Test fun itemLinksNormalizeToPermanentHttpsUrls() {
        assertEquals("sample_item-1.0", ArchiveUrls.itemFromUrl("http://www.archive.org/details/sample_item-1.0/?utm_source=test"))
        assertEquals("https://archive.org/details/sample_item-1.0", InternetArchiveService.ITEM_LINKS.fromUrl("http://www.archive.org/download/sample_item-1.0").url)
    }

    @Test fun fileNamesPreserveSpacesUnicodePlusAndNestedPaths() {
        val target = ArchiveTrackTarget("sample", "Disc 1/Café + birds.mp3")
        assertEquals("https://archive.org/download/sample/Disc%201/Caf%C3%A9%20%2B%20birds.mp3", target.url)
        assertEquals(target, ArchiveUrls.trackFromUrl(target.url + "?download=1"))
        assertEquals(target, ArchiveUrls.trackFromId(target.id))
    }

    @Test fun literalPlusInPathIsNotDecodedAsSpace() {
        assertEquals("a+b.mp3", ArchiveUrls.trackFromUrl("https://archive.org/download/sample/a+b.mp3")?.filename)
    }

    @Test fun detailsArePlaylistsAndDownloadsAreStreams() {
        val service = InternetArchiveService(99)
        assertEquals(StreamingService.LinkType.PLAYLIST, service.getLinkTypeByUrl("https://archive.org/details/sample"))
        assertEquals(StreamingService.LinkType.STREAM, service.getLinkTypeByUrl("https://archive.org/download/sample/audio.mp3"))
        assertEquals(99, service.serviceId)
    }

    @Test fun rejectsForeignHostsUserInfoAndNonWebSchemes() {
        listOf(
            "https://archive.org.example.invalid/details/sample",
            "https://evil.invalid@archive.org/details/sample",
            "https://archive.org:444/details/sample",
            "file:///details/sample",
            "https://web.archive.org/web/20200101/https://example.invalid/video",
            "https://ia800000.us.archive.org/1/items/sample/audio.mp3"
        ).forEach { url ->
            assertNull(url, ArchiveUrls.itemFromUrl(url))
            assertNull(url, ArchiveUrls.trackFromUrl(url))
        }
    }

    @Test fun rejectsTraversalAndAmbiguousPathEncoding() {
        listOf("../audio.mp3", "%2e%2e/audio.mp3", "%2Fsecret.mp3", "a%5Cb.mp3", "a%00b.mp3", "a//b.mp3", "%ZZ.mp3").forEach { name ->
            assertNull(name, ArchiveUrls.trackFromUrl("https://archive.org/download/sample/$name"))
        }
    }

    @Test fun percentEncodedFilenameIsEncodedOnlyOncePerRoundTrip() {
        val target = ArchiveTrackTarget("sample", "100%25.mp3")
        assertEquals(target, ArchiveUrls.trackFromUrl(target.url))
    }

    @Test fun rejectsUnsupportedDetailsSubpagesAndMissingNames() {
        assertNull(ArchiveUrls.itemFromUrl("https://archive.org/details/sample/page/1"))
        assertNull(ArchiveUrls.trackFromUrl("https://archive.org/download/sample/"))
        assertNull(ArchiveUrls.itemFromUrl("https://archive.org/details/"))
        assertThrows(ParsingException::class.java) { ArchiveUrls.download("../sample", "audio.mp3") }
        assertThrows(ParsingException::class.java) { ArchiveUrls.trackFromId("sample/../audio.mp3") }
    }
}
