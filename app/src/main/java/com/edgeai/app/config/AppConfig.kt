package com.edgeai.app.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfig @Inject constructor(context: Context) {

    private val root: JSONObject

    init {
        val json = context.assets.open("edgeai_config.json")
            .bufferedReader()
            .use { it.readText() }
        root = JSONObject(json)
    }

    val modelConfig: ModelConfig by lazy { ModelConfig(root.getJSONObject("model")) }
    val inferenceConfig: InferenceConfig by lazy { InferenceConfig(root.getJSONObject("inference")) }
    val prompts: PromptsConfig by lazy { PromptsConfig(root.getJSONObject("prompts")) }
    val toolsConfig: ToolsConfig by lazy { ToolsConfig(root.getJSONObject("tools")) }
    val ragConfig: RagConfig by lazy { RagConfig(root.getJSONObject("rag")) }
    val processingConfig: ProcessingConfig by lazy { ProcessingConfig(root.getJSONObject("processing")) }
    val preprocessingConfig: PreprocessingConfig by lazy { PreprocessingConfig(root.getJSONObject("preprocessing")) }
    val supportedLanguages: List<String> by lazy { root.getJSONArray("supported_languages").toStringList() }
    val uiConfig: UiConfig by lazy { UiConfig(root.getJSONObject("ui")) }
}

class ModelConfig(private val json: JSONObject) {
    val supportedExtensions: List<String> by lazy { json.getJSONArray("supported_extensions").toStringList() }
    val filePrefixes: List<String> by lazy { json.getJSONArray("file_prefixes").toStringList() }
    val searchDirectories: List<String> by lazy { json.getJSONArray("search_directories").toStringList() }
    val displayName: String by lazy { json.getString("display_name") }
    val maxTokens: Int by lazy { json.getInt("max_tokens") }
    val temperature: Float by lazy { json.getDouble("temperature").toFloat() }
    val topK: Int by lazy { json.getInt("top_k") }
    val topP: Float by lazy { json.getDouble("top_p").toFloat() }
}

class InferenceConfig(private val json: JSONObject) {
    val maxRetries: Int by lazy { json.getInt("max_retries") }
    val retryOnMalformedJson: Boolean by lazy { json.getBoolean("retry_on_malformed_json") }
    val streamBufferSize: Int by lazy { json.getInt("stream_buffer_size") }
}

class PromptsConfig(private val json: JSONObject) {
    val systemChat: String by lazy { json.getString("system_chat") }
    val ocrCrossref: String by lazy { json.getString("ocr_crossref") }
    val nerExtract: String by lazy { json.getString("ner_extract") }
    val relationshipExtract: String by lazy { json.getString("relationship_extract") }
    val contradictionCheck: String by lazy { json.getString("contradiction_check") }
    val dateExtract: String by lazy { json.getString("date_extract") }
    val combinedExtract: String by lazy { json.getString("combined_extract") }
    val summarize: String by lazy { json.getString("summarize") }
    val translate: String by lazy { json.getString("translate") }
}

class ToolsConfig(private val json: JSONObject) {
    val toolDefinitions: Map<String, ToolDefinition> by lazy {
        val map = mutableMapOf<String, ToolDefinition>()
        for (key in json.keys()) {
            map[key] = ToolDefinition(json.getJSONObject(key))
        }
        map
    }
}

class ToolDefinition(private val json: JSONObject) {
    val name: String by lazy { json.getString("name") }
    val description: String by lazy { json.getString("description") }
    val params: List<ToolParam> by lazy {
        val arr = json.getJSONArray("params")
        (0 until arr.length()).map { ToolParam(arr.getJSONObject(it)) }
    }
}

class ToolParam(private val json: JSONObject) {
    val name: String by lazy { json.getString("name") }
    val type: String by lazy { json.getString("type") }
    val description: String by lazy { json.getString("description") }
    val required: Boolean by lazy { json.optBoolean("required", true) }
    val default: String? by lazy { json.optString("default", null) }
}

class RagConfig(private val json: JSONObject) {
    val chunkSize: Int by lazy { json.getInt("chunk_size") }
    val chunkOverlap: Int by lazy { json.getInt("chunk_overlap") }
    val ftsResultLimit: Int by lazy { json.getInt("fts_result_limit") }
    val vectorResultLimit: Int by lazy { json.getInt("vector_result_limit") }
    val mergedTopK: Int by lazy { json.getInt("merged_top_k") }
    val ftsWeight: Float by lazy { json.getDouble("fts_weight").toFloat() }
    val vectorWeight: Float by lazy { json.getDouble("vector_weight").toFloat() }
}

class ProcessingConfig(private val json: JSONObject) {
    val maxPagesPerPdf: Int by lazy { json.getInt("max_pages_per_pdf") }
    val ocrConfidenceThreshold: Float by lazy { json.getDouble("ocr_confidence_threshold").toFloat() }
    val nerBatchSize: Int by lazy { json.getInt("ner_batch_size") }
    val contradictionCheckOnIngest: Boolean by lazy { json.getBoolean("contradiction_check_on_ingest") }
    val maxSimilarDocsForContradiction: Int by lazy { json.getInt("max_similar_docs_for_contradiction") }
}

class PreprocessingConfig(private val json: JSONObject) {
    val enabled: Boolean by lazy { json.optBoolean("enabled", true) }
    val grayscale: Boolean by lazy { json.getBoolean("grayscale") }
    val contrastBoost: Float by lazy { json.getDouble("contrast_boost").toFloat() }
    val sharpen: Boolean by lazy { json.getBoolean("sharpen") }
    val binarize: Boolean by lazy { json.getBoolean("binarize") }
    val binarizeThreshold: Int by lazy { json.getInt("binarize_threshold") }
}

class UiConfig(private val json: JSONObject) {
    val maxResponseDisplayLines: Int by lazy { json.getInt("max_response_display_lines") }
    val streamingCursorChar: String by lazy { json.getString("streaming_cursor_char") }
    val imageMaxDimension: Int by lazy { json.getInt("image_max_dimension") }
    val imageQuality: Int by lazy { json.getInt("image_quality") }
    val graphForceIterations: Int by lazy { json.getInt("graph_force_iterations") }
    val timelineDateFormat: String by lazy { json.getString("timeline_date_format") }
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { getString(it) }
