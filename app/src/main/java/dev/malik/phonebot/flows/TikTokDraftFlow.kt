package dev.malik.phonebot.flows

import dev.malik.phonebot.config.BotConfig
import dev.malik.phonebot.engine.BotFlow
import dev.malik.phonebot.engine.NotificationEvent

/** Flow 1: OnePublish's "Your content … is ready" inbox notification from TikTok. */
object TikTokDraftFlow : BotFlow {
    override val id = "tiktok_draft"

    override fun matches(event: NotificationEvent, config: BotConfig): Boolean =
        event.packageName in config.packages &&
            !event.isGroupSummary &&
            config.titleRegex.containsMatchIn(event.title) &&
            config.textRegex.containsMatchIn(event.text)
}
