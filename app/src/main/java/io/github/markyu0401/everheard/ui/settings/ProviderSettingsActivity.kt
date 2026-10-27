package io.github.markyu0401.everheard.ui.settings

import android.view.WindowManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.provider.ProviderConfig
import io.github.markyu0401.everheard.data.provider.ProviderConfigStore
import io.github.markyu0401.everheard.data.provider.ProviderTester
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.EverheardPill
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.TextAction
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProviderSettingsActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                ProviderSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun ProviderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember {
        ProviderConfigStore.inDirectory(context.filesDir)
    }
    var llmBaseUrl by remember { mutableStateOf("") }
    var llmModel by remember { mutableStateOf("") }
    var llmApiKey by remember { mutableStateOf("") }
    var asrBaseUrl by remember { mutableStateOf("") }
    var asrModel by remember { mutableStateOf("") }
    var asrApiKey by remember { mutableStateOf("") }
    var embeddingBaseUrl by remember { mutableStateOf("") }
    var embeddingModel by remember { mutableStateOf("") }
    var embeddingApiKey by remember { mutableStateOf("") }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.provider_saved)

    LaunchedEffect(Unit) {
        val config = store.load()
        llmBaseUrl = config.llmBaseUrl
        llmModel = config.llmModel
        llmApiKey = config.llmApiKey
        asrBaseUrl = config.asrBaseUrl
        asrModel = config.asrModel
        asrApiKey = config.asrApiKey
        embeddingBaseUrl = config.embeddingBaseUrl
        embeddingModel = config.embeddingModel
        embeddingApiKey = config.embeddingApiKey
        configured = config.llmConfigured
    }

    fun currentConfig() = ProviderConfig(
        llmBaseUrl = llmBaseUrl,
        llmModel = llmModel,
        llmApiKey = llmApiKey,
        asrBaseUrl = asrBaseUrl,
        asrModel = asrModel,
        asrApiKey = asrApiKey,
        embeddingBaseUrl = embeddingBaseUrl,
        embeddingModel = embeddingModel,
        embeddingApiKey = embeddingApiKey
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.provider_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            ScreenHeader(
                title = stringResource(R.string.provider_title),
                subtitle = stringResource(R.string.provider_subtitle)
            )
            Spacer(Modifier.height(18.dp))

            SectionLabel(stringResource(R.string.provider_llm))
            EverheardCard {
                ProviderField(
                    value = llmBaseUrl,
                    onValueChange = { llmBaseUrl = it },
                    label = stringResource(R.string.provider_base_url)
                )
                ProviderField(
                    value = llmModel,
                    onValueChange = { llmModel = it },
                    label = stringResource(R.string.provider_model)
                )
                ProviderField(
                    value = llmApiKey,
                    onValueChange = { llmApiKey = it },
                    label = stringResource(R.string.provider_api_key),
                    secret = true,
                    showDivider = false
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.provider_asr))
            EverheardCard {
                ProviderField(
                    value = asrBaseUrl,
                    onValueChange = { asrBaseUrl = it },
                    label = stringResource(R.string.provider_base_url)
                )
                ProviderField(
                    value = asrModel,
                    onValueChange = { asrModel = it },
                    label = stringResource(R.string.provider_model)
                )
                ProviderField(
                    value = asrApiKey,
                    onValueChange = { asrApiKey = it },
                    label = stringResource(R.string.provider_api_key),
                    secret = true,
                    showDivider = false
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel(stringResource(R.string.provider_embedding))
            EverheardCard {
                ProviderField(
                    value = embeddingBaseUrl,
                    onValueChange = { embeddingBaseUrl = it },
                    label = stringResource(R.string.provider_base_url)
                )
                ProviderField(
                    value = embeddingModel,
                    onValueChange = { embeddingModel = it },
                    label = stringResource(R.string.provider_model)
                )
                ProviderField(
                    value = embeddingApiKey,
                    onValueChange = { embeddingApiKey = it },
                    label = stringResource(R.string.provider_api_key),
                    secret = true,
                    showDivider = false
                )
            }

            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.provider_save),
                onClick = {
                    scope.launch {
                        val config = currentConfig()
                        withContext(Dispatchers.IO) { store.save(config) }
                        withContext(Dispatchers.Main) {
                            configured = config.llmConfigured
                            testMessage = savedMessage
                        }
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            TextAction(
                text = if (testing) {
                    stringResource(R.string.provider_testing)
                } else {
                    stringResource(R.string.provider_test)
                },
                onClick = {
                    if (testing) return@TextAction
                    testing = true
                    testMessage = null
                    val config = currentConfig()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { ProviderTester.testLlm(config) }
                        withContext(Dispatchers.Main) {
                            testing = false
                            testMessage = result.message
                        }
                    }
                },
                showArrow = false
            )
            testMessage?.let { message ->
                Spacer(Modifier.height(10.dp))
                EverheardPill(
                    text = message,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProviderField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
    showDivider: Boolean = true
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (showDivider) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 14.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
