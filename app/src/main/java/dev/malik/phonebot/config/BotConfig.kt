package dev.malik.phonebot.config

import android.content.Context
import android.util.Log
import dev.malik.phonebot.TAG
import org.json.JSONObject
import java.io.File

/**
 * FR-9: everything that depends on TikTok's wording lives in selectors.json, so a TikTok change is
 * an `adb push`, not a rebuild. Labels are matched exactly after stripping TikTok's invisible
 * direction marks (see [normalize]). Defaults recorded on TikTok 47.4.3, English.
 */
data class BotConfig(
    /** TikTok package names, first installed one wins. */
    val packages: List<String>,
    /** Bottom-bar tab, matched on content description. */
    val inboxTab: String,
    /** Row on the Inbox screen and title of the list it opens. */
    val systemNotifications: String,
    /** Filter chip on the System notifications list. */
    val platformFilter: String,
    /** Title of a card that holds a ready draft. */
    val itemRegex: Regex,
    /** Link inside the card that opens the editor. */
    val viewMore: String,
    val next: String,
    val post: String,
    /** Never clicked, whatever a selector says. */
    val forbidden: List<String>,
    /** Editor wait; TikTok shows "Loading…" while it downloads the draft: ~15 s on good Wi-Fi, over 45 s on a slow link. */
    val editorTimeoutMs: Long,
) {
    companion object {
        val DEFAULT = BotConfig(
            packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            inboxTab = "Inbox",
            systemNotifications = "System notifications",
            platformFilter = "TikTok Platform",
            itemRegex = Regex("^Your content from OnePublish is ready"),
            viewMore = "View more",
            next = "Next",
            post = "Post",
            forbidden = listOf("Your Story", "Drafts", "Delete", "Discard", "Log out", "Remove", "Report"),
            editorTimeoutMs = 120_000,
        )

        fun parse(json: String): BotConfig {
            val o = JSONObject(json)
            val d = DEFAULT
            fun str(key: String, def: String) = o.optString(key).takeIf { it.isNotEmpty() } ?: def
            fun list(key: String, def: List<String>) =
                o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: def
            return BotConfig(
                packages = list("packages", d.packages),
                inboxTab = str("inboxTab", d.inboxTab),
                systemNotifications = str("systemNotifications", d.systemNotifications),
                platformFilter = str("platformFilter", d.platformFilter),
                itemRegex = o.optString("itemRegex").takeIf { it.isNotEmpty() }?.let(::Regex) ?: d.itemRegex,
                viewMore = str("viewMore", d.viewMore),
                next = str("next", d.next),
                post = str("post", d.post),
                forbidden = list("forbidden", d.forbidden),
                editorTimeoutMs = o.optLong("editorTimeoutMs", d.editorTimeoutMs),
            )
        }

        /** TikTok wraps labels in LRM/RLM, bidi overrides and word joiners ("1⁠2⁠m"); drop them. */
        fun normalize(s: CharSequence?): String =
            s?.toString()?.replace(INVISIBLE, "")?.trim().orEmpty()

        private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]")
    }
}

/**
 * Reads /sdcard/Android/data/<app id>/files/selectors.json, which `adb push` can write without root.
 * Re-reads only when the file changes; a broken file falls back to the built-in default and logs why.
 */
class ConfigStore(context: Context) {
    val file = File(context.getExternalFilesDir(null), "selectors.json")

    private var cached: BotConfig = BotConfig.DEFAULT
    private var cachedStamp = -1L
    var lastError: String? = null
        private set

    @Synchronized
    fun current(): BotConfig {
        val stamp = if (file.exists()) file.lastModified() else 0L
        if (stamp == cachedStamp) return cached
        cachedStamp = stamp
        cached = if (stamp == 0L) {
            lastError = null
            BotConfig.DEFAULT
        } else {
            runCatching { BotConfig.parse(file.readText()) }
                .onSuccess { lastError = null }
                .getOrElse {
                    lastError = "selectors.json ignored: ${it.message}"
                    Log.w(TAG, lastError!!)
                    BotConfig.DEFAULT
                }
        }
        return cached
    }
}
