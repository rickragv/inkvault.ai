package com.edgeai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubble(
    content: String,
    isUser: Boolean,
    isStreaming: Boolean = false,
    streamingCursor: String = "",
    timestamp: Long? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = if (isUser) androidx.compose.ui.Alignment.End
            else androidx.compose.ui.Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isUser) 18.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 18.dp,
                        )
                    )
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    if (!isUser) {
                        Text(
                            text = "InkVault",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }

                    val displayText = if (isStreaming) "$content$streamingCursor" else content
                    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

                    if (isUser) {
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    } else {
                        // Render markdown for assistant messages
                        Text(
                            text = parseMarkdown(displayText, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                }
            }

            // Timestamp
            if (timestamp != null) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(
                    text = timeFormat.format(Date(timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Parse basic markdown into AnnotatedString.
 * Supports: **bold**, *italic*, * bullet points, [Doc: title] references
 */
private fun parseMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trimStart()

            // Bullet points: "* text" or "- text"
            if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                append("  \u2022 ")
                appendInlineMarkdown(trimmed.substring(2), defaultColor)
            } else {
                appendInlineMarkdown(line, defaultColor)
            }

            if (lineIndex < lines.size - 1) append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String, defaultColor: Color) {
    var i = 0
    while (i < text.length) {
        when {
            // **bold**
            i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // [Doc: title] reference
            text[i] == '[' && text.startsWith("[Doc:", i) -> {
                val end = text.indexOf(']', i)
                if (end > 0) {
                    withStyle(SpanStyle(
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF42A5F5),
                    )) {
                        append(text.substring(i, end + 1))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
