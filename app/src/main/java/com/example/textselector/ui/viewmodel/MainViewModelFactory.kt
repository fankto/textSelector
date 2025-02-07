package com.example.textselector.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.textselector.data.TextSelectorDatabase
import com.example.textselector.data.TextSelectorRepository

class MainViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val repository = TextSelectorRepository(
        TextSelectorDatabase.getInstance(context).savedSelectionDao()
    )

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
