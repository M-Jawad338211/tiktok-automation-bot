package dev.malik.phonebot.engine

import dev.malik.phonebot.data.RunOutcome

/**
 * NFR-8: a flow is one automation the engine can run. Adding WhatsApp or Discord later means a
 * new class registered in [Engine.flows], not an engine change.
 */
interface BotFlow {
    val id: String

    /** Walks the target app; ends by returning a result or calling [FlowRun.fail]. */
    suspend fun run(run: FlowRun): FlowResult
}

data class FlowResult(
    val outcome: RunOutcome,
    val reason: String? = null,
    /** For SUCCESS: when the posted item arrived, so later checks skip it. */
    val itemAt: Long? = null,
)

/** Thrown by [FlowRun.fail]; [state] names the step that gave up. */
class FlowFailure(val state: String, message: String) : Exception(message)
