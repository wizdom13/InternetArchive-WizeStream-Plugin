/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.wizestream.plugin.internetarchive

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

internal fun fixture(name: String): String = requireNotNull(
    FixtureDownloader::class.java.getResourceAsStream("/fixtures/$name.json")
).bufferedReader().use { it.readText() }

internal fun fixtureJson(name: String): JsonObject = json(fixture(name))
internal fun json(value: String): JsonObject = JsonParser.`object`().from(value)

internal data class FixtureResponse(val body: String, val status: Int = 200, val latestUrl: String? = null)

/** Any unexpected URL fails locally. Tests never contact Archive, media CDNs or the network. */
internal class FixtureDownloader(private val responses: Map<String, FixtureResponse>) : Downloader() {
    val requests = mutableListOf<String>()

    override fun execute(request: Request): Response {
        val url = request.url()
        requests += url
        val response = responses[url] ?: throw AssertionError("Unexpected network request: $url")
        return Response(response.status, "Fixture", emptyMap(), response.body, response.body.toByteArray(), response.latestUrl ?: url)
    }

    override fun executeAsync(request: Request, callback: AsyncCallback): CancellableCall =
        throw AssertionError("Asynchronous networking is not used by these extractors")
}
