package com.leapmotor.translator

import android.app.Application
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class TranslatorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Log to Android logcat
                Log.e("TranslatorCrash", "Fatal exception", throwable)
                
                // Write stack trace to file
                val crashFile = File(filesDir, "crash_log.txt")
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                crashFile.writeText(sw.toString())
                
                // Also write to a cumulative log for history
                val historyFile = File(filesDir, "crash_history.log")
                historyFile.appendText("\n--- Crash at ${java.util.Date()} ---\n${sw.toString()}\n")
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Pass to default handler (which usually crashes the app)
                // or kill process ourselves
                defaultHandler?.uncaughtException(thread, throwable) ?: run {
                    Process.killProcess(Process.myPid())
                    exitProcess(1)
                }
            }
        }
    }
}
