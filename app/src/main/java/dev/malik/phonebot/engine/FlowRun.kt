package dev.malik.phonebot.engine

import android.util.Log
import dev.malik.phonebot.PhoneBotApp
import dev.malik.phonebot.TAG
import dev.malik.phonebot.config.BotConfig
import dev.malik.phonebot.data.BotSettings
import dev.malik.phonebot.data.StepEntity
import dev.malik.phonebot.service.BotAccessibilityService
import kotlinx.coroutines.delay

/** What a flow gets for one run: the hands, the config, and the run log. */
class FlowRun(
    val app: PhoneBotApp,
    val service: BotAccessibilityService,
    val config: BotConfig,
    val settings: BotSettings,
    val runId: Long,
) {
    suspend fun step(name: String, detail: String? = null, ok: Boolean = true) {
        Log.i(TAG, "run $runId: $name${detail?.let { " — $it" } ?: ""}")
        app.db.runs().insertStep(StepEntity(runId = runId, at = System.currentTimeMillis(), name = name, ok = ok, detail = detail))
    }

    fun fail(state: String, reason: String): Nothing = throw FlowFailure(state, reason)

    /**
     * Polls every 500 ms until [probe] returns non-null, or fails [state] after [timeoutMs].
     * The engine never acts blind: every action waits for the screen it expects.
     */
    suspend fun <T : Any> waitFor(state: String, timeoutMs: Long, probe: suspend () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            runCatching { probe() }.getOrNull()?.let { return it }
            if (System.currentTimeMillis() > end) fail(state, "timed out after ${timeoutMs / 1000}s")
            delay(POLL_MS)
        }
    }

    /** Human-paced gap between actions (SRS risk table: 0.8–2 s). */
    suspend fun pause() = delay((800L..1600L).random())

    companion object {
        const val POLL_MS = 500L
    }
}
