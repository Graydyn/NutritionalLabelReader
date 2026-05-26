package com.graydyn.tracker.data.repository

import com.graydyn.tracker.data.db.DiaryEntryDao
import com.graydyn.tracker.data.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val dao: DiaryEntryDao) {
    fun getEntriesForDate(date: String): Flow<List<DiaryEntry>> = dao.getEntriesForDate(date)
    suspend fun insert(entry: DiaryEntry) = dao.insert(entry)
    suspend fun insertAll(entries: List<DiaryEntry>) = dao.insertAll(entries)
    suspend fun delete(entry: DiaryEntry) = dao.delete(entry)
}
