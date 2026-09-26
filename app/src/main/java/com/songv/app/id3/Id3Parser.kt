package com.songv.app.id3

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Inflater

/**
 * Parser manual de tags ID3v2 (v2.2, v2.3 e v2.4).
 *
 * Existe porque bibliotecas prontas leem USLT (letra simples) mas não SYLT (letra sincronizada),
 * que é o que o agente grava via mutagen. Cobre também o que costuma quebrar parsers caseiros:
 * cabeçalho estendido, unsynchronisation (na tag inteira ou por frame), frames comprimidos com
 * zlib, tamanhos sync-safe (v2.4) vs. inteiros normais (v2.3) e os IDs de 3 letras da v2.2.
 *
 * A leitura é feita frame a frame direto do arquivo: o frame da capa (que pode ter megabytes)
 * só é lido quando pedido, o que deixa a varredura da biblioteca bem mais rápida.
 */
object Id3Parser {

    private const val LATIN1 = 0
    private const val UTF16 = 1
    private const val UTF16BE = 2
    private const val UTF8 = 3
    private const val LIMITE_FRAME = 32 * 1024 * 1024
    private const val LIMITE_LEITURA_UNICA = 8 * 1024 * 1024

    private val FRAMES_TEXTO = setOf("TIT2", "TPE1", "TALB", "TPE2", "TYER", "TDRC", "TRCK", "TPOS")

    /** IDs de 3 letras da v2.2 → equivalentes da v2.3/v2.4. */
    private val V22 = mapOf(
        "TT2" to "TIT2", "TP1" to "TPE1", "TAL" to "TALB", "TP2" to "TPE2", "TYE" to "TYER",
        "TRK" to "TRCK", "TPA" to "TPOS", "PIC" to "APIC", "SLT" to "SYLT", "ULT" to "USLT",
    )

    fun lerTags(caminho: String): TagsId3 = try {
        comFonte(caminho) { ler(it, querCapa = false).tags }
    } catch (_: Exception) {
        TagsId3()
    }

    /** Bytes da imagem de capa, preferindo a capa frontal (tipo 3) quando há mais de uma. */
    fun lerCapa(caminho: String): ByteArray? = try {
        comFonte(caminho) { ler(it, querCapa = true).capa }
    } catch (_: Exception) {
        null
    }

    /**
     * Lê a tag inteira numa única leitura sequencial. No armazenamento compartilhado do Android
     * cada leitura passa pelo FUSE, e ler frame a frame (muitas leituras pequenas) levava ~1 s por
     * arquivo. Só tags gigantes (capas enormes) caem na leitura por frames, direto do disco.
     */
    private fun <T> comFonte(caminho: String, bloco: (Fonte) -> T): T {
        val arquivo = File(caminho)
        java.io.BufferedInputStream(java.io.FileInputStream(arquivo), 64 * 1024).use { entrada ->
            val cabecalho = lerAte(entrada, 10)
            if (cabecalho.size < 10 || cabecalho[0] != 'I'.code.toByte() || cabecalho[1] != 'D'.code.toByte() || cabecalho[2] != '3'.code.toByte()) {
                return bloco(FonteMemoria(cabecalho))
            }
            val tamanhoTag = syncSafe(cabecalho, 6)
            if (tamanhoTag <= LIMITE_LEITURA_UNICA) {
                return bloco(FonteMemoria(cabecalho + lerAte(entrada, tamanhoTag)))
            }
        }
        return RandomAccessFile(arquivo, "r").use { bloco(FonteArquivo(it)) }
    }

    private fun lerAte(entrada: java.io.InputStream, n: Int): ByteArray {
        val buffer = ByteArray(n)
        var lidos = 0
        while (lidos < n) {
            val r = entrada.read(buffer, lidos, n - lidos)
            if (r < 0) break
            lidos += r
        }
        return if (lidos == n) buffer else buffer.copyOf(lidos)
    }

    /** Mesmo parser, sobre uma tag inteira em memória (a partir do cabeçalho "ID3"). Usado nos testes. */
    internal fun lerTags(bytes: ByteArray): TagsId3 = ler(FonteMemoria(bytes), querCapa = false).tags
    internal fun lerCapa(bytes: ByteArray): ByteArray? = ler(FonteMemoria(bytes), querCapa = true).capa

