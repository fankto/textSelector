package com.example.textselector.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_selection")
data class SavedSelection(
    @PrimaryKey val id: Long = System.currentTimeMillis(),
    val name: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getPreviewText(maxChars: Int = 150): String {
        val preview = text.take(maxChars)
        return if (text.length > maxChars) "$preview..." else preview
    }
}
