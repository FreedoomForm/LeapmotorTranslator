package com.leapmotor.translator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot receiver to auto-start the translation service after device reboot.
 * 
 * Note: The AccessibilityService must be manually enabled by the user first.
 * This receiver simply ensures the service is ready once permissions are granted.
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Boot completed, checking accessibility service status")
                // The AccessibilityService will be started automatically by the system
                // if it was enabled before reboot.
                // We just log for debugging purposes.
            }
        }
    }
}
