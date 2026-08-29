package com.vivorecordshield.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag

/**
 * TouchShieldManager
 *
 * Creates and manages a minimal transparent overlay window that intercepts
 * touch events ONLY within the Record button bounds.
 *
 * Design decisions:
 *  - Uses TYPE_APPLICATION_OVERLAY (requires SYSTEM_ALERT_WINDOW permission).
 *  - The overlay View is fully transparent (alpha = 0 in normal mode).
 *  - In debug mode, it shows a semi-transparent red tint so bounds are visible.
 *  - FLAG_NOT_FOCUSABLE ensures keyboard/focus stays in the actual in-call UI.
 *  - FLAG_NOT_TOUCH_MODAL is NOT set: touches within our window rect are consumed.
 *  - The overlay is ONLY sized to cover the Record button + margin — NOT full-screen.
 *
 * The overlay intercepts touch events at the OS level, so even proximity-sensor
 * triggered touches are absorbed before they can reach com.android.incallui.
 *
 * IMPORTANT: The overlay window is added with exact pixel bounds derived from the
 * AccessibilityNodeInfo of the Record button. If the node is not found, no overlay
 * is created (fail-safe behavior: other controls remain fully functional).
 */
class TouchShieldManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var shieldView: View? = null
    private var currentBounds: Rect? = null
    private var isDebugMode: Boolean = false

    val isActive: Boolean get() = shieldView != null

    /**
     * Place (or reposition) the shield over [recordBounds].
     * If a shield is already active at the same bounds, this is a no-op.
     */
    fun applyShield(recordBounds: Rect) {
        val marginPx = dpToPx(ShieldConfig.getShieldMarginDp(context))
        isDebugMode = ShieldConfig.isDebugBoundsEnabled(context)

        val shieldRect = Rect(
            recordBounds.left - marginPx,
            recordBounds.top - marginPx,
            recordBounds.right + marginPx,
            recordBounds.bottom + marginPx
        )

        // Clamp to screen bounds (no negative positions)
        shieldRect.left = maxOf(0, shieldRect.left)
        shieldRect.top = maxOf(0, shieldRect.top)

        // Avoid re-creating if bounds have not changed (e.g. redundant accessibility events)
        if (shieldRect == currentBounds && shieldView != null) {
            DebugLogger.log(Tag.OVERLAY, "applyShield: bounds unchanged, skipping recreate")
            return
        }

        // Remove existing overlay before creating new one
        removeShield()

        val params = buildLayoutParams(shieldRect)

        val view = View(context).apply {
            if (isDebugMode) {
                // Red semi-transparent: shows exactly which area is protected
                setBackgroundColor(Color.argb(80, 255, 0, 0))
            } else {
                // Fully transparent: invisible in production
                setBackgroundColor(Color.TRANSPARENT)
            }
            // Touch events within this view are consumed (return false = don't propagate)
            setOnTouchListener { _, _ ->
                DebugLogger.log(Tag.OVERLAY, "touch intercepted on shield")
                true  // consume the touch event
            }
        }

        try {
            windowManager.addView(view, params)
            shieldView = view
            currentBounds = shieldRect

            DebugLogger.log(
                Tag.OVERLAY, "created",
                "bounds=[${shieldRect.left},${shieldRect.top}][${shieldRect.right},${shieldRect.bottom}]" +
                        " debug=$isDebugMode"
            )

            // Persist for status display
            ShieldConfig.saveLastDetectedBounds(
                context,
                "[${recordBounds.left},${recordBounds.top}][${recordBounds.right},${recordBounds.bottom}]"
            )
        } catch (e: Exception) {
            DebugLogger.error(Tag.OVERLAY, "failed to add overlay view", e)
            shieldView = null
            currentBounds = null
        }
    }

    /**
     * Remove the shield if it is currently active.
     * Safe to call even if no shield exists.
     */
    fun removeShield() {
        val v = shieldView ?: return
        try {
            windowManager.removeViewImmediate(v)
            DebugLogger.log(Tag.OVERLAY, "removed")
        } catch (e: Exception) {
            DebugLogger.warn(Tag.OVERLAY, "removeShield error: ${e.message}")
        } finally {
            shieldView = null
            currentBounds = null
        }
    }

    /**
     * Toggle debug (red tint) mode while shield is active.
     */
    fun refreshDebugMode() {
        val bounds = currentBounds ?: return
        // Re-apply triggers a full shield recreate with updated debug flag
        applyShield(Rect(
            bounds.left + dpToPx(ShieldConfig.getShieldMarginDp(context)),
            bounds.top + dpToPx(ShieldConfig.getShieldMarginDp(context)),
            bounds.right - dpToPx(ShieldConfig.getShieldMarginDp(context)),
            bounds.bottom - dpToPx(ShieldConfig.getShieldMarginDp(context))
        ))
    }

    private fun buildLayoutParams(rect: Rect): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            rect.left,
            rect.top,
            type,
            // NOT_FOCUSABLE: keyboard focus stays in incall UI
            // NOT_TOUCH_MODAL is intentionally NOT included so touches in our rect are consumed
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = rect.left
            y = rect.top
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
