package io.github.markyu0401.everheard.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.mcp.McpConfig
import io.github.markyu0401.everheard.mcp.McpConfigStore
import io.github.markyu0401.everheard.mcp.McpServerManager
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class McpSettingsActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                McpSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun McpSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember {
        McpConfigStore(File(context.filesDir, McpConfigStore.FILE_NAME))
    }
    var config by remember { mutableStateOf(McpConfig()) }
    var status by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        config = store.load()
        loaded = true
    }

    fun save(next: McpConfig) {
        config = next
        scope.launch {
            withContext(Dispatchers.IO) { store.save(next) }
        }
    }

    fun enable() {
        val token = config.token.ifBlank { McpConfigStore.newToken() }
        val next = config.copy(enabled = true, token = token)
        val result = McpServerManager.start(context, next)
        if (result.isSuccess) {
            val port = result.getOrThrow()
            save(next.copy(port = port))
            status = "Listening on 127.0.0.1:$port"
        } else {
            status = result.exceptionOrNull()?.message ?: "Could not start the server"
        }
    }

    fun disable() {
        McpServerManager.stop()
        save(config.copy(enabled = false))
        status = "Server stopped"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BackButton(onClick = onBack)
        LargeTitle(
            title = stringResource(R.string.mcp_title),
            subtitle = stringResource(R.string.mcp_subtitle)
        )
        Spacer(Modifier.height(20.dp))

        GroupedCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mcp_enable),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.mcp_enable_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { checked ->
                        if (checked) enable() else disable()
                    }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.mcp_endpoint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        GroupedCard {
            OutlinedTextField(
                value = config.port.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { port ->
                        if (port in 1024..65535) {
                            save(config.copy(port = port))
                        }
                    }
                },
                label = { Text(stringResource(R.string.mcp_port)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            Text(
                text = "http://127.0.0.1:${McpServerManager.port() ?: config.port}/mcp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.mcp_token),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        GroupedCard {
            OutlinedTextField(
                value = config.token,
                onValueChange = { },
                readOnly = true,
                label = { Text(stringResource(R.string.mcp_token_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            PrimaryButton(
                text = stringResource(R.string.mcp_copy_token),
                enabled = config.token.isNotBlank(),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Everheard MCP token", config.token)
                    )
                    Toast.makeText(
                        context,
                        R.string.mcp_copied,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = stringResource(R.string.mcp_new_token),
                onClick = {
                    val token = McpConfigStore.newToken()
                    val next = config.copy(token = token)
                    save(next)
                    if (next.enabled) {
                        val result = McpServerManager.start(context, next)
                        status = if (result.isSuccess) {
                            "Listening on 127.0.0.1:${result.getOrThrow()}"
                        } else {
                            result.exceptionOrNull()?.message
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        status?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.mcp_note),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}