    // ---- Leitura ----

    private class Resultado(val tags: TagsId3, val capa: ByteArray?)

    private class Acumulador {
        var titulo: String? = null
        var artista: String? = null
        var album: String? = null
        var artistaAlbum: String? = null
        var ano: Int? = null
        var faixa: Int? = null
        var disco: Int? = null
        var sylt: List<LinhaSylt>? = null
        var uslt: String? = null
        var temCapa = false
        var capa: ByteArray? = null
        var capaFrontal = false

        fun tags() = TagsId3(titulo, artista, album, artistaAlbum, ano, faixa, disco, sylt, uslt, temCapa)
    }

    private fun ler(fonte: Fonte, querCapa: Boolean): Resultado {
        val vazio = Resultado(TagsId3(), null)
        if (fonte.tamanho < 10) return vazio
        val cabecalho = fonte.ler(0, 10)
        if (cabecalho[0] != 'I'.code.toByte() || cabecalho[1] != 'D'.code.toByte() || cabecalho[2] != '3'.code.toByte()) {
            return vazio
        }
        val versao = cabecalho[3].toInt() and 0xFF
        if (versao !in 2..4) return vazio
        val flags = cabecalho[5].toInt() and 0xFF
        val tamanhoTag = minOf(syncSafe(cabecalho, 6).toLong(), fonte.tamanho - 10).toInt()

        // Na v2.2/v2.3 a unsynchronisation vale para o corpo inteiro: decodifica tudo antes.
        val corpo: Fonte = if (versao < 4 && flags and 0x80 != 0) {
            FonteMemoria(desincronizar(fonte.ler(10, tamanhoTag)))
        } else {
            FonteRecorte(fonte, 10, tamanhoTag.toLong())
        }

        var pos = 0L
        if (versao >= 3 && flags and 0x40 != 0 && corpo.tamanho >= 4) {
            val b = corpo.ler(0, 4)
            pos = if (versao == 4) syncSafe(b, 0).toLong() else int32(b, 0).toLong() + 4
        }

        val tamanhoCabecalhoFrame = if (versao == 2) 6 else 10
        val acc = Acumulador()

        while (pos + tamanhoCabecalhoFrame <= corpo.tamanho) {
            val fh = corpo.ler(pos, tamanhoCabecalhoFrame)
            if (fh[0].toInt() == 0) break // início do padding
            val idBruto = String(fh, 0, if (versao == 2) 3 else 4, Charsets.ISO_8859_1)
            if (!idBruto.all { it in 'A'..'Z' || it in '0'..'9' }) break

            val tamanho = when (versao) {
                2 -> int24(fh, 3)
                3 -> int32(fh, 4)
                else -> syncSafe(fh, 4)
            }
            val flagsFrame = if (versao == 2) 0 else ((fh[8].toInt() and 0xFF) shl 8) or (fh[9].toInt() and 0xFF)
            val inicio = pos + tamanhoCabecalhoFrame
            if (tamanho <= 0 || inicio + tamanho > corpo.tamanho) break

            val id = if (versao == 2) V22[idBruto] ?: idBruto else idBruto
            if (id == "APIC") acc.temCapa = true

            val interessa = if (id == "APIC") querCapa && !acc.capaFrontal else id in FRAMES_TEXTO || id == "SYLT" || id == "USLT"
            if (interessa && tamanho <= LIMITE_FRAME) {
                try {
                    val dados = decodificarFlags(corpo.ler(inicio, tamanho), versao, flagsFrame)
                    if (dados != null && dados.isNotEmpty()) processar(id, dados, versao, acc)
                } catch (_: Exception) {
                    // Frame corrompido: ignora e segue para o próximo.
                }
            }
            pos = inicio + tamanho
        }
        return Resultado(acc.tags(), acc.capa)
    }

