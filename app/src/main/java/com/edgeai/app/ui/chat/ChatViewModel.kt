package com.edgeai.app.ui.chat

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.entity.ChatMessage
import com.edgeai.app.data.entity.ChatSession
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.repository.ChatRepository
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.data.repository.EntityRepository
import com.edgeai.app.ml.DocumentProcessingPipeline
import com.edgeai.app.ml.GemmaInferenceEngine
import com.edgeai.app.ml.GemmaModelManager
import com.edgeai.app.ml.InferenceChunk
import com.edgeai.app.ml.InvestigationToolSet
import com.edgeai.app.ml.ModelState
import com.edgeai.app.ml.RagPipeline
import com.google.ai.edge.litertlm.Conversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val pendingUserMessage: String? = null,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val processingStatus: String? = null,
    val modelState: ModelState = ModelState.NotLoaded,
    val pendingImages: List<Bitmap> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val documentRepository: DocumentRepository,
    private val inferenceEngine: GemmaInferenceEngine,
    private val modelManager: GemmaModelManager,
    private val toolSet: InvestigationToolSet,
    private val ragPipeline: RagPipeline,
    private val pipeline: DocumentProcessingPipeline,
    private val entityRepository: EntityRepository,
    private val config: AppConfig,
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
        private const val MAX_TOOL_CALL_ROUNDS = 5
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeConversation: Conversation? = null
    private var generationJob: Job? = null
    private val sessionDocIds = mutableListOf<Long>()

    init {
        viewModelScope.launch {
            modelManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(modelState = state)
            }
        }
        viewModelScope.launch {
            chatRepository.getAllSessions().collect { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
            }
        }
        viewModelScope.launch {
            if (modelManager.state.value == ModelState.NotLoaded) {
                modelManager.loadModel()
            }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val sessionId = chatRepository.createSession()
            switchToSession(sessionId)
        }
    }

    fun switchToSession(sessionId: Long) {
        activeConversation?.close()
        activeConversation = null
        sessionDocIds.clear()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentSessionId = sessionId,
                streamingText = "",
                pendingImages = emptyList(),
                pendingUserMessage = null,
                processingStatus = null,
            )
            chatRepository.getMessages(sessionId).collect { messages ->
                _uiState.value = _uiState.value.copy(
                    messages = messages,
                    // Clear optimistic message once DB has it
                    pendingUserMessage = if (messages.any { it.content == _uiState.value.pendingUserMessage })
                        null else _uiState.value.pendingUserMessage,
                )
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                _uiState.value = _uiState.value.copy(
                    currentSessionId = null,
                    messages = emptyList(),
                )
                activeConversation?.close()
                activeConversation = null
            }
        }
    }

    fun addPendingImages(images: List<Bitmap>) {
        _uiState.value = _uiState.value.copy(
            pendingImages = _uiState.value.pendingImages + images,
        )
    }

    fun removePendingImage(index: Int) {
        val current = _uiState.value.pendingImages.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _uiState.value = _uiState.value.copy(pendingImages = current)
        }
    }

    fun sendMessage(text: String) {
        val sessionId = _uiState.value.currentSessionId ?: run {
            viewModelScope.launch {
                val newId = chatRepository.createSession()
                _uiState.value = _uiState.value.copy(currentSessionId = newId)
                // Start collecting messages for this session
                launch {
                    chatRepository.getMessages(newId).collect { messages ->
                        _uiState.value = _uiState.value.copy(
                            messages = messages,
                            pendingUserMessage = if (messages.any { it.content == _uiState.value.pendingUserMessage })
                                null else _uiState.value.pendingUserMessage,
                        )
                    }
                }
                sendMessageInternal(newId, text)
            }
            return
        }
        viewModelScope.launch { sendMessageInternal(sessionId, text) }
    }

    private suspend fun sendMessageInternal(sessionId: Long, text: String) {
        if (!inferenceEngine.isReady) {
            _uiState.value = _uiState.value.copy(error = "Model not loaded yet")
            return
        }

        // INSTANT: show user message + generating state
        _uiState.value = _uiState.value.copy(
            pendingUserMessage = text,
            isGenerating = true,
            streamingText = "",
            processingStatus = null,
            error = null,
        )

        // Save user message to DB
        chatRepository.addMessage(sessionId, "user", text)

        // Process pending images
        val pendingImages = _uiState.value.pendingImages
        if (pendingImages.isNotEmpty()) {
            activeConversation?.close()
            activeConversation = null

            for ((index, image) in pendingImages.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    processingStatus = "Reading document ${index + 1} of ${pendingImages.size}..."
                )
                try {
                    ingestImage(sessionId, image, "Document ${index + 1}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to ingest image $index", e)
                    chatRepository.addMessage(sessionId, "system", "Failed to process image ${index + 1}: ${e.message}")
                }
            }
            _uiState.value = _uiState.value.copy(pendingImages = emptyList())

            // Run NER/graph/timeline — blocks until done so entities are ready for chat
            runPipelineInBackground(sessionId)
        }

        // Generate AI response
        generationJob = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(processingStatus = null)

                // Always create fresh conversation (pipeline may have closed the previous one)
                activeConversation?.let { inferenceEngine.closeCurrentConversation() }
                activeConversation = inferenceEngine.createChatConversation(
                    config.prompts.systemChat
                )
                val conversation = activeConversation!!

                // RAG context
                val context = try {
                    val chunks = ragPipeline.retrieve(text)
                    if (chunks.isNotEmpty()) ragPipeline.buildContext(chunks) else ""
                } catch (_: Exception) { "" }

                val fullMessage = if (context.isNotBlank()) {
                    "$context\n\nUser question: $text"
                } else {
                    text
                }

                // Agentic loop: model generates → tool call? → execute → feed back → repeat
                var currentMessage = fullMessage
                var finalResponse = ""

                for (round in 0 until MAX_TOOL_CALL_ROUNDS) {
                    val responseBuilder = StringBuilder()

                    inferenceEngine.sendMessageStream(conversation, currentMessage)
                        .collect { chunk ->
                            when (chunk) {
                                is InferenceChunk.Token -> {
                                    responseBuilder.append(chunk.text)
                                    // Only show non-tool-call text in streaming
                                    val display = stripToolCalls(responseBuilder.toString())
                                    if (display.isNotBlank()) {
                                        _uiState.value = _uiState.value.copy(
                                            streamingText = display
                                        )
                                    }
                                }
                                is InferenceChunk.Done -> {}
                            }
                        }

                    val responseText = responseBuilder.toString()
                    val toolCall = extractToolCall(responseText)

                    if (toolCall != null) {
                        // Execute tool call silently — no intermediate text shown to user
                        Log.i(TAG, "Tool call round $round: ${toolCall.name}(${toolCall.args})")
                        _uiState.value = _uiState.value.copy(
                            processingStatus = "Looking up ${toolCall.name.replace("_", " ")}...",
                            streamingText = "",
                        )

                        val result = toolSet.execute(toolCall.name, toolCall.args)

                        // Feed result back to model — ask for clean answer only
                        currentMessage = "Tool result for ${toolCall.name}:\n$result\n\nNow answer the user's question in natural language. Do NOT mention tool calls, function names, or JSON. Just give a clear, direct answer citing [Doc: title] for sources."
                        _uiState.value = _uiState.value.copy(processingStatus = null)
                    } else {
                        // No tool call — this is the final response
                        finalResponse = responseText
                        break
                    }
                }

                // Save final response (strip any remaining tool call artifacts)
                val cleanResponse = stripToolCalls(finalResponse).trim()
                if (cleanResponse.isNotBlank()) {
                    chatRepository.addMessage(sessionId, "assistant", cleanResponse)
                }

                // Auto-title
                val messages = chatRepository.getMessagesOnce(sessionId)
                if (messages.count { it.role == "user" } <= 1) {
                    val title = text.take(50).let {
                        if (it.length < text.length) "$it..." else it
                    }
                    chatRepository.updateSessionTitle(sessionId, title)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    streamingText = "",
                    processingStatus = null,
                    pendingUserMessage = null,
                )
            }
        }
    }

    // --- Tool Call Parsing ---

    data class ToolCall(
        val name: String,
        val argsRaw: String,
        val args: Map<String, String>,
    )

    /**
     * Extract a tool call from model response.
     * Handles multiple Gemma 4 formats:
     * - <|tool_call>call:name(args)<tool_call|>
     * - <tool_call>call:name{args}</tool_call>
     * - {"tool": "name", "args": {...}}
     */
    private fun extractToolCall(response: String): ToolCall? {
        // Format 1: <|tool_call>call:name(args)<tool_call|>  (and variations)
        val gemmaRegex = Regex(
            """<\|?tool_call>call:(\w+)[\(\{](.*?)[\)\}]</?tool_call\|?>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val gemmaMatch = gemmaRegex.find(response)
        if (gemmaMatch != null) {
            val name = gemmaMatch.groupValues[1]
            val argsRaw = gemmaMatch.groupValues[2]
            return ToolCall(name, argsRaw, parseToolArgs(argsRaw))
        }

        // Format 2: JSON {"tool": "name", "args": {...}}
        val jsonRegex = Regex(
            """\{"tool":\s*"(\w+)",\s*"args":\s*(\{.*?\})\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        val jsonMatch = jsonRegex.find(response)
        if (jsonMatch != null) {
            val name = jsonMatch.groupValues[1]
            val argsJson = jsonMatch.groupValues[2]
            val args = mutableMapOf<String, String>()
            try {
                val obj = JSONObject(argsJson)
                for (key in obj.keys()) {
                    args[key] = obj.getString(key)
                }
            } catch (_: Exception) {}
            return ToolCall(name, argsJson, args)
        }

        return null
    }

    /**
     * Strip tool call XML from response text so user never sees raw tool calls.
     */
    private fun stripToolCalls(text: String): String {
        return text
            .replace(Regex("""<\|?tool_call>.*?</?tool_call\|?>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""\{"tool":\s*"\w+",\s*"args":\s*\{.*?\}\}""", RegexOption.DOT_MATCHES_ALL), "")
            .trim()
    }

    /**
     * Parse tool args from key=value or key:'value' format.
     */
    private fun parseToolArgs(argsStr: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        // Handle: entity_type='person', limit=5
        // Handle: entity_name="Arvind Sharma"
        // Handle: key:value
        val pairs = argsStr.split(",")
        for (pair in pairs) {
            val kv = pair.split(Regex("[=:]"), limit = 2)
            if (kv.size == 2) {
                args[kv[0].trim()] = kv[1].trim().trim('\'', '"')
            }
        }
        return args
    }

    // --- Document Ingestion ---

    /**
     * Fast ingest: OCR only (~3s per image). No Gemma vision call.
     */
    private suspend fun ingestImage(sessionId: Long, image: Bitmap, title: String) = withContext(Dispatchers.IO) {
        val docId = pipeline.ingestImageFast(image, title, object : DocumentProcessingPipeline.ProgressCallback {
            override fun onProgress(step: String) {
                _uiState.value = _uiState.value.copy(processingStatus = step)
            }
        })
        sessionDocIds.add(docId)
        chatRepository.addMessage(sessionId, "system", "Document ingested: \"$title\" (ID: $docId)")
    }

    /**
     * Run NER/relationships/timeline/embeddings in background.
     * Must close chat conversation first (LiteRT-LM single session).
     * Re-creates chat conversation after pipeline completes.
     */
    private suspend fun runPipelineInBackground(sessionId: Long) = withContext(Dispatchers.IO) {
        // Close chat conversation so pipeline can use the engine
        activeConversation?.let {
            inferenceEngine.closeCurrentConversation()
        }
        activeConversation = null

        for ((index, docId) in sessionDocIds.withIndex()) {
            _uiState.value = _uiState.value.copy(
                processingStatus = "Analyzing document ${index + 1}/${sessionDocIds.size}..."
            )
            try {
                pipeline.runFullPipeline(docId, object : DocumentProcessingPipeline.ProgressCallback {
                    override fun onProgress(step: String) {
                        _uiState.value = _uiState.value.copy(processingStatus = step)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline failed for doc $docId", e)
            }
        }

        val entityCount = sessionDocIds.sumOf {
            try { entityRepository.getByDocument(it).size } catch (_: Exception) { 0 }
        }
        chatRepository.addMessage(sessionId, "system",
            "Analysis complete: ${sessionDocIds.size} docs, $entityCount entities. Graph and timeline ready.")
        _uiState.value = _uiState.value.copy(processingStatus = null)
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        inferenceEngine.cancel()
        _uiState.value = _uiState.value.copy(
            isGenerating = false, streamingText = "", processingStatus = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        activeConversation?.close()
    }
}
