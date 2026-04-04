package com.edgeai.app.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.edgeai.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps LiteRT-LM for Gemma 4 inference.
 *
 * CRITICAL: LiteRT-LM only supports ONE conversation at a time.
 * All methods that create conversations must go through the sessionMutex.
 * The chat conversation (held by ChatViewModel) must be closed before
 * any background pipeline work can run.
 */
@Singleton
class GemmaInferenceEngine @Inject constructor(
    private val modelManager: GemmaModelManager,
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "GemmaInference"
    }

    /** Mutex to enforce single-session constraint of LiteRT-LM */
    val sessionMutex = Mutex()

    /**
     * The ONE active conversation. Every create/close goes through this.
     * Both the engine's own methods and ChatViewModel must use this.
     */
    @Volatile
    private var currentConversation: Conversation? = null

    val isReady: Boolean get() = modelManager.engine != null

    /**
     * Close the current conversation. MUST be called before creating a new one.
     */
    fun closeCurrentConversation() {
        try {
            currentConversation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing conversation: ${e.message}")
        }
        currentConversation = null
    }

    /**
     * Create a new conversation, closing any existing one first.
     * Caller MUST hold sessionMutex or guarantee exclusive access.
     */
    private fun createConversationInternal(systemPrompt: String? = null): Conversation {
        val engine = modelManager.engine
            ?: throw IllegalStateException("Engine not initialized. Load model first.")

        closeCurrentConversation()

        val convConfig = ConversationConfig(
            systemInstruction = systemPrompt?.let { Contents.of(Content.Text(it)) },
            samplerConfig = SamplerConfig(
                topK = config.modelConfig.topK,
                topP = config.modelConfig.topP.toDouble(),
                temperature = config.modelConfig.temperature.toDouble(),
            ),
        )

        val conv = engine.createConversation(convConfig)
        currentConversation = conv
        return conv
    }

    /**
     * Creates a chat conversation. Acquires mutex, closes any existing session.
     * Returns the conversation — caller uses sendMessageStream() for multi-turn.
     * Caller MUST call closeCurrentConversation() when done with the chat.
     */
    suspend fun createChatConversation(systemPrompt: String): Conversation = sessionMutex.withLock {
        createConversationInternal(systemPrompt)
    }

    /**
     * Run single-turn text inference. Acquires mutex, creates/closes conversation automatically.
     */
    fun generateStream(prompt: String): Flow<InferenceChunk> = flow {
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        Log.d(TAG, "Starting text inference, prompt length: ${prompt.length}")

        sessionMutex.lock()
        val conv = try {
            createConversationInternal()
        } catch (e: Exception) {
            sessionMutex.unlock()
            throw e
        }

        try {
            conv.sendMessageAsync(prompt)
                .collect { message ->
                    val text = message.toString()
                    if (text.isNotEmpty()) {
                        tokenCount++
                        emit(InferenceChunk.Token(text))
                    }
                }
        } finally {
            closeCurrentConversation()
            sessionMutex.unlock()
        }

        val elapsed = System.currentTimeMillis() - startTime
        emit(InferenceChunk.Done(
            metrics = InferenceMetrics(
                totalLatencyMs = elapsed,
                tokenCount = tokenCount,
                tokensPerSecond = if (elapsed > 0) tokenCount * 1000f / elapsed else 0f,
                peakMemoryMb = getUsedMemoryMb(),
                modelInfo = modelManager.getModelInfo(),
            ),
        ))
    }.flowOn(Dispatchers.Default)

    /**
     * Run single-turn multimodal inference. Acquires mutex, creates/closes conversation automatically.
     */
    fun generateStreamWithImage(prompt: String, image: Bitmap): Flow<InferenceChunk> = flow {
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        Log.d(TAG, "Starting multimodal inference, image: ${image.width}x${image.height}")

        val imageBytes = bitmapToPngBytes(image)

        sessionMutex.lock()
        val conv = try {
            createConversationInternal()
        } catch (e: Exception) {
            sessionMutex.unlock()
            throw e
        }

        try {
            val contents = Contents.of(
                Content.ImageBytes(imageBytes),
                Content.Text(prompt),
            )
            conv.sendMessageAsync(contents)
                .collect { message ->
                    val text = message.toString()
                    if (text.isNotEmpty()) {
                        tokenCount++
                        emit(InferenceChunk.Token(text))
                    }
                }
        } finally {
            closeCurrentConversation()
            sessionMutex.unlock()
        }

        val elapsed = System.currentTimeMillis() - startTime
        emit(InferenceChunk.Done(
            metrics = InferenceMetrics(
                totalLatencyMs = elapsed,
                tokenCount = tokenCount,
                tokensPerSecond = if (elapsed > 0) tokenCount * 1000f / elapsed else 0f,
                peakMemoryMb = getUsedMemoryMb(),
                modelInfo = modelManager.getModelInfo(),
            ),
        ))
    }.flowOn(Dispatchers.Default)

    /**
     * Collect full text response. Acquires mutex automatically.
     * Used by NER/contradiction analysis — safe to call from background.
     */
    suspend fun collectFullResponse(prompt: String): String {
        val sb = StringBuilder()
        generateStream(prompt).collect { chunk ->
            if (chunk is InferenceChunk.Token) sb.append(chunk.text)
        }
        return sb.toString()
    }

    /**
     * Collect full multimodal response. Acquires mutex automatically.
     */
    suspend fun collectFullResponseWithImage(prompt: String, image: Bitmap): String {
        val sb = StringBuilder()
        generateStreamWithImage(prompt, image).collect { chunk ->
            if (chunk is InferenceChunk.Token) sb.append(chunk.text)
        }
        return sb.toString()
    }

    /**
     * Send a message to an EXISTING conversation (for multi-turn chat).
     * Caller must already hold the conversation from createChatConversation().
     * Does NOT acquire mutex — assumes caller manages the session lifecycle.
     */
    fun sendMessageStream(conversation: Conversation, message: String): Flow<InferenceChunk> = flow {
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        try {
            conversation.sendMessageAsync(message)
                .collect { msg ->
                    val text = msg.toString()
                    if (text.isNotEmpty()) {
                        tokenCount++
                        emit(InferenceChunk.Token(text))
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessageStream failed: ${e.message}")
            throw e
        }

        val elapsed = System.currentTimeMillis() - startTime
        emit(InferenceChunk.Done(
            metrics = InferenceMetrics(
                totalLatencyMs = elapsed,
                tokenCount = tokenCount,
                tokensPerSecond = if (elapsed > 0) tokenCount * 1000f / elapsed else 0f,
                peakMemoryMb = getUsedMemoryMb(),
                modelInfo = modelManager.getModelInfo(),
            ),
        ))
    }.flowOn(Dispatchers.Default)

    fun cancel() {
        currentConversation?.cancelProcess()
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, config.uiConfig.imageQuality, stream)
        return stream.toByteArray()
    }

    private fun getUsedMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576
    }
}

sealed interface InferenceChunk {
    data class Token(val text: String) : InferenceChunk
    data class Done(val metrics: InferenceMetrics) : InferenceChunk
}

data class InferenceMetrics(
    val totalLatencyMs: Long,
    val tokenCount: Int,
    val tokensPerSecond: Float,
    val peakMemoryMb: Long = 0,
    val modelInfo: ModelInfo? = null,
)
