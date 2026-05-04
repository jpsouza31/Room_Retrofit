package com.app.room_retrofit.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.domain.model.EvStat
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PokedexUiState(
    val pokemon: List<Pokemon> = emptyList(),
    val filteredPokemon: List<Pokemon> = emptyList(),
    val query: String = "",
    val selectedEvStat: EvStat = EvStat.ALL,
    val isLoading: Boolean = false,
    val isLoadingNextPage: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
    val isOffline: Boolean = false
)

@HiltViewModel
class PokedexViewModel @Inject constructor(
    private val repository: PokedexRepository
) : ViewModel() {
    private val pageSize = 20
    private var totalCount: Int? = null
    private val nextOffsetByFilter = mutableMapOf<EvStat, Int>()
    private val loadedPokemonByFilter = mutableMapOf<EvStat, List<Pokemon>>()
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(PokedexUiState())
    val uiState: StateFlow<PokedexUiState> = _uiState.asStateFlow()

    init {
        loadInitialPage()
    }

    fun loadInitialPage(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val cachedPokemon = repository.getCachedPokemon()
            if (cachedPokemon.isNotEmpty() && !forceRefresh) {
                loadedPokemonByFilter[EvStat.ALL] = cachedPokemon
                nextOffsetByFilter[EvStat.ALL] = repository.getHighestCachedId()
                _uiState.value = _uiState.value.copy(
                    pokemon = cachedPokemon,
                    isLoading = false,
                    canLoadMore = canLoadMoreFrom(repository.getHighestCachedId())
                ).withFilters()
            } else {
                nextOffsetByFilter[EvStat.ALL] = if (forceRefresh) 0 else repository.getHighestCachedId()
                _uiState.value = _uiState.value.copy(
                    pokemon = if (forceRefresh) cachedPokemon else emptyList(),
                    filteredPokemon = if (forceRefresh) cachedPokemon else emptyList()
                )
            }

            val shouldRefresh = forceRefresh || repository.shouldRefreshCache()
            if (!shouldRefresh && cachedPokemon.isNotEmpty()) return@launch

            loadPageForFilter(stat = EvStat.ALL, refreshing = forceRefresh)
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingNextPage || !state.canLoadMore) return
        if (state.query.isNotBlank()) return

        viewModelScope.launch {
            loadPageForFilter(stat = state.selectedEvStat, refreshing = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val selectedStat = _uiState.value.selectedEvStat
            nextOffsetByFilter.clear()
            loadedPokemonByFilter.clear()
            val cachedPokemon = repository.getCachedPokemon()
            loadedPokemonByFilter[selectedStat] = cachedPokemon.forStat(selectedStat)
            nextOffsetByFilter[selectedStat] = 0
            _uiState.value = _uiState.value.copy(
                pokemon = cachedPokemon.forStat(selectedStat),
                canLoadMore = true
            ).withFilters()
            loadPageForFilter(stat = selectedStat, refreshing = true)
        }
    }

    fun updateQuery(query: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = query)

        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            val stat = _uiState.value.selectedEvStat
            val pokemon = loadedPokemonByFilter[stat].orEmpty()
            _uiState.value = _uiState.value.copy(
                pokemon = pokemon,
                isLoading = false,
                canLoadMore = canLoadMoreFrom(nextOffsetByFilter[stat] ?: pokemon.size),
                error = null
            ).withFilters()
            return
        }

        _uiState.value = _uiState.value.copy(
            pokemon = emptyList(),
            filteredPokemon = emptyList(),
            isLoading = true,
            isLoadingNextPage = false,
            canLoadMore = false
        )

        searchJob = viewModelScope.launch {
            delay(300)
            when (val result = repository.searchPokemon(normalizedQuery)) {
                is Resource.Success -> {
                    val pokemon = result.data
                        .orEmpty()
                        .forStat(_uiState.value.selectedEvStat)
                    _uiState.value = _uiState.value.copy(
                        pokemon = pokemon,
                        isLoading = false,
                        isLoadingNextPage = false,
                        canLoadMore = false,
                        error = null,
                        isOffline = false
                    ).withFilters()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        pokemon = emptyList(),
                        filteredPokemon = emptyList(),
                        isLoading = false,
                        isLoadingNextPage = false,
                        canLoadMore = false,
                        error = result.message
                    )
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun selectEvStat(stat: EvStat) {
        viewModelScope.launch {
            if (_uiState.value.query.isNotBlank()) {
                _uiState.value = _uiState.value.copy(selectedEvStat = stat)
                updateQuery(_uiState.value.query)
                return@launch
            }

            val cachedPokemon = repository.getCachedPokemon()
            val pokemon = cachedPokemon.forStat(stat)
            val nextOffset = maxOf(nextOffsetByFilter[stat] ?: 0, repository.getHighestCachedId())
            loadedPokemonByFilter[stat] = pokemon
            nextOffsetByFilter[stat] = nextOffset

            _uiState.value = _uiState.value.copy(
                pokemon = pokemon,
                selectedEvStat = stat,
                isLoadingNextPage = false,
                canLoadMore = canLoadMoreFrom(nextOffset)
            ).withFilters()

            if (pokemon.size < pageSize && canLoadMoreFrom(nextOffset)) {
                loadPageForFilter(stat = stat, refreshing = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearLocalCache() {
        searchJob?.cancel()
        viewModelScope.launch {
            repository.clearCache()
            nextOffsetByFilter.clear()
            loadedPokemonByFilter.clear()
            totalCount = null
            _uiState.value = PokedexUiState(
                error = "Cache local limpo."
            )
        }
    }

    private fun PokedexUiState.withFilters(): PokedexUiState {
        val normalizedQuery = query.trim()
        val filtered = pokemon
            .asSequence()
            .filter { item ->
                normalizedQuery.isBlank() ||
                    item.name.contains(normalizedQuery, ignoreCase = true) ||
                    item.id.toString() == normalizedQuery
            }
            .filter { item ->
                selectedEvStat == EvStat.ALL || item.evFor(selectedEvStat) > 0
            }
            .sortedBy { it.id }
            .toList()
        return copy(filteredPokemon = filtered)
    }

    private fun List<Pokemon>.forStat(stat: EvStat): List<Pokemon> =
        asSequence()
            .filter { stat == EvStat.ALL || it.evFor(stat) > 0 }
            .sortedBy { it.id }
            .toList()

    private suspend fun loadPageForFilter(stat: EvStat, refreshing: Boolean) {
        val offset = nextOffsetByFilter[stat] ?: 0
        _uiState.value = _uiState.value.copy(
            isLoading = offset == 0 && _uiState.value.pokemon.isEmpty(),
            isRefreshing = refreshing,
            isLoadingNextPage = offset > 0
        )

        if (totalCount == null) {
            when (val countResult = repository.getTotalPokemonCount()) {
                is Resource.Success -> totalCount = countResult.data
                is Resource.Error -> {
                    val cachedPokemon = repository.getCachedPokemon()
                    val pokemon = cachedPokemon.forStat(stat)
                    loadedPokemonByFilter[stat] = pokemon
                    nextOffsetByFilter[stat] = repository.getHighestCachedId()
                    _uiState.value = _uiState.value.copy(
                        pokemon = pokemon,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingNextPage = false,
                        canLoadMore = false,
                        error = countResult.message,
                        isOffline = true
                    ).withFilters()
                    return
                }
                is Resource.Loading -> Unit
            }
        }

        if (stat == EvStat.ALL) {
            loadAllPokemonPage(offset = offset)
        } else {
            loadEvFilteredPage(stat = stat, offset = offset)
        }
    }

    private suspend fun loadAllPokemonPage(offset: Int) {
        when (val result = repository.loadPage(pageSize, offset)) {
            is Resource.Success -> {
                val pokemon = result.data.orEmpty().sortedBy { it.id }
                loadedPokemonByFilter[EvStat.ALL] = pokemon
                nextOffsetByFilter[EvStat.ALL] = repository.getHighestCachedId()
                _uiState.value = _uiState.value.copy(
                    pokemon = pokemon,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingNextPage = false,
                    canLoadMore = canLoadMoreFrom(repository.getHighestCachedId()),
                    error = null,
                    isOffline = false
                ).withFilters()
            }
            is Resource.Error -> {
                val pokemon = result.data ?: repository.getCachedPokemon()
                loadedPokemonByFilter[EvStat.ALL] = pokemon
                nextOffsetByFilter[EvStat.ALL] = repository.getHighestCachedId()
                _uiState.value = _uiState.value.copy(
                    pokemon = pokemon.sortedBy { it.id },
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingNextPage = false,
                    canLoadMore = false,
                    error = result.message,
                    isOffline = result.message?.contains("offline", ignoreCase = true) == true ||
                        result.message?.contains("conexao", ignoreCase = true) == true
                ).withFilters()
            }
            is Resource.Loading -> Unit
        }
    }

    private suspend fun loadEvFilteredPage(stat: EvStat, offset: Int) {
        val total = totalCount ?: return
        val cachedPokemon = repository.getCachedPokemon()
        var scanOffset = maxOf(offset, repository.getHighestCachedId())
        val targetSize = loadedPokemonByFilter[stat].orEmpty().size + pageSize
        val accumulated = (loadedPokemonByFilter[stat].orEmpty() + cachedPokemon.forStat(stat))
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toMutableList()
        val accumulatedIds = accumulated.map { it.id }.toMutableSet()

        while (accumulated.size < targetSize && scanOffset < total) {
            when (val result = repository.fetchPage(pageSize, scanOffset)) {
                is Resource.Success -> {
                    result.data
                        .orEmpty()
                        .asSequence()
                        .filter { it.evFor(stat) > 0 }
                        .filter { accumulatedIds.add(it.id) }
                        .forEach { accumulated.add(it) }
                    scanOffset += pageSize
                }
                is Resource.Error -> {
                    val pokemon = (accumulated + result.data.orEmpty().forStat(stat))
                        .distinctBy { it.id }
                        .sortedBy { it.id }
                    loadedPokemonByFilter[stat] = pokemon
                    nextOffsetByFilter[stat] = scanOffset
                    _uiState.value = _uiState.value.copy(
                        pokemon = pokemon,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingNextPage = false,
                        canLoadMore = false,
                        error = result.message,
                        isOffline = result.message?.contains("offline", ignoreCase = true) == true ||
                            result.message?.contains("conexao", ignoreCase = true) == true
                    ).withFilters()
                    return
                }
                is Resource.Loading -> Unit
            }
        }

        val pokemon = accumulated.sortedBy { it.id }
        loadedPokemonByFilter[stat] = pokemon
        nextOffsetByFilter[stat] = scanOffset
        _uiState.value = _uiState.value.copy(
            pokemon = pokemon,
            isLoading = false,
            isRefreshing = false,
            isLoadingNextPage = false,
            canLoadMore = scanOffset < total,
            error = null,
            isOffline = false
        ).withFilters()
    }

    private fun canLoadMoreFrom(loadedCount: Int): Boolean =
        totalCount?.let { loadedCount < it } ?: true
}
