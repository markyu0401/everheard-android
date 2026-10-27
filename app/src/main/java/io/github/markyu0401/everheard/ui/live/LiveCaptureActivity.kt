package io.github.markyu0401.everheard.ui.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.asr.AsrRequest
import io.github.markyu0401.everheard.asr.SenseVoiceAsr
import io.github.markyu0401.everheard.asr.SherpaAsr
import io.github.markyu0401.everheard.asr.SpeechSegmenter
import io.github.markyu0401.everheard.asr.StreamingAsrSession
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.capture.vad.SileroVad
import io.github.markyu0401.everheard.data.transcript.EncryptedTranscriptStore
import io.github.markyu0401.everheard.data.transcript.TranscriptSession
import io.github.markyu0401.everheard.data.transcript.TranscriptUtterance
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.EverheardPill
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import io.github.markyu0401.everheard.ui.theme.EverheardSans
import io.github.markyu0401.everheard.ui.theme.LocalSemanticColors
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveCaptureActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
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
    var vad by remember { mutableStateOf<SileroVad?>(null) }
    var segmenter by remember { mutableStateOf<SpeechSegmenter?>(null) }
    val speechSegments = remember { mutableListOf<SpeechSegmenter.Segment>() }
    val speechLock = remember { Any() }
    var sessionStartedAt by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var saved by remember { mutableStateOf(false) }
    var refining by remember { mutableStateOf(false) }
    val store = remember {
        EncryptedTranscriptStore(File(context.filesDir, "transcripts.bin"))
    }

    val loadingText = stringResource(R.string.live_loading)
    val recordingText = stringResource(R.string.live_recording)
    val finishingText = stringResource(R.string.live_finishing)
    val refiningText = stringResource(R.string.live_refining)
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

    LaunchedEffect(recording) {
        while (recording) {
            elapsedMs = (System.currentTimeMillis() - sessionStartedAt).coerceAtLeast(0L)
            delay(1000)
        }
    }

    fun start() {
        if (recording) return
        recording = true
        saved = false
        refining = false
        sessionStartedAt = System.currentTimeMillis()
        elapsedMs = 0L
        finalText = null
        partial = ""
        status = loadingText
        synchronized(speechLock) { speechSegments.clear() }
        scope.launch {
            try {
                val engine = withContext(Dispatchers.Default) { SherpaAsr(context.assets) }
                val newVad = withContext(Dispatchers.Default) { SileroVad(context.assets) }
                if (!recording) {
                    newVad.release()
                    status = stoppedText
                    return@launch
                }
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    newVad.release()
                    recording = false
                    status = "$errorLabel: $permissionMissing"
                    return@launch
                }
                val newSession = engine.createSession()
                val newSegmenter = SpeechSegmenter(minSpeechMs = 300, endSilenceMs = 500)
                val newSource = AudioRecordSource()
                session = newSession
                source = newSource
                vad = newVad
                segmenter = newSegmenter
                newSource.start { frame ->
                    try {
                        val speech = newVad.isSpeech(frame)
                        val emitted = newSegmenter.accept(frame, speech)
                        if (emitted.isNotEmpty()) {
                            synchronized(speechLock) { speechSegments.addAll(emitted) }
                        }
                        newSession.accept(frame)
                        if (speech) {
                            val text = newSession.partialText()
                            mainHandler.post { partial = text }
                        }
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
        val currentSegmenter = segmenter
        val currentVad = vad
        source = null
        session = null
        segmenter = null
        vad = null
        scope.launch {
            currentSource?.stop()
            currentSegmenter?.flush()?.let { segment ->
                synchronized(speechLock) { speechSegments.add(segment) }
            }
            currentVad?.release()
            val result = withContext(Dispatchers.Default) {
                currentSession?.finish()
            }
            currentSession?.close()

            val recorded = synchronized(speechLock) {
                val segments = speechSegments.toList()
                speechSegments.clear()
                val total = segments.sumOf { it.pcm16.size }
                val pcm = ShortArray(total)
                var offset = 0
                segments.forEach { segment ->
                    segment.pcm16.copyInto(pcm, offset)
                    offset += segment.pcm16.size
                }
                pcm
            }

            partial = result?.text ?: partial
            finalText = result?.text
            status = stoppedText

            if (recorded.isNotEmpty()) {
                status = refiningText
                refining = true
                val refined = withContext(Dispatchers.Default) {
                    runCatching {
                        SenseVoiceAsr(context.assets)
                            .transcribe(AsrRequest(pcm16 = recorded))
                            .text
                            .trim()
                    }.onFailure {
                        Log.w("LiveCapture", "SenseVoice refinement failed", it)
                    }.getOrNull()
                }
                if (!refined.isNullOrBlank()) {
                    partial = refined
                    finalText = refined
                }
                refining = false
                status = stoppedText
            } else {
                // No VAD-confirmed speech: never keep a hallucinated silence
                // transcript such as "我." from the offline model.
                partial = ""
                finalText = ""
            }
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
            vad?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp)
        ) {
            BackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
            Text(
                text = stringResource(R.string.live_title),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            EverheardPill(
                text = stringResource(R.string.home_chip_local),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            ScreenHeader(
                title = stringResource(R.string.live_title),
                subtitle = stringResource(R.string.live_subtitle)
            )
            Spacer(Modifier.height(18.dp))

            EverheardCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = status.ifEmpty { stoppedText },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (recording) {
                                semantic.live
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatElapsed(elapsedMs),
                            style = TextStyle(
                                fontFamily = EverheardSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    LiveWave(
                        active = recording,
                        color = if (recording) semantic.live else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        RecordButton(
                            recording = recording,
                            startLabel = startLabel,
                            stopLabel = stopLabel,
                            onClick = if (recording) ::stop else ::start
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            EverheardCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = stringResource(R.string.live_transcript_label),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
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
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(
                text = stringResource(R.string.live_save),
                onClick = ::save,
                enabled = finalText?.isNotBlank() == true && !saved && !recording && !refining
            )
        }
    }
}

@Composable
private fun LiveWave(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "live-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val static = listOf(0.32f, 0.52f, 0.82f, 0.62f, 0.42f, 0.72f, 0.34f)
    Canvas(modifier = modifier) {
        val count = 7
        val gap = size.width * 0.035f
        val barWidth = (size.width - gap * (count - 1)) / count
        for (i in 0 until count) {
            val fraction = if (active) {
                0.25f + 0.75f * ((sin(phase + i * 0.9f) + 1f) / 2f)
            } else {
                static[i]
            }
            val h = size.height * fraction
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.9f else 0.55f),
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
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

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
        if (recording) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = semantic.live.copy(alpha = 0.18f),
                    radius = size.minDimension / 2f * pulse
                )
            }
        }
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (recording) semantic.live else MaterialTheme.colorScheme.primary
                )
                .clickable(
                    onClickLabel = if (recording) stopLabel else startLabel,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (recording) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_stat_everheard),
                    contentDescription = startLabel,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
