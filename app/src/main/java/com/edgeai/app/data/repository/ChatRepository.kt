package com.edgeai.app.data.repository

import com.edgeai.app.data.db.dao.ChatSessionDao
import com.edgeai.app.data.entity.ChatMessage
import com.edgeai.app.data.entity.ChatSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
) {
    fun getAllSessions(): Flow<List<ChatSession>> = chatSessionDao.getAllSessions()

    suspend fun getSession(sessionId: Long): ChatSession? =
        chatSessionDao.getSession(sessionId)

    suspend fun createSession(title: String = "New Investigation"): Long =
        chatSessionDao.insertSession(ChatSession(title = title))

    fun getMessages(sessionId: Long): Flow<List<ChatMessage>> =
        chatSessionDao.getMessages(sessionId)

    suspend fun getMessagesOnce(sessionId: Long): List<ChatMessage> =
        chatSessionDao.getMessagesOnce(sessionId)

    suspend fun addMessage(
        sessionId: Long,
        role: String,
        content: String,
        toolName: String? = null,
        sourceDocs: String? = null,
    ): Long {
        chatSessionDao.touchSession(sessionId, System.currentTimeMillis())
        return chatSessionDao.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = role,
                content = content,
                toolName = toolName,
                sourceDocs = sourceDocs,
            )
        )
    }

    suspend fun updateSessionTitle(sessionId: Long, title: String) =
        chatSessionDao.updateTitle(sessionId, title, System.currentTimeMillis())

    suspend fun deleteSession(sessionId: Long) =
        chatSessionDao.deleteSession(sessionId)
}
