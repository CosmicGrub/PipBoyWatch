package com.pipboywatch.app.quest

import android.content.Context
import com.pipboywatch.app.data.PipBoyDatabase
import com.pipboywatch.app.data.QuestEntity
import kotlinx.coroutines.flow.Flow

class QuestRepository(context: Context) {
    private val dao = PipBoyDatabase.getInstance(context.applicationContext).questDao()

    fun observeAll(): Flow<List<QuestEntity>> = dao.observeAll()

    suspend fun addQuest(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dao.insert(QuestEntity(text = trimmed, createdAt = System.currentTimeMillis()))
    }

    suspend fun toggleDone(quest: QuestEntity) {
        dao.setDone(quest.id, !quest.isDone)
    }

    suspend fun remove(quest: QuestEntity) {
        dao.delete(quest)
    }
}
