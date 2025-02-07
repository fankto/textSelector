package com.example.textselector.data

class TextSelectorRepository(private val dao: SavedSelectionDao) {
    suspend fun getAllSelections(): List<SavedSelection> = dao.getAll()
    suspend fun insertSelection(selection: SavedSelection) = dao.insert(selection)
    suspend fun updateSelection(selection: SavedSelection) = dao.update(selection)
    suspend fun deleteSelection(selection: SavedSelection) = dao.delete(selection)
}
