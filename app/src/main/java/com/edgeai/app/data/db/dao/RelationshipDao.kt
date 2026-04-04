package com.edgeai.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.edgeai.app.data.entity.EntityRelationship
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {

    @Insert
    suspend fun insertAll(relationships: List<EntityRelationship>)

    @Query("SELECT * FROM entity_relationships WHERE sourceEntityId = :entityId OR targetEntityId = :entityId")
    suspend fun getByEntity(entityId: Long): List<EntityRelationship>

    @Query("SELECT * FROM entity_relationships WHERE documentId = :documentId")
    suspend fun getByDocument(documentId: Long): List<EntityRelationship>

    @Query("SELECT * FROM entity_relationships")
    fun getAll(): Flow<List<EntityRelationship>>

    @Query("DELETE FROM entity_relationships WHERE documentId = :documentId")
    suspend fun deleteByDocument(documentId: Long)
}
