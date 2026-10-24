package dev.malik.phonebot.engine

import android.util.Log
import dev.malik.phonebot.PhoneBotApp
import dev.malik.phonebot.TAG
import dev.malik.phonebot.data.RunEntity
import dev.malik.phonebot.data.RunOutcome
import dev.malik.phonebot.data.StepEntity
import dev.malik.phonebot.flows.TikTokDraftFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Receives notifications from the listener, finds the flow that wants them, applies the gate and
 * records the run. Milestone 2 stops at "matched"; milestone 3 adds the state machine that drives
 * the accessibility service.
 */
class Engine(private val app: PhoneBotApp) {
    val flows: List<BotFlow> = listOf(TikTokDraftFlow)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /** Only one run at a time; later notifications wait their turn. */
    private val runLock = Mutex()

    fun onNotification(event: NotificationEvent) {
        val config = app.config.current()
        if (event.packageName in config.packages) {
            // Spike aid: every TikTok notification's wording shows up in `adb logcat -s PhoneBot`.
            Log.d(TAG, "notification pkg=${event.packageName} summary=${event.isGroupSummary} title=\"${event.title}\" text=\"${event.text}\"")
        }
        val flow = flows.firstOrNull { it.matches(event, config) } ?: return
        scope.launch {
            runCatching { handle(flow, event) }
                .onFailure { Log.e(TAG, "run for ${event.key} crashed", it) }
        }
    }

    private suspend fun handle(flow: BotFlow, event: NotificationEvent) = runLock.withLock {
        val settings = app.settings.current()
        val dao = app.db.runs()
        val outcome = if (settings.tiktokEnabled) RunOutcome.MATCHED else RunOutcome.IGNORED
        val runId = dao.insertRun(
            RunEntity(
                flowId = flow.id,
                notifKey = event.key,
                postTime = event.postTime,
                triggeredAt = System.currentTimeMillis(),
                outcome = outcome,
                reason = if (settings.tiktokEnabled) "flow engine not built yet (milestone 3)" else "switch off",
            ),
        )
        if (runId == -1L) {
            Log.d(TAG, "already handled ${event.key}@${event.postTime}")
            return@withLock
        }
        dao.insertStep(StepEntity(runId = runId, at = System.currentTimeMillis(), name = "Triggered", ok = true, detail = event.title))
        Log.i(TAG, "${flow.id} run $runId: $outcome")
    }
}
