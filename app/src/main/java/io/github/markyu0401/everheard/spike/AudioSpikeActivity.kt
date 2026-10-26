package io.github.markyu0401.everheard.spike

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.capture.audio.WavWriter
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M0 measurement tool: record 10 s, write a 16 kHz mono WAV, show the path.
 * It exists so a real phone can prove the capture chain before the product UI
 * depends on it.
 */
class AudioSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardTheme {
                AudioSpikeScreen(onBack = { finish() })
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun AudioSpikeScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val noPermissionMessage = stringResource(R.string.audio_spike_no_permission)
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var frames by remember { mutableStateOf(0) }
    var savedPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeSource by remember { mutableStateOf<AudioRecordSource?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeSource?.stop()
        }
    }

    fun recordTenSeconds() {
        if (recording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            error = noPermissionMessage
            return
        }
        recording = true
        savedPath = null
        error = null
        frames = 0
        elapsedMs = 0

        scope.launch {
            val collected = Collections.synchronizedList(mutableListOf<ShortArray>())
            val source = AudioRecordSource()
            activeSource = source

            try {
                withContext(Dispatchers.Default) {
                    source.start { frame ->
                        collected.add(frame)
                    }
                }

                val startedAt = SystemClock.elapsedRealtime()
                while (isActive && SystemClock.elapsedRealtime() - startedAt < 10_000) {
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    frames = collected.size
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

                val file = File(
                    context.filesDir,
                    "spike/audio-${System.currentTimeMillis()}.wav"
                )
                WavWriter.write(file, pcm)

                savedPath = file.absolutePath
                frames = collected.size
            } catch (throwable: Throwable) {
                source.stop()
                activeSource = null
                error = throwable.message ?: throwable.javaClass.simpleName
            } finally {
                recording = false
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
            text = stringResource(R.string.audio_spike_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.audio_spike_body),
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
            text = if (recording) {
                stringResource(R.string.audio_spike_recording)
            } else {
                stringResource(R.string.audio_spike_frames, frames)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::recordTenSeconds,
            enabled = !recording,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.audio_spike_record))
        }

        savedPath?.let { path ->
            Spacer(Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.audio_spike_saved),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
