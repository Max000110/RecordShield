package com.vivorecordshield.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vivorecordshield.R
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.metrics.ShieldMetricsManager
import com.vivorecordshield.oem.VivoOptimizationHelper
import com.vivorecordshield.service.RecordShieldAccessibilityService
import com.vivorecordshield.service.ServiceRecoveryManager
import com.vivorecordshield.service.ShieldForegroundService

/**
 * MainActivity – Control Panel & Observability Dashboard
 */
class MainActivity : AppCompatActivity() {

    private lateinit var switchShieldEnabled: SwitchCompat
    private lateinit var switchDebugBounds: SwitchCompat
    private lateinit var btnStartService: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnOpenDiagnostics: Button
    private lateinit var btnTriggerFullRecovery: Button
    private lateinit var btnTestDetection: Button
    private lateinit var btnRefreshLogs: Button

    private lateinit var tvOverallStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvCallStatus: TextView
    private lateinit var tvShieldStatus: TextView
    private lateinit var tvNodeStatus: TextView
    private lateinit var tvCurrentBounds: TextView
    private lateinit var tvResourceId: TextView
    private lateinit var tvIncallPackage: TextView
    private lateinit var tvFailureCounter: TextView
    private lateinit var tvRestartCount: TextView
    private lateinit var tvCrashCount: TextView
    private lateinit var tvKillCount: TextView
    private lateinit var tvRecoveryCount: TextView
    private lateinit var tvBootCount: TextView
    private lateinit var tvLogs: TextView

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updatePermissionStatus() }

    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updatePermissionStatus() }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != RecordShieldAccessibilityService.ACTION_STATUS_UPDATE) return

            val incall = intent.getBooleanExtra(RecordShieldAccessibilityService.EXTRA_INCALL_ACTIVE, false)
            val active = intent.getBooleanExtra(RecordShieldAccessibilityService.EXTRA_SHIELD_ACTIVE, false)
            val found = intent.getBooleanExtra(RecordShieldAccessibilityService.EXTRA_NODE_FOUND, false)
            val bounds = intent.getStringExtra(RecordShieldAccessibilityService.EXTRA_BOUNDS) ?: "—"
            val resId = intent.getStringExtra(RecordShieldAccessibilityService.EXTRA_RESOURCE_ID) ?: "—"

            updateRuntimeStatus(incall, active, found, bounds, resId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DebugLogger.init(cacheDir)
        bindViews()
        setupUI()
        updatePermissionStatus()
        refreshLogs()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            statusReceiver,
            IntentFilter(RecordShieldAccessibilityService.ACTION_STATUS_UPDATE)
        )
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        refreshLogs()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }

    private fun bindViews() {
        switchShieldEnabled = findViewById(R.id.switchShieldEnabled)
        switchDebugBounds = findViewById(R.id.switchDebugBounds)
        btnStartService = findViewById(R.id.btnStartService)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnOpenAccessibility = findViewById(R.id.btnOpenAccessibility)
        btnOpenDiagnostics = findViewById(R.id.btnOpenDiagnostics)
        btnTriggerFullRecovery = findViewById(R.id.btnTriggerFullRecovery)
        btnTestDetection = findViewById(R.id.btnTestDetection)
        btnRefreshLogs = findViewById(R.id.btnRefreshLogs)

        tvOverallStatus = findViewById(R.id.tvOverallStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvShieldStatus = findViewById(R.id.tvShieldStatus)
        tvNodeStatus = findViewById(R.id.tvNodeStatus)
        tvCurrentBounds = findViewById(R.id.tvCurrentBounds)
        tvResourceId = findViewById(R.id.tvResourceId)
        tvIncallPackage = findViewById(R.id.tvIncallPackage)
        tvFailureCounter = findViewById(R.id.tvFailureCounter)
        tvRestartCount = findViewById(R.id.tvRestartCount)
        tvCrashCount = findViewById(R.id.tvCrashCount)
        tvKillCount = findViewById(R.id.tvKillCount)
        tvRecoveryCount = findViewById(R.id.tvRecoveryCount)
        tvBootCount = findViewById(R.id.tvBootCount)
        tvLogs = findViewById(R.id.tvLogs)
    }

    private fun setupUI() {
        switchShieldEnabled.isChecked = ShieldConfig.isShieldEnabled(this)
        switchShieldEnabled.setOnCheckedChangeListener { _, checked ->
            ShieldConfig.setShieldEnabled(this, checked)
            DebugLogger.log(Tag.CONFIG, "shield enabled = $checked")
            if (checked) {
                ServiceRecoveryManager.startPrimaryService(this, "USER_ENABLE_SWITCH")
            } else {
                RecordShieldAccessibilityService.instance?.onInterrupt()
            }
            updatePermissionStatus()
        }

        switchDebugBounds.isChecked = ShieldConfig.isDebugBoundsEnabled(this)
        switchDebugBounds.setOnCheckedChangeListener { _, checked ->
            ShieldConfig.setDebugBoundsEnabled(this, checked)
            RecordShieldAccessibilityService.instance?.refreshDebugMode()
            DebugLogger.log(Tag.CONFIG, "debug bounds = $checked")
        }

        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        }

        btnOpenAccessibility.setOnClickListener {
            showAccessibilityDialog()
        }

        btnOpenDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        btnTriggerFullRecovery.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Trigger Full Recovery Mode")
                .setMessage("This will reset watchdogs, force-restart ShieldForegroundService, and clear failure counters. Proceed?")
                .setPositiveButton("Trigger") { _, _ ->
                    ServiceRecoveryManager.triggerFullRecoveryMode(this, "MANUAL_USER_TRIGGER")
                    updatePermissionStatus()
                    Toast.makeText(this, "Full Recovery Mode Triggered", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnRefreshLogs.setOnClickListener {
            refreshLogs()
        }

        btnStartService.setOnClickListener {
            ServiceRecoveryManager.startPrimaryService(this, "USER_START_BUTTON")
            Toast.makeText(this, "Protection service started", Toast.LENGTH_SHORT).show()
        }

        btnTestDetection.setOnClickListener {
            val msg = if (isAccessibilityEnabled()) {
                "Accessibility service is running.\n\n" +
                        "Make a call to see detection in action.\n\n" +
                        "Last detected bounds:\n${ShieldConfig.getLastDetectedBounds(this) ?: "none yet"}\n\n" +
                        "Last detected resource-id:\n${ShieldConfig.getLastDetectedResourceId(this) ?: "none yet"}"
            } else {
                "Accessibility service is NOT enabled.\nPlease enable it first."
            }
            AlertDialog.Builder(this)
                .setTitle("Detection Status")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }

        tvIncallPackage.text = ShieldConfig.INCALLUI_PACKAGE
        updateRuntimeStatus(false, false, false, "—", "—")
    }

    private fun updatePermissionStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = isAccessibilityEnabled()
        val shieldOn = ShieldConfig.isShieldEnabled(this)

        if (overlayOk) {
            tvOverlayStatus.text = "✅ Granted"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
            btnGrantOverlay.isVisible = false
        } else {
            tvOverlayStatus.text = "❌ Not Granted"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            btnGrantOverlay.isVisible = true
        }

        if (accOk) {
            tvAccessibilityStatus.text = "✅ Enabled"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_ok))
            btnOpenAccessibility.text = "Re-configure Accessibility"
        } else {
            tvAccessibilityStatus.text = "❌ Disabled"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_error))
            btnOpenAccessibility.text = "Enable Accessibility Service"
        }

        switchShieldEnabled.isChecked = shieldOn

        val ready = overlayOk && accOk && shieldOn
        tvOverallStatus.text = if (ready) "🟢 Protection Ready" else "🔴 Action Required"
        tvOverallStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (ready) R.color.status_ok else R.color.status_error
            )
        )

        val failureCount = ShieldMetricsManager.getFailureCounter(this)
        val maxFailures = ShieldConfig.MAX_CONSECUTIVE_FAILURES_BEFORE_FULL_RECOVERY
        tvFailureCounter.text = "Consecutive Kills: $failureCount / $maxFailures"

        tvRestartCount.text = ShieldMetricsManager.getRestartCount(this).toString()
        tvCrashCount.text = ShieldMetricsManager.getCrashCount(this).toString()
        tvKillCount.text = ShieldMetricsManager.getKillCount(this).toString()
        tvRecoveryCount.text = ShieldMetricsManager.getRecoveryCount(this).toString()
        tvBootCount.text = ShieldMetricsManager.getBootCount(this).toString()
    }

    private fun updateRuntimeStatus(
        incallActive: Boolean,
        shieldActive: Boolean,
        nodeFound: Boolean,
        bounds: String,
        resourceId: String
    ) {
        runOnUiThread {
            tvCallStatus.text = if (incallActive) "📞 In-call UI Active" else "📵 No active call"
            tvShieldStatus.text = if (shieldActive) "🛡️ Shield ON" else "⚫ Shield OFF"
            tvNodeStatus.text = if (nodeFound) "✅ Record node found" else "❓ Record node not found"
            tvCurrentBounds.text = "Bounds: $bounds"
            tvResourceId.text = "Resource-ID: $resourceId"

            tvShieldStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (shieldActive) R.color.status_ok else R.color.status_idle
                )
            )
        }
    }

    private fun refreshLogs() {
        tvLogs.text = DebugLogger.formatForDisplay(80)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return services.any { it.id.contains("RecordShieldAccessibilityService") }
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage(
                "1. Tap OK to open Accessibility Settings.\n\n" +
                        "2. Find 'RecordShield' in the list.\n\n" +
                        "3. Tap it and enable it.\n\n" +
                        "4. Accept the permission dialog.\n\n" +
                        "Note: On Vivo V40, go to:\n" +
                        "Settings → Accessibility → Installed Apps → RecordShield"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
