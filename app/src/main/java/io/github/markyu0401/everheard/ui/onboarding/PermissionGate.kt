package io.github.markyu0401.everheard.ui.onboarding

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors

enum class PermissionKind { Microphone, Notifications }

/**
 * A blocking gate, not a dismissible banner. Capture without the microphone
 * permission is impossible, and capture without a visible notification is not
 * something this app does.
 */
@Composable
fun PermissionGate(
    kind: PermissionKind,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    val accent = if (kind == PermissionKind.Microphone) semantic.decision else semantic.commitment
    val badge = if (kind == PermissionKind.Microphone) "MIC" else "NOTIFY"
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = accent.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
            modifier = Modifier.size(64.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(text = action, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOpenSettings) {
            Text(
                text = stringResource(R.string.permission_open_settings),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0D, heightDp = 700)
@Composable
private fun PermissionGatePreview() {
    EverheardTheme {
        PermissionGate(
            kind = PermissionKind.Microphone,
            onRequest = {},
            onOpenSettings = {}
        )
    }
}
