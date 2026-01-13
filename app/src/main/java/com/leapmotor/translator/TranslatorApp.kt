package com.leapmotor.translator

import android.app.Application
import android.os.Process
import android.os.StrictMode
import com.leapmotor.translator.core.AppPreferences
import com.leapmotor.translator.core.Logger
import com.leapmotor.translator.filter.KalmanFilter2DPool
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

/**
 * Application class for LeapmotorTranslator.
 * 
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 * 
 * Responsibilities:
 * - Initialize Hilt DI container
 * - Initialize core infrastructure
 * - Set up crash handling
 * - Configure StrictMode for debug builds
 * - Pre-warm object pools
 */
@HiltAndroidApp
class TranslatorApp : Application() {
    
    companion object {
        private const val TAG = "TranslatorApp"
        
        @Volatile
        private var instance: TranslatorApp? = null
        
        fun getInstance(): TranslatorApp? = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize logging first
        initializeLogging()
        
        Logger.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Logger.i(TAG, "LeapmotorTranslator Starting...")
        Logger.i(TAG, "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Logger.i(TAG, "Build Type: ${BuildConfig.BUILD_TYPE}")
        Logger.i(TAG, "Hilt DI: Enabled")
        Logger.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Set up crash handling
        setupCrashHandler()
        
        // Initialize preferences
        initializePreferences()
        
        // Pre-warm pools for better initial performance
        preWarmPools()
        
        // Enable StrictMode in debug builds
        if (BuildConfig.DEBUG && BuildConfig.ENABLE_STRICT_MODE) {
            enableStrictMode()
        }
        
        // Update session timestamp
        AppPreferences.lastSessionTimestamp.value = System.currentTimeMillis()
        
        Logger.i(TAG, "Application initialized successfully")
    }
    
    private fun initializeLogging() {
        Logger.minLevel = if (BuildConfig.DEBUG) {
            Logger.Level.VERBOSE
        } else {
            Logger.Level.INFO
        }
        Logger.includeTimestamp = true
        Logger.logToLogcat = true
    }
    
    private fun initializePreferences() {
        AppPreferences.init(this)
        Logger.d(TAG, "Preferences initialized")
    }
    
    private fun preWarmPools() {
        KalmanFilter2DPool.prefill(32)
        Logger.d(TAG, "Object pools pre-warmed")
    }
    
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
        
        Logger.d(TAG, "StrictMode enabled")
    }
    
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Logger.wtf(TAG, "FATAL EXCEPTION in thread ${thread.name}", throwable)
                saveCrashLog(throwable)
                appendToCrashHistory(throwable)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                defaultHandler?.uncaughtException(thread, throwable) ?: run {
                    Process.killProcess(Process.myPid())
                    exitProcess(1)
                }
            }
        }
        
        Logger.d(TAG, "Crash handler installed")
    }
    
    private fun saveCrashLog(throwable: Throwable) {
        try {
            val crashFile = File(filesDir, "crash_log.txt")
            val stackTrace = StringWriter().also { sw ->
                PrintWriter(sw).use { pw -> throwable.printStackTrace(pw) }
            }.toString()
            
            val crashReport = buildString {
                appendLine("═══════════════════════════════════════")
                appendLine("CRASH REPORT")
                appendLine("═══════════════════════════════════════")
                appendLine()
                appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android: ${android.os.Build.VERSION.SDK_INT}")
                appendLine()
                appendLine("═══════════════════════════════════════")
                appendLine("STACK TRACE")
                appendLine("═══════════════════════════════════════")
                appendLine()
                append(stackTrace)
                appendLine()
                appendLine("═══════════════════════════════════════")
                appendLine("RECENT LOGS")
                appendLine("═══════════════════════════════════════")
                appendLine()
                Logger.getLogEntries().takeLast(20).forEach {
                    appendLine(it.format())
                }
            }
            
            crashFile.writeText(crashReport)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun appendToCrashHistory(throwable: Throwable) {
        try {
            val historyFile = File(filesDir, "crash_history.log")
            val stackTrace = StringWriter().also { sw ->
                PrintWriter(sw).use { pw -> throwable.printStackTrace(pw) }
            }.toString()
            
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = "\n\n══════ Crash at $timestamp ══════\n$stackTrace"
            
            historyFile.appendText(entry)
            
            if (historyFile.length() > 100_000) {
                val content = historyFile.readText()
                val trimmed = content.takeLast(50_000)
                historyFile.writeText("... (truncated)\n$trimmed")
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        KalmanFilter2DPool.clear()
        Logger.i(TAG, "Application terminated")
        instance = null
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Logger.w(TAG, "Low memory warning - clearing caches")
        KalmanFilter2DPool.clear()
        System.gc()
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        val levelName = when (level) {
            TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            else -> "LEVEL_$level"
        }
        
        Logger.d(TAG, "Trim memory: $levelName")
        
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            KalmanFilter2DPool.clear()
        }
    }
}
