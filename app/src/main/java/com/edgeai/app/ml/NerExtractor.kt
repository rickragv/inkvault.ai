package com.edgeai.app.ml

import android.util.Log
import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.entity.ExtractedEntity
import com.edgeai.app.data.entity.EntityRelationship
import com.edgeai.app.data.entity.TimelineEvent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NerExtractor @Inject constructor(
    private val inferenceEngine: GemmaInferenceEngine,
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "NerExtractor"
    }

    /**
     * Extract ALL in one Gemma call: entities + relationships + dates.
     * 3x faster than separate calls.
     */
    suspend fun extractAll(documentId: Long, text: String): ExtractionResult {
        val prompt = config.prompts.combinedExtract.replace("{text}", text)
        val response = inferenceEngine.collectFullResponse(prompt)
        return parseCombinedResponse(documentId, response)
    }

    /**
     * Extract named entities from document text using Gemma 4.
     */
    suspend fun extract(documentId: Long, text: String): List<ExtractedEntity> {
        val prompt = config.prompts.nerExtract.replace("{text}", text)
        val response = inferenceEngine.collectFullResponse(prompt)
        return parseEntities(documentId, response)
    }

    /**
     * Extract relationships between entities in a document.
     */
    suspend fun extractRelationships(
        documentId: Long,
        text: String,
        entities: List<ExtractedEntity>,
    ): List<EntityRelationship> {
        val entitiesJson = entities.joinToString(", ") {
            "${it.normalizedName} (${it.entityType})"
        }
        val prompt = config.prompts.relationshipExtract
            .replace("{entities}", entitiesJson)
            .replace("{text}", text)
        val response = inferenceEngine.collectFullResponse(prompt)
        return parseRelationships(documentId, response, entities)
    }

    /**
     * Extract dates and timeline events from document text.
     */
    suspend fun extractDates(documentId: Long, text: String): List<TimelineEvent> {
        val prompt = config.prompts.dateExtract.replace("{text}", text)
        val response = inferenceEngine.collectFullResponse(prompt)
        return parseTimelineEvents(documentId, response)
    }

    private fun parseEntities(documentId: Long, response: String): List<ExtractedEntity> {
        val entities = mutableListOf<ExtractedEntity>()
        try {
            val jsonStr = extractJsonArray(response)
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                entities.add(
                    ExtractedEntity(
                        documentId = documentId,
                        entityType = obj.optString("type", "unknown"),
                        mentionText = obj.optString("mention", ""),
                        normalizedName = obj.optString("normalized", "").lowercase(),
                        startPosition = obj.optInt("start", -1).takeIf { it >= 0 },
                        endPosition = obj.optInt("end", -1).takeIf { it >= 0 },
                        confidence = obj.optDouble("confidence", 0.8).toFloat(),
                        metadata = obj.optJSONObject("metadata")?.toString(),
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse NER response: ${e.message}")
        }
        return entities
    }

    private fun parseRelationships(
        documentId: Long,
        response: String,
        entities: List<ExtractedEntity>,
    ): List<EntityRelationship> {
        val relationships = mutableListOf<EntityRelationship>()
        try {
            val jsonStr = extractJsonArray(response)
            val arr = JSONArray(jsonStr)
            val entityMap = entities.associateBy { it.normalizedName }

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sourceName = obj.optString("source", "").lowercase()
                val targetName = obj.optString("target", "").lowercase()
                val sourceEntity = entityMap[sourceName]
                val targetEntity = entityMap[targetName]

                if (sourceEntity != null && targetEntity != null && sourceEntity.id != 0L && targetEntity.id != 0L) {
                    relationships.add(
                        EntityRelationship(
                            sourceEntityId = sourceEntity.id,
                            targetEntityId = targetEntity.id,
                            relationshipType = obj.optString("type", "mentioned_with"),
                            documentId = documentId,
                            evidence = obj.optString("evidence", null),
                            confidence = obj.optDouble("confidence", 0.7).toFloat(),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse relationships: ${e.message}")
        }
        return relationships
    }

    private fun parseTimelineEvents(documentId: Long, response: String): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        try {
            val jsonStr = extractJsonArray(response)
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val dateStr = obj.optString("date", "")
                val millis = try {
                    dateFormat.parse(dateStr)?.time ?: 0L
                } catch (_: Exception) { 0L }

                if (millis > 0) {
                    events.add(
                        TimelineEvent(
                            documentId = documentId,
                            eventDate = dateStr,
                            eventDateMillis = millis,
                            description = obj.optString("description", ""),
                            sourceSnippet = obj.optString("snippet", ""),
                            eventType = obj.optString("type", null),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse timeline events: ${e.message}")
        }
        return events
    }

    /**
     * Extract the first JSON array from a response that may contain surrounding text.
     */
    /**
     * Parse the combined JSON response containing entities, relationships, and dates.
     */
    private fun parseCombinedResponse(documentId: Long, response: String): ExtractionResult {
        val entities = mutableListOf<ExtractedEntity>()
        val pendingRels = mutableListOf<PendingRelationship>()
        val events = mutableListOf<TimelineEvent>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        try {
            val jsonStr = extractJsonObject(response)
            val root = JSONObject(jsonStr)

            // Parse entities
            val entitiesArr = root.optJSONArray("entities") ?: JSONArray()
            for (i in 0 until entitiesArr.length()) {
                val obj = entitiesArr.getJSONObject(i)
                entities.add(
                    ExtractedEntity(
                        documentId = documentId,
                        entityType = obj.optString("type", "unknown"),
                        mentionText = obj.optString("mention", ""),
                        normalizedName = obj.optString("normalized", "").lowercase(),
                        startPosition = null,
                        endPosition = null,
                        confidence = obj.optDouble("confidence", 0.8).toFloat(),
                        metadata = obj.optJSONObject("metadata")?.toString(),
                    )
                )
            }

            // Parse dates/timeline
            val datesArr = root.optJSONArray("dates") ?: JSONArray()
            for (i in 0 until datesArr.length()) {
                val obj = datesArr.getJSONObject(i)
                val dateStr = obj.optString("date", "")
                val millis = try {
                    dateFormat.parse(dateStr)?.time ?: 0L
                } catch (_: Exception) { 0L }

                if (millis > 0) {
                    events.add(
                        TimelineEvent(
                            documentId = documentId,
                            eventDate = dateStr,
                            eventDateMillis = millis,
                            description = obj.optString("description", ""),
                            sourceSnippet = obj.optString("snippet", ""),
                            eventType = obj.optString("type", null),
                        )
                    )
                }
            }

            // Parse relationships (uses entity normalized names)
            val relsArr = root.optJSONArray("relationships") ?: JSONArray()
            for (i in 0 until relsArr.length()) {
                val obj = relsArr.getJSONObject(i)
                pendingRels.add(
                    PendingRelationship(
                        sourceName = obj.optString("source", "").lowercase(),
                        targetName = obj.optString("target", "").lowercase(),
                        type = obj.optString("type", "mentioned_with"),
                        evidence = obj.optString("evidence", null),
                        confidence = obj.optDouble("confidence", 0.7).toFloat(),
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse combined response: ${e.message}")
        }

        return ExtractionResult(
            entities = entities,
            pendingRelationships = pendingRels,
            events = events,
        )
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

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            text.substring(start, end + 1)
        } else {
            "{}"
        }
    }
}

data class ExtractionResult(
    val entities: List<ExtractedEntity>,
    val pendingRelationships: List<PendingRelationship>,
    val events: List<TimelineEvent>,
)

data class PendingRelationship(
    val sourceName: String,
    val targetName: String,
    val type: String,
    val evidence: String?,
    val confidence: Float,
)
