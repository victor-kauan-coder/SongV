package com.songv.app.letra

import android.content.Context
import android.net.Uri
import com.songv.app.id3.Id3Parser
import com.songv.app.model.FonteLetra
import com.songv.app.model.Letra
import com.songv.app.model.LinhaLetra
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Descobre qual letra mostrar para uma faixa e busca/importa letras quando falta.
 *
 * Prioridade: letra que o usuário buscou online ou importou → SYLT embutido → arquivo `.lrc`
 * com o mesmo nome ao lado do MP3 → USLT que na verdade contém LRC → USLT simples.
 */
class LetraRepository(private val context: Context) {

    sealed interface ResultadoBusca {
        data class Encontrada(val letra: Letra) : ResultadoBusca
        data object Instrumental : ResultadoBusca
        data object NaoEncontrada : ResultadoBusca
        data object SemConexao : ResultadoBusca
        data object Falha : ResultadoBusca
    }

    private val pasta = File(context.filesDir, "letras")

    private fun arquivoSalvo(m: Musica) = File(pasta, "${m.id}.lrc")
    private fun arquivoOrigem(m: Musica) = File(pasta, "${m.id}.origem")

    fun temLetraSalva(m: Musica) = arquivoSalvo(m).exists()

    suspend fun carregar(m: Musica): Letra = withContext(Dispatchers.IO) {
        lerSalva(m)?.let { return@withContext it }

        val tags = if (m.ehMp3) Id3Parser.lerTags(m.caminho) else null
        tags?.sylt?.let { sylt ->
            val linhas = normalizar(sylt.map { LinhaLetra(it.texto, it.tempoMs) })
            if (linhas.isNotEmpty()) return@withContext Letra(linhas, TipoLetra.SINCRONIZADA, FonteLetra.EMBUTIDA)
        }

        // .lrc ao lado do arquivo. No Android 11+ o acesso a arquivos que não são mídia costuma
        // ser negado; nesse caso simplesmente não há .lrc e seguimos para o USLT.
        runCatching {
            val lrc = File(m.caminho.substringBeforeLast('.') + ".lrc")
            if (lrc.canRead()) Lrc.parse(lrc.readText()).takeIf { it.isNotEmpty() } else null
        }.getOrNull()?.let { return@withContext Letra(it, TipoLetra.SINCRONIZADA, FonteLetra.ARQUIVO_LRC) }

        tags?.uslt?.let { texto -> return@withContext deTexto(texto, FonteLetra.EMBUTIDA) }
        Letra.AUSENTE
    }

    /** Busca na LRCLIB: primeiro pela assinatura exata (título/artista/álbum/duração), depois pela busca. */
    suspend fun buscarOnline(m: Musica): ResultadoBusca = withContext(Dispatchers.IO) {
        if (!Rede.temConexao(context)) return@withContext ResultadoBusca.SemConexao
        try {
            val titulo = limparTitulo(m.titulo)
            val artista = m.artistas.first()
            val duracaoSeg = (m.duracaoMs / 1000).toInt()

            val exata = Rede.requisitar(
                "$LRCLIB/get?track_name=${enc(titulo)}&artist_name=${enc(artista)}" +
                    (if (m.album.isNotBlank()) "&album_name=${enc(m.album)}" else "") +
                    (if (duracaoSeg > 0) "&duration=$duracaoSeg" else ""),
            )
            val candidato: JSONObject? = if (exata.codigo == 200) {
                JSONObject(exata.corpo)
            } else {
                val busca = Rede.requisitar("$LRCLIB/search?track_name=${enc(titulo)}&artist_name=${enc(artista)}")
                if (busca.codigo != 200) return@withContext ResultadoBusca.Falha
                melhorResultado(JSONArray(busca.corpo), duracaoSeg)
            }
            if (candidato == null) return@withContext ResultadoBusca.NaoEncontrada
            if (candidato.optBoolean("instrumental")) return@withContext ResultadoBusca.Instrumental

            val sincronizada = candidato.textoOuNull("syncedLyrics")
            val simples = candidato.textoOuNull("plainLyrics")
            val texto = sincronizada ?: simples ?: return@withContext ResultadoBusca.NaoEncontrada
            val letra = deTexto(texto, FonteLetra.ONLINE)
            if (letra.tipo == TipoLetra.AUSENTE) return@withContext ResultadoBusca.NaoEncontrada
            salvar(m, texto, FonteLetra.ONLINE)
            ResultadoBusca.Encontrada(letra)
        } catch (_: Exception) {
            ResultadoBusca.Falha
        }
    }

