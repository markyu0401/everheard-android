package io.github.markyu0401.everheard.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.markyu0401.everheard.ui.theme.EverheardTheme
import org.junit.Rule
import org.junit.Test

class PermissionGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun microphone_gate_explains_why_and_offers_the_request() {
        composeRule.setContent {
            EverheardTheme {
                PermissionGate(
                    kind = PermissionKind.Microphone,
                    onRequest = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("Microphone access is required").assertIsDisplayed()
        composeRule.onNodeWithText("Allow microphone").assertIsDisplayed()
        composeRule.onNodeWithText("Open system settings").assertIsDisplayed()
    }
}
