package io.github.markyu0401.everheard.ui.schedule

import android.app.TimePickerDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.schedule.ScheduleMode
import io.github.markyu0401.everheard.capture.schedule.ScheduleRule
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

class ScheduleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardTheme {
                val viewModel: ScheduleViewModel = viewModel()
                val rules by viewModel.rules.collectAsStateWithLifecycle()
                ScheduleScreen(
                    rules = rules,
                    onSave = viewModel::save,
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun ScheduleScreen(
    rules: List<ScheduleRule>,
    onSave: (List<ScheduleRule>) -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<ScheduleRule?>(null) }
    var creatingMode by remember { mutableStateOf<ScheduleMode?>(null) }
    val locale = LocalConfiguration.current.locales[0]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp)
    ) {
        BackButton(onClick = onBack)

        LargeTitle(
            title = stringResource(R.string.schedule_title),
            subtitle = stringResource(R.string.schedule_subtitle)
        )

        Spacer(Modifier.height(20.dp))

        if (rules.isEmpty()) {
            GroupedCard {
                SettingsRow(
                    title = stringResource(R.string.schedule_empty_title),
                    subtitle = stringResource(R.string.schedule_empty),
                    showDivider = true
                )
                SettingsRow(
                    icon = Icons.Filled.Add,
                    title = stringResource(R.string.schedule_add_rule),
                    showDivider = false,
                    onClick = { creatingMode = ScheduleMode.BLOCK }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    GroupedCard {
                        rules.forEachIndexed { index, rule ->
                            SettingsRow(
                                title = if (rule.mode == ScheduleMode.BLOCK) {
                                    stringResource(R.string.schedule_block)
                                } else {
                                    stringResource(R.string.schedule_allow)
                                },
                                subtitle = scheduleSubtitle(rule, locale),
                                showDivider = index != rules.lastIndex,
                                onClick = { editing = rule }
                            )
                        }
                    }
                }
                item {
                    GroupedCard {
                        SettingsRow(
                            icon = Icons.Filled.Add,
                            title = stringResource(R.string.schedule_add_rule),
                            showDivider = false,
                            onClick = { creatingMode = ScheduleMode.BLOCK }
                        )
                    }
                }
            }
        }
    }

    val ruleBeingEdited = editing
    if (ruleBeingEdited != null) {
        ScheduleRuleEditor(
            initial = ruleBeingEdited,
            onSave = { updated ->
                onSave(rules.map { if (it.id == updated.id) updated else it })
                editing = null
            },
            onDelete = {
                onSave(rules - ruleBeingEdited)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    val modeBeingCreated = creatingMode
    if (modeBeingCreated != null) {
        ScheduleRuleEditor(
            initial = ScheduleRule(
                id = System.currentTimeMillis(),
                days = emptySet(),
                startMinute = 9 * 60,
                endMinute = 17 * 60,
                mode = modeBeingCreated
            ),
            onSave = { created ->
                onSave(rules + created)
                creatingMode = null
            },
            onDelete = null,
            onDismiss = { creatingMode = null }
        )
    }
}

@Composable
private fun scheduleSubtitle(rule: ScheduleRule, locale: Locale): String {
    val days = if (rule.days.isEmpty() || rule.days.size == 7) {
        stringResource(R.string.schedule_every_day)
    } else {
        DayOfWeek.values()
            .filter { it in rule.days }
            .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    return "$days · ${formatMinute(rule.startMinute)} – ${formatMinute(rule.endMinute)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleRuleEditor(
    initial: ScheduleRule,
    onSave: (ScheduleRule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var days by remember { mutableStateOf(initial.days) }
    var startMinute by remember { mutableIntStateOf(initial.startMinute) }
    var endMinute by remember { mutableIntStateOf(initial.endMinute) }
    var mode by remember { mutableStateOf(initial.mode) }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (onDelete == null) {
                    stringResource(R.string.schedule_new_rule)
                } else {
                    stringResource(R.string.schedule_edit_rule)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.schedule_days),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = {
                                days = if (day in days) days - day else days + day
                            },
                            label = {
                                Text(day.getDisplayName(TextStyle.SHORT, locale))
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.schedule_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == ScheduleMode.BLOCK,
                        onClick = { mode = ScheduleMode.BLOCK },
                        label = { Text(stringResource(R.string.schedule_block)) }
                    )
                    FilterChip(
                        selected = mode == ScheduleMode.ALLOW,
                        onClick = { mode = ScheduleMode.ALLOW },
                        label = { Text(stringResource(R.string.schedule_allow)) }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { pickTime(startMinute) { startMinute = it } },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.schedule_start)} " +
                                formatMinute(startMinute)
                        )
                    }
                    OutlinedButton(
                        onClick = { pickTime(endMinute) { endMinute = it } },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.schedule_end)} " +
                                formatMinute(endMinute)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            days = days,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            mode = mode
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.schedule_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.schedule_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            }
        }
    )
}

private fun formatMinute(minute: Int): String =
    String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)
