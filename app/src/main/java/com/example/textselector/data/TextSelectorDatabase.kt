package com.example.textselector.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedSelection::class], version = 1, exportSchema = false)
abstract class TextSelectorDatabase : RoomDatabase() {
    abstract fun savedSelectionDao(): SavedSelectionDao

    companion object {
        @Volatile private var INSTANCE: TextSelectorDatabase? = null

        fun getInstance(context: Context): TextSelectorDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TextSelectorDatabase::class.java,
                    "text_selector_database"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
