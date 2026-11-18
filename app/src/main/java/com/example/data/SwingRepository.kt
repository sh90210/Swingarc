package com.example.data

import com.example.model.SwingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.File

class SwingRepository(private val swingDao: SwingDao) {

    val allSwings: Flow<List<SwingSession>> =
        swingDao.observeAllSwings().map { list -> list.map { it.toDomain() } }

    fun observeSwing(id: Long): Flow<SwingSession?> =
        swingDao.observeSwingById(id).map { it?.toDomain() }

    suspend fun saveSwing(session: SwingSession): Long =
        swingDao.insertSwing(SwingEntity.fromDomain(session))

    suspend fun deleteSwing(id: Long) {
        val existing = swingDao.observeSwingById(id).firstOrNull()
        existing?.videoPath?.let { path ->
            runCatching { File(path).delete() }
        }
        swingDao.deleteSwingById(id)
    }

    suspend fun clearAllSwings() {
        val all = swingDao.observeAllSwings().firstOrNull().orEmpty()
        all.forEach { entity ->
            entity.videoPath?.let { path -> runCatching { File(path).delete() } }
        }
        swingDao.clearAll()
    }
}
