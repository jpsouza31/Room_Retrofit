package com.app.room_retrofit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PokemonTest {

    @Test
    fun equals_sameContentSpriteBytes_areEqual() {
        val a = pokemon(id = 1, spriteBytes = byteArrayOf(1, 2, 3))
        val b = pokemon(id = 1, spriteBytes = byteArrayOf(1, 2, 3))
        assertEquals(a, b)
    }

    @Test
    fun equals_differentContentSpriteBytes_areNotEqual() {
        val a = pokemon(id = 1, spriteBytes = byteArrayOf(1, 2, 3))
        val b = pokemon(id = 1, spriteBytes = byteArrayOf(9, 9, 9))
        assertNotEquals(a, b)
    }

    @Test
    fun equals_nullVsNonNullSpriteBytes_areNotEqual() {
        val a = pokemon(id = 1, spriteBytes = null)
        val b = pokemon(id = 1, spriteBytes = byteArrayOf(1))
        assertNotEquals(a, b)
    }

    @Test
    fun equals_bothNullSpriteBytes_areEqual() {
        val a = pokemon(id = 1, spriteBytes = null)
        val b = pokemon(id = 1, spriteBytes = null)
        assertEquals(a, b)
    }

    @Test
    fun hashCode_equalPokemon_sameHash() {
        val a = pokemon(id = 1, spriteBytes = byteArrayOf(1, 2, 3))
        val b = pokemon(id = 1, spriteBytes = byteArrayOf(1, 2, 3))
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun hashCode_differentPokemon_differentHash() {
        val a = pokemon(id = 1, spriteBytes = byteArrayOf(1, 2, 3))
        val b = pokemon(id = 2, spriteBytes = byteArrayOf(1, 2, 3))
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun usableAsMapKey_equalPokemonResolvesToSameKey() {
        val a = pokemon(id = 42, spriteBytes = byteArrayOf(7, 8))
        val b = pokemon(id = 42, spriteBytes = byteArrayOf(7, 8))
        val map = mutableMapOf(a to "value")
        assertEquals("value", map[b])
    }

    private fun pokemon(id: Int, spriteBytes: ByteArray?) = Pokemon(
        id = id,
        name = "pokemon-$id",
        spriteUrl = null,
        spriteBytes = spriteBytes,
        types = listOf("normal"),
        stats = PokemonStats(45, 49, 49, 65, 65, 45),
        evYield = PokemonEvYield(1, 0, 0, 0, 0, 0)
    )
}
