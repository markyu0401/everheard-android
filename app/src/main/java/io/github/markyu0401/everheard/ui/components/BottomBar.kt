package io.github.markyu0401.everheard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.CaptureController
import io.github.markyu0401.everheard.capture.CaptureState

enum class MainTab {
    TODAY,
    LIBRARY,
    MEMORY,
    SETTINGS,
    @Deprecated("Projects and Insights are folded into Memory")
    PROJECTS,
    @Deprecated("Projects and Insights are folded into Memory")
    INSIGHTS
}

/**
 * Four destinations and one primary action. The capture orb overlaps the bar
 * by 16 dp so it is always one tap away, and the active destination is marked
 * by ink and weight rather than a coloured pill.
 */
@Composable
fun BottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by CaptureController.state.collectAsStateWithLifecycle()
    val running = state.isServiceAlive
    val recording = state == CaptureState.RECORDING

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                NavItem(
                    icon = Icons.Outlined.CalendarToday,
                    label = stringResource(R.string.tab_today),
                    selected = selected == MainTab.TODAY,
                    onClick = { onSelect(MainTab.TODAY) },
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    label = stringResource(R.string.tab_library),
                    selected = selected == MainTab.LIBRARY,
                    onClick = { onSelect(MainTab.LIBRARY) },
                    modifier = Modifier.weight(1f)
                )
                CaptureSlot(
                    running = running,
                    recording = recording,
                    onClick = {
                        if (running) {
                            CaptureController.stop(context)
                        } else {
                            CaptureController.start(context)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    icon = Icons.Outlined.AutoAwesome,
                    label = stringResource(R.string.tab_memory),
                    selected = selected == MainTab.MEMORY,
                    onClick = { onSelect(MainTab.MEMORY) },
                    modifier = Modifier.weight(1f)
                )
                NavItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.tab_settings),
                    selected = selected == MainTab.SETTINGS,
                    onClick = { onSelect(MainTab.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = color
        )
    }
}

@Composable
private fun CaptureSlot(
    running: Boolean,
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orbColor = if (recording) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val labelColor = if (recording) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier
                .offset(y = (-16).dp)
                .size(62.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(orbColor)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (running) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.onError)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_stat_everheard),
                        contentDescription = stringResource(R.string.nav_listen),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Text(
            text = if (running) {
                stringResource(R.string.nav_stop)
            } else {
                stringResource(R.string.nav_listen)
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = labelColor
        )
    }
}