    private fun processar(id: String, dados: ByteArray, versao: Int, acc: Acumulador) {
        when (id) {
            "TIT2" -> acc.titulo = acc.titulo ?: texto(dados)
            "TPE1" -> acc.artista = acc.artista ?: texto(dados)
            "TALB" -> acc.album = acc.album ?: texto(dados)
            "TPE2" -> acc.artistaAlbum = acc.artistaAlbum ?: texto(dados)
            "TYER", "TDRC" -> acc.ano = acc.ano ?: texto(dados)?.take(4)?.toIntOrNull()?.takeIf { it > 0 }
            "TRCK" -> acc.faixa = acc.faixa ?: numeroInicial(texto(dados))
            "TPOS" -> acc.disco = acc.disco ?: numeroInicial(texto(dados))
            "USLT" -> if (acc.uslt == null) acc.uslt = uslt(dados)
            "SYLT" -> if (acc.sylt == null) acc.sylt = sylt(dados)
            "APIC" -> apic(dados, versao)?.let { (tipo, imagem) ->
                if (acc.capa == null || tipo == 3) {
                    acc.capa = imagem
                    acc.capaFrontal = tipo == 3
                }
            }
        }
    }

    // ---- Frames ----

    /** Frames T***: encoding + texto. Vários valores vêm separados por \0 ("A\0B" → "A, B"). */
    private fun texto(d: ByteArray): String? {
        val bruto = decodificar(d.copyOfRange(1, d.size), d[0].toInt() and 0xFF)
        return bruto.split('\u0000')
            .map { it.replace("﻿", "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifEmpty { null }
    }

    /** USLT: encoding + idioma(3) + descrição terminada + texto. */
    private fun uslt(d: ByteArray): String? {
        val enc = d[0].toInt() and 0xFF
        val fimDescricao = indiceTerminador(d, 4, enc)
        val inicio = fimDescricao + tamanhoTerminador(enc)
        if (inicio > d.size) return null
        return decodificar(d.copyOfRange(inicio, d.size), enc)
            .replace("﻿", "")
            .trim('\u0000', ' ', '\n', '\r')
            .ifBlank { null }
    }

    /**
     * SYLT: encoding + idioma(3) + formato de tempo(1) + tipo(1) + descrição + repetições de
     * [texto terminado + tempo em 4 bytes]. Só aceitamos tempo em milissegundos (formato 2),
     * que é o que o agente grava; frames MPEG (formato 1) dependeriam do bitrate.
     */
    private fun sylt(d: ByteArray): List<LinhaSylt>? {
        val enc = d[0].toInt() and 0xFF
        val formatoTempo = d[4].toInt() and 0xFF
        if (formatoTempo != 2) return null
        var pos = indiceTerminador(d, 6, enc) + tamanhoTerminador(enc)
        val linhas = mutableListOf<LinhaSylt>()
        while (pos < d.size) {
            val fimTexto = indiceTerminador(d, pos, enc)
            val texto = decodificar(d.copyOfRange(pos, fimTexto), enc)
            pos = fimTexto + tamanhoTerminador(enc)
            if (pos + 4 > d.size) break
            val tempo = int32(d, pos).toLong() and 0xFFFFFFFFL
            pos += 4
            linhas += LinhaSylt(texto.replace("﻿", "").trim(), tempo)
        }
        return linhas.sortedBy { it.tempoMs }.takeIf { l -> l.any { it.texto.isNotEmpty() } }
    }

    /** APIC (v2.3/v2.4) ou PIC (v2.2). Devolve (tipo da imagem, bytes). */
    private fun apic(d: ByteArray, versao: Int): Pair<Int, ByteArray>? {
        val enc = d[0].toInt() and 0xFF
        var pos = if (versao == 2) {
            4 // formato de 3 letras ("JPG"/"PNG")
        } else {
            indiceTerminador(d, 1, LATIN1) + 1 // mime terminado em \0
        }
        if (pos >= d.size) return null
        val tipo = d[pos].toInt() and 0xFF
        pos += 1
        pos = indiceTerminador(d, pos, enc) + tamanhoTerminador(enc)
        if (pos >= d.size) return null
        return tipo to d.copyOfRange(pos, d.size)
    }

    // ---- Flags de frame ----

    private fun decodificarFlags(bruto: ByteArray, versao: Int, f: Int): ByteArray? {
        var extra = 0
        return when (versao) {
            3 -> {
                val comprimido = f and 0x0080 != 0
                if (f and 0x0040 != 0) return null // criptografado
                if (comprimido) extra += 4 // tamanho descomprimido
                if (f and 0x0020 != 0) extra += 1 // id de grupo
                val d = bruto.copyOfRange(extra, bruto.size)
                if (comprimido) inflar(d) else d
            }
            4 -> {
                if (f and 0x0004 != 0) return null // criptografado
                if (f and 0x0040 != 0) extra += 1 // id de grupo
                if (f and 0x0001 != 0) extra += 4 // indicador de tamanho
                var d = bruto.copyOfRange(extra, bruto.size)
                if (f and 0x0002 != 0) d = desincronizar(d)
                if (f and 0x0008 != 0) d = inflar(d)
                d
            }
            else -> bruto
        }
    }

    private fun inflar(d: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(d)
        val saida = ByteArrayOutputStream(d.size * 2)
        val buffer = ByteArray(8192)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                saida.write(buffer, 0, n)
            }
        } finally {
            inflater.end()
        }
        return saida.toByteArray()
    }

