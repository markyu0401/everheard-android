package io.github.markyu0401.everheard.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.github.markyu0401.everheard.capture.audio.AudioRecordSource
import io.github.markyu0401.everheard.capture.vad.SileroVad
import io.github.markyu0401.everheard.speaker.SherpaSpeakerEmbedder
import io.github.markyu0401.everheard.speaker.SpeakerModelMissingException
import io.github.markyu0401.everheard.speaker.VoiceEnrollment
import io.github.markyu0401.everheard.speaker.VoiceIssue
import io.github.markyu0401.everheard.speaker.VoiceProfileStore
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.GroupedCard
import io.github.markyu0401.everheard.ui.components.LargeTitle
import io.github.markyu0401.everheard.ui.components.PrimaryButton
import io.github.markyu0401.everheard.ui.components.SectionHeader
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VoiceEnrollment"

private enum class VoiceSampleStage {
    CHINESE,
    ENGLISH
}

class VoiceEnrollmentActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            EverheardAppTheme {
                VoiceEnrollmentScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun VoiceEnrollmentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember {
        VoiceProfileStore(File(context.filesDir, "voice_profile.bin"))
    }
    val frames = remember { Collections.synchronizedList(mutableListOf<ShortArray>()) }
    val totalSamples = remember { AtomicInteger(0) }
    val speechSamples = remember { AtomicInteger(0) }
    val lastSpeechAt = remember { AtomicLong(SystemClock.elapsedRealtime()) }

