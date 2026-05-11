package com.app.room_retrofit.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PokemonDetailUiState(
    val pokemon: Pokemon? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PokemonDetailViewModel @Inject constructor(
    private val repository: PokedexRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PokemonDetailUiState())
    val uiState: StateFlow<PokemonDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadPokemon(id: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PokemonDetailUiState(isLoading = true)
            val cached = repository.getPokemonById(id)
            if (cached != null) {
                _uiState.value = PokemonDetailUiState(pokemon = cached)
            } else {
                when (val result = repository.searchPokemon(id.toString())) {
                    is Resource.Success -> {
                        val pokemon = result.data.orEmpty().firstOrNull()
                        _uiState.value = if (pokemon != null) {
                            PokemonDetailUiState(pokemon = pokemon)
                        } else {
                            PokemonDetailUiState(error = "Pokemon nao encontrado")
                        }
                    }
                    is Resource.Error -> {
                        _uiState.value = PokemonDetailUiState(error = result.message)
                    }
                }
            }

            repository.pokemonByIdFlow(id).collect { pokemon ->
                if (pokemon != null) {
                    _uiState.value = PokemonDetailUiState(pokemon = pokemon)
                }
            }
        }
    }
}
