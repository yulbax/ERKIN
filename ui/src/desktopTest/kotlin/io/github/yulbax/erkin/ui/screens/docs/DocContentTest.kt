package io.github.yulbax.erkin.ui.screens.docs

import io.github.yulbax.erkin.ui.res.Res
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocContentTest {

    @Test
    fun imagesSplitTheTextAndResolveFromTheArticleDirectory() {
        val doc = DocParser.parse("# Title\n\nIntro\n\n![Home](../img/ru/main.png)\n\nMore", "files/docs/ru")

        assertEquals(
            Doc("Title", listOf(DocBlock.Text("Intro"), DocBlock.Image("files/docs/img/ru/main.png", "Home"), DocBlock.Text("More"))),
            doc
        )
    }

    @Test
    fun everyArticleHasATitleAndItsImagesInEveryWrittenLanguage() = runBlocking {
        listOf(DocLibrary.FALLBACK_LANGUAGE, "ru").forEach { language ->
            DocArticle.entries.forEach { article ->
                val doc = DocLibrary.load(article, language)
                assertTrue("$language/${article.slug}", doc.title.isNotBlank())
                doc.blocks.filterIsInstance<DocBlock.Image>().forEach { image ->
                    assertTrue(image.path, image.path.contains("/$language/") && Res.readBytes(image.path).isNotEmpty())
                }
            }
        }
    }
}
