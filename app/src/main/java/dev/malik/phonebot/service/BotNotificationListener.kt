package dev.malik.phonebot.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.malik.phonebot.TAG
import dev.malik.phonebot.app
import dev.malik.phonebot.engine.NotificationEvent

/** The trigger: hands every posted notification to the engine, which keeps only what a flow matches. */
class BotNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "notification listener connected")
        // Edge case 1: a draft notification that arrived while the process was dead is still active.
        runCatching { activeNotifications }.getOrNull()?.forEach(::dispatch)
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = dispatch(sbn)

    private fun dispatch(sbn: StatusBarNotification) {
        runCatching { app.engine.onNotification(NotificationEvent.from(sbn)) }
            .onFailure { Log.e(TAG, "failed to handle ${sbn.key}", it) }
    }
}
