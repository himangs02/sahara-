package com.yourteam.sahara

import android.app.Instrumentation
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertTrue

/**
 * Drives the real device UI through the accessibility tree, so tests see exactly what TalkBack sees
 * and are not tied to a synthetic Compose clock.
 */
class DeviceUi(private val instrumentation: Instrumentation) {

    private val root get() = instrumentation.uiAutomation.rootInActiveWindow

    private fun find(node: AccessibilityNodeInfo?, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (matches(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), matches)?.let { return it }
        return null
    }

    private fun label(node: AccessibilityNodeInfo) = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())

    fun findText(text: String): AccessibilityNodeInfo? = find(root) { text in label(it) }

    fun waitFor(description: String, timeoutMs: Long = 12_000, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            find(root, matches)?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Not visible: $description; screen shows: ${visibleLabels().joinToString(" | ")}")
    }

    fun waitForText(text: String) = waitFor(text) { text in label(it) }

    fun waitForLabelStartingWith(prefix: String) = waitFor("label starting with $prefix") { node ->
        label(node).any { it.startsWith(prefix) }
    }

    /** Retries briefly because a language change can recreate the Activity between finding and clicking a node. */
    fun clickText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (true) {
            var node: AccessibilityNodeInfo? = waitForText(text)
            while (node != null && !node.isClickable) node = node.parent
            checkNotNull(node) { "No clickable parent for $text" }
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) break
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Click was not accepted: $text")
            SystemClock.sleep(200)
        }
        instrumentation.waitForIdleSync()
    }

    /**
     * Brings [text] on screen by searching upward first, then downward. After each scroll it waits
     * for the animation to settle, so content between two pages is never skipped.
     */
    fun scrollUntilVisible(text: String) {
        fun scroll(action: Int): Boolean {
            val moved = find(root) { it.isScrollable }?.performAction(action) ?: false
            instrumentation.waitForIdleSync()
            val settle = SystemClock.uptimeMillis() + 800
            while (SystemClock.uptimeMillis() < settle) {
                if (findText(text) != null) return moved
                SystemClock.sleep(100)
            }
            return moved
        }
        for (action in listOf(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            var steps = 0
            while (findText(text) == null && steps++ < 20 && scroll(action)) Unit
            if (findText(text) != null) return
        }
    }

    /** Clicks a node in any window, including system dialogs, matched by view id suffix or one of [texts]. */
    fun clickSystemButton(viewIdSuffix: String, texts: List<String>) {
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val node = waitFor("system button $viewIdSuffix / $texts") { node ->
            node.viewIdResourceName?.endsWith(viewIdSuffix) == true || label(node).any { it in texts }
        }
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        assertTrue(checkNotNull(clickable).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    fun waitForPackage(packageName: String, timeoutMs: Long = 12_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (root?.packageName?.toString() == packageName) return
            SystemClock.sleep(200)
        }
        throw AssertionError("Expected $packageName on screen but found ${root?.packageName}")
    }

    fun waitUntilGone(text: String, timeoutMs: Long = 12_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (findText(text) == null) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Still visible: $text")
    }

    fun typeIntoFirstField(text: String) {
        val field = waitFor("an editable field") { it.isEditable }
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        instrumentation.waitForIdleSync()
    }

    private fun editableFields(): List<AccessibilityNodeInfo> {
        val fields = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isEditable) fields += node
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root)
        return fields
    }

    /** Sets each text into the editable fields in top-to-bottom order (e.g. username then password). */
    fun typeIntoFields(vararg texts: String) {
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (editableFields().size < texts.size) {
            if (SystemClock.uptimeMillis() > deadline) throw AssertionError("Expected ${texts.size} fields; found ${editableFields().size}")
            SystemClock.sleep(100)
        }
        val fields = editableFields()
        texts.forEachIndexed { i, text ->
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            assertTrue(fields[i].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        }
        instrumentation.waitForIdleSync()
    }

    fun visibleLabels(): List<String> {
        val labels = mutableListOf<String>()
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.let { labels += it.toString() }
            node.contentDescription?.let { labels += "[$it]" }
            for (i in 0 until node.childCount) collect(node.getChild(i))
        }
        collect(root)
        return labels
    }

    /** Clickable nodes currently on screen that have no spoken label or are smaller than [minSizePx]. */
    fun inaccessibleClickables(minSizePx: Int): List<String> {
        val problems = mutableListOf<String>()
        fun hasLabel(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (label(node).any { it.isNotBlank() }) return true
            return (0 until node.childCount).any { hasLabel(node.getChild(it)) }
        }
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isClickable && node.isVisibleToUser) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                val clipped = bounds.top <= 0 || bounds.bottom >= instrumentation.targetContext.resources.displayMetrics.heightPixels
                if (!hasLabel(node)) problems += "unlabelled ${node.className} at $bounds"
                if (!clipped && (bounds.width() < minSizePx || bounds.height() < minSizePx)) {
                    problems += "too small (${bounds.width()}x${bounds.height()}) ${label(node)} at $bounds"
                }
            }
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(root)
        return problems
    }
}
