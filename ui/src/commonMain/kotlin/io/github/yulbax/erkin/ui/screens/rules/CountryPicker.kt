package io.github.yulbax.erkin.ui.screens.rules

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yulbax.erkin.ui.components.CountryFlag
import io.github.yulbax.erkin.ui.components.transparentFieldColors
import io.github.yulbax.erkin.ui.res.*
import io.github.yulbax.erkin.vpn.core.RuleValues
import java.util.Locale
import org.jetbrains.compose.resources.stringResource

internal fun countryName(code: String): String =
    runCatching { Locale.Builder().setRegion(code).build().displayCountry }.getOrNull()?.takeIf { it.isNotBlank() } ?: code

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CountryPicker(code: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(code.takeIf { it in RuleValues.Country.CODES }?.let(::countryName).orEmpty()) }
    val countries = remember { RuleValues.Country.CODES.map { it to countryName(it) }.sortedBy { it.second } }
    val matches = countries.filter { (iso, name) ->
        query.isBlank() || name.contains(query.trim(), ignoreCase = true) || iso.equals(query.trim(), ignoreCase = true)
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        TextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
                onSelect(countries.firstOrNull { (iso, name) -> name.equals(it.trim(), true) || iso.equals(it.trim(), true) }?.first.orEmpty())
            },
            label = { Text(stringResource(Res.string.rule_country)) },
            placeholder = { Text(stringResource(Res.string.rule_country_search)) },
            leadingIcon = if (code in RuleValues.Country.CODES) {
                { CountryFlag(code, 18.sp) }
            } else {
                null
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            colors = transparentFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            matches.forEach { (iso, name) ->
                DropdownMenuItem(
                    text = { Text("$name ($iso)") },
                    leadingIcon = { CountryFlag(iso, 18.sp) },
                    onClick = {
                        query = name
                        onSelect(iso)
                        expanded = false
                    }
                )
            }
        }
    }
}
