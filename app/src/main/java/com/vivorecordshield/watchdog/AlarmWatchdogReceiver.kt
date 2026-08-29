package com.vivorecordshield.watchdog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.service.ServiceRecoveryManager
import com.vivorecordshield.wakelock.ShieldWakeLockManager

/**
 * AlarmWatchdogReceiver
 * Receives AlarmManager watchdog ticks and executes service recovery.
 */
class AlarmWatchdogReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_WATCHDOG_TICK = "com.vivorecordshield.ALARM_WATCHDOG_TICK"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        ShieldWakeLockManager.acquireBriefWakeLock(context, 5000L)
        try {
            val action = intent?.action
            DebugLogger.log(Tag.SHIELD, "AlarmWatchdogReceiver tick received: action=$action")

            if (action == ACTION_ALARM_WATCHDOG_TICK) {
                ServiceRecoveryManager.startPrimaryService(context, "ALARM_WATCHDOG")
            }

            SentinelAlarmWatchdog.scheduleNextAlarmWatchdog(context)

        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Error in AlarmWatchdogReceiver", e)
        } finally {
            ShieldWakeLockManager.releaseWakeLock()
        }
    }
}
