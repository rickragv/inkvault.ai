package com.edgeai.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.entity.TimelineEvent
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.data.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val events: List<TimelineEvent> = emptyList(),
    val documentTitles: Map<Long, String> = emptyMap(),
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            timelineRepository.getAllChronological().collect { events ->
                val docIds = events.map { it.documentId }.distinct()
                val titles = mutableMapOf<Long, String>()
                for (id in docIds) {
                    documentRepository.getById(id)?.let { titles[id] = it.title }
                }
                _uiState.value = TimelineUiState(
                    events = events,
                    documentTitles = titles,
                )
            }
        }
    }
}
