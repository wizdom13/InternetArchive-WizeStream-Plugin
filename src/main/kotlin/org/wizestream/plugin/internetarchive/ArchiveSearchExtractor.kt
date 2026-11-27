/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchExtractor

internal class ArchiveSearchExtractor(service: StreamingService, linkHandler: SearchQueryHandler) :
    SearchExtractor(service, linkHandler) {
    private lateinit var initial: JsonObject

    override fun onFetchPage(downloader: Downloader) {
        initial = ArchiveApi.get(ArchiveSearch.pageUrl(searchString, 1), downloader)
    }

    override fun getInitialPageInternal(): InfoItemsPage<InfoItem> {
        assertPageFetched()
        return ArchiveSearch.parse(initial, searchString, 1, serviceId)
    }

    override fun getPageInternal(page: Page): InfoItemsPage<InfoItem> {
        val number = ArchiveSearch.pageNumber(searchString, page)
        return ArchiveSearch.parse(ArchiveApi.get(page.url, downloader), searchString, number, serviceId)
    }
}
