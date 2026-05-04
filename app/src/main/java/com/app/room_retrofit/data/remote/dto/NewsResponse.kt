package com.app.room_retrofit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<ArticleDto>
)

data class ArticleDto(
    val source: SourceDto?,
    val title: String?,
    val description: String?,
    val url: String?,
    val urlToImage: String?,
    @SerializedName("publishedAt") val publishedAt: String?
)

data class SourceDto(
    val id: String?,
    val name: String?
)
