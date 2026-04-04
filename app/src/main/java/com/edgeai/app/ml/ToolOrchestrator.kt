package com.edgeai.app.ml

import android.util.Log
import com.edgeai.app.config.AppConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid agentic orchestrator: LLM plans, App executes, LLM synthesizes.
 *
 * Call 1 (Planner): LLM decides which tools to call based on user question
 * App: Executes tools deterministically, gathers data
 * Call 2 (Synthesizer): LLM answers from gathered data
 *
 * This works reliably on small models (Gemma 4 E2B / 2B params) because:
 * - Planning only requires outputting plain text tool names (no structured tokens)
 * - Execution is deterministic (no parsing failures)
 * - Synthesis gets full data context (accurate answers)
 */
@Singleton
class ToolOrchestrator @Inject constructor(
    private val inferenceEngine: GemmaInferenceEngine,
    private val toolSet: InvestigationToolSet,
    private val ragPipeline: RagPipeline,
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "ToolOrchestrator"
        private const val MAX_TOOLS_PER_QUERY = 4

        private val PLANNER_PROMPT = """You are a tool planner. Given the user's question, decide which tools to call.

Available tools:
- search_documents query=<text> — Search documents by keyword
- search_document_by_title query=<text> — Find document by title or number
- get_document_stats — Get corpus overview
- extract_entities document_id=<id> — Get entities from a document
- query_entities entity_name=<name> — Find entity across all documents
- list_entities_by_type entity_type=<person|company|money|date|location|shell_entity|designation> — List all entities of a type
- find_cross_document_entities — Find entities appearing in multiple documents
- find_most_frequent_entities entity_type=<type> limit=<n> — Top entities by frequency
- find_contradictions doc_id_a=<id> doc_id_b=<id> — Compare two documents
- find_all_contradictions — Find all contradictions in corpus
- compare_events event_a=<text> event_b=<text> — Which event happened first
- query_financial_data — Get all financial/monetary data
- build_timeline document_ids=all — Build chronological timeline
- summarize_corpus — Summarize entire investigation
- deep_analysis question=<text> — Deep analytical reasoning
- scan_for_red_flags — Scan for suspicious patterns

Output ONLY tool calls, one per line, in this exact format:
tool_name param1=value1 param2=value2

Output between 1 and 4 tools. Most important tool first.
Do NOT output anything else — no explanations, no reasoning.

User question: """

        private val SYNTHESIZER_PROMPT = """You are a document analysis assistant. Answer the user's question using ONLY the data provided below.

Rules:
- Give a DIRECT answer. No preamble, no reasoning steps.
- NEVER say "based on the data" or "according to the analysis".
- Cite sources as [Doc: title].
- Use **bold** for important names and amounts.
- Use bullet points for lists.
- If the data shows suspicious patterns, state them clearly.
- If data is insufficient, say what's missing.

"""
    }

    data class OrchestratorResult(
        val toolsCalled: List<String>,
        val gatheredContext: String,
        val finalPrompt: String,
    )

    /**
     * Plan which tools to call, execute them, and build context for synthesis.
     */
    suspend fun planAndExecute(
        userQuestion: String,
        onProgress: (String) -> Unit,
    ): OrchestratorResult {
        // Step 1: LLM plans which tools to call
        onProgress("Planning analysis...")
        val toolPlan = planTools(userQuestion)
        Log.i(TAG, "Planner output: $toolPlan")

        // Step 2: Parse tool names and args from planner output
        val toolCalls = parseToolPlan(toolPlan)
        Log.i(TAG, "Parsed ${toolCalls.size} tool calls: ${toolCalls.map { it.first }}")

        // Step 3: Execute tools sequentially, gather results
        val results = mutableListOf<Pair<String, String>>()
        val calledTools = mutableListOf<String>()

        for ((toolName, args) in toolCalls.take(MAX_TOOLS_PER_QUERY)) {
            onProgress("Analyzing: ${toolName.replace("_", " ")}...")
            try {
                val result = toolSet.execute(toolName, args)
                results.add(toolName to result)
                calledTools.add(toolName)
                Log.i(TAG, "Tool $toolName returned ${result.length} chars")

                // Sufficiency check: if we have substantial data, may skip remaining
                val totalData = results.sumOf { it.second.length }
                if (totalData > 3000 && results.size >= 2) {
                    Log.i(TAG, "Sufficient data gathered (${totalData} chars), skipping remaining tools")
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tool $toolName failed: ${e.message}")
            }
        }

        // Step 4: If no tools returned data, fall back to RAG search
        if (results.isEmpty() || results.all { it.second.contains("\"error\"") }) {
            onProgress("Searching documents...")
            try {
                val ragChunks = ragPipeline.retrieve(userQuestion, topK = 8)
                val ragContext = ragPipeline.buildContext(ragChunks)
                results.add("search_documents" to ragContext)
                calledTools.add("search_documents (fallback)")
            } catch (e: Exception) {
                Log.e(TAG, "RAG fallback failed: ${e.message}")
            }
        }

        // Step 5: Assemble context for synthesizer
        val gatheredContext = buildString {
            appendLine("ANALYSIS DATA:")
            appendLine()
            for ((toolName, result) in results) {
                appendLine("--- ${toolName.replace("_", " ").uppercase()} ---")
                appendLine(result.take(4000))
                appendLine()
            }
        }

        val finalPrompt = buildString {
            append(SYNTHESIZER_PROMPT)
            appendLine(gatheredContext)
            appendLine("User question: $userQuestion")
        }

        return OrchestratorResult(
            toolsCalled = calledTools,
            gatheredContext = gatheredContext,
            finalPrompt = finalPrompt,
        )
    }

    /**
     * LLM Call 1: Ask model which tools to call.
     * Short, fast output — just tool names and params.
     */
    private suspend fun planTools(question: String): String {
        val prompt = PLANNER_PROMPT + question
        return inferenceEngine.collectFullResponse(prompt)
    }

    /**
     * Parse the planner's output into tool name + args pairs.
     * Expected format: "tool_name param1=value1 param2=value2"
     */
    private fun parseToolPlan(plannerOutput: String): List<Pair<String, Map<String, String>>> {
        val knownTools = setOf(
            "search_documents", "search_document_by_title", "get_document_stats",
            "extract_entities", "query_entities", "list_entities_by_type",
            "find_cross_document_entities", "find_most_frequent_entities",
            "find_contradictions", "find_all_contradictions", "compare_events",
            "query_financial_data", "build_timeline", "summarize_document",
            "summarize_corpus", "deep_analysis", "scan_for_red_flags",
        )

        val toolCalls = mutableListOf<Pair<String, Map<String, String>>>()

        for (line in plannerOutput.lines()) {
            val trimmed = line.trim()
                .removePrefix("-").removePrefix("*").removePrefix("1.").removePrefix("2.")
                .removePrefix("3.").removePrefix("4.")
                .trim()
                .removePrefix("`").removeSuffix("`")
                .trim()

            if (trimmed.isBlank()) continue

            // Find which known tool name this line starts with
            val matchedTool = knownTools.find { trimmed.startsWith(it) }
            if (matchedTool != null) {
                val argsStr = trimmed.removePrefix(matchedTool).trim()
                val args = mutableMapOf<String, String>()

                // Parse key=value pairs
                val argPattern = Regex("""(\w+)=["']?([^"'\s,]+)["']?""")
                for (match in argPattern.findAll(argsStr)) {
                    args[match.groupValues[1]] = match.groupValues[2]
                }

                toolCalls.add(matchedTool to args)
            }
        }

        // Fallback: if planner returned nothing usable, use search_documents
        if (toolCalls.isEmpty()) {
            Log.w(TAG, "Planner returned no parseable tools, falling back to search")
            toolCalls.add("search_documents" to mapOf("query" to plannerOutput.take(100)))
        }

        return toolCalls
    }
}
