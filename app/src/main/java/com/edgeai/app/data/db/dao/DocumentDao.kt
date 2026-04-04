package com.edgeai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.edgeai.app.data.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getCount(): Int

    @Query("UPDATE documents SET isProcessed = :isProcessed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProcessed(id: Long, isProcessed: Boolean, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * FTS5 full-text search. Uses RawQuery because documents_fts5 is created
     * via raw SQL callback (Room can't verify it at compile time).
     */
    @RawQuery
    suspend fun searchFtsRaw(query: SupportSQLiteQuery): List<DocumentEntity>

    suspend fun searchFts(query: String, limit: Int = 10): List<DocumentEntity> {
        val sanitized = query.replace("\"", "").replace("'", "")
        if (sanitized.isBlank()) return emptyList()
        val sql = """
            SELECT d.* FROM documents d
            INNER JOIN documents_fts5 ON documents_fts5.rowid = d.id
            WHERE documents_fts5 MATCH ?
            LIMIT ?
        """
        return try {
            searchFtsRaw(SimpleSQLiteQuery(sql, arrayOf(sanitized, limit)))
        } catch (e: Exception) {
            // Fallback: simple LIKE search if FTS fails
            searchFtsRaw(SimpleSQLiteQuery(
                "SELECT * FROM documents WHERE fullText LIKE ? OR title LIKE ? LIMIT ?",
                arrayOf("%$sanitized%", "%$sanitized%", limit)
            ))
        }
    }

    @Query("SELECT * FROM documents WHERE isProcessed = 0 ORDER BY createdAt ASC")
    suspend fun getUnprocessed(): List<DocumentEntity>
}