    var source by remember { mutableStateOf<AudioRecordSource?>(null) }
    var vad by remember { mutableStateOf<SileroVad?>(null) }
    var recordingStage by remember { mutableStateOf<VoiceSampleStage?>(null) }
    var processingStage by remember { mutableStateOf<VoiceSampleStage?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var chineseEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var englishEmbedding by remember { mutableStateOf<FloatArray?>(null) }
    var chineseSamples by remember { mutableStateOf(0) }
    var englishSamples by remember { mutableStateOf(0) }

    val permissionError = stringResource(R.string.voice_permission)
    val tooShortError = stringResource(R.string.voice_too_short)
    val tooLongError = stringResource(R.string.voice_too_long)
    val tooQuietError = stringResource(R.string.voice_too_quiet)
    val notEnoughSpeechError = stringResource(R.string.voice_not_enough_speech)
    val modelMissingError = stringResource(R.string.voice_model_missing)
    val failedError = stringResource(R.string.voice_failed)

    fun startRecording(stage: VoiceSampleStage) {
        if (recordingStage != null || processingStage != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            error = permissionError
            return
        }
        frames.clear()
        totalSamples.set(0)
        speechSamples.set(0)
        lastSpeechAt.set(SystemClock.elapsedRealtime())
        elapsedSeconds = 0f
        error = null
        saved = false
        val vadInstance = SileroVad(context.assets)
        val sourceInstance = AudioRecordSource()
        vad = vadInstance
        source = sourceInstance
        recordingStage = stage
        Log.i(TAG, "enrollment recording started: $stage")
        sourceInstance.start { frame ->
            frames.add(frame)
            totalSamples.addAndGet(frame.size)
            if (vadInstance.isSpeech(frame)) {
                speechSamples.addAndGet(frame.size)
                lastSpeechAt.set(SystemClock.elapsedRealtime())
            }
        }
    }

    fun stopRecording() {
        val stage = recordingStage ?: return
        recordingStage = null
        source?.stop()
        source = null
        vad?.release()
        vad = null
        processingStage = stage
        val pcm = synchronized(frames) {
            val total = frames.sumOf { it.size }
            val result = ShortArray(total)
            var offset = 0
            frames.forEach { frame ->
                frame.copyInto(result, offset)
                offset += frame.size
            }
            result
        }
        val speech = speechSamples.get()
        val assessment = VoiceEnrollment.assess(pcm, speech)
        Log.i(
            TAG,
            "assessment stage=$stage duration=${assessment.durationSeconds}s " +
                "rms=${assessment.rms} speechRatio=${assessment.speechRatio} " +
                "issue=${assessment.issue}"
        )
        if (!assessment.ok) {
            error = when (assessment.issue) {
                VoiceIssue.TOO_SHORT -> tooShortError
                VoiceIssue.TOO_LONG -> tooLongError
                VoiceIssue.TOO_QUIET -> tooQuietError
                VoiceIssue.NOT_ENOUGH_SPEECH -> notEnoughSpeechError
                null -> null
            }
            processingStage = null
            return
        }
        scope.launch {
            try {
                val embedder = SherpaSpeakerEmbedder(context.assets)
                val embedding = embedder.embed(pcm)
                when (stage) {
                    VoiceSampleStage.CHINESE -> {
                        chineseEmbedding = embedding
                        chineseSamples = pcm.size
                    }
                    VoiceSampleStage.ENGLISH -> {
                        englishEmbedding = embedding
                        englishSamples = pcm.size
                    }
                }
                Log.i(TAG, "sample ready: $stage samples=${pcm.size}")
            } catch (throwable: Throwable) {
                Log.e(TAG, "sample embedding failed: $stage", throwable)
                error = if (throwable is SpeakerModelMissingException) {
                    modelMissingError
                } else {
                    failedError
                }
            } finally {
                processingStage = null
            }
        }
    }

    fun saveProfile() {
        val chinese = chineseEmbedding ?: return
        val english = englishEmbedding ?: return
        if (processingStage != null) return
        processingStage = VoiceSampleStage.CHINESE
        scope.launch {
            try {
                store.save(
                    embeddings = listOf(chinese, english),
                    samples = chineseSamples + englishSamples
                )
                saved = true
                Log.i(
                    TAG,
                    "bilingual profile saved chinese=${chineseSamples} " +
                        "english=${englishSamples}"
                )
            } catch (throwable: Throwable) {
                Log.e(TAG, "profile save failed", throwable)
                error = failedError
            } finally {
                processingStage = null
            }
        }
    }

    LaunchedEffect(recordingStage) {
        while (recordingStage != null) {
            delay(100)
            elapsedSeconds = totalSamples.get().toFloat() / 16_000f
            val total = totalSamples.get()
            val speech = speechSamples.get()
            val enoughSpeech = total > 0 &&
                speech.toFloat() / total >= VoiceEnrollment.MIN_SPEECH_RATIO
            val quietFor = SystemClock.elapsedRealtime() - lastSpeechAt.get()
            if (elapsedSeconds >= VoiceEnrollment.MAX_SECONDS) {
                stopRecording()
            } else if (
                elapsedSeconds >= VoiceEnrollment.MIN_SECONDS &&
                enoughSpeech &&
                quietFor >= 4_000
            ) {
                Log.i(TAG, "auto-stopping after silence")
                stopRecording()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            source?.stop()
            vad?.release()
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
            title = stringResource(R.string.voice_title),
            subtitle = stringResource(R.string.voice_subtitle)
        )

        Spacer(Modifier.height(20.dp))

        if (saved) {
            GroupedCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.voice_saved),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.voice_saved_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.voice_done),
                onClick = onBack
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.voice_record_again),
                onClick = {
                    saved = false
                    chineseEmbedding = null
                    englishEmbedding = null
                    chineseSamples = 0
                    englishSamples = 0
                    error = null
                }
            )
            return@Column
        }

        SectionHeader(stringResource(R.string.voice_profiles_header))

        SampleSection(
            header = stringResource(R.string.voice_chinese_section),
            prompts = listOf(
                stringResource(R.string.voice_chinese_prompt_1),
                stringResource(R.string.voice_chinese_prompt_2)
            ),
            ready = chineseEmbedding != null,
            recording = recordingStage == VoiceSampleStage.CHINESE,
            processing = processingStage == VoiceSampleStage.CHINESE,
            recordLabel = stringResource(R.string.voice_record_chinese),
            stopLabel = stringResource(R.string.voice_stop),
            readyLabel = stringResource(R.string.voice_sample_ready),
            checkingLabel = stringResource(R.string.voice_checking),
            onToggle = {
                if (recordingStage == VoiceSampleStage.CHINESE) {
                    stopRecording()
                } else {
                    startRecording(VoiceSampleStage.CHINESE)
                }
            }
        )

        Spacer(Modifier.height(20.dp))

        SampleSection(
            header = stringResource(R.string.voice_english_section),
            prompts = listOf(
                stringResource(R.string.voice_english_prompt_1),
                stringResource(R.string.voice_english_prompt_2)
            ),
            ready = englishEmbedding != null,
            recording = recordingStage == VoiceSampleStage.ENGLISH,
            processing = processingStage == VoiceSampleStage.ENGLISH,
            recordLabel = stringResource(R.string.voice_record_english),
            stopLabel = stringResource(R.string.voice_stop),
            readyLabel = stringResource(R.string.voice_sample_ready),
            checkingLabel = stringResource(R.string.voice_checking),
            onToggle = {
                if (recordingStage == VoiceSampleStage.ENGLISH) {
                    stopRecording()
                } else {
                    startRecording(VoiceSampleStage.ENGLISH)
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.voice_instructions),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(20.dp))

        if (chineseEmbedding != null && englishEmbedding != null) {
            PrimaryButton(
                text = stringResource(R.string.voice_save_profile),
                enabled = processingStage == null,
                onClick = { saveProfile() }
            )
        } else {
            Text(
                text = stringResource(R.string.voice_need_both),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (recordingStage != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.voice_seconds, elapsedSeconds.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SampleSection(
    header: String,
    prompts: List<String>,
    ready: Boolean,
    recording: Boolean,
    processing: Boolean,
    recordLabel: String,
    stopLabel: String,
    readyLabel: String,
    checkingLabel: String,
    onToggle: () -> Unit
) {
    SectionHeader(header)
    GroupedCard {
        prompts.forEachIndexed { index, prompt ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (index != prompts.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
        if (ready) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = readyLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton(
        text = when {
            recording -> stopLabel
            processing -> checkingLabel
            ready -> recordLabel
            else -> recordLabel
        },
        enabled = !processing,
        onClick = onToggle
    )
}
