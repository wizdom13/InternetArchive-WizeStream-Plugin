/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector

/** An Archive item is a playlist, even when it contains just one supported recording. */
internal class ArchivePlaylistExtractor(service: StreamingService, linkHandler: ListLinkHandler) :
    PlaylistExtractor(service, linkHandler) {
    private lateinit var item: ArchiveItem

    override fun onFetchPage(downloader: Downloader) {
        item = ArchiveApi.item(id, downloader)
    }

    override fun getName(): String = item.title
    override fun getUploaderUrl(): String = ""
    override fun getUploaderName(): String = item.creator
    override fun getUploaderAvatarUrl(): String = ""
    override fun isUploaderVerified(): Boolean = false
    override fun getStreamCount(): Long = item.tracks.size.toLong()
    override fun getThumbnailUrl(): String = item.thumbnail

    override fun getInitialPage(): InfoItemsPage<StreamInfoItem> {
        assertPageFetched()
        return tracks(0)
    }

    override fun getPage(page: Page): InfoItemsPage<StreamInfoItem> {
        fetchPage()
        val offset = page.id?.toIntOrNull() ?: throw ParsingException("Internet Archive track page has no position")
        if (page.url != item.url || offset <= 0 || offset % TRACKS_PER_PAGE != 0 || offset >= item.tracks.size) {
            throw ParsingException("Internet Archive track continuation changed its item or position")
        }
        return tracks(offset)
    }

    private fun tracks(offset: Int): InfoItemsPage<StreamInfoItem> {
        val end = minOf(offset + TRACKS_PER_PAGE, item.tracks.size)
        val collector = StreamInfoItemsCollector(serviceId)
        item.tracks.subList(offset, end).forEach { collector.commit(ArchiveTrackInfo(item, it)) }
        return InfoItemsPage(collector, if (end < item.tracks.size) Page(item.url, end.toString()) else null)
    }

    companion object {
        const val TRACKS_PER_PAGE = 100
    }
}
