package com.songv.app.conexao

import android.net.Uri
import com.songv.app.model.FonteLetra
import com.songv.app.model.Letra
import com.songv.app.model.LinhaLetra
import com.songv.app.model.Musica
import com.songv.app.model.TipoLetra
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * As músicas do computador conectado, vistas do celular: a lista, as capas, as letras e os bytes.
 * É o caminho inverso do que o computador faz com a biblioteca do celular — tudo pela mesma
 * conexão cifrada, sob demanda. As faixas viram [Musica] com id `pc:<id>` e URI `songvpc://`,
 * que o player lê pela [com.songv.app.player.FonteDoComputador].
 */
class ComputadorRemoto {

    class Biblioteca(val nome: String, val musicas: List<Musica>) {
        val porId: Map<String, Musica> = musicas.associateBy { it.id }
    }

    private val _biblioteca = MutableStateFlow<Biblioteca?>(null)
    val biblioteca: StateFlow<Biblioteca?> = _biblioteca.asStateFlow()

    /** Manda uma mensagem ao computador; null sem sessão. Definido pela [ConexaoComputador]. */
    @Volatile var enviar: ((JSONObject) -> Boolean)? = null

    fun conectou(envio: (JSONObject) -> Boolean) {
        enviar = envio
        envio(JSONObject().put("t", "biblioteca-pc?"))
    }

    /** Sessão caiu: a lista some e quem espera bytes, capa ou letra desiste na hora. */
    fun desconectou() {
        enviar = null
        _biblioteca.value = null
        synchronized(blocos) { blocos.clear() }
        pedidos.values.forEach { it.falhar() }
        pedidos.clear()
        capas.values.forEach { it.cancel() }
        capas.clear()
        letras.values.forEach { it.cancel() }
        letras.clear()
    }

    // ---- lista ----

    fun receberBiblioteca(m: JSONObject) {
        val faixas = m.optJSONArray("faixas") ?: return
        val musicas = (0 until faixas.length()).mapNotNull { faixas.optJSONObject(it)?.let(::musica) }
        _biblioteca.value = Biblioteca(m.optString("nome").ifBlank { "Computador" }, musicas)
    }

    private fun musica(o: JSONObject): Musica? {
        // O id vira nome de arquivo (cache de capa) e caminho de URI: só hexadecimal.
        val id = o.optString("id").takeIf { ID_VALIDO.matches(it) } ?: return null
        val tamanho = o.optLong("tamanho").takeIf { it > 0 } ?: return null
        return Musica(
            id = PREFIXO_ID + id,
            uri = Uri.parse("$ESQUEMA://faixa/$id"),
            caminho = "$ESQUEMA:$id",
            titulo = o.optString("titulo").ifBlank { "Sem título" },
            artista = o.optString("artista").ifBlank { "Artista desconhecido" },
            album = o.optString("album"),
            artistaAlbum = o.optString("artistaAlbum"),
            ano = o.optInt("ano").takeIf { it > 0 },
            faixa = o.optInt("faixa").takeIf { it > 0 },
            disco = o.optInt("disco").takeIf { it > 0 },
            duracaoMs = o.optLong("duracaoMs"),
            adicionadaEmSeg = o.optLong("adicionada"),
            modificadoSeg = o.optLong("modificado"),
            tamanhoBytes = tamanho,
            temCapa = true,
            tipoLetra = TipoLetra.AUSENTE,
        )
    }

    fun tamanho(idPc: String): Long? = _biblioteca.value?.porId?.get(PREFIXO_ID + idPc)?.tamanhoBytes

    // ---- capas e letras: pedidas uma vez, respondidas pelo computador ----

    private val capas = ConcurrentHashMap<String, CompletableDeferred<ByteArray>>()
    private val letras = ConcurrentHashMap<String, CompletableDeferred<Letra>>()

    /** Bytes da capa (vazio = a faixa não tem capa) ou null se o computador não respondeu. */
    suspend fun capa(idPc: String): ByteArray? = pedir(capas, idPc, "capa-pc?")

    /** A letra do arquivo no computador, ou null se ele não respondeu. */
    suspend fun letra(idPc: String): Letra? = pedir(letras, idPc, "letra-pc?")

    private suspend fun <T> pedir(mapa: ConcurrentHashMap<String, CompletableDeferred<T>>, id: String, tipo: String): T? {
        val envio = enviar ?: return null
        var novo = false
        val espera = mapa.getOrPut(id) { novo = true; CompletableDeferred() }
        if (novo && !envio(JSONObject().put("t", tipo).put("faixa", id))) mapa.remove(id)?.cancel()
        return withTimeoutOrNull(15_000) { runCatching { espera.await() }.getOrNull() }
            .also { if (it == null) mapa.remove(id, espera) }
    }

