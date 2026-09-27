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
import com.songv.app.model.separarArtistas
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.ConnectException
import java.net.UnknownHostException

/**
 * Descobre qual letra mostrar para uma faixa e busca/importa letras quando falta.
 *
 * Prioridade: letra que o usuário buscou online ou importou → SYLT embutido → arquivo `.lrc`
 * com o mesmo nome ao lado do MP3 → USLT que na verdade contém LRC → USLT simples.
 */
class LetraRepository(private val context: Context) {

    sealed interface ResultadoBusca {
        /** [diferencaSeg]: diferença de duração da versão encontrada para a faixa local. */
        data class Encontrada(val letra: Letra, val diferencaSeg: Int? = null) : ResultadoBusca
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

    /** Resultado de uma pesquisa na LRCLIB: versões pontuadas (a melhor primeiro) ou o motivo da falha. */
    sealed interface Pesquisa {
        data class Ok(val candidatos: List<CandidatoLetra>) : Pesquisa
        data object SemConexao : Pesquisa
        data object Falha : Pesquisa
    }

    /** Busca automática: aplica a melhor versão encontrada, se houver uma confiável. */
    suspend fun buscarOnline(m: Musica): ResultadoBusca {
        return when (val p = pesquisar(BuscaLetra.consultas(m.titulo, m.artista), m)) {
            Pesquisa.SemConexao -> ResultadoBusca.SemConexao
            Pesquisa.Falha -> ResultadoBusca.Falha
            is Pesquisa.Ok -> {
                val melhor = p.candidatos.firstOrNull { it.aceitavel } ?: return ResultadoBusca.NaoEncontrada
                withContext(Dispatchers.IO) { aplicar(m, melhor) }
            }
        }
    }

    /**
     * Pesquisa na LRCLIB por várias estratégias ao mesmo tempo — assinatura exata, título+artista
     * e texto livre — e junta tudo numa lista pontuada. Se nada confiável aparecer, tenta ainda só
     * pelo título. Cada chamada tenta de novo quando a LRCLIB responde 503/429 (acontece bastante).
     */
    /** Pesquisa manual, com o título e o artista digitados pelo usuário. */
    suspend fun pesquisar(titulo: String, artista: String, m: Musica): Pesquisa =
        pesquisar(listOf(BuscaLetra.Consulta(titulo.trim(), artista.trim())), m)

    suspend fun pesquisar(consultas: List<BuscaLetra.Consulta>, m: Musica): Pesquisa = withContext(Dispatchers.IO) {
        if (!Rede.temConexao(context)) return@withContext Pesquisa.SemConexao
        val duracaoSeg = (m.duracaoMs / 1000).toInt()
        val encontrados = LinkedHashMap<Long, CandidatoLetra>()
        var respostas = 0
        var semRede = 0

        suspend fun rodada(urls: List<String>) {
            val resultados = coroutineScope {
                urls.distinct().map { url -> async { runCatching { Rede.requisitarComRetry(url) } } }.awaitAll()
            }
            resultados.forEach { r ->
                r.onSuccess { resp ->
                    respostas++
                    if (resp.codigo == 200) runCatching { lerCandidatos(resp.corpo) }.getOrNull()?.forEach { encontrados.putIfAbsent(it.id, it) }
                }.onFailure { e -> if (e is UnknownHostException || e is ConnectException) semRede++ }
            }
        }

        fun pontuados() = encontrados.values
            .map { BuscaLetra.pontuar(it, consultas, m.artistas, duracaoSeg) }
            .sortedWith(compareByDescending<CandidatoLetra> { it.aceitavel }.thenByDescending { it.pontuacao })

        rodada(
            consultas.flatMap { c ->
                val principal = separarArtistas(c.artista).firstOrNull().orEmpty().ifEmpty { c.artista }
                val t = enc(c.titulo)
                val a = enc(principal)
                listOfNotNull(
                    "$LRCLIB/search?track_name=$t&artist_name=$a".takeIf { principal.isNotBlank() },
                    "$LRCLIB/search?q=${enc("$principal ${c.titulo}".trim())}",
                    "$LRCLIB/get?track_name=$t&artist_name=$a&duration=$duracaoSeg".takeIf { duracaoSeg > 0 && principal.isNotBlank() },
                )
            },
        )
        if (pontuados().none { it.aceitavel }) {
            rodada(consultas.flatMap { c -> listOf("$LRCLIB/search?track_name=${enc(c.titulo)}", "$LRCLIB/search?q=${enc(c.titulo)}") })
        }
        when {
            respostas == 0 && semRede > 0 -> Pesquisa.SemConexao
            respostas == 0 -> Pesquisa.Falha
            else -> Pesquisa.Ok(pontuados().take(25))
        }
    }

    /** Salva a versão escolhida (automática ou pelo usuário) como a letra da faixa. */
    fun aplicar(m: Musica, c: CandidatoLetra): ResultadoBusca {
        if (c.instrumental && c.sincronizada == null && c.simples == null) return ResultadoBusca.Instrumental
        val texto = c.sincronizada ?: c.simples ?: return ResultadoBusca.NaoEncontrada
        val letra = deTexto(texto, FonteLetra.ONLINE)
        if (letra.tipo == TipoLetra.AUSENTE) return ResultadoBusca.NaoEncontrada
        salvar(m, texto, FonteLetra.ONLINE)
        return ResultadoBusca.Encontrada(letra, c.diferencaSeg)
    }

    private fun lerCandidatos(json: String): List<CandidatoLetra> {
        val bruto = json.trim()
        val lista = if (bruto.startsWith("[")) {
            JSONArray(bruto).let { a -> List(a.length()) { a.getJSONObject(it) } }
        } else {
            listOf(JSONObject(bruto))
        }
        return lista.mapNotNull { o ->
            val id = o.optLong("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
            CandidatoLetra(
                id = id,
                faixa = o.optString("trackName"),
                artista = o.optString("artistName"),
                album = if (o.isNull("albumName")) "" else o.optString("albumName"),
                duracaoSeg = o.optDouble("duration", 0.0),
                sincronizada = o.textoOuNull("syncedLyrics"),
                simples = o.textoOuNull("plainLyrics"),
                instrumental = o.optBoolean("instrumental"),
            )
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

        /** Título pronto para buscar (sem "(Official Video)", "Artista - ", "| Legendado", "feat."…). */
        fun limparTitulo(titulo: String): String = BuscaLetra.consulta(titulo, "").titulo

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        private fun JSONObject.textoOuNull(chave: String): String? =
            if (isNull(chave)) null else optString(chave).takeIf { it.isNotBlank() }
    }
}
