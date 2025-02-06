package com.example.textselector

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SavedSelection::class], version = 1)
abstract class TextSelectorDatabase : RoomDatabase() {
    abstract fun savedSelectionDao(): SavedSelectionDao

    companion object {
        @Volatile
        private var INSTANCE: TextSelectorDatabase? = null

        fun getDatabase(context: Context): TextSelectorDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TextSelectorDatabase::class.java,
                    "text_selector_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
