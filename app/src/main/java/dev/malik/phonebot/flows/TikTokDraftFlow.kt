package dev.malik.phonebot.flows

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import dev.malik.phonebot.config.BotConfig
import dev.malik.phonebot.config.BotConfig.Companion.normalize
import dev.malik.phonebot.data.RunOutcome
import dev.malik.phonebot.engine.BotFlow
import dev.malik.phonebot.engine.FlowResult
import dev.malik.phonebot.engine.FlowRun
import dev.malik.phonebot.service.bounds
import dev.malik.phonebot.service.clickableAncestor
import dev.malik.phonebot.service.subtree
import kotlinx.coroutines.delay
import java.io.File

/**
 * Flow 1: TikTok never pushes the OnePublish draft notification, so each check opens TikTok →
 * Inbox → System notifications → TikTok Platform, takes the newest "Your content from OnePublish
 * is ready" card, and if it arrived after the last posted one: View more → Next → Post.
 */
object TikTokDraftFlow : BotFlow {
    override val id = "tiktok_draft"

    override suspend fun run(run: FlowRun): FlowResult {
        val c = run.config
        openTikTok(run)
        val listLoadedAt = openSystemList(run)

        run.nodes().firstOrNull { normalize(it.text) == c.platformFilter }?.let {
            run.service.click(it, c.forbidden)
            run.step("Filter", c.platformFilter)
            delay(1_500)
        }

        val card = newestDraft(run) ?: return leave(run, FlowResult(RunOutcome.NO_NEW_DRAFT, "no OnePublish draft in System notifications"))
        val lastPosted = run.app.db.runs().lastPostedItemAt(id)
        val ageLabel = card.subtree().map { normalize(it.text) }.firstNotNullOfOrNull { AGE.find(it)?.value }
            ?: run.fail("Find draft", "can't read the draft's age")
        val age = parseAge(ageLabel) ?: run.fail("Find draft", "unknown age label \"$ageLabel\"")
        val earliest = listLoadedAt - age.last
        val latest = listLoadedAt - age.first
        if (lastPosted != null && earliest <= lastPosted) {
            return leave(run, FlowResult(RunOutcome.NO_NEW_DRAFT, "newest draft ($ageLabel old) is already posted"))
        }
        run.step("Find draft", "newest draft is $ageLabel old")

        val viewMore = card.subtree().firstOrNull { normalize(it.text) == c.viewMore } ?: card
        run.service.click(viewMore, c.forbidden) || run.fail("Open draft", "click on ${c.viewMore} rejected")

        val next = run.waitFor("Editor", c.editorTimeoutMs) { run.button(c.next) }
        run.step("Editor", "Next is enabled")
        run.pause()
        run.service.click(next, c.forbidden) || run.fail("Editor", "click on ${c.next} rejected")

        val post = run.waitFor("Post page", 30_000) { run.button(c.post)?.takeIf { run.button(c.next) == null } }
        val shot = run.service.screenshot(File(run.app.getExternalFilesDir(null), "screens/run-${run.runId}-postpage.png"))
        run.step("Post page", shot?.name)

        if (run.settings.dryRun) {
            run.step("Dry run", "would tap ${c.post}")
            // Back to the list so the next run starts clean; Home happens in the engine.
            run.service.back()
            run.pause()
            run.service.back()
            run.pause()
            return FlowResult(RunOutcome.DRY_RUN, "stopped before ${c.post} (dry run); draft $ageLabel old", latest)
        }

        run.pause()
        run.service.click(post, c.forbidden) || run.fail("Post page", "click on ${c.post} rejected")
        run.step("Post", "tapped")
        // Post was tapped: from here a failure is never retried, it could double-post.
        run.waitFor("Confirm", 60_000) { Unit.takeIf { run.button(c.post) == null && run.button(c.next) == null } }
        run.step("Confirm", "post page closed")
        return FlowResult(RunOutcome.SUCCESS, "posted draft $ageLabel old", latest)
    }

