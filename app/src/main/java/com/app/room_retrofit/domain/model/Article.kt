package com.app.room_retrofit.domain.model

import com.app.room_retrofit.data.local.entity.ArticleEntity
import com.app.room_retrofit.data.remote.dto.ArticleDto

data class Article(
    val url: String,
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val sourceName: String
)

fun ArticleEntity.toArticle() = Article(
    url = url,
    title = title,
    description = description,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    sourceName = sourceName
)

fun ArticleDto.toEntity() = ArticleEntity(
    url = url ?: "",
    title = title ?: "(no title)",
    description = description,
    urlToImage = urlToImage,
    publishedAt = publishedAt ?: "",
    sourceName = source?.name ?: "Unknown"
)
