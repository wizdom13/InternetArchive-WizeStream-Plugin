/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor

class InternetArchiveService(id: Int) : StreamingService(
    id,
    "Internet Archive",
    listOf(ServiceInfo.MediaCapability.AUDIO, ServiceInfo.MediaCapability.VIDEO)
) {
    override fun getBaseUrl(): String = ArchiveUrls.BASE

    override fun getStreamLHFactory(): LinkHandlerFactory = STREAM_LINKS
    override fun getPlaylistLHFactory(): ListLinkHandlerFactory = ITEM_LINKS
    override fun getSearchQHFactory(): SearchQueryHandlerFactory = SEARCH_LINKS
    override fun getSearchExtractor(queryHandler: SearchQueryHandler): SearchExtractor =
        ArchiveSearchExtractor(this, queryHandler)
    override fun getStreamExtractor(linkHandler: LinkHandler): StreamExtractor =
        ArchiveStreamExtractor(this, linkHandler)
    override fun getPlaylistExtractor(linkHandler: ListLinkHandler): PlaylistExtractor =
        ArchivePlaylistExtractor(this, linkHandler)

    override fun getChannelLHFactory(): ListLinkHandlerFactory? = null
    override fun getChannelTabLHFactory(): ListLinkHandlerFactory? = null
    override fun getCommentsLHFactory(): ListLinkHandlerFactory? = null
    override fun getSuggestionExtractor(): SuggestionExtractor? = null
    override fun getSubscriptionExtractor(): SubscriptionExtractor? = null
    override fun getKioskList(): KioskList = KioskList(this)
    override fun getChannelExtractor(linkHandler: ListLinkHandler): ChannelExtractor =
        throw ContentNotSupportedException("Internet Archive collection and creator browsing is not supported")
    override fun getChannelTabExtractor(linkHandler: ListLinkHandler): ChannelTabExtractor =
        throw ContentNotSupportedException("Internet Archive channel tabs are not supported")
    override fun getCommentsExtractor(linkHandler: ListLinkHandler): CommentsExtractor =
        throw ContentNotSupportedException("Internet Archive reviews are not supported")

    companion object {
        internal val STREAM_LINKS = object : LinkHandlerFactory() {
            override fun onAcceptUrl(url: String): Boolean = ArchiveUrls.trackFromUrl(url) != null
            override fun getId(url: String): String = ArchiveUrls.trackFromUrl(url)?.id
                ?: throw ParsingException("Use an Internet Archive download URL for a specific track")
            override fun getUrl(id: String): String = ArchiveUrls.trackFromId(id).url
        }

        internal val ITEM_LINKS = object : ListLinkHandlerFactory() {
            override fun onAcceptUrl(url: String): Boolean = ArchiveUrls.itemFromUrl(url) != null
            override fun getId(url: String): String = ArchiveUrls.itemFromUrl(url)
                ?: throw ParsingException("Use an Internet Archive details URL for an audio or video item")
            override fun getUrl(id: String, contentFilter: List<FilterItem>?, sortFilter: List<FilterItem>?): String =
                ArchiveUrls.itemUrl(id)
        }

        internal val SEARCH_LINKS = object : SearchQueryHandlerFactory() {
            override fun getUrl(query: String, contentFilter: List<FilterItem>?, sortFilter: List<FilterItem>?): String =
                ArchiveSearch.pageUrl(query, 1)
        }
    }
}
