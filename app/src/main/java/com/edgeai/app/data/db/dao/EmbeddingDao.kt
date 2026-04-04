package com.edgeai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.edgeai.app.data.entity.DocumentEmbedding

@Dao
interface EmbeddingDao {

    @Insert
    suspend fun insertAll(embeddings: List<DocumentEmbedding>)

    @Query("SELECT * FROM document_embeddings")
    suspend fun getAll(): List<DocumentEmbedding>

    @Query("SELECT * FROM document_embeddings WHERE documentId = :documentId ORDER BY chunkIndex ASC")
    suspend fun getByDocument(documentId: Long): List<DocumentEmbedding>

    @Query("DELETE FROM document_embeddings WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: Long)
}
