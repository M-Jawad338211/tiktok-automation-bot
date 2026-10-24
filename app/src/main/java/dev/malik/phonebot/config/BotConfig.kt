package dev.malik.phonebot.config

import android.content.Context
import android.util.Log
import dev.malik.phonebot.TAG
import org.json.JSONObject
import java.io.File

/**
 * FR-9: everything that depends on TikTok's wording lives in selectors.json, so a TikTok change is
 * an `adb push`, not a rebuild. Button selectors, the dialog table and timeouts join this file in
 * milestone 3, once the spike has recorded the real screens.
 */
data class BotConfig(
    val packages: List<String>,
    val titleRegex: Regex,
    val textRegex: Regex,
) {
    companion object {
        val DEFAULT = BotConfig(
            packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            titleRegex = Regex("^Your content from OnePublish is ready"),
            textRegex = Regex("^Edit your video before sharing"),
        )

        fun parse(json: String): BotConfig {
            val o = JSONObject(json)
            val trigger = o.optJSONObject("trigger") ?: JSONObject()
            val pkgs = trigger.optJSONArray("packages")
            return BotConfig(
                packages = if (pkgs == null) DEFAULT.packages else List(pkgs.length()) { pkgs.getString(it) },
                titleRegex = trigger.optString("titleRegex").takeIf { it.isNotEmpty() }?.let(::Regex) ?: DEFAULT.titleRegex,
                textRegex = trigger.optString("textRegex").takeIf { it.isNotEmpty() }?.let(::Regex) ?: DEFAULT.textRegex,
            )
        }
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
