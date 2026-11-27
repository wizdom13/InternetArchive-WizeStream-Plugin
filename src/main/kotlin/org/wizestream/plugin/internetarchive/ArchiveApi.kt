/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException

internal object ArchiveApi {
    fun item(identifier: String, downloader: Downloader): ArchiveItem =
        ArchiveItem.parse(identifier, get(ArchiveUrls.metadataUrl(identifier), downloader))

    fun get(url: String, downloader: Downloader): JsonObject {
        val requested = ArchiveUrls.trustedUri(url)
        val response = downloader.get(url, mapOf("Accept" to listOf("application/json")))
        when (response.responseCode()) {
            401, 403 -> throw ContentNotAvailableException("This Internet Archive resource is restricted or requires sign-in")
            404, 410 -> throw ContentNotAvailableException("This Internet Archive resource was removed or is unavailable")
            429 -> throw ExtractionException("Internet Archive is limiting requests. Please try again later")
            in 500..599 -> throw ExtractionException("Internet Archive is temporarily unavailable. Please try again later")
            in 200..299 -> Unit
            else -> throw ExtractionException("Internet Archive returned HTTP ${response.responseCode()}")
        }
        val latest = response.latestUrl().takeUnless { it.isNullOrBlank() } ?: url
        val redirected = ArchiveUrls.trustedUri(latest)
        if (redirected.path != requested.path || redirected.scheme != "https") {
            throw ParsingException("Internet Archive redirected the request away from its public API")
        }
        val body = response.responseBody()
        if (body.length > 16 * 1024 * 1024) {
            throw ContentNotSupportedException("This Internet Archive metadata response is too large")
        }
        if (body.trim() == "[]") {
            throw ContentNotAvailableException("This Internet Archive item does not exist or is unavailable")
        }
        val json = try {
            JsonParser.`object`().from(body)
        } catch (error: JsonParserException) {
            throw ParsingException("Internet Archive did not return valid JSON metadata", error)
        }
        if (json.containsKey("error")) {
            when (ArchiveText.number(json["errcode"])) {
                101L -> throw ContentNotAvailableException("This Internet Archive item is still being created")
                102L, 105L, 106L -> throw ExtractionException("Internet Archive cannot currently access this item. Please try again later")
                104L -> throw ContentNotAvailableException("This Internet Archive item has been deleted")
                else -> throw ContentNotAvailableException("Internet Archive could not provide this resource")
            }
        }
        return json
    }
}
