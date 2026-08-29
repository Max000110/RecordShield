package com.vivorecordshield.metrics

import android.content.Context
import android.content.SharedPreferences
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag

/**
 * ShieldMetricsManager
 * Telemetry and persistent failure counter store for RecordShield.
 */
object ShieldMetricsManager {

    private const val PREFS_NAME = "shield_metrics_prefs"
    private const val KEY_RESTART_COUNT = "restart_count"
    private const val KEY_CRASH_COUNT = "crash_count"
    private const val KEY_KILL_COUNT = "kill_count"
    private const val KEY_RECOVERY_COUNT = "recovery_count"
    private const val KEY_FAILURE_COUNTER = "failure_counter"
    private const val KEY_LAST_START_TIME = "last_start_time"
    private const val KEY_LAST_RECOVERY_TIME = "last_recovery_time"
    private const val KEY_BOOT_COUNT = "boot_count"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun incrementRestartCount(ctx: Context): Int {
        val count = prefs(ctx).getInt(KEY_RESTART_COUNT, 0) + 1
        prefs(ctx).edit().putInt(KEY_RESTART_COUNT, count).apply()
        DebugLogger.log(Tag.SHIELD, "Restart count incremented: $count")
        return count
    }

    fun getRestartCount(ctx: Context): Int = prefs(ctx).getInt(KEY_RESTART_COUNT, 0)

    fun incrementCrashCount(ctx: Context): Int {
        val count = prefs(ctx).getInt(KEY_CRASH_COUNT, 0) + 1
        prefs(ctx).edit().putInt(KEY_CRASH_COUNT, count).apply()
        DebugLogger.warn(Tag.SHIELD, "Crash count incremented: $count")
        return count
    }

    fun getCrashCount(ctx: Context): Int = prefs(ctx).getInt(KEY_CRASH_COUNT, 0)

    fun incrementKillCount(ctx: Context): Int {
        val count = prefs(ctx).getInt(KEY_KILL_COUNT, 0) + 1
        prefs(ctx).edit().putInt(KEY_KILL_COUNT, count).apply()
        DebugLogger.warn(Tag.SHIELD, "Kill count incremented: $count")
        return count
    }

    fun getKillCount(ctx: Context): Int = prefs(ctx).getInt(KEY_KILL_COUNT, 0)

    fun incrementRecoveryCount(ctx: Context): Int {
        val count = prefs(ctx).getInt(KEY_RECOVERY_COUNT, 0) + 1
        val now = System.currentTimeMillis()
        prefs(ctx).edit()
            .putInt(KEY_RECOVERY_COUNT, count)
            .putLong(KEY_LAST_RECOVERY_TIME, now)
            .apply()
        DebugLogger.log(Tag.SHIELD, "FULL RECOVERY MODE executed: recoveryCount=$count")
        return count
    }

    fun getRecoveryCount(ctx: Context): Int = prefs(ctx).getInt(KEY_RECOVERY_COUNT, 0)

    fun incrementFailureCounter(ctx: Context): Int {
        val current = prefs(ctx).getInt(KEY_FAILURE_COUNTER, 0) + 1
        prefs(ctx).edit().putInt(KEY_FAILURE_COUNTER, current).apply()
        DebugLogger.warn(Tag.SHIELD, "Failure counter: $current / ${ShieldConfig.MAX_CONSECUTIVE_FAILURES_BEFORE_FULL_RECOVERY}")
        return current
    }

    fun getFailureCounter(ctx: Context): Int = prefs(ctx).getInt(KEY_FAILURE_COUNTER, 0)

    fun resetFailureCounter(ctx: Context) {
        prefs(ctx).edit().putInt(KEY_FAILURE_COUNTER, 0).apply()
        DebugLogger.log(Tag.SHIELD, "Failure counter reset to 0")
    }

    fun incrementBootCount(ctx: Context): Int {
        val count = prefs(ctx).getInt(KEY_BOOT_COUNT, 0) + 1
        prefs(ctx).edit().putInt(KEY_BOOT_COUNT, count).apply()
        DebugLogger.log(Tag.BOOT, "Boot count incremented: $count")
        return count
    }

    fun getBootCount(ctx: Context): Int = prefs(ctx).getInt(KEY_BOOT_COUNT, 0)

    fun updateLastStartTime(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_LAST_START_TIME, System.currentTimeMillis()).apply()
    }

    fun getLastStartTime(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_START_TIME, 0L)

    fun getLastRecoveryTime(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_RECOVERY_TIME, 0L)
}
