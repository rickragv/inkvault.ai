package com.edgeai.app.ui.chat

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeai.app.ml.ModelState
import com.edgeai.app.ui.components.ChatBubble
import com.edgeai.app.ui.components.ChatInputBar
import com.edgeai.app.ui.components.SessionDrawer
import com.edgeai.app.ui.components.ToolCallCard
import com.edgeai.app.ui.components.TypingIndicator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        val bitmaps = uris.mapNotNull { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) { null }
        }
        if (bitmaps.isNotEmpty()) {
            viewModel.addPendingImages(bitmaps)
        }
    }

    // Count total items for scrolling (messages + optimistic + streaming)
    val totalItems = uiState.messages.size +
        (if (uiState.pendingUserMessage != null) 1 else 0) +
        (if (uiState.isGenerating) 1 else 0)

    // Auto-scroll to bottom
    LaunchedEffect(totalItems, uiState.streamingText) {
        if (totalItems > 0) {
            listState.animateScrollToItem(maxOf(0, totalItems - 1))
        }
    }

    // Create first session if none exists
    LaunchedEffect(uiState.sessions) {
        if (uiState.currentSessionId == null && uiState.sessions.isNotEmpty()) {
            viewModel.switchToSession(uiState.sessions.first().id)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SessionDrawer(
                    sessions = uiState.sessions,
                    currentSessionId = uiState.currentSessionId,
                    onSessionClick = { id ->
                        viewModel.switchToSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewSession = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = { viewModel.deleteSession(it) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("InkVault.ai") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Sessions")
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.createNewSession() }) {
                            Text("New Chat")
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    // Pending image thumbnails — hide when generating
                    if (uiState.pendingImages.isNotEmpty() && !uiState.isGenerating) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            uiState.pendingImages.forEachIndexed { index, bitmap ->
                                Box {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Pending image $index",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .clickable { viewModel.removePendingImage(index) },
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }

                    ChatInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText.trim())
                                inputText = ""
                            }
                        },
                        onAttach = { imagePickerLauncher.launch("image/*") },
                        enabled = !uiState.isGenerating && uiState.modelState == ModelState.Ready,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when {
                    uiState.modelState is ModelState.Loading -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading Gemma 4...",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    uiState.modelState is ModelState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = (uiState.modelState as ModelState.Error).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    uiState.currentSessionId == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "InkVault.ai",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Secure Document Intelligence",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                text = "100% on-device. Zero data leaves your phone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            TextButton(
                                onClick = { viewModel.createNewSession() },
                                modifier = Modifier.padding(top = 24.dp),
                            ) {
                                Text("Start New Investigation")
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // DB messages (excludes tool_call and tool_result — those are internal)
                            items(uiState.messages) { message ->
                                when (message.role) {
                                    "user" -> ChatBubble(
                                        content = message.content,
                                        isUser = true,
                                        timestamp = message.createdAt,
                                    )
                                    "assistant" -> ChatBubble(
                                        content = message.content,
                                        isUser = false,
                                        timestamp = message.createdAt,
                                    )
                                    "system" -> ChatBubble(
                                        content = message.content,
                                        isUser = false,
                                        timestamp = message.createdAt,
                                    )
                                    "tool_call" -> ToolCallCard(
                                        toolName = message.toolName ?: "tool",
                                        args = message.content,
                                        result = null,
                                    )
                                    "tool_result" -> ToolCallCard(
                                        toolName = message.toolName ?: "tool",
                                        args = "",
                                        result = message.content,
                                    )
                                }
                            }

                            // Optimistic user message (shows instantly before DB confirms)
                            if (uiState.pendingUserMessage != null) {
                                item(key = "pending_user") {
                                    ChatBubble(
                                        content = uiState.pendingUserMessage!!,
                                        isUser = true,
                                    )
                                }
                            }

                            // Active generation area
                            if (uiState.isGenerating) {
                                item(key = "generating") {
                                    Column {
                                        // Processing status with spinner
                                        if (uiState.processingStatus != null) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    text = uiState.processingStatus!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(start = 10.dp),
                                                )
                                            }
                                        }

                                        // Streaming AI response
                                        if (uiState.streamingText.isNotBlank()) {
                                            ChatBubble(
                                                content = uiState.streamingText,
                                                isUser = false,
                                                isStreaming = true,
                                                streamingCursor = "\u2588",
                                            )
                                        }

                                        // Typing dots (when waiting for model, no streaming yet)
                                        if (uiState.streamingText.isBlank() && uiState.processingStatus == null) {
                                            TypingIndicator()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
