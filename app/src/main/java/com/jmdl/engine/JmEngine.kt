package com.jmdl.engine

import io.github.jukomu.jmcomic.api.enums.ClientType
import io.github.jukomu.jmcomic.core.JmComic
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration
import java.io.File

/**
 * Thin wrapper over jmcomic-android-support. The library talks to the JM mobile API
 * (token-signed, no proxy/Cloudflare), downloads all images AND descrambles them via the
 * auto-registered [io.github.jukomu.jmcomic.android.support.AndroidImageProcessor], writing
 * decoded files under the given work dir. All calls here are blocking.
 */
object JmEngine {

    @Volatile
    private var clientRef: JmApiClient? = null

    private fun client(): JmApiClient =
        clientRef ?: synchronized(this) {
            clientRef ?: JmComic.newApiClient(
                JmConfiguration.Builder()
                    .clientType(ClientType.API)
                    .retryTimes(5)
                    .build()
            ).also { clientRef = it }
        }

    data class Outcome(val total: Int, val success: Int, val failed: Int)

    /** Blocking. Album title (or "JM{id}" fallback). May throw on network / not-found. */
    fun getTitle(jmId: String): String {
        val album = client().getAlbum(jmId)
        val t = album.title
        return if (t.isNullOrBlank()) "JM$jmId" else t
    }

    /**
     * Blocking. Downloads the whole album into [workDir] (single-chapter → flat images,
     * multi-chapter → "NN_title/photoId/" subfolders). Images are descrambled by the lib.
     */
    fun download(jmId: String, workDir: File, onProgress: (done: Int, total: Int) -> Unit): Outcome {
        val c = client()
        val album = c.getAlbum(jmId) // cached from getTitle()
        if (!workDir.exists()) workDir.mkdirs()
        var lastTotal = 0
        val result = c.download(album)
            .withPath(workDir.toPath())
            .withProgress { p ->
                lastTotal = p.totalImages()
                onProgress(p.completedImages(), p.totalImages())
            }
            .execute()
        val success = result.successfulFiles.size
        val failed = result.failedTasks.size
        val total = if (lastTotal > 0) lastTotal else success + failed
        return Outcome(total = total, success = success, failed = failed)
    }
}
