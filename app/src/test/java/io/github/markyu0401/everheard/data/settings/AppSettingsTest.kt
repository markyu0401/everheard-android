package io.github.markyu0401.everheard.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppSettingsTest {

    @Test
    fun system_theme_follows_the_device() {
        assertThat(ThemeMode.SYSTEM.resolve(systemDark = true)).isTrue()
        assertThat(ThemeMode.SYSTEM.resolve(systemDark = false)).isFalse()
        assertThat(ThemeMode.LIGHT.resolve(systemDark = true)).isFalse()
        assertThat(ThemeMode.DARK.resolve(systemDark = false)).isTrue()
    }

    @Test
    fun language_tags_match_android_resource_qualifiers() {
        assertThat(AppLanguage.SYSTEM.tag).isNull()
        assertThat(AppLanguage.ENGLISH.tag).isEqualTo("en")
        assertThat(AppLanguage.CHINESE.tag).isEqualTo("zh-CN")
    }
}
