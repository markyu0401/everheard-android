package io.github.markyu0401.everheard.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    var source by remember { mutableStateOf<AudioRecordSource?>(null) }
    var vad by remember { mutableStateOf<SileroVad?>(null) }
    var recording by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    val permissionError = stringResource(R.string.voice_permission)
    val tooShortError = stringResource(R.string.voice_too_short)
    val tooLongError = stringResource(R.string.voice_too_long)
    val tooQuietError = stringResource(R.string.voice_too_quiet)
    val notEnoughSpeechError = stringResource(R.string.voice_not_enough_speech)
    val modelMissingError = stringResource(R.string.voice_model_missing)
    val failedError = stringResource(R.string.voice_failed)

    fun startRecording() {
        if (recording || processing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            error = permissionError
            return
        }
        frames.clear()
        totalSamples.set(0)
        speechSamples.set(0)
        elapsedSeconds = 0f
        error = null
        saved = false
        val vadInstance = SileroVad(context.assets)
        val sourceInstance = AudioRecordSource()
        vad = vadInstance
        source = sourceInstance
        recording = true
        sourceInstance.start { frame ->
            frames.add(frame)
            totalSamples.addAndGet(frame.size)
            if (vadInstance.isSpeech(frame)) {
                speechSamples.addAndGet(frame.size)
            }
        }
    }

    fun stopRecording() {
        if (!recording) return
        recording = false
        source?.stop()
        source = null
        vad?.release()
        vad = null
        processing = true
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
        if (!assessment.ok) {
            error = when (assessment.issue) {
                VoiceIssue.TOO_SHORT -> tooShortError
                VoiceIssue.TOO_LONG -> tooLongError
                VoiceIssue.TOO_QUIET -> tooQuietError
                VoiceIssue.NOT_ENOUGH_SPEECH -> notEnoughSpeechError
                null -> null
            }
            processing = false
            return
        }
        scope.launch {
            try {
                val embedder = SherpaSpeakerEmbedder(context.assets)
                val embedding = embedder.embed(pcm)
                store.save(embedding, pcm.size)
                saved = true
            } catch (throwable: Throwable) {
                error = if (throwable is io.github.markyu0401.everheard.speaker.SpeakerModelMissingException) {
                    modelMissingError
                } else {
                    failedError
                }
            } finally {
                processing = false
            }
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(100)
            elapsedSeconds = totalSamples.get().toFloat() / 16_000f
            if (elapsedSeconds >= VoiceEnrollment.MAX_SECONDS) {
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
                    error = null
                }
            )
            return@Column
        }

        SectionHeader(stringResource(R.string.voice_prompts_header))
        GroupedCard {
            val prompts = listOf(
                stringResource(R.string.voice_prompt_1),
                stringResource(R.string.voice_prompt_2),
                stringResource(R.string.voice_prompt_3)
            )
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
        }

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
        PrimaryButton(
            text = when {
                recording -> stringResource(R.string.voice_stop)
                processing -> stringResource(R.string.voice_checking)
                else -> stringResource(R.string.voice_record)
            },
            enabled = !processing,
            onClick = {
                if (recording) stopRecording() else startRecording()
            }
        )
        if (recording) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.voice_seconds, elapsedSeconds.toInt()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
