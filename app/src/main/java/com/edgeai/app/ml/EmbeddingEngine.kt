package com.edgeai.app.ml

import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.entity.DocumentEmbedding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * TF-IDF based embedding engine for semantic search.
 * LiteRT-LM doesn't expose raw embedding vectors, so we use
 * a pure Kotlin TF-IDF implementation for vector similarity.
 */
@Singleton
class EmbeddingEngine @Inject constructor(
    private val config: AppConfig,
) {
    companion object {
        private val STOP_WORDS = setOf(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "need", "dare", "ought",
            "used", "to", "of", "in", "for", "on", "with", "at", "by", "from",
            "as", "into", "through", "during", "before", "after", "above", "below",
            "between", "out", "off", "over", "under", "again", "further", "then",
            "once", "here", "there", "when", "where", "why", "how", "all", "both",
            "each", "few", "more", "most", "other", "some", "such", "no", "nor",
            "not", "only", "own", "same", "so", "than", "too", "very", "and",
            "but", "or", "if", "while", "that", "this", "it", "he", "she", "they",
            "we", "you", "i", "me", "my", "your", "his", "her", "its", "our",
            "their", "what", "which", "who", "whom",
        )
    }

    /**
     * Split text into overlapping chunks for embedding.
     */
    fun chunkText(text: String): List<String> {
        val chunkSize = config.ragConfig.chunkSize
        val overlap = config.ragConfig.chunkOverlap

        if (text.length <= chunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + chunkSize, text.length)
            chunks.add(text.substring(start, end))
            start += chunkSize - overlap
        }
        return chunks
    }

    /**
     * Compute TF-IDF vector for a text chunk given the corpus vocabulary.
     */
    fun computeTfIdfVector(text: String, idfWeights: Map<String, Float>): FloatArray {
        val terms = tokenize(text)
        val tf = mutableMapOf<String, Float>()
        for (term in terms) {
            tf[term] = (tf[term] ?: 0f) + 1f
        }
        val maxTf = tf.values.maxOrNull() ?: 1f

        // Build vector in vocabulary order
        val vocab = idfWeights.keys.toList()
        val vector = FloatArray(vocab.size)
        for ((i, term) in vocab.withIndex()) {
            val normalizedTf = (tf[term] ?: 0f) / maxTf
            vector[i] = normalizedTf * (idfWeights[term] ?: 0f)
        }
        return vector
    }

    /**
     * Build IDF weights from a corpus of text chunks.
     */
    fun buildIdfWeights(allChunks: List<String>): Map<String, Float> {
        val docCount = allChunks.size.toFloat()
        val docFrequency = mutableMapOf<String, Int>()

        for (chunk in allChunks) {
            val uniqueTerms = tokenize(chunk).toSet()
            for (term in uniqueTerms) {
                docFrequency[term] = (docFrequency[term] ?: 0) + 1
            }
        }

        return docFrequency.mapValues { (_, df) ->
            kotlin.math.ln((docCount + 1) / (df + 1)) + 1f
        }
    }

    /**
     * Compute cosine similarity between two vectors.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    /**
     * Search embeddings for the most similar chunks to a query.
     */
    fun search(
        queryVector: FloatArray,
        allEmbeddings: List<DocumentEmbedding>,
        topK: Int = 5,
    ): List<Pair<DocumentEmbedding, Float>> {
        return allEmbeddings
            .map { emb ->
                val embVector = deserializeVector(emb.embedding)
                emb to cosineSimilarity(queryVector, embVector)
            }
            .sortedByDescending { it.second }
            .take(topK)
    }

    /**
     * Serialize a float array to bytes for Room storage.
     */
    fun serializeVector(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in vector) buffer.putFloat(f)
        return buffer.array()
    }

    /**
     * Deserialize bytes back to a float array.
     */
    fun deserializeVector(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
    }
}
