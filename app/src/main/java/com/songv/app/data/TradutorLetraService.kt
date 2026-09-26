package com.songv.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Resultado de uma tentativa de tradução: sucesso com o texto, ou um motivo de falha pra exibir. */
sealed class ResultadoTraducao {
    data class Sucesso(val linhasTraduzidas: List<String>) : ResultadoTraducao()
    data object SemConexao : ResultadoTraducao()
    data object Falha : ResultadoTraducao()
}

/**
 * Traduz letras de música sob demanda, usando o endpoint público (não-oficial, sem chave) do
 * Google Translate. É um recurso totalmente opcional: só é chamado quando o usuário aperta o
 * botão de traduzir, e falha graciosamente sem internet (checamos a conectividade antes de
 * tentar, pra dar um erro claro em vez de travar esperando timeout).
 */
class TradutorLetraService(private val context: Context) {

    /** true se o aparelho tem alguma rede (wifi ou dados) validada para acesso à internet. */
    fun temConexaoDisponivel(): Boolean {
        val gerenciador = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val rede = gerenciador.activeNetwork ?: return false
        val capacidades = gerenciador.getNetworkCapabilities(rede) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Traduz cada [linhas] para [idiomaDestino] (código ISO 639-1, ex: "pt", "en", "es"),
     * preservando a ordem. Cada linha é traduzida numa requisição própria, em paralelo — mais
     * chamadas de rede do que juntar tudo numa só, mas evita o problema de reflow: o Google
     * Translate normaliza quebras de linha e espaços do texto original, então qualquer separador
     * artificial inserido pra juntar as linhas numa única chamada pode ser reformatado ou
     * removido de forma imprevisível, quebrando o alinhamento entre linha original e traduzida.
     */
    suspend fun traduzir(linhas: List<String>, idiomaDestino: String): ResultadoTraducao {
        if (linhas.isEmpty()) return ResultadoTraducao.Sucesso(emptyList())
        if (!temConexaoDisponivel()) return ResultadoTraducao.SemConexao

        return withContext(Dispatchers.IO) {
            try {
                val traducoes = linhas.map { linha ->
                    async {
                        if (linha.isBlank()) "" else chamarApiTraducao(linha, idiomaDestino)
                    }
                }.awaitAll()

                if (traducoes.any { it == null }) {
                    ResultadoTraducao.Falha
                } else {
                    @Suppress("UNCHECKED_CAST")
                    ResultadoTraducao.Sucesso(traducoes as List<String>)
                }
            } catch (e: Exception) {
                ResultadoTraducao.Falha
            }
        }
    }

    private fun chamarApiTraducao(texto: String, idiomaDestino: String): String? {
        val textoCodificado = URLEncoder.encode(texto, "UTF-8")
        val url = URL(
            "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=auto&tl=$idiomaDestino&dt=t&q=$textoCodificado"
        )

        val conexao = url.openConnection() as HttpURLConnection
        conexao.connectTimeout = 8000
        conexao.readTimeout = 8000
        conexao.requestMethod = "GET"

        return try {
            if (conexao.responseCode != HttpURLConnection.HTTP_OK) return null
            val corpo = conexao.inputStream.bufferedReader().use { it.readText() }
            extrairTextoTraduzido(corpo)
        } catch (e: Exception) {
            null
        } finally {
            conexao.disconnect()
        }
    }

    /**
     * A resposta do endpoint é um JSON aninhado tipo `[[["trad1","orig1",...],["trad2",...]],...]`.
     * Concatenamos o primeiro elemento de cada bloco de frase, que é o texto traduzido.
     */
    private fun extrairTextoTraduzido(corpoJson: String): String {
        val raiz = JSONArray(corpoJson)
        val blocos = raiz.getJSONArray(0)
        val builder = StringBuilder()
        for (i in 0 until blocos.length()) {
            val bloco = blocos.optJSONArray(i) ?: continue
            builder.append(bloco.optString(0, ""))
        }
        return builder.toString()
    }
}

/** Idiomas de destino oferecidos no seletor de tradução, com nome exibido em português. */
data class IdiomaTraducao(val codigo: String, val nome: String)

val IDIOMAS_TRADUCAO = listOf(
    IdiomaTraducao("pt", "Português"),
    IdiomaTraducao("en", "Inglês"),
    IdiomaTraducao("es", "Espanhol"),
    IdiomaTraducao("fr", "Francês"),
    IdiomaTraducao("de", "Alemão"),
    IdiomaTraducao("it", "Italiano"),
    IdiomaTraducao("ja", "Japonês"),
    IdiomaTraducao("ko", "Coreano"),
)
