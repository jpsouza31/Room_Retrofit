package com.app.room_retrofit.data.repository

import android.content.Context
import com.app.room_retrofit.BuildConfig
import com.app.room_retrofit.data.local.dao.ArticleDao
import com.app.room_retrofit.data.remote.api.NewsApiService
import com.app.room_retrofit.domain.model.Article
import com.app.room_retrofit.domain.model.toArticle
import com.app.room_retrofit.domain.model.toEntity
import com.app.room_retrofit.util.Resource
import com.app.room_retrofit.util.isOnline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository @Inject constructor(
    private val dao: ArticleDao,
    private val api: NewsApiService,
    @ApplicationContext private val context: Context
) {
    private val cacheValidityMs = 5 * 60 * 1000L

    fun getNews(forceRefresh: Boolean = false): Flow<Resource<List<Article>>> = flow {
        emit(Resource.Loading())

        val cachedEntities = dao.getArticles().first()
        val cachedArticles = cachedEntities.map { it.toArticle() }

        if (cachedArticles.isNotEmpty()) {
            emit(Resource.Loading(cachedArticles))
        }

        val cacheStale = isCacheStale()

        if (!forceRefresh && !cacheStale && cachedArticles.isNotEmpty()) {
            emit(Resource.Success(cachedArticles))
            return@flow
        }

        if (!isOnline(context)) {
            if (cachedArticles.isNotEmpty()) {
                emit(Resource.Error("Você está offline. Exibindo dados em cache.", cachedArticles))
            } else {
                emit(Resource.Error("Sem conexão e sem dados em cache."))
            }
            return@flow
        }

        try {
            val response = api.getTopHeadlines(apiKey = BuildConfig.NEWS_API_KEY)
            val entities = response.articles
                .filter { it.url != null }
                .map { it.toEntity() }
            dao.clearArticles()
            dao.insertArticles(entities)
            val freshArticles = entities.sortedByDescending { it.publishedAt }.map { it.toArticle() }
            emit(Resource.Success(freshArticles))
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Erro desconhecido"
            if (cachedArticles.isNotEmpty()) {
                emit(Resource.Error(msg, cachedArticles))
            } else {
                emit(Resource.Error(msg))
            }
        }
    }

    private suspend fun isCacheStale(): Boolean {
        val lastCacheTime = dao.getLastCacheTime() ?: return true
        return System.currentTimeMillis() - lastCacheTime > cacheValidityMs
    }
}
