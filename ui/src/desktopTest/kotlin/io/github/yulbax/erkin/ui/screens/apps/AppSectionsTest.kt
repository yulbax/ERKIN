package io.github.yulbax.erkin.ui.screens.apps

import io.github.yulbax.erkin.ui.components.ScrollbarSection
import io.github.yulbax.erkin.ui.viewmodel.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSectionsTest {
    private fun apps(vararg names: String) = names.map { AppInfo(packageName = it, name = it, isSystemApp = false) }

    @Test
    fun groupsByFirstLetterIgnoringCase() {
        assertEquals(
            listOf(
                ScrollbarSection("#", 0),
                ScrollbarSection("A", 2),
                ScrollbarSection("B", 4),
                ScrollbarSection("Т", 5)
            ),
            appSections(apps("7-Zip", " _tool", "alacritty", "Anki", "brave", "Телеграм"))
        )
    }

    @Test
    fun blankNamesGoToOther() {
        assertEquals("#", sectionLabel("   "))
        assertEquals("Ё", sectionLabel("ёлка"))
    }

    @Test
    fun emptyListHasNoSections() {
        assertEquals(emptyList<ScrollbarSection>(), appSections(emptyList()))
    }
}
