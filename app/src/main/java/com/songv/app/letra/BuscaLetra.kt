package com.songv.app.letra

import com.songv.app.data.normalizarBusca
import kotlin.math.abs

/** Uma versão de letra encontrada na LRCLIB, já pontuada contra a faixa que está tocando. */
data class CandidatoLetra(
    val id: Long,
    val faixa: String,
    val artista: String,
    val album: String,
    val duracaoSeg: Double,
    val sincronizada: String?,
    val simples: String?,
    val instrumental: Boolean,
    val pontuacao: Double = 0.0,
    /** Diferença de duração para a faixa local, em segundos (null quando uma das duas é desconhecida). */
    val diferencaSeg: Int? = null,
    val aceitavel: Boolean = false,
) {
    val temSincronia: Boolean get() = sincronizada != null
}

/**
 * Como a letra certa é escolhida entre os resultados da LRCLIB.
 *
 * A versão anterior só aceitava resultados com até 4 s de diferença de duração. MP3 baixados de
 * clipe (introdução, final estendido) costumam ter 10–20 s a mais, e a letra "não era encontrada"
 * mesmo com dezenas de resultados corretos. Agora a duração é só um dos critérios: título e
 * artista pesam mais, e letra sincronizada ganha um bônus.
 */
object BuscaLetra {

    data class Consulta(val titulo: String, val artista: String)

