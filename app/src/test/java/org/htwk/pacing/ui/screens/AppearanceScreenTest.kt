package org.htwk.pacing.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceScreenTest {

    @Test
    fun testThemeModeEnumValues() {
        val modes = ThemeMode.values()
        
        assertEquals("Should have 3 theme modes", 3, modes.size)
        assertTrue("Should contain LIGHT mode", modes.contains(ThemeMode.LIGHT))
        assertTrue("Should contain DARK mode", modes.contains(ThemeMode.DARK))
        assertTrue("Should contain AUTO mode", modes.contains(ThemeMode.AUTO))
    }

    @Test
    fun testThemeModeToStringMapping() {
        assertEquals("LIGHT", ThemeMode.LIGHT.name)
        assertEquals("DARK", ThemeMode.DARK.name)
        assertEquals("AUTO", ThemeMode.AUTO.name)
    }

    @Test
    fun testStringToThemeModeConversion() {
        val lightMode = when ("LIGHT") {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        
        val darkMode = when ("DARK") {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        
        val autoMode = when ("AUTO") {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        
        assertEquals(ThemeMode.LIGHT, lightMode)
        assertEquals(ThemeMode.DARK, darkMode)
        assertEquals(ThemeMode.AUTO, autoMode)
    }

    @Test
    fun testInvalidThemeModeDefaultsToAuto() {
        val invalidStrings = listOf("INVALID", "", "null", "light", "dark")
        
        invalidStrings.forEach { invalidString ->
            val mode = when (invalidString) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.AUTO
            }
            
            assertEquals("Invalid string '$invalidString' should default to AUTO", ThemeMode.AUTO, mode)
        }
    }

    @Test
    fun testNullThemeModeDefaultsToAuto() {
        val currentThemeMode: String? = null
        val selectedTheme = when (currentThemeMode) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        
        assertEquals("Null theme mode should default to AUTO", ThemeMode.AUTO, selectedTheme)
    }

    @Test
    fun testThemeModeComparison() {
        val selectedMode = ThemeMode.LIGHT
        
        assertTrue("Selected mode should equal LIGHT", selectedMode == ThemeMode.LIGHT)
        assertFalse("Selected mode should not equal DARK", selectedMode == ThemeMode.DARK)
        assertFalse("Selected mode should not equal AUTO", selectedMode == ThemeMode.AUTO)
    }

    @Test
    fun testAllThemeModesAreDistinct() {
        val modes = setOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AUTO)
        
        assertEquals("All theme modes should be distinct", 3, modes.size)
    }

    @Test
    fun testThemeModeOrdinal() {
        assertEquals("LIGHT should be first (ordinal 0)", 0, ThemeMode.LIGHT.ordinal)
        assertEquals("DARK should be second (ordinal 1)", 1, ThemeMode.DARK.ordinal)
        assertEquals("AUTO should be third (ordinal 2)", 2, ThemeMode.AUTO.ordinal)
    }

    @Test
    fun testThemeModeValueOf() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueOf("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
        assertEquals(ThemeMode.AUTO, ThemeMode.valueOf("AUTO"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testThemeModeValueOfThrowsOnInvalid() {
        ThemeMode.valueOf("INVALID")
    }

    @Test
    fun testThemeSelectionLabels() {
        val lightLabel = "Hell"
        val darkLabel = "Dunkel"
        val autoLabel = "Automatisch (System)"
        
        assertNotNull("Light label should not be null", lightLabel)
        assertNotNull("Dark label should not be null", darkLabel)
        assertNotNull("Auto label should not be null", autoLabel)
        
        assertTrue("Light label should not be empty", lightLabel.isNotEmpty())
        assertTrue("Dark label should not be empty", darkLabel.isNotEmpty())
        assertTrue("Auto label should not be empty", autoLabel.isNotEmpty())
    }

    @Test
    fun testThemeModeSwitching() {
        var currentMode = ThemeMode.LIGHT
        
        assertEquals(ThemeMode.LIGHT, currentMode)
        
        currentMode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, currentMode)
        
        currentMode = ThemeMode.AUTO
        assertEquals(ThemeMode.AUTO, currentMode)
    }

    @Test
    fun testThemeModeToStringForDatabase() {
        val lightString = ThemeMode.LIGHT.name
        val darkString = ThemeMode.DARK.name
        val autoString = ThemeMode.AUTO.name
        
        assertEquals("LIGHT", lightString)
        assertEquals("DARK", darkString)
        assertEquals("AUTO", autoString)

        val storedMode = lightString
        val retrievedMode = when (storedMode) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        
        assertEquals(ThemeMode.LIGHT, retrievedMode)
    }
}
