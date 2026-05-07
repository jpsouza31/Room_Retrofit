package com.app.room_retrofit.data.remote.api

import com.app.room_retrofit.data.remote.dto.PokemonDetailDto
import com.app.room_retrofit.data.remote.dto.PokemonListResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface PokeApiService {
    @GET("api/v2/pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): PokemonListResponse

    @GET("api/v2/pokemon/{nameOrId}")
    suspend fun getPokemonDetail(
        @Path("nameOrId") nameOrId: String
    ): PokemonDetailDto

    @GET
    suspend fun getSprite(
        @Url url: String
    ): ResponseBody
}
