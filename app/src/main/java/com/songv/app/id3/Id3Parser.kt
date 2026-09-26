package com.songv.app.id3

import java.io.File
import java.io.RandomAccessFile

/**
 * Parser manual de tags ID3v2 (v2.3 e v2.4).
 *
 * Motivo de existir: bibliotecas prontas (ex: mp3agic) não leem o frame SYLT
 * (letra sincronizada), só USLT (letra simples). O agente que gera os MP3s
 * grava especificamente TIT2, TPE1, APIC, SYLT e USLT via mutagen, salvando
 * como ID3v2.3 (`v2_version=3`). Este parser cobre exatamente esses frames.
 *
 * Formato do header ID3v2 (10 bytes):
 *   "ID3" (3 bytes) + versão major/minor (2 bytes) + flags (1 byte) + tamanho sync-safe (4 bytes)
 *
 * Formato do header de frame:
 *   - v2.3: ID (4 bytes) + tamanho como inteiro de 32 bits NORMAL (4 bytes) + flags (2 bytes)
 *   - v2.4: ID (4 bytes) + tamanho SYNC-SAFE (4 bytes) + flags (2 bytes)
 *   A diferença entre v2.3 e v2.4 no tamanho do frame é a pegadinha mais comum
 *   de bug nesse tipo de parser — tratamos os dois casos abaixo.
 */
object Id3Parser {

    private const val ENCODING_LATIN1 = 0
    private const val ENCODING_UTF16_BOM = 1
    private const val ENCODING_UTF16_BE = 2
    private const val ENCODING_UTF8 = 3

    fun parse(caminhoArquivo: String): TagsId3 {
        val vazio = TagsId3(null, null, null, null, null)
        val file = File(caminhoArquivo)
        if (!file.exists() || file.length() < 10) return vazio

        return try {
            parseInterno(file)
        } catch (_: Exception) {
            // Arquivo truncado, corrompido, ou header ID3 inesperado: melhor não travar a varredura
            vazio
        }
    }

    private fun parseInterno(file: File): TagsId3 {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            raf.readFully(header)

            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return TagsId3(null, null, null, null, null)
            }

            val versaoMajor = header[3].toInt() and 0xFF // 3 = v2.3, 4 = v2.4
            val tagSize = syncSafeToInt(header[6], header[7], header[8], header[9])

            val corpoTag = ByteArray(tagSize)
            raf.readFully(corpoTag)

            var titulo: String? = null
            var artista: String? = null
            var capa: FrameApic? = null
            var sylt: FrameSylt? = null
            var uslt: FrameUslt? = null

