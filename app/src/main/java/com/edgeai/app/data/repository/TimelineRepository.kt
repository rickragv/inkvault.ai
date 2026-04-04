package com.edgeai.app.data.repository

import com.edgeai.app.data.db.dao.TimelineEventDao
import com.edgeai.app.data.entity.TimelineEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimelineRepository @Inject constructor(
    private val timelineEventDao: TimelineEventDao,
) {
    fun getAllChronological(): Flow<List<TimelineEvent>> =
        timelineEventDao.getAllChronological()

    suspend fun getByDocument(documentId: Long): List<TimelineEvent> =
        timelineEventDao.getByDocument(documentId)

    suspend fun getByDocuments(documentIds: List<Long>): List<TimelineEvent> =
        timelineEventDao.getByDocuments(documentIds)

    suspend fun getByDateRange(startMillis: Long, endMillis: Long): List<TimelineEvent> =
        timelineEventDao.getByDateRange(startMillis, endMillis)

    suspend fun insertAll(events: List<TimelineEvent>) =
        timelineEventDao.insertAll(events)
}
