package com.edgeai.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "extracted_entities",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("documentId"),
        Index("entityType"),
        Index("normalizedName"),
    ],
)
data class ExtractedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val entityType: String,
    val mentionText: String,
    val normalizedName: String,
    val startPosition: Int? = null,
    val endPosition: Int? = null,
    val confidence: Float = 0f,
    val metadata: String? = null,
)
