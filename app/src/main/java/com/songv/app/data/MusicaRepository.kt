package com.songv.app.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import com.songv.app.id3.Id3Parser
import com.songv.app.model.LinhaLetra
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Varre o MediaStore em busca de arquivos MP3, filtra pela pasta configurada
 * (padrão "Music"), e para cada um lê as tags ID3 com o [Id3Parser] manual
 * (necessário para extrair SYLT, que bibliotecas prontas não suportam).
 */
class MusicaRepository(private val context: Context) {

    companion object {
        private const val TAG = "MusicaRepository"
        const val PASTA_PADRAO = "Music"

        /**
         * Lado máximo (em pixels) para as capas decodificadas em memória. As capas de MP3 costumam
         * vir em alta resolução (1000px+), mas o app só as exibe como thumbnails pequenos — manter
         * o bitmap original inteiro por música, multiplicado pela biblioteca toda, é o que causava
         * o app travar/consumir memória demais ao carregar. 300px é mais que suficiente mesmo para
         * o círculo de artista ou a capa grande da tela do player.
         */
        private const val TAMANHO_MAXIMO_CAPA_PX = 300
    }

    /**
     * Decodifica bytes de imagem (capa embutida no MP3) já reduzida para no máximo
     * [tamanhoMaximoPx] de lado, sem nunca alocar o bitmap na resolução original.
     * Usa a técnica padrão do Android: primeiro lê só as dimensões (inJustDecodeBounds),
     * calcula a potência de 2 mais próxima que atende ao tamanho alvo, e só então decodifica
     * de fato já reduzido.
     */
    private fun decodificarCapaReduzida(dados: ByteArray, tamanhoMaximoPx: Int): Bitmap? {
        val opcoesMedida = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(dados, 0, dados.size, opcoesMedida)

        var inSampleSize = 1
        var largura = opcoesMedida.outWidth
        var altura = opcoesMedida.outHeight
        while (largura / 2 >= tamanhoMaximoPx || altura / 2 >= tamanhoMaximoPx) {
            largura /= 2
            altura /= 2
            inSampleSize *= 2
        }

        val opcoesFinais = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return BitmapFactory.decodeByteArray(dados, 0, dados.size, opcoesFinais)
    }

    /**
     * Varre o armazenamento em busca de MP3s dentro de [pastaFiltro] (relativa, ex: "Music").
     * Passe null para varrer todas as pastas.
     */
    suspend fun varrer(pastaFiltro: String? = PASTA_PADRAO): List<Musica> = withContext(Dispatchers.IO) {
        val resultado = mutableListOf<Musica>()

        val projecao = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA, // caminho absoluto no disco
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selecao = "${MediaStore.Audio.Media.MIME_TYPE} = ?"
        val selecaoArgs = arrayOf("audio/mpeg")

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projecao,
            selecao,
            selecaoArgs,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nomeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val caminhoRelCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val duracaoCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                try {
                    val id = c.getLong(idCol)
                    val caminho = c.getString(dataCol) ?: continue
                    val nomeArquivo = c.getString(nomeCol) ?: caminho.substringAfterLast('/')
                    val caminhoRelativo = c.getString(caminhoRelCol) ?: ""
                    val duracaoMediaStore = c.getLong(duracaoCol)

                    // Filtra pela pasta configurada, se houver
                    if (pastaFiltro != null && !caminhoRelativo.contains(pastaFiltro, ignoreCase = true)) {
                        continue
                    }

                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val musica = construirMusica(id.toString(), uri, caminho, nomeArquivo, duracaoMediaStore)
                    resultado.add(musica)
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao processar faixa, pulando: ${e.message}")
                }
            }
        }

        resultado
    }

    private fun construirMusica(
        id: String,
        uri: android.net.Uri,
        caminho: String,
        nomeArquivo: String,
        duracaoMediaStore: Long
    ): Musica {
        val tags = Id3Parser.parse(caminho)

        val capaBitmap: Bitmap? = tags.capa?.let { apic ->
            try {
                decodificarCapaReduzida(apic.dados, TAMANHO_MAXIMO_CAPA_PX)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao decodificar capa de $nomeArquivo: ${e.message}")
                null
            }
        }

        val duracaoMs = if (duracaoMediaStore > 0) {
            duracaoMediaStore
        } else {
            obterDuracaoViaRetriever(caminho)
        }

        val (letra, tipoLetra) = when {
            tags.sylt != null && tags.sylt.linhas.isNotEmpty() -> {
                tags.sylt.linhas.map { LinhaLetra(it.texto, it.tempoMs) } to TipoLetra.SINCRONIZADA
            }
            tags.uslt != null && tags.uslt.texto.isNotBlank() -> {
                tags.uslt.texto.lines()
                    .filter { it.isNotBlank() }
                    .map { LinhaLetra(it.trim(), null) } to TipoLetra.SIMPLES
            }
            else -> emptyList<LinhaLetra>() to TipoLetra.AUSENTE
        }

        return Musica(
            id = id,
            uri = uri,
            caminhoArquivo = caminho,
            titulo = tags.titulo?.takeIf { it.isNotBlank() } ?: nomeArquivo.substringBeforeLast('.'),
            artista = tags.artista?.takeIf { it.isNotBlank() } ?: "Artista desconhecido",
            capa = capaBitmap,
            duracaoMs = duracaoMs,
            letra = letra,
            tipoLetra = tipoLetra
        )
    }

    private fun obterDuracaoViaRetriever(caminho: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(caminho)
            val duracao = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duracao?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
