package com.edgeai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.edgeai.app.data.entity.Contradiction
import kotlinx.coroutines.flow.Flow

@Dao
interface ContradictionDao {

    @Insert
    suspend fun insert(contradiction: Contradiction): Long

    @Insert
    suspend fun insertAll(contradictions: List<Contradiction>)

    @Query("SELECT * FROM contradictions ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Contradiction>>

    @Query("SELECT * FROM contradictions WHERE documentIdA = :documentId OR documentIdB = :documentId ORDER BY createdAt DESC")
    suspend fun getByDocument(documentId: Long): List<Contradiction>

    @Query("SELECT * FROM contradictions WHERE documentIdA = :docA AND documentIdB = :docB OR documentIdA = :docB AND documentIdB = :docA")
    suspend fun getBetweenDocuments(docA: Long, docB: Long): List<Contradiction>

    @Query("UPDATE contradictions SET isResolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Long)

    @Query("SELECT COUNT(*) FROM contradictions WHERE isResolved = 0")
    suspend fun getUnresolvedCount(): Int
}
