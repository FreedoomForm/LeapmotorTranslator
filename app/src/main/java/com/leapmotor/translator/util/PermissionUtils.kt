package com.leapmotor.translator.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object PermissionUtils {

    /**
     * Check if the device is a Xiaomi device (MIUI).
     */
    fun isXiaomi(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
               Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
               Build.MANUFACTURER.equals("Poco", ignoreCase = true)
    }
    
    /**
     * Alias for isXiaomi for compatibility.
     */
    fun isXiaomiDevice(): Boolean = isXiaomi()

    /**
     * Open MIUI Autostart permission settings.
     */
    fun openMiuiAutostart(context: Context) {
        try {
            val intent = Intent()
            intent.component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                 // Fallback for some versions
                val intent = Intent()
                intent.component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.autostart.AutoStartManagementActivity"
                )
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Fallback to app details
                openAppDetails(context)
            }
        }
    }

    /**
     * Open MIUI "Display pop-up windows while running in the background" permission settings.
     * This is often hidden under "Other permissions" or "Permissions".
     */
    fun openMiuiPopupPermission(context: Context) {
        try {
            // Direct intent to "Other permissions" page for this app
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            intent.putExtra("extra_pkgname", context.packageName)
            context.startActivity(intent)
        } catch (e: Exception) {
             try {
                // Alternative intent
                 val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                 intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                 intent.putExtra("extra_pkgname", context.packageName)
                 context.startActivity(intent)
             } catch (ex: Exception) {
                 openAppDetails(context)
             }
        }
    }
    
    /**
     * Open MIUI permission settings - main entry point.
     */
    fun openMIUIPermissionSettings(context: Context) {
        openMiuiPopupPermission(context)
    }

    /**
     * Open standard App Details page as a fallback.
     */
    fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", context.packageName, null)
        context.startActivity(intent)
    }
}
