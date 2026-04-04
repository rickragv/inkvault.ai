package com.edgeai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.edgeai.app.data.entity.TimelineEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEventDao {

    @Insert
    suspend fun insertAll(events: List<TimelineEvent>)

    @Query("SELECT * FROM timeline_events ORDER BY eventDateMillis ASC")
    fun getAllChronological(): Flow<List<TimelineEvent>>

    @Query("SELECT * FROM timeline_events WHERE eventDateMillis BETWEEN :startMillis AND :endMillis ORDER BY eventDateMillis ASC")
    suspend fun getByDateRange(startMillis: Long, endMillis: Long): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE documentId = :documentId ORDER BY eventDateMillis ASC")
    suspend fun getByDocument(documentId: Long): List<TimelineEvent>

    @Query("SELECT * FROM timeline_events WHERE documentId IN (:documentIds) ORDER BY eventDateMillis ASC")
    suspend fun getByDocuments(documentIds: List<Long>): List<TimelineEvent>

    @Query("DELETE FROM timeline_events WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: Long)
}
