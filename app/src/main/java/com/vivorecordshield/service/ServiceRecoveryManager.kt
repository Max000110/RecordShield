package com.vivorecordshield.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.metrics.ShieldMetricsManager
import com.vivorecordshield.watchdog.SentinelAlarmWatchdog
import com.vivorecordshield.watchdog.ShieldWatchdog

/**
 * ServiceRecoveryManager
 * Central recovery controller for process restarts, watchdog scheduling,
 * and 5-time failure threshold execution.
 */
object ServiceRecoveryManager {

    fun startPrimaryService(ctx: Context, sourceReason: String) {
        try {
            if (!ShieldConfig.isShieldEnabled(ctx)) {
                DebugLogger.warn(Tag.SHIELD, "Service start skipped: Shield disabled by user setting")
                return
            }

            DebugLogger.log(Tag.SHIELD, "Starting primary service: reason=$sourceReason")
            val intent = Intent(ctx, ShieldForegroundService::class.java).apply {
                action = ShieldForegroundService.ACTION_START
                putExtra("source_reason", sourceReason)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }

            scheduleAllWatchdogs(ctx)

        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Failed to start primary service: reason=$sourceReason", e)
            handleProcessTerminationDetected(ctx, "start_exception_${e.javaClass.simpleName}")
        }
    }

    fun scheduleAllWatchdogs(ctx: Context) {
        try {
            ShieldWatchdog.schedulePeriodicWatchdog(ctx)
            SentinelAlarmWatchdog.scheduleNextAlarmWatchdog(ctx)
            DebugLogger.log(Tag.SHIELD, "All recovery watchdogs scheduled")
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Error scheduling watchdogs", e)
        }
    }

    fun handleProcessTerminationDetected(ctx: Context, reason: String) {
        DebugLogger.warn(Tag.SHIELD, "Process or service termination detected: reason=$reason")

        val failureCount = ShieldMetricsManager.incrementFailureCounter(ctx)
        ShieldMetricsManager.incrementKillCount(ctx)

        EmergencyRestartReceiver.scheduleEmergencyRestart(ctx, delayMs = ShieldConfig.EMERGENCY_RESTART_DELAY_MS)
        ShieldWatchdog.enqueueImmediateWatchdog(ctx)

        if (failureCount >= ShieldConfig.MAX_CONSECUTIVE_FAILURES_BEFORE_FULL_RECOVERY) {
            triggerFullRecoveryMode(ctx, reason)
        }
    }

    fun triggerFullRecoveryMode(ctx: Context, triggerReason: String) {
        DebugLogger.warn(Tag.SHIELD, "FULL RECOVERY MODE TRIGGERED: consecutiveFailuresReached=5 trigger=$triggerReason")

        ShieldMetricsManager.incrementRecoveryCount(ctx)

        SentinelAlarmWatchdog.cancelAlarmWatchdog(ctx)
        ShieldWatchdog.cancelAllWatchdogs(ctx)

        ShieldWatchdog.enqueueImmediateWatchdog(ctx)
        ShieldWatchdog.schedulePeriodicWatchdog(ctx)
        SentinelAlarmWatchdog.scheduleNextAlarmWatchdog(ctx)

        startPrimaryService(ctx, "FULL_RECOVERY_MODE_TRIGGERED")
        ShieldMetricsManager.resetFailureCounter(ctx)

        DebugLogger.log(Tag.SHIELD, "FULL RECOVERY MODE completed successfully")
    }
}
