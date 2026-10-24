package dev.malik.phonebot.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.malik.phonebot.TAG
import dev.malik.phonebot.app
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The hands. Milestone 2 only tracks the foreground app and can dump the screen tree (FR-14);
 * snapshot/click/back/home for the flow engine arrive in milestone 3.
 */
class BotAccessibilityService : AccessibilityService() {

    @Volatile
    var foregroundPackage: String? = null
        private set

    // `adb shell am broadcast -a dev.malik.phonebot.DUMP` dumps whatever TikTok shows right now.
    // uiautomator often can't dump TikTok because its video never lets the screen go idle.
    private val dumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            dumpScreen()
        }
    }

    override fun onServiceConnected() {
        instance = this
        registerReceiver(dumpReceiver, IntentFilter(ACTION_DUMP), RECEIVER_EXPORTED)
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            foregroundPackage = event.packageName?.toString()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(dumpReceiver) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Writes every in-scope window's node tree to dumps/screen-<time>.txt and returns the file. */
    fun dumpScreen(): File? = runCatching {
        val allowed = app.config.current().packages + SYSTEM_UI
        val out = StringBuilder()
        for (window in windows) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString()
            if (pkg !in allowed) {
                out.append("# window ${window.title} ($pkg) skipped: out of scope\n")
                continue
            }
            out.append("# window ${window.title} ($pkg) layer=${window.layer} active=${window.isActive}\n")
            appendNode(out, root, 0)
        }
        val dir = File(getExternalFilesDir(null), "dumps").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(dir, "screen-$stamp.txt").apply { writeText(out.toString()) }
            .also { Log.i(TAG, "screen dumped to ${it.absolutePath}") }
    }.onFailure { Log.e(TAG, "screen dump failed", it) }.getOrNull()

    private fun appendNode(out: StringBuilder, node: AccessibilityNodeInfo, depth: Int) {
        val r = Rect().also(node::getBoundsInScreen)
        out.append("  ".repeat(depth))
            .append(node.className?.toString()?.substringAfterLast('.'))
            .apply { node.text?.let { append(" text=\"").append(it).append('"') } }
            .apply { node.contentDescription?.let { append(" desc=\"").append(it).append('"') } }
            .apply { node.viewIdResourceName?.let { append(" id=").append(it) } }
            .append(if (node.isClickable) " clickable" else "")
            .append(if (node.isEnabled) "" else " disabled")
            .append(" [${r.left},${r.top}][${r.right},${r.bottom}]\n")
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { appendNode(out, it, depth + 1) }
        }
    }

    companion object {
        const val ACTION_DUMP = "dev.malik.phonebot.DUMP"
        private const val SYSTEM_UI = "com.android.systemui"

        /** Set while the system has the service bound. */
        @Volatile
        var instance: BotAccessibilityService? = null
            private set
    }
}
