package com.vivorecordshield.watchdog

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.service.ServiceRecoveryManager
import com.vivorecordshield.wakelock.ShieldWakeLockManager
import java.util.concurrent.TimeUnit

/**
 * ShieldWatchdog
 * WorkManager-based periodic and one-time immediate fallback watchdog worker.
 */
class ShieldWatchdog(
    private val appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    companion object {
        const val PERIODIC_WORK_TAG = "shield_periodic_watchdog"
        const val IMMEDIATE_WORK_TAG = "shield_immediate_watchdog"

        fun schedulePeriodicWatchdog(ctx: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<ShieldWatchdog>(
                    ShieldConfig.WORKER_WATCHDOG_INTERVAL_MIN,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .addTag(PERIODIC_WORK_TAG)
                    .build()

                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                DebugLogger.log(Tag.SHIELD, "WorkManager periodic watchdog enqueued: intervalMin=${ShieldConfig.WORKER_WATCHDOG_INTERVAL_MIN}")
            } catch (e: Exception) {
                DebugLogger.error(Tag.SHIELD, "Failed to schedule WorkManager periodic watchdog", e)
            }
        }

        fun enqueueImmediateWatchdog(ctx: Context) {
            try {
                val workRequest = OneTimeWorkRequestBuilder<ShieldWatchdog>()
                    .addTag(IMMEDIATE_WORK_TAG)
                    .build()

                WorkManager.getInstance(ctx).enqueueUniqueWork(
                    IMMEDIATE_WORK_TAG,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                DebugLogger.log(Tag.SHIELD, "WorkManager immediate watchdog enqueued")
            } catch (e: Exception) {
                DebugLogger.error(Tag.SHIELD, "Failed to enqueue immediate WorkManager watchdog", e)
            }
        }

        fun cancelAllWatchdogs(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelAllWorkByTag(PERIODIC_WORK_TAG)
                WorkManager.getInstance(ctx).cancelAllWorkByTag(IMMEDIATE_WORK_TAG)
                DebugLogger.log(Tag.SHIELD, "All WorkManager watchdogs cancelled")
            } catch (e: Exception) {
                DebugLogger.error(Tag.SHIELD, "Error cancelling WorkManager watchdogs", e)
            }
        }
    }

    override fun doWork(): Result {
        ShieldWakeLockManager.acquireBriefWakeLock(appContext, 5000L)
        return try {
            DebugLogger.log(Tag.SHIELD, "ShieldWatchdog execution started")
            ServiceRecoveryManager.startPrimaryService(appContext, "WORKMANAGER_WATCHDOG")
            Result.success()
        } catch (e: Exception) {
            DebugLogger.error(Tag.SHIELD, "Error during ShieldWatchdog execution", e)
            Result.retry()
        } finally {
            ShieldWakeLockManager.releaseWakeLock()
        }
    }
}
