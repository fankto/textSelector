package com.example.textselector.ui.viewmodel

import androidx.lifecycle.*
import com.example.textselector.data.SavedSelection
import com.example.textselector.data.TextSelectorRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: TextSelectorRepository) : ViewModel() {

    private val _savedSelections = MutableLiveData<List<SavedSelection>>()
    val savedSelections: LiveData<List<SavedSelection>> get() = _savedSelections

    init {
        loadSelections()
    }

    fun loadSelections() {
        viewModelScope.launch {
            _savedSelections.value = repository.getAllSelections()
        }
    }

    fun saveSelection(selection: SavedSelection) {
        viewModelScope.launch {
            repository.insertSelection(selection)
            loadSelections()
        }
    }

    fun updateSelection(selection: SavedSelection) {
        viewModelScope.launch {
            repository.updateSelection(selection)
            loadSelections()
        }
    }

    fun deleteSelection(selection: SavedSelection) {
        viewModelScope.launch {
            repository.deleteSelection(selection)
            loadSelections()
        }
    }
}
