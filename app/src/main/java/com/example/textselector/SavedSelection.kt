// app/src/main/java/com/example/textselector/SavedSelection.kt
package com.example.textselector

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_selection")
data class SavedSelection(
    @PrimaryKey val id: Long = System.currentTimeMillis(), // use timestamp as unique id
    val name: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getPreviewText(): String {
        // Get first paragraph or first 150 chars
        val endIndex = minOf(
            text.indexOf("\n").takeIf { it != -1 } ?: 150,
            150
        )
        return text.take(endIndex) + if (text.length > endIndex) "..." else ""
    }
}
