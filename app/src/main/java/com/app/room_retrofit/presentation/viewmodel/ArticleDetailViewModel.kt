package com.app.room_retrofit.presentation.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.room_retrofit.data.local.dao.ArticleDao
import com.app.room_retrofit.domain.model.Article
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    private val dao: ArticleDao,
    @ApplicationContext context: Context
) : ViewModel() {

    val isOnline: Boolean = context.isOnline()

    private val _article = MutableStateFlow<Article?>(null)
    val article: StateFlow<Article?> = _article.asStateFlow()

    fun loadArticle(url: String) {
        viewModelScope.launch {
            _article.value = dao.getArticleByUrl(url)?.let { entity ->
                Article(
                    url = entity.url,
                    title = entity.title,
                    description = entity.description,
                    urlToImage = entity.urlToImage,
                    publishedAt = entity.publishedAt,
                    sourceName = entity.sourceName
                )
            }
        }
    }
}

private fun Context.isOnline(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
