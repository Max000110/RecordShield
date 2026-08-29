package com.vivorecordshield.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.wakelock.ShieldWakeLockManager

/**
 * EmergencyRestartReceiver
 * Handles alarm-triggered emergency restarts after process termination or task removal.
 */
class EmergencyRestartReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EMERGENCY_RESTART = "com.vivorecordshield.EMERGENCY_RESTART"

        fun scheduleEmergencyRestart(ctx: Context, delayMs: Long = 3000L) {
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(ctx, EmergencyRestartReceiver::class.java).apply {
                    action = ACTION_EMERGENCY_RESTART
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    ctx,
                    9002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAt = SystemClock.elapsedRealtime() + delayMs
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                DebugLogger.log(Tag.SHIELD, "Scheduled emergency restart alarm: delayMs=$delayMs")
            } catch (e: Exception) {
                DebugLogger.error(Tag.SHIELD, "Failed to schedule emergency restart alarm", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        ShieldWakeLockManager.acquireBriefWakeLock(context, 5000L)
        try {
            val action = intent?.action
            DebugLogger.warn(Tag.SHIELD, "EmergencyRestartReceiver triggered: action=$action")

            if (action == ACTION_EMERGENCY_RESTART || action == Intent.ACTION_RUN) {
                ServiceRecoveryManager.startPrimaryService(context, "EMERGENCY_RESTART_RECEIVER")
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Error in EmergencyRestartReceiver", e)
        } finally {
            ShieldWakeLockManager.releaseWakeLock()
        }
    }
}
