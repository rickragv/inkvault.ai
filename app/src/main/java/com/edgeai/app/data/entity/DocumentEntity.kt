package com.edgeai.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceType: String,
    val sourceUri: String? = null,
    val fullText: String = "",
    val ocrText: String? = null,
    val gemmaText: String? = null,
    val language: String? = null,
    val pageCount: Int = 1,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
    val metadata: String? = null,
)
