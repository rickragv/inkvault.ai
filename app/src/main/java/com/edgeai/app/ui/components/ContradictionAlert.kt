package com.edgeai.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edgeai.app.data.entity.Contradiction

@Composable
fun ContradictionAlert(
    contradiction: Contradiction,
    docATitle: String = "Document A",
    docBTitle: String = "Document B",
    modifier: Modifier = Modifier,
) {
    val severityColor = when (contradiction.severity) {
        "high" -> MaterialTheme.colorScheme.error
        "medium" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.08f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = contradiction.contradictionType.replace("_", " ")
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = severityColor,
                )
                Text(
                    text = " \u2022 ${contradiction.severity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Text(
                text = contradiction.summary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (contradiction.evidenceA.isNotBlank()) {
                Text(
                    text = "$docATitle: \"${contradiction.evidenceA}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (contradiction.evidenceB.isNotBlank()) {
                Text(
                    text = "$docBTitle: \"${contradiction.evidenceB}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
