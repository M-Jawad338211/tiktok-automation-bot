package dev.malik.phonebot.engine

import android.util.Log
import dev.malik.phonebot.PhoneBotApp
import dev.malik.phonebot.TAG
import dev.malik.phonebot.data.RunEntity
import dev.malik.phonebot.data.RunOutcome
import dev.malik.phonebot.data.RunTrigger
import dev.malik.phonebot.data.StepEntity
import dev.malik.phonebot.flows.TikTokDraftFlow
import dev.malik.phonebot.service.BotAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Runs flows one at a time, wraps each in the device prep, the wall-clock cap and the run log,
 * and turns every exception into a FAILED run instead of a crash (NFR-6).
 */
class Engine(private val app: PhoneBotApp) {
    val flows: List<BotFlow> = listOf(TikTokDraftFlow)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /** Only one run at a time; a check that fires during a run waits its turn. */
    private val runLock = Mutex()

    fun check(trigger: RunTrigger, flow: BotFlow = TikTokDraftFlow) {
        scope.launch {
            runCatching { runLock.withLock { run(flow, trigger) } }
                .onFailure { Log.e(TAG, "${flow.id} check crashed", it) }
        }
    }

    private suspend fun run(flow: BotFlow, trigger: RunTrigger) {
        val dao = app.db.runs()
        val started = System.currentTimeMillis()
        val base = RunEntity(flowId = flow.id, trigger = trigger, startedAt = started, outcome = RunOutcome.RUNNING)
        val runId = dao.insertRun(base)
        dao.insertStep(StepEntity(runId = runId, at = started, name = "Triggered", ok = true, detail = trigger.name))

        val service = BotAccessibilityService.instance
        if (service == null) {
            finish(base.copy(id = runId, outcome = RunOutcome.FAILED, reason = "accessibility service is off", durationMs = 0))
            return
        }
        val run = FlowRun(app, service, app.config.current(), app.settings.current(), runId)
        val device = DeviceController(app)
        var relock = false
        var screenshot: String? = null
        val result = try {
            withTimeout(RUN_CAP_MS) {
                relock = device.prepare(run)
                flow.run(run)
            }.also { service.home() }
        } catch (e: FlowFailure) {
            screenshot = failureShot(service, runId)
            run.step(e.state, e.message, ok = false)
            FlowResult(RunOutcome.FAILED, "${e.state}: ${e.message}")
        } catch (e: TimeoutCancellationException) {
            screenshot = failureShot(service, runId)
            FlowResult(RunOutcome.FAILED, "run took longer than ${RUN_CAP_MS / 1000}s")
        } catch (e: Exception) {
            Log.e(TAG, "run $runId crashed", e)
            screenshot = failureShot(service, runId)
            FlowResult(RunOutcome.FAILED, "crash: ${e.message}")
        }
        // On failure TikTok is left as it is so the screen can be inspected; the next run backs out.
        if (relock) service.lockScreen()
        finish(
            base.copy(
                id = runId,
                outcome = result.outcome,
                reason = result.reason,
                durationMs = System.currentTimeMillis() - started,
                screenshotPath = screenshot,
                itemAt = result.itemAt,
            ),
        )
    }

    private suspend fun failureShot(service: BotAccessibilityService, runId: Long): String? =
        service.screenshot(File(app.getExternalFilesDir(null), "screens/run-$runId-failed.png"))?.absolutePath

    private suspend fun finish(row: RunEntity) {
        app.db.runs().updateRun(row)
        Log.i(TAG, "run ${row.id}: ${row.outcome} ${row.reason.orEmpty()}")
        Notifier.runFinished(app, row)
    }

    private companion object {
        /** SRS caps a run at 120 s; TikTok's draft download (up to 2 min on slow links) plus the 60 s confirm wait needs more. */
        const val RUN_CAP_MS = 240_000L
    }
}
