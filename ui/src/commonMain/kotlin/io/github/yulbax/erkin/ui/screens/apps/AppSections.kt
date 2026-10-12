package io.github.yulbax.erkin.ui.screens.apps

import io.github.yulbax.erkin.ui.components.ScrollbarSection
import io.github.yulbax.erkin.ui.viewmodel.AppInfo

private const val OTHER_SECTION = "#"

internal fun appSections(apps: List<AppInfo>): List<ScrollbarSection> {
    val sections = mutableListOf<ScrollbarSection>()
    apps.forEachIndexed { index, app ->
        val label = sectionLabel(app.name)
        if (sections.lastOrNull()?.label != label) sections += ScrollbarSection(label, index)
    }
    return sections
}

internal fun sectionLabel(name: String): String {
    val first = name.trimStart().firstOrNull() ?: return OTHER_SECTION
    return if (first.isLetter()) first.uppercase() else OTHER_SECTION
}
