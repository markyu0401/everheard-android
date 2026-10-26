package io.github.markyu0401.everheard.capture.audio

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioRecordSourceTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun records_frames_at_the_expected_size() {
        val source = AudioRecordSource()
        val frames = AtomicInteger(0)
        val badSizes = AtomicInteger(0)

        source.start { frame ->
            frames.incrementAndGet()
            if (frame.size != 320) badSizes.incrementAndGet()
        }

        Thread.sleep(3_000)
        source.stop()

        assertTrue("recorded at least 100 frames", frames.get() >= 100)
        assertTrue("all frames are 320 samples", badSizes.get() == 0)
        assertTrue("source is stopped", !source.isRecording)
    }
}
