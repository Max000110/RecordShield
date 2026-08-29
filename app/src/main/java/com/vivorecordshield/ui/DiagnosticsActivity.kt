package com.vivorecordshield.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vivorecordshield.R
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.metrics.ShieldMetricsManager
import com.vivorecordshield.oem.VivoOptimizationHelper
import com.vivorecordshield.service.RecordShieldAccessibilityService
import com.vivorecordshield.service.ShieldForegroundService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiagnosticsActivity
 * Production OEM Survival Assistant and Diagnostics Screen for RecordShield (Section S).
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var tvAppVersion: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvVivoVersion: TextView
    private lateinit var tvIncallPackage: TextView
    private lateinit var tvRecordResId: TextView
    private lateinit var tvRecordNodeDetected: TextView
    private lateinit var tvRecordNodeBounds: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayPermission: TextView
    private lateinit var tvBatteryOptStatus: TextView
    private lateinit var tvFgServiceStatus: TextView
    private lateinit var tvWatchdogStatus: TextView
    private lateinit var tvLastHeartbeat: TextView
    private lateinit var tvLastRecovery: TextView
    private lateinit var tvFailureCount: TextView
    private lateinit var tvRecoveryCount: TextView
    private lateinit var tvBootCount: TextView

    private lateinit var btnFixBatteryOpt: Button
    private lateinit var btnFixAutoStart: Button
    private lateinit var btnFixHighPower: Button
    private lateinit var btnOpenAppDetails: Button
    private lateinit var btnRefreshDiagnostics: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "OEM Survival Assistant"

        bindViews()
        setupListeners()
        refreshDiagnosticsUI()
    }

    override fun onResume() {
        super.onResume()
        refreshDiagnosticsUI()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindViews() {
        tvAppVersion = findViewById(R.id.tvAppVersion)
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion)
        tvVivoVersion = findViewById(R.id.tvVivoVersion)
        tvIncallPackage = findViewById(R.id.tvIncallPackage)
        tvRecordResId = findViewById(R.id.tvRecordResId)
        tvRecordNodeDetected = findViewById(R.id.tvRecordNodeDetected)
        tvRecordNodeBounds = findViewById(R.id.tvRecordNodeBounds)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayPermission = findViewById(R.id.tvOverlayPermission)
        tvBatteryOptStatus = findViewById(R.id.tvBatteryOptStatus)
        tvFgServiceStatus = findViewById(R.id.tvFgServiceStatus)
        tvWatchdogStatus = findViewById(R.id.tvWatchdogStatus)
        tvLastHeartbeat = findViewById(R.id.tvLastHeartbeat)
        tvLastRecovery = findViewById(R.id.tvLastRecovery)
        tvFailureCount = findViewById(R.id.tvFailureCount)
        tvRecoveryCount = findViewById(R.id.tvRecoveryCount)
        tvBootCount = findViewById(R.id.tvBootCount)

        btnFixBatteryOpt = findViewById(R.id.btnFixBatteryOpt)
        btnFixAutoStart = findViewById(R.id.btnFixAutoStart)
        btnFixHighPower = findViewById(R.id.btnFixHighPower)
        btnOpenAppDetails = findViewById(R.id.btnOpenAppDetails)
        btnRefreshDiagnostics = findViewById(R.id.btnRefreshDiagnostics)
    }

    private fun setupListeners() {
        btnFixBatteryOpt.setOnClickListener {
            VivoOptimizationHelper.requestBatteryOptimizationExemption(this)
        }

        btnFixAutoStart.setOnClickListener {
            val launched = VivoOptimizationHelper.openVivoAutoStartSettings(this)
            if (!launched) {
                Toast.makeText(this, "Opened App Settings. Enable 'Auto-start' manually.", Toast.LENGTH_LONG).show()
            }
        }

        btnFixHighPower.setOnClickListener {
            val launched = VivoOptimizationHelper.openVivoHighPowerSettings(this)
            if (!launched) {
                Toast.makeText(this, "Opened App Settings. Set Battery to 'No restrictions'.", Toast.LENGTH_LONG).show()
            }
        }

        btnOpenAppDetails.setOnClickListener {
            VivoOptimizationHelper.openAppSettings(this)
        }

        btnRefreshDiagnostics.setOnClickListener {
            refreshDiagnosticsUI()
            Toast.makeText(this, "Diagnostics Refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDiagnosticsUI() {
        val status = VivoOptimizationHelper.getDiagnosticStatus(this)
        val isAccEnabled = isAccessibilityEnabled()
        val isOverlayGranted = Settings.canDrawOverlays(this)
        val isFgRunning = ShieldForegroundService.isServiceRunning

        tvAppVersion.text = "Version: ${ShieldConfig.APP_VERSION} (${ShieldConfig.VERSION_CODE})"
        tvAndroidVersion.text = "Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        tvVivoVersion.text = "Build ID: ${Build.DISPLAY}"
        tvIncallPackage.text = "InCallUI Package: ${ShieldConfig.INCALLUI_PACKAGE}"
        tvRecordResId.text = "Target ResID: ${ShieldConfig.PRIMARY_RECORD_RESOURCE_ID}"

        val lastBounds = ShieldConfig.getLastDetectedBounds(this)
        tvRecordNodeDetected.text = if (lastBounds != null) "YES" else "NO / IDLE"
        tvRecordNodeBounds.text = "Bounds: ${lastBounds ?: "—"}"

        tvOverlayStatus.text = if (isOverlayGranted) "ACTIVE / READY" else "INACTIVE"
        tvAccessibilityStatus.text = if (isAccEnabled) "ENABLED" else "DISABLED"
        tvOverlayPermission.text = if (isOverlayGranted) "GRANTED" else "DENIED"

        if (status.isIgnoringBatteryOptimizations) {
            tvBatteryOptStatus.text = "EXEMPT (Optimal)"
            tvBatteryOptStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
            btnFixBatteryOpt.isEnabled = false
        } else {
            tvBatteryOptStatus.text = "NOT EXEMPT (Action Required)"
            tvBatteryOptStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            btnFixBatteryOpt.isEnabled = true
        }

        tvFgServiceStatus.text = if (isFgRunning) "RUNNING" else "STOPPED"
        tvWatchdogStatus.text = if (isFgRunning && isAccEnabled) "HEALTHY" else "UNHEALTHY"

        val lastStart = ShieldMetricsManager.getLastStartTime(this)
        tvLastHeartbeat.text = if (lastStart > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastStart))
        } else "Never"

        val lastRecovery = ShieldMetricsManager.getLastRecoveryTime(this)
        tvLastRecovery.text = if (lastRecovery > 0) {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(lastRecovery))
        } else "Never"

        tvFailureCount.text = "Failure Counter: ${ShieldMetricsManager.getFailureCounter(this)} / ${ShieldConfig.MAX_CONSECUTIVE_FAILURES_BEFORE_FULL_RECOVERY}"
        tvRecoveryCount.text = "Recovery Count: ${ShieldMetricsManager.getRecoveryCount(this)}"
        tvBootCount.text = "Boot Count: ${ShieldMetricsManager.getBootCount(this)}"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return services.any { it.id.contains("RecordShieldAccessibilityService") }
    }
}
