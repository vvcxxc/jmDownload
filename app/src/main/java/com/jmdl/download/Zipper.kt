package com.jmdl.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Zipper {

    /**
     * Zips every file under [workDir] (recursive, path-sorted) into a new file [zipName]
     * inside the SAF tree [treeUri]. Returns the actual file name written (de-duplicated on
     * collision). Throws with a user-facing message on failure.
     */
    fun zipToTree(context: Context, workDir: File, treeUri: Uri, zipName: String): String {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("保存目录不可用，请重新选择")
        if (!tree.canWrite()) throw IllegalStateException("没有保存目录的写入权限，请重新选择")

        val files = workDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(workDir).invariantSeparatorsPath }
            .toList()
        if (files.isEmpty()) throw IllegalStateException("没有可打包的图片")

        val finalName = uniqueName(tree, zipName)
        val doc = tree.createFile("application/zip", finalName)
            ?: throw IllegalStateException("无法在保存目录创建文件")

        try {
            val out = context.contentResolver.openOutputStream(doc.uri)
                ?: throw IllegalStateException("无法写入保存目录")
            out.use { os ->
                ZipOutputStream(os.buffered()).use { zos ->
                    zos.setLevel(0) // images are already compressed
                    val buf = ByteArray(64 * 1024)
                    for (f in files) {
                        val entryName = f.relativeTo(workDir).invariantSeparatorsPath
                        zos.putNextEntry(ZipEntry(entryName))
                        f.inputStream().use { ins ->
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                zos.write(buf, 0, n)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            runCatching { doc.delete() }
            throw e
        }
        return finalName
    }

    private fun uniqueName(tree: DocumentFile, name: String): String {
        if (tree.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot >= 0) name.substring(0, dot) else name
        val ext = if (dot >= 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (tree.findFile(candidate) == null) return candidate
            i++
        }
    }
}
