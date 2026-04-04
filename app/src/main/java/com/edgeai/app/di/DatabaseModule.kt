package com.edgeai.app.di

import android.content.Context
import androidx.room.Room
import com.edgeai.app.data.db.EdgeAiDatabase
import com.edgeai.app.data.db.dao.ChatSessionDao
import com.edgeai.app.data.db.dao.ContradictionDao
import com.edgeai.app.data.db.dao.DocumentDao
import com.edgeai.app.data.db.dao.EmbeddingDao
import com.edgeai.app.data.db.dao.EntityDao
import com.edgeai.app.data.db.dao.RelationshipDao
import com.edgeai.app.data.db.dao.TimelineEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): EdgeAiDatabase = Room.databaseBuilder(
        context,
        EdgeAiDatabase::class.java,
        EdgeAiDatabase.DATABASE_NAME,
    )
        .addCallback(EdgeAiDatabase.FTS5_CALLBACK)
        .build()

    @Provides
    fun provideDocumentDao(db: EdgeAiDatabase): DocumentDao = db.documentDao()

    @Provides
    fun provideEntityDao(db: EdgeAiDatabase): EntityDao = db.entityDao()

    @Provides
    fun provideRelationshipDao(db: EdgeAiDatabase): RelationshipDao = db.relationshipDao()

    @Provides
    fun provideContradictionDao(db: EdgeAiDatabase): ContradictionDao = db.contradictionDao()

    @Provides
    fun provideTimelineEventDao(db: EdgeAiDatabase): TimelineEventDao = db.timelineEventDao()

    @Provides
    fun provideChatSessionDao(db: EdgeAiDatabase): ChatSessionDao = db.chatSessionDao()

    @Provides
    fun provideEmbeddingDao(db: EdgeAiDatabase): EmbeddingDao = db.embeddingDao()
}