    /** Capa: `u16 tamanho do id ‖ id ‖ imagem` (a partir de [de] em [q]). */
    fun receberCapa(q: ByteArray, de: Int) {
        if (q.size < de + 2) return
        val n = ((q[de].toInt() and 0xff) shl 8) or (q[de + 1].toInt() and 0xff)
        if (q.size < de + 2 + n) return
        val id = String(q, de + 2, n)
        capas.remove(id)?.complete(q.copyOfRange(de + 2 + n, q.size))
    }

    fun receberLetra(m: JSONObject) {
        val espera = letras.remove(m.optString("faixa")) ?: return
        val l = m.optJSONObject("letra")
        val linhas = l?.optJSONArray("linhas")
        if (l == null || linhas == null || linhas.length() == 0) {
            espera.complete(Letra.AUSENTE)
            return
        }
        val sincronizada = l.optBoolean("sincronizada")
        val lista = (0 until linhas.length()).mapNotNull { i ->
            linhas.optJSONObject(i)?.let { o -> LinhaLetra(o.optString("texto"), if (sincronizada && !o.isNull("t")) o.optLong("t") else null) }
        }
        espera.complete(Letra(lista, if (sincronizada) TipoLetra.SINCRONIZADA else TipoLetra.SIMPLES, FonteLetra.EMBUTIDA))
    }

    // ---- bytes: blocos de 1 MB, os 12 mais recentes em memória ----

    private class Bloco(val inicio: Long, tamanho: Int) {
        val dados = ByteArray(tamanho)
        var recebidos = 0
        @Volatile var falhou = false
        val pronto = CountDownLatch(1)
        fun falhar() { falhou = true; pronto.countDown() }
    }

    private val blocos = object : LinkedHashMap<String, Bloco>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bloco>) = size > 12
    }
    private val pedidos = ConcurrentHashMap<Int, Bloco>()
    private val proximoPedido = AtomicInteger()

    /**
     * O bloco [indice] da faixa, esperando ele chegar (roda na thread de carga do ExoPlayer).
     * Já pede os dois seguintes, para a música não parar esperando a rede.
     */
    fun bloco(idPc: String, indice: Long, tamanhoFaixa: Long): ByteArray {
        val b = garantir(idPc, indice, tamanhoFaixa) ?: throw IOException("Sem conexão com o computador")
        for (k in 1..2) if ((indice + k) * BLOCO < tamanhoFaixa) garantir(idPc, indice + k, tamanhoFaixa)
        if (!b.pronto.await(20, TimeUnit.SECONDS) || b.falhou) {
            synchronized(blocos) { blocos.remove("$idPc#$indice") }
            throw IOException("O computador não mandou a música a tempo")
        }
        return b.dados
    }

    private fun garantir(idPc: String, indice: Long, tamanhoFaixa: Long): Bloco? = synchronized(blocos) {
        val chave = "$idPc#$indice"
        blocos[chave]?.takeIf { !it.falhou }?.let { return it }
        val envio = enviar ?: return null
        val inicio = indice * BLOCO
        val b = Bloco(inicio, minOf(BLOCO.toLong(), tamanhoFaixa - inicio).toInt())
        val pedido = proximoPedido.incrementAndGet()
        blocos[chave] = b
        pedidos[pedido] = b
        val ok = envio(JSONObject().put("t", "ler-pc").put("pedido", pedido).put("faixa", idPc).put("de", inicio).put("tamanho", b.dados.size))
        if (!ok) {
            blocos.remove(chave)
            pedidos.remove(pedido)
            return null
        }
        b
    }

    /** Pedaço de áudio: `u32 pedido ‖ u64 início ‖ bytes` (a partir de [de] em [q]). */
    fun receberAudio(q: ByteArray, de: Int) {
        if (q.size < de + 12) return
        val cab = ByteBuffer.wrap(q, de, 12)
        val pedido = cab.int
        val inicio = cab.long
        val b = pedidos[pedido] ?: return
        val deslocamento = inicio - b.inicio
        val n = q.size - de - 12
        if (deslocamento < 0 || deslocamento + n > b.dados.size) return
        System.arraycopy(q, de + 12, b.dados, deslocamento.toInt(), n)
        b.recebidos += n // um pedido é respondido em ordem, por uma única thread de leitura
        if (b.recebidos >= b.dados.size) {
            pedidos.remove(pedido)
            b.pronto.countDown()
        }
    }

    companion object {
        const val ESQUEMA = "songvpc"
        const val PREFIXO_ID = "pc:"
        const val BLOCO = 1024 * 1024
        private val ID_VALIDO = Regex("[0-9a-f]{1,64}")

        fun ehDoComputador(id: String) = id.startsWith(PREFIXO_ID)
    }
}
