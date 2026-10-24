package dev.malik.phonebot.engine

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import dev.malik.phonebot.config.BotConfig

/** The parts of a posted notification that flows match on. */
data class NotificationEvent(
    val key: String,
    val packageName: String,
    val postTime: Long,
    val title: String,
    val text: String,
    val isGroupSummary: Boolean,
    val contentIntent: PendingIntent?,
) {
    companion object {
        fun from(sbn: StatusBarNotification): NotificationEvent {
            val n = sbn.notification
            return NotificationEvent(
                key = sbn.key,
                packageName = sbn.packageName,
                postTime = sbn.postTime,
                title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                contentIntent = n.contentIntent,
            )
        }
    }
}

/**
 * NFR-8: a flow is a trigger matcher plus (from milestone 3) its state machine.
 * Adding WhatsApp or Discord later means a new class registered in [Engine.flows], not an engine change.
 */
interface BotFlow {
    val id: String
    fun matches(event: NotificationEvent, config: BotConfig): Boolean
}
