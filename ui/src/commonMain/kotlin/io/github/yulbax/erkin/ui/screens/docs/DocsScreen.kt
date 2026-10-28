package io.github.yulbax.erkin.ui.screens.docs

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.SourceIcon
import io.github.yulbax.erkin.ui.res.Res
import io.github.yulbax.erkin.ui.res.docs_language
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

@Composable
fun Docs(onOpen: (DocArticle) -> Unit) {
    val language = stringResource(Res.string.docs_language)
    val titles by produceState(emptyMap<DocArticle, String>(), language) {
        value = DocArticle.entries.associateWith { DocLibrary.load(it, language).title }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GroupCard(
            items = DocArticle.entries.map { article ->
                { ArticleRow(article, titles[article].orEmpty()) { onOpen(article) } }
            }
        )
    }
}

@Composable
private fun ArticleRow(article: DocArticle, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceIcon(article.icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun DocPage(article: DocArticle) {
    val language = stringResource(Res.string.docs_language)
    val doc by produceState<Doc?>(null, article, language) { value = DocLibrary.load(article, language) }
    val current = doc ?: return
    val typography = MaterialTheme.typography
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(current.title, style = typography.headlineSmall)
        current.blocks.forEach { block ->
            when (block) {
                is DocBlock.Text -> Markdown(
                    content = block.markdown,
                    typography = markdownTypography(
                        h1 = typography.titleLarge,
                        h2 = typography.titleMedium,
                        h3 = typography.titleSmall,
                        h4 = typography.titleSmall,
                        h5 = typography.titleSmall,
                        h6 = typography.titleSmall
                    )
                )
                is DocBlock.Image -> DocImage(block, Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun DocImage(image: DocBlock.Image, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, image.path) {
        value = runCatching { Res.readBytes(image.path).decodeToImageBitmap() }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = image.description,
            contentScale = ContentScale.FillWidth,
            modifier = modifier.widthIn(max = IMAGE_MAX_WIDTH).fillMaxWidth().clip(RoundedCornerShape(16.dp))
        )
    }
}

private val IMAGE_MAX_WIDTH = 320.dp
