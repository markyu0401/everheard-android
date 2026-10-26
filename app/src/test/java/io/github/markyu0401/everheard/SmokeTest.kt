package io.github.markyu0401.everheard

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun package_is_stable() {
        assertEquals("io.github.markyu0401.everheard", BuildConfig.APPLICATION_ID)
    }
}
