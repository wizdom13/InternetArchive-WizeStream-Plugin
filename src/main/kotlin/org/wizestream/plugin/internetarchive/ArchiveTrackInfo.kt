/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamType

internal class ArchiveTrackInfo(private val item: ArchiveItem, private val track: ArchiveTrack) : StreamInfoItemExtractor {
    override fun getName(): String = track.title
    override fun getUrl(): String = ArchiveUrls.download(item.identifier, track.filename)
    override fun getThumbnailUrl(): String = item.thumbnail
    override fun getStreamType(): StreamType = track.type
    override fun getDuration(): Long = track.duration
    override fun getViewCount(): Long = -1
    override fun getUploaderName(): String = item.creator
    override fun getTextualUploadDate(): String? = item.date
    override fun getUploadDate(): DateWrapper? = ArchiveText.date(item.date)
    override fun getAlbumUrl(): String = item.url
}
