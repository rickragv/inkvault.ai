package com.edgeai.app.ml

import android.util.Log
import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.entity.Contradiction
import com.edgeai.app.data.entity.DocumentEntity
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContradictionAnalyzer @Inject constructor(
    private val inferenceEngine: GemmaInferenceEngine,
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "ContradictionAnalyzer"
    }

    /**
     * Compare two documents and find contradictions between them.
     */
    suspend fun compare(docA: DocumentEntity, docB: DocumentEntity): List<Contradiction> {
        val prompt = config.prompts.contradictionCheck
            .replace("{doc_a_title}", docA.title)
            .replace("{doc_b_title}", docB.title)
            .replace("{text_a}", docA.fullText.take(3000))
            .replace("{text_b}", docB.fullText.take(3000))

        val response = inferenceEngine.collectFullResponse(prompt)
        return parseContradictions(docA.id, docB.id, response)
    }

    /**
     * Analyze a new document against a set of existing similar documents.
     */
    suspend fun analyzeAgainstCorpus(
        newDoc: DocumentEntity,
        similarDocs: List<DocumentEntity>,
    ): List<Contradiction> {
        val allContradictions = mutableListOf<Contradiction>()
        val maxDocs = config.processingConfig.maxSimilarDocsForContradiction

        for (existingDoc in similarDocs.take(maxDocs)) {
            try {
                val contradictions = compare(newDoc, existingDoc)
                allContradictions.addAll(contradictions)
            } catch (e: Exception) {
                Log.e(TAG, "Error comparing doc ${newDoc.id} with ${existingDoc.id}", e)
            }
        }

        return allContradictions
    }

    private fun parseContradictions(
        docIdA: Long,
        docIdB: Long,
        response: String,
    ): List<Contradiction> {
        val contradictions = mutableListOf<Contradiction>()
        try {
            val jsonStr = extractJsonArray(response)
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                contradictions.add(
                    Contradiction(
                        documentIdA = docIdA,
                        documentIdB = docIdB,
                        contradictionType = obj.optString("type", "statement_contradiction"),
                        summary = obj.optString("summary", ""),
                        evidenceA = obj.optString("evidence_a", ""),
                        evidenceB = obj.optString("evidence_b", ""),
                        severity = obj.optString("severity", "medium"),
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse contradictions: ${e.message}")
        }
        return contradictions
    }

    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) {
            text.substring(start, end + 1)
        } else {
            "[]"
        }
    }
}
