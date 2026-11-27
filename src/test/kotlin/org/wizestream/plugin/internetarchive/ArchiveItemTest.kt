/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.StreamType

class ArchiveItemTest {
    @Test fun audioDerivativesGroupIntoDistinctOrderedTracks() {
        val item = audio()
        assertEquals(listOf("Forest", "River"), item.tracks.map { it.title })
        assertEquals(listOf("01 Forest.flac", "02 River.flac"), item.tracks.map { it.filename })
        assertEquals(listOf(MediaFormat.MP3, MediaFormat.OGG), item.tracks.first().files.map { it.mediaFormat })
        assertTrue(item.tracks.all { it.type == StreamType.AUDIO_STREAM })
        assertEquals(61L, item.tracks.first().duration)
        assertEquals(123L, item.tracks.last().duration)
    }

    @Test fun originalAndAlternativeLinksSelectTheSameTrack() {
        val item = audio()
        assertSame(item.track("01 Forest.flac"), item.track("01 Forest.ogg"))
        assertNotEquals(item.track("01 Forest.mp3").filename, item.track("02 River.mp3").filename)
    }

    @Test fun realTrimmedVideoMetadataExposesBothProgressiveEncodings() {
        val item = ArchiveItem.parse("big-buck-bunny-trailer", fixtureJson("video-item"))
        assertEquals(1, item.tracks.size)
        val track = item.tracks.single()
        assertEquals("Big-buck-bunny_trailer.webm", track.filename)
        assertEquals(StreamType.VIDEO_STREAM, track.type)
        assertEquals(setOf(MediaFormat.MPEG_4, MediaFormat.WEBM), track.files.map { it.mediaFormat }.toSet())
        assertTrue(track.files.all { it.height == 360 && it.width == 640 })
        assertEquals(32L, track.duration)
    }

    @Test fun optionalMetadataListsAndHtmlAreHandledWithoutExposingUploaderEmail() {
        val item = audio()
        assertEquals("Field Recordist; Public Sound Library", item.creator)
        assertEquals("Two recorded walks & their surroundings.", item.description)
        assertFalse(item.creator.contains("@"))
        assertEquals(2026, ArchiveText.date(item.date)?.offsetDateTime()?.year)
        assertEquals("https://archive.org/services/img/neutral-audio-fixture", item.thumbnail)
    }

    @Test fun optionalMetadataCanBeAbsent() {
        val data = fixtureJson("audio-item")
        (data["metadata"] as JsonObject).keys.removeAll(setOf("title", "creator", "description", "publicdate", "licenseurl"))
        val item = ArchiveItem.parse("neutral-audio-fixture", data)
        assertEquals("neutral-audio-fixture", item.title)
        assertEquals("", item.creator)
        assertNull(item.date)
    }

    @Test fun rejectsMismatchedItemIdentity() {
        assertThrows(ParsingException::class.java) { ArchiveItem.parse("other-item", fixtureJson("audio-item")) }
    }

    @Test fun rejectsExplicitRestrictionFlagsAtEitherLevel() {
        listOf("is_dark", "is_restricted", "is_lending_required", "access-restricted-item", "requires_login").forEach { flag ->
            val data = fixtureJson("audio-item")
            data[flag] = true
            assertThrows(flag, ContentNotAvailableException::class.java) { ArchiveItem.parse("neutral-audio-fixture", data) }
            data.remove(flag)
            (data["metadata"] as JsonObject)[flag] = "true"
            assertThrows(flag, ContentNotAvailableException::class.java) { ArchiveItem.parse("neutral-audio-fixture", data) }
        }
    }

    @Test fun falseRestrictionFlagsDoNotHidePublicMedia() {
        val data = fixtureJson("audio-item")
        data["is_dark"] = false
        (data["metadata"] as JsonObject)["access-restricted-item"] = "false"
        assertEquals(2, ArchiveItem.parse("neutral-audio-fixture", data).tracks.size)
    }

