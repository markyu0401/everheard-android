package io.github.markyu0401.everheard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R

enum class MainTab {
    TODAY,
    LIBRARY,
    PROJECTS,
    INSIGHTS,
    SETTINGS
}

/**
 * Persistent taskbar for the three top-level destinations. Detail screens keep
 * their own back affordance and do not show it.
 */
@Composable
fun BottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTab(
                    tab = MainTab.TODAY,
                    icon = Icons.Filled.DateRange,
                    label = stringResource(R.string.tab_today),
                    selected = selected == MainTab.TODAY,
                    onClick = { onSelect(MainTab.TODAY) },
                    modifier = Modifier.weight(1f)
                )
                BottomTab(
                    tab = MainTab.LIBRARY,
                    icon = Icons.Filled.List,
                    label = stringResource(R.string.tab_library),
                    selected = selected == MainTab.LIBRARY,
                    onClick = { onSelect(MainTab.LIBRARY) },
                    modifier = Modifier.weight(1f)
                )
                BottomTab(
                    tab = MainTab.PROJECTS,
                    icon = Icons.Filled.Star,
                    label = stringResource(R.string.tab_projects),
                    selected = selected == MainTab.PROJECTS,
                    onClick = { onSelect(MainTab.PROJECTS) },
                    modifier = Modifier.weight(1f)
                )
                BottomTab(
                    tab = MainTab.INSIGHTS,
                    icon = Icons.Filled.Face,
                    label = stringResource(R.string.tab_insights),
                    selected = selected == MainTab.INSIGHTS,
                    onClick = { onSelect(MainTab.INSIGHTS) },
                    modifier = Modifier.weight(1f)
                )
                BottomTab(
                    tab = MainTab.SETTINGS,
                    icon = Icons.Filled.Settings,
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
private fun BottomTab(
    tab: MainTab,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                }
            )
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}
