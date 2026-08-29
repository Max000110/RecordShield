package com.vivorecordshield.config

import android.content.Context
import android.content.SharedPreferences

/**
 * ShieldConfig
 * Central configuration parameters and resource discovery hints for RecordShield.
 */
object ShieldConfig {

    const val APP_VERSION = "2.1.1"
    const val VERSION_CODE = 20101

    // Target InCall UI Package
    const val INCALLUI_PACKAGE = "com.android.incallui"

    // Primary & Fallback Resource IDs confirmed from live InCallUI.apk ARSC parsing
    const val PRIMARY_RECORD_RESOURCE_ID = "com.android.incallui:id/record_or_contacts"
    const val PRIMARY_RECORD_RES_NAME = "record_or_contacts"

    val RECORD_RESOURCE_ID_HINTS = listOf(
        "record_or_contacts",   // HIGHEST confidence: confirmed 0x7f0a03f1
        "record_layertext",     // Confirmed 0x7f0a03f0
        "record_scrollview",    // Confirmed 0x7f0a03f2
        "record_agreement",     // Confirmed 0x7f0a03ef
        "record_button",        // From DEX string: "onClick record_button"
        "btn_record",
        "record_btn",
        "call_record"
    )

    val RECORD_SEARCH_TEXTS = listOf("Record", "record", "Stop recording", "Stop Recording")
    val RECORD_CONTENT_DESCS = listOf("Record", "record", "Stop recording", "Call recording")

    // Service & Watchdog intervals
    const val ALARM_WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L   // 5 minutes
    const val WORKER_WATCHDOG_INTERVAL_MIN = 15L             // 15 minutes
    const val EMERGENCY_RESTART_DELAY_MS = 3 * 1000L         // 3 seconds

    // Failure Counter & Recovery Thresholds
    const val MAX_CONSECUTIVE_FAILURES_BEFORE_FULL_RECOVERY = 5
    const val WAKELOCK_MAX_TIMEOUT_MS = 10 * 1000L           // 10 seconds

    // SharedPreferences Keys
    private const val PREFS_NAME = "record_shield_prefs"
    private const val KEY_SHIELD_ENABLED = "shield_enabled"
    private const val KEY_DEBUG_BOUNDS = "debug_bounds"
    private const val KEY_SHIELD_MARGIN_DP = "shield_margin_dp"
    private const val KEY_LAST_BOUNDS = "last_detected_bounds"
    private const val KEY_LAST_RESOURCE_ID = "last_detected_resource_id"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isShieldEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SHIELD_ENABLED, true)

    fun setShieldEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SHIELD_ENABLED, enabled).apply()
    }

    fun isDebugBoundsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DEBUG_BOUNDS, false)

    fun setDebugBoundsEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DEBUG_BOUNDS, enabled).apply()
    }

    fun getShieldMarginDp(ctx: Context): Int =
        prefs(ctx).getInt(KEY_SHIELD_MARGIN_DP, 4)

    fun saveLastDetectedBounds(ctx: Context, bounds: String) {
        prefs(ctx).edit().putString(KEY_LAST_BOUNDS, bounds).apply()
    }

    fun getLastDetectedBounds(ctx: Context): String? =
        prefs(ctx).getString(KEY_LAST_BOUNDS, null)

    fun saveLastDetectedResourceId(ctx: Context, resourceId: String) {
        prefs(ctx).edit().putString(KEY_LAST_RESOURCE_ID, resourceId).apply()
    }

    fun getLastDetectedResourceId(ctx: Context): String? =
        prefs(ctx).getString(KEY_LAST_RESOURCE_ID, null)
}
