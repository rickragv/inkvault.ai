package com.edgeai.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edgeai.app.ui.theme.EntityCompany
import com.edgeai.app.ui.theme.EntityDate
import com.edgeai.app.ui.theme.EntityDesignation
import com.edgeai.app.ui.theme.EntityLocation
import com.edgeai.app.ui.theme.EntityMoney
import com.edgeai.app.ui.theme.EntityPerson
import com.edgeai.app.ui.theme.EntityShell

@Composable
fun EntityChip(
    name: String,
    entityType: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val color = entityTypeColor(entityType)

    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = {
            Text(
                text = entityTypeIcon(entityType),
                modifier = Modifier.padding(end = 2.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.12f),
            labelColor = color,
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier,
    )
}

fun entityTypeColor(type: String): Color = when (type.lowercase()) {
    "person" -> EntityPerson
    "company" -> EntityCompany
    "shell_entity" -> EntityShell
    "money" -> EntityMoney
    "date" -> EntityDate
    "location" -> EntityLocation
    "designation" -> EntityDesignation
    else -> Color.Gray
}

fun entityTypeIcon(type: String): String = when (type.lowercase()) {
    "person" -> "\uD83D\uDC64"
    "company" -> "\uD83C\uDFE2"
    "shell_entity" -> "\u26A0\uFE0F"
    "money" -> "\uD83D\uDCB0"
    "date" -> "\uD83D\uDCC5"
    "location" -> "\uD83D\uDCCD"
    "designation" -> "\uD83C\uDFF7\uFE0F"
    else -> "\uD83D\uDD35"
}
