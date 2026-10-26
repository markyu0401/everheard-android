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
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.capture.vad.SileroVad
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M0 measurement tool: record 10 s, run Silero VAD over every 20 ms frame, and
 * report the speech ratio. The CSV is the raw evidence for the battery/VAD
 * report.
 */
class VadSpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardTheme {
                VadSpikeScreen(onBack = { finish() })
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun VadSpikeScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val noPermissionMessage = stringResource(R.string.audio_spike_no_permission)
    var recording by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var speechFrames by remember { mutableStateOf(0) }
    var totalFrames by remember { mutableStateOf(0) }
    var savedPath by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeSource by remember { mutableStateOf<AudioRecordSource?>(null) }
    var activeVad by remember { mutableStateOf<SileroVad?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeSource?.stop()
            activeVad?.release()
        }
    }

    fun runTenSeconds() {
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
        speechFrames = 0
        totalFrames = 0
        elapsedMs = 0

        scope.launch {
            val source = AudioRecordSource()
            val vad = SileroVad(context.assets)
            activeSource = source
            activeVad = vad

            try {
                val csv = StringBuilder("frame,speech\n")

                withContext(Dispatchers.Default) {
                    source.start { frame ->
                        val speech = vad.isSpeech(frame)
                        synchronized(csv) {
                            csv.append(if (speech) "1" else "0").append('\n')
                        }
                    }
                }

                val startedAt = SystemClock.elapsedRealtime()
                while (isActive && SystemClock.elapsedRealtime() - startedAt < 10_000) {
                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    val lines = synchronized(csv) { csv.lines().size - 2 }
                    totalFrames = lines
                    speechFrames = synchronized(csv) {
                        csv.lines().drop(1).count { it == "1" }
                    }
                    delay(100)
                }

                source.stop()
                activeSource = null

                val finalLines = synchronized(csv) { csv.lines().drop(1).filter { it.isNotEmpty() } }
                totalFrames = finalLines.size
                speechFrames = finalLines.count { it == "1" }

                val file = File(
                    context.filesDir,
                    "spike/vad-${System.currentTimeMillis()}.csv"
                )
                file.parentFile?.mkdirs()
                file.writeText(synchronized(csv) { csv.toString() })

                savedPath = file.absolutePath
            } catch (throwable: Throwable) {
                source.stop()
                error = throwable.message ?: throwable.javaClass.simpleName
            } finally {
                vad.release()
                activeVad = null
                recording = false
                elapsedMs = 10_000
            }
        }
    }

    val ratio = if (totalFrames > 0) speechFrames.toFloat() / totalFrames else 0f

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
            text = stringResource(R.string.vad_spike_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vad_spike_body),
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
            text = stringResource(R.string.vad_spike_frames, totalFrames, speechFrames),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.vad_spike_ratio, ratio * 100f),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = ::runTenSeconds,
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
                Column(modifier = Modifier.padding(14.dp)) {
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
