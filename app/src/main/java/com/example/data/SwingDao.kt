package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SwingDao {
    @Query("SELECT * FROM swing_sessions ORDER BY createdAtMs DESC")
    fun observeAllSwings(): Flow<List<SwingEntity>>

    @Query("SELECT * FROM swing_sessions WHERE id = :id LIMIT 1")
    fun observeSwingById(id: Long): Flow<SwingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwing(entity: SwingEntity): Long

    @Query("DELETE FROM swing_sessions WHERE id = :id")
    suspend fun deleteSwingById(id: Long)

    @Query("DELETE FROM swing_sessions")
    suspend fun clearAll()
}
