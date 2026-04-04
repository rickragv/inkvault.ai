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
import com.edgeai.app.ml.ToolOrchestrator
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
    private val orchestrator: ToolOrchestrator,
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

        // Generate AI response using hybrid orchestrator:
        // Call 1 (Planner): LLM decides which tools to call
        // App: Executes tools, gathers data
        // Call 2 (Synthesizer): LLM answers from gathered data (streaming)
        generationJob = viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(processingStatus = null)

                // Close any existing conversation for planner + tool execution
                activeConversation?.let { inferenceEngine.closeCurrentConversation() }
                activeConversation = null

                // Step 1 & 2: LLM plans tools → App executes tools → gathers data
                val orchestratorResult = withContext(Dispatchers.IO) {
                    orchestrator.planAndExecute(text) { status ->
                        _uiState.value = _uiState.value.copy(processingStatus = status)
                    }
                }

                Log.i(TAG, "Orchestrator called ${orchestratorResult.toolsCalled.size} tools: ${orchestratorResult.toolsCalled}")

                // Step 3: Create fresh conversation for synthesis
                _uiState.value = _uiState.value.copy(processingStatus = null)
                activeConversation = inferenceEngine.createChatConversation(
                    config.prompts.systemChat
                )
                val conversation = activeConversation!!

                // Step 4: Stream the final answer from model with full data context
                val responseBuilder = StringBuilder()
                inferenceEngine.sendMessageStream(conversation, orchestratorResult.finalPrompt)
                    .collect { chunk ->
                        when (chunk) {
                            is InferenceChunk.Token -> {
                                responseBuilder.append(chunk.text)
                                _uiState.value = _uiState.value.copy(
                                    streamingText = responseBuilder.toString()
                                )
                            }
                            is InferenceChunk.Done -> {}
                        }
                    }

                // Save final response
                val finalResponse = responseBuilder.toString().trim()
                if (finalResponse.isNotBlank()) {
                    chatRepository.addMessage(sessionId, "assistant", finalResponse)
                }

                // Auto-title first message
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

    /** All known tool names for regex matching */
    private val toolNames = listOf(
        "search_documents", "extract_entities", "find_contradictions",
        "build_timeline", "query_entities", "summarize_document",
        "get_document_stats", "list_entities_by_type", "find_cross_document_entities",
        "find_most_frequent_entities", "summarize_corpus", "deep_analysis",
        "query_financial_data", "scan_for_red_flags", "find_all_contradictions",
        "compare_events", "search_document_by_title",
    )

    private val toolNamePattern = toolNames.joinToString("|")

    /**
     * Extract a tool call from model response.
     * Handles ALL Gemma 4 output formats.
     */
    private fun extractToolCall(response: String): ToolCall? {
        // Format 1: <|tool_call>call:name(args)<tool_call|>
        val gemmaRegex = Regex(
            """<\|?tool_call>call:(\w+)[\(\{](.*?)[\)\}]</?tool_call\|?>""",
            RegexOption.DOT_MATCHES_ALL
        )
        gemmaRegex.find(response)?.let {
            val name = it.groupValues[1]
            val argsRaw = it.groupValues[2]
            return ToolCall(name, argsRaw, parseToolArgs(argsRaw))
        }

        // Format 2: {"name": "func", "parameters": {...}}  (Gemma 4 JSON schema format)
        val jsonSchemaRegex = Regex(
            """\{"name":\s*"($toolNamePattern)",\s*"parameters":\s*(\{.*?\})\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        jsonSchemaRegex.find(response)?.let {
            val name = it.groupValues[1]
            val argsJson = it.groupValues[2]
            return ToolCall(name, argsJson, parseJsonArgs(argsJson))
        }

        // Format 3: {"tool": "name", "args": {...}}
        val jsonToolRegex = Regex(
            """\{"tool":\s*"($toolNamePattern)",\s*"args":\s*(\{.*?\})\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        jsonToolRegex.find(response)?.let {
            val name = it.groupValues[1]
            val argsJson = it.groupValues[2]
            return ToolCall(name, argsJson, parseJsonArgs(argsJson))
        }

        // Format 4: Plain text "Tool Call: `name(args)`" or "`name(args)`"
        val plainTextRegex = Regex(
            """(?:Tool Call:?\s*)?`($toolNamePattern)\((.*?)\)`""",
            RegexOption.DOT_MATCHES_ALL
        )
        plainTextRegex.find(response)?.let {
            val name = it.groupValues[1]
            val argsRaw = it.groupValues[2]
            return ToolCall(name, argsRaw, parseToolArgs(argsRaw))
        }

        // Format 5: Bare function call in text: name(args) on its own line
        val bareFuncRegex = Regex(
            """(?:^|\n)\s*($toolNamePattern)\((.*?)\)\s*(?:\n|$)""",
            RegexOption.DOT_MATCHES_ALL
        )
        bareFuncRegex.find(response)?.let {
            val name = it.groupValues[1]
            val argsRaw = it.groupValues[2]
            return ToolCall(name, argsRaw, parseToolArgs(argsRaw))
        }

        return null
    }

    /**
     * Strip ALL tool call artifacts from response text.
     */
    private fun stripToolCalls(text: String): String {
        var result = text
        // XML tool calls
        result = result.replace(Regex("""<\|?tool_call>.*?</?tool_call\|?>""", RegexOption.DOT_MATCHES_ALL), "")
        // JSON tool calls
        result = result.replace(Regex("""\{"(?:name|tool)":\s*"(?:$toolNamePattern)".*?\}""", RegexOption.DOT_MATCHES_ALL), "")
        // Plain text tool calls
        result = result.replace(Regex("""(?:Tool Call:?\s*)?`(?:$toolNamePattern)\(.*?\)`""", RegexOption.DOT_MATCHES_ALL), "")
        // Bare function calls
        result = result.replace(Regex("""(?:^|\n)\s*(?:$toolNamePattern)\(.*?\)\s*(?:\n|$)""", RegexOption.DOT_MATCHES_ALL), "\n")
        // Strip reasoning prefixes
        result = result.replace(Regex("""^(?:I will|Let me|First,? I|I can|I need to|To (?:answer|provide|analyze)).*?\n""", RegexOption.MULTILINE), "")
        result = result.replace(Regex("""^(?:Here is my|Here are the|Based on the tool).*?:\s*\n""", RegexOption.MULTILINE), "")
        return result.trim()
    }

    /**
     * Parse tool args from key=value or key:'value' format.
     */
    private fun parseToolArgs(argsStr: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        val pairs = argsStr.split(",")
        for (pair in pairs) {
            val kv = pair.split(Regex("[=:]"), limit = 2)
            if (kv.size == 2) {
                args[kv[0].trim()] = kv[1].trim().trim('\'', '"', '<', '>', '|')
            }
        }
        return args
    }

    /**
     * Parse JSON object args.
     */
    private fun parseJsonArgs(json: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(json)
            for (key in obj.keys()) {
                args[key] = obj.getString(key)
            }
        } catch (_: Exception) {
            return parseToolArgs(json)
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