    private val RUIDO = listOf(
        "official", "oficial", "video", "clipe", "clip", "lyric", "lyrics", "letra", "legendado", "legenda",
        "traducao", "audio", "remasterizado", "remasterizada", "visualizer", "visualiser", "remaster", "remastered", "hd", "hq", "4k", "1080p",
        "720p", "mv", "m/v", "feat", "ft", "part", "with", "prod", "ao vivo", "live", "acustico", "acoustic",
        "version", "versao", "color coded", "performance",
    )
    private val TRECHO_ENTRE_PARENTESES = Regex("""\s*[(\[【{][^)\]】}]*[)\]】}]""")
    private val SEPARADOR_ARTISTA_TITULO = Regex("""\s+[-–—]\s+""")
    private val PARTICIPACAO_SOLTA = Regex("""\s+(feat\.?|ft\.?|part\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val SUFIXO_CANAL = Regex("""(\s*-\s*topic|vevo|\s+official)\s*$""", RegexOption.IGNORE_CASE)

    private val CAMEL_CASE = Regex("""(?<=\p{Ll})(?=\p{Lu})""")

    /**
     * As interpretações possíveis das tags para buscar. A primeira é a principal; quando o título
     * tem "X - Y" e X não bate com o artista da tag (canal do YouTube, "Vários"), entra também a
     * leitura "X é o artista, Y é a música".
     */
    fun consultas(tituloTag: String, artistaTag: String): List<Consulta> {
        val principal = consulta(tituloTag, artistaTag)
        val partes = tituloTag.split(SEPARADOR_ARTISTA_TITULO).filterNot { ehSoRuido(it) }
        val alternativa = if (partes.size >= 2 && !pareceArtista(partes[0], principal.artista)) {
            consulta(partes.drop(1).joinToString(" - "), partes[0])
        } else {
            null
        }
        return listOfNotNull(principal, alternativa?.takeIf { it != principal })
    }

    /** Título e artista prontos para buscar, a partir das tags (que muitas vezes vêm do YouTube). */
    fun consulta(tituloTag: String, artistaTag: String): Consulta {
        var artista = SUFIXO_CANAL.replace(artistaTag.trim(), "").trim()
        // "ImagineDragons" (nome de canal) → "Imagine Dragons"
        if (' ' !in artista && CAMEL_CASE.containsMatchIn(artista)) artista = artista.replace(CAMEL_CASE, " ")
        var titulo = tituloTag.trim()

        // "Artista - Música - Clipe Oficial" → "Música": tira as partes que são o artista ou ruído.
        val partes = titulo.split(SEPARADOR_ARTISTA_TITULO)
        if (partes.size >= 2) {
            val uteis = partes.filterNot { pareceArtista(it, artista) || ehSoRuido(it) }
            if (uteis.isNotEmpty()) titulo = uteis.joinToString(" - ")
        }
        titulo = titulo.substringBefore(" | ").substringBefore(" ｜ ")
        titulo = TRECHO_ENTRE_PARENTESES.replace(titulo) { if (ehRuido(it.value)) "" else it.value }
        titulo = PARTICIPACAO_SOLTA.replace(titulo, "")
        titulo = titulo.trim().trim('"', '\'', '“', '”').trim()
        return Consulta(titulo.ifEmpty { tituloTag.trim() }, artista.ifEmpty { artistaTag.trim() })
    }

    private fun ehRuido(trecho: String): Boolean {
        val n = normalizarBusca(trecho)
        return RUIDO.any { r -> Regex("""(^|[^a-z0-9])${Regex.escape(r)}([^a-z0-9]|$)""").containsMatchIn(n) }
    }

    /** true quando, tirando as palavras de ruído (e anos/números), não sobra nada: "Official Video", "Remastered 2011". */
    private fun ehSoRuido(trecho: String): Boolean {
        var n = normalizarBusca(TRECHO_ENTRE_PARENTESES.replace(trecho, " "))
        RUIDO.forEach { r -> n = Regex("""(^|[^a-z0-9])${Regex.escape(r)}(?=[^a-z0-9]|$)""").replace(n, " ") }
        return n.replace(Regex("""[^\p{L}]+"""), "").isEmpty()
    }

    private fun pareceArtista(texto: String, artista: String) =
        artista.isNotBlank() && similaridade(normalizar(texto), normalizar(artista)) >= 0.7

    /** Minúsculas, sem acentos, sem trechos entre parênteses/colchetes e sem pontuação. */
    fun normalizar(s: String): String =
        normalizarBusca(TRECHO_ENTRE_PARENTESES.replace(s, " "))
            .replace('&', ' ')
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()

    fun similaridade(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        // "imagine dragons" x "imaginedragons": iguais sem os espaços.
        if (a.replace(" ", "") == b.replace(" ", "")) return 0.95
        val (curta, longa) = if (a.length <= b.length) a to b else b to a
        if (curta.length >= 3 && longa.contains(curta)) return 0.75 + 0.2 * curta.length / longa.length
        val ta = a.split(' ').toSet()
        val tb = b.split(' ').toSet()
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size
    }

    private fun pontosDuracao(diferenca: Int?): Double = when {
        diferenca == null -> 0.5
        diferenca <= 2 -> 1.0
        diferenca <= 5 -> 0.85
        diferenca <= 12 -> 0.6
        diferenca <= 25 -> 0.35
        diferenca <= 60 -> 0.15
        else -> 0.0
    }

    /**
     * Pontua um resultado. Título pesa 3, artista 2, duração 1,2 e letra sincronizada vale 0,8 de
     * bônus — o suficiente para preferir a sincronizada com 10 s de diferença a uma simples exata.
     */
    /** Pontua contra todas as interpretações e fica com a melhor. */
    fun pontuar(c: CandidatoLetra, consultas: List<Consulta>, artistasConhecidos: List<String>, duracaoSeg: Int): CandidatoLetra =
        consultas.map { pontuar(c, it, artistasConhecidos, duracaoSeg) }
            .maxWith(compareBy<CandidatoLetra> { it.aceitavel }.thenBy { it.pontuacao })

    fun pontuar(c: CandidatoLetra, consulta: Consulta, artistasConhecidos: List<String>, duracaoSeg: Int): CandidatoLetra {
        val titulo = similaridade(normalizar(c.faixa), normalizar(consulta.titulo))
        val artistaDesconhecido = consulta.artista.isBlank() || normalizar(consulta.artista) == "artista desconhecido"
        val deles = normalizar(c.artista)
        val artista = if (artistaDesconhecido) 0.5 else {
            (artistasConhecidos + consulta.artista).map(::normalizar).filter { it.isNotEmpty() }
                .maxOfOrNull { nosso -> maxOf(similaridade(nosso, deles), if (deles.contains(nosso) || nosso.contains(deles)) 0.8 else 0.0) } ?: 0.0
        }
        val diferenca = if (duracaoSeg > 0 && c.duracaoSeg > 0) abs(c.duracaoSeg - duracaoSeg).toInt() else null
        val pontos = 3 * titulo + 2 * artista + 1.2 * pontosDuracao(diferenca) +
            (if (c.temSincronia) 0.8 else 0.0) - (if (c.instrumental) 0.5 else 0.0)
        val aceitavel = titulo >= 0.6 && (artistaDesconhecido || artista >= 0.34) && (c.sincronizada != null || c.simples != null || c.instrumental)
        return c.copy(pontuacao = pontos, diferencaSeg = diferenca, aceitavel = aceitavel)
    }
}
