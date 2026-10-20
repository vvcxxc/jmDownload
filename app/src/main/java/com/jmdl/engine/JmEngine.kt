package com.jmdl.engine

import com.jmdl.util.AppLogger
import io.github.jukomu.jmcomic.api.enums.ClientType
import io.github.jukomu.jmcomic.api.model.JmAlbumMeta
import io.github.jukomu.jmcomic.api.model.JmSearchPage
import io.github.jukomu.jmcomic.api.model.SearchQuery
import io.github.jukomu.jmcomic.core.JmComic
import io.github.jukomu.jmcomic.core.client.impl.JmApiClient
import io.github.jukomu.jmcomic.core.config.JmConfiguration
import java.io.File

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

    data class AlbumInfo(val title: String, val authors: List<String>)

    fun getAlbumInfo(jmId: String): AlbumInfo {
        return try {
            val album = client().getAlbum(jmId)
            val title = album.title
            val safeTitle = if (title.isNullOrBlank()) {
                AppLogger.w("Album title is blank for jmId=$jmId")
                "JM$jmId"
            } else {
                title
            }
            val authors = album.authors.orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            AppLogger.i("Album info jmId=$jmId title=$safeTitle authors=$authors")
            AlbumInfo(safeTitle, authors)
        } catch (e: Exception) {
            AppLogger.e("Failed to get album info for jmId=$jmId", e)
            throw e
        }
    }

    fun getTitle(jmId: String): String = getAlbumInfo(jmId).title

    fun download(jmId: String, workDir: File, onProgress: (done: Int, total: Int) -> Unit): Outcome {
        return try {
            val c = client()
            val album = c.getAlbum(jmId)
            if (!workDir.exists()) {
                workDir.mkdirs()
                AppLogger.i("Created work directory: ${workDir.path}")
            }

            var stableTotal = 0
            val result = c.download(album)
                .withPath(workDir.toPath())
                .withProgress { p ->
                    val reportedTotal = p.totalImages()
                    val reportedDone = p.completedImages()
                    
                    if (reportedTotal > 0) {
                        stableTotal = if (stableTotal == 0 || reportedTotal < stableTotal) {
                            reportedTotal
                        } else {
                            stableTotal
                        }
                    }
                    
                    val (safeDone, safeTotal) = calculateSafeProgress(reportedDone, reportedTotal, stableTotal)
                    onProgress(safeDone, safeTotal)
                }
                .execute()

            val success = result.successfulFiles.size
            val failed = result.failedTasks.size
            val total = stableTotal.coerceAtLeast(success + failed)
            
            AppLogger.i("Download completed jmId=$jmId success=$success failed=$failed total=$total")
            Outcome(total = total, success = success, failed = failed)
            
        } catch (e: Exception) {
            AppLogger.e("Download failed jmId=$jmId", e)
            throw e
        }
    }

    fun calculateSafeProgress(done: Int, total: Int, lastReportedTotal: Int): Pair<Int, Int> {
        val stableTotal = if (total > 0) total else lastReportedTotal
        val safeDone = done.coerceAtMost(stableTotal.coerceAtLeast(done))
        val safeTotal = stableTotal.coerceAtLeast(safeDone)
        
        if (total > 0 && lastReportedTotal > 0 && total != lastReportedTotal) {
            AppLogger.w("Total pages changed: $lastReportedTotal -> $total")
        }
        
        return safeDone to safeTotal
    }

    fun calculateSafeProgress(done: Int, total: Int, lastDone: Int, lastTotal: Int): Pair<Int, Int> {
        val stableTotal = if (total > 0) total else lastTotal
        val safeDone = done.coerceAtMost(stableTotal.coerceAtLeast(done))
        val safeTotal = stableTotal.coerceAtLeast(safeDone)
        
        if (total > 0 && lastTotal > 0 && total != lastTotal) {
            AppLogger.w("Total pages changed: $lastTotal -> $total")
        }
        
        return safeDone to safeTotal
    }

    fun searchByAlbum(jmId: String): io.github.jukomu.jmcomic.api.model.JmAlbum {
        return client().getAlbum(jmId)
    }

    fun searchByKeyword(keyword: String, page: Int = 1): JmSearchPage {
        val query = SearchQuery.Builder()
            .text(keyword)
            .page(page)
            .build()
        return client().search(query)
    }
}