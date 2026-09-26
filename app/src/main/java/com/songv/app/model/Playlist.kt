package com.songv.app.model

/**
 * Representa uma playlist criada pelo usuário: um nome e uma lista ordenada de ids de [Musica].
 *
 * @param id identificador estável (timestamp de criação em string), usado como key em listas Compose
 * @param nome nome escolhido pelo usuário para a playlist
 * @param musicasIds ids das músicas na playlist, na ordem de inserção
 */
data class Playlist(
    val id: String,
    val nome: String,
    val musicasIds: List<String> = emptyList()
)
