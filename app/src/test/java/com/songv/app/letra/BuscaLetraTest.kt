package com.songv.app.letra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuscaLetraTest {

    @Test
    fun limpa_titulos_vindos_do_youtube() {
        assertEquals("Believer", BuscaLetra.consulta("Imagine Dragons - Believer (Official Music Video)", "Imagine Dragons").titulo)
        assertEquals("Believer", BuscaLetra.consulta("Believer | Legendado", "Imagine Dragons").titulo)
        assertEquals("Believer", BuscaLetra.consulta("Believer - Clipe Oficial", "Imagine Dragons").titulo)
        assertEquals("Evidências", BuscaLetra.consulta("Evidências (Ao Vivo)", "Chitãozinho & Xororó").titulo)
        assertEquals("Música", BuscaLetra.consulta("Música (part. Fulano)", "Banda").titulo)
        assertEquals("Música", BuscaLetra.consulta("Música feat. Fulano", "Banda").titulo)
        assertEquals("Canção", BuscaLetra.consulta("Canção - Remasterizado", "Banda").titulo)
    }

    @Test
    fun nao_corta_titulos_legitimos() {
        assertEquals("Dança com Você", BuscaLetra.consulta("Dança com Você", "Banda").titulo)
        assertEquals("Stay With Me", BuscaLetra.consulta("Stay With Me", "Cantor").titulo)
        assertEquals("Parte 1 - Parte 2", BuscaLetra.consulta("Parte 1 - Parte 2", "Banda").titulo)
    }

    @Test
    fun limpa_nome_de_canal_do_artista() {
        assertEquals("Imagine Dragons", BuscaLetra.consulta("Believer", "ImagineDragonsVEVO").artista)
        assertEquals("Banda", BuscaLetra.consulta("Faixa", "Banda - Topic").artista)
    }

    @Test
    fun nome_de_canal_colado_vira_artista_e_sai_do_titulo() {
        val c = BuscaLetra.consultas("Imagine Dragons - Believer (Official Music Video)", "ImagineDragonsVEVO").first()
        assertEquals(BuscaLetra.Consulta("Believer", "Imagine Dragons"), c)
    }

    @Test
    fun titulo_artista_com_tag_de_artista_generica_gera_segunda_interpretacao() {
        val todas = BuscaLetra.consultas("Los Hermanos - Anna Júlia", "Vários Artistas")
        assertTrue(todas.contains(BuscaLetra.Consulta("Anna Júlia", "Los Hermanos")))
    }

    @Test
    fun pior_caso_do_youtube_encontra_a_versao_certa() {
        val consultas = BuscaLetra.consultas("Imagine Dragons - Believer (Official Music Video)", "ImagineDragonsVEVO")
        // Faixa local de 48 s contra 205 s da original: a duração não pode vetar.
        val c = BuscaLetra.pontuar(candidato(1, "Believer", "Imagine Dragons", 205.0, true), consultas, listOf("ImagineDragonsVEVO"), 48)
        assertTrue(c.aceitavel)
    }

    private fun candidato(id: Long, faixa: String, artista: String, dur: Double, sinc: Boolean) =
        CandidatoLetra(id, faixa, artista, "", dur, if (sinc) "[00:01.00]a\n[00:02.00]b" else null, "a\nb", false)

    @Test
    fun duracao_diferente_nao_descarta_a_letra_certa() {
        // Arquivo de clipe com 12 s a mais: antes o filtro de ±4 s jogava fora todos os resultados.
        val consulta = BuscaLetra.Consulta("Believer", "Imagine Dragons")
        val c = BuscaLetra.pontuar(candidato(1, "Believer", "Imagine Dragons", 205.0, true), consulta, listOf("Imagine Dragons"), 217)
        assertTrue(c.aceitavel)
        assertEquals(12, c.diferencaSeg)
    }

    @Test
    fun prefere_sincronizada_com_pequena_diferenca_a_simples_exata() {
        val consulta = BuscaLetra.Consulta("Faixa", "Banda")
        val sincronizada = BuscaLetra.pontuar(candidato(1, "Faixa", "Banda", 210.0, true), consulta, listOf("Banda"), 200)
        val simples = BuscaLetra.pontuar(candidato(2, "Faixa", "Banda", 200.0, false), consulta, listOf("Banda"), 200)
        assertTrue(sincronizada.pontuacao > simples.pontuacao)
    }

    @Test
    fun rejeita_titulo_ou_artista_errados() {
        val consulta = BuscaLetra.Consulta("Faixa Certa", "Banda Certa")
        assertFalse(BuscaLetra.pontuar(candidato(1, "Outra Coisa", "Banda Certa", 200.0, true), consulta, listOf("Banda Certa"), 200).aceitavel)
        assertFalse(BuscaLetra.pontuar(candidato(2, "Faixa Certa", "Ninguém", 200.0, true), consulta, listOf("Banda Certa"), 200).aceitavel)
    }

    @Test
    fun artista_em_dupla_ou_com_acento_diferente_ainda_casa() {
        val consulta = BuscaLetra.Consulta("Evidências", "Chitãozinho & Xororó")
        val c = BuscaLetra.pontuar(candidato(1, "Evidencias", "Chitaozinho", 281.0, true), consulta, listOf("Chitãozinho", "Xororó"), 280)
        assertTrue(c.aceitavel)
    }
}
