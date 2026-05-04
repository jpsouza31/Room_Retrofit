package com.app.room_retrofit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.room_retrofit.data.local.dao.PokemonDao
import com.app.room_retrofit.data.local.entity.PokemonEntity

@Database(entities = [PokemonEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pokemon ADD COLUMN spriteBytes BLOB")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN hpEv INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN attackEv INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN defenseEv INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN specialAttackEv INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN specialDefenseEv INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN speedEv INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pokemon ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
