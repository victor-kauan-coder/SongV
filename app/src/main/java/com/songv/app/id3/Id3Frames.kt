package com.songv.app.id3

/** Resultado bruto da leitura de um frame TIT2/TPE1 (texto simples). */
data class FrameTexto(val valor: String)

/** Resultado bruto da leitura do frame APIC (capa do álbum). */
data class FrameApic(
    val mime: String,
    val tipo: Int,
    val descricao: String,
    val dados: ByteArray
)

/** Uma linha sincronizada dentro de um frame SYLT. */
data class LinhaSylt(val texto: String, val tempoMs: Long)

/** Resultado bruto da leitura do frame SYLT (letra sincronizada). */
data class FrameSylt(
    val idioma: String,
    val formatoTempo: Int, // 1 = frames MPEG, 2 = milissegundos (é o que o servidor grava)
    val tipoConteudo: Int, // 1 = lyrics
    val linhas: List<LinhaSylt>
)

/** Resultado bruto da leitura do frame USLT (letra simples, sem tempo). */
data class FrameUslt(
    val idioma: String,
    val descricao: String,
    val texto: String
)

/** Tags agregadas que o parser devolve depois de ler o arquivo inteiro. */
data class TagsId3(
    val titulo: String?,
    val artista: String?,
    val capa: FrameApic?,
    val sylt: FrameSylt?,
    val uslt: FrameUslt?
)
