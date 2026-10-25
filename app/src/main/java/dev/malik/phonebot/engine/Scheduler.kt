package dev.malik.phonebot.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.malik.phonebot.TAG
import dev.malik.phonebot.app
import dev.malik.phonebot.data.RunTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Arms one exact alarm for the next inbox check. WorkManager can't go below 15 minutes and Doze
 * delays it; an exact allow-while-idle alarm fires on time even with the screen off.
 */
object Scheduler {
    private const val ACTION_CHECK = "dev.malik.phonebot.CHECK"

    /**
     * Brings the alarm in line with the settings: cancels it when the flow is off, keeps a pending
     * check that is still due within one interval, and otherwise schedules one interval from now.
     */
    suspend fun sync(context: Context, forceReschedule: Boolean = false) {
        val settings = context.app.settings
        val s = settings.current()
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pi = checkIntent(context)
        if (!s.tiktokEnabled) {
            alarms.cancel(pi)
            settings.setNextCheckAt(0)
            return
        }
        val now = System.currentTimeMillis()
        val interval = s.intervalMinutes * 60_000L
        val keep = !forceReschedule && s.nextCheckAt in (now + 1)..(now + interval)
        val at = if (keep) s.nextCheckAt else now + interval
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        settings.setNextCheckAt(at)
        Log.i(TAG, "next check at $at (in ${(at - now) / 1000}s)")
    }

    private fun checkIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CheckReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    internal fun isCheck(intent: Intent) = intent.action == ACTION_CHECK
}

/** Alarm fired: run a check and arm the next one. Boot or app update: re-arm only. */
class CheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val isCheck = Scheduler.isCheck(intent)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (isCheck) {
                    context.app.engine.check(RunTrigger.SCHEDULE)
                    Scheduler.sync(context, forceReschedule = true)
                } else {
                    Scheduler.sync(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
