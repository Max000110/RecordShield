package com.vivorecordshield.ui

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.vivorecordshield.service.RecordShieldAccessibilityService

/**
 * Utility to broadcast shield status updates to any listening UI components.
 */
object StatusBroadcaster {
    fun send(
        ctx: Context,
        incallActive: Boolean,
        shieldActive: Boolean,
        nodeFound: Boolean,
        bounds: String? = null,
        resourceId: String? = null
    ) {
        val intent = Intent(RecordShieldAccessibilityService.ACTION_STATUS_UPDATE).apply {
            putExtra(RecordShieldAccessibilityService.EXTRA_INCALL_ACTIVE, incallActive)
            putExtra(RecordShieldAccessibilityService.EXTRA_SHIELD_ACTIVE, shieldActive)
            putExtra(RecordShieldAccessibilityService.EXTRA_NODE_FOUND, nodeFound)
            bounds?.let { putExtra(RecordShieldAccessibilityService.EXTRA_BOUNDS, it) }
            resourceId?.let { putExtra(RecordShieldAccessibilityService.EXTRA_RESOURCE_ID, it) }
        }
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)
    }
}
