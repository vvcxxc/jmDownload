package com.jmdl.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object AppLogger {
    private const val TAG = "JMDownload"
    private const val MAX_LINES = 400
    private const val MAX_LOG_AGE_DAYS = 7
    private val entries = ConcurrentLinkedQueue<String>()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val dateParser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var logFile: File? = null

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
        val cutoffDate = Date(System.currentTimeMillis() - MAX_LOG_AGE_DAYS * 24 * 60 * 60 * 1000L)
        return lines.filter { line ->
            try {
                val dateStr = line.substring(0, 10)
                val logDate = dateParser.parse(dateStr)
                logDate != null && logDate.after(cutoffDate)
            } catch (_: Exception) {
                true
            }
        }
    }

    fun i(message: String) {
        Log.i(TAG, message)
        writeToFile(message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        writeToFile("[WARN] $fullMsg")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val fullMsg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        writeToFile("[ERROR] $fullMsg")
    }

    fun logProgress(taskId: String, done: Int, total: Int, phase: String = "download") {
        i("[$phase] task=$taskId done=$done total=$total at=${timestamp()}")
    }

    fun snapshot(): List<String> {
        logFile?.let { file ->
            if (file.exists()) {
                return runCatching { file.readLines().takeLast(MAX_LINES).reversed() }.getOrDefault(entries.toList().reversed())
            }
        }
        return entries.toList().reversed()
    }

    fun clear() {
        entries.clear()
        logFile?.delete()
        i("Log cleared")
    }

    private fun writeToFile(message: String) {
        val line = "${formatter.format(Date())} $message"
        entries.add(line)
        while (entries.size > MAX_LINES) entries.remove()
        logFile?.let { file ->
            runCatching {
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
            }
        }
    }

    private fun timestamp(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
}