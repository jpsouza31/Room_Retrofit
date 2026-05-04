package com.app.room_retrofit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.room_retrofit.data.local.entity.PokemonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {
    @Query("SELECT * FROM pokemon ORDER BY id ASC")
    fun getPokemon(): Flow<List<PokemonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPokemon(pokemon: List<PokemonEntity>)

    @Query("DELETE FROM pokemon")
    suspend fun clearPokemon()

    @Query("SELECT MAX(cachedAt) FROM pokemon")
    suspend fun getLastCacheTime(): Long?

    @Query("SELECT MAX(id) FROM pokemon")
    suspend fun getHighestCachedId(): Int?

    @Query("SELECT * FROM pokemon WHERE id = :id LIMIT 1")
    suspend fun getPokemonById(id: Int): PokemonEntity?

    @Query("SELECT * FROM pokemon WHERE name LIKE '%' || :name || '%' ORDER BY id ASC")
    suspend fun searchPokemonByName(name: String): List<PokemonEntity>
}
