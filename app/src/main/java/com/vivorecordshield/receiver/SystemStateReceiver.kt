package com.vivorecordshield.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.service.ServiceRecoveryManager
import com.vivorecordshield.wakelock.ShieldWakeLockManager

/**
 * SystemStateReceiver
 * Heartbeat receiver listening for power plug/unplug and screen unlock events.
 */
class SystemStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        ShieldWakeLockManager.acquireBriefWakeLock(context, 3000L)
        try {
            DebugLogger.log(Tag.SHIELD, "SystemStateReceiver event received: action=$action")
            when (action) {
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_USER_PRESENT -> {
                    ServiceRecoveryManager.startPrimaryService(context, "SYSTEM_STATE_$action")
                }
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Error in SystemStateReceiver", e)
        } finally {
            ShieldWakeLockManager.releaseWakeLock()
        }
    }
}
