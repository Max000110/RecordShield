package com.vivorecordshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.metrics.ShieldMetricsManager
import com.vivorecordshield.wakelock.ShieldWakeLockManager

/**
 * BootReceiver
 * Direct-Boot aware boot receiver handling device reboot, package update, and user unlock events.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        ShieldWakeLockManager.acquireBriefWakeLock(context, 10000L)
        try {
            val action = intent?.action ?: return
            DebugLogger.log(Tag.BOOT, "BootReceiver event received: action=$action")

            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    ShieldMetricsManager.incrementBootCount(context)
                    DebugLogger.log(Tag.BOOT, "Device boot completed -> initializing RecordShield")
                    ServiceRecoveryManager.startPrimaryService(context, "BOOT_COMPLETED")
                }
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    DebugLogger.log(Tag.BOOT, "App package updated/replaced -> restoring RecordShield")
                    ServiceRecoveryManager.startPrimaryService(context, "PACKAGE_REPLACED")
                }
                Intent.ACTION_USER_UNLOCKED -> {
                    DebugLogger.log(Tag.BOOT, "User unlocked device -> restoring RecordShield")
                    ServiceRecoveryManager.startPrimaryService(context, "USER_UNLOCKED")
                }
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.BOOT, "Error handling boot event", e)
        } finally {
            ShieldWakeLockManager.releaseWakeLock()
        }
    }
}
