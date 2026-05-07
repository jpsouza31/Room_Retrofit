package com.app.room_retrofit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val spriteUrl: String?,
    val spriteBytes: ByteArray?,
    val types: String,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val speed: Int,
    val hpEv: Int,
    val attackEv: Int,
    val defenseEv: Int,
    val specialAttackEv: Int,
    val specialDefenseEv: Int,
    val speedEv: Int,
    val cachedAt: Long = System.currentTimeMillis()
)
