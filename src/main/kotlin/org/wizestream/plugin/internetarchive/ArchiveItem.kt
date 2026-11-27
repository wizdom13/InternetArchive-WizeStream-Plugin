/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jsoup.Jsoup
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.localization.DateWrapper

internal data class ArchiveItem(
    val identifier: String,
    val title: String,
    val creator: String,
    val description: String,
    val date: String?,
    val license: String,
    val tracks: List<ArchiveTrack>
) {
    val url: String get() = ArchiveUrls.itemUrl(identifier)
    val thumbnail: String get() = ArchiveUrls.thumbnailUrl(identifier)

    fun track(filename: String): ArchiveTrack = tracks.firstOrNull {
        it.filename == filename || it.files.any { file -> file.name == filename }
    } ?: throw ContentNotAvailableException("This file is unavailable, restricted, or has no supported audio/video format")

    companion object {
        val MEDIA_TYPES = setOf("audio", "movies", "etree")
        private val restrictionFields = setOf(
            "is_dark", "is_restricted", "is_lending_required", "access-restricted-item", "access-restricted",
            "restricted", "requires_login", "private"
        )

        fun restricted(json: JsonObject): Boolean = restrictionFields.any { ArchiveText.flag(json[it]) }

        fun parse(identifier: String, json: JsonObject): ArchiveItem {
            val metadata = json["metadata"] as? JsonObject
                ?: throw ContentNotAvailableException("Internet Archive returned no item metadata")
            if (restricted(json) || restricted(metadata)) {
                throw ContentNotAvailableException("This Internet Archive item is restricted or requires sign-in or borrowing")
            }
            if (ArchiveText.value(metadata["identifier"]) != identifier) {
                throw ParsingException("Internet Archive returned metadata for a different item")
            }
            val mediaType = ArchiveText.value(metadata["mediatype"])
            if (mediaType !in MEDIA_TYPES) {
                throw ContentNotSupportedException("Only Internet Archive audio, video, and recorded concert items are supported; collections and books are not")
            }
            val files = json["files"] as? List<*>
                ?: throw ContentNotAvailableException("This Internet Archive item has no available files")
            val title = ArchiveText.plain(metadata["title"]).ifBlank { identifier }
            val tracks = ArchiveFiles.tracks(files, title, mediaType)
            if (tracks.isEmpty()) {
                val hasRestrictedFiles = files.filterIsInstance<JsonObject>().any(::restricted)
                if (hasRestrictedFiles) {
                    throw ContentNotAvailableException("This item's audio/video files are restricted or unavailable")
                }
                throw ContentNotSupportedException("This item has no supported public MP4, WebM, MP3, M4A, Ogg Vorbis, or Opus files")
            }
            return ArchiveItem(
                identifier,
                title,
                // Uploader metadata often contains an email address, not a public creator name.
                ArchiveText.plain(metadata["creator"]).ifBlank { ArchiveText.plain(metadata["publisher"]) },
                ArchiveText.plain(metadata["description"]),
                ArchiveText.value(metadata["publicdate"]).ifBlank { ArchiveText.value(metadata["addeddate"]) }.ifBlank { null },
                ArchiveText.value(metadata["licenseurl"]),
                tracks
            )
        }
    }
}

internal object ArchiveText {
    fun value(value: Any?): String = when (value) {
        is String -> value.trim()
        is Number -> value.toString()
        is List<*> -> value.map(::value).filter(String::isNotBlank).joinToString("; ")
        else -> ""
    }

    fun plain(value: Any?): String = Jsoup.parse(value(value)).text()

    fun number(value: Any?, fallback: Long = -1): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull() ?: fallback
        else -> fallback
    }

    fun flag(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.trim().lowercase() in setOf("true", "1", "yes")
        is List<*> -> value.any(::flag)
        else -> false
    }

    fun duration(value: Any?): Long {
        val text = value(value)
        val seconds = text.toDoubleOrNull() ?: run {
            val parts = text.split(':')
            if (parts.size !in 2..3) return -1
            val values = parts.map { it.toDoubleOrNull() ?: return -1 }
            if (values.any { !it.isFinite() || it < 0 } || values.drop(1).any { it >= 60 }) return -1
            values.fold(0.0) { result, number -> result * 60 + number }
        }
        return if (seconds.isFinite() && seconds >= 0 && seconds < Long.MAX_VALUE.toDouble()) seconds.toLong() else -1
    }

    fun date(text: String?): DateWrapper? {
        if (text.isNullOrBlank()) return null
        return try {
            DateWrapper(OffsetDateTime.parse(text))
        } catch (_: Exception) {
            try {
                DateWrapper(LocalDateTime.parse(text.replace(' ', 'T')).atOffset(ZoneOffset.UTC))
            } catch (_: Exception) {
                try {
                    DateWrapper(LocalDate.parse(text).atStartOfDay().atOffset(ZoneOffset.UTC), true)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
