package com.app.room_retrofit.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.room_retrofit.data.repository.NewsRepository
import com.app.room_retrofit.domain.model.Article
import com.app.room_retrofit.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewsUiState(
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false
)

@HiltViewModel
class NewsViewModel @Inject constructor(
    private val repository: NewsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    init {
        loadNews(forceRefresh = false)
    }

    fun loadNews(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.getNews(forceRefresh).collect { result ->
                _uiState.value = when (result) {
                    is Resource.Loading -> _uiState.value.copy(
                        isLoading = result.data == null,
                        isRefreshing = forceRefresh,
                        articles = result.data ?: _uiState.value.articles
                    )
                    is Resource.Success -> _uiState.value.copy(
                        articles = result.data ?: emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        isOffline = false
                    )
                    is Resource.Error -> _uiState.value.copy(
                        articles = result.data ?: _uiState.value.articles,
                        isLoading = false,
                        isRefreshing = false,
                        error = result.message,
                        isOffline = result.message?.contains("offline", ignoreCase = true) == true
                    )
                }
            }
        }
    }

    fun refresh() = loadNews(forceRefresh = true)

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
