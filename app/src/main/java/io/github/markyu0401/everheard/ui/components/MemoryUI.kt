package io.github.markyu0401.everheard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.Inspiration
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import java.util.Locale

@Composable
fun MemorySummaryCard(memory: SessionMemory) {
    if (memory.summary.isBlank()) return
    GroupedCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.memory_summary),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = memory.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            memory.summaryEvidence?.let { evidence ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u201C${evidence.quote}\u201D  ${formatMemoryTime(evidence.startMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemoryEventRow(
    event: MemoryEvent,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val color = memoryTypeColor(event.type)
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeChip(type = event.type, color = color)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatMemoryTime(event.evidence.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = event.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (event.evidence.speaker != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (event.evidence.speaker == "me") {
                        stringResource(R.string.speaker_me)
                    } else {
                        event.evidence.speaker
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
fun CommitmentRow(
    commitment: Commitment,
    showDivider: Boolean = true
) {
    Column {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeChip(
                    type = MemoryEventType.COMMITMENT,
                    color = LocalSemanticColors.current.commitment
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.memory_open),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalSemanticColors.current.commitment
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = commitment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val meta = buildString {
                commitment.owner?.let { owner ->
                    append(if (owner == "me") stringResource(R.string.memory_owner_me) else owner)
                }
                commitment.dueHint?.let { due ->
                    if (isNotEmpty()) append(" · ")
                    append(stringResource(R.string.memory_due, due))
                }
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
fun InspirationRow(
    inspiration: Inspiration,
    showDivider: Boolean = true
) {
    Column {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeChip(
                    type = MemoryEventType.INSPIRATION,
                    color = LocalSemanticColors.current.insight
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatMemoryTime(inspiration.evidence.startMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = inspiration.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun MemoryTypeChip(type: MemoryEventType, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = memoryTypeLabel(type),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun memoryTypeLabel(type: MemoryEventType): String = stringResource(
    when (type) {
        MemoryEventType.PROGRESS -> R.string.memory_event_progress
        MemoryEventType.DECISION -> R.string.memory_event_decision
        MemoryEventType.COMMITMENT -> R.string.memory_event_commitment
        MemoryEventType.RISK -> R.string.memory_event_risk
        MemoryEventType.INSPIRATION -> R.string.memory_event_inspiration
    }
)

@Composable
private fun memoryTypeColor(type: MemoryEventType): Color {
    val semantic = LocalSemanticColors.current
    return when (type) {
        MemoryEventType.PROGRESS -> MaterialTheme.colorScheme.primary
        MemoryEventType.DECISION -> semantic.decision
        MemoryEventType.COMMITMENT -> semantic.commitment
        MemoryEventType.RISK -> semantic.risk
        MemoryEventType.INSPIRATION -> semantic.insight
    }
}

private fun formatMemoryTime(ms: Long): String {
    val totalSeconds = ms / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
