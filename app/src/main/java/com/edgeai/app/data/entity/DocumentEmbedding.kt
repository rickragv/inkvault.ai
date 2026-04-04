package com.edgeai.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_embeddings",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("documentId")],
)
data class DocumentEmbedding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val chunkText: String,
    val embedding: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentEmbedding) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
