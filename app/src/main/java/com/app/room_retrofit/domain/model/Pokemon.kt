package com.app.room_retrofit.domain.model

import com.app.room_retrofit.data.local.entity.PokemonEntity
import com.app.room_retrofit.data.remote.dto.PokemonDetailDto

data class Pokemon(
    val id: Int,
    val name: String,
    val spriteUrl: String?,
    val spriteBytes: ByteArray?,
    val types: List<String>,
    val stats: PokemonStats,
    val evYield: PokemonEvYield
) {
    val totalEvYield: Int
        get() = evYield.hp +
            evYield.attack +
            evYield.defense +
            evYield.specialAttack +
            evYield.specialDefense +
            evYield.speed

    fun evFor(stat: EvStat): Int =
        when (stat) {
            EvStat.ALL -> totalEvYield
            EvStat.HP -> evYield.hp
            EvStat.ATTACK -> evYield.attack
            EvStat.DEFENSE -> evYield.defense
            EvStat.SPECIAL_ATTACK -> evYield.specialAttack
            EvStat.SPECIAL_DEFENSE -> evYield.specialDefense
            EvStat.SPEED -> evYield.speed
        }
}

data class PokemonStats(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val speed: Int
)

data class PokemonEvYield(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val speed: Int
)

enum class EvStat(val label: String) {
    ALL("Todos"),
    HP("HP"),
    ATTACK("Attack"),
    DEFENSE("Defense"),
    SPECIAL_ATTACK("Sp. Atk"),
    SPECIAL_DEFENSE("Sp. Def"),
    SPEED("Speed")
}

fun PokemonEntity.toPokemon() = Pokemon(
    id = id,
    name = name,
    spriteUrl = spriteUrl,
    spriteBytes = spriteBytes,
    types = types.split(",").filter { it.isNotBlank() },
    stats = PokemonStats(
        hp = hp,
        attack = attack,
        defense = defense,
        specialAttack = specialAttack,
        specialDefense = specialDefense,
        speed = speed
    ),
    evYield = PokemonEvYield(
        hp = hpEv,
        attack = attackEv,
        defense = defenseEv,
        specialAttack = specialAttackEv,
        specialDefense = specialDefenseEv,
        speed = speedEv
    )
)

fun PokemonDetailDto.toEntity(spriteBytes: ByteArray? = null) = PokemonEntity(
    id = id,
    name = name,
    spriteUrl = sprites.other?.officialArtwork?.frontDefault ?: sprites.frontDefault,
    spriteBytes = spriteBytes,
    types = types.sortedBy { it.slot }.joinToString(",") { it.type.name },
    hp = baseStat("hp"),
    attack = baseStat("attack"),
    defense = baseStat("defense"),
    specialAttack = baseStat("special-attack"),
    specialDefense = baseStat("special-defense"),
    speed = baseStat("speed"),
    hpEv = effort("hp"),
    attackEv = effort("attack"),
    defenseEv = effort("defense"),
    specialAttackEv = effort("special-attack"),
    specialDefenseEv = effort("special-defense"),
    speedEv = effort("speed")
)

private fun PokemonDetailDto.baseStat(name: String): Int =
    stats.firstOrNull { it.stat.name == name }?.baseStat ?: 0

private fun PokemonDetailDto.effort(name: String): Int =
    stats.firstOrNull { it.stat.name == name }?.effort ?: 0
