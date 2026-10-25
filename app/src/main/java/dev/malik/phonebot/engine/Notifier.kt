package dev.malik.phonebot.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.malik.phonebot.R
import dev.malik.phonebot.data.RunEntity
import dev.malik.phonebot.data.RunOutcome
import dev.malik.phonebot.ui.MainActivity

/** FR-7: the bot's own notifications. Quiet checks (nothing new) post nothing. */
object Notifier {
    private const val CHANNEL = "runs"

    fun runFinished(context: Context, run: RunEntity) {
        val title = when (run.outcome) {
            RunOutcome.SUCCESS -> "TikTok draft posted"
            RunOutcome.DRY_RUN -> "Dry run reached the post page"
            RunOutcome.FAILED -> "TikTok autopost failed"
            else -> return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Run results", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bot)
            .setContentTitle(title)
            .setContentText(run.reason)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(run.id.toInt(), n)
    }
}
