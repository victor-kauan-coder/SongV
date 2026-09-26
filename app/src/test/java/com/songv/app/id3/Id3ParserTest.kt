package com.songv.app.id3

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Monta tags ID3 byte a byte (como o mutagen do agente grava) e confere o que o parser extrai. */
class Id3ParserTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8)
    private fun latin1(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private fun int32(n: Int) = bytes(n ushr 24 and 0xFF, n ushr 16 and 0xFF, n ushr 8 and 0xFF, n and 0xFF)
    private fun syncSafe(n: Int) = bytes(n ushr 21 and 0x7F, n ushr 14 and 0x7F, n ushr 7 and 0x7F, n and 0x7F)

    private fun frame23(id: String, dados: ByteArray) = latin1(id) + int32(dados.size) + bytes(0, 0) + dados
    private fun frame24(id: String, dados: ByteArray) = latin1(id) + syncSafe(dados.size) + bytes(0, 0) + dados
    private fun frame22(id: String, dados: ByteArray) =
        latin1(id) + bytes(dados.size ushr 16 and 0xFF, dados.size ushr 8 and 0xFF, dados.size and 0xFF) + dados

    private fun tag(versao: Int, corpo: ByteArray, flags: Int = 0) =
        latin1("ID3") + bytes(versao, 0, flags) + syncSafe(corpo.size) + corpo + ByteArray(32) // + padding

    private fun texto(s: String) = bytes(3) + utf8(s)

    private fun sylt(vararg linhas: Pair<String, Int>): ByteArray {
        val saida = ByteArrayOutputStream()
        saida.write(bytes(3) + latin1("por") + bytes(2, 1, 0)) // UTF-8, idioma, ms, letra, descrição vazia
        linhas.forEach { (t, ms) -> saida.write(utf8(t) + bytes(0) + int32(ms)) }
        return saida.toByteArray()
    }

    private fun apic(tipo: Int, dados: ByteArray) = bytes(0) + latin1("image/jpeg") + bytes(0, tipo, 0) + dados

    @Test
    fun le_frames_do_agente_em_v23() {
        val corpo = frame23("TIT2", texto("Luz de Esquina")) +
            frame23("TPE1", texto("Maré Alta")) +
            frame23("TALB", texto("Cidade Acesa")) +
            frame23("TRCK", texto("3/12")) +
            frame23("TYER", texto("2024")) +
            frame23("SYLT", sylt("Primeira" to 1_000, "" to 5_000, "Segunda" to 9_500)) +
            frame23("USLT", bytes(3) + latin1("por") + bytes(0) + utf8("Primeira\nSegunda"))
        val tags = Id3Parser.lerTags(tag(3, corpo))

        assertEquals("Luz de Esquina", tags.titulo)
        assertEquals("Maré Alta", tags.artista)
        assertEquals("Cidade Acesa", tags.album)
        assertEquals(3, tags.faixa)
        assertEquals(2024, tags.ano)
        assertEquals(listOf(LinhaSylt("Primeira", 1_000), LinhaSylt("", 5_000), LinhaSylt("Segunda", 9_500)), tags.sylt)
        assertEquals("Primeira\nSegunda", tags.uslt)
        assertTrue(!tags.temCapa)
    }

    @Test
    fun prefere_a_capa_frontal_mesmo_se_vier_depois() {
        val contracapa = bytes(0xFF, 0xD8, 1, 1)
        val frente = bytes(0xFF, 0xD8, 2, 2, 2)
        val arquivo = tag(3, frame23("APIC", apic(4, contracapa)) + frame23("APIC", apic(3, frente)))

        assertTrue(Id3Parser.lerTags(arquivo).temCapa)
        assertArrayEquals(frente, Id3Parser.lerCapa(arquivo))
    }

    @Test
    fun le_v24_com_tamanho_sync_safe_e_varios_artistas() {
        // 200 bytes: em sync-safe isso é 0x01 0x48, diferente do inteiro normal (0x00 0xC8).
        val longo = "x".repeat(199)
        val corpo = frame24("TIT2", texto(longo)) + frame24("TPE1", texto("A\u0000B")) + frame24("TDRC", texto("2019-05-01"))
        val tags = Id3Parser.lerTags(tag(4, corpo))

        assertEquals(longo, tags.titulo)
        assertEquals("A, B", tags.artista)
        assertEquals(2019, tags.ano)
    }

    @Test
    fun le_v22_com_ids_de_tres_letras() {
        val corpo = frame22("TT2", bytes(0) + latin1("Antiga")) + frame22("TP1", bytes(0) + latin1("Artista"))
        val tags = Id3Parser.lerTags(tag(2, corpo))

        assertEquals("Antiga", tags.titulo)
        assertEquals("Artista", tags.artista)
    }

    @Test
    fun desfaz_unsynchronisation_da_tag_inteira() {
        val imagem = bytes(0xFF, 0x00, 0xFF, 0xE0, 7)
        val corpo = frame23("TIT2", texto("Com FF")) + frame23("APIC", apic(3, imagem))
        // Unsynchronisation: depois de todo 0xFF entra um 0x00.
        val sincronizado = ByteArrayOutputStream()
        corpo.forEach { b ->
            sincronizado.write(b.toInt())
            if (b == 0xFF.toByte()) sincronizado.write(0)
        }
        val arquivo = tag(3, sincronizado.toByteArray(), flags = 0x80)

        assertEquals("Com FF", Id3Parser.lerTags(arquivo).titulo)
        assertArrayEquals(imagem, Id3Parser.lerCapa(arquivo))
    }

    @Test
    fun arquivo_sem_tag_nao_quebra() {
        assertNull(Id3Parser.lerTags(bytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)).titulo)
        assertNull(Id3Parser.lerCapa(ByteArray(0)))
    }
}
