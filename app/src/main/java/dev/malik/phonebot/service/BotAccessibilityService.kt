package dev.malik.phonebot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.malik.phonebot.TAG
import dev.malik.phonebot.app
import dev.malik.phonebot.config.BotConfig
import dev.malik.phonebot.data.RunTrigger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The hands: reads TikTok's view tree and clicks, taps, goes Back/Home, locks and screenshots.
 * Runs in the app process and holds the background-activity-start exemption, so the engine
 * launches TikTok through it.
 */
class BotAccessibilityService : AccessibilityService() {

    // `adb shell am broadcast -a dev.malik.phonebot.DUMP` dumps whatever TikTok shows right now.
    // uiautomator often can't dump TikTok because its video never lets the screen go idle.
    // Debug builds also accept `-a dev.malik.phonebot.RUN`, the same as tapping Run now.
    private val dumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_DUMP -> dumpScreen()
                ACTION_RUN -> if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) app.engine.check(RunTrigger.MANUAL)
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        runCatching { unregisterReceiver(dumpReceiver) }
        registerReceiver(dumpReceiver, IntentFilter(ACTION_DUMP).apply { addAction(ACTION_RUN) }, RECEIVER_EXPORTED)
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(dumpReceiver) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Package of the focused window, or null on the lock screen / between windows. */
    fun foregroundPackage(): String? =
        windows.firstOrNull { it.isActive }?.root?.packageName?.toString()

    /** Roots of every window that belongs to [packages], top layer first (dialogs before the screen). */
    fun roots(packages: Collection<String>): List<AccessibilityNodeInfo> =
        windows.sortedByDescending { it.layer }
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() in packages }

    /**
     * Clicks [node], or its nearest clickable ancestor. Refuses anything whose label is in
     * [BotConfig.forbidden]; falls back to a tap on the node's centre if the click is rejected.
     */
    suspend fun click(node: AccessibilityNodeInfo, forbidden: List<String>): Boolean {
        val target = node.clickableAncestor() ?: node
        val labels = (target.subtree() + node).flatMap { listOf(BotConfig.normalize(it.text), BotConfig.normalize(it.contentDescription)) }
        labels.firstOrNull { it in forbidden }?.let {
            Log.w(TAG, "refused to click \"$it\"")
            return false
        }
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect().also(target::getBoundsInScreen)
        return !r.isEmpty && tap(r.exactCenterX(), r.exactCenterY())
    }

    suspend fun tap(x: Float, y: Float): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) = cont.resume(true)
            override fun onCancelled(g: GestureDescription) = cont.resume(false)
        }
        if (!dispatchGesture(gesture, callback, null)) cont.resume(false)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)

    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)

    fun lockScreen() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    /** Saves a PNG of the screen, for failure reports and the dry-run post page. */
    suspend fun screenshot(file: File): File? = suspendCancellableCoroutine { cont ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val saved = runCatching {
                    val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)!!
                    file.parentFile?.mkdirs()
                    file.outputStream().use { hw.copy(Bitmap.Config.ARGB_8888, false).compress(Bitmap.CompressFormat.PNG, 100, it) }
                    result.hardwareBuffer.close()
                    file
                }.onFailure { Log.e(TAG, "screenshot failed", it) }.getOrNull()
                cont.resume(saved)
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "screenshot failed: $errorCode")
                cont.resume(null)
            }
        })
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
        const val ACTION_RUN = "dev.malik.phonebot.RUN"
        const val SYSTEM_UI = "com.android.systemui"

        /** Set while the system has the service bound. */
        @Volatile
        var instance: BotAccessibilityService? = null
            private set
    }
}

/** Depth-first list of this node and everything below it. */
fun AccessibilityNodeInfo.subtree(): List<AccessibilityNodeInfo> {
    val out = ArrayList<AccessibilityNodeInfo>()
    fun walk(n: AccessibilityNodeInfo) {
        out += n
        for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
    }
    walk(this)
    return out
}

fun AccessibilityNodeInfo.clickableAncestor(): AccessibilityNodeInfo? {
    var n: AccessibilityNodeInfo? = this
    while (n != null && !n.isClickable) n = n.parent
    return n
}

fun AccessibilityNodeInfo.bounds(): Rect = Rect().also(::getBoundsInScreen)
