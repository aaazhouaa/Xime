package com.kingzcheung.xime.service

import android.media.AudioManager
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class KeySoundTest {

    @Test
    fun testSoundTypeConstants() {
        assertEquals("default", SettingsPreferences.SOUND_TYPE_DEFAULT)
        assertEquals("system", SettingsPreferences.SOUND_TYPE_SYSTEM)
    }

    @Test
    fun testSystemSoundFxMapping() {
        val mapping = mapOf(
            "delete" to AudioManager.FX_KEYPRESS_DELETE,
            "enter" to AudioManager.FX_KEYPRESS_RETURN,
            "space" to AudioManager.FX_KEYPRESS_SPACEBAR,
            "standard" to AudioManager.FX_KEYPRESS_STANDARD
        )

        assertEquals(AudioManager.FX_KEYPRESS_DELETE, mapping["delete"])
        assertEquals(AudioManager.FX_KEYPRESS_RETURN, mapping["enter"])
        assertEquals(AudioManager.FX_KEYPRESS_SPACEBAR, mapping["space"])
        assertEquals(AudioManager.FX_KEYPRESS_STANDARD, mapping["standard"])
    }
}
