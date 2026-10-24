package dev.malik.phonebot.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class BotSettings(
    /** FR-5: the TikTok flow's switch. Off means notifications are logged as ignored. */
    val tiktokEnabled: Boolean,
    /** FR-10: walk the flow but never tap Post. Defaults on until milestone 4. */
    val dryRun: Boolean,
)

class SettingsRepo(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<BotSettings> = store.data.map { it.toSettings() }

    suspend fun current(): BotSettings = flow.first()

    suspend fun setTiktokEnabled(on: Boolean) = store.edit { it[TIKTOK_ENABLED] = on }

    suspend fun setDryRun(on: Boolean) = store.edit { it[DRY_RUN] = on }

    private fun Preferences.toSettings() = BotSettings(
        tiktokEnabled = this[TIKTOK_ENABLED] ?: false,
        dryRun = this[DRY_RUN] ?: true,
    )

    private companion object {
        val TIKTOK_ENABLED = booleanPreferencesKey("tiktok_enabled")
        val DRY_RUN = booleanPreferencesKey("dry_run")
    }
}
