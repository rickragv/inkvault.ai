package com.edgeai.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun StreamingText(
    text: String,
    isStreaming: Boolean,
    cursorChar: String = "\u2588",
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (isStreaming) "$text$cursorChar" else text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
        overflow = TextOverflow.Visible,
    )
}
