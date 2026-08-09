package com.pipboywatch.app.notes

import android.content.Context
import com.pipboywatch.app.data.NoteEntity
import com.pipboywatch.app.data.PipBoyDatabase
import kotlinx.coroutines.flow.Flow

class NoteRepository(context: Context) {
    private val dao = PipBoyDatabase.getInstance(context.applicationContext).noteDao()

    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun addFromWatch(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        dao.insert(NoteEntity(text = trimmed, receivedAt = System.currentTimeMillis(), source = "watch"))
    }
}
