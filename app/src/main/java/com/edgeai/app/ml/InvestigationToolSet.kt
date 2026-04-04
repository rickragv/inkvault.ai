package com.edgeai.app.ml

import android.util.Log
import com.edgeai.app.data.db.dao.DocumentDao
import com.edgeai.app.data.db.dao.EntityDao
import com.edgeai.app.data.db.dao.EntityTypeCount
import com.edgeai.app.data.db.dao.TimelineEventDao
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.repository.ContradictionRepository
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.data.repository.EntityRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements all tools the LLM can call during the agentic chat loop.
 * Each method corresponds to a tool defined in edgeai_config.json.
 * Returns JSON strings that are fed back to the model as tool results.
 */
@Singleton
class InvestigationToolSet @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val entityRepository: EntityRepository,
    private val contradictionRepository: ContradictionRepository,
    private val contradictionAnalyzer: ContradictionAnalyzer,
    private val ragPipeline: RagPipeline,
    private val nerExtractor: NerExtractor,
    private val timelineEventDao: TimelineEventDao,
    private val documentDao: DocumentDao,
) {
    companion object {
        private const val TAG = "InvestigationToolSet"
    }

    /**
     * Dispatches a tool call to the appropriate handler.
     */
    suspend fun execute(toolName: String, args: Map<String, String>): String {
        return try {
            when (toolName) {
                "search_documents" -> searchDocuments(
                    query = args["query"] ?: "",
                    limit = args["limit"]?.toIntOrNull() ?: 5,
                )
                "extract_entities" -> extractEntities(
                    documentId = args["document_id"]?.toLongOrNull() ?: 0L,
                )
                "find_contradictions" -> findContradictions(
                    docIdA = args["doc_id_a"]?.toLongOrNull() ?: 0L,
                    docIdB = args["doc_id_b"]?.toLongOrNull() ?: 0L,
                )
                "build_timeline" -> buildTimeline(
                    documentIdsStr = args["document_ids"] ?: "all",
                )
                "query_entities" -> queryEntities(
                    entityName = args["entity_name"] ?: "",
                    entityType = args["entity_type"],
                )
                "summarize_document" -> summarizeDocument(
                    documentId = args["document_id"]?.toLongOrNull() ?: 0L,
                )
                "get_document_stats" -> getDocumentStats()
                "list_entities_by_type" -> listEntitiesByType(
                    entityType = args["entity_type"] ?: "all",
                    limit = args["limit"]?.toIntOrNull() ?: 50,
                )
                "find_cross_document_entities" -> findCrossDocumentEntities(
                    minDocuments = args["min_documents"]?.toIntOrNull() ?: 2,
                    entityType = args["entity_type"],
                )
                "find_most_frequent_entities" -> findMostFrequentEntities(
                    entityType = args["entity_type"],
                    limit = args["limit"]?.toIntOrNull() ?: 10,
                )
                "summarize_corpus" -> summarizeCorpus()
                "deep_analysis" -> deepAnalysis(
                    question = args["question"] ?: "",
                )
                "query_financial_data" -> queryFinancialData(
                    documentId = args["document_id"]?.toLongOrNull(),
                )
                "scan_for_red_flags" -> scanForRedFlags(
                    documentId = args["document_id"]?.toLongOrNull(),
                )
                "find_all_contradictions" -> findAllContradictions()
                "compare_events" -> compareEvents(
                    eventA = args["event_a"] ?: "",
                    eventB = args["event_b"] ?: "",
                )
                "search_document_by_title" -> searchDocumentByTitle(
                    query = args["query"] ?: "",
                )
                else -> JSONObject().put("error", "Unknown tool: $toolName").toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $toolName", e)
            JSONObject().put("error", e.message ?: "Tool execution failed").toString()
        }
    }

    private suspend fun searchDocuments(query: String, limit: Int): String {
        if (query.isBlank()) return JSONObject().put("error", "Empty search query").toString()

        val chunks = ragPipeline.retrieve(query, limit)
        val result = JSONArray()
        for (chunk in chunks) {
            result.put(JSONObject().apply {
                put("document_id", chunk.documentId)
                put("title", chunk.documentTitle)
                put("excerpt", chunk.chunkText.take(500))
                put("relevance_score", "%.2f".format(chunk.score))
                put("match_source", chunk.source)
            })
        }
        return JSONObject()
            .put("results", result)
            .put("total_matches", chunks.size)
            .toString()
    }

    private suspend fun extractEntities(documentId: Long): String {
        val doc = documentRepository.getById(documentId)
            ?: return JSONObject().put("error", "Document not found: $documentId").toString()

        // Return cached entities if already processed
        val existing = entityRepository.getByDocument(documentId)
        if (existing.isNotEmpty()) {
            val result = JSONArray()
            for (entity in existing) {
                result.put(JSONObject().apply {
                    put("type", entity.entityType)
                    put("name", entity.mentionText)
                    put("normalized", entity.normalizedName)
                    if (entity.metadata != null) put("metadata", JSONObject(entity.metadata))
                })
            }
            return JSONObject()
                .put("document", doc.title)
                .put("entities", result)
                .put("total", existing.size)
                .toString()
        }

        // Extract fresh
        val entities = nerExtractor.extract(documentId, doc.fullText)
        entityRepository.insertEntities(entities)

        val result = JSONArray()
        for (entity in entities) {
            result.put(JSONObject().apply {
                put("type", entity.entityType)
                put("name", entity.mentionText)
                put("normalized", entity.normalizedName)
            })
        }
        return JSONObject()
            .put("document", doc.title)
            .put("entities", result)
            .put("total", entities.size)
            .toString()
    }

    private suspend fun findContradictions(docIdA: Long, docIdB: Long): String {
        val docA = documentRepository.getById(docIdA)
            ?: return JSONObject().put("error", "Document A not found: $docIdA").toString()
        val docB = documentRepository.getById(docIdB)
            ?: return JSONObject().put("error", "Document B not found: $docIdB").toString()

        // Check cache first
        val cached = contradictionRepository.getBetweenDocuments(docIdA, docIdB)
        if (cached.isNotEmpty()) {
            val result = JSONArray()
            for (c in cached) {
                result.put(JSONObject().apply {
                    put("type", c.contradictionType)
                    put("summary", c.summary)
                    put("evidence_a", c.evidenceA)
                    put("evidence_b", c.evidenceB)
                    put("severity", c.severity)
                })
            }
            return JSONObject()
                .put("doc_a", docA.title)
                .put("doc_b", docB.title)
                .put("contradictions", result)
                .put("total", cached.size)
                .toString()
        }

        // Analyze fresh
        val contradictions = contradictionAnalyzer.compare(docA, docB)
        if (contradictions.isNotEmpty()) {
            contradictionRepository.insertAll(contradictions)
        }

        val result = JSONArray()
        for (c in contradictions) {
            result.put(JSONObject().apply {
                put("type", c.contradictionType)
                put("summary", c.summary)
                put("evidence_a", c.evidenceA)
                put("evidence_b", c.evidenceB)
                put("severity", c.severity)
            })
        }
        return JSONObject()
            .put("doc_a", docA.title)
            .put("doc_b", docB.title)
            .put("contradictions", result)
            .put("total", contradictions.size)
            .toString()
    }

    private suspend fun buildTimeline(documentIdsStr: String): String {
        val events = if (documentIdsStr.equals("all", ignoreCase = true)) {
            val allDocs = documentDao.getAllOnce()
            val allIds = allDocs.map { it.id }
            timelineEventDao.getByDocuments(allIds)
        } else {
            val ids = documentIdsStr.split(",").mapNotNull { it.trim().toLongOrNull() }
            if (ids.isEmpty()) {
                return JSONObject().put("error", "No valid document IDs provided").toString()
            }
            timelineEventDao.getByDocuments(ids)
        }

        val result = JSONArray()
        for (event in events) {
            val doc = documentDao.getById(event.documentId)
            result.put(JSONObject().apply {
                put("date", event.eventDate)
                put("description", event.description)
                put("source_document", doc?.title ?: "Unknown")
                put("document_id", event.documentId)
                put("type", event.eventType ?: "other")
                put("snippet", event.sourceSnippet.take(200))
            })
        }
        return JSONObject()
            .put("timeline", result)
            .put("total_events", events.size)
            .toString()
    }

    private suspend fun queryEntities(entityName: String, entityType: String?): String {
        if (entityName.isBlank()) {
            return JSONObject().put("error", "Empty entity name").toString()
        }

        val entities = entityRepository.searchByName(entityName)
        val filtered = if (!entityType.isNullOrBlank()) {
            entities.filter { it.entityType.equals(entityType, ignoreCase = true) }
        } else {
            entities
        }

        // Group by document
        val byDoc = filtered.groupBy { it.documentId }
        val result = JSONArray()
        for ((docId, docEntities) in byDoc) {
            val doc = documentRepository.getById(docId)
            result.put(JSONObject().apply {
                put("document_id", docId)
                put("document_title", doc?.title ?: "Unknown")
                put("mentions", docEntities.size)
                put("types", JSONArray(docEntities.map { it.entityType }.distinct()))
                put("contexts", JSONArray(docEntities.take(3).map { it.mentionText }))
            })
        }

        return JSONObject()
            .put("entity_name", entityName)
            .put("total_mentions", filtered.size)
            .put("documents_containing", byDoc.size)
            .put("appearances", result)
            .toString()
    }

    private suspend fun summarizeDocument(documentId: Long): String {
        val doc = documentRepository.getById(documentId)
            ?: return JSONObject().put("error", "Document not found: $documentId").toString()

        val summary = ragPipeline.summarize(doc)
        return JSONObject()
            .put("document_id", documentId)
            .put("title", doc.title)
            .put("summary", summary)
            .toString()
    }

    private suspend fun getDocumentStats(): String {
        val stats = documentRepository.getStats()
        val recentDocs = documentDao.getAllOnce().take(5)

        val typeCounts = JSONObject()
        for ((type, count) in stats.entityTypeCounts) {
            typeCounts.put(type, count)
        }

        val recent = JSONArray()
        for (doc in recentDocs) {
            recent.put(JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("source_type", doc.sourceType)
                put("processed", doc.isProcessed)
            })
        }

        return JSONObject()
            .put("total_documents", stats.documentCount)
            .put("total_entities", stats.entityCount)
            .put("entity_type_breakdown", typeCounts)
            .put("recent_documents", recent)
            .toString()
    }

    /**
     * List all entities of a specific type across the entire corpus.
     * Handles: "Who are all the people mentioned?" / "List all companies"
     */
    private suspend fun listEntitiesByType(entityType: String, limit: Int): String {
        val allEntities = entityRepository.getEntitiesInMultipleDocuments(minDocs = 1)

        val filtered = if (entityType.equals("all", ignoreCase = true)) {
            allEntities
        } else {
            allEntities.filter { it.entityType.equals(entityType, ignoreCase = true) }
        }

        // Group by normalized name to deduplicate, count documents per entity
        val grouped = filtered.groupBy { it.normalizedName }
        val ranked = grouped.entries
            .sortedByDescending { it.value.size }
            .take(limit)

        val result = JSONArray()
        for ((name, mentions) in ranked) {
            val docIds = mentions.map { it.documentId }.distinct()
            val docTitles = docIds.mapNotNull { id ->
                documentRepository.getById(id)?.title
            }
            result.put(JSONObject().apply {
                put("name", mentions.first().mentionText)
                put("normalized_name", name)
                put("type", mentions.first().entityType)
                put("total_mentions", mentions.size)
                put("document_count", docIds.size)
                put("documents", JSONArray(docTitles))
            })
        }

        return JSONObject()
            .put("entity_type", entityType)
            .put("entities", result)
            .put("total_unique", ranked.size)
            .toString()
    }

    /**
     * Find entities that appear across multiple documents — the cross-referencing tool.
     * Handles: "Which entities appear in more than one document?"
     *          "Show me every entity that appears in both land records and ministry contracts"
     */
    private suspend fun findCrossDocumentEntities(minDocuments: Int, entityType: String?): String {
        val crossDocEntities = entityRepository.getEntitiesInMultipleDocuments(minDocuments)

        val filtered = if (!entityType.isNullOrBlank() && !entityType.equals("all", ignoreCase = true)) {
            crossDocEntities.filter { it.entityType.equals(entityType, ignoreCase = true) }
        } else {
            crossDocEntities
        }

        // Group by normalized name
        val grouped = filtered.groupBy { it.normalizedName }
        val result = JSONArray()

        for ((name, mentions) in grouped.entries.sortedByDescending { it.value.map { m -> m.documentId }.distinct().size }) {
            val docIds = mentions.map { it.documentId }.distinct()
            val docDetails = JSONArray()
            for (docId in docIds) {
                val doc = documentRepository.getById(docId)
                val docMentions = mentions.filter { it.documentId == docId }
                docDetails.put(JSONObject().apply {
                    put("document_id", docId)
                    put("document_title", doc?.title ?: "Unknown")
                    put("mention_count", docMentions.size)
                    put("contexts", JSONArray(docMentions.take(2).map { it.mentionText }))
                })
            }

            result.put(JSONObject().apply {
                put("name", mentions.first().mentionText)
                put("normalized_name", name)
                put("type", mentions.first().entityType)
                put("appears_in_documents", docIds.size)
                put("total_mentions", mentions.size)
                put("document_details", docDetails)
            })
        }

        return JSONObject()
            .put("min_documents_threshold", minDocuments)
            .put("cross_document_entities", result)
            .put("total_cross_doc_entities", grouped.size)
            .toString()
    }

    /**
     * Find the most frequently mentioned entities across the corpus.
     * Handles: "Who are the three people who appear most frequently across all documents?"
     */
    private suspend fun findMostFrequentEntities(entityType: String?, limit: Int): String {
        val allEntities = entityRepository.getEntitiesInMultipleDocuments(minDocs = 1)

        val filtered = if (!entityType.isNullOrBlank() && !entityType.equals("all", ignoreCase = true)) {
            allEntities.filter { it.entityType.equals(entityType, ignoreCase = true) }
        } else {
            allEntities
        }

        val grouped = filtered.groupBy { it.normalizedName }
        val ranked = grouped.entries
            .sortedByDescending { it.value.size }
            .take(limit)

        val result = JSONArray()
        for ((name, mentions) in ranked) {
            val docIds = mentions.map { it.documentId }.distinct()
            val docTitles = docIds.mapNotNull { id ->
                documentRepository.getById(id)?.title
            }
            result.put(JSONObject().apply {
                put("rank", result.length() + 1)
                put("name", mentions.first().mentionText)
                put("type", mentions.first().entityType)
                put("total_mentions", mentions.size)
                put("document_count", docIds.size)
                put("found_in", JSONArray(docTitles))
            })
        }

        return JSONObject()
            .put("filter_type", entityType ?: "all")
            .put("top_entities", result)
            .put("total_ranked", ranked.size)
            .toString()
    }

    // ===== NEW TOOLS: Deep analysis, financial, red flags, corpus-wide =====

    /**
     * Summarize the ENTIRE investigation corpus in one go.
     * Handles: "Give me a one paragraph summary of the entire investigation"
     */
    private suspend fun summarizeCorpus(): String {
        val allDocs = documentDao.getAllOnce()
        if (allDocs.isEmpty()) {
            return JSONObject().put("error", "No documents in corpus").toString()
        }

        // Build a condensed view of the entire corpus for the model
        val corpusOverview = buildString {
            appendLine("CORPUS OVERVIEW (${allDocs.size} documents):")
            appendLine()
            for (doc in allDocs) {
                appendLine("--- ${doc.title} (ID: ${doc.id}, ${doc.sourceType}) ---")
                appendLine(doc.fullText.take(1500))
                appendLine()
            }
        }

        // Get key entities and contradictions
        val crossDocEntities = entityRepository.getEntitiesInMultipleDocuments(2)
        val entitySummary = crossDocEntities
            .groupBy { it.normalizedName }
            .entries
            .sortedByDescending { it.value.size }
            .take(10)
            .joinToString(", ") { "${it.key} (${it.value.first().entityType}, ${it.value.size} mentions)" }

        val contradictionCount = contradictionRepository.getUnresolvedCount()

        return JSONObject()
            .put("total_documents", allDocs.size)
            .put("document_titles", JSONArray(allDocs.map { it.title }))
            .put("corpus_text", corpusOverview.take(8000))
            .put("key_cross_document_entities", entitySummary)
            .put("unresolved_contradictions", contradictionCount)
            .put("instruction", "Using the above corpus overview, provide a comprehensive summary of the entire investigation. Identify the main narrative, key players, and suspicious patterns.")
            .toString()
    }

    /**
     * Deep analytical reasoning over the corpus with maximum context.
     * Handles: "Build a case: is there evidence of money laundering?"
     *          "Who benefits most from the arrangements?"
     *          "What is the chain of money flow?"
     *          "If you were a journalist, what would be your lead story?"
     *          "What questions should I ask in my next RTI?"
     */
    private suspend fun deepAnalysis(question: String): String {
        if (question.isBlank()) {
            return JSONObject().put("error", "Empty analysis question").toString()
        }

        // Pull maximum relevant context via RAG
        val chunks = ragPipeline.retrieve(question, topK = 10)
        val context = ragPipeline.buildContext(chunks)

        // Also pull all entities for context
        val allEntities = entityRepository.getEntitiesInMultipleDocuments(1)
        val entityGroups = allEntities
            .groupBy { it.entityType }
            .mapValues { (_, entities) ->
                entities.groupBy { it.normalizedName }.entries
                    .sortedByDescending { it.value.size }
                    .take(10)
                    .map { "${it.key} (${it.value.size}x)" }
            }

        val entityContext = buildString {
            for ((type, names) in entityGroups) {
                appendLine("$type: ${names.joinToString(", ")}")
            }
        }

        // Pull financial data
        val moneyEntities = allEntities.filter { it.entityType == "money" }
        val financialContext = if (moneyEntities.isNotEmpty()) {
            "Financial entities found: " + moneyEntities.joinToString("; ") {
                "${it.mentionText} [Doc: ${it.documentId}]"
            }
        } else ""

        // Pull contradictions
        val contradictions = mutableListOf<String>()
        // Get all contradictions from the repository
        val allDocs = documentDao.getAllOnce()
        for (doc in allDocs) {
            val docContradictions = contradictionRepository.getByDocument(doc.id)
            for (c in docContradictions) {
                contradictions.add("${c.contradictionType}: ${c.summary} (severity: ${c.severity})")
            }
        }

        return JSONObject()
            .put("analysis_question", question)
            .put("relevant_documents", context.take(6000))
            .put("entity_landscape", entityContext.take(2000))
            .put("financial_data", financialContext.take(2000))
            .put("known_contradictions", JSONArray(contradictions.distinct().take(10)))
            .put("instruction", "Using ALL the above context — documents, entities, financial data, and contradictions — provide a deep analytical answer to the question. Be specific, cite sources, and flag any patterns or red flags you identify.")
            .toString()
    }

    /**
     * Aggregate and analyze all financial/money data across the corpus.
     * Handles: "What is the total money involved across all documents?"
     *          "Summarize all financial transactions mentioned"
     *          "How much FDI did X receive?"
     *          "Was the stamp duty paid correctly?"
     *          "What was declared value vs market value?"
     */
    private suspend fun queryFinancialData(documentId: Long?): String {
        val allEntities = if (documentId != null) {
            entityRepository.getByDocument(documentId)
        } else {
            entityRepository.getEntitiesInMultipleDocuments(1)
        }

        val moneyEntities = allEntities.filter { it.entityType == "money" }

        if (moneyEntities.isEmpty()) {
            return JSONObject()
                .put("error", "No financial data found" + if (documentId != null) " in document $documentId" else " in corpus")
                .toString()
        }

        // Group by document
        val byDoc = moneyEntities.groupBy { it.documentId }
        val transactions = JSONArray()
        for ((docId, docMoney) in byDoc) {
            val doc = documentRepository.getById(docId)
            for (entity in docMoney) {
                transactions.put(JSONObject().apply {
                    put("amount_text", entity.mentionText)
                    put("document_id", docId)
                    put("document_title", doc?.title ?: "Unknown")
                    if (entity.metadata != null) {
                        try {
                            val meta = JSONObject(entity.metadata)
                            if (meta.has("amount")) put("parsed_amount", meta.get("amount"))
                            if (meta.has("currency")) put("currency", meta.getString("currency"))
                        } catch (_: Exception) {}
                    }
                })
            }
        }

        // Also get any entities related to money (companies receiving/paying)
        val relatedEntities = mutableSetOf<String>()
        for (entity in moneyEntities) {
            // Find entities mentioned near money in same document
            val docEntities = allEntities.filter {
                it.documentId == entity.documentId && it.entityType in listOf("person", "company", "shell_entity")
            }
            relatedEntities.addAll(docEntities.map { "${it.mentionText} (${it.entityType})" })
        }

        // Pull surrounding text for context
        val financialContext = JSONArray()
        for ((docId, docMoney) in byDoc) {
            val doc = documentRepository.getById(docId) ?: continue
            val text = doc.fullText
            for (entity in docMoney.take(5)) {
                val pos = entity.startPosition ?: text.indexOf(entity.mentionText)
                if (pos >= 0) {
                    val start = maxOf(0, pos - 150)
                    val end = minOf(text.length, pos + entity.mentionText.length + 150)
                    financialContext.put(JSONObject().apply {
                        put("amount", entity.mentionText)
                        put("context", text.substring(start, end).trim())
                        put("document", doc.title)
                    })
                }
            }
        }

        return JSONObject()
            .put("total_financial_mentions", moneyEntities.size)
            .put("documents_with_financial_data", byDoc.size)
            .put("transactions", transactions)
            .put("related_entities", JSONArray(relatedEntities.take(20).toList()))
            .put("financial_context", financialContext)
            .put("instruction", "Analyze the financial data above. Calculate totals where possible, identify money flows between entities, and flag any discrepancies or suspicious patterns.")
            .toString()
    }

    /**
     * Systematically scan documents for red flags and suspicious patterns.
     * Handles: "Are there any suspicious instructions in these documents?"
     *          "Does any document suggest illegal activity?"
     *          "Is there anything unusual about how this deal was structured?"
     *          "What information did the government refuse to provide?"
     */
    private suspend fun scanForRedFlags(documentId: Long?): String {
        val docs = if (documentId != null) {
            listOfNotNull(documentRepository.getById(documentId))
        } else {
            documentDao.getAllOnce()
        }

        if (docs.isEmpty()) {
            return JSONObject().put("error", "No documents to scan").toString()
        }

        // Gather all relevant data for red flag analysis
        val shellEntities = entityRepository.getEntitiesInMultipleDocuments(1)
            .filter { it.entityType == "shell_entity" }

        val contradictions = mutableListOf<JSONObject>()
        for (doc in docs) {
            val docContradictions = contradictionRepository.getByDocument(doc.id)
            for (c in docContradictions) {
                contradictions.add(JSONObject().apply {
                    put("type", c.contradictionType)
                    put("summary", c.summary)
                    put("severity", c.severity)
                })
            }
        }

        // Pull document text for pattern scanning
        val documentTexts = JSONArray()
        for (doc in docs) {
            documentTexts.put(JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("text", doc.fullText.take(3000))
            })
        }

        // Pull money flows
        val moneyEntities = entityRepository.getEntitiesInMultipleDocuments(1)
            .filter { it.entityType == "money" }
        val financialMentions = moneyEntities.map {
            "${it.mentionText} [Doc ${it.documentId}]"
        }

        return JSONObject()
            .put("documents_scanned", docs.size)
            .put("document_texts", documentTexts)
            .put("shell_entities_found", JSONArray(shellEntities.map {
                JSONObject().put("name", it.mentionText).put("document_id", it.documentId)
            }))
            .put("known_contradictions", JSONArray(contradictions))
            .put("financial_mentions", JSONArray(financialMentions.take(20)))
            .put("instruction", "Analyze the above documents for red flags. Look for: 1) Shell companies or intermediaries, 2) Unusual transaction patterns, 3) Discrepancies between stated and actual values, 4) Instructions to destroy/hide records, 5) Conflicts of interest, 6) Government information withholding patterns, 7) Back-dated or suspicious timing, 8) Round-tripping of funds. Report each finding with severity (high/medium/low) and cite the source document.")
            .toString()
    }

    /**
     * Find contradictions across the ENTIRE corpus, not just between two specific documents.
     * Handles: "Are there any date contradictions across these documents?"
     *          "Find all inconsistencies across documents"
     */
    private suspend fun findAllContradictions(): String {
        val allDocs = documentDao.getAllOnce()
        if (allDocs.size < 2) {
            return JSONObject().put("error", "Need at least 2 documents to find contradictions").toString()
        }

        // Get all cached contradictions
        val allContradictions = JSONArray()
        val checked = mutableSetOf<String>()

        for (doc in allDocs) {
            val docContradictions = contradictionRepository.getByDocument(doc.id)
            for (c in docContradictions) {
                val key = "${minOf(c.documentIdA, c.documentIdB)}_${maxOf(c.documentIdA, c.documentIdB)}_${c.contradictionType}"
                if (key !in checked) {
                    checked.add(key)
                    val docA = documentRepository.getById(c.documentIdA)
                    val docB = documentRepository.getById(c.documentIdB)
                    allContradictions.put(JSONObject().apply {
                        put("type", c.contradictionType)
                        put("summary", c.summary)
                        put("severity", c.severity)
                        put("document_a", docA?.title ?: "Doc ${c.documentIdA}")
                        put("document_b", docB?.title ?: "Doc ${c.documentIdB}")
                        put("evidence_a", c.evidenceA)
                        put("evidence_b", c.evidenceB)
                        put("resolved", c.isResolved)
                    })
                }
            }
        }

        // If no cached contradictions, run analysis on most overlapping doc pairs
        if (allContradictions.length() == 0 && allDocs.size >= 2) {
            // Analyze top document pairs based on shared entities
            val crossEntities = entityRepository.getEntitiesInMultipleDocuments(2)
            val docPairOverlap = mutableMapOf<Pair<Long, Long>, Int>()

            val byName = crossEntities.groupBy { it.normalizedName }
            for ((_, mentions) in byName) {
                val docIds = mentions.map { it.documentId }.distinct()
                for (i in docIds.indices) {
                    for (j in i + 1 until docIds.size) {
                        val pair = Pair(minOf(docIds[i], docIds[j]), maxOf(docIds[i], docIds[j]))
                        docPairOverlap[pair] = (docPairOverlap[pair] ?: 0) + 1
                    }
                }
            }

            // Analyze top 5 most overlapping pairs
            val topPairs = docPairOverlap.entries.sortedByDescending { it.value }.take(5)
            for ((pair, _) in topPairs) {
                val docA = documentRepository.getById(pair.first) ?: continue
                val docB = documentRepository.getById(pair.second) ?: continue
                try {
                    val newContradictions = contradictionAnalyzer.compare(docA, docB)
                    if (newContradictions.isNotEmpty()) {
                        contradictionRepository.insertAll(newContradictions)
                        for (c in newContradictions) {
                            allContradictions.put(JSONObject().apply {
                                put("type", c.contradictionType)
                                put("summary", c.summary)
                                put("severity", c.severity)
                                put("document_a", docA.title)
                                put("document_b", docB.title)
                                put("evidence_a", c.evidenceA)
                                put("evidence_b", c.evidenceB)
                            })
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Contradiction analysis failed for ${pair.first} vs ${pair.second}", e)
                }
            }
        }

        return JSONObject()
            .put("total_contradictions", allContradictions.length())
            .put("contradictions", allContradictions)
            .put("documents_checked", allDocs.size)
            .toString()
    }

    /**
     * Compare two specific events chronologically.
     * Handles: "Which happened first — the payment or the contract signing?"
     *          "When was the Pune land registered?"
     */
    private suspend fun compareEvents(eventA: String, eventB: String): String {
        val allDocs = documentDao.getAllOnce()
        val allIds = allDocs.map { it.id }
        val allEvents = timelineEventDao.getByDocuments(allIds)

        // Search for events matching each description
        val matchesA = allEvents.filter {
            it.description.contains(eventA, ignoreCase = true) ||
                it.sourceSnippet.contains(eventA, ignoreCase = true)
        }
        val matchesB = allEvents.filter {
            it.description.contains(eventB, ignoreCase = true) ||
                it.sourceSnippet.contains(eventB, ignoreCase = true)
        }

        val resultA = JSONArray()
        for (event in matchesA) {
            val doc = documentDao.getById(event.documentId)
            resultA.put(JSONObject().apply {
                put("date", event.eventDate)
                put("date_millis", event.eventDateMillis)
                put("description", event.description)
                put("source_document", doc?.title ?: "Unknown")
                put("snippet", event.sourceSnippet.take(200))
            })
        }

        val resultB = JSONArray()
        for (event in matchesB) {
            val doc = documentDao.getById(event.documentId)
            resultB.put(JSONObject().apply {
                put("date", event.eventDate)
                put("date_millis", event.eventDateMillis)
                put("description", event.description)
                put("source_document", doc?.title ?: "Unknown")
                put("snippet", event.sourceSnippet.take(200))
            })
        }

        // Determine which happened first
        val earliestA = matchesA.minByOrNull { it.eventDateMillis }
        val earliestB = matchesB.minByOrNull { it.eventDateMillis }
        val comparison = when {
            earliestA == null && earliestB == null -> "Neither event found in timeline"
            earliestA == null -> "Event A ('$eventA') not found in timeline; Event B occurred on ${earliestB?.eventDate}"
            earliestB == null -> "Event B ('$eventB') not found in timeline; Event A occurred on ${earliestA.eventDate}"
            earliestA.eventDateMillis < earliestB.eventDateMillis ->
                "'$eventA' happened FIRST on ${earliestA.eventDate}, then '$eventB' on ${earliestB.eventDate} (${(earliestB.eventDateMillis - earliestA.eventDateMillis) / 86400000} days later)"
            earliestA.eventDateMillis > earliestB.eventDateMillis ->
                "'$eventB' happened FIRST on ${earliestB.eventDate}, then '$eventA' on ${earliestA.eventDate} (${(earliestA.eventDateMillis - earliestB.eventDateMillis) / 86400000} days later)"
            else -> "Both events occurred on the same date: ${earliestA.eventDate}"
        }

        return JSONObject()
            .put("event_a_query", eventA)
            .put("event_b_query", eventB)
            .put("event_a_matches", resultA)
            .put("event_b_matches", resultB)
            .put("chronological_comparison", comparison)
            .toString()
    }

    /**
     * Search for a document by title or description instead of requiring an ID.
     * Handles: "What is Document 3 about?" / "Explain the RTI reply" / "The internal memo"
     */
    private suspend fun searchDocumentByTitle(query: String): String {
        if (query.isBlank()) {
            return JSONObject().put("error", "Empty search query").toString()
        }

        val allDocs = documentDao.getAllOnce()

        // Score each document by title match and content match
        val scored = allDocs.map { doc ->
            val titleScore = when {
                doc.title.equals(query, ignoreCase = true) -> 100
                doc.title.contains(query, ignoreCase = true) -> 80
                query.contains(doc.id.toString()) -> 70  // "Document 3" → matches doc.id == 3
                else -> 0
            }
            val contentScore = if (doc.fullText.contains(query, ignoreCase = true)) 20 else 0
            doc to (titleScore + contentScore)
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }

        // Also try to match by document number ("Document 3" → ID 3)
        val numberMatch = Regex("\\d+").find(query)?.value?.toLongOrNull()
        val byId = if (numberMatch != null) documentRepository.getById(numberMatch) else null

        val results = JSONArray()
        if (byId != null && scored.none { it.first.id == byId.id }) {
            results.put(JSONObject().apply {
                put("id", byId.id)
                put("title", byId.title)
                put("source_type", byId.sourceType)
                put("text_preview", byId.fullText.take(2000))
                put("full_text_length", byId.fullText.length)
                put("language", byId.language)
                put("match_type", "id_match")
            })
        }
        for ((doc, score) in scored.take(3)) {
            results.put(JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("source_type", doc.sourceType)
                put("text_preview", doc.fullText.take(2000))
                put("full_text_length", doc.fullText.length)
                put("language", doc.language)
                put("match_type", "title_content_match")
                put("relevance_score", score)
            })
        }

        return JSONObject()
            .put("query", query)
            .put("matches", results)
            .put("total_matches", results.length())
            .toString()
    }
}
