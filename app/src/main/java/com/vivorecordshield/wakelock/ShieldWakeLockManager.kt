package com.vivorecordshield.wakelock

import android.content.Context
import android.os.PowerManager
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag

/**
 * ShieldWakeLockManager
 * Controlled WakeLock manager preventing CPU sleep during watchdog verification.
 */
object ShieldWakeLockManager {

    private const val WAKELOCK_TAG = "RecordShield:WatchdogLock"
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquireBriefWakeLock(ctx: Context, timeoutMs: Long = ShieldConfig.WAKELOCK_MAX_TIMEOUT_MS) {
        try {
            if (wakeLock == null) {
                val pm = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let { lock ->
                if (!lock.isHeld) {
                    lock.acquire(timeoutMs)
                    DebugLogger.log(Tag.SHIELD, "WakeLock acquired: timeoutMs=$timeoutMs")
                }
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Failed to acquire WakeLock", e)
        }
    }

    @Synchronized
    fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    DebugLogger.log(Tag.SHIELD, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Failed to release WakeLock", e)
        } finally {
            wakeLock = null
        }
    }
}
