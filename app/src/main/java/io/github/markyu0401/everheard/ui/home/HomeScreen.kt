package io.github.markyu0401.everheard.ui.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.CaptureController
import io.github.markyu0401.everheard.capture.CaptureState
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.components.StatusDot
import io.github.markyu0401.everheard.ui.live.LiveCaptureActivity
import io.github.markyu0401.everheard.ui.schedule.ScheduleActivity
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import io.github.markyu0401.everheard.ui.transcript.TranscriptListActivity

/**
 * A calm, grouped home: one capture card, one library list, and no developer
 * instrumentation on the main surface.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val serviceState by CaptureController.state.collectAsStateWithLifecycle()
    val serviceRunning = serviceState.isServiceAlive

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            LargeTitle(
                title = stringResource(R.string.home_title),
                subtitle = stringResource(R.string.home_body)
            )

            Spacer(Modifier.height(24.dp))

            CaptureCard(
                state = serviceState,
                onStart = { CaptureController.start(context) },
                onStop = { CaptureController.stop(context) }
            )

            Spacer(Modifier.height(28.dp))

            SectionHeader(stringResource(R.string.home_library))
            GroupedCard {
                SettingsRow(
                    icon = Icons.Filled.List,
                    title = stringResource(R.string.transcript_open),
                    subtitle = stringResource(R.string.home_transcript_subtitle),
                    onClick = {
                        context.startActivity(Intent(context, TranscriptListActivity::class.java))
                    }
                )
                SettingsRow(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.schedule_open),
                    subtitle = stringResource(R.string.home_schedule_subtitle),
                    onClick = {
                        context.startActivity(Intent(context, ScheduleActivity::class.java))
                    }
                )
                SettingsRow(
                    iconPainter = painterResource(R.drawable.ic_stat_everheard),
                    title = stringResource(R.string.live_open),
                    subtitle = stringResource(R.string.home_live_subtitle),
                    showDivider = false,
                    onClick = {
                        context.startActivity(Intent(context, LiveCaptureActivity::class.java))
                    }
                )
            }

            Spacer(Modifier.height(28.dp))
        }

        Text(
            text = stringResource(R.string.home_footer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun CaptureCard(
    state: CaptureState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val running = state.isServiceAlive
    val recording = state == CaptureState.RECORDING
    val statusColor = when {
        recording -> semantic.risk
        running -> semantic.decision
        else -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (state) {
        CaptureState.RECORDING -> stringResource(R.string.home_service_recording)
        CaptureState.ARMED -> stringResource(R.string.home_service_armed)
        CaptureState.STOPPED -> stringResource(R.string.home_service_stopped)
        else -> stringResource(R.string.home_service_running)
    }
    val body = when (state) {
        CaptureState.RECORDING -> stringResource(R.string.home_capture_body_recording)
        CaptureState.ARMED -> stringResource(R.string.home_capture_body_armed)
        CaptureState.PAUSED_SCHEDULE -> stringResource(R.string.home_capture_body_schedule)
        CaptureState.PAUSED_USER -> stringResource(R.string.home_capture_body_user)
        CaptureState.PAUSED_BATTERY -> stringResource(R.string.home_capture_body_battery)
        CaptureState.PAUSED_THERMAL -> stringResource(R.string.home_capture_body_thermal)
        CaptureState.PAUSED_SILENCE -> stringResource(R.string.home_capture_body_silence)
        CaptureState.ERROR -> stringResource(R.string.home_capture_body_error)
        CaptureState.STOPPED -> stringResource(R.string.home_capture_body_stopped)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            StatusDot(color = statusColor, text = statusText)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (recording) {
                        semantic.risk.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stat_everheard),
                            contentDescription = null,
                            tint = if (recording) semantic.risk else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_capture_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (running) {
                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_service_stop),
                        style = MaterialTheme.typography.titleMedium,
                        color = semantic.risk
                    )
                }
            } else {
                PrimaryButton(
                    text = stringResource(R.string.home_service_start),
                    onClick = onStart
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomePreview() {
    EverheardTheme(darkTheme = false) {
        HomeScreen()
    }
}
