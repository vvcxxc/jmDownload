package com.jmdl.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.content.ContentUris
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.jmdl.data.Task
import com.jmdl.util.AppLogger
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object Storage {

    private const val SUBDIR = "JMComic"

    data class Saved(val fileUri: String, val name: String)

    class DuplicateFileException(val fileName: String) : Exception("文件已存在: $fileName")

    fun findExistingFile(context: Context, zipName: String, treeUri: String?): String? {
        if (treeUri != null) {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            return dir?.findFile(zipName)?.uri?.toString()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return findExistingDownloadsFile(context, zipName)
        }
        return null
    }

    fun saveZip(context: Context, workDir: File, zipName: String, treeUri: String?): Saved {
        checkDuplicate(context, zipName, treeUri)
        return when {
            treeUri != null -> writeTreeFile(context, Uri.parse(treeUri), zipName) { out ->
                Zipper.writeZip(workDir, out)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> writeDownloadsFile(context, zipName) { out ->
                Zipper.writeZip(workDir, out)
            }
            else -> throw IllegalStateException("请先在顶部选择保存目录（安卓9及以下需手动选一次）")
        }
    }

    fun copyFileToSaveLocation(
        context: Context,
        sourceUri: Uri,
        zipName: String,
        treeUri: String?,
    ): Saved {
        checkDuplicate(context, zipName, treeUri)
        val input = context.contentResolver.openInputStream(sourceUri) ?: run {
            val error = "无法读取源文件: $sourceUri"
            AppLogger.e(error)
            throw IllegalStateException(error)
        }
        return input.use { source ->
            when {
                treeUri != null -> writeTreeFile(context, Uri.parse(treeUri), zipName) { out ->
                    source.copyTo(out)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> writeDownloadsFile(context, zipName) { out ->
                    source.copyTo(out)
                }
                else -> throw IllegalStateException("请先在顶部选择保存目录")
            }
        }
    }

    private fun checkDuplicate(context: Context, zipName: String, treeUri: String?) {
        if (treeUri != null) {
            if (DocumentFile.fromTreeUri(context, Uri.parse(treeUri))?.findFile(zipName) != null) {
                throw DuplicateFileException(zipName)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (downloadsFileExists(context, zipName)) throw DuplicateFileException(zipName)
        }
    }

    private fun requireTreeDir(context: Context, tree: Uri): DocumentFile {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: run {
            val error = "保存目录不可用，请重新选择"
            AppLogger.e(error)
            throw IllegalStateException(error)
        }
        if (!dir.canWrite()) {
            val error = "没有保存目录的写入权限，请重新选择"
            AppLogger.e(error)
            throw IllegalStateException(error)
        }
        return dir
    }

    private fun writeTreeFile(
        context: Context,
        tree: Uri,
        zipName: String,
        write: (OutputStream) -> Unit,
    ): Saved {
        val dir = requireTreeDir(context, tree)
        val doc = dir.createFile("application/zip", zipName) ?: run {
            val error = "无法在保存目录创建文件"
            AppLogger.e(error)
            throw IllegalStateException(error)
        }
        try {
            context.contentResolver.openOutputStream(doc.uri)?.use(write) ?: run {
                val error = "无法写入保存目录"
                AppLogger.e(error)
                throw IllegalStateException(error)
            }
            AppLogger.i("Saved zip to tree: ${doc.uri}")
            return Saved(doc.uri.toString(), zipName)
        } catch (e: Exception) {
            AppLogger.e("Failed to write zip to tree: $zipName", e)
            runCatching { doc.delete() }
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeDownloadsFile(
        context: Context,
        zipName: String,
        write: (OutputStream) -> Unit,
    ): Saved {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, zipName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: run {
            val error = "无法在下载目录创建文件"
            AppLogger.e(error)
            throw IllegalStateException(error)
        }
        try {
            resolver.openOutputStream(uri)?.use(write) ?: run {
                val error = "无法写入下载目录"
                AppLogger.e(error)
                throw IllegalStateException(error)
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            AppLogger.i("Saved zip to downloads: $uri")
            return Saved(uri.toString(), zipName)
        } catch (e: Exception) {
            AppLogger.e("Failed to write zip to downloads: $zipName", e)
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    fun openInput(context: Context, task: Task): InputStream {
        val uri = fileUriOf(context, task) ?: run {
            val error = "找不到文件: ${task.zipName}"
            AppLogger.e(error)
            throw IllegalStateException(error)
        }

        val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (input != null) {
            AppLogger.i("打开文件成功: $uri")
            return input
        }
        AppLogger.w("打开文件失败，尝试重新查询: $uri")

        if (task.zipName != null) {
            AppLogger.i("尝试通过文件名重新查询 MediaStore: ${task.zipName}")
            val reQueryUri = queryUriFromMediaStore(context, task.zipName)
            if (reQueryUri != null) {
                AppLogger.i("重新查询成功，新 URI: $reQueryUri")
                val reQueryInput = runCatching { context.contentResolver.openInputStream(reQueryUri) }.getOrNull()
                if (reQueryInput != null) {
                    AppLogger.i("使用新 URI 打开文件成功")
                    return reQueryInput
                }
                AppLogger.w("使用新 URI 打开失败")
            } else {
                AppLogger.w("通过文件名未查询到文件: ${task.zipName}")
            }
        }

        if (isDownloadsDocument(uri)) {
            AppLogger.i("尝试将 SAF Downloads 文档转换为 MediaStore URI")
            val mediaStoreUri = convertDownloadsDocumentToMediaStoreUri(context, uri)
            if (mediaStoreUri != null) {
                AppLogger.i("转换成功，MediaStore URI: $mediaStoreUri")
                val mediaInput = runCatching { context.contentResolver.openInputStream(mediaStoreUri) }.getOrNull()
                if (mediaInput != null) {
                    AppLogger.i("使用 MediaStore URI 打开文件成功")
                    return mediaInput
                }
                AppLogger.w("使用 MediaStore URI 打开失败")
            } else {
                AppLogger.w("无法将 SAF 文档转换为 MediaStore URI")
            }

            AppLogger.i("尝试通过 SAF 直接访问")
            val safInput = runCatching { 
                val document = DocumentFile.fromSingleUri(context, uri)
                if (document != null && document.exists()) {
                    context.contentResolver.openInputStream(uri)
                } else {
                    null
                }
            }.getOrNull()
            if (safInput != null) {
                AppLogger.i("使用 SAF 直接访问成功")
                return safInput
            }
            AppLogger.w("使用 SAF 直接访问失败")

            AppLogger.i("尝试通过 MediaStore 查询获取文件")
            val mediaStoreFile = localFileOf(context, uri)
            if (mediaStoreFile != null) {
                AppLogger.i("通过 MediaStore 查询成功")
                return mediaStoreFile.inputStream()
            }
            AppLogger.w("通过 MediaStore 查询失败")
        }

        val error = "无法读取文件: $uri"
        AppLogger.e(error)
        throw IllegalStateException(error)
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * Best-effort resolution of a content URI to a local File. Used to read the ZIP
     * central directory without decompressing/de-streaming the whole archive.
     * Returns null when the URI is not file-backed or scoped storage forbids access.
     */
    fun localFileOf(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let { File(it) }?.takeIf { it.isFile }
        val isDocument = runCatching { DocumentsContract.isDocumentUri(context, uri) }
            .getOrDefault(false)
        if (!isDocument) return null
        return try {
            val documentId = DocumentsContract.getDocumentId(uri)
            val split = documentId.split(":")
            val type = split.firstOrNull()
            val id = split.lastOrNull()
            
            if (id != null) {
                when (type) {
                    "msf" -> {
                        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
                        val selection = "${MediaStore.Files.FileColumns._ID} = ?"
                        val selectionArgs = arrayOf(id)
                        context.contentResolver.query(
                            MediaStore.Files.getContentUri("external"),
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists()) {
                                        return file
                                    }
                                }
                            }
                        }
                    }
                    "download" -> {
                        val projection = arrayOf(MediaStore.Downloads.DATA)
                        val selection = "${MediaStore.Downloads._ID} = ?"
                        val selectionArgs = arrayOf(id)
                        context.contentResolver.query(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATA))
                                if (path != null) {
                                    val file = File(path)
                                    if (file.exists()) {
                                        return file
                                    }
                                }
                            }
                        }
                    }
                    "raw" -> {
                        val file = File(id)
                        if (file.exists()) {
                            return file
                        }
                    }
                    "primary" -> {
                        val file = File(Environment.getExternalStorageDirectory(), id)
                        if (file.isFile) {
                            return file
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.w("从 MediaStore 查询文件失败: ${e.message}")
            null
        }
    }

    private fun convertDownloadsDocumentToMediaStoreUri(context: Context, uri: Uri): Uri? {
        return try {
            val documentId = DocumentsContract.getDocumentId(uri)
            val split = documentId.split(":")
            val type = split.firstOrNull()
            val id = split.lastOrNull()
            
            if (id != null) {
                when (type) {
                    "raw" -> {
                        val file = File(id)
                        if (file.exists()) {
                            return Uri.fromFile(file)
                        }
                    }
                    "msf" -> {
                        return ContentUris.withAppendedId(
                            MediaStore.Files.getContentUri("external"),
                            id.toLong()
                        )
                    }
                    "download" -> {
                        return ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            id.toLong()
                        )
                    }
                    "image" -> {
                        return ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toLong()
                        )
                    }
                    "video" -> {
                        return ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id.toLong()
                        )
                    }
                    "audio" -> {
                        return ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toLong()
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.w("转换 Downloads 文档 URI 失败: ${e.message}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryUriFromMediaStore(context: Context, fileName: String): Uri? {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY).buildUpon()
                        .appendPath(id.toString())
                        .build()
                } else {
                    null
                }
            }
        }.onFailure {
            AppLogger.e("查询 MediaStore 失败: ${it.message}")
            null
        }.getOrNull()
    }

    fun deleteFile(context: Context, task: Task) {
        val uri = fileUriOf(context, task) ?: return

        runCatching {
            if (uri.authority == MediaStore.AUTHORITY) {
                context.contentResolver.delete(uri, null, null)
            } else {
                DocumentFile.fromSingleUri(context, uri)?.delete()
            }
            AppLogger.i("Deleted file: $uri")
        }.onFailure {
            AppLogger.w("Failed to delete file: $uri", it)
        }
    }

    private fun fileUriOf(context: Context, task: Task): Uri? {
        task.fileUri?.let { return Uri.parse(it) }
        val tree = task.saveTreeUri ?: return null
        val name = task.zipName ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(tree))?.findFile(name)?.uri
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun downloadsFileExists(context: Context, name: String): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf(name)
        var exists = false
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            exists = cursor.count > 0
        }
        return exists
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findExistingDownloadsFile(context: Context, name: String): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf(name)
        var uri: String? = null
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                uri = ContentUris.withAppendedId(collection, id).toString()
            }
        }
        return uri
    }
}
