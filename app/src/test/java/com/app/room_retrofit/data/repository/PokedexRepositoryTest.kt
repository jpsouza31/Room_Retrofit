package com.app.room_retrofit.data.repository

import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.domain.model.PokemonEvYield
import com.app.room_retrofit.domain.model.PokemonStats
import org.junit.Assert.assertEquals
import org.junit.Test

class PokedexRepositoryTest {

    @Test
    fun nextPageOffsetForCache_usesOnlyContiguousPokedexPrefix() {
        val cache = listOf(
            pokemon(id = 1),
            pokemon(id = 2),
            pokemon(id = 150)
        )

        assertEquals(2, cache.nextPageOffsetForCache())
    }

    @Test
    fun nextPageOffsetForCache_returnsZeroWhenFirstPageIsMissing() {
        val cache = listOf(pokemon(id = 25), pokemon(id = 150))

        assertEquals(0, cache.nextPageOffsetForCache())
    }

    @Test
    fun nextPageOffsetForCache_ignoresInputOrder() {
        val cache = listOf(pokemon(id = 3), pokemon(id = 1), pokemon(id = 2))

        assertEquals(3, cache.nextPageOffsetForCache())
    }

    private fun pokemon(id: Int) = Pokemon(
        id = id,
        name = "pokemon-$id",
        spriteUrl = null,
        spriteBytes = null,
        types = listOf("normal"),
        stats = PokemonStats(
            hp = 1,
            attack = 1,
            defense = 1,
            specialAttack = 1,
            specialDefense = 1,
            speed = 1
        ),
        evYield = PokemonEvYield(
            hp = 1,
            attack = 0,
            defense = 0,
            specialAttack = 0,
            specialDefense = 0,
            speed = 0
        )
    )
}
