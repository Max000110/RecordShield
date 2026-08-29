package com.vivorecordshield.watchdog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag

/**
 * SentinelAlarmWatchdog
 * AlarmManager RTC_WAKEUP watchdog scheduler for RecordShield.
 */
object SentinelAlarmWatchdog {

    private const val REQUEST_CODE = 8002

    fun scheduleNextAlarmWatchdog(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, AlarmWatchdogReceiver::class.java).apply {
                action = AlarmWatchdogReceiver.ACTION_ALARM_WATCHDOG_TICK
            }

            val pendingIntent = PendingIntent.getBroadcast(
                ctx,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMs = SystemClock.elapsedRealtime() + ShieldConfig.ALARM_WATCHDOG_INTERVAL_MS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pendingIntent)
                DebugLogger.log(Tag.SHIELD, "Exact AlarmManager watchdog scheduled: intervalMs=${ShieldConfig.ALARM_WATCHDOG_INTERVAL_MS}")
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, pendingIntent)
                DebugLogger.log(Tag.SHIELD, "Inexact AlarmManager watchdog scheduled: intervalMs=${ShieldConfig.ALARM_WATCHDOG_INTERVAL_MS}")
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Failed to schedule AlarmManager watchdog", e)
        }
    }

    fun cancelAlarmWatchdog(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, AlarmWatchdogReceiver::class.java).apply {
                action = AlarmWatchdogReceiver.ACTION_ALARM_WATCHDOG_TICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                ctx,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { am.cancel(it) }
            DebugLogger.log(Tag.SHIELD, "AlarmManager watchdog cancelled")
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Error cancelling AlarmManager watchdog", e)
        }
    }
}
