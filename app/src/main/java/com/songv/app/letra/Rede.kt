package com.songv.app.letra

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

/** Pequeno cliente HTTP usado pelos dois recursos online (tradução e LRCLIB). Nada de libs extras. */
internal object Rede {

    const val USER_AGENT = "SongV/2.0 (https://github.com/victor-kauan-coder/SongV)"

    class Resposta(val codigo: Int, val corpo: String)

    fun temConexao(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** GET (sem [corpoForm]) ou POST form-urlencoded. Bloqueante: chame fora da main thread. */
    fun requisitar(url: String, corpoForm: String? = null, timeoutMs: Int = 10_000): Resposta {
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