    private suspend fun openTikTok(run: FlowRun) {
        val pm = run.service.packageManager
        val (pkg, launch) = run.config.packages.firstNotNullOfOrNull { p -> pm.getLaunchIntentForPackage(p)?.let { p to it } }
            ?: run.fail("Open TikTok", "TikTok is not installed")
        run.service.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        run.waitFor("Open TikTok", 15_000) { pkg.takeIf { run.service.foregroundPackage() == it } }
        run.step("Open TikTok", pkg)
        delay(1_500)
    }

    /**
     * From wherever TikTok resumed, reaches Inbox and opens System notifications fresh (ages on an
     * already-open list go stale). Returns the time the list appeared, the reference for its ages.
     */
    private suspend fun openSystemList(run: FlowRun): Long {
        val c = run.config
        repeat(8) {
            val nodes = run.nodes()
            val onList = isSystemList(nodes, c)
            val inboxRow = nodes.firstOrNull { normalize(it.text) == c.systemNotifications }.takeIf { !onList }
            val inboxTab = nodes.firstOrNull { normalize(it.contentDescription) == c.inboxTab && it.isClickable }
            when {
                inboxRow != null -> {
                    run.service.click(inboxRow, c.forbidden)
                    run.waitFor("System notifications", 15_000) { Unit.takeIf { isSystemList(run.nodes(), c) } }
                    run.step("System notifications", "opened")
                    return System.currentTimeMillis()
                }
                run.service.foregroundPackage() !in c.packages -> openTikTok(run)
                inboxTab != null && !onList -> run.service.click(inboxTab, c.forbidden)
                else -> run.service.back()
            }
            run.pause()
        }
        run.fail("Navigate", "could not reach ${c.inboxTab} → ${c.systemNotifications}")
    }

    private fun isSystemList(nodes: List<AccessibilityNodeInfo>, c: BotConfig) =
        nodes.any { normalize(it.text) == c.platformFilter } && nodes.any { normalize(it.text) == c.systemNotifications }

    /** The top-most card whose title matches, waiting a few seconds for the filtered list to load. */
    private suspend fun newestDraft(run: FlowRun): AccessibilityNodeInfo? {
        val end = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < end) {
            val card = run.nodes()
                .filter { run.config.itemRegex.containsMatchIn(normalize(it.text)) }
                .mapNotNull { it.clickableAncestor() }
                .filter { !it.bounds().isEmpty }
                .minByOrNull { it.bounds().top }
            if (card != null) return card
            delay(FlowRun.POLL_MS)
        }
        return null
    }

    private suspend fun leave(run: FlowRun, result: FlowResult): FlowResult {
        run.step(result.outcome.name, result.reason)
        run.service.back()
        run.pause()
        return result
    }

    private fun FlowRun.nodes() = service.roots(config.packages).flatMap { it.subtree() }

    /** A node labelled exactly [label] whose clickable target is enabled. */
    private fun FlowRun.button(label: String): AccessibilityNodeInfo? =
        nodes().firstOrNull { normalize(it.text) == label && it.clickableAncestor()?.isEnabled == true }

    private val AGE = Regex("(\\d+)\\s*([smhdw])$|(just now|now)$", RegexOption.IGNORE_CASE)

    /** TikTok's relative age label as a range of milliseconds: "12m" means 12:00 to 12:59 ago. */
    internal fun parseAge(label: String): LongRange? {
        val m = AGE.find(label) ?: return null
        if (m.groupValues[3].isNotEmpty()) return 0L..60_000L
        val n = m.groupValues[1].toLong()
        val unit = when (m.groupValues[2].lowercase()) {
            "s" -> 1_000L
            "m" -> 60_000L
            "h" -> 3_600_000L
            "d" -> 86_400_000L
            "w" -> 604_800_000L
            else -> return null
        }
        return n * unit..(n + 1) * unit
    }
}
