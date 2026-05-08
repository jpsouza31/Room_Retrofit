package com.app.room_retrofit.presentation.viewmodel

import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.domain.model.EvStat
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.domain.model.PokemonEvYield
import com.app.room_retrofit.domain.model.PokemonStats
import com.app.room_retrofit.util.Resource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PokedexPaginatorTest {

    private lateinit var repository: PokedexRepository
    private lateinit var paginator: PokedexPaginator

    @Before
    fun setUp() {
        repository = mock()
        paginator = PokedexPaginator(repository)
    }

    // --- State management ---

    @Test
    fun canLoadMore_withTotalCount_returnsTrueWhenOffsetIsLess() {
        paginator.totalCount = 100
        assertTrue(paginator.canLoadMore(offset = 20))
        assertTrue(paginator.canLoadMore(offset = 99))
        assertFalse(paginator.canLoadMore(offset = 100))
        assertFalse(paginator.canLoadMore(offset = 200))
    }

    @Test
    fun canLoadMore_withoutTotalCount_alwaysReturnsTrue() {
        paginator.totalCount = null
        assertTrue(paginator.canLoadMore(0))
        assertTrue(paginator.canLoadMore(9999))
    }

    @Test
    fun offset_defaultsToZeroForUnknownStat() {
        assertEquals(0, paginator.offset(EvStat.ALL))
        assertEquals(0, paginator.offset(EvStat.HP))
    }

    @Test
    fun offsetOrNull_returnsNullForUnknownStat() {
        assertNull(paginator.offsetOrNull(EvStat.ALL))
    }

    @Test
    fun setOffset_andOffset_returnStoredValue() {
        paginator.setOffset(EvStat.SPEED, 40)
        assertEquals(40, paginator.offset(EvStat.SPEED))
    }

    @Test
    fun loaded_defaultsToEmptyList() {
        assertEquals(emptyList<Pokemon>(), paginator.loaded(EvStat.ALL))
    }

    @Test
    fun setLoaded_andLoaded_returnStoredList() {
        val list = listOf(pokemon(1), pokemon(2))
        paginator.setLoaded(EvStat.ALL, list)
        assertEquals(list, paginator.loaded(EvStat.ALL))
    }

    @Test
    fun reset_clearsAllStateAndTotalCount() {
        paginator.totalCount = 151
        paginator.setOffset(EvStat.ALL, 20)
        paginator.setLoaded(EvStat.ALL, listOf(pokemon(1)))

        paginator.reset()

        assertNull(paginator.totalCount)
        assertEquals(0, paginator.offset(EvStat.ALL))
        assertEquals(emptyList<Pokemon>(), paginator.loaded(EvStat.ALL))
    }

    @Test
    fun pageSize_is20() {
        assertEquals(20, paginator.pageSize)
    }

    // --- loadAllPage ---

    @Test
    fun loadAllPage_onSuccess_returnsSortedCacheAndUpdatesState() = runBlocking {
        val page = listOf(pokemon(2), pokemon(1))
        val cache = listOf(pokemon(1), pokemon(2))
        whenever(repository.fetchPage(20, 0)).thenReturn(Resource.Success(page))
        whenever(repository.getCachedPokemon()).thenReturn(cache)
        whenever(repository.getNextPageOffset()).thenReturn(2)
        paginator.totalCount = 100

        val result = paginator.loadAllPage(0) as PageLoadResult.Success

        assertEquals(listOf(pokemon(1), pokemon(2)), result.pokemon)
        assertEquals(2, result.nextOffset)
        assertTrue(result.canLoadMore)
        assertEquals(2, paginator.offset(EvStat.ALL))
    }

    @Test
    fun loadAllPage_onError_withOfflineFlag_setsOfflineTrue() = runBlocking {
        val cached = listOf(pokemon(1))
        whenever(repository.fetchPage(20, 0)).thenReturn(
            Resource.Error("error", isOffline = true)
        )
        whenever(repository.getCachedPokemon()).thenReturn(cached)
        whenever(repository.getNextPageOffset()).thenReturn(1)

        val result = paginator.loadAllPage(0) as PageLoadResult.Failure

        assertTrue(result.isOffline)
        assertEquals(cached, result.pokemon)
    }

    @Test
    fun loadAllPage_onError_withoutOfflineFlag_setsOfflineFalse() = runBlocking {
        whenever(repository.fetchPage(20, 0)).thenReturn(Resource.Error("network error"))
        whenever(repository.getCachedPokemon()).thenReturn(emptyList())
        whenever(repository.getNextPageOffset()).thenReturn(0)

        val result = paginator.loadAllPage(0) as PageLoadResult.Failure

        assertFalse(result.isOffline)
    }

    // --- loadEvFilteredPage ---

    @Test
    fun loadEvFilteredPage_withoutTotalCount_returnsSuccessWithCurrentState() = runBlocking {
        paginator.totalCount = null
        paginator.setLoaded(EvStat.SPEED, listOf(pokemon(1, speedEv = 1)))
        paginator.setOffset(EvStat.SPEED, 20)

        val result = paginator.loadEvFilteredPage(EvStat.SPEED) as PageLoadResult.Success

        assertEquals(listOf(pokemon(1, speedEv = 1)), result.pokemon)
        assertFalse(result.canLoadMore)
    }

    @Test
    fun loadEvFilteredPage_filtersToOnlyMatchingStat() = runBlocking {
        paginator.totalCount = 10
        whenever(repository.getCachedPokemon()).thenReturn(emptyList())
        whenever(repository.getNextPageOffset()).thenReturn(0)
        whenever(repository.fetchPage(20, 0)).thenReturn(
            Resource.Success(listOf(pokemon(1, speedEv = 0), pokemon(2, speedEv = 2)))
        )

        val result = paginator.loadEvFilteredPage(EvStat.SPEED) as PageLoadResult.Success

        assertEquals(1, result.pokemon.size)
        assertEquals(2, result.pokemon[0].id)
    }

    private fun pokemon(id: Int, speedEv: Int = 0) = Pokemon(
        id = id,
        name = "pokemon-$id",
        spriteUrl = null,
        spriteBytes = null,
        types = listOf("normal"),
        stats = PokemonStats(1, 1, 1, 1, 1, 1),
        evYield = PokemonEvYield(0, 0, 0, 0, 0, speedEv)
    )
}
