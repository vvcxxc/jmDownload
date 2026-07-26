package com.jmdl.download

import com.jmdl.util.AppLogger
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Zipper {

    fun writeZip(workDir: File, out: OutputStream) {
        val files = workDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(workDir).invariantSeparatorsPath }
            .toList()

        if (files.isEmpty()) {
            val error = "没有可打包的图片: ${workDir.path}"
            AppLogger.e(error)
            throw IllegalStateException(error)
        }

        AppLogger.i("Zipping ${files.size} files from: ${workDir.path}")

        ZipOutputStream(out.buffered()).use { zos ->
            zos.setLevel(0)
            val buf = ByteArray(64 * 1024)

            for ((index, f) in files.withIndex()) {
                try {
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

                    if ((index + 1) % 100 == 0 || index == files.size - 1) {
                        AppLogger.i("Zipping progress: ${index + 1}/${files.size}")
                    }
                } catch (e: Exception) {
                    AppLogger.e("Failed to zip file: ${f.path}", e)
                    throw e
                }
            }
        }

        AppLogger.i("Zipping completed")
    }
}