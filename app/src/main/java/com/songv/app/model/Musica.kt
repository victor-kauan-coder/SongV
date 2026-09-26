package com.songv.app.model

import android.graphics.Bitmap
import android.net.Uri

/**
 * Representa uma música encontrada na varredura local, já com as tags ID3 extraídas.
 *
 * @param id identificador estável (baseado no caminho do arquivo), usado como key em listas Compose
 * @param uri Uri de conteúdo (content://) usada pelo ExoPlayer e pelo MediaStore
 * @param caminhoArquivo caminho absoluto no disco, usado pelo parser ID3 manual
 * @param titulo de TIT2, ou o nome do arquivo se a tag não existir
 * @param artista de TPE1, ou "Artista desconhecido" se a tag não existir
 * @param capa Bitmap decodificado do frame APIC, ou null se não houver capa embutida
 * @param duracaoMs duração total da faixa, obtida via MediaMetadataRetriever
 * @param letra lista de linhas (com ou sem timestamp, dependendo de [tipoLetra])
 * @param tipoLetra indica se a letra é sincronizada, simples ou ausente
 */
data class Musica(
    val id: String,
    val uri: Uri,
    val caminhoArquivo: String,
    val titulo: String,
    val artista: String,
    val capa: Bitmap?,
    val duracaoMs: Long,
    val letra: List<LinhaLetra>,
    val tipoLetra: TipoLetra
)
