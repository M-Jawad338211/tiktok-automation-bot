package dev.malik.phonebot.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import dev.malik.phonebot.app
import dev.malik.phonebot.service.BotAccessibilityService

/** FR-11: the setup checks shown as red/green rows on the main screen. */
data class SetupStatus(
    val accessibility: Boolean,
    val postNotifications: Boolean,
    val batteryExempt: Boolean,
    /** "com.zhiliaoapp.musically 47.4.3", or null when TikTok is not installed. */
    val tiktok: String?,
) {
    companion object {
        fun read(context: Context): SetupStatus {
            val pm = context.packageManager
            val tiktok = context.app.config.current().packages.firstNotNullOfOrNull { pkg ->
                runCatching { "$pkg ${pm.getPackageInfo(pkg, 0).versionName}" }.getOrNull()
            }
            return SetupStatus(
                accessibility = isAccessibilityEnabled(context),
                postNotifications = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                batteryExempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
                tiktok = tiktok,
            )
        }

        private fun isAccessibilityEnabled(context: Context): Boolean {
            val ours = ComponentName(context, BotAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
        }
    }
}

object SetupIntents {
    fun accessibility() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun batteryExemption(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
}
