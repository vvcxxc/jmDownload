package com.jmdl.download

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Zipper {

    /** Streams every file under [workDir] (recursive, path-sorted) into [out] as a zip. */
    fun writeZip(workDir: File, out: OutputStream) {
        val files = workDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(workDir).invariantSeparatorsPath }
            .toList()
        if (files.isEmpty()) throw IllegalStateException("没有可打包的图片")

        ZipOutputStream(out.buffered()).use { zos ->
            zos.setLevel(0) // images are already compressed
            val buf = ByteArray(64 * 1024)
            for (f in files) {
                zos.putNextEntry(ZipEntry(f.relativeTo(workDir).invariantSeparatorsPath))
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
}
