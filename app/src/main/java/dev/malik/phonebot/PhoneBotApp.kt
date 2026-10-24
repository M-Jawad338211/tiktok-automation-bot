package dev.malik.phonebot

import android.app.Application
import android.content.Context
import dev.malik.phonebot.config.ConfigStore
import dev.malik.phonebot.data.BotDatabase
import dev.malik.phonebot.data.SettingsRepo
import dev.malik.phonebot.engine.Engine

const val TAG = "PhoneBot"

/** Holds the app-wide singletons; both services and the UI reach them through [Context.app]. */
class PhoneBotApp : Application() {
    val db by lazy { BotDatabase.create(this) }
    val settings by lazy { SettingsRepo(this) }
    val config by lazy { ConfigStore(this) }
    val engine by lazy { Engine(this) }
}

val Context.app: PhoneBotApp get() = applicationContext as PhoneBotApp
