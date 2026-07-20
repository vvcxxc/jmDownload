package com.jmdl.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.jmdl.data.Task
import java.io.File
import java.io.InputStream

/**
 * Storage backend for saved zips.
 * - Default (no folder chosen): public Download/JMComic via MediaStore — no picker, no
 *   permission (Android 10+). On Android 9 and below the user must pick a SAF folder once.
 * - If the user picked a SAF folder (顶部"更改"): save there.
 * Each saved zip is identified by its own content Uri ([Task.fileUri]) so view / delete /
 * move all work regardless of which backend produced it.
 */
object Storage {

    private const val SUBDIR = "JMComic"

    data class Saved(val fileUri: String, val name: String)

    /** Save the zip of [workDir] as [zipName]. [treeUri] = user-picked SAF folder, or null = default Downloads. */
    fun saveZip(context: Context, workDir: File, zipName: String, treeUri: String?): Saved = when {
        treeUri != null -> saveToTree(context, workDir, Uri.parse(treeUri), zipName)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToDownloads(context, workDir, zipName)
        else -> throw IllegalStateException("请先在顶部选择保存目录（安卓9及以下需手动选一次）")
    }

    private fun saveToTree(context: Context, workDir: File, tree: Uri, zipName: String): Saved {
        val dir = DocumentFile.fromTreeUri(context, tree)
            ?: throw IllegalStateException("保存目录不可用，请重新选择")
        if (!dir.canWrite()) throw IllegalStateException("没有保存目录的写入权限，请重新选择")
        val name = uniqueTreeName(dir, zipName)
        val doc = dir.createFile("application/zip", name)
            ?: throw IllegalStateException("无法在保存目录创建文件")
        try {
            val out = context.contentResolver.openOutputStream(doc.uri)
                ?: throw IllegalStateException("无法写入保存目录")
            out.use { Zipper.writeZip(workDir, it) }
        } catch (e: Exception) {
            runCatching { doc.delete() }
            throw e
        }
        return Saved(doc.uri.toString(), name)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(context: Context, workDir: File, zipName: String): Saved {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, zipName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("无法在下载目录创建文件")
        try {
            val out = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("无法写入下载目录")
            out.use { Zipper.writeZip(workDir, it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return Saved(uri.toString(), zipName)
    }

    /** Open the task's saved zip for reading. Throws if it can't be located/opened. */
    fun openInput(context: Context, task: Task): InputStream {
        val uri = fileUriOf(context, task) ?: throw IllegalStateException("找不到文件")
        return context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取文件")
    }

    /** Delete the task's saved zip (MediaStore or SAF). Best-effort. */
    fun deleteFile(context: Context, task: Task) {
        val uri = fileUriOf(context, task) ?: return
        runCatching {
            if (uri.authority == MediaStore.AUTHORITY) {
                context.contentResolver.delete(uri, null, null)
            } else {
                DocumentFile.fromSingleUri(context, uri)?.delete()
            }
        }
    }

    private fun fileUriOf(context: Context, task: Task): Uri? {
        task.fileUri?.let { return Uri.parse(it) }
        // legacy fallback: derive from tree + name
        val tree = task.saveTreeUri ?: return null
        val name = task.zipName ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(tree))?.findFile(name)?.uri
    }

    private fun uniqueTreeName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var i = 1
        while (dir.findFile("$base ($i)$ext") != null) i++
        return "$base ($i)$ext"
    }
}
