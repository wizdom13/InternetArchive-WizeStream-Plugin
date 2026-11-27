/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import org.schabi.newpipe.extractor.exceptions.ParsingException

internal data class ArchiveTrackTarget(val identifier: String, val filename: String) {
    val id: String get() = "$identifier/$filename"
    val url: String get() = ArchiveUrls.download(identifier, filename)
}

/** Only public, permanent archive.org URLs are accepted; never persist an iaNNNN data-node URL. */
internal object ArchiveUrls {
    const val BASE = "https://archive.org"
    private val identifierPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,255}")

    fun validIdentifier(value: String): Boolean = identifierPattern.matches(value)

    fun validFilename(value: String): Boolean = value.isNotBlank() && value.length <= 4096 &&
        value.none { it == '\\' || it.code < 32 || it.code == 127 } &&
        value.split('/').none { it.isEmpty() || it == "." || it == ".." }

    fun itemUrl(identifier: String): String {
        requireIdentifier(identifier)
        return "$BASE/details/$identifier"
    }

    fun metadataUrl(identifier: String): String {
        requireIdentifier(identifier)
        return "$BASE/metadata/$identifier?extended_err=1"
    }

    fun thumbnailUrl(identifier: String): String {
        requireIdentifier(identifier)
        return "$BASE/services/img/$identifier"
    }

    fun download(identifier: String, filename: String): String {
        requireIdentifier(identifier)
        if (!validFilename(filename)) throw ParsingException("Invalid Internet Archive filename")
        return "$BASE/download/$identifier/${filename.split('/').joinToString("/", transform = ::encode)}"
    }

    fun itemFromUrl(url: String): String? = safely {
        val parts = pathParts(trustedUri(url))
        if (parts.size != 2 || parts[0] !in setOf("details", "download")) return@safely null
        parts[1].takeIf(::validIdentifier)
    }

    fun trackFromUrl(url: String): ArchiveTrackTarget? = safely {
        val parts = pathParts(trustedUri(url))
        if (parts.size < 3 || parts[0] != "download") return@safely null
        val identifier = parts[1]
        val filename = parts.drop(2).joinToString("/")
        if (!validIdentifier(identifier) || !validFilename(filename)) return@safely null
        ArchiveTrackTarget(identifier, filename)
    }

    fun trackFromId(id: String): ArchiveTrackTarget {
        val identifier = id.substringBefore('/')
        val filename = id.substringAfter('/', "")
        requireIdentifier(identifier)
        if (!validFilename(filename)) throw ParsingException("Invalid Internet Archive track identity")
        return ArchiveTrackTarget(identifier, filename)
    }

    fun trustedUri(url: String): URI {
        val uri = try {
            URI(url)
        } catch (error: Exception) {
            throw ParsingException("Invalid Internet Archive URL", error)
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (scheme !in setOf("https", "http") || host !in setOf("archive.org", "www.archive.org") ||
            uri.rawUserInfo != null || uri.port != -1 && uri.port != if (scheme == "https") 443 else 80
        ) {
            throw ParsingException("This URL does not belong to Internet Archive")
        }
        return uri
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun decode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (error: IllegalArgumentException) {
        throw ParsingException("Invalid URL encoding", error)
    }

    private fun pathParts(uri: URI): List<String> {
        val path = uri.rawPath.orEmpty().removeSuffix("/")
        if (!path.startsWith('/')) throw ParsingException("Missing Internet Archive URL path")
        return path.drop(1).split('/').map { part ->
            decode(part).also {
                if (it.isEmpty() || it == "." || it == ".." || '/' in it || '\\' in it || it.any { c -> c.code < 32 }) {
                    throw ParsingException("Invalid Internet Archive URL path")
                }
            }
        }
    }

    private fun requireIdentifier(value: String) {
        if (!validIdentifier(value)) throw ParsingException("Invalid Internet Archive item identifier")
    }

    private inline fun <T> safely(block: () -> T?): T? = try {
        block()
    } catch (_: ParsingException) {
        null
    }
}
