package com.app.room_retrofit.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.data.repository.nextPageOffsetForCache
import com.app.room_retrofit.domain.model.EvStat
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val paginator = PokedexPaginator(repository)
    private var searchJob: Job? = null
    private var pageLoadJob: Job? = null

    private val _uiState = MutableStateFlow(PokedexUiState())
    val uiState: StateFlow<PokedexUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.connectivityFlow().collect { isOnline ->
                _uiState.value = _uiState.value.copy(isOffline = !isOnline)
            }
        }
        viewModelScope.launch {
            // Room is the single source of truth — always update pokemon from Room Flow,
            // never block on loading state
            repository.pokemonFlow().collect { freshList ->
                val state = _uiState.value
                if (state.query.isBlank()) {
                    val stat = state.selectedEvStat
                    val pokemon = freshList.forStat(stat)
                    paginator.setOffset(stat, freshList.nextPageOffsetForCache())
                    _uiState.value = state.copy(pokemon = pokemon).withFilters()
                }
            }
        }
        loadInitialPage()
    }

    fun loadInitialPage(forceRefresh: Boolean = false) {
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            loadInitialPageInternal(forceRefresh)
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingNextPage || !state.canLoadMore) return
        if (state.query.isNotBlank()) return
        pageLoadJob = viewModelScope.launch {
            loadPageForFilter(stat = state.selectedEvStat, refreshing = false)
        }
    }

    fun refresh() {
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            val selectedStat = _uiState.value.selectedEvStat
            paginator.reset()
            paginator.setOffset(selectedStat, 0)
            _uiState.value = _uiState.value.copy(canLoadMore = true).withFilters()
            loadPageForFilter(stat = selectedStat, refreshing = true)
        }
    }

    fun updateQuery(query: String) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(query = query)

        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            viewModelScope.launch {
                val stat = _uiState.value.selectedEvStat
                val cachedPokemon = repository.getCachedPokemon().forStat(stat)
                _uiState.value = _uiState.value.copy(
                    pokemon = cachedPokemon,
                    isLoading = false,
                    canLoadMore = paginator.canLoadMore(paginator.offsetOrNull(stat) ?: cachedPokemon.size),
                    error = null
                ).withFilters()
            }
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

            // Show cache immediately if available
            val cachedResults = repository.searchPokemonFlow(normalizedQuery).first()
            if (cachedResults.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    pokemon = cachedResults.forStat(_uiState.value.selectedEvStat),
                    isLoading = false,
                    canLoadMore = false,
                    error = null
                ).withFilters()
            }

            // Subscribe to Room Flow so updates (e.g. network fetch) reflect reactively
            launch {
                repository.searchPokemonFlow(normalizedQuery).collect { freshResults ->
                    if (freshResults.isEmpty() && _uiState.value.isLoading) return@collect
                    _uiState.value = _uiState.value.copy(
                        pokemon = freshResults.forStat(_uiState.value.selectedEvStat),
                        isLoading = false,
                        canLoadMore = false,
                        error = null
                    ).withFilters()
                }
            }

            // If cache empty, fetch from network — Room Flow above picks up the write
            if (cachedResults.isEmpty()) {
                when (val result = repository.searchPokemon(normalizedQuery)) {
                    is Resource.Success -> {
                        if (result.data.orEmpty().isEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                canLoadMore = false,
                                error = "Nenhum Pokemon encontrado"
                            )
                        }
                    }
                    is Resource.Error -> {
                        _uiState.value = _uiState.value.copy(
                            pokemon = emptyList(),
                            filteredPokemon = emptyList(),
                            isLoading = false,
                            canLoadMore = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun selectEvStat(stat: EvStat) {
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            if (_uiState.value.query.isNotBlank()) {
                _uiState.value = _uiState.value.copy(selectedEvStat = stat)
                updateQuery(_uiState.value.query)
                return@launch
            }

            val cachedPokemon = repository.getCachedPokemon()
            val pokemon = cachedPokemon.forStat(stat)
            val nextOffset = maxOf(paginator.offset(stat), repository.getNextPageOffset())
            paginator.setOffset(stat, nextOffset)

            _uiState.value = _uiState.value.copy(
                pokemon = pokemon,
                selectedEvStat = stat,
                isLoadingNextPage = false,
                canLoadMore = paginator.canLoadMore(nextOffset)
            ).withFilters()

            if (pokemon.size < paginator.pageSize && paginator.canLoadMore(nextOffset)) {
                loadPageForFilter(stat = stat, refreshing = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearLocalCache() {
        searchJob?.cancel()
        pageLoadJob?.cancel()
        pageLoadJob = viewModelScope.launch {
            repository.clearCache()
            paginator.reset()
            _uiState.value = PokedexUiState()
            loadInitialPageInternal()
        }
    }

    private suspend fun loadInitialPageInternal(forceRefresh: Boolean = false) {
        val cachedPokemon = repository.getCachedPokemon()
        val shouldRefresh = forceRefresh || repository.shouldRefreshCache()
        if (cachedPokemon.isNotEmpty() && !shouldRefresh) {
            val nextOffset = repository.getNextPageOffset()
            paginator.setOffset(EvStat.ALL, nextOffset)
            _uiState.value = _uiState.value.copy(
                pokemon = cachedPokemon,
                isLoading = false,
                canLoadMore = paginator.canLoadMore(nextOffset)
            ).withFilters()
        } else {
            paginator.setOffset(EvStat.ALL, 0)
            _uiState.value = _uiState.value.copy(
                pokemon = cachedPokemon,
                filteredPokemon = cachedPokemon
            )
        }

        if (!shouldRefresh && cachedPokemon.isNotEmpty()) return

        loadPageForFilter(stat = EvStat.ALL, refreshing = forceRefresh)
    }

    private fun PokedexUiState.withFilters(): PokedexUiState {
        val normalizedQuery = query.trim()
        val filtered = pokemon.asSequence()
            .filter { item ->
                normalizedQuery.isBlank() ||
                    item.name.contains(normalizedQuery, ignoreCase = true) ||
                    item.id.toString() == normalizedQuery
            }
            .filter { item -> selectedEvStat == EvStat.ALL || item.evFor(selectedEvStat) > 0 }
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
        val offset = paginator.offset(stat)
        _uiState.value = _uiState.value.copy(
            isLoading = offset == 0 && _uiState.value.pokemon.isEmpty(),
            isRefreshing = refreshing,
            isLoadingNextPage = offset > 0
        )

        if (paginator.totalCount == null) {
            when (val countResult = repository.getTotalPokemonCount()) {
                is Resource.Success -> paginator.totalCount = countResult.data
                is Resource.Error -> {
                    paginator.setOffset(stat, repository.getNextPageOffset())
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingNextPage = false,
                        canLoadMore = false,
                        error = countResult.message,
                        isOffline = countResult.isOffline
                    ).withFilters()
                    return
                }
            }
        }

        val result = if (stat == EvStat.ALL) {
            paginator.loadAllPage(offset)
        } else {
            paginator.loadEvFilteredPage(stat)
        }

        applyPageResult(result)
    }

    private fun applyPageResult(result: PageLoadResult) {
        _uiState.value = when (result) {
            is PageLoadResult.Success -> _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingNextPage = false,
                canLoadMore = result.canLoadMore,
                error = null
            ).withFilters()
            is PageLoadResult.Failure -> _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingNextPage = false,
                canLoadMore = false,
                error = result.message,
                isOffline = result.isOffline
            ).withFilters()
        }
    }
}
