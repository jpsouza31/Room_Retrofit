package com.app.room_retrofit.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PokemonListResponse(
    val count: Int,
    val results: List<PokemonListItemDto>
)

data class PokemonListItemDto(
    val name: String,
    val url: String
)

data class PokemonDetailDto(
    val id: Int,
    val name: String,
    val sprites: PokemonSpritesDto,
    val stats: List<PokemonStatSlotDto>,
    val types: List<PokemonTypeSlotDto>
)

data class PokemonSpritesDto(
    @SerializedName("front_default") val frontDefault: String?,
    val other: PokemonOtherSpritesDto?
)

data class PokemonOtherSpritesDto(
    @SerializedName("official-artwork") val officialArtwork: PokemonOfficialArtworkDto?
)

data class PokemonOfficialArtworkDto(
    @SerializedName("front_default") val frontDefault: String?
)

data class PokemonStatSlotDto(
    @SerializedName("base_stat") val baseStat: Int,
    val effort: Int,
    val stat: NamedApiResourceDto
)

data class PokemonTypeSlotDto(
    val slot: Int,
    val type: NamedApiResourceDto
)

data class NamedApiResourceDto(
    val name: String,
    val url: String
)
