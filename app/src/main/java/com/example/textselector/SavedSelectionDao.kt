package com.example.textselector

import androidx.room.*

@Dao
interface SavedSelectionDao {
    @Query("SELECT * FROM saved_selection ORDER BY timestamp DESC")
    suspend fun getAll(): List<SavedSelection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(selection: SavedSelection)

    @Update
    suspend fun update(selection: SavedSelection)

    @Delete
    suspend fun delete(selection: SavedSelection)
}
