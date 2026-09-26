package com.songv.app.id3

/** Uma linha do frame SYLT (letra sincronizada), com o tempo em milissegundos. */
data class LinhaSylt(val texto: String, val tempoMs: Long)

/**
 * Tags lidas de uma tag ID3v2. A capa não vem aqui: é pesada e só é lida sob demanda por
 * [Id3Parser.lerCapa] — [temCapa] apenas diz se existe um frame APIC/PIC.
 */
data class TagsId3(
    val titulo: String? = null,
    val artista: String? = null,
    val album: String? = null,
    val artistaAlbum: String? = null,
    val ano: Int? = null,
    val faixa: Int? = null,
    val disco: Int? = null,
    val sylt: List<LinhaSylt>? = null,
    val uslt: String? = null,
    val temCapa: Boolean = false,
)
