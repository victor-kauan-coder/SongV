package com.songv.app.letra

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/**
 * Tradução da letra, linha a linha alinhada com o original, mais a romanização quando o idioma
 * de origem não usa alfabeto latino (coreano, japonês, russo…).
 *
 * Usa o endpoint público do Google Tradutor. A versão anterior disparava uma requisição por
 * linha, todas em paralelo: numa letra de 60 linhas isso estourava o limite do serviço (HTTP 429)
 * e bastava uma linha falhar para a tradução inteira falhar. Agora a letra vai em lotes, com as
 * linhas separadas por quebra de linha — o serviço preserva as quebras, então dá para realinhar.
 * Se algum lote voltar desalinhado, só aquele lote é refeito linha a linha, com poucas
 * requisições simultâneas e nova tentativa em caso de limite de taxa.
 */
class TradutorLetra(private val context: Context) {

    data class Traducao(
        val idioma: String,
        /** Mesmo tamanho da letra; null nas linhas vazias (pausas). */
        val linhas: List<String?>,
        val romanizacao: List<String?>?,
        val idiomaOrigem: String?,
    )

    sealed interface Resultado {
        data class Sucesso(val traducao: Traducao) : Resultado
        data class MesmoIdioma(val idioma: String) : Resultado
        data object SemConexao : Resultado
        data object Falha : Resultado
    }

    private val pastaCache = File(context.filesDir, "traducoes")

    suspend fun traduzir(linhas: List<String>, destino: String): Resultado = withContext(Dispatchers.IO) {
        val indices = linhas.indices.filter { linhas[it].isNotBlank() }
        if (indices.isEmpty()) return@withContext Resultado.Falha
        val textos = indices.map { linhas[it].trim() }

        val arquivoCache = File(pastaCache, "${destino}_${textos.joinToString("\n").hashCode().toUInt()}.json")
        lerCache(arquivoCache, textos.size)?.let { return@withContext montar(it, linhas.size, indices, destino) }

        if (!Rede.temConexao(context)) return@withContext Resultado.SemConexao

        try {
            val blocos = agruparEmBlocos(textos)
            val traducoes = ArrayList<String>(textos.size)
            val romanizacoes = ArrayList<String?>(textos.size)
            var origem: String? = null
            for (bloco in blocos) {
                val r = traduzirBloco(bloco, destino)
                origem = origem ?: r.origem
                traducoes += r.linhas
                romanizacoes += r.romanizacao
            }
            if (origem != null && mesmoIdioma(origem, destino)) return@withContext Resultado.MesmoIdioma(destino)

            val bruto = Bruto(traducoes, romanizacoes.takeIf { l -> l.any { !it.isNullOrBlank() } }, origem)
            salvarCache(arquivoCache, bruto)
            montar(bruto, linhas.size, indices, destino)
        } catch (_: Exception) {
            Resultado.Falha
        }
    }

    // ---- Lotes ----

    private class Bruto(val linhas: List<String>, val romanizacao: List<String?>?, val origem: String?)
    private class RespostaBloco(val linhas: List<String>, val romanizacao: List<String?>, val origem: String?)

    private fun agruparEmBlocos(textos: List<String>): List<List<String>> {
        val blocos = mutableListOf<MutableList<String>>()
        var tamanho = 0
        for (t in textos) {
            if (blocos.isEmpty() || tamanho + t.length > MAX_CARACTERES_BLOCO) {
                blocos += mutableListOf<String>()
                tamanho = 0
            }
            blocos.last() += t
            tamanho += t.length + 1
        }
        return blocos
    }

    private suspend fun traduzirBloco(bloco: List<String>, destino: String): RespostaBloco {
        val resposta = interpretarResposta(chamar(bloco.joinToString("\n"), destino))
        val partes = resposta.traducao.split('\n').map { it.trim() }
        val roman = resposta.romanizacao?.split('\n')?.map { it.trim() }
        if (partes.size == bloco.size) {
            return RespostaBloco(partes, roman?.takeIf { it.size == bloco.size } ?: List(bloco.size) { null }, resposta.origem)
        }
        // Desalinhado: refaz esse bloco linha a linha, com no máximo 3 requisições por vez.
        val limite = Semaphore(3)
        val individuais = coroutineScope {
            bloco.map { linha -> async { limite.withPermit { interpretarResposta(chamar(linha, destino)) } } }.awaitAll()
        }
        return RespostaBloco(
            individuais.map { it.traducao.trim() },
            individuais.map { it.romanizacao?.trim() },
            resposta.origem,
        )
    }

