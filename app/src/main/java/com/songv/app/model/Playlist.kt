package com.songv.app.model

/** Playlist criada pelo usuário: nome + ids de [Musica] na ordem escolhida. */
data class Playlist(
    val id: String,
    val nome: String,
    val musicasIds: List<String> = emptyList(),
)
