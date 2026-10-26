package io.github.markyu0401.everheard.capture

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.markyu0401.everheard.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureServiceTest {

    @Test
    fun start_from_visible_activity_shows_foreground_notification() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                CaptureController.start(activity)
            }

            Thread.sleep(1_000)

            val context = ApplicationProvider.getApplicationContext<Context>()
            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = manager.activeNotifications.firstOrNull {
                it.id == CaptureNotification.NOTIF_ID
            }

            assertNotNull("foreground notification is visible", notification)
            assertTrue("service reports running", CaptureService.isRunning)

            CaptureController.stop(context)
        }
    }
}
