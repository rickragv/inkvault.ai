package com.edgeai.app.ui.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.edgeai.app.data.entity.EntityRelationship
import com.edgeai.app.data.entity.ExtractedEntity
import com.edgeai.app.data.repository.EntityRepository
import com.edgeai.app.ui.components.GraphEdge
import com.edgeai.app.ui.components.GraphNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GraphUiState(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val entityCount: Int = 0,
    val relationshipCount: Int = 0,
)

@HiltViewModel
class GraphViewModel @Inject constructor(
    private val entityRepository: EntityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                entityRepository.getAllEntities(),
                entityRepository.getAllRelationships(),
            ) { entities, relationships ->
                buildGraph(entities, relationships)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun buildGraph(
        entities: List<ExtractedEntity>,
        relationships: List<EntityRelationship>,
    ): GraphUiState {
        // Group entities by normalized name to deduplicate
        val uniqueEntities = entities
            .groupBy { it.normalizedName }
            .map { (name, group) ->
                GraphNode(
                    id = name,
                    label = group.first().mentionText,
                    type = group.first().entityType,
                )
            }
            .take(50) // Limit for performance

        val entityIds = uniqueEntities.map { it.id }.toSet()
        val entityIdMap = entities.associate { it.id to it.normalizedName }

        val graphEdges = relationships.mapNotNull { rel ->
            val sourceId = entityIdMap[rel.sourceEntityId]
            val targetId = entityIdMap[rel.targetEntityId]
            if (sourceId != null && targetId != null && sourceId in entityIds && targetId in entityIds) {
                GraphEdge(
                    sourceId = sourceId,
                    targetId = targetId,
                    label = rel.relationshipType,
                )
            } else null
        }.distinctBy { setOf(it.sourceId, it.targetId) }

        return GraphUiState(
            nodes = uniqueEntities,
            edges = graphEdges,
            entityCount = entities.size,
            relationshipCount = relationships.size,
        )
    }
}
