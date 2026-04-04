package com.edgeai.app.di

import android.content.Context
import com.edgeai.app.config.AppConfig
import com.edgeai.app.data.db.dao.DocumentDao
import com.edgeai.app.data.db.dao.EmbeddingDao
import com.edgeai.app.data.db.dao.EntityDao
import com.edgeai.app.data.db.dao.TimelineEventDao
import com.edgeai.app.data.repository.ContradictionRepository
import com.edgeai.app.data.repository.DocumentRepository
import com.edgeai.app.data.repository.EntityRepository
import com.edgeai.app.ml.ContradictionAnalyzer
import com.edgeai.app.ml.EmbeddingEngine
import com.edgeai.app.ml.GemmaInferenceEngine
import com.edgeai.app.ml.GemmaInferenceEngineProvider
import com.edgeai.app.ml.GemmaModelManager
import com.edgeai.app.ml.ImagePreprocessor
import com.edgeai.app.ml.InvestigationToolSet
import com.edgeai.app.ml.MlKitOcrEngine
import com.edgeai.app.ml.NerExtractor
import com.edgeai.app.ml.RagPipeline
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppConfig(
        @ApplicationContext context: Context,
    ): AppConfig = AppConfig(context)

    @Provides
    @Singleton
    fun provideGemmaModelManager(
        @ApplicationContext context: Context,
        config: AppConfig,
    ): GemmaModelManager = GemmaModelManager(context, config)

    @Provides
    @Singleton
    fun provideGemmaInferenceEngine(
        modelManager: GemmaModelManager,
        config: AppConfig,
    ): GemmaInferenceEngine {
        val engine = GemmaInferenceEngine(modelManager, config)
        GemmaInferenceEngineProvider.set(engine)
        return engine
    }

    @Provides
    @Singleton
    fun provideImagePreprocessor(
        config: AppConfig,
    ): ImagePreprocessor = ImagePreprocessor(config)

    @Provides
    @Singleton
    fun provideMlKitOcrEngine(): MlKitOcrEngine = MlKitOcrEngine()

    @Provides
    @Singleton
    fun provideNerExtractor(
        inferenceEngine: GemmaInferenceEngine,
        config: AppConfig,
    ): NerExtractor = NerExtractor(inferenceEngine, config)

    @Provides
    @Singleton
    fun provideContradictionAnalyzer(
        inferenceEngine: GemmaInferenceEngine,
        config: AppConfig,
    ): ContradictionAnalyzer = ContradictionAnalyzer(inferenceEngine, config)

    @Provides
    @Singleton
    fun provideEmbeddingEngine(
        config: AppConfig,
    ): EmbeddingEngine = EmbeddingEngine(config)

    @Provides
    @Singleton
    fun provideRagPipeline(
        documentDao: DocumentDao,
        embeddingDao: EmbeddingDao,
        embeddingEngine: EmbeddingEngine,
        config: AppConfig,
    ): RagPipeline = RagPipeline(documentDao, embeddingDao, embeddingEngine, config)

    @Provides
    @Singleton
    fun provideInvestigationToolSet(
        documentRepository: DocumentRepository,
        entityRepository: EntityRepository,
        contradictionRepository: ContradictionRepository,
        contradictionAnalyzer: ContradictionAnalyzer,
        ragPipeline: RagPipeline,
        nerExtractor: NerExtractor,
        timelineEventDao: TimelineEventDao,
        documentDao: DocumentDao,
    ): InvestigationToolSet = InvestigationToolSet(
        documentRepository, entityRepository, contradictionRepository,
        contradictionAnalyzer, ragPipeline, nerExtractor, timelineEventDao, documentDao,
    )
}
