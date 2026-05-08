package com.app.room_retrofit.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val spriteUrl: String?,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val spriteBytes: ByteArray?,
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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PokemonEntity) return false
        return id == other.id &&
            name == other.name &&
            spriteUrl == other.spriteUrl &&
            (spriteBytes?.contentEquals(other.spriteBytes) ?: (other.spriteBytes == null)) &&
            types == other.types &&
            hp == other.hp && attack == other.attack && defense == other.defense &&
            specialAttack == other.specialAttack && specialDefense == other.specialDefense &&
            speed == other.speed &&
            hpEv == other.hpEv && attackEv == other.attackEv && defenseEv == other.defenseEv &&
            specialAttackEv == other.specialAttackEv && specialDefenseEv == other.specialDefenseEv &&
            speedEv == other.speedEv
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + (spriteUrl?.hashCode() ?: 0)
        result = 31 * result + (spriteBytes?.contentHashCode() ?: 0)
        result = 31 * result + types.hashCode()
        result = 31 * result + hp
        result = 31 * result + attack
        result = 31 * result + defense
        result = 31 * result + specialAttack
        result = 31 * result + specialDefense
        result = 31 * result + speed
        result = 31 * result + hpEv
        result = 31 * result + attackEv
        result = 31 * result + defenseEv
        result = 31 * result + specialAttackEv
        result = 31 * result + specialDefenseEv
        result = 31 * result + speedEv
        return result
    }
}
