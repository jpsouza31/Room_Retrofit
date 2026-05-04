package com.app.room_retrofit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.app.room_retrofit.data.local.dao.PokemonDao
import com.app.room_retrofit.data.local.entity.PokemonEntity

@Database(entities = [PokemonEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao
}
