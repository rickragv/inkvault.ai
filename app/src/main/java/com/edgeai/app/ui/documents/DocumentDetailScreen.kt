package com.edgeai.app.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.entity.ExtractedEntity
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.data.repository.EntityRepository
import com.edgeai.app.ui.components.EntityChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentDetailScreen(
    documentId: Long,
    onBack: () -> Unit,
    viewModel: DocumentDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(documentId) { viewModel.loadDocument(documentId) }

    val document = viewModel.document
    val entities = viewModel.entities

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.title ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            document?.let { doc ->
                // Metadata
                Text(
                    text = "Source: ${doc.sourceType} | Pages: ${doc.pageCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                doc.language?.let {
                    Text(
                        text = "Language: $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Entities
                if (entities.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = "Entities (${entities.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        entities.take(30).forEach { entity ->
                            EntityChip(
                                name = entity.mentionText,
                                entityType = entity.entityType,
                            )
                        }
                    }
                }

                // Full text
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "Full Text",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = doc.fullText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
