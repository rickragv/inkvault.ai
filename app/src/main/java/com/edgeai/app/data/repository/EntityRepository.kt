package com.edgeai.app.data.repository

import com.edgeai.app.data.db.dao.EntityDao
import com.edgeai.app.data.db.dao.RelationshipDao
import com.edgeai.app.data.entity.EntityRelationship
import com.edgeai.app.data.entity.ExtractedEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityRepository @Inject constructor(
    private val entityDao: EntityDao,
    private val relationshipDao: RelationshipDao,
) {
    fun getAllEntities(): Flow<List<ExtractedEntity>> = entityDao.getAllEntities()

    fun getAllRelationships(): Flow<List<EntityRelationship>> = relationshipDao.getAll()

    suspend fun getByDocument(documentId: Long): List<ExtractedEntity> =
        entityDao.getByDocument(documentId)

    suspend fun searchByName(query: String): List<ExtractedEntity> =
        entityDao.searchByName(query.lowercase())

    suspend fun getByNormalizedName(name: String): List<ExtractedEntity> =
        entityDao.getByNormalizedName(name.lowercase())

    suspend fun getEntitiesInMultipleDocuments(minDocs: Int = 2): List<ExtractedEntity> =
        entityDao.getEntitiesInMultipleDocuments(minDocs)

    suspend fun insertEntities(entities: List<ExtractedEntity>) =
        entityDao.insertAll(entities)

    suspend fun insertRelationships(relationships: List<EntityRelationship>) =
        relationshipDao.insertAll(relationships)

    suspend fun getRelationshipsByDocument(documentId: Long): List<EntityRelationship> =
        relationshipDao.getByDocument(documentId)
}