    @Test fun collectionsBooksAndUnsupportedOnlyItemsHaveClearErrors() {
        listOf("collection", "texts", "web").forEach { type ->
            val data = fixtureJson("audio-item")
            (data["metadata"] as JsonObject)["mediatype"] = type
            assertThrows(ContentNotSupportedException::class.java) { ArchiveItem.parse("neutral-audio-fixture", data) }
        }
        val data = fixtureJson("audio-item")
        data["files"] = JsonArray().apply { add(json("""{"name":"only.flac","format":"Flac"}""")) }
        assertThrows(ContentNotSupportedException::class.java) { ArchiveItem.parse("neutral-audio-fixture", data) }
    }

    @Test fun privateMissingAndUnsafeFilesAreNeverReturned() {
        val item = audio()
        listOf("private.mp3", "../escape.mp3", "not-listed.mp3", "01 Forest.png").forEach { name ->
            assertThrows(ContentNotAvailableException::class.java) { item.track(name) }
        }
    }

    @Test fun duplicateNamesAndOriginalCyclesFailClearly() {
        val duplicate = fixtureJson("audio-item")
        (duplicate["files"] as JsonArray).add(json("""{"name":"01 Forest.mp3","format":"VBR MP3"}"""))
        assertThrows(ParsingException::class.java) { ArchiveItem.parse("neutral-audio-fixture", duplicate) }
        val cycle = listOf(
            json("""{"name":"one.mp3","format":"MP3","original":"two.mp3"}"""),
            json("""{"name":"two.mp3","format":"MP3","original":"one.mp3"}""")
        )
        assertThrows(ParsingException::class.java) { ArchiveFiles.tracks(cycle, "Cycle", "audio") }
    }

    @Test fun similarFileNamesAreNotGuessedToBeAlternateEncodings() {
        val tracks = ArchiveFiles.tracks(listOf(
            json("""{"name":"intro.mp3","format":"MP3"}"""),
            json("""{"name":"intro.ogg","format":"Ogg Vorbis"}""")
        ), "Untitled", "audio")
        assertEquals(2, tracks.size)
    }

    @Test fun missingOriginalKeepsARealDownloadIdentity() {
        val tracks = ArchiveFiles.tracks(listOf(json("""{"name":"playable.mp3","format":"MP3","original":"missing.wav"}""")), "Title", "audio")
        assertEquals("playable.mp3", tracks.single().filename)
    }

    @Test fun doesNotMisclassifyTheoraOrSidecarsAsAudio() {
        val tracks = ArchiveFiles.tracks(listOf(
            json("""{"name":"video.ogg","format":"Ogg Video"}"""),
            json("""{"name":"bad.mp3","format":"Metadata"}"""),
            json("""{"name":"empty.mp3","format":"MP3","size":"0"}"""),
            json("""{"name":"valid.opus","format":"Ogg Opus"}""")
        ), "Formats", "audio")
        assertEquals(listOf("valid.opus"), tracks.map { it.filename })
    }

    @Test fun transitiveDerivativesKeepOriginalIdentity() {
        val tracks = ArchiveFiles.tracks(listOf(
            json("""{"name":"master.wav","format":"WAVE"}"""),
            json("""{"name":"derived.mp3","format":"MP3","original":"master.wav"}"""),
            json("""{"name":"alternate.ogg","format":"Ogg Vorbis","original":"derived.mp3"}""")
        ), "Formats", "audio")
        assertEquals(1, tracks.size)
        assertEquals("master.wav", tracks.single().filename)
        assertEquals(2, tracks.single().files.size)
    }

    @Test fun durationAndDatesRejectInvalidValues() {
        assertEquals(3723L, ArchiveText.duration("1:02:03.4"))
        assertEquals(42L, ArchiveText.duration(42.5))
        listOf("NaN", "Infinity", "-1", "1:99", "unknown").forEach { assertEquals(-1L, ArchiveText.duration(it)) }
        assertNull(ArchiveText.date("not-a-date"))
        assertTrue(requireNotNull(ArchiveText.date("2026-01-01")).isApproximation)
    }

    private fun audio(): ArchiveItem = ArchiveItem.parse("neutral-audio-fixture", fixtureJson("audio-item"))
}
