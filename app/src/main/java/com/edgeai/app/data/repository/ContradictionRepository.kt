package com.edgeai.app.data.repository

import com.edgeai.app.data.db.dao.ContradictionDao
import com.edgeai.app.data.entity.Contradiction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContradictionRepository @Inject constructor(
    private val contradictionDao: ContradictionDao,
) {
    fun getAll(): Flow<List<Contradiction>> = contradictionDao.getAll()

    suspend fun getByDocument(documentId: Long): List<Contradiction> =
        contradictionDao.getByDocument(documentId)

    suspend fun getBetweenDocuments(docA: Long, docB: Long): List<Contradiction> =
        contradictionDao.getBetweenDocuments(docA, docB)

    suspend fun insert(contradiction: Contradiction): Long =
        contradictionDao.insert(contradiction)

    suspend fun insertAll(contradictions: List<Contradiction>) =
        contradictionDao.insertAll(contradictions)

    suspend fun markResolved(id: Long) = contradictionDao.markResolved(id)

    suspend fun getUnresolvedCount(): Int = contradictionDao.getUnresolvedCount()
}
