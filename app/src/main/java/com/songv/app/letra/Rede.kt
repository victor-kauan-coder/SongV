package com.songv.app.letra

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import java.net.URL

/** Pequeno cliente HTTP usado pelos dois recursos online (tradução e LRCLIB). Nada de libs extras. */
internal object Rede {

    const val USER_AGENT = "SongV/2.0 (https://github.com/victor-kauan-coder/SongV)"

    class Resposta(val codigo: Int, val corpo: String)

    fun temConexao(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        // Só INTERNET: exigir VALIDATED dava "sem internet" em redes que funcionam (VPN, alguns
        // roteadores e aparelhos). Se a rede não tiver saída de verdade, a própria requisição falha.
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Como [requisitar], mas tenta de novo (até 3 vezes) em 429, 5xx e erros de conexão/timeout. */
    suspend fun requisitarComRetry(url: String, corpoForm: String? = null): Resposta {
        var espera = 600L
        var ultimoErro: Exception? = null
        repeat(3) { tentativa ->
            try {
                val r = requisitar(url, corpoForm)
                if ((r.codigo == 429 || r.codigo >= 500) && tentativa < 2) {
                    delay(espera)
                    espera *= 2
                } else {
                    return r
                }
            } catch (e: UnknownHostException) {
                throw e // sem DNS: tentar de novo não adianta
            } catch (e: IOException) {
                ultimoErro = e
                if (tentativa < 2) delay(espera)
                espera *= 2
            }
        }
        throw ultimoErro ?: IOException("sem resposta")
    }

    /** GET (sem [corpoForm]) ou POST form-urlencoded. Bloqueante: chame fora da main thread. */
    fun requisitar(url: String, corpoForm: String? = null, timeoutMs: Int = 12_000): Resposta {
        val conexao = URL(url).openConnection() as HttpURLConnection
        try {
            conexao.connectTimeout = timeoutMs
            conexao.readTimeout = timeoutMs
            conexao.setRequestProperty("User-Agent", USER_AGENT)
            conexao.setRequestProperty("Accept", "application/json")
            if (corpoForm != null) {
                conexao.requestMethod = "POST"
                conexao.doOutput = true
                conexao.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                conexao.outputStream.use { it.write(corpoForm.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = conexao.responseCode
            val stream = if (codigo in 200..299) conexao.inputStream else conexao.errorStream
            val corpo = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Resposta(codigo, corpo)
        } finally {
            conexao.disconnect()
        }
    }
}
