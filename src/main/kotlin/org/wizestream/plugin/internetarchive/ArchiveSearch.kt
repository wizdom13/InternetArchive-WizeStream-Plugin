/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.MultiInfoItemsCollector
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor

internal object ArchiveSearch {
    const val PAGE_SIZE = 50
    const val MAX_RESULTS = 10_000
    private val fields = listOf("identifier", "title", "creator", "publisher", "mediatype", "access-restricted-item")

    fun pageUrl(search: String, page: Int): String {
        if (page !in 1..MAX_RESULTS / PAGE_SIZE || search.length > 1024) {
            throw ParsingException("Internet Archive search exceeds the supported page or query size")
        }
        // Search text is literal: user-provided query syntax cannot remove the media/access scope.
        val terms = search.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        val text = terms.joinToString(" AND ") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }.ifBlank { "*:*" }
        val query = "($text) AND mediatype:(audio OR movies OR etree) AND NOT access-restricted-item:true"
        val parameters = mutableListOf("q" to query, "output" to "json", "rows" to PAGE_SIZE.toString(), "page" to page.toString())
        parameters += fields.map { "fl[]" to it }
        parameters += listOf("sort[]" to "downloads desc", "sort[]" to "identifier asc")
        return "${ArchiveUrls.BASE}/advancedsearch.php?" + parameters.joinToString("&") {
            "${ArchiveUrls.encode(it.first)}=${ArchiveUrls.encode(it.second)}"
        }
    }

    fun pageNumber(search: String, page: Page): Int {
        val number = page.id?.toIntOrNull() ?: throw ParsingException("Internet Archive search page has no valid position")
        if (number <= 1 || page.url != pageUrl(search, number)) {
            throw ParsingException("Internet Archive search continuation changed its query or page")
        }
        return number
    }

    fun parse(json: JsonObject, search: String, page: Int, serviceId: Int): InfoItemsPage<InfoItem> {
        val header = json["responseHeader"] as? JsonObject
        if (header != null && ArchiveText.number(header["status"], 0) != 0L) {
            throw ParsingException("Internet Archive search could not process this query")
        }
        val response = json["response"] as? JsonObject
            ?: throw ParsingException("Internet Archive search returned no response")
        val start = ArchiveText.number(response["start"])
        val total = ArchiveText.number(response["numFound"])
        val expected = (page - 1) * PAGE_SIZE
        val docs = response["docs"] as? List<*>
            ?: throw ParsingException("Internet Archive search returned no result list")
        if (start != expected.toLong() || total < 0 || docs.size > PAGE_SIZE) {
            throw ParsingException("Internet Archive search returned an invalid page position")
        }
        val collector = MultiInfoItemsCollector(serviceId)
        val seen = mutableSetOf<String>()
        docs.filterIsInstance<JsonObject>().forEach { doc ->
            val identifier = ArchiveText.value(doc["identifier"])
            if (!ArchiveUrls.validIdentifier(identifier) || !seen.add(identifier) || ArchiveItem.restricted(doc) ||
                ArchiveText.value(doc["mediatype"]) !in ArchiveItem.MEDIA_TYPES
            ) return@forEach
            collector.commit(object : PlaylistInfoItemExtractor {
                override fun getName(): String = ArchiveText.plain(doc["title"]).ifBlank { identifier }
                override fun getUrl(): String = ArchiveUrls.itemUrl(identifier)
                override fun getThumbnailUrl(): String = ArchiveUrls.thumbnailUrl(identifier)
                override fun getUploaderName(): String = ArchiveText.plain(doc["creator"])
                    .ifBlank { ArchiveText.plain(doc["publisher"]) }
                // Search metadata does not tell us how many playable tracks an item contains.
                override fun getStreamCount(): Long = -1
            })
        }
        // Do not request beyond the provider's 10,000-result sorted pagination window.
        // Short/empty pages are terminal even if a stale numFound claims more matches.
        val next = if (docs.size == PAGE_SIZE && start + docs.size < minOf(total, MAX_RESULTS.toLong())) {
            Page(pageUrl(search, page + 1), (page + 1).toString())
        } else null
        return InfoItemsPage(collector, next)
    }
}
