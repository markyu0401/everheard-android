package io.github.markyu0401.everheard.capture.vad

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SileroVadTest {

    @Test
    fun initializes_and_reports_silence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vad = SileroVad(context.assets)

        repeat(50) {
            assertFalse(vad.isSpeech(ShortArray(320)))
        }

        vad.release()
    }
}