    private suspend fun chamar(texto: String, destino: String): String {
        val url = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=auto&tl=$destino&dt=t&dt=rm&ie=UTF-8&oe=UTF-8"
        val corpo = "q=" + URLEncoder.encode(texto, "UTF-8")
        var espera = 800L
        repeat(3) { tentativa ->
            val r = Rede.requisitar(url, corpo)
            if (r.codigo == 200) return r.corpo
            if ((r.codigo == 429 || r.codigo >= 500) && tentativa < 2) {
                delay(espera)
                espera *= 2
            } else {
                error("HTTP ${r.codigo}")
            }
        }
        error("sem resposta")
    }

    private fun montar(bruto: Bruto, total: Int, indices: List<Int>, destino: String): Resultado {
        val linhas = arrayOfNulls<String>(total)
        val roman = bruto.romanizacao?.let { arrayOfNulls<String>(total) }
        indices.forEachIndexed { i, original ->
            linhas[original] = bruto.linhas.getOrNull(i)
            roman?.set(original, bruto.romanizacao.getOrNull(i))
        }
        return Resultado.Sucesso(Traducao(destino, linhas.toList(), roman?.toList(), bruto.origem))
    }

    // ---- Cache em disco (a mesma letra no mesmo idioma nunca é traduzida duas vezes) ----

    private fun lerCache(arquivo: File, esperado: Int): Bruto? = try {
        if (!arquivo.exists()) {
            null
        } else {
            val json = JSONObject(arquivo.readText())
            val trad = json.getJSONArray("linhas").let { a -> List(a.length()) { a.getString(it) } }
            val rom = json.optJSONArray("romanizacao")?.let { a -> List(a.length()) { if (a.isNull(it)) null else a.getString(it) } }
            val origem = if (json.isNull("origem")) null else json.optString("origem")
            Bruto(trad, rom, origem).takeIf { trad.size == esperado }
        }
    } catch (_: Exception) {
        null
    }

    private fun salvarCache(arquivo: File, bruto: Bruto) {
        runCatching {
            pastaCache.mkdirs()
            val json = JSONObject()
                .put("linhas", JSONArray(bruto.linhas))
                .put("romanizacao", bruto.romanizacao?.let { JSONArray(it) } ?: JSONObject.NULL)
                .put("origem", bruto.origem ?: JSONObject.NULL)
            arquivo.writeText(json.toString())
        }
    }

    companion object {
        private const val MAX_CARACTERES_BLOCO = 1800

        /** "zh-CN" e "zh" contam como o mesmo idioma; "pt" e "pt-PT" também. */
        fun mesmoIdioma(a: String, b: String) = a.substringBefore('-').equals(b.substringBefore('-'), ignoreCase = true)
    }
}

/** Resposta crua do endpoint: tradução concatenada, romanização da origem e idioma detectado. */
internal class RespostaGtx(val traducao: String, val romanizacao: String?, val origem: String?)

/**
 * O endpoint responde `[[["trad","orig",...],...,[null,null,null,"romanização"]],null,"en",...]`.
 * Atenção: `optString` do org.json devolve o texto "null" para JSON null — por isso o `isNull`.
 */
internal fun interpretarResposta(json: String): RespostaGtx {
    val raiz = JSONArray(json)
    val segmentos = raiz.optJSONArray(0) ?: JSONArray()
    val traducao = StringBuilder()
    var romanizacao: String? = null
    for (i in 0 until segmentos.length()) {
        val s = segmentos.optJSONArray(i) ?: continue
        if (!s.isNull(0)) {
            traducao.append(s.getString(0))
        } else if (s.length() > 3 && !s.isNull(3)) {
            romanizacao = s.getString(3)
        }
    }
    val origem = if (raiz.length() > 2 && !raiz.isNull(2)) raiz.getString(2) else null
    return RespostaGtx(traducao.toString(), romanizacao, origem)
}

data class IdiomaTraducao(val codigo: String, val nome: String)

val IDIOMAS_TRADUCAO = listOf(
    IdiomaTraducao("pt", "Português"),
    IdiomaTraducao("en", "Inglês"),
    IdiomaTraducao("es", "Espanhol"),
    IdiomaTraducao("fr", "Francês"),
    IdiomaTraducao("it", "Italiano"),
    IdiomaTraducao("de", "Alemão"),
    IdiomaTraducao("ja", "Japonês"),
    IdiomaTraducao("ko", "Coreano"),
    IdiomaTraducao("zh-CN", "Chinês"),
    IdiomaTraducao("ru", "Russo"),
    IdiomaTraducao("ar", "Árabe"),
    IdiomaTraducao("hi", "Hindi"),
    IdiomaTraducao("tr", "Turco"),
    IdiomaTraducao("nl", "Holandês"),
    IdiomaTraducao("pl", "Polonês"),
    IdiomaTraducao("sv", "Sueco"),
)

fun nomeIdioma(codigo: String?): String =
    IDIOMAS_TRADUCAO.firstOrNull { TradutorLetra.mesmoIdioma(it.codigo, codigo ?: "") }?.nome ?: (codigo ?: "?")
