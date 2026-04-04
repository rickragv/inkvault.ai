package com.edgeai.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entity_relationships",
    foreignKeys = [
        ForeignKey(
            entity = ExtractedEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceEntityId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExtractedEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetEntityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceEntityId"),
        Index("targetEntityId"),
        Index("documentId"),
    ],
)
data class EntityRelationship(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceEntityId: Long,
    val targetEntityId: Long,
    val relationshipType: String,
    val documentId: Long,
    val evidence: String? = null,
    val confidence: Float = 0f,
)