            var pos = 0
            while (pos + 10 <= corpoTag.size) {
                val frameId = String(corpoTag, pos, 4, Charsets.ISO_8859_1)

                // Padding final: encontramos bytes zerados, não há mais frames
                if (frameId[0] == '\u0000') break

                val sizeBytes = corpoTag.copyOfRange(pos + 4, pos + 8)
                val frameSize = if (versaoMajor >= 4) {
                    syncSafeToInt(sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3])
                } else {
                    normalIntFromBytes(sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3])
                }
                // flags do frame: 2 bytes, ignoramos (não tratamos compressão/criptografia)
                val frameStart = pos + 10

                if (frameSize <= 0 || frameStart + frameSize > corpoTag.size) break

                val frameBytes = corpoTag.copyOfRange(frameStart, frameStart + frameSize)

                try {
                    when (frameId) {
                        "TIT2" -> titulo = parseFrameTexto(frameBytes)
                        "TPE1" -> artista = parseFrameTexto(frameBytes)
                        "APIC" -> capa = parseApic(frameBytes)
                        "SYLT" -> sylt = parseSylt(frameBytes)
                        "USLT" -> uslt = parseUslt(frameBytes)
                    }
                } catch (_: Exception) {
                    // Frame corrompido ou formato inesperado: ignora e segue para o próximo
                }

                pos = frameStart + frameSize
            }

            return TagsId3(titulo, artista, capa, sylt, uslt)
        }
    }

    // ---- Leitura de cada tipo de frame ----

    /** TIT2/TPE1: encoding(1 byte) + string de texto (sem terminador, ocupa o resto do frame). */
    private fun parseFrameTexto(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val encoding = bytes[0].toInt() and 0xFF
        val corpo = bytes.copyOfRange(1, bytes.size)
        return decodeString(corpo, encoding).trim('\u0000').trim()
    }

    /**
     * APIC: encoding(1) + mime(string ISO-8859-1 terminada em 0x00) + tipo(1) +
     *       descrição(string no encoding declarado, terminada) + dados binários até o fim.
     */
    private fun parseApic(bytes: ByteArray): FrameApic {
        val encoding = bytes[0].toInt() and 0xFF
        var pos = 1

        val mimeEnd = indexOfTerminator(bytes, pos, ENCODING_LATIN1)
        val mime = String(bytes, pos, mimeEnd - pos, Charsets.ISO_8859_1)
        pos = mimeEnd + 1

        val tipo = bytes[pos].toInt() and 0xFF
        pos += 1

        val descEnd = indexOfTerminator(bytes, pos, encoding)
        val descricao = decodeString(bytes.copyOfRange(pos, descEnd), encoding)
        pos = descEnd + terminatorLength(encoding)

        val dados = bytes.copyOfRange(pos, bytes.size)
        return FrameApic(mime = mime, tipo = tipo, descricao = descricao, dados = dados)
    }

    /**
     * SYLT: encoding(1) + idioma(3, ISO-8859-1) + formato_tempo(1) + tipo_conteudo(1) +
     *       descrição(string terminada) + repetição de [texto(terminado) + tempo(4 bytes BE)].
     */
    private fun parseSylt(bytes: ByteArray): FrameSylt {
        val encoding = bytes[0].toInt() and 0xFF
        var pos = 1

        val idioma = String(bytes, pos, 3, Charsets.ISO_8859_1)
        pos += 3

        val formatoTempo = bytes[pos].toInt() and 0xFF
        pos += 1
        val tipoConteudo = bytes[pos].toInt() and 0xFF
        pos += 1

        val descEnd = indexOfTerminator(bytes, pos, encoding)
        pos = descEnd + terminatorLength(encoding)

        val linhas = mutableListOf<LinhaSylt>()
        while (pos < bytes.size) {
            val textoEnd = indexOfTerminator(bytes, pos, encoding)
            if (textoEnd > bytes.size) break
            val texto = decodeString(bytes.copyOfRange(pos, textoEnd), encoding)
            pos = textoEnd + terminatorLength(encoding)

            if (pos + 4 > bytes.size) break
            val tempoMs = normalIntFromBytes(bytes[pos], bytes[pos + 1], bytes[pos + 2], bytes[pos + 3]).toLong()
            pos += 4

            if (texto.isNotBlank()) {
                linhas.add(LinhaSylt(texto = texto.trim(), tempoMs = tempoMs))
            }
        }

        return FrameSylt(
            idioma = idioma,
            formatoTempo = formatoTempo,
            tipoConteudo = tipoConteudo,
            linhas = linhas.sortedBy { it.tempoMs }
        )
    }

    /**
     * USLT: encoding(1) + idioma(3) + descrição(string terminada) + texto (resto do frame).
     */
    private fun parseUslt(bytes: ByteArray): FrameUslt {
        val encoding = bytes[0].toInt() and 0xFF
        var pos = 1

        val idioma = String(bytes, pos, 3, Charsets.ISO_8859_1)
        pos += 3

        val descEnd = indexOfTerminator(bytes, pos, encoding)
        val descricao = decodeString(bytes.copyOfRange(pos, descEnd), encoding)
        pos = descEnd + terminatorLength(encoding)

        val texto = decodeString(bytes.copyOfRange(pos, bytes.size), encoding)

        return FrameUslt(idioma = idioma, descricao = descricao, texto = texto.trim('\u0000').trim())
    }

    // ---- Utilitários de baixo nível ----

    /** Converte 4 bytes sync-safe (7 bits úteis cada) em Int — usado no header geral e em frames v2.4. */
    private fun syncSafeToInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0x7F) shl 21) or
                ((b1.toInt() and 0x7F) shl 14) or
                ((b2.toInt() and 0x7F) shl 7) or
                (b3.toInt() and 0x7F)
    }

    /** Converte 4 bytes num inteiro de 32 bits normal (big-endian) — usado em tamanho de frame v2.3 e timestamps SYLT. */
    private fun normalIntFromBytes(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0xFF) shl 24) or
                ((b1.toInt() and 0xFF) shl 16) or
                ((b2.toInt() and 0xFF) shl 8) or
                (b3.toInt() and 0xFF)
    }

    /** Quantos bytes o terminador de string ocupa nesse encoding (UTF-16 usa 2 bytes, os demais 1). */
    private fun terminatorLength(encoding: Int): Int {
        return if (encoding == ENCODING_UTF16_BOM || encoding == ENCODING_UTF16_BE) 2 else 1
    }

    /** Encontra o índice do terminador de string a partir de `start`, respeitando o tamanho do terminador do encoding. */
    private fun indexOfTerminator(bytes: ByteArray, start: Int, encoding: Int): Int {
        val step = terminatorLength(encoding)
        var i = start
        while (i + step <= bytes.size) {
            if (step == 1) {
                if (bytes[i] == 0.toByte()) return i
            } else {
                if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte()) return i
            }
            i += step
        }
        return bytes.size
    }

    /** Decodifica bytes de acordo com o byte de encoding declarado no frame ID3. */
    private fun decodeString(bytes: ByteArray, encoding: Int): String {
        return when (encoding) {
            ENCODING_LATIN1 -> String(bytes, Charsets.ISO_8859_1)
            ENCODING_UTF16_BOM -> String(bytes, Charsets.UTF_16) // detecta BOM automaticamente
            ENCODING_UTF16_BE -> String(bytes, Charsets.UTF_16BE)
            ENCODING_UTF8 -> String(bytes, Charsets.UTF_8)
            else -> String(bytes, Charsets.UTF_8)
        }
    }
}