    /** Desfaz a unsynchronisation: toda sequência 0xFF 0x00 volta a ser 0xFF. */
    private fun desincronizar(d: ByteArray): ByteArray {
        val saida = ByteArrayOutputStream(d.size)
        var i = 0
        while (i < d.size) {
            saida.write(d[i].toInt())
            if (d[i] == 0xFF.toByte() && i + 1 < d.size && d[i + 1].toInt() == 0) i++
            i++
        }
        return saida.toByteArray()
    }

    // ---- Utilitários ----

    private fun numeroInicial(s: String?): Int? = s?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()?.takeIf { it > 0 }

    private fun syncSafe(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0x7F) shl 21) or ((b[i + 1].toInt() and 0x7F) shl 14) or
            ((b[i + 2].toInt() and 0x7F) shl 7) or (b[i + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or
            ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    private fun int24(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 16) or ((b[i + 1].toInt() and 0xFF) shl 8) or (b[i + 2].toInt() and 0xFF)

    private fun tamanhoTerminador(enc: Int) = if (enc == UTF16 || enc == UTF16BE) 2 else 1

    /** Índice do terminador de string a partir de [inicio] (alinhado de 2 em 2 no UTF-16). */
    private fun indiceTerminador(b: ByteArray, inicio: Int, enc: Int): Int {
        val passo = tamanhoTerminador(enc)
        var i = inicio
        while (i + passo <= b.size) {
            if (b[i].toInt() == 0 && (passo == 1 || b[i + 1].toInt() == 0)) return i
            i += passo
        }
        return b.size
    }

    private fun decodificar(b: ByteArray, enc: Int): String = when (enc) {
        LATIN1 -> String(b, Charsets.ISO_8859_1)
        UTF16 -> String(b, Charsets.UTF_16)
        UTF16BE -> String(b, Charsets.UTF_16BE)
        UTF8 -> String(b, Charsets.UTF_8)
        else -> String(b, Charsets.UTF_8)
    }

    // ---- Fontes de bytes (arquivo ou memória) ----

    private abstract class Fonte {
        abstract val tamanho: Long
        abstract fun ler(pos: Long, n: Int): ByteArray
    }

    private class FonteArquivo(private val raf: RandomAccessFile) : Fonte() {
        override val tamanho = raf.length()
        override fun ler(pos: Long, n: Int) = ByteArray(n).also { raf.seek(pos); raf.readFully(it) }
    }

    private class FonteMemoria(private val b: ByteArray) : Fonte() {
        override val tamanho = b.size.toLong()
        override fun ler(pos: Long, n: Int) = b.copyOfRange(pos.toInt(), pos.toInt() + n)
    }

    private class FonteRecorte(private val base: Fonte, private val inicio: Long, override val tamanho: Long) : Fonte() {
        override fun ler(pos: Long, n: Int) = base.ler(inicio + pos, n)
    }
}
