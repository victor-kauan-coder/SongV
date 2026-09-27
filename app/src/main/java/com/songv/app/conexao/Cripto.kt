package com.songv.app.conexao

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografia do canal celular ↔ computador — as mesmas funções de `desktop/src-tauri/src/cripto.rs`
 * (ver docs/desktop.md). Os testes dos dois lados usam os mesmos vetores.
 */
object Cripto {

    val PREFIXO_C2N = byteArrayOf('C'.code.toByte(), '2'.code.toByte(), 'N'.code.toByte(), 0)
    val PREFIXO_N2C = byteArrayOf('N'.code.toByte(), '2'.code.toByte(), 'C'.code.toByte(), 0)

    private val aleatorio = SecureRandom()

    fun aleatorio(tamanho: Int) = ByteArray(tamanho).also { aleatorio.nextBytes(it) }

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, sal: ByteArray, info: String, tamanho: Int): ByteArray {
        val extrair = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(sal, "HmacSHA256")) }
        val prk = extrair.doFinal(ikm)
        val expandir = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
        val saida = ByteArray(tamanho)
        var anterior = ByteArray(0)
        var feito = 0
        var i = 1
        while (feito < tamanho) {
            expandir.update(anterior)
            expandir.update(info.toByteArray())
            expandir.update(i.toByte())
            anterior = expandir.doFinal()
            val n = minOf(anterior.size, tamanho - feito)
            System.arraycopy(anterior, 0, saida, feito, n)
            feito += n
            i++
        }
        return saida
    }

    fun compromisso(publica: ByteArray, nonce: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { update(publica); update(nonce) }.digest()

    fun chavePar(z: ByteArray, nonceC: ByteArray, nonceN: ByteArray) = hkdf(z, nonceC + nonceN, "songv-par", 32)

    fun codigo(z: ByteArray, nonceC: ByteArray, nonceN: ByteArray): String {
        val b = hkdf(z, nonceC + nonceN, "songv-codigo", 4)
        val n = BigInteger(1, b).mod(BigInteger.valueOf(1_000_000)).toInt()
        return "%06d".format(n)
    }

    /** (celular→computador, computador→celular). */
    fun chavesSessao(k: ByteArray, nonceC: ByteArray, nonceN: ByteArray): Pair<ByteArray, ByteArray> {
        val sal = nonceC + nonceN
        return hkdf(k, sal, "songv-c2n", 32) to hkdf(k, sal, "songv-n2c", 32)
    }

    /** Par de chaves P-256 efêmero do pareamento, com a pública em SEC1 não comprimido (65 bytes). */
    class Efemera {
        private val par: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        val publica: ByteArray = (par.public as ECPublicKey).w.let { p -> byteArrayOf(4) + fixo32(p.affineX) + fixo32(p.affineY) }

        /** Segredo compartilhado: a coordenada x (32 bytes), igual ao `raw_secret_bytes` do Rust. */
        fun acordo(publicaOutro: ByteArray): ByteArray {
            require(publicaOutro.size == 65 && publicaOutro[0] == 4.toByte()) { "chave pública inválida" }
            val ponto = ECPoint(BigInteger(1, publicaOutro.copyOfRange(1, 33)), BigInteger(1, publicaOutro.copyOfRange(33, 65)))
            val params = (par.public as ECPublicKey).params
            val outra = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ponto, params))
            return KeyAgreement.getInstance("ECDH").run {
                init(par.private)
                doPhase(outra, true)
                generateSecret()
            }
        }

        private fun fixo32(n: BigInteger): ByteArray {
            val b = n.toByteArray()
            return when {
                b.size == 32 -> b
                b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
                else -> ByteArray(32 - b.size) + b
            }
        }
    }

    /** Uma direção do canal: AES-256-GCM, nonce = prefixo ‖ contador. Repetição não decifra. */
    class Selador(chave: ByteArray, private val prefixo: ByteArray) {
        private val chave = SecretKeySpec(chave, "AES")
        private var contador = 0L

        private fun proximoNonce(): ByteArray {
            val n = ByteArray(12)
            System.arraycopy(prefixo, 0, n, 0, 4)
            var c = contador++
            for (i in 11 downTo 4) {
                n[i] = (c and 0xFF).toByte()
                c = c ushr 8
            }
            return n
        }

        fun selar(claro: ByteArray): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, chave, GCMParameterSpec(128, proximoNonce()))
                doFinal(claro)
            }

        fun abrir(cifrado: ByteArray): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, chave, GCMParameterSpec(128, proximoNonce()))
                doFinal(cifrado)
            }
    }

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}
