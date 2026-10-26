package dev.malik.phonebot.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class BotSettings(
    /** FR-5: the TikTok flow's switch. Off means no scheduled checks; Run now still works. */
    val tiktokEnabled: Boolean,
    /** FR-10: walk the flow but never tap Post. Defaults on until milestone 4. */
    val dryRun: Boolean,
    /** How often the bot opens TikTok's inbox to look for a new draft. */
    val intervalMinutes: Int,
    /** Wall-clock time of the next scheduled check, 0 when none is armed. */
    val nextCheckAt: Long,
)

class SettingsRepo(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<BotSettings> = store.data.map { it.toSettings() }

    suspend fun current(): BotSettings = flow.first()

    suspend fun setTiktokEnabled(on: Boolean) = store.edit { it[TIKTOK_ENABLED] = on }

    suspend fun setDryRun(on: Boolean) = store.edit { it[DRY_RUN] = on }

    suspend fun setIntervalMinutes(minutes: Int) = store.edit { it[INTERVAL_MINUTES] = minutes }

    suspend fun setNextCheckAt(at: Long) = store.edit { it[NEXT_CHECK_AT] = at }

    private fun Preferences.toSettings() = BotSettings(
        tiktokEnabled = this[TIKTOK_ENABLED] ?: false,
        dryRun = this[DRY_RUN] ?: true,
        intervalMinutes = this[INTERVAL_MINUTES] ?: DEFAULT_INTERVAL_MINUTES,
        nextCheckAt = this[NEXT_CHECK_AT] ?: 0L,
    )

    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 30
        val INTERVAL_CHOICES = listOf(5, 15, 30, 60, 120)

        private val TIKTOK_ENABLED = booleanPreferencesKey("tiktok_enabled")
        private val DRY_RUN = booleanPreferencesKey("dry_run")
        private val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        private val NEXT_CHECK_AT = longPreferencesKey("next_check_at")
    }
}
