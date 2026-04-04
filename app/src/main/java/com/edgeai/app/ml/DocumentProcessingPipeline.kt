package com.edgeai.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.db.dao.EmbeddingDao
import com.edgeai.app.data.entity.DocumentEmbedding
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.repository.ContradictionRepository
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.data.repository.EntityRepository
import com.edgeai.app.data.repository.TimelineRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Full document processing pipeline shared between Chat and Documents screens.
 * Runs: OCR → Gemma cross-ref → NER → Relationships → Timeline → Embeddings → Contradictions
 */
@Singleton
class DocumentProcessingPipeline @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val entityRepository: EntityRepository,
    private val timelineRepository: TimelineRepository,
    private val contradictionRepository: ContradictionRepository,
    private val inferenceEngine: GemmaInferenceEngine,
    private val imagePreprocessor: ImagePreprocessor,
    private val ocrEngine: MlKitOcrEngine,
    private val nerExtractor: NerExtractor,
    private val contradictionAnalyzer: ContradictionAnalyzer,
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingDao: EmbeddingDao,
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "DocPipeline"
    }

    interface ProgressCallback {
        fun onProgress(step: String)
    }

    /**
     * Fast ingest: preprocess → OCR only → save immediately.
     * Skips Gemma multimodal cross-reference (~60-90s on CPU).
     * Returns the document ID. User can chat right away.
     */
    suspend fun ingestImageFast(image: Bitmap, title: String, callback: ProgressCallback? = null): Long {
        callback?.onProgress("Reading $title...")
        val processed = imagePreprocessor.process(image)
        val ocrResult = ocrEngine.recognizeText(processed)
        Log.i(TAG, "OCR completed: ${ocrResult.lines.size} lines in ${ocrResult.latencyMs}ms")

        val doc = DocumentEntity(
            title = title,
            sourceType = "camera",
            ocrText = ocrResult.fullText,
            fullText = ocrResult.fullText,
        )
        val docId = documentRepository.insert(doc)
        Log.i(TAG, "Document saved (fast): $title (ID: $docId) — ${ocrResult.lines.size} lines")
        return docId
    }

    /**
     * Full ingest: preprocess → OCR → Gemma multimodal cross-reference → save.
     * Slower but more accurate for multilingual/faded documents.
     * Returns the document ID.
     */
    suspend fun ingestImage(image: Bitmap, title: String, callback: ProgressCallback? = null): Long {
        callback?.onProgress("Running OCR on $title...")
        val processed = imagePreprocessor.process(image)
        val ocrResult = ocrEngine.recognizeText(processed)
        Log.i(TAG, "OCR completed: ${ocrResult.lines.size} lines in ${ocrResult.latencyMs}ms")

        callback?.onProgress("AI cross-referencing $title...")
        val gemmaText = if (inferenceEngine.isReady) {
            val prompt = config.prompts.ocrCrossref.replace("{ocr_text}", ocrResult.fullText)
            inferenceEngine.collectFullResponseWithImage(prompt, processed)
        } else {
            ocrResult.fullText
        }

        val doc = DocumentEntity(
            title = title,
            sourceType = "camera",
            ocrText = ocrResult.fullText,
            gemmaText = gemmaText,
            fullText = gemmaText.ifBlank { ocrResult.fullText },
        )
        val docId = documentRepository.insert(doc)
        Log.i(TAG, "Document saved: $title (ID: $docId)")
        return docId
    }

    /**
     * Ingest raw text as a document. Returns the document ID.
     */
    suspend fun ingestText(text: String, title: String): Long {
        val doc = DocumentEntity(
            title = title,
            sourceType = "text",
            fullText = text,
        )
        return documentRepository.insert(doc)
    }

    /**
     * Run the full analysis pipeline on an already-saved document:
     * NER → Relationships → Timeline → Embeddings → Contradiction check
     */
    suspend fun runFullPipeline(docId: Long, callback: ProgressCallback? = null) {
        val doc = documentRepository.getById(docId) ?: return
        if (doc.fullText.isBlank()) return

        // 1. Combined extraction: entities + relationships + dates in ONE Gemma call (3x faster)
        callback?.onProgress("Analyzing ${doc.title}...")
        var extractionResult: ExtractionResult? = null
        try {
            extractionResult = nerExtractor.extractAll(docId, doc.fullText)

            // Save entities
            if (extractionResult.entities.isNotEmpty()) {
                entityRepository.insertEntities(extractionResult.entities)
                Log.i(TAG, "Entities: ${extractionResult.entities.size} from ${doc.title}")
            }

            // Save timeline events
            if (extractionResult.events.isNotEmpty()) {
                timelineRepository.insertAll(extractionResult.events)
                Log.i(TAG, "Timeline: ${extractionResult.events.size} events from ${doc.title}")
            }

            // Save relationships (resolve entity names to IDs from DB)
            if (extractionResult.pendingRelationships.isNotEmpty()) {
                val savedEntities = entityRepository.getByDocument(docId)
                val entityMap = savedEntities.associateBy { it.normalizedName }
                val relationships = extractionResult.pendingRelationships.mapNotNull { rel ->
                    val source = entityMap[rel.sourceName]
                    val target = entityMap[rel.targetName]
                    if (source != null && target != null) {
                        com.edgeai.app.data.entity.EntityRelationship(
                            sourceEntityId = source.id,
                            targetEntityId = target.id,
                            relationshipType = rel.type,
                            documentId = docId,
                            evidence = rel.evidence,
                            confidence = rel.confidence,
                        )
                    } else null
                }
                if (relationships.isNotEmpty()) {
                    entityRepository.insertRelationships(relationships)
                    Log.i(TAG, "Relationships: ${relationships.size} from ${doc.title}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Combined extraction failed for doc $docId", e)
        }

        // 4. Embeddings for vector search
        callback?.onProgress("Indexing ${doc.title} for search...")
        try {
            val chunks = embeddingEngine.chunkText(doc.fullText)
            val allChunks = embeddingDao.getAll().map { it.chunkText } + chunks
            val idfWeights = embeddingEngine.buildIdfWeights(allChunks)
            val embeddings = chunks.mapIndexed { index, chunk ->
                val vector = embeddingEngine.computeTfIdfVector(chunk, idfWeights)
                DocumentEmbedding(
                    documentId = docId,
                    chunkIndex = index,
                    chunkText = chunk,
                    embedding = embeddingEngine.serializeVector(vector),
                )
            }
            embeddingDao.insertAll(embeddings)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed for doc $docId", e)
        }

        // 5. Contradiction check against similar docs
        if (config.processingConfig.contradictionCheckOnIngest) {
            callback?.onProgress("Checking for contradictions...")
            try {
                val similarDocs = documentRepository.searchFts(
                    doc.title, config.processingConfig.maxSimilarDocsForContradiction
                ).filter { it.id != docId }
                if (similarDocs.isNotEmpty()) {
                    val contradictions = contradictionAnalyzer.analyzeAgainstCorpus(doc, similarDocs)
                    if (contradictions.isNotEmpty()) {
                        contradictionRepository.insertAll(contradictions)
                        Log.i(TAG, "Contradictions: ${contradictions.size} found for ${doc.title}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Contradiction check failed for doc $docId", e)
            }
        }

        // Mark as processed
        documentRepository.markProcessed(docId)
        callback?.onProgress("${doc.title} fully processed")
        Log.i(TAG, "Pipeline complete for ${doc.title} (ID: $docId)")
    }
}
