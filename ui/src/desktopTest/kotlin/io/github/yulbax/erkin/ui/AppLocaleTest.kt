package io.github.yulbax.erkin.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.yulbax.erkin.ui.platform.AppLocale
import io.github.yulbax.erkin.ui.platform.ProvideAppLocale
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
    private val initial = AppLocale.currentTag()

    @After
    fun restore() {
        AppLocale.apply(initial)
    }

    @Test
    fun switchingLanguageUpdatesStringsWithoutRecreatingTheScreen() = runBlocking {
        withContext(Dispatchers.Main) {
            AppLocale.apply("en")
            var text = ""
            var compositions = 0
            val scene = ImageComposeScene(width = 100, height = 100, density = Density(1f)) {
                ProvideAppLocale {
                    text = stringResource(Res.string.cancel)
                }
                androidx.compose.runtime.SideEffect { compositions++ }
            }
            try {
                settle(scene) { text == "Cancel" }
                assertEquals("Cancel", text)
                val before = compositions

                AppLocale.apply("ru")
                settle(scene) { text == "Отмена" }

                assertEquals("Отмена", text)
                assertEquals("the outer composition must not be recreated", before, compositions)
                assertEquals("ru", AppLocale.currentTag())
            } finally {
                scene.close()
            }
        }
    }

    private suspend fun settle(scene: ImageComposeScene, done: () -> Boolean) {
        val start = System.nanoTime()
        repeat(100) {
            scene.render(System.nanoTime() - start)
            if (done()) return
            delay(20)
        }
    }
}
