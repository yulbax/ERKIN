package io.github.yulbax.erkin.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.ui.components.GroupCard
import io.github.yulbax.erkin.ui.components.GroupInfoRow
import io.github.yulbax.erkin.util.VersionInfo
import org.koin.compose.koinInject

@Composable
fun About() {
    val versions = koinInject<VersionInfo>()
    val unknown = stringResource(Res.string.about_unknown)

    val appVersion = versions.appVersion?.let { version ->
        versions.shortBuild?.let { "$version ($it)" } ?: version
    } ?: unknown
    val coreVersion by produceState(initialValue = "…") {
        value = versions.coreVersion() ?: unknown
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        GroupCard(
            title = stringResource(Res.string.about_versions),
            items = listOf(
                { GroupInfoRow(stringResource(Res.string.about_app), appVersion) },
                { GroupInfoRow(stringResource(Res.string.about_core), coreVersion) },
                { GroupInfoRow(stringResource(Res.string.about_byedpi), versions.byeDpiVersion) }
            )
        )

        GroupCard(
            title = stringResource(Res.string.about_legal),
            items = listOf(
                {
                    GroupInfoRow(
                        stringResource(Res.string.about_license_row),
                        stringResource(Res.string.about_license_value)
                    )
                },
                {
                    GroupInfoRow(
                        stringResource(Res.string.about_font_row),
                        stringResource(Res.string.about_font_value)
                    )
                }
            )
        )
    }
}
