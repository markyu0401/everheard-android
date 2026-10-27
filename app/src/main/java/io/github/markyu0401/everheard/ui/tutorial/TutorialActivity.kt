package io.github.markyu0401.everheard.ui.tutorial

import android.os.Bundle
import androidx.activity.compose.setContent
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme

/** Replayable walkthrough launched from Settings → Help. */
class TutorialActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardAppTheme {
                TutorialScreen(onDone = { finish() })
            }
        }
    }
}
