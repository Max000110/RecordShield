package com.vivorecordshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vivorecordshield.R
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.metrics.ShieldMetricsManager
import com.vivorecordshield.ui.MainActivity

/**
 * ShieldForegroundService
 * Hardened Foreground Service managing notification lifecycle, START_STICKY,
 * onTaskRemoved recovery, and unexpected termination detection.
 */
class ShieldForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "record_shield_fg"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.vivorecordshield.START_FG"
        const val ACTION_STOP = "com.vivorecordshield.STOP_FG"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        var serviceStartTimeMs: Long = 0L
            private set
    }

    private var isManualStopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        DebugLogger.log(Tag.SHIELD, "ShieldForegroundService onCreate initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val sourceReason = intent?.getStringExtra("source_reason") ?: "UNKNOWN"

        DebugLogger.log(Tag.SHIELD, "onStartCommand: action=$action reason=$sourceReason flags=$flags startId=$startId")

        if (action == ACTION_STOP) {
            DebugLogger.log(Tag.SHIELD, "ShieldForegroundService stopping by request")
            isManualStopRequested = true
            isServiceRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        isServiceRunning = true
        isManualStopRequested = false
        serviceStartTimeMs = System.currentTimeMillis()

        ShieldMetricsManager.updateLastStartTime(this)
        ShieldMetricsManager.incrementRestartCount(this)
        ServiceRecoveryManager.scheduleAllWatchdogs(this)

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        DebugLogger.warn(Tag.SHIELD, "onTaskRemoved triggered (App swiped from Recents)")
        if (!isManualStopRequested) {
            ServiceRecoveryManager.handleProcessTerminationDetected(this, "ON_TASK_REMOVED")
        }
    }

    override fun onDestroy() {
        DebugLogger.warn(Tag.SHIELD, "ShieldForegroundService onDestroy called")
        isServiceRunning = false

        if (!isManualStopRequested) {
            DebugLogger.error(Tag.SHIELD, "UNEXPECTED SERVICE DESTRUCTION -> Triggering Emergency Recovery")
            ServiceRecoveryManager.handleProcessTerminationDetected(this, "UNEXPECTED_ON_DESTROY")
        }

        super.onDestroy()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLogger.error(Tag.SHIELD, "Uncaught Exception in Thread ${thread.name}", throwable)
            ShieldMetricsManager.incrementCrashCount(this)
            ServiceRecoveryManager.handleProcessTerminationDetected(this, "UNCAUGHT_EXCEPTION_${throwable.javaClass.simpleName}")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RecordShield Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps RecordShield active during calls"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RecordShield Active")
            .setContentText("Protecting call recording from accidental touches")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
