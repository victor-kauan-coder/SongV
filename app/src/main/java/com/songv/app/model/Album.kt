package com.songv.app.model

/** Álbum montado a partir das tags das faixas (não existe arquivo de álbum no disco). */
data class Album(
    val chave: String,
    val titulo: String,
    val artista: String,
    val ano: Int?,
    val musicas: List<Musica>,
) {
    /** Faixa usada como capa: a primeira que tem imagem embutida. */
    val faixaDaCapa: Musica get() = musicas.firstOrNull { it.temCapa } ?: musicas.first()
    val duracaoMs: Long get() = musicas.sumOf { it.duracaoMs }
}

data class Artista(
    val nome: String,
    val musicas: List<Musica>,
    val albuns: List<Album>,
) {
    val faixaDaCapa: Musica get() = musicas.firstOrNull { it.temCapa } ?: musicas.first()
}
