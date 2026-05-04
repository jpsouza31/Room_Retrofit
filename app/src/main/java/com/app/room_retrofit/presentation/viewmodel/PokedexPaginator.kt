package com.app.room_retrofit.presentation.viewmodel

import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.domain.model.EvStat
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.util.Resource

sealed interface PageLoadResult {
    data class Success(
        val pokemon: List<Pokemon>,
        val nextOffset: Int,
        val canLoadMore: Boolean
    ) : PageLoadResult

    data class Failure(
        val pokemon: List<Pokemon>,
        val message: String?,
        val isOffline: Boolean
    ) : PageLoadResult
}

class PokedexPaginator(private val repository: PokedexRepository) {

    val pageSize = 20
    var totalCount: Int? = null

    private val nextOffsetByFilter = mutableMapOf<EvStat, Int>()
    private val loadedByFilter = mutableMapOf<EvStat, List<Pokemon>>()

    fun offset(stat: EvStat): Int = nextOffsetByFilter[stat] ?: 0
    fun offsetOrNull(stat: EvStat): Int? = nextOffsetByFilter[stat]
    fun loaded(stat: EvStat): List<Pokemon> = loadedByFilter[stat].orEmpty()
    fun setOffset(stat: EvStat, v: Int) { nextOffsetByFilter[stat] = v }
    fun setLoaded(stat: EvStat, v: List<Pokemon>) { loadedByFilter[stat] = v }
    fun canLoadMore(offset: Int): Boolean = totalCount?.let { offset < it } ?: true

    fun reset() {
        nextOffsetByFilter.clear()
        loadedByFilter.clear()
        totalCount = null
    }

    suspend fun loadAllPage(offset: Int): PageLoadResult {
        return when (val result = repository.loadPage(pageSize, offset)) {
            is Resource.Success -> {
                val pokemon = repository.getCachedPokemon().sortedBy { it.id }
                val nextOffset = repository.getNextPageOffset()
                setLoaded(EvStat.ALL, pokemon)
                setOffset(EvStat.ALL, nextOffset)
                PageLoadResult.Success(pokemon, nextOffset, canLoadMore(nextOffset))
            }
            is Resource.Error -> {
                val pokemon = (result.data ?: repository.getCachedPokemon()).sortedBy { it.id }
                val nextOffset = repository.getNextPageOffset()
                setLoaded(EvStat.ALL, pokemon)
                setOffset(EvStat.ALL, nextOffset)
                PageLoadResult.Failure(pokemon, result.message, result.isOffline)
            }
            is Resource.Loading -> PageLoadResult.Success(loaded(EvStat.ALL), offset(EvStat.ALL), canLoadMore(offset(EvStat.ALL)))
        }
    }

    suspend fun loadEvFilteredPage(stat: EvStat): PageLoadResult {
        val total = totalCount ?: return PageLoadResult.Success(loaded(stat), offset(stat), false)
        val cachedPokemon = repository.getCachedPokemon()
        var scanOffset = maxOf(offset(stat), repository.getNextPageOffset())
        val targetSize = loaded(stat).size + pageSize
        val accumulated = (loaded(stat) + cachedPokemon.forStat(stat))
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toMutableList()
        val seen = accumulated.map { it.id }.toMutableSet()

        while (accumulated.size < targetSize && scanOffset < total) {
            when (val result = repository.fetchPage(pageSize, scanOffset)) {
                is Resource.Success -> {
                    result.data.orEmpty()
                        .filter { it.evFor(stat) > 0 && seen.add(it.id) }
                        .forEach { accumulated.add(it) }
                    scanOffset += pageSize
                }
                is Resource.Error -> {
                    val pokemon = (accumulated + result.data.orEmpty().forStat(stat))
                        .distinctBy { it.id }
                        .sortedBy { it.id }
                    setLoaded(stat, pokemon)
                    setOffset(stat, scanOffset)
                    return PageLoadResult.Failure(pokemon, result.message, result.isOffline)
                }
                is Resource.Loading -> Unit
            }
        }

        val pokemon = accumulated.sortedBy { it.id }
        setLoaded(stat, pokemon)
        setOffset(stat, scanOffset)
        return PageLoadResult.Success(pokemon, scanOffset, scanOffset < total)
    }

    private fun List<Pokemon>.forStat(stat: EvStat): List<Pokemon> =
        filter { it.evFor(stat) > 0 }.sortedBy { it.id }
}
