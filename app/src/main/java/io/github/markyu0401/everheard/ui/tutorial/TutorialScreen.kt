package io.github.markyu0401.everheard.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.ui.components.ArcMark
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.theme.displayFontFor

private data class TutorialPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int
)

/**
 * First-run walkthrough. Shown once after the permission gate, and replayable
 * from Settings → Help. It only describes behaviour that exists in the build.
 */
@Composable
fun TutorialScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        TutorialPage(Icons.Outlined.Notifications, R.string.tutorial_1_title, R.string.tutorial_1_body),
        TutorialPage(Icons.Outlined.Schedule, R.string.tutorial_2_title, R.string.tutorial_2_body),
        TutorialPage(Icons.Outlined.GraphicEq, R.string.tutorial_3_title, R.string.tutorial_3_body),
        TutorialPage(Icons.Outlined.People, R.string.tutorial_4_title, R.string.tutorial_4_body),
        TutorialPage(Icons.Outlined.Lock, R.string.tutorial_5_title, R.string.tutorial_5_body)
    )

    var index by rememberSaveable { mutableStateOf(0) }
    val page = pages[index]

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcMark(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            if (index < pages.lastIndex) {
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.tutorial_skip))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.tutorial_step, index + 1, pages.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            val title = stringResource(page.titleRes)
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = displayFontFor(title)
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(page.bodyRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (i == index) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (index > 0) {
            TextButton(
                onClick = { index-- },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tutorial_back))
            }
            Spacer(Modifier.height(4.dp))
        }

        PrimaryButton(
            text = if (index == pages.lastIndex) {
                stringResource(R.string.tutorial_done)
            } else {
                stringResource(R.string.tutorial_next)
            },
            onClick = {
                if (index == pages.lastIndex) {
                    onDone()
                } else {
                    index++
                }
            }
        )
    }
}
