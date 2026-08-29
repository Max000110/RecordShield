package com.vivorecordshield.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.detector.RecordButtonDetector
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag
import com.vivorecordshield.overlay.TouchShieldManager
import com.vivorecordshield.ui.StatusBroadcaster

/**
 * RecordShieldAccessibilityService
 *
 * The core of the protection system.
 *
 * Responsibilities:
 *  1. Receive AccessibilityEvents scoped ONLY to com.android.incallui.
 *  2. On window state or content changes, traverse the AccessibilityNodeInfo tree.
 *  3. Detect the Record button using RecordButtonDetector.
 *  4. Instruct TouchShieldManager to place/move/remove a transparent overlay.
 *  5. On incallui disappearance → remove overlay → fail-safe.
 *
 * SAFETY RULES enforced here:
 *  - We never block, move, or hide other call controls (End, Mute, Speaker, etc.)
 *  - We never disable recording services.
 *  - If Record node not found → log warning → no overlay placed (no broad block).
 *  - If the service crashes → Android will restart it automatically.
 *  - Shield is always removed when incallui is no longer the active window.
 *
 * Vivo/Funtouch OS considerations:
 *  - The in-call UI may be recreated (speaker toggle, Bluetooth switch, rotation)
 *    so we re-detect on EVERY relevant event, not just the first appearance.
 *  - We debounce rapid re-triggers via a 150ms Handler delay to avoid jitter
 *    during UI transitions.
 */
class RecordShieldAccessibilityService : AccessibilityService() {

    companion object {
        var instance: RecordShieldAccessibilityService? = null
            private set

        const val ACTION_STATUS_UPDATE = "com.vivorecordshield.STATUS_UPDATE"
        const val EXTRA_SHIELD_ACTIVE = "shield_active"
        const val EXTRA_INCALL_ACTIVE = "incall_active"
        const val EXTRA_NODE_FOUND = "node_found"
        const val EXTRA_BOUNDS = "bounds"
        const val EXTRA_RESOURCE_ID = "resource_id"
    }

    private lateinit var shieldManager: TouchShieldManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingDetect: Runnable? = null

    private var incallUiActive = false
    private var lastKnownResourceId: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        shieldManager = TouchShieldManager(applicationContext)
        DebugLogger.init(cacheDir)
        DebugLogger.log(Tag.SHIELD, "AccessibilityService connected")
        StatusBroadcaster.send(this, incallActive = false, shieldActive = false, nodeFound = false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!ShieldConfig.isShieldEnabled(this)) {
            if (shieldManager.isActive) shieldManager.removeShield()
            return
        }

        val pkg = event.packageName?.toString() ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == ShieldConfig.INCALLUI_PACKAGE) {
                    DebugLogger.log(Tag.CALL_UI, "window state changed",
                        "pkg=$pkg class=${event.className}")
                    incallUiActive = true
                    scheduleDetection(150L)
                } else {
                    if (incallUiActive) {
                        DebugLogger.log(Tag.CALL_UI, "incallui window lost focus → removing shield")
                        incallUiActive = false
                        mainHandler.removeCallbacks(pendingDetect ?: return)
                        shieldManager.removeShield()
                        StatusBroadcaster.send(this, incallActive = false, shieldActive = false, nodeFound = false)
                    }
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == ShieldConfig.INCALLUI_PACKAGE && incallUiActive) {
                    // Debounce: content changes can fire repeatedly during UI animations
                    scheduleDetection(200L)
                }
            }
        }
    }

    override fun onInterrupt() {
        DebugLogger.warn(Tag.SHIELD, "onInterrupt called → removing shield defensively")
        mainHandler.removeCallbacksAndMessages(null)
        shieldManager.removeShield()
        incallUiActive = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DebugLogger.warn(Tag.SHIELD, "AccessibilityService unbound → cleaning up")
        mainHandler.removeCallbacksAndMessages(null)
        shieldManager.removeShield()
        incallUiActive = false
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        shieldManager.removeShield()
        instance = null
    }

    /**
     * Schedule a detection pass with debouncing.
     * Cancels any pending pass before scheduling a new one.
     */
    private fun scheduleDetection(delayMs: Long) {
        pendingDetect?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { performDetection() }
        pendingDetect = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    /**
     * Core detection pass:
     *  - Get the root node for the incallui window
     *  - Delegate to RecordButtonDetector
     *  - Apply or remove shield based on result
     */
    private fun performDetection() {
        if (!incallUiActive) return

        val root: AccessibilityNodeInfo? = try {
            // Find the window belonging to com.android.incallui specifically
            windows?.firstOrNull { it.root?.packageName == ShieldConfig.INCALLUI_PACKAGE }?.root
                ?: rootInActiveWindow
        } catch (e: Exception) {
            DebugLogger.error(Tag.CALL_UI, "cannot get root node", e)
            null
        }

        if (root == null) {
            DebugLogger.warn(Tag.CALL_UI, "root node is null → scheduling retry")
            scheduleDetection(500L)
            return
        }

        val result = RecordButtonDetector.find(root, lastKnownResourceId)

        if (result != null) {
            // Cache stable resource ID for faster future detection
            if (!result.resourceId.isNullOrBlank() && result.resourceId != lastKnownResourceId) {
                lastKnownResourceId = result.resourceId
                ShieldConfig.saveLastDetectedResourceId(this, result.resourceId)
                DebugLogger.log(Tag.RECORD_NODE, "caching resourceId", "id=${result.resourceId}")
            }

            shieldManager.applyShield(result.bounds)

            StatusBroadcaster.send(
                this,
                incallActive = true,
                shieldActive = true,
                nodeFound = true,
                bounds = result.bounds.toShortString(),
                resourceId = result.resourceId
            )
        } else {
            // FAIL-SAFE: no broad overlay placed when node not found
            DebugLogger.warn(Tag.RECORD_NODE, "Record node not found – no shield placed")
            if (shieldManager.isActive) {
                shieldManager.removeShield()
            }
            StatusBroadcaster.send(
                this,
                incallActive = true,
                shieldActive = false,
                nodeFound = false
            )
        }
    }

    /**
     * Called from UI: manually toggle debug bounds mode without restarting.
     */
    fun refreshDebugMode() {
        shieldManager.refreshDebugMode()
    }
}
