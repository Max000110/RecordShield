package com.vivorecordshield.oem

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag

/**
 * VivoOptimizationHelper
 * Diagnostics and intent launchers for Vivo V40 (Funtouch OS 16).
 */
object VivoOptimizationHelper {

    data class DiagnosticStatus(
        val isManufacturerVivo: Boolean,
        val isIgnoringBatteryOptimizations: Boolean,
        val areNotificationsEnabled: Boolean,
        val autoStartIntentAvailable: Boolean,
        val iManagerIntentAvailable: Boolean
    )

    fun isVivoDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("vivo") || brand.contains("vivo") || manufacturer.contains("iqoo")
    }

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun areNotificationsEnabled(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.areNotificationsEnabled()
    }

    fun getDiagnosticStatus(ctx: Context): DiagnosticStatus {
        return DiagnosticStatus(
            isManufacturerVivo = isVivoDevice(),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(ctx),
            areNotificationsEnabled = areNotificationsEnabled(ctx),
            autoStartIntentAvailable = canResolveIntent(ctx, getVivoAutoStartIntent()),
            iManagerIntentAvailable = canResolveIntent(ctx, getVivoHighPowerIntent())
        )
    }

    fun requestBatteryOptimizationExemption(ctx: Context) {
        try {
            if (!isIgnoringBatteryOptimizations(ctx)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                DebugLogger.log(Tag.SHIELD, "Requested battery optimization exemption")
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Failed to launch battery exemption request", e)
            openAppSettings(ctx)
        }
    }

    fun openVivoAutoStartSettings(ctx: Context): Boolean {
        return tryLaunchIntent(ctx, getVivoAutoStartIntent(), "Vivo Auto-Start Settings")
    }

    fun openVivoHighPowerSettings(ctx: Context): Boolean {
        return tryLaunchIntent(ctx, getVivoHighPowerIntent(), "Vivo iManager / High Power Usage")
    }

    fun openAppSettings(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            DebugLogger.log(Tag.SHIELD, "Opened App Details Settings")
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Failed to open application details settings", e)
        }
    }

    private fun getVivoAutoStartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun getVivoHighPowerIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.HighPowerConsumptionActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun canResolveIntent(ctx: Context, intent: Intent): Boolean {
        return try {
            intent.resolveActivity(ctx.packageManager) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun tryLaunchIntent(ctx: Context, intent: Intent, label: String): Boolean {
        return try {
            if (canResolveIntent(ctx, intent)) {
                ctx.startActivity(intent)
                DebugLogger.log(Tag.SHIELD, "Launched $label")
                true
            } else {
                DebugLogger.warn(Tag.SHIELD, "Intent for $label could not be resolved")
                openAppSettings(ctx)
                false
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Error launching $label", e)
            openAppSettings(ctx)
            false
        }
    }
}
