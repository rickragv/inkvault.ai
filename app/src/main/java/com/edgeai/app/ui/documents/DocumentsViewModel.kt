package com.edgeai.app.ui.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.ml.DocumentProcessingPipeline
import com.edgeai.app.ml.ImagePreprocessor
import com.edgeai.app.ml.MlKitOcrEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentsUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val isIngesting: Boolean = false,
    val ingestProgress: String = "",
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val documentRepository: DocumentRepository,
    private val pipeline: DocumentProcessingPipeline,
    private val imagePreprocessor: ImagePreprocessor,
    private val ocrEngine: MlKitOcrEngine,
    private val config: AppConfig,
) : ViewModel() {

    companion object {
        private const val TAG = "DocumentsViewModel"
    }

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            documentRepository.getAll().collect { docs ->
                _uiState.value = _uiState.value.copy(documents = docs)
            }
        }
    }

    fun ingestImages(images: List<Bitmap>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIngesting = true)
            val docIds = mutableListOf<Long>()

            for ((i, image) in images.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    ingestProgress = "OCR + AI on image ${i + 1}/${images.size}..."
                )
                try {
                    val docId = pipeline.ingestImage(image, "Document ${System.currentTimeMillis()}", progressCallback)
                    docIds.add(docId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to ingest image $i", e)
                }
            }

            // Run full pipeline on all docs
            for ((i, docId) in docIds.withIndex()) {
                _uiState.value = _uiState.value.copy(
                    ingestProgress = "Analyzing document ${i + 1}/${docIds.size}..."
                )
                try {
                    pipeline.runFullPipeline(docId, progressCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Pipeline failed for doc $docId", e)
                }
            }

            _uiState.value = _uiState.value.copy(isIngesting = false, ingestProgress = "")
        }
    }

    private val progressCallback = object : DocumentProcessingPipeline.ProgressCallback {
        override fun onProgress(step: String) {
            _uiState.value = _uiState.value.copy(ingestProgress = step)
        }
    }

    fun ingestPdf(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIngesting = true)
            try {
                val fd = appContext.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                val renderer = PdfRenderer(fd)
                val allText = StringBuilder()
                val maxPages = minOf(renderer.pageCount, config.processingConfig.maxPagesPerPdf)

                for (pageIndex in 0 until maxPages) {
                    _uiState.value = _uiState.value.copy(
                        ingestProgress = "Processing PDF page ${pageIndex + 1}/$maxPages..."
                    )
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val processed = imagePreprocessor.process(bitmap)
                    val ocrResult = ocrEngine.recognizeText(processed)
                    allText.appendLine(ocrResult.fullText)
                }

                renderer.close()
                fd.close()

                val doc = DocumentEntity(
                    title = "PDF Document",
                    sourceType = "pdf",
                    sourceUri = uri.toString(),
                    fullText = allText.toString(),
                    ocrText = allText.toString(),
                    pageCount = maxPages,
                )
                val docId = documentRepository.insert(doc)
                pipeline.runFullPipeline(docId, progressCallback)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest PDF", e)
            }
            _uiState.value = _uiState.value.copy(isIngesting = false, ingestProgress = "")
        }
    }

    fun ingestText(text: String, title: String = "Text Document") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIngesting = true, ingestProgress = "Saving text...")
            try {
                val docId = pipeline.ingestText(text, title)
                pipeline.runFullPipeline(docId, progressCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest text", e)
            }
            _uiState.value = _uiState.value.copy(isIngesting = false, ingestProgress = "")
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch { documentRepository.delete(id) }
    }
}
