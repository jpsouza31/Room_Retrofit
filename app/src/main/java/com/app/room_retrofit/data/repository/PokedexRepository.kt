package com.app.room_retrofit.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.room.withTransaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.app.room_retrofit.R
import com.app.room_retrofit.data.local.AppDatabase
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
    private val db: AppDatabase,
    private val dao: PokemonDao,
    private val api: PokeApiService,
    @ApplicationContext private val context: Context
) {
    private val cacheValidityMs = 24 * 60 * 60 * 1000L

    fun isOnline(): Boolean = context.isOnline()

    fun connectivityFlow(): Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)
        trySend(context.isOnline())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun pokemonFlow(): Flow<List<Pokemon>> =
        dao.getPokemon().map { list -> list.map { it.toPokemon() } }

    fun pokemonByIdFlow(id: Int): Flow<Pokemon?> =
        dao.observePokemonById(id).map { it?.toPokemon() }

    fun searchPokemonFlow(query: String): Flow<List<Pokemon>> {
        val normalizedQuery = query.trim().lowercase()
        return normalizedQuery.toIntOrNull()
            ?.let { id ->
                dao.observePokemonById(id).map { entity ->
                    entity?.let { listOf(it.toPokemon()) }.orEmpty()
                }
            }
            ?: dao.observePokemonByName(normalizedQuery).map { list ->
                list.map { it.toPokemon() }
            }
    }

    suspend fun getCachedPokemon(): List<Pokemon> =
        dao.getPokemon().first().map { it.toPokemon() }.sortedBy { it.id }

    suspend fun getNextPageOffset(): Int =
        getCachedPokemon().nextPageOffsetForCache()

    suspend fun getTotalPokemonCount(): Resource<Int> {
        if (!context.isOnline()) {
            val cachedCount = dao.getCachedPokemonCount()
            return if (cachedCount > 0) {
                Resource.Success(cachedCount)
            } else {
                Resource.Error(context.getString(R.string.error_no_connection_count), isOffline = true)
            }
        }
        return try {
            Resource.Success(api.getPokemonList(limit = 1).count)
        } catch (error: Exception) {
            Resource.Error(error.localizedMessage ?: "Erro desconhecido")
        }
    }

    suspend fun clearCache() {
        dao.clearPokemon()
    }

    suspend fun fetchPage(limit: Int, offset: Int): Resource<List<Pokemon>> {
        if (!context.isOnline()) {
            val cachedPokemon = getCachedPokemon()
            return if (cachedPokemon.isNotEmpty()) {
                Resource.Error(context.getString(R.string.error_offline_using_cache), cachedPokemon, isOffline = true)
            } else {
                Resource.Error(context.getString(R.string.error_offline_no_cache), isOffline = true)
            }
        }

        return try {
            val entities = api.getPokemonList(limit = limit, offset = offset)
                .results
                .mapPageToEntities()
                .sortedBy { it.id }

            val cachedEntities: List<PokemonEntity> = db.withTransaction {
                dao.insertPokemon(entities)
                dao.getPokemonByIds(entities.map { it.id })
            }
            Resource.Success(cachedEntities.map { it.toPokemon() })
        } catch (error: Exception) {
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
            return Resource.Error(context.getString(R.string.error_pokemon_not_found_cache), isOffline = true)
        }

        return try {
            val detail = api.getPokemonDetail(normalizedQuery)
            val entity = detail.toEntity(spriteBytes = fetchSpriteBytes(detail))
            val cachedEntity: PokemonEntity? = db.withTransaction {
                dao.insertPokemon(listOf(entity))
                dao.getPokemonById(entity.id)
            }
            Resource.Success(listOfNotNull(cachedEntity?.toPokemon()))
        } catch (error: Exception) {
            val message = if (error is HttpException && error.code() == 404) {
                context.getString(R.string.error_pokemon_not_found)
            } else {
                error.localizedMessage ?: context.getString(R.string.error_pokemon_not_found)
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
        return try {
            spriteUrl?.let { api.getSprite(it).bytes() }
        } catch (e: Exception) {
            null
        }
    }

}

private fun Context.isOnline(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

internal fun List<Pokemon>.nextPageOffsetForCache(): Int {
    var expectedId = 1
    for (pokemon in sortedBy { it.id }) {
        if (pokemon.id != expectedId) break
        expectedId++
    }
    return expectedId - 1
}
