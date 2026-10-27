package io.github.markyu0401.everheard.ui.settings

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.data.provider.ProviderConfig
import io.github.markyu0401.everheard.data.provider.ProviderConfigStore
import io.github.markyu0401.everheard.data.provider.ProviderTester
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
        ProviderConfigStore(File(context.filesDir, ProviderConfigStore.FILE_NAME))
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        BackButton(onClick = onBack)
        LargeTitle(
            title = stringResource(R.string.provider_title),
            subtitle = stringResource(R.string.provider_subtitle)
        )
        Spacer(Modifier.height(20.dp))

        SectionHeaderText(stringResource(R.string.provider_llm))
        GroupedCard {
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
                secret = true
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeaderText(stringResource(R.string.provider_asr))
        GroupedCard {
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
                secret = true
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeaderText(stringResource(R.string.provider_embedding))
        GroupedCard {
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
                secret = true
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = stringResource(R.string.provider_save),
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { store.save(currentConfig()) }
                    withContext(Dispatchers.Main) {
                        testMessage = savedMessage
                    }
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = if (testing) {
                stringResource(R.string.provider_testing)
            } else {
                stringResource(R.string.provider_test)
            },
            enabled = !testing,
            onClick = {
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
            }
        )
        testMessage?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeaderText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ProviderField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}
