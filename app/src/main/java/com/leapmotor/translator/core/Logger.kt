package com.leapmotor.translator.core

import android.util.Log
import com.leapmotor.translator.BuildConfig
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Professional logging infrastructure with multiple log levels,
 * file persistence, and performance tracking.
 * 
 * Features:
 * - Thread-safe logging
 * - Log level filtering
 * - In-memory log buffer for debugging
 * - Performance timing utilities
 * - Structured logging support
 */
object Logger {
    
    // ============================================================================
    // LOG LEVELS
    // ============================================================================
    
    enum class Level(val priority: Int, val tag: String) {
        VERBOSE(Log.VERBOSE, "V"),
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARN(Log.WARN, "W"),
        ERROR(Log.ERROR, "E"),
        ASSERT(Log.ASSERT, "WTF")
    }
    
    // ============================================================================
    // CONFIGURATION
    // ============================================================================
    
    /**
     * Minimum log level to output. Lower levels are ignored.
     */
    var minLevel: Level = if (BuildConfig.DEBUG) Level.VERBOSE else Level.INFO
    
    /**
     * Maximum number of log entries to keep in memory.
     */
    var maxBufferSize: Int = 500
    
    /**
     * Whether to include timestamps in log messages.
     */
    var includeTimestamp: Boolean = true
    
    /**
     * Whether to log to Android LogCat.
     */
    var logToLogcat: Boolean = true
    
    // ============================================================================
    // INTERNAL STATE
    // ============================================================================
    
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val activeTimers = mutableMapOf<String, Long>()
    
    /**
     * Represents a single log entry.
     */
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null,
        val extras: Map<String, Any>? = null
    ) {
        fun format(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            val error = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
            val extra = extras?.entries?.joinToString(", ") { "${it.key}=${it.value}" }?.let { " [$it]" } ?: ""
            return "[$time] [${level.tag}/$tag]$extra $message$error"
        }
    }
    
    // ============================================================================
    // LOGGING METHODS
    // ============================================================================
    
    /**
     * Log a verbose message.
     */
    fun v(tag: String, message: String, extras: Map<String, Any>? = null) = log(Level.VERBOSE, tag, message, null, extras)
    
    /**
     * Log a debug message.
     */
    fun d(tag: String, message: String, extras: Map<String, Any>? = null) = log(Level.DEBUG, tag, message, null, extras)
    
    /**
     * Log an info message.
     */
    fun i(tag: String, message: String, extras: Map<String, Any>? = null) = log(Level.INFO, tag, message, null, extras)
    
    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null, extras: Map<String, Any>? = null) = 
        log(Level.WARN, tag, message, throwable, extras)
    
    /**
     * Log an error message.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null, extras: Map<String, Any>? = null) = 
        log(Level.ERROR, tag, message, throwable, extras)
    
    /**
     * Log a "What a Terrible Failure" message (always logged).
     */
    fun wtf(tag: String, message: String, throwable: Throwable? = null) = 
        log(Level.ASSERT, tag, message, throwable, null)
    
    /**
     * Core logging method.
     */
    private fun log(level: Level, tag: String, message: String, throwable: Throwable?, extras: Map<String, Any>?) {
        if (level.priority < minLevel.priority) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            extras = extras
        )
        
        // Add to buffer
        synchronized(logBuffer) {
            logBuffer.add(entry)
            while (logBuffer.size > maxBufferSize) {
                logBuffer.poll()
            }
        }
        
        // Log to LogCat
        if (logToLogcat) {
            val formattedMessage = buildString {
                if (extras != null) {
                    append("[")
                    append(extras.entries.joinToString(", ") { "${it.key}=${it.value}" })
                    append("] ")
                }
                append(message)
            }
            
            when (level) {
                Level.VERBOSE -> Log.v(tag, formattedMessage, throwable)
                Level.DEBUG -> Log.d(tag, formattedMessage, throwable)
                Level.INFO -> Log.i(tag, formattedMessage, throwable)
                Level.WARN -> Log.w(tag, formattedMessage, throwable)
                Level.ERROR -> Log.e(tag, formattedMessage, throwable)
                Level.ASSERT -> Log.wtf(tag, formattedMessage, throwable)
            }
        }
    }
    
    // ============================================================================
    // PERFORMANCE TIMING
    // ============================================================================
    
    /**
     * Start a timer with the given name.
     */
    fun startTimer(name: String) {
        activeTimers[name] = System.nanoTime()
    }
    
    /**
     * Stop a timer and log the elapsed time.
     * 
     * @return Elapsed time in milliseconds, or -1 if timer not found
     */
    fun stopTimer(name: String, tag: String = "Performance"): Long {
        val startTime = activeTimers.remove(name) ?: return -1
        val elapsedNanos = System.nanoTime() - startTime
        val elapsedMs = elapsedNanos / 1_000_000
        d(tag, "⏱ $name completed in ${elapsedMs}ms (${elapsedNanos}ns)")
        return elapsedMs
    }
    
    /**
     * Measure the execution time of a block.
     */
    inline fun <T> timed(tag: String, name: String, block: () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            d(tag, "⏱ $name: ${elapsed}ms")
        }
    }
    
    /**
     * Measure the execution time of a suspend block.
     */
    suspend inline fun <T> timedSuspend(tag: String, name: String, crossinline block: suspend () -> T): T {
        val start = System.nanoTime()
        return try {
            block()
        } finally {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            d(tag, "⏱ $name: ${elapsed}ms")
        }
    }
    
    // ============================================================================
    // BUFFER ACCESS
    // ============================================================================
    
    /**
     * Get all logged entries.
     */
    fun getLogEntries(): List<LogEntry> = logBuffer.toList()
    
    /**
     * Get log entries as formatted strings.
     */
    fun getFormattedLogs(): List<String> = logBuffer.map { it.format() }
    
    /**
     * Get log entries filtered by level.
     */
    fun getLogEntries(minLevel: Level): List<LogEntry> = 
        logBuffer.filter { it.level.priority >= minLevel.priority }
    
    /**
     * Get log entries filtered by tag.
     */
    fun getLogEntries(tag: String): List<LogEntry> = 
        logBuffer.filter { it.tag == tag }
    
    /**
     * Clear the log buffer.
     */
    fun clearBuffer() {
        logBuffer.clear()
    }
    
    /**
     * Export logs as a single string (useful for crash reports).
     */
    fun exportLogs(): String = getFormattedLogs().joinToString("\n")
}

// ============================================================================
// INLINE TAG HELPERS
// ============================================================================

/**
 * Extension to get a logger tag from the class name.
 */
inline val <reified T : Any> T.TAG: String
    get() = T::class.java.simpleName.take(23)  // 23 is Android's LogCat tag limit

/**
 * Convenience extension for logging within a class.
 */
inline fun <reified T : Any> T.logD(message: String, extras: Map<String, Any>? = null) {
    Logger.d(TAG, message, extras)
}

inline fun <reified T : Any> T.logE(message: String, throwable: Throwable? = null) {
    Logger.e(TAG, message, throwable)
}

inline fun <reified T : Any> T.logI(message: String) {
    Logger.i(TAG, message)
}

inline fun <reified T : Any> T.logW(message: String, throwable: Throwable? = null) {
    Logger.w(TAG, message, throwable)
}
