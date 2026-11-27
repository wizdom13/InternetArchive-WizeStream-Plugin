/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.plugin.WizeStreamPlugin
import org.schabi.newpipe.extractor.plugin.WizeStreamPluginContext

/** Native API 2.0 entry point. The host owns networking, playback and the assigned service ID. */
class InternetArchivePlugin : WizeStreamPlugin {
    override fun createService(serviceId: Int, context: WizeStreamPluginContext): StreamingService =
        InternetArchiveService(serviceId)
}
