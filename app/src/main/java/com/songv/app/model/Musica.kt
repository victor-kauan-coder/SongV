package com.songv.app.model

import android.net.Uri
import com.songv.app.data.normalizarBusca

/**
 * Uma faixa da biblioteca local, com as tags já lidas (ID3 no MP3, MediaStore como reserva).
 *
 * A capa não fica aqui: bitmaps de centenas de faixas em memória eram o que deixava o app
 * pesado. Ela é carregada sob demanda pelo [com.songv.app.data.CapaRepository].
 *
 * @param id `_ID` do MediaStore em texto — é a chave usada em favoritos, playlists e histórico.
 * @param modificadoSeg data de modificação do arquivo; invalida caches (capa, índice da biblioteca).
 */
data class Musica(
    val id: String,
    val uri: Uri,
    val caminho: String,
    val titulo: String,
    val artista: String,
    val album: String,
    val artistaAlbum: String,
    val ano: Int?,
    val faixa: Int?,
    val disco: Int?,
    val duracaoMs: Long,
    val adicionadaEmSeg: Long,
    val modificadoSeg: Long,
    val tamanhoBytes: Long,
    val temCapa: Boolean,
    val tipoLetra: TipoLetra,
) {
    val pasta: String get() = caminho.substringBeforeLast('/', "")
    val ehMp3: Boolean get() = caminho.endsWith(".mp3", ignoreCase = true)

    /** Texto sem acentos e em minúsculas, para a busca ("voce" encontra "Você"). */
    val chaveBusca: String by lazy { normalizarBusca("$titulo $artista $album") }

    /** Artistas individuais de uma faixa com participação ("A feat. B", "A, B", "A & B"). */
    val artistas: List<String> by lazy { separarArtistas(artista) }
}

private val SEPARADORES_ARTISTA = Regex("""\s*(?:,|;|/|\s&\s|\sfeat\.?\s|\sft\.?\s|\sfeaturing\s)\s*""", RegexOption.IGNORE_CASE)

fun separarArtistas(texto: String): List<String> =
    texto.split(SEPARADORES_ARTISTA).map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf(texto) }
