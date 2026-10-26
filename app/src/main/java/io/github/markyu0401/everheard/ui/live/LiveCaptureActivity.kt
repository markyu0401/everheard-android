package io.github.markyu0401.everheard.ui.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.asr.SherpaAsr
import io.github.markyu0401.everheard.asr.StreamingAsrSession
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.PrimaryButton
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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
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
    val waitingText = stringResource(R.string.live_waiting)
    val startLabel = stringResource(R.string.live_record_start)
    val stopLabel = stringResource(R.string.live_record_stop)
    val transcriptText = when {
        !finalText.isNullOrBlank() -> finalText!!
        partial.isNotBlank() -> partial
        else -> waitingText
    }

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
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp)
    ) {
        BackButton(onClick = onBack)

        LargeTitle(
            title = stringResource(R.string.live_title),
            subtitle = stringResource(R.string.live_subtitle)
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RecordButton(
                recording = recording,
                startLabel = startLabel,
                stopLabel = stopLabel,
                onClick = if (recording) ::stop else ::start
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = status.ifEmpty { stoppedText },
                style = MaterialTheme.typography.labelMedium,
                color = if (recording) semantic.risk else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))

        GroupedCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(R.string.live_transcript_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = transcriptText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (transcriptText == waitingText) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.live_save),
            onClick = ::save,
            enabled = finalText?.isNotBlank() == true && !saved && !recording
        )
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    startLabel: String,
    stopLabel: String,
    onClick: () -> Unit
) {
    val semantic = LocalSemanticColors.current
    val transition = rememberInfiniteTransition(label = "record-pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
        if (recording) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = semantic.risk.copy(alpha = 0.18f),
                    radius = size.minDimension / 2f * pulse
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = if (recording) semantic.risk else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(84.dp)
                .clickable(
                    onClickLabel = if (recording) stopLabel else startLabel,
                    onClick = onClick
                )
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (recording) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_stat_everheard),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}
