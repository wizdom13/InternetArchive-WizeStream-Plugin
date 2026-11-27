/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonObject
import java.util.Locale
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.stream.StreamType

internal data class ArchiveMediaFile(
    val name: String,
    val mediaFormat: MediaFormat,
    val video: Boolean,
    val duration: Long,
    val width: Int,
    val height: Int,
    val bitrate: Int
)

internal data class ArchiveTrack(
    val filename: String,
    val title: String,
    val duration: Long,
    val files: List<ArchiveMediaFile>
) {
    val type: StreamType get() = if (files.any { it.video }) StreamType.VIDEO_STREAM else StreamType.AUDIO_STREAM
}

internal object ArchiveFiles {
    fun tracks(records: List<*>, itemTitle: String, mediaType: String): List<ArchiveTrack> {
        val named = linkedMapOf<String, JsonObject>()
        records.filterIsInstance<JsonObject>().forEach { file ->
            val name = ArchiveText.value(file["name"])
            if (!ArchiveUrls.validFilename(name)) return@forEach
            if (named.put(name, file) != null) throw ParsingException("Internet Archive returned duplicate filenames")
        }
        val groups = linkedMapOf<String, MutableList<ArchiveMediaFile>>()
        named.forEach { (name, record) ->
            val mediaFile = mediaFile(name, record, mediaType) ?: return@forEach
            val original = original(name, named)
            groups.getOrPut(original) { mutableListOf() }.add(mediaFile)
        }
        return groups.map { (root, variants) ->
            // Canonical identity uses the original filename whenever it is a public file. This
            // remains stable when Archive regenerates or adds alternate encodings.
            val ordered = variants.sortedWith(compareBy<ArchiveMediaFile> { !it.video }
                .thenByDescending { it.height }.thenBy { it.name })
            val originalRecord = named[root]
            val canonical = root.takeIf { originalRecord != null && !ArchiveItem.restricted(originalRecord) }
                ?: ordered.first().name
            val source = named[canonical] ?: named.getValue(ordered.first().name)
            val title = ArchiveText.plain(source["title"])
                .ifBlank { ordered.asSequence().mapNotNull { named[it.name] }.map { ArchiveText.plain(it["title"]) }.firstOrNull(String::isNotBlank).orEmpty() }
                .ifBlank { if (groups.size == 1) itemTitle else canonical.substringAfterLast('/').substringBeforeLast('.') }
            val duration = ArchiveText.duration(source["length"]).takeIf { it >= 0 }
                ?: ordered.firstOrNull { it.duration >= 0 }?.duration ?: -1
            val order = ArchiveText.value(source["track"]).substringBefore('/').toIntOrNull() ?: Int.MAX_VALUE
            order to ArchiveTrack(canonical, title, duration, ordered)
        }.sortedWith(compareBy<Pair<Int, ArchiveTrack>> { it.first }.thenBy { it.second.filename })
            .map { it.second }
    }

    private fun original(name: String, records: Map<String, JsonObject>): String {
        var current = name
        val seen = mutableSetOf<String>()
        while (seen.add(current)) {
            val parent = ArchiveText.value(records[current]?.get("original"))
            // Do not invent file links to missing originals or infer groups from similar names.
            if (parent.isEmpty() || parent !in records || !ArchiveUrls.validFilename(parent)) return current
            current = parent
        }
        throw ParsingException("Internet Archive returned a cyclic original-file relationship")
    }

    private fun mediaFile(name: String, json: JsonObject, mediaType: String): ArchiveMediaFile? {
        if (ArchiveItem.restricted(json) || ArchiveText.number(json["size"]) == 0L) return null
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val format = ArchiveText.value(json["format"]).lowercase(Locale.ROOT)
        // A filename alone is insufficient when the server positively identifies a different
        // format (for example an .ogg file containing Theora video, or a metadata sidecar).
        val selection = when (extension) {
            "mp4", "m4v" -> if (format.isEmpty() || format in setOf("mpeg4", "mpeg-4", "h.264", "h.264 ia", "512kb mpeg4", "256kb mpeg4", "64kb mpeg4")) MediaFormat.MPEG_4 to true else null
            "webm" -> if (format.isEmpty() || format == "webm") {
                if (mediaType == "audio" || mediaType == "etree") MediaFormat.WEBMA to false else MediaFormat.WEBM to true
            } else null
            "mp3" -> if (format.isEmpty() || format.contains("mp3") || format == "mpeg audio") MediaFormat.MP3 to false else null
            "m4a" -> if (format.isEmpty() || format in setOf("m4a", "aac", "mpeg4 audio", "mpeg-4 audio")) MediaFormat.M4A to false else null
            "ogg", "oga" -> if (format.isEmpty() && mediaType != "movies" || format == "ogg vorbis") MediaFormat.OGG to false else null
            "opus" -> if (format.isEmpty() || format in setOf("opus", "ogg opus")) MediaFormat.OPUS to false else null
            else -> null
        } ?: return null
        return ArchiveMediaFile(
            name,
            selection.first,
            selection.second,
            ArchiveText.duration(json["length"]),
            positiveInt(json["width"]),
            positiveInt(json["height"]),
            // IA's bitrate metadata is expressed in kilobits per second when present.
            positiveInt(json["bitrate"])
        )
    }

    private fun positiveInt(value: Any?): Int = ArchiveText.number(value)
        .takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: -1
}
