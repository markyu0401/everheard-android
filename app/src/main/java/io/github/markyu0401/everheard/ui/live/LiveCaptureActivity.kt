package io.github.markyu0401.everheard.ui.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.asr.SherpaAsr
import io.github.markyu0401.everheard.asr.StreamingAsrSession
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardTheme {
                LiveCaptureScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun LiveCaptureScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val semantic = LocalSemanticColors.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var status by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<StreamingAsrSession?>(null) }
    var source by remember { mutableStateOf<AudioRecordSource?>(null) }
    var sessionStartedAt by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf(false) }
    val store = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }

    val loadingText = stringResource(R.string.live_loading)
    val recordingText = stringResource(R.string.live_recording)
    val finishingText = stringResource(R.string.live_finishing)
    val stoppedText = stringResource(R.string.live_stopped)
    val savedText = stringResource(R.string.live_saved)
    val errorLabel = stringResource(R.string.live_error_label)
    val permissionMissing = stringResource(R.string.live_permission_missing)

    fun start() {
        if (recording) return
        recording = true
        saved = false
        sessionStartedAt = System.currentTimeMillis()
        finalText = null
        partial = ""
        status = loadingText
        scope.launch {
            try {
                val engine = withContext(Dispatchers.Default) { SherpaAsr(context.assets) }
                if (!recording) {
                    status = stoppedText
                    return@launch
                }
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    recording = false
                    status = "$errorLabel: $permissionMissing"
                    return@launch
                }
                val newSession = engine.createSession()
                val newSource = AudioRecordSource()
                session = newSession
                source = newSource
                newSource.start { frame ->
                    try {
                        newSession.accept(frame)
                        val text = newSession.partialText()
                        mainHandler.post { partial = text }
                    } catch (_: Exception) {
                        // A native recognizer failure is surfaced by the stop path.
                    }
                }
                status = recordingText
            } catch (error: Exception) {
                recording = false
                status = "$errorLabel: ${error.message ?: ""}"
            }
        }
    }

    fun stop() {
        if (!recording) return
        recording = false
        status = finishingText
        val currentSource = source
        val currentSession = session
        source = null
        session = null
        scope.launch {
            currentSource?.stop()
            val result = withContext(Dispatchers.Default) {
                currentSession?.finish()
            }
            currentSession?.close()
            partial = result?.text ?: partial
            finalText = result?.text
            status = stoppedText
        }
    }

    fun save() {
        val text = finalText?.trim().orEmpty()
        if (text.isEmpty() || saved || recording) return
        saved = true
        val endedAt = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val session = TranscriptSession(
            id = id,
            startedAt = sessionStartedAt,
            endedAt = endedAt,
            title = text.take(40),
            utterances = listOf(
                TranscriptUtterance(
                    id = UUID.randomUUID().toString(),
                    sessionId = id,
                    startMs = 0,
                    endMs = (endedAt - sessionStartedAt).coerceAtLeast(0),
                    text = text
                )
            )
        )
        scope.launch {
            try {
                store.createSession(session)
                status = savedText
            } catch (error: Exception) {
                saved = false
                status = "$errorLabel: ${error.message ?: ""}"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            source?.stop()
            session?.close()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.audio_spike_back))
        }

        Text(
            text = stringResource(R.string.live_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.live_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = ::start,
                enabled = !recording,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.live_start))
            }
            OutlinedButton(
                onClick = ::stop,
                enabled = recording,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.live_stop))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = semantic.decision
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.live_transcript_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val waiting = stringResource(R.string.live_waiting)
                Text(
                    text = finalText ?: partial.ifEmpty { waiting },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = ::save,
            enabled = finalText?.isNotBlank() == true && !saved && !recording,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.live_save))
        }
    }
}
