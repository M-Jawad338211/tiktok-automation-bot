package dev.malik.phonebot.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.malik.phonebot.app
import dev.malik.phonebot.data.RunEntity
import dev.malik.phonebot.data.RunTrigger
import dev.malik.phonebot.data.SettingsRepo
import dev.malik.phonebot.engine.Scheduler
import dev.malik.phonebot.data.RunOutcome
import dev.malik.phonebot.service.BotAccessibilityService
import dev.malik.phonebot.service.BotTileService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(SetupStatus.read(context)) }
    val settings by context.app.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val runs by context.app.db.runs().recentRuns().collectAsStateWithLifecycle(initialValue = emptyList())

    // Permissions are granted in system screens, so re-check whenever we come back.
    LifecycleResumeEffect(Unit) {
        status = SetupStatus.read(context)
        onPauseOrDispose { }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = SetupStatus.read(context)
    }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Settings screen not found", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("PhoneBot", style = MaterialTheme.typography.headlineMedium) }

            item {
                Section("Setup") {
                    StatusRow("Accessibility service", status.accessibility) { open(SetupIntents.accessibility()) }
                    StatusRow("Bot notifications", status.postNotifications) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    StatusRow("Battery: unrestricted", status.batteryExempt) { open(SetupIntents.batteryExemption(context)) }
                    StatusRow(status.tiktok?.let { "TikTok $it" } ?: "TikTok not installed", status.tiktok != null, action = null)
                    context.app.config.lastError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Section("Flows") {
                    SwitchRow("TikTok draft autopost", "Checks TikTok's inbox and posts the newest OnePublish draft", settings?.tiktokEnabled == true) { on ->
                        scope.launch {
                            context.app.settings.setTiktokEnabled(on)
                            Scheduler.sync(context)
                            BotTileService.refresh(context)
                        }
                    }
                    Text("Check every", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsRepo.INTERVAL_CHOICES.forEach { minutes ->
                            FilterChip(
                                selected = settings?.intervalMinutes == minutes,
                                onClick = {
                                    scope.launch {
                                        context.app.settings.setIntervalMinutes(minutes)
                                        Scheduler.sync(context, forceReschedule = true)
                                    }
                                },
                                label = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} h") },
                            )
                        }
                    }
                    Text(
                        settings?.nextCheckAt?.takeIf { it > 0 }?.let { "Next check: ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}" }
                            ?: "No check scheduled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchRow("Dry run", "Walks the flow but never taps Post", settings?.dryRun != false) { on ->
                        scope.launch { context.app.settings.setDryRun(on) }
                    }
                }
            }

            item {
                Section("Debug") {
                    OutlinedButton(onClick = {
                        context.app.engine.check(RunTrigger.MANUAL)
                        Toast.makeText(context, "Checking TikTok's inbox", Toast.LENGTH_SHORT).show()
                    }) { Text("Run now") }
                    OutlinedButton(onClick = {
                        val service = BotAccessibilityService.instance
                        if (service == null) {
                            Toast.makeText(context, "Turn on the accessibility service first", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        Toast.makeText(context, "Open TikTok now; dumping in 5 s", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            delay(5_000)
                            val file = service.dumpScreen()
                            Toast.makeText(context, file?.let { "Saved ${it.name}" } ?: "Dump failed", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Dump screen in 5 s") }
                }
            }

            item { Text("Last runs", style = MaterialTheme.typography.titleMedium) }
            if (runs.isEmpty()) {
                item { Text("No runs yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(runs, key = { it.id }) { RunRow(it) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, action: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(10.dp), shape = CircleShape, color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error) {}
        Spacer(Modifier.size(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (!ok && action != null) TextButton(onClick = action) { Text("Fix") }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RunRow(run: RunEntity) {
    val color = when (run.outcome) {
        RunOutcome.SUCCESS, RunOutcome.DRY_RUN -> Color(0xFF2E7D32)
        RunOutcome.FAILED, RunOutcome.INTERRUPTED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(run.outcome.name, color = color, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.size(8.dp))
            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(run.startedAt)), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(8.dp))
            Text(run.trigger.name.lowercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        run.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
