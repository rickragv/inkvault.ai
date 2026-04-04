package com.edgeai.app.ui.documents

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.entity.ExtractedEntity
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.data.repository.EntityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val entityRepository: EntityRepository,
) : ViewModel() {

    var document by mutableStateOf<DocumentEntity?>(null)
        private set

    var entities by mutableStateOf<List<ExtractedEntity>>(emptyList())
        private set

    fun loadDocument(documentId: Long) {
        viewModelScope.launch {
            document = documentRepository.getById(documentId)
            entities = entityRepository.getByDocument(documentId)
        }
    }
}
