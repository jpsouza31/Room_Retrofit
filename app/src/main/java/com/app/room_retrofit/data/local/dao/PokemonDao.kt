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

    @Query("SELECT MIN(cachedAt) FROM pokemon")
    suspend fun getLastCacheTime(): Long?

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun getCachedPokemonCount(): Int

    @Query("SELECT * FROM pokemon WHERE id = :id LIMIT 1")
    suspend fun getPokemonById(id: Int): PokemonEntity?

    @Query("SELECT * FROM pokemon WHERE id IN (:ids) ORDER BY id ASC")
    suspend fun getPokemonByIds(ids: List<Int>): List<PokemonEntity>

    @Query("SELECT * FROM pokemon WHERE name LIKE '%' || :name || '%' ORDER BY id ASC")
    suspend fun searchPokemonByName(name: String): List<PokemonEntity>

    @Query("SELECT * FROM pokemon WHERE id = :id LIMIT 1")
    fun observePokemonById(id: Int): Flow<PokemonEntity?>

    @Query("SELECT * FROM pokemon WHERE name LIKE '%' || :name || '%' ORDER BY id ASC")
    fun observePokemonByName(name: String): Flow<List<PokemonEntity>>
}
