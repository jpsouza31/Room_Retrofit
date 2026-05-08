package com.app.room_retrofit.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.domain.model.Pokemon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PokemonDetailViewModel @Inject constructor(
    private val repository: PokedexRepository
) : ViewModel() {

    private val _pokemon = MutableStateFlow<Pokemon?>(null)
    val pokemon: StateFlow<Pokemon?> = _pokemon.asStateFlow()

    private var loadJob: Job? = null

    fun loadPokemon(id: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = repository.getPokemonById(id)
            if (cached != null) {
                _pokemon.value = cached
            } else {
                // Not in Room — fetch from network, which writes to Room
                repository.searchPokemon(id.toString())
            }

            // Subscribe to Room Flow — picks up both cached and freshly written data
            repository.pokemonByIdFlow(id).collect { pokemon ->
                if (pokemon != null) _pokemon.value = pokemon
            }
        }
    }
}
