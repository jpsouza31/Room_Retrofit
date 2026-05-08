package com.app.room_retrofit.presentation.viewmodel

import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.domain.model.EvStat
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.util.Resource

sealed interface PageLoadResult {
    data class Success(val canLoadMore: Boolean) : PageLoadResult
    data class Failure(val message: String?, val isOffline: Boolean) : PageLoadResult
}

class PokedexPaginator(private val repository: PokedexRepository) {

    val pageSize = 20
    var totalCount: Int? = null

    companion object {
        private const val MAX_SCAN_PAGES = 50
    }

    private val nextOffsetByFilter = mutableMapOf<EvStat, Int>()

    fun offset(stat: EvStat): Int = nextOffsetByFilter[stat] ?: 0
    fun offsetOrNull(stat: EvStat): Int? = nextOffsetByFilter[stat]
    fun setOffset(stat: EvStat, v: Int) { nextOffsetByFilter[stat] = v }
    fun canLoadMore(offset: Int): Boolean = totalCount?.let { offset < it } ?: true

    fun reset() {
        nextOffsetByFilter.clear()
        totalCount = null
    }

    suspend fun loadAllPage(offset: Int): PageLoadResult {
        return when (val result = repository.fetchPage(pageSize, offset)) {
            is Resource.Success -> {
                val nextOffset = repository.getNextPageOffset()
                setOffset(EvStat.ALL, nextOffset)
                PageLoadResult.Success(canLoadMore(nextOffset))
            }
            is Resource.Error -> {
                val nextOffset = repository.getNextPageOffset()
                setOffset(EvStat.ALL, nextOffset)
                PageLoadResult.Failure(result.message, result.isOffline)
            }
        }
    }

    suspend fun loadEvFilteredPage(stat: EvStat): PageLoadResult {
        val total = totalCount ?: return PageLoadResult.Success(canLoadMore = false)
        var scanOffset = maxOf(offset(stat), repository.getNextPageOffset())
        val initialCount = repository.getCachedPokemon().forStat(stat).size
        val targetCount = initialCount + 1
        val scanLimit = scanOffset + pageSize * MAX_SCAN_PAGES

        while (scanOffset < total && scanOffset < scanLimit) {
            val currentCount = repository.getCachedPokemon().forStat(stat).size
            if (currentCount >= targetCount) break

            when (val result = repository.fetchPage(pageSize, scanOffset)) {
                is Resource.Success -> scanOffset += pageSize
                is Resource.Error -> {
                    setOffset(stat, scanOffset)
                    return PageLoadResult.Failure(result.message, result.isOffline)
                }
            }
        }

        setOffset(stat, scanOffset)
        return PageLoadResult.Success(canLoadMore = scanOffset < total)
    }

    private fun List<Pokemon>.forStat(stat: EvStat): List<Pokemon> =
        filter { it.evFor(stat) > 0 }.sortedBy { it.id }
}
