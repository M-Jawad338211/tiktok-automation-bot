package dev.malik.phonebot.engine

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import kotlinx.coroutines.delay

/**
 * Wakes the screen and dismisses a swipe-only lock before a run, and puts the phone back to sleep
 * afterwards if it was asleep. A PIN/pattern lock that is engaged cannot be passed (SRS: out of scope).
 */
class DeviceController(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    fun isReady() = power.isInteractive && !keyguard.isKeyguardLocked

    /** Returns true when the phone was asleep or locked, so the caller relocks it after the run. */
    suspend fun prepare(run: FlowRun): Boolean {
        if (isReady()) return false
        if (keyguard.isKeyguardLocked && keyguard.isKeyguardSecure && keyguard.isDeviceLocked) {
            run.fail("Prepare device", "phone is locked with a PIN/pattern")
        }
        // Started from the accessibility service context, which is exempt from background-start limits.
        run.service.startActivity(Intent(run.service, WakeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(10) {
            delay(500)
            if (isReady()) return true
        }
        run.fail("Prepare device", "screen did not wake or unlock")
    }
}

/** Invisible activity that turns the screen on and asks the system to drop a swipe lock. */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finish()
            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        })
    }
}
