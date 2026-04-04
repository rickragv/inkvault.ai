package com.edgeai.app.data.repository

import com.edgeai.app.data.db.dao.DocumentDao
import com.edgeai.app.data.db.dao.EmbeddingDao
import com.edgeai.app.data.db.dao.EntityDao
import com.edgeai.app.data.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val entityDao: EntityDao,
    private val embeddingDao: EmbeddingDao,
) {
    fun getAll(): Flow<List<DocumentEntity>> = documentDao.getAll()

    suspend fun getAllOnce(): List<DocumentEntity> = documentDao.getAllOnce()

    suspend fun getById(id: Long): DocumentEntity? = documentDao.getById(id)

    suspend fun insert(document: DocumentEntity): Long = documentDao.insert(document)

    suspend fun update(document: DocumentEntity) = documentDao.update(document)

    suspend fun delete(id: Long) = documentDao.delete(id)

    suspend fun searchFts(query: String, limit: Int = 10): List<DocumentEntity> =
        documentDao.searchFts(query, limit)

    suspend fun getUnprocessed(): List<DocumentEntity> = documentDao.getUnprocessed()

    suspend fun markProcessed(id: Long) = documentDao.updateProcessed(id, true, System.currentTimeMillis())

    suspend fun getStats(): DocumentStats {
        val docCount = documentDao.getCount()
        val entityCount = entityDao.getCount()
        val typeCounts = entityDao.getTypeCounts()
        return DocumentStats(
            documentCount = docCount,
            entityCount = entityCount,
            entityTypeCounts = typeCounts.associate { it.entityType to it.count },
        )
    }
}

data class DocumentStats(
    val documentCount: Int,
    val entityCount: Int,
    val entityTypeCounts: Map<String, Int>,
)
