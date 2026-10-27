package io.github.markyu0401.everheard.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.ui.components.ArcMark
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.theme.EverheardTheme

enum class PermissionKind { Microphone, Notifications }

@Composable
fun PermissionGate(
    kind: PermissionKind,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = if (kind == PermissionKind.Microphone) {
        stringResource(R.string.permission_microphone_title)
    } else {
        stringResource(R.string.permission_notifications_title)
    }
    val body = if (kind == PermissionKind.Microphone) {
        stringResource(R.string.permission_microphone_body)
    } else {
        stringResource(R.string.permission_notifications_body)
    }
    val action = if (kind == PermissionKind.Microphone) {
        stringResource(R.string.permission_microphone_action)
    } else {
        stringResource(R.string.permission_notifications_action)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArcMark(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = io.github.markyu0401.everheard.ui.theme.displayFontFor(title)
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = action, onClick = onRequest, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOpenSettings) {
            Text(
                text = stringResource(R.string.permission_open_settings),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionGatePreview() {
    EverheardTheme(darkTheme = false) {
        PermissionGate(
            kind = PermissionKind.Microphone,
            onRequest = {},
            onOpenSettings = {}
        )
    }
}
