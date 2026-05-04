package com.app.room_retrofit.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.app.room_retrofit.data.local.dao.PokemonDao
import com.app.room_retrofit.data.local.entity.PokemonEntity
import com.app.room_retrofit.data.remote.api.PokeApiService
import com.app.room_retrofit.data.remote.dto.PokemonDetailDto
import com.app.room_retrofit.data.remote.dto.PokemonListItemDto
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.domain.model.toEntity
import com.app.room_retrofit.domain.model.toPokemon
import com.app.room_retrofit.util.Resource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PokedexRepository @Inject constructor(
    private val dao: PokemonDao,
    private val api: PokeApiService,
    @ApplicationContext private val context: Context
) {
    private val cacheValidityMs = 24 * 60 * 60 * 1000L

    fun isOnline(): Boolean = context.isOnline()

    fun pokemonFlow(): Flow<List<Pokemon>> =
        dao.getPokemon().map { list -> list.map { it.toPokemon() } }

    suspend fun getCachedPokemon(): List<Pokemon> =
        dao.getPokemon().first().map { it.toPokemon() }.sortedBy { it.id }

    suspend fun getHighestCachedId(): Int =
        dao.getHighestCachedId() ?: 0

    suspend fun getTotalPokemonCount(): Resource<Int> {
        if (!context.isOnline()) {
            return Resource.Error("Sem conexao para consultar a contagem da PokeAPI.")
        }
        return runCatching {
            Resource.Success(api.getPokemonList(limit = 1).count)
        }.getOrElse { error ->
            Resource.Error(error.localizedMessage ?: "Erro desconhecido")
        }
    }

    suspend fun clearCache() {
        dao.clearPokemon()
    }

    suspend fun loadPage(limit: Int, offset: Int): Resource<List<Pokemon>> {
        return fetchPage(limit = limit, offset = offset)
    }

    suspend fun fetchPage(limit: Int, offset: Int): Resource<List<Pokemon>> {
        if (!context.isOnline()) {
            val cachedPokemon = getCachedPokemon()
            return if (cachedPokemon.isNotEmpty()) {
                Resource.Error("Voce esta offline. Exibindo cache local.", cachedPokemon)
            } else {
                Resource.Error("Sem conexao e sem Pokemon em cache.")
            }
        }

        return runCatching {
            val entities = api.getPokemonList(limit = limit, offset = offset)
                .results
                .mapPageToEntities()
                .sortedBy { it.id }

            dao.insertPokemon(entities)
            Resource.Success(entities.map { it.toPokemon() })
        }.getOrElse { error ->
            val cachedPokemon = getCachedPokemon()
            val message = error.localizedMessage ?: "Erro desconhecido"
            if (cachedPokemon.isNotEmpty()) Resource.Error(message, cachedPokemon) else Resource.Error(message)
        }
    }

    suspend fun getPokemonById(id: Int): Pokemon? =
        dao.getPokemonById(id)?.toPokemon()

    suspend fun searchPokemon(query: String): Resource<List<Pokemon>> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            return Resource.Error("Busca vazia.")
        }

        val cachedPokemon = normalizedQuery.toIntOrNull()
            ?.let { id -> dao.getPokemonById(id)?.let(::listOf) }
            ?: dao.searchPokemonByName(normalizedQuery)

        if (cachedPokemon.isNotEmpty()) {
            return Resource.Success(cachedPokemon.map { it.toPokemon() })
        }

        if (!context.isOnline()) {
            return Resource.Error("Pokemon nao encontrado no cache local.")
        }

        return runCatching {
            val detail = api.getPokemonDetail(normalizedQuery)
            val entity = detail.toEntity(spriteBytes = fetchSpriteBytes(detail))
            dao.insertPokemon(listOf(entity))
            Resource.Success(listOf(entity.toPokemon()))
        }.getOrElse { error ->
            val message = if (error is HttpException && error.code() == 404) {
                "Pokemon nao encontrado."
            } else {
                error.localizedMessage ?: "Pokemon nao encontrado."
            }
            Resource.Error(message)
        }
    }

    private suspend fun isCacheStale(): Boolean {
        val lastCacheTime = dao.getLastCacheTime() ?: return true
        return System.currentTimeMillis() - lastCacheTime > cacheValidityMs
    }

    suspend fun shouldRefreshCache(): Boolean =
        isCacheStale() || getCachedPokemon().isEmpty()

    private suspend fun List<PokemonListItemDto>.mapPageToEntities() =
        coroutineScope {
            map { item ->
                async {
                    val detail = api.getPokemonDetail(item.name)
                    detail.toEntity(spriteBytes = fetchSpriteBytes(detail))
                }
            }.awaitAll()
        }

    private suspend fun fetchSpriteBytes(detail: PokemonDetailDto): ByteArray? {
        val spriteUrl = detail.sprites.frontDefault
            ?: detail.sprites.other?.officialArtwork?.frontDefault
        return runCatching {
            spriteUrl?.let { api.getSprite(it).bytes() }
        }.getOrNull()
    }

}

private fun Context.isOnline(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
