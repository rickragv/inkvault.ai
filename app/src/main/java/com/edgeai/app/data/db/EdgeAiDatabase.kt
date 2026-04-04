package com.edgeai.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.edgeai.app.data.db.dao.ChatSessionDao
import com.edgeai.app.data.db.dao.ContradictionDao
import com.edgeai.app.data.db.dao.DocumentDao
import com.edgeai.app.data.db.dao.EmbeddingDao
import com.edgeai.app.data.db.dao.EntityDao
import com.edgeai.app.data.db.dao.RelationshipDao
import com.edgeai.app.data.db.dao.TimelineEventDao
import com.edgeai.app.data.entity.ChatMessage
import com.edgeai.app.data.entity.ChatSession
import com.edgeai.app.data.entity.Contradiction
import com.edgeai.app.data.entity.DocumentEmbedding
import com.edgeai.app.data.entity.DocumentEntity
import com.edgeai.app.data.entity.EntityRelationship
import com.edgeai.app.data.entity.ExtractedEntity
import com.edgeai.app.data.entity.TimelineEvent

@Database(
    entities = [
        DocumentEntity::class,
        ExtractedEntity::class,
        EntityRelationship::class,
        Contradiction::class,
        TimelineEvent::class,
        ChatSession::class,
        ChatMessage::class,
        DocumentEmbedding::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EdgeAiDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun entityDao(): EntityDao
    abstract fun relationshipDao(): RelationshipDao
    abstract fun contradictionDao(): ContradictionDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun embeddingDao(): EmbeddingDao

    companion object {
        const val DATABASE_NAME = "edgeai.db"

        /**
         * Callback to create FTS5 virtual table and sync triggers.
         * Room 2.6.1 only supports @Fts4, so we use raw SQL for FTS5.
         */
        val FTS5_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // Try FTS5 first, fall back to FTS4 if not available
                val useFts5 = try {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts5
                        USING fts5(title, fullText, content='documents', content_rowid='id')
                    """)
                    true
                } catch (e: Exception) {
                    android.util.Log.w("EdgeAiDatabase", "FTS5 not available, falling back to FTS4: ${e.message}")
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts5
                        USING fts4(title, fullText, content='documents')
                    """)
                    false
                }

                // Triggers to keep FTS in sync with documents table
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS documents_ai AFTER INSERT ON documents BEGIN
                        INSERT INTO documents_fts5(rowid, title, fullText) VALUES (new.id, new.title, new.fullText);
                    END
                """)

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS documents_ad AFTER DELETE ON documents BEGIN
                        INSERT INTO documents_fts5(documents_fts5, rowid, title, fullText) VALUES ('delete', old.id, old.title, old.fullText);
                    END
                """)

                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS documents_au AFTER UPDATE ON documents BEGIN
                        INSERT INTO documents_fts5(documents_fts5, rowid, title, fullText) VALUES ('delete', old.id, old.title, old.fullText);
                        INSERT INTO documents_fts5(rowid, title, fullText) VALUES (new.id, new.title, new.fullText);
                    END
                """)
            }
        }
    }
}
