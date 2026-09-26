package com.songv.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.songv.app.id3.Id3Parser
import com.songv.app.letra.Lrc
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Varre o MediaStore e lê as tags de cada faixa.
 *
 * Guarda um índice em disco (`biblioteca.json`): na próxima abertura só são relidas as faixas
 * novas ou modificadas, então a biblioteca aparece praticamente na hora. As tags ID3 têm
 * prioridade; as colunas do MediaStore servem de reserva (e cobrem M4A/FLAC/OGG, que o
 * ExoPlayer toca mas o parser ID3 não lê).
 */
class MusicaRepository(private val context: Context) {

    private val arquivoIndice = File(context.filesDir, "biblioteca.json")
    private val pastaLetrasSalvas = File(context.filesDir, "letras")

    private class Linha(
        val id: Long, val caminho: String, val nome: String, val pastaRelativa: String,
        val duracao: Long, val adicionada: Long, val modificada: Long, val tamanho: Long,
        val titulo: String?, val artista: String?, val album: String?, val artistaAlbum: String?,
        val ano: Int, val faixa: Int,
    )

    /**
     * @param pasta nome da pasta a considerar (ex.: "Music"); vazio = todas as pastas.
     * @param ignorarCurtas pula áudios com menos de 30 s (toques, áudios de mensagem…).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun varrer(
        pasta: String,
        ignorarCurtas: Boolean,
        aoProgredir: (lidas: Int, total: Int) -> Unit,
    ): List<Musica> = withContext(Dispatchers.IO) {
        val linhas = consultar().filter { l ->
            (pasta.isBlank() || pertenceAPasta(l, pasta)) && (!ignorarCurtas || l.duracao == 0L || l.duracao >= 30_000)
        }
        val indice = lerIndice()
        val lidas = AtomicInteger(0)
        val leitores = Dispatchers.IO.limitedParallelism(4)

        val musicas = coroutineScope {
            linhas.map { l ->
                async(leitores) {
                    val emCache = indice[l.caminho]?.takeIf { it.id == l.id.toString() && it.modificadoSeg == l.modificada && it.tamanhoBytes == l.tamanho }
                    val musica = emCache ?: runCatching { construir(l) }.onFailure {
                        Log.w(TAG, "Falha ao ler ${l.caminho}: ${it.message}")
                    }.getOrNull()
                    aoProgredir(lidas.incrementAndGet(), linhas.size)
                    musica?.let { comLetraSalva(it) }
                }
            }.awaitAll().filterNotNull()
        }
        salvarIndice(musicas)
        musicas
    }

    /** Pastas (primeiro nível, ex.: "Music", "Download") que contêm música — para o filtro nas configurações. */
    suspend fun pastasDisponiveis(): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        consultar().groupingBy { it.pastaRelativa.trim('/').substringBefore('/') }
            .eachCount()
            .filterKeys { it.isNotBlank() }
            .toList()
            .sortedByDescending { it.second }
    }

    private fun pertenceAPasta(l: Linha, pasta: String): Boolean =
        l.pastaRelativa.trim('/').substringBefore('/').equals(pasta, ignoreCase = true)

    private fun consultar(): List<Linha> {
        val colunas = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.TRACK,
        )
        // RELATIVE_PATH só existe a partir do Android 10 e ALBUM_ARTIST a partir do 11. Pedir uma
        // coluna inexistente derruba a consulta inteira — era por isso que a biblioteca não
        // carregava no Android 8 e 9.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) colunas += MediaStore.Audio.Media.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) colunas += MediaStore.Audio.Media.ALBUM_ARTIST

        val selecao = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} = 'audio/mpeg'"
        val resultado = mutableListOf<Linha>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, colunas.toTypedArray(), selecao, null, null,
        )?.use { c ->
            fun idx(nome: String) = c.getColumnIndex(nome)
            val iId = idx(MediaStore.Audio.Media._ID)
            val iDados = idx(MediaStore.Audio.Media.DATA)
            val iNome = idx(MediaStore.Audio.Media.DISPLAY_NAME)
            val iDur = idx(MediaStore.Audio.Media.DURATION)
            val iAdd = idx(MediaStore.Audio.Media.DATE_ADDED)
            val iMod = idx(MediaStore.Audio.Media.DATE_MODIFIED)
            val iTam = idx(MediaStore.Audio.Media.SIZE)
            val iTit = idx(MediaStore.Audio.Media.TITLE)
            val iArt = idx(MediaStore.Audio.Media.ARTIST)
            val iAlb = idx(MediaStore.Audio.Media.ALBUM)
            val iAno = idx(MediaStore.Audio.Media.YEAR)
            val iFaixa = idx(MediaStore.Audio.Media.TRACK)
            val iRel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) idx(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            val iArtAlb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) idx(MediaStore.Audio.Media.ALBUM_ARTIST) else -1

            while (c.moveToNext()) {
                val caminho = c.getString(iDados) ?: continue
                val relativa = if (iRel >= 0) c.getString(iRel).orEmpty() else relativaDoCaminho(caminho)
                resultado += Linha(
                    id = c.getLong(iId),
                    caminho = caminho,
                    nome = c.getString(iNome) ?: caminho.substringAfterLast('/'),
                    pastaRelativa = relativa,
                    duracao = c.getLong(iDur),
                    adicionada = c.getLong(iAdd),
                    modificada = c.getLong(iMod),
                    tamanho = c.getLong(iTam),
                    titulo = c.getString(iTit),
                    artista = c.getString(iArt)?.takeUnless { it == "<unknown>" },
                    // Sem tag de álbum, o MediaStore usa o nome da pasta — isso criaria álbuns falsos.
                    album = c.getString(iAlb)?.takeUnless { it == "<unknown>" || it == caminho.substringBeforeLast('/').substringAfterLast('/') },
                    artistaAlbum = if (iArtAlb >= 0) c.getString(iArtAlb) else null,
                    ano = c.getInt(iAno),
                    faixa = c.getInt(iFaixa),
                )
            }
        }
        return resultado
    }

    /**
     * "/storage/emulated/0/Music/Album/x.mp3" → "Music/Album/" e "/storage/ABCD-1234/Music/x.mp3"
     * (cartão SD) → "Music/" — o equivalente ao RELATIVE_PATH para Android 8 e 9.
     */
    private fun relativaDoCaminho(caminho: String): String {
        val partes = caminho.trim('/').split('/')
        val raiz = when {
            partes.getOrNull(0) == "storage" && partes.getOrNull(1) == "emulated" -> 3
            partes.getOrNull(0) == "storage" -> 2
            partes.getOrNull(0) == "sdcard" -> 1
            else -> 0
        }
        return partes.drop(raiz).dropLast(1).joinToString("/", postfix = "/")
    }

    private fun construir(l: Linha): Musica {
        val tags = if (l.caminho.endsWith(".mp3", ignoreCase = true)) Id3Parser.lerTags(l.caminho) else null
        val tipoLetra = when {
            tags?.sylt != null -> TipoLetra.SINCRONIZADA
            tags?.uslt != null && Lrc.ehLrc(tags.uslt) -> TipoLetra.SINCRONIZADA
            tags?.uslt != null -> TipoLetra.SIMPLES
            else -> TipoLetra.AUSENTE
        }
        val artista = tags?.artista ?: l.artista ?: "Artista desconhecido"
        // MediaStore guarda a faixa como disco*1000 + faixa.
        val faixaMs = l.faixa.takeIf { it > 0 }?.rem(1000)?.takeIf { it > 0 }
        return Musica(
            id = l.id.toString(),
            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, l.id),
            caminho = l.caminho,
            titulo = tags?.titulo ?: l.titulo?.takeIf { it.isNotBlank() } ?: l.nome.substringBeforeLast('.'),
            artista = artista,
            album = tags?.album ?: l.album.orEmpty(),
            artistaAlbum = tags?.artistaAlbum ?: l.artistaAlbum.orEmpty(),
            ano = tags?.ano ?: l.ano.takeIf { it > 0 },
            faixa = tags?.faixa ?: faixaMs,
            disco = tags?.disco ?: l.faixa.takeIf { it >= 1000 }?.div(1000),
            duracaoMs = l.duracao,
            adicionadaEmSeg = l.adicionada,
            modificadoSeg = l.modificada,
            tamanhoBytes = l.tamanho,
            // Fora do MP3 não sabemos sem abrir o arquivo; o CapaRepository tenta e lembra o resultado.
            temCapa = tags?.temCapa ?: true,
            tipoLetra = tipoLetra,
        )
    }

    /** Letras baixadas/importadas pelo usuário contam como sincronizadas/simples na biblioteca. */
    private fun comLetraSalva(m: Musica): Musica {
        val salva = File(pastaLetrasSalvas, "${m.id}.lrc")
        if (!salva.exists()) return m
        val tipo = runCatching { if (Lrc.ehLrc(salva.readText())) TipoLetra.SINCRONIZADA else TipoLetra.SIMPLES }.getOrNull()
        return if (tipo != null && tipo != m.tipoLetra) m.copy(tipoLetra = tipo) else m
    }

    // ---- Índice em disco ----

    private fun lerIndice(): Map<String, Musica> = try {
        if (!arquivoIndice.exists()) {
            emptyMap()
        } else {
            val raiz = JSONObject(arquivoIndice.readText())
            if (raiz.optInt("versao") != VERSAO_INDICE) {
                emptyMap()
            } else {
                val lista = raiz.getJSONArray("musicas")
                (0 until lista.length()).associate { i ->
                    val o = lista.getJSONObject(i)
                    val id = o.getString("id")
                    o.getString("caminho") to Musica(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong()),
                        caminho = o.getString("caminho"),
                        titulo = o.getString("titulo"),
                        artista = o.getString("artista"),
                        album = o.getString("album"),
                        artistaAlbum = o.getString("artistaAlbum"),
                        ano = o.optInt("ano").takeIf { it > 0 },
                        faixa = o.optInt("faixa").takeIf { it > 0 },
                        disco = o.optInt("disco").takeIf { it > 0 },
                        duracaoMs = o.getLong("duracao"),
                        adicionadaEmSeg = o.getLong("adicionada"),
                        modificadoSeg = o.getLong("modificada"),
                        tamanhoBytes = o.getLong("tamanho"),
                        temCapa = o.getBoolean("capa"),
                        tipoLetra = TipoLetra.valueOf(o.getString("letra")),
                    )
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Índice da biblioteca ilegível, relendo tudo: ${e.message}")
        emptyMap()
    }

    private fun salvarIndice(musicas: List<Musica>) {
        runCatching {
            val lista = JSONArray()
            musicas.forEach { m ->
                lista.put(
                    JSONObject()
                        .put("id", m.id).put("caminho", m.caminho).put("titulo", m.titulo)
                        .put("artista", m.artista).put("album", m.album).put("artistaAlbum", m.artistaAlbum)
                        .put("ano", m.ano ?: 0).put("faixa", m.faixa ?: 0).put("disco", m.disco ?: 0)
                        .put("duracao", m.duracaoMs).put("adicionada", m.adicionadaEmSeg)
                        .put("modificada", m.modificadoSeg).put("tamanho", m.tamanhoBytes)
                        .put("capa", m.temCapa).put("letra", m.tipoLetra.name),
                )
            }
            val temporario = File(arquivoIndice.parentFile, "biblioteca.json.tmp")
            temporario.writeText(JSONObject().put("versao", VERSAO_INDICE).put("musicas", lista).toString())
            temporario.renameTo(arquivoIndice)
        }
    }

    companion object {
        private const val TAG = "MusicaRepository"
        private const val VERSAO_INDICE = 2
        const val PASTA_PADRAO = "Music"
    }
}
