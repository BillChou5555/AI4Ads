package com.aiads.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiads.data.local.DatabaseHelper
import com.aiads.data.model.Ad
import com.aiads.data.remote.SearchApi
import kotlinx.coroutines.launch

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val items: List<Ad>) : SearchState()
    data class Empty(val message: String) : SearchState()
    data class Error(val message: String) : SearchState()
}

class SearchViewModel(
    private val searchApi: SearchApi,
    private val database: DatabaseHelper
) : ViewModel() {

    private val _state = MutableLiveData<SearchState>(SearchState.Idle)
    val state: LiveData<SearchState> = _state

    private val _history = MutableLiveData<List<String>>(emptyList())
    val history: LiveData<List<String>> = _history

    init { loadHistory() }

    fun search(query: String) {
        if (query.isBlank()) return
        _state.value = SearchState.Loading
        database.insertSearchHistory(query)
        loadHistory()

        viewModelScope.launch {
            try {
                val response = searchApi.search(query)
                _state.value = if (response.items.isEmpty()) {
                    SearchState.Empty("未找到相关广告")
                } else {
                    SearchState.Success(response.items)
                }
            } catch (e: Exception) {
                _state.value = SearchState.Error("搜索失败，请重试")
            }
        }
    }

    fun clearHistory() {
        database.clearSearchHistory()
        _history.value = emptyList()
    }

    fun resetState() {
        _state.value = SearchState.Idle
    }

    private fun loadHistory() {
        _history.value = database.getSearchHistory()
    }
}