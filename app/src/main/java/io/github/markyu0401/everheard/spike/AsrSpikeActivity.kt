package io.github.markyu0401.everheard.spike

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.asr.AsrRequest
import io.github.markyu0401.everheard.asr.SherpaAsr
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M0 measurement tool: record 10 s, transcribe on-device, show the transcript,
 * RTF, and latency. A missing model is a readable error, not a crash.
 */
class AsrSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardTheme {
                AsrSpikeScreen(onBack = { finish() })
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun AsrSpikeScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val noPermissionMessage = stringResource(R.string.audio_spike_no_permission)
    val emptyTranscriptMessage = stringResource(R.string.asr_spike_empty)
    var busy by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var transcript by remember { mutableStateOf<String?>(null) }
    var rtf by remember { mutableStateOf<Double?>(null) }
    var latencyMs by remember { mutableLongStateOf(0L) }
    var savedPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeSource by remember { mutableStateOf<AudioRecordSource?>(null) }

    DisposableEffect(Unit) {
        onDispose { activeSource?.stop() }
    }

    fun runTenSeconds() {
        if (busy) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            error = noPermissionMessage
            return
        }

        busy = true
        transcript = null
        rtf = null
        latencyMs = 0
        savedPath = null
        error = null
        elapsedMs = 0

        scope.launch {
            val collected = Collections.synchronizedList(mutableListOf<ShortArray>())
            val source = AudioRecordSource()
            activeSource = source

            try {
                withContext(Dispatchers.Default) {
                    source.start { frame -> collected.add(frame) }
                }

                val startedAt = SystemClock.elapsedRealtime()
                while (isActive && SystemClock.elapsedRealtime() - startedAt < 10_000) {
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    delay(50)
                }

                source.stop()
                activeSource = null

                val totalSamples = synchronized(collected) { collected.sumOf { it.size } }
                val pcm = ShortArray(totalSamples)
                var offset = 0
                synchronized(collected) {
                    for (frame in collected) {
                        frame.copyInto(pcm, offset)
                        offset += frame.size
                    }
                }

                val asr = withContext(Dispatchers.Default) {
                    SherpaAsr(context.assets)
                }
                val result = asr.transcribe(AsrRequest(pcm16 = pcm))
                val wallMs = SystemClock.elapsedRealtime() - startedAt

                transcript = result.text.ifBlank { emptyTranscriptMessage }
                latencyMs = result.latencyMs
                rtf = wallMs / 10_000.0

                val file = File(
                    context.filesDir,
                    "spike/asr-${System.currentTimeMillis()}.txt"
                )
                file.parentFile?.mkdirs()
                file.writeText(
                    buildString {
                        appendLine("model=${result.model}")
                        appendLine("rtf=${"%.3f".format(rtf ?: 0.0)}")
                        appendLine("latencyMs=$latencyMs")
                        appendLine("transcript=${result.text}")
                    }
                )
                savedPath = file.absolutePath
            } catch (throwable: Throwable) {
                source.stop()
                activeSource = null
                error = throwable.message ?: throwable.javaClass.simpleName
            } finally {
                busy = false
                elapsedMs = 10_000
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.audio_spike_back))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.asr_spike_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.asr_spike_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = formatDuration(elapsedMs),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (busy) {
                stringResource(R.string.asr_spike_transcribing)
            } else {
                stringResource(R.string.asr_spike_model, SherpaAsr.MODEL_ID)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        rtf?.let { value ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.asr_spike_rtf, value),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = stringResource(R.string.asr_spike_latency, latencyMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::runTenSeconds,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.asr_spike_record))
        }

        transcript?.let { text ->
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.asr_spike_transcript),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        savedPath?.let { path ->
            Spacer(Modifier.height(14.dp))
            Text(
                text = path,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        error?.let { message ->
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.audio_spike_error, message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
