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

    /** For ExportManager. */
    suspend fun getAllOnce(): List<QuestEntity> = dao.getAllOnce()

    /** For RestoreManager — always inserts as a new row (id=0). Quests are
     * an accumulating list, so a restore is expected to add history back,
     * not upsert against something already present. */
    suspend fun restoreQuest(quest: QuestEntity) {
        dao.insert(quest.copy(id = 0))
    }
}
