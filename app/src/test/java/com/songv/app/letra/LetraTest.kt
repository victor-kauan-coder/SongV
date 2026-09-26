package com.songv.app.letra

import com.songv.app.model.FonteLetra
import com.songv.app.model.LinhaLetra
import com.songv.app.model.TipoLetra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetraTest {

    @Test
    fun lrc_com_varios_tempos_offset_e_marcas_de_palavra() {
        val lrc = """
            [ti:Exemplo]
            [offset:+500]
            [00:10.00][00:30.50]Refrão que volta
            [00:05.2]<00:05.20>Primeira <00:06.00>linha
            [00:20.123]
            [00:21.00]Depois da pausa
        """.trimIndent()

        assertTrue(Lrc.ehLrc(lrc))
        assertEquals(
            listOf(
                LinhaLetra("Primeira linha", 4_700),
                LinhaLetra("Refrão que volta", 9_500),
                LinhaLetra("", 19_623),
                LinhaLetra("Depois da pausa", 20_500),
                LinhaLetra("Refrão que volta", 30_000),
            ),
            Lrc.parse(lrc),
        )
    }

    @Test
    fun normalizar_junta_pausas_e_tira_as_das_pontas() {
        val linhas = listOf(
            LinhaLetra("", 0), LinhaLetra("A", 1), LinhaLetra(" ", 2), LinhaLetra("", 3), LinhaLetra("B", 4), LinhaLetra("", 5),
        )
        assertEquals(listOf(LinhaLetra("A", 1), LinhaLetra("", 2), LinhaLetra("B", 4)), normalizar(linhas))
    }

    @Test
    fun indice_da_linha_atual_por_busca_binaria() {
        val linhas = listOf(LinhaLetra("a", 1_000), LinhaLetra("b", 2_000), LinhaLetra("c", 3_000))
        assertEquals(-1, indiceLinhaAtual(linhas, 999)) // ainda na introdução
        assertEquals(0, indiceLinhaAtual(linhas, 1_000))
        assertEquals(1, indiceLinhaAtual(linhas, 2_999))
        assertEquals(2, indiceLinhaAtual(linhas, 60_000))
    }

    @Test
    fun texto_simples_vira_letra_sem_sincronia() {
        val letra = LetraRepository.deTexto("\nUma linha\n\nOutra linha\n", FonteLetra.EMBUTIDA)
        assertEquals(TipoLetra.SIMPLES, letra.tipo)
        assertFalse(Lrc.ehLrc("Uma linha\n[refrão]\nOutra"))
        assertEquals("Uma linha", letra.linhas.first().texto)
    }

    @Test
    fun limpa_titulo_antes_de_buscar_online() {
        assertEquals("Faixa", LetraRepository.limparTitulo("Faixa (Official Video)"))
        assertEquals("Faixa", LetraRepository.limparTitulo("Faixa [feat. Alguém]"))
        assertEquals("Faixa", LetraRepository.limparTitulo("Faixa - Remastered 2011"))
        assertEquals("(Intro)", LetraRepository.limparTitulo("(Intro)"))
    }

    @Test
    fun le_resposta_do_tradutor_com_romanizacao() {
        val json = """
            [[["olá mundo\n","hello world\n",null,null,3],["segunda linha","second line",null,null,3],
            [null,null,null,"annyeong\ndul"]],null,"ko",null,null,null,1]
        """.trimIndent()
        val r = interpretarResposta(json)
        assertEquals("olá mundo\nsegunda linha", r.traducao)
        assertEquals("annyeong\ndul", r.romanizacao)
        assertEquals("ko", r.origem)
    }

    @Test
    fun idiomas_equivalentes() {
        assertTrue(TradutorLetra.mesmoIdioma("zh-CN", "zh"))
        assertTrue(TradutorLetra.mesmoIdioma("pt", "PT"))
        assertFalse(TradutorLetra.mesmoIdioma("pt", "es"))
    }
}
