package com.edgeai.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_events",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("documentId"),
        Index("eventDateMillis"),
    ],
)
data class TimelineEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val eventDate: String,
    val eventDateMillis: Long,
    val description: String,
    val sourceSnippet: String,
    val eventType: String? = null,
)
