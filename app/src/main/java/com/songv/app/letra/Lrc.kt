package com.songv.app.letra

import com.songv.app.model.LinhaLetra
import java.util.Locale

/**
 * Parser de letras no formato LRC — o mesmo usado pela LRCLIB, por arquivos `.lrc` e por
 * muitos programas que gravam a letra sincronizada como texto dentro do USLT.
 *
 * Aceita `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, vários tempos na mesma linha
 * (`[00:12.00][00:45.10]refrão`), a tag `[offset:±ms]` e remove marcações de palavra do
 * LRC estendido (`<00:12.34>`).
 */
object Lrc {

    private val TEMPO = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val MARCA_PALAVRA = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val LINHA_COM_TEMPO = Regex("""^\s*\[\d{1,3}:\d{1,2}""")

    /** true se o texto tem cara de LRC (pelo menos duas linhas começando com timestamp). */
    fun ehLrc(texto: String): Boolean = texto.lineSequence().count { LINHA_COM_TEMPO.containsMatchIn(it) } >= 2

    fun parse(texto: String): List<LinhaLetra> {
        // Pela especificação, offset positivo faz a letra aparecer antes.
        val offset = OFFSET.find(texto)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val linhas = mutableListOf<LinhaLetra>()
        for (bruta in texto.lines()) {
            var resto = bruta.trim()
            val tempos = mutableListOf<Long>()
            while (true) {
                val m = TEMPO.find(resto)?.takeIf { it.range.first == 0 } ?: break
                tempos += milissegundos(m)
                resto = resto.substring(m.range.last + 1)
            }
            if (tempos.isEmpty()) continue
            val limpo = MARCA_PALAVRA.replace(resto, "").trim()
            tempos.forEach { linhas += LinhaLetra(limpo, (it - offset).coerceAtLeast(0)) }
        }
        return normalizar(linhas.sortedBy { it.tempoMs })
    }

    /** Gera LRC a partir de linhas sincronizadas (usado para salvar letras importadas/baixadas). */
    fun gerar(linhas: List<LinhaLetra>): String = linhas.joinToString("\n") { l ->
        val t = l.tempoMs ?: 0L
        String.format(Locale.ROOT, "[%02d:%02d.%02d]%s", t / 60_000, (t / 1000) % 60, (t % 1000) / 10, l.texto)
    }

    private fun milissegundos(m: MatchResult): Long {
        val (min, seg, fracao) = m.destructured
        val ms = if (fracao.isEmpty()) 0L else fracao.padEnd(3, '0').take(3).toLong()
        return min.toLong() * 60_000 + seg.toLong() * 1000 + ms
    }
}

/**
 * Limpa uma letra sincronizada: tira pausas no começo e no fim e junta pausas consecutivas
 * (linhas vazias viram um único marcador de trecho instrumental).
 */
fun normalizar(linhas: List<LinhaLetra>): List<LinhaLetra> {
    val saida = ArrayList<LinhaLetra>(linhas.size)
    for (linha in linhas) {
        val vazia = linha.texto.isBlank()
        if (vazia && (saida.isEmpty() || saida.last().texto.isBlank())) continue
        saida += if (vazia) linha.copy(texto = "") else linha
    }
    while (saida.isNotEmpty() && saida.last().texto.isBlank()) saida.removeAt(saida.lastIndex)
    return saida
}

/** Índice da linha que está tocando em [posicaoMs], ou -1 antes da primeira (introdução). */
fun indiceLinhaAtual(linhas: List<LinhaLetra>, posicaoMs: Long): Int {
    var baixo = 0
    var alto = linhas.lastIndex
    var resposta = -1
    while (baixo <= alto) {
        val meio = (baixo + alto) ushr 1
        val t = linhas[meio].tempoMs ?: return -1
        if (t <= posicaoMs) {
            resposta = meio
            baixo = meio + 1
        } else {
            alto = meio - 1
        }
    }
    return resposta
}
