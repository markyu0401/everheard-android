package io.github.markyu0401.everheard.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.github.markyu0401.everheard.ui.components.SettingsRow
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "VoiceEnrollment"

private data class VoiceLanguageOption(
    val tag: String,
    val labelRes: Int,
    val promptRes: List<Int>
)

private val VOICE_LANGUAGES = listOf(
    VoiceLanguageOption(
        tag = "zh",
        labelRes = R.string.voice_language_chinese,
        promptRes = listOf(
            R.string.voice_chinese_prompt_1,
            R.string.voice_chinese_prompt_2
        )
    ),
    VoiceLanguageOption(
        tag = "en",
        labelRes = R.string.voice_language_english,
        promptRes = listOf(
            R.string.voice_english_prompt_1,
            R.string.voice_english_prompt_2
        )
    )
)

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
    var recordingLanguage by remember { mutableStateOf<String?>(null) }
    var processingLanguage by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("zh") }
    var menuExpanded by remember { mutableStateOf(false) }
    var recordedEmbeddings by remember { mutableStateOf<Map<String, FloatArray>>(emptyMap()) }
    var samplesByLanguage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val permissionError = stringResource(R.string.voice_permission)
    val tooShortError = stringResource(R.string.voice_too_short)
    val tooLongError = stringResource(R.string.voice_too_long)
    val tooQuietError = stringResource(R.string.voice_too_quiet)
    val notEnoughSpeechError = stringResource(R.string.voice_not_enough_speech)
    val modelMissingError = stringResource(R.string.voice_model_missing)
    val failedError = stringResource(R.string.voice_failed)

    LaunchedEffect(Unit) {
        val existing = store.load()
        if (existing != null) {
            val embeddings = existing.allEmbeddings()
            val languages = existing.languages.ifEmpty {
                List(embeddings.size) { index -> if (index == 0) "en" else "und" }
            }
            recordedEmbeddings = languages.zip(embeddings).toMap()
            val perLanguage = if (embeddings.isEmpty()) 0 else existing.samples / embeddings.size
            samplesByLanguage = languages.associateWith { perLanguage }
            selectedLanguage = languages.firstOrNull { tag ->
                VOICE_LANGUAGES.any { it.tag == tag }
            } ?: "zh"
        }
    }

    fun startRecording() {
        if (recordingLanguage != null || processingLanguage != null) return
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
        recordingLanguage = selectedLanguage
        Log.i(TAG, "enrollment recording started: $selectedLanguage")
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
        val language = recordingLanguage ?: return
        recordingLanguage = null
        source?.stop()
        source = null
        vad?.release()
        vad = null
        processingLanguage = language
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
            "assessment language=$language duration=${assessment.durationSeconds}s " +
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
            processingLanguage = null
            return
        }
        scope.launch {
            try {
                val embedder = SherpaSpeakerEmbedder(context.assets)
                val embedding = embedder.embed(pcm)
                recordedEmbeddings = recordedEmbeddings + (language to embedding)
                samplesByLanguage = samplesByLanguage + (language to pcm.size)
                Log.i(TAG, "sample ready: $language samples=${pcm.size}")
            } catch (throwable: Throwable) {
                Log.e(TAG, "sample embedding failed: $language", throwable)
                error = if (throwable is SpeakerModelMissingException) {
                    modelMissingError
                } else {
                    failedError
                }
            } finally {
                processingLanguage = null
            }
        }
    }

    fun saveProfile() {
        if (recordedEmbeddings.isEmpty() || processingLanguage != null) return
        val languages = recordedEmbeddings.keys.toList()
        val embeddings = languages.map { recordedEmbeddings.getValue(it) }
        processingLanguage = selectedLanguage
        scope.launch {
            try {
                store.save(
                    embeddings = embeddings,
                    languages = languages,
                    samples = samplesByLanguage.values.sum()
                )
                saved = true
                Log.i(
                    TAG,
                    "voice profile saved languages=$languages " +
                        "samples=${samplesByLanguage.values.sum()}"
                )
            } catch (throwable: Throwable) {
                Log.e(TAG, "profile save failed", throwable)
                error = failedError
            } finally {
                processingLanguage = null
            }
        }
    }

    LaunchedEffect(recordingLanguage) {
        while (recordingLanguage != null) {
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

    val selectedOption = VOICE_LANGUAGES.firstOrNull { it.tag == selectedLanguage }
        ?: VOICE_LANGUAGES.first()
    val selectedReady = recordedEmbeddings.containsKey(selectedOption.tag)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
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
                    recordedEmbeddings = emptyMap()
                    samplesByLanguage = emptyMap()
                    error = null
                }
            )
            return@Column
        }

        SectionHeader(stringResource(R.string.voice_language_header))
        GroupedCard {
            Box {
                SettingsRow(
                    icon = Icons.Filled.Face,
                    title = stringResource(selectedOption.labelRes),
                    subtitle = stringResource(
                        if (selectedReady) {
                            R.string.voice_sample_ready
                        } else {
                            R.string.voice_not_recorded
                        }
                    ),
                    trailingContent = {
                        Text(
                            text = "▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    showDivider = false,
                    showChevron = false,
                    onClick = { menuExpanded = true }
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    VOICE_LANGUAGES.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = buildString {
                                        append(stringResource(option.labelRes))
                                        if (recordedEmbeddings.containsKey(option.tag)) {
                                            append("  ✓")
                                        }
                                    }
                                )
                            },
                            onClick = {
                                selectedLanguage = option.tag
                                menuExpanded = false
                                error = null
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        SampleSection(
            header = stringResource(R.string.voice_prompts_header),
            prompts = selectedOption.promptRes.map { stringResource(it) },
            ready = selectedReady,
            recording = recordingLanguage == selectedOption.tag,
            processing = processingLanguage == selectedOption.tag,
            recordLabel = stringResource(R.string.voice_record),
            stopLabel = stringResource(R.string.voice_stop),
            readyLabel = stringResource(R.string.voice_sample_ready),
            checkingLabel = stringResource(R.string.voice_checking),
            onToggle = {
                if (recordingLanguage == selectedOption.tag) {
                    stopRecording()
                } else {
                    startRecording()
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

        if (recordedEmbeddings.isNotEmpty()) {
            PrimaryButton(
                text = stringResource(R.string.voice_save_profile),
                enabled = processingLanguage == null,
                onClick = { saveProfile() }
            )
        } else {
            Text(
                text = stringResource(R.string.voice_need_one),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (recordingLanguage != null) {
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
            else -> recordLabel
        },
        enabled = !processing,
        onClick = onToggle
    )
}
