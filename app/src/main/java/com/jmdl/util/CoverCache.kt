package com.jmdl.util

import android.content.Context
import android.net.Uri
import com.jmdl.core.PageOrdering
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object CoverCache {

    fun getDir(context: Context): File {
        val dir = File(context.cacheDir, "covers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCoverFile(context: Context, jmId: String): File {
        return File(getDir(context), "${jmId}.jpg")
    }

    suspend fun extractAndCache(
        context: Context,
        fileUri: String,
        jmId: String,
    ): File? = withContext(Dispatchers.IO) {
        val coverFile = getCoverFile(context, jmId)
        if (coverFile.exists()) {
            AppLogger.i("CoverCache: already cached jmId=$jmId")
            return@withContext coverFile
        }
        try {
            val uri = Uri.parse(fileUri)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && PageOrdering.isImageName(entry.name)) {
                            FileOutputStream(coverFile).use { fos ->
                                zis.copyTo(fos, bufferSize = 64 * 1024)
                            }
                            AppLogger.i("CoverCache: extracted jmId=$jmId entry=${entry.name}")
                            return@withContext coverFile
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            AppLogger.w("CoverCache: no entries in zip jmId=$jmId")
            null
        } catch (e: Exception) {
            AppLogger.e("CoverCache: extract failed jmId=$jmId", e)
            null
        }
    }

}