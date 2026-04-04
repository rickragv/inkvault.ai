package com.edgeai.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contradictions",
    indices = [
        Index("documentIdA"),
        Index("documentIdB"),
    ],
)
data class Contradiction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentIdA: Long,
    val documentIdB: Long,
    val contradictionType: String,
    val summary: String,
    val evidenceA: String,
    val evidenceB: String,
    val severity: String,
    val isResolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
