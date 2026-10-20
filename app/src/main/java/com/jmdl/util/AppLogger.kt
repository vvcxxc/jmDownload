package com.jmdl.util

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

object AppLogger {
    private const val TAG = "JMDownload"
    private const val MAX_LINES = 400
    private const val MAX_LOG_AGE_DAYS = 7L
    private const val TRIM_EVERY = 200

    private val entries = ConcurrentLinkedQueue<String>()
    private val fileTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // All disk writes are serialized on one daemon thread; callers never block on I/O.
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AppLogger").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null
    private var writesSinceTrim = 0

    fun init(context: Context) {
        logFile = File(context.filesDir, "app.log")
        logFile?.let { file ->
            if (file.exists()) {
                runCatching {
                    val lines = file.readLines()
                    val filtered = filterOldLogs(lines)
                    filtered.takeLast(MAX_LINES).forEach { entries.add(it) }
                    if (filtered.size != lines.size) {
                        file.writeText(filtered.joinToString("\n"))
                        i("Cleaned ${lines.size - filtered.size} old log entries")
                    }
                }
            }
        }
        i("AppLogger initialized")
    }

    private fun filterOldLogs(lines: List<String>): List<String> {
        val cutoff = LocalDate.now().minusDays(MAX_LOG_AGE_DAYS)
        return lines.filter { line ->
            try {
                !LocalDate.parse(line.substring(0, 10)).isBefore(cutoff)
            } catch (_: Exception) {
                true
            }
        }
    }

    fun i(message: String) {
        Log.i(TAG, message)
        write(message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        write("[WARN] $fullMsg")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        write("[ERROR] $fullMsg")
    }

    fun snapshot(): List<String> = entries.toList().reversed()

    fun clear() {
        entries.clear()
        val file = logFile
        writer.execute {
            writesSinceTrim = 0
            runCatching { file?.delete() }
        }
        // Enqueued after the delete task, so the writer recreates the file in order.
        i("Log cleared")
    }

    private fun write(message: String) {
        val line = "${LocalDateTime.now().format(fileTime)} $message"
        entries.add(line)
        while (entries.size > MAX_LINES) entries.remove()
        val file = logFile ?: return
        writer.execute { appendLine(file, line) }
    }

    private fun appendLine(file: File, line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
            // The in-memory queue is capped, but appending alone would let the file
            // grow forever (progress logs are written several times per second).
            writesSinceTrim++
            if (writesSinceTrim >= TRIM_EVERY) {
                writesSinceTrim = 0
                file.writeText(entries.joinToString("\n") + "\n")
            }
        }
    }
}