/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

internal class ArchiveStreamExtractor(service: StreamingService, linkHandler: LinkHandler) :
    StreamExtractor(service, linkHandler) {
    private val target = ArchiveUrls.trackFromId(linkHandler.id)
    private lateinit var item: ArchiveItem
    private lateinit var track: ArchiveTrack

    override fun onFetchPage(downloader: Downloader) {
        item = ArchiveApi.item(target.identifier, downloader)
        track = item.track(target.filename)
    }

    override fun getId(): String = ArchiveTrackTarget(target.identifier, track.filename).id
    override fun getUrl(): String = ArchiveUrls.download(target.identifier, track.filename)
    override fun getName(): String = track.title
    override fun getUploaderUrl(): String = ""
    override fun getUploaderName(): String = item.creator
    override fun getThumbnailUrl(): String = item.thumbnail
    override fun getDescription(): Description = Description(item.description, Description.PLAIN_TEXT)
    override fun getLength(): Long = track.duration
    override fun getStreamType(): StreamType = track.type
    override fun getTextualUploadDate(): String? = item.date
    override fun getUploadDate(): DateWrapper? = ArchiveText.date(item.date)
    override fun getLicence(): String = item.license
    override fun isSupportComments(): Boolean = false
    override fun isSupportBulletComments(): Boolean = false
    override fun isSupportRelatedItems(): Boolean = item.tracks.size > 1

    override fun getAudioStreams(): List<AudioStream> = track.files.filter { !it.video }.map { file ->
        AudioStream.Builder()
            .setId(file.name)
            .setContent(ArchiveUrls.download(item.identifier, file.name), true)
            .setMediaFormat(file.mediaFormat)
            .setAverageBitrate(file.bitrate)
            .build()
    }

    override fun getVideoStreams(): List<VideoStream> = track.files.filter { it.video }.map { file ->
        VideoStream.Builder()
            .setId(file.name)
            .setContent(ArchiveUrls.download(item.identifier, file.name), true)
            .setMediaFormat(file.mediaFormat)
            .setIsVideoOnly(false)
            .setWidth(file.width)
            .setHeight(file.height)
            .setResolution(if (file.height > 0) "${file.height}p" else VideoStream.RESOLUTION_UNKNOWN)
            .build()
    }

    override fun getVideoOnlyStreams(): List<VideoStream> = emptyList()

    override fun getPartitions(): StreamInfoItemsCollector = StreamInfoItemsCollector(serviceId).also { collector ->
        if (item.tracks.size > 1) item.tracks.forEach { collector.commit(ArchiveTrackInfo(item, it)) }
    }

    override fun getRelatedItems(): StreamInfoItemsCollector = StreamInfoItemsCollector(serviceId).also { collector ->
        item.tracks.filter { it.filename != track.filename }.forEach { collector.commit(ArchiveTrackInfo(item, it)) }
    }
}
