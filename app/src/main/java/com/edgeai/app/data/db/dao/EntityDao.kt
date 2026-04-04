package com.edgeai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.edgeai.app.data.entity.ExtractedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityDao {

    @Insert
    suspend fun insertAll(entities: List<ExtractedEntity>)

    @Query("SELECT * FROM extracted_entities WHERE documentId = :documentId ORDER BY startPosition ASC")
    suspend fun getByDocument(documentId: Long): List<ExtractedEntity>

    @Query("SELECT * FROM extracted_entities WHERE entityType = :entityType ORDER BY normalizedName")
    fun getByType(entityType: String): Flow<List<ExtractedEntity>>

    @Query("SELECT * FROM extracted_entities WHERE normalizedName = :name")
    suspend fun getByNormalizedName(name: String): List<ExtractedEntity>

    @Query("SELECT * FROM extracted_entities WHERE normalizedName LIKE '%' || :query || '%'")
    suspend fun searchByName(query: String): List<ExtractedEntity>

    /**
     * Find entities that appear in multiple documents — key for cross-referencing.
     */
    @Query("""
        SELECT * FROM extracted_entities
        WHERE normalizedName IN (
            SELECT normalizedName FROM extracted_entities
            GROUP BY normalizedName
            HAVING COUNT(DISTINCT documentId) >= :minDocs
        )
        ORDER BY normalizedName, documentId
    """)
    suspend fun getEntitiesInMultipleDocuments(minDocs: Int = 2): List<ExtractedEntity>

    @Query("SELECT * FROM extracted_entities ORDER BY normalizedName")
    fun getAllEntities(): Flow<List<ExtractedEntity>>

    @Query("SELECT COUNT(*) FROM extracted_entities")
    suspend fun getCount(): Int

    @Query("""
        SELECT entityType, COUNT(*) as count FROM extracted_entities
        GROUP BY entityType ORDER BY count DESC
    """)
    suspend fun getTypeCounts(): List<EntityTypeCount>

    @Query("DELETE FROM extracted_entities WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: Long)
}

data class EntityTypeCount(
    val entityType: String,
    val count: Int,
)
