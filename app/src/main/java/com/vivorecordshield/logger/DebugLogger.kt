package com.vivorecordshield.logger

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingDeque

/**
 * Structured, thread-safe logger.
 * Writes to both logcat and a rolling in-memory + file log.
 * Tag format:  RS/<TAG>
 *
 * Usage:
 *   DebugLogger.log(Tag.OVERLAY, "created", "bounds=$bounds")
 *   DebugLogger.warn(Tag.RECORD_NODE, "node not found")
 *   DebugLogger.error(Tag.CALL_UI, "exception", ex)
 */
object DebugLogger {

    enum class Tag {
        CALL_UI,
        RECORD_NODE,
        OVERLAY,
        CALL,
        SHIELD,
        BOOT,
        CONFIG
    }

    private const val MAX_ENTRIES = 500
    private val entries = LinkedBlockingDeque<LogEntry>(MAX_ENTRIES)
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: Tag,
        val message: String,
        val extra: String? = null
    ) {
        override fun toString(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            return buildString {
                append("[$time] [$level] [${tag.name}] $message")
                if (extra != null) append(" | $extra")
            }
        }
    }

    fun init(cacheDir: File) {
        logFile = File(cacheDir, "recordshield.log")
    }

    fun log(tag: Tag, message: String, extra: String? = null) {
        record("I", tag, message, extra)
        Log.i("RS/${tag.name}", if (extra != null) "$message | $extra" else message)
    }

    fun warn(tag: Tag, message: String, extra: String? = null) {
        record("W", tag, message, extra)
        Log.w("RS/${tag.name}", if (extra != null) "$message | $extra" else message)
    }

    fun error(tag: Tag, message: String, throwable: Throwable? = null) {
        record("E", tag, message, throwable?.message)
        Log.e("RS/${tag.name}", message, throwable)
    }

    fun getRecentLogs(limit: Int = 100): List<LogEntry> {
        return entries.toList().takeLast(limit)
    }

    fun formatForDisplay(limit: Int = 60): String {
        return getRecentLogs(limit).joinToString("\n") { it.toString() }
    }

    private fun record(level: String, tag: Tag, message: String, extra: String?) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, extra)
        if (entries.size >= MAX_ENTRIES) entries.poll()
        entries.offer(entry)
        writeToFile(entry)
    }

    private fun writeToFile(entry: LogEntry) {
        try {
            logFile?.let { f ->
                if (f.length() > 2 * 1024 * 1024) f.delete() // rotate at 2MB
                FileWriter(f, true).use { w -> w.appendLine(entry.toString()) }
            }
        } catch (_: Exception) {}
    }
}
