package com.edgeai.app.ml

import android.util.Log
import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.db.dao.DocumentDao
import com.edgeai.app.data.db.dao.EmbeddingDao
import com.edgeai.app.data.entity.DocumentEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagPipeline @Inject constructor(
    private val documentDao: DocumentDao,
    private val embeddingDao: EmbeddingDao,
    private val embeddingEngine: EmbeddingEngine,
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "RagPipeline"
    }

    /**
     * Hybrid retrieval: FTS5 keyword search + TF-IDF vector similarity.
     * Returns the top K most relevant chunks with their source documents.
     */
    suspend fun retrieve(query: String, topK: Int? = null): List<RetrievedChunk> {
        val k = topK ?: config.ragConfig.mergedTopK
        val ftsWeight = config.ragConfig.ftsWeight
        val vectorWeight = config.ragConfig.vectorWeight

        val results = mutableMapOf<String, RetrievedChunk>()

        // FTS5 keyword search
        try {
            val ftsResults = documentDao.searchFts(query, config.ragConfig.ftsResultLimit)
            for ((rank, doc) in ftsResults.withIndex()) {
                val score = ftsWeight * (1f - rank.toFloat() / ftsResults.size)
                val key = "fts_${doc.id}"
                results[key] = RetrievedChunk(
                    documentId = doc.id,
                    documentTitle = doc.title,
                    chunkText = doc.fullText.take(config.ragConfig.chunkSize),
                    score = score,
                    source = "fts5",
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 search failed: ${e.message}")
        }

        // Vector similarity search
        try {
            val allEmbeddings = embeddingDao.getAll()
            if (allEmbeddings.isNotEmpty()) {
                val allChunks = allEmbeddings.map { it.chunkText }
                val idfWeights = embeddingEngine.buildIdfWeights(allChunks)
                val queryVector = embeddingEngine.computeTfIdfVector(query, idfWeights)

                val vectorResults = embeddingEngine.search(
                    queryVector, allEmbeddings, config.ragConfig.vectorResultLimit
                )

                for ((emb, similarity) in vectorResults) {
                    val doc = documentDao.getById(emb.documentId) ?: continue
                    val score = vectorWeight * similarity
                    val key = "vec_${emb.documentId}_${emb.chunkIndex}"

                    val existing = results.values.find { it.documentId == emb.documentId }
                    if (existing != null) {
                        // Merge scores for same document
                        val mergedKey = results.entries.find { it.value == existing }?.key ?: key
                        results[mergedKey] = existing.copy(score = existing.score + score)
                    } else {
                        results[key] = RetrievedChunk(
                            documentId = emb.documentId,
                            documentTitle = doc.title,
                            chunkText = emb.chunkText,
                            score = score,
                            source = "vector",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vector search failed: ${e.message}")
        }

        return results.values
            .sortedByDescending { it.score }
            .take(k)
    }

    /**
     * Build context string from retrieved chunks for the LLM.
     */
    fun buildContext(chunks: List<RetrievedChunk>): String {
        if (chunks.isEmpty()) return "No relevant documents found in the corpus."

        return buildString {
            appendLine("RELEVANT DOCUMENTS:")
            appendLine()
            for ((i, chunk) in chunks.withIndex()) {
                appendLine("[Doc ${i + 1}: ${chunk.documentTitle}] (ID: ${chunk.documentId})")
                appendLine(chunk.chunkText)
                appendLine()
            }
        }
    }

    /**
     * Summarize a document using Gemma 4.
     */
    suspend fun summarize(doc: DocumentEntity): String {
        val prompt = config.prompts.summarize
            .replace("{title}", doc.title)
            .replace("{text}", doc.fullText.take(4000))

        val inferenceEngine = GemmaInferenceEngineProvider.get()
        return inferenceEngine?.collectFullResponse(prompt) ?: "Model not available."
    }
}

data class RetrievedChunk(
    val documentId: Long,
    val documentTitle: String,
    val chunkText: String,
    val score: Float,
    val source: String,
)

/**
 * Simple singleton accessor for inference engine.
 * Avoids circular DI since RagPipeline needs inference for summarize.
 */
object GemmaInferenceEngineProvider {
    @Volatile
    private var instance: GemmaInferenceEngine? = null

    fun set(engine: GemmaInferenceEngine) {
        instance = engine
    }

    fun get(): GemmaInferenceEngine? = instance
}
