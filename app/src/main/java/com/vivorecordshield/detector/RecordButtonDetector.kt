package com.vivorecordshield.detector

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.vivorecordshield.config.ShieldConfig
import com.vivorecordshield.logger.DebugLogger
import com.vivorecordshield.logger.DebugLogger.Tag

/**
 * RecordButtonDetector
 *
 * Traverses an AccessibilityNodeInfo tree to find the in-call "Record" button.
 *
 * Strategy (in priority order):
 *   1. viewIdResourceName containing known record id hints
 *   2. text matching known record texts
 *   3. contentDescription matching known record descriptions
 *   4. Recurse into children for all strategies
 *
 * IMPORTANT:
 *   - We only READ the node; we do NOT attempt node.setClickable(false) because
 *     that cannot mutate the actual Android View in another app's process.
 *   - We return the bounds so the overlay engine can place a touch shield.
 *   - We track the stable resourceId when found, for faster future re-detection.
 */
object RecordButtonDetector {

    data class DetectionResult(
        val bounds: Rect,
        val resourceId: String?,
        val text: String?,
        val contentDesc: String?
    )

    /**
     * Main detection entry point.
     * @param root Root AccessibilityNodeInfo from the incallui window.
     * @param knownResourceId If a resource ID was found before, try it first for speed.
     * @return DetectionResult or null if the Record node is not found.
     */
    fun find(root: AccessibilityNodeInfo?, knownResourceId: String?): DetectionResult? {
        if (root == null) return null

        // --- Strategy 1: search by known stable resource ID (fastest path) ---
        if (!knownResourceId.isNullOrBlank()) {
            val byId = findByResourceId(root, knownResourceId)
            if (byId != null) {
                DebugLogger.log(Tag.RECORD_NODE, "found via cached resourceId",
                    "id=$knownResourceId bounds=${boundsOf(byId)}")
                return makeResult(byId)
            }
        }

        // --- Strategy 2: full resource-id hint scan ---
        val byHint = findByResourceIdHints(root)
        if (byHint != null) {
            DebugLogger.log(Tag.RECORD_NODE, "found via resource-id hint",
                "id=${byHint.viewIdResourceName} bounds=${boundsOf(byHint)}")
            return makeResult(byHint)
        }

        // --- Strategy 3: text match ---
        val byText = findByText(root)
        if (byText != null) {
            DebugLogger.log(Tag.RECORD_NODE, "found via text match",
                "text=${byText.text} bounds=${boundsOf(byText)}")
            return makeResult(byText)
        }

        // --- Strategy 4: content description match ---
        val byDesc = findByContentDesc(root)
        if (byDesc != null) {
            DebugLogger.log(Tag.RECORD_NODE, "found via contentDesc",
                "desc=${byDesc.contentDescription} bounds=${boundsOf(byDesc)}")
            return makeResult(byDesc)
        }

        DebugLogger.warn(Tag.RECORD_NODE, "Record node NOT found in current window tree")
        return null
    }

    // ---------- private helpers ----------

    private fun makeResult(node: AccessibilityNodeInfo): DetectionResult {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return DetectionResult(
            bounds = rect,
            resourceId = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDesc = node.contentDescription?.toString()
        )
    }

    private fun boundsOf(node: AccessibilityNodeInfo): String {
        val r = Rect()
        node.getBoundsInScreen(r)
        return "[${r.left},${r.top}][${r.right},${r.bottom}]"
    }

    private fun findByResourceId(root: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        return try {
            root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull()
        } catch (_: Exception) { null }
    }

    private fun findByResourceIdHints(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseFirst(root) { node ->
            val id = node.viewIdResourceName?.lowercase() ?: return@traverseFirst false
            ShieldConfig.RECORD_RESOURCE_ID_HINTS.any { hint -> hint in id }
        }
    }

    private fun findByText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (searchText in ShieldConfig.RECORD_SEARCH_TEXTS) {
            try {
                val results = root.findAccessibilityNodeInfosByText(searchText)
                val match = results.firstOrNull { node ->
                    // Avoid matching elements that are clearly not the record button
                    // (e.g. labels saying "Recording started")
                    val nodeText = node.text?.toString() ?: ""
                    ShieldConfig.RECORD_SEARCH_TEXTS.any { it.equals(nodeText, ignoreCase = true) }
                            && node.isClickable
                }
                if (match != null) return match
            } catch (_: Exception) {}
        }
        return null
    }

    private fun findByContentDesc(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return traverseFirst(root) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: return@traverseFirst false
            ShieldConfig.RECORD_CONTENT_DESCS.any { it.lowercase() in desc }
        }
    }

    /**
     * Generic DFS traversal returning the first node satisfying [predicate].
     * Properly recycles intermediate nodes to avoid memory leaks.
     */
    private fun traverseFirst(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                stack.addLast(child)
            }
        }
        return null
    }
}