    /** Importa um .lrc (ou .txt) escolhido pelo usuário no seletor de arquivos do sistema. */
    suspend fun importar(m: Musica, uri: Uri): Letra? = withContext(Dispatchers.IO) {
        val texto = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()?.removePrefix("﻿") ?: return@withContext null
        val letra = deTexto(texto, FonteLetra.IMPORTADA)
        if (letra.tipo == TipoLetra.AUSENTE) return@withContext null
        salvar(m, texto, FonteLetra.IMPORTADA)
        letra
    }

    /** Apaga a letra baixada/importada, voltando para a embutida no arquivo. */
    suspend fun removerSalva(m: Musica) = withContext(Dispatchers.IO) {
        arquivoSalvo(m).delete()
        arquivoOrigem(m).delete()
    }

    private fun salvar(m: Musica, texto: String, fonte: FonteLetra) {
        pasta.mkdirs()
        arquivoSalvo(m).writeText(texto)
        arquivoOrigem(m).writeText(fonte.name)
    }

    private fun lerSalva(m: Musica): Letra? {
        val arquivo = arquivoSalvo(m)
        if (!arquivo.exists()) return null
        val fonte = runCatching { FonteLetra.valueOf(arquivoOrigem(m).readText().trim()) }.getOrDefault(FonteLetra.ONLINE)
        return runCatching { deTexto(arquivo.readText(), fonte) }.getOrNull()?.takeIf { it.tipo != TipoLetra.AUSENTE }
    }

    private fun melhorResultado(resultados: JSONArray, duracaoSeg: Int): JSONObject? {
        val lista = List(resultados.length()) { resultados.getJSONObject(it) }
        fun diferenca(o: JSONObject) = if (duracaoSeg > 0) abs(o.optDouble("duration", 0.0) - duracaoSeg) else 0.0
        val proximos = lista.filter { diferenca(it) <= 4 }.ifEmpty { if (duracaoSeg > 0) emptyList() else lista }
        return proximos.sortedWith(
            compareByDescending<JSONObject> { it.textoOuNull("syncedLyrics") != null }.thenBy { diferenca(it) },
        ).firstOrNull()
    }

    companion object {
        private const val LRCLIB = "https://lrclib.net/api"

        /** Texto de letra (LRC ou simples) → [Letra]. */
        fun deTexto(texto: String, fonte: FonteLetra): Letra {
            if (Lrc.ehLrc(texto)) {
                val linhas = Lrc.parse(texto)
                if (linhas.isNotEmpty()) return Letra(linhas, TipoLetra.SINCRONIZADA, fonte)
            }
            val linhas = texto.lines().map { it.trim() }
                .dropWhile { it.isEmpty() }
                .map { LinhaLetra(it, null) }
            return if (linhas.any { it.texto.isNotEmpty() }) Letra(linhas, TipoLetra.SIMPLES, fonte) else Letra.AUSENTE
        }

        /** Tira "(Official Video)", "[Lyrics]", "- Remastered 2011", "(feat. X)" do título antes de buscar. */
        fun limparTitulo(titulo: String): String = titulo
            .replace(Regex("""\s*[(\[][^)\]]*(official|video|lyric|audio|visualizer|remaster|feat\.?|ft\.)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+.*remaster.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { titulo }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        private fun JSONObject.textoOuNull(chave: String): String? =
            if (isNull(chave)) null else optString(chave).takeIf { it.isNotBlank() }
    }
}
