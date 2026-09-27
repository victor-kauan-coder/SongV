package com.songv.app.conexao

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CriptoTest {

    // Os mesmos vetores de desktop/src-tauri/src/cripto.rs, calculados à parte em Python.
    private val z = ByteArray(32) { 7 }
    private val nc = ByteArray(32) { 1 }
    private val nn = ByteArray(32) { 2 }

    @Test
    fun vetores_iguais_aos_do_computador() {
        val k = Cripto.chavePar(z, nc, nn)
        assertEquals("c3b60285360b1260164f69fae59493e1a9ead4191e478f535356fd1e30bb78f4", Cripto.hex(k))
        assertEquals("634289", Cripto.codigo(z, nc, nn))
        val (c2n, n2c) = Cripto.chavesSessao(k, nc, nn)
        assertNotEquals(Cripto.hex(c2n), Cripto.hex(n2c))
        val quadro = Cripto.Selador(c2n, Cripto.PREFIXO_C2N).selar(byteArrayOf(1) + "{\"t\":\"ping\"}".toByteArray())
        assertEquals("10f370b5f663b4f9ba7a4a656a44acda70164cae4f6b867c3cc9735b85", Cripto.hex(quadro))
    }

    @Test
    fun ida_e_volta_e_recusa_repeticao() {
        val chave = Cripto.aleatorio(32)
        val a = Cripto.Selador(chave, Cripto.PREFIXO_N2C)
        val b = Cripto.Selador(chave, Cripto.PREFIXO_N2C)
        val q = a.selar("um".toByteArray())
        assertArrayEquals("um".toByteArray(), b.abrir(q))
        assertThrows(Exception::class.java) { b.abrir(q) }
    }

    @Test
    fun ecdh_dos_dois_lados_da_o_mesmo_codigo() {
        val a = Cripto.Efemera()
        val b = Cripto.Efemera()
        assertEquals(65, a.publica.size)
        val za = a.acordo(b.publica)
        assertArrayEquals(za, b.acordo(a.publica))
        assertEquals(Cripto.codigo(za, nc, nn), Cripto.codigo(b.acordo(a.publica), nc, nn))
    }
}
