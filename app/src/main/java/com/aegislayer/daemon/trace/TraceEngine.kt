package com.aegislayer.daemon.trace

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.FileWriter

object TraceEngine {

    private const val LOG_FILE_NAME = "aegis_trace.log"
    private const val MAX_FILE_LINES = 500
    private var logFile: File? = null

    private val _liveEntries = MutableSharedFlow<TraceEntry>(extraBufferCapacity = 50)
    val liveEntries = _liveEntries.asSharedFlow()

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        Log.d("AegisLayer", "TraceEngine: initialized at ${logFile?.absolutePath}")
    }

    fun log(level: TraceLevel, tag: String, message: String) {
        val entry = TraceEntry(System.currentTimeMillis(), level, tag, message)
        Log.d("AegisLayer", entry.toString())
        writeToFile(entry)
        _liveEntries.tryEmit(entry)
    }

    fun readRecent(n: Int = 50): List<TraceEntry> {
        val file = logFile ?: return emptyList()
        if (!file.exists()) return emptyList()

        return try {
            file.readLines()
                .takeLast(n)
                .mapNotNull { parseLine(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeToFile(entry: TraceEntry) {
        val file = logFile ?: return
        try {
            // Trim file if too large
            if (file.exists()) {
                val lines = file.readLines()
                if (lines.size >= MAX_FILE_LINES) {
                    file.writeText(lines.takeLast(MAX_FILE_LINES - 1).joinToString("\n") + "\n")
                }
            }
            FileWriter(file, true).use { it.appendLine(entry.toString()) }
        } catch (e: Exception) {
            Log.e("AegisLayer", "TraceEngine: Failed to write log - ${e.message}")
        }
    }

    private fun parseLine(line: String): TraceEntry? {
        // Format: [HH:mm:ss][LEVEL][TAG] message
        return try {
            val levelMatch = Regex("""\[(\d{2}:\d{2}:\d{2})\]\[(\w+)\]\[(\w+)\] (.+)""")
                .matchEntire(line) ?: return null
            val (_, levelStr, tag, message) = levelMatch.destructured
            val level = TraceLevel.valueOf(levelStr)
            TraceEntry(System.currentTimeMillis(), level, tag, message)
        } catch (e: Exception) {
            null
        }
    }
}
