package com.prgramed.eprayer.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prgramed.eprayer.domain.model.CalculationMethodType

private val NavyCard = Color(0xFF162135)
private val Peach = Color(0xFFE8B98A)
private val TextMuted = Color(0xFF8899AA)

private val methodDisplayNames = mapOf(
    CalculationMethodType.MUSLIM_WORLD_LEAGUE to "Muslim World League",
    CalculationMethodType.ISNA to "ISNA",
    CalculationMethodType.EGYPTIAN to "Egyptian General Authority",
    CalculationMethodType.UMM_AL_QURA to "Umm Al-Qura",
    CalculationMethodType.KARACHI to "University of Karachi",
    CalculationMethodType.DUBAI to "Dubai",
    CalculationMethodType.QATAR to "Qatar",
    CalculationMethodType.KUWAIT to "Kuwait",
    CalculationMethodType.MOONSIGHTING_COMMITTEE to "Moonsighting Committee",
    CalculationMethodType.SINGAPORE to "Singapore",
    CalculationMethodType.NORTH_AMERICA to "ISNA (North America)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculationMethodSection(
    selectedMethod: CalculationMethodType,
    onMethodSelected: (CalculationMethodType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Calculation Method",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            TextField(
                value = methodDisplayNames[selectedMethod] ?: selectedMethod.name,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NavyCard,
                    unfocusedContainerColor = NavyCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedTrailingIconColor = TextMuted,
                    unfocusedTrailingIconColor = TextMuted,
                    focusedIndicatorColor = Peach,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                CalculationMethodType.entries.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(methodDisplayNames[method] ?: method.name) },
                        onClick = {
                            onMethodSelected(method)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
