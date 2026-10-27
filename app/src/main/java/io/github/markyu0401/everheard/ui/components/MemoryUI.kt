package io.github.markyu0401.everheard.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.memory.Commitment
import io.github.markyu0401.everheard.data.memory.Inspiration
import io.github.markyu0401.everheard.data.memory.MemoryEvent
import io.github.markyu0401.everheard.data.memory.MemoryEventType
import io.github.markyu0401.everheard.data.memory.SessionMemory
import io.github.markyu0401.everheard.ui.theme.EverheardSerif
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import java.util.Locale

@Composable
fun MemorySummaryCard(memory: SessionMemory) {
    if (memory.summary.isBlank()) return
    EverheardCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.memory_summary),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = memory.summary,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = EverheardSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 21.sp,
                    lineHeight = 25.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            memory.summaryEvidence?.let { evidence ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "\u201C${evidence.quote}\u201D  ${formatMemoryTime(evidence.startMs)}",
                    style = MaterialTheme.typography.labelSmall,
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
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickableRow(onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeLabel(type = event.type)
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatMemoryTime(event.evidence.startMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = event.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
fun CommitmentRow(
    commitment: Commitment,
    showDivider: Boolean = true,
    onAddToCalendar: (() -> Unit)? = null
) {
    Column {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(17.dp)
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                )
                Text(
                    text = commitment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                commitment.dueHint?.let { due ->
                    Text(
                        text = due,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = LocalSemanticColors.current.commitment
                    )
                }
            }
            val ownerLabel = if (commitment.owner != null) {
                if (commitment.owner == "me") {
                    stringResource(R.string.memory_owner_me)
                } else {
                    commitment.owner
                }
            } else {
                null
            }
            val dueLabel = if (commitment.dueHint != null) {
                stringResource(R.string.memory_due, commitment.dueHint)
            } else {
                null
            }
            val meta = buildString {
                if (ownerLabel != null) append(ownerLabel)
                if (dueLabel != null) {
                    if (isNotEmpty()) append(" · ")
                    append(dueLabel)
                }
            }
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 27.dp)
                )
            }
            if (onAddToCalendar != null) {
                Spacer(Modifier.height(3.dp))
                TextAction(
                    text = stringResource(R.string.memory_add_calendar),
                    onClick = onAddToCalendar,
                    showArrow = false,
                    modifier = Modifier.padding(start = 27.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 14.dp),
                thickness = 1.dp,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = inspiration.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatMemoryTime(inspiration.evidence.startMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 14.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun MemoryTypeLabel(type: MemoryEventType) {
    Text(
        text = memoryTypeLabelText(type),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.5.sp
        ),
        color = memoryTypeColor(type)
    )
}

@Composable
private fun memoryTypeLabelText(type: MemoryEventType): String = stringResource(
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

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(
        Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    )
