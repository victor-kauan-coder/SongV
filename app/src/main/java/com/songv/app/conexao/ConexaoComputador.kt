package com.songv.app.conexao

import android.content.ContentUris
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.songv.app.letra.TradutorLetra
import com.songv.app.model.LinhaLetra
import com.songv.app.player.PlayerHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

/**
 * Conexão com o SongV do computador (ver docs/desktop.md). Duas coisas independentes:
 *
 * - **Conectado:** o computador navega e toca a biblioteca deste celular (pede a lista, capas,
 *   letras e os bytes das faixas). O som daqui continua saindo daqui.
 * - **Som no computador** ("Tocar em…"): o ExoPlayer segue sendo a fonte da verdade, tocando a
 *   fila em **volume 0**; cada mudança de estado vira uma mensagem e o computador toca o arquivo
 *   original. A cada segundo ele informa a posição e, se o player mudo daqui se afastar, é ele que
 *   se ajusta (em silêncio).
 */
class ConexaoComputador(private val context: Context) {

    sealed interface Estado {
        data object Desconectado : Estado
        data class Conectando(val nome: String) : Estado
        data class Pareando(val nome: String, val codigo: String, val confirmadoAqui: Boolean) : Estado
        /** [somNoComputador]: o som do celular está saindo lá ("Tocar em…"). */
        data class Conectado(val computador: Computador, val somNoComputador: Boolean, val volume: Float) : Estado
    }

    val computadores = Computadores(context)
    private val _estado = MutableStateFlow<Estado>(Estado.Desconectado)
    val estado: StateFlow<Estado> = _estado
    private val _avisos = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val avisos: SharedFlow<String> = _avisos

    /** Cor de destaque do app, mandada ao computador para a tela de lá combinar. */
    @Volatile var destaque: String = "#FF6B1A"

    /** A biblioteca atual do app (por id). O computador só lê faixas que estão nela. */
    @Volatile var biblioteca: () -> Map<String, com.songv.app.model.Musica> = { emptyMap() }

    private val app get() = context.applicationContext as com.songv.app.SongVApp
    @Volatile private var querTocarLa = false
    @Volatile private var silencioso = false

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessao: Job? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var fila: Channel<ByteArray>? = null
    @Volatile private var dados: Channel<ByteArray>? = null
    @Volatile private var decisao: CompletableDeferred<Boolean>? = null
    private val player: ExoPlayer get() = PlayerHolder.player(context)

    // Estado da sessão (lido/escrito na thread principal, exceto onde indicado).
    private var saidaAtiva = false
    private var ajusteInterno = false
    private var ultimoAjuste = 0L
    @Volatile private var latenciaMs = 20L
    @Volatile private var permitidas: Set<String> = emptySet()
    @Volatile private var capaEnviada: String? = null
    @Volatile private var letra: JSONObject? = null

    private class Falha(mensagem: String) : Exception(mensagem)

    // =====================================================================================
    // API para a interface
    // =====================================================================================

    /** Conecta (pareando se preciso). Com [tocarLa], o som passa a sair no computador. */
    fun conectar(c: Computador, tocarLa: Boolean = false, semAvisos: Boolean = false) {
        querTocarLa = tocarLa
        silencioso = semAvisos
        val anterior = sessao
        sessao = escopo.launch {
            // Fecha o socket antes de esperar: a leitura bloqueada da sessão anterior só sai assim.
            encerrarSocket()
            anterior?.cancelAndJoin()
            executar(c)
        }
    }

    fun decidirPareamento(ok: Boolean) {
        val d = decisao
        // Já confirmou aqui e agora desistiu: derruba a tentativa inteira.
        if (d == null || !d.complete(ok) && !ok) cancelar()
    }

    /** "Tocar em…" um computador: se já está conectado a ele, só troca a saída. */
    fun tocarEm(c: Computador) {
        val e = _estado.value
        if (e is Estado.Conectado && e.computador.id == c.id) {
            escopo.launch(Dispatchers.Main) { ativarSaida() }
        } else {
            conectar(c, tocarLa = true)
        }
    }

    /** Reconecta em silêncio ao último computador usado (ao abrir o app). */
    fun conectarAoUltimo() {
        if (_estado.value !is Estado.Desconectado) return
        computadores.ultimo()?.let { conectar(it, tocarLa = false, semAvisos = true) }
    }

    fun desconectar() = cancelar()

    /** Desiste de conectar/parear, ou encerra a conexão. */
    fun cancelar() {
        val s = sessao
        escopo.launch {
            encerrarSocket()
            s?.cancelAndJoin()
        }
    }

    /** Volta o som para o celular, de onde o computador parou (a música continua tocando). */
    fun tocarNoCelular() {
        enviar(JSONObject().put("t", "sair"))
        escopo.launch(Dispatchers.Main) {
            tocarAqui = true
            desativarSaida()
        }
    }

    fun volumeDoComputador(v: Float) {
        enviar(JSONObject().put("t", "volume").put("volume", v.toDouble()))
        (_estado.value as? Estado.Conectado)?.let { _estado.value = it.copy(volume = v) }
    }

    /** A tela de letra do celular mudou (outra faixa, tradução, sincronia): repassa ao computador. */
    fun atualizarLetra(faixaId: String, linhas: List<LinhaLetra>, sincronizada: Boolean, traducao: TradutorLetra.Traducao?, atrasoMs: Long) {
        val a = JSONArray()
        linhas.forEachIndexed { i, l ->
            val o = JSONObject().put("texto", l.texto)
            o.put("t", l.tempoMs ?: JSONObject.NULL)
            traducao?.linhas?.getOrNull(i)?.let { o.put("trad", it) }
            traducao?.romanizacao?.getOrNull(i)?.let { o.put("rom", it) }
            a.put(o)
        }
        val msg = JSONObject().put("t", "letra").put("faixa", faixaId).put("sincronizada", sincronizada)
            .put("atrasoMs", atrasoMs).put("linhas", a)
        letra = msg
        enviar(msg)
    }

    // =====================================================================================
    // Conexão
    // =====================================================================================

    private suspend fun executar(alvo: Computador) {
        _estado.value = Estado.Conectando(alvo.nome)
        try {
            val sock = abrirSocket(alvo.host, alvo.porta)
            socket = sock
            sock.soTimeout = 15_000
            val entrada = DataInputStream(BufferedInputStream(sock.getInputStream(), 64 * 1024))
            val saida = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 64 * 1024))

            val conhecido = computadores.pareados.value.firstOrNull { it.id.isNotEmpty() && it.id == alvo.id }
                ?: computadores.porEndereco(alvo.host, alvo.porta)
            val chave = conhecido?.let { computadores.chave(it.id) }
            val (k, nc, nn, pc) = if (conhecido != null && chave != null) {
                reconectar(entrada, saida, conhecido, chave)
            } else {
                parear(entrada, saida, alvo)
            }

            // Confirmação de chave: cada lado manda "pronto" cifrado.
            val (c2n, n2c) = Cripto.chavesSessao(k, nc, nn)
            val selar = Cripto.Selador(c2n, Cripto.PREFIXO_C2N)
            val abrir = Cripto.Selador(n2c, Cripto.PREFIXO_N2C)
            Quadros.escrever(saida, selar.selar(Quadros.json(JSONObject().put("t", "pronto"))))
            val pronto = runCatching { abrir.abrir(Quadros.ler(entrada)) }.getOrNull()
            if (pronto == null || pronto[0] != Quadros.JSON || JSONObject(String(pronto, 1, pronto.size - 1)).optString("t") != "pronto") {
                throw Falha("O computador não confirmou a chave. Pareie de novo.")
            }
            if (conhecido == null || chave == null) computadores.guardar(pc.copy(pareado = true), k)
            else computadores.atualizar(pc.id, pc.nome, alvo.host, alvo.porta)

            sessao(sock, entrada, saida, selar, abrir, pc.copy(host = alvo.host, porta = alvo.porta, pareado = true))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SongV", "conexão com ${alvo.nome} terminou", e)
            // Reconexão automática ao abrir o app falha calada: o computador pode estar desligado.
            val texto = when {
                e is Falha -> e.message
                silencioso -> null
                e is java.net.SocketTimeoutException -> "O computador não respondeu. Ele está com o SongV aberto?"
                e is java.net.ConnectException || e is java.net.NoRouteToHostException ->
                    "Não achei ${alvo.nome} na rede. Os dois estão na mesma rede?"
                else -> "A conexão com ${alvo.nome} caiu."
            }
            texto?.let { _avisos.tryEmit(it) }
        } finally {
            decisao = null
            encerrarSocket()
            withContext(NonCancellable + Dispatchers.Main) { desativarSaida() }
            _estado.value = Estado.Desconectado
        }
    }

    private data class Chaves(val k: ByteArray, val nc: ByteArray, val nn: ByteArray, val pc: Computador)

    private fun reconectar(entrada: DataInputStream, saida: DataOutputStream, c: Computador, k: ByteArray): Chaves {
        val nc = Cripto.aleatorio(32)
        Quadros.escrever(saida, Quadros.claro(JSONObject()
            .put("t", "ola").put("v", 1).put("modo", "sessao")
            .put("id", computadores.meuId).put("nome", computadores.meuNome).put("nonce", b64(nc))))
        val r = Quadros.lerClaro(Quadros.ler(entrada))
        if (r.optString("t") == "erro") {
            if (r.optString("motivo") == "desconhecido") {
                computadores.esquecer(c.id)
                throw Falha("${c.nome} não reconhece mais este celular. Toque nele de novo para parear.")
            }
            throw Falha("${c.nome} recusou a conexão.")
        }
        if (r.optString("id") != c.id) throw Falha("Outro computador respondeu nesse endereço. Pareie de novo.")
        return Chaves(k, nc, deB64(r.getString("nonce"), 32), c.copy(nome = r.optString("nome", c.nome)))
    }

    private suspend fun parear(entrada: DataInputStream, saida: DataOutputStream, alvo: Computador): Chaves = coroutineScope {
        val efemera = Cripto.Efemera()
        val nc = Cripto.aleatorio(32)
        Quadros.escrever(saida, Quadros.claro(JSONObject()
            .put("t", "ola").put("v", 1).put("modo", "parear")
            .put("id", computadores.meuId).put("nome", computadores.meuNome)
            .put("compromisso", b64(Cripto.compromisso(efemera.publica, nc)))))
        val r = Quadros.lerClaro(Quadros.ler(entrada))
        if (r.optString("t") == "erro") {
            throw Falha(
                if (r.optString("motivo") == "pareamento-fechado") "No computador, clique em “Parear um celular” e tente de novo."
                else "${alvo.nome} recusou o pareamento.",
            )
        }
        val publicaN = deB64(r.getString("pub"), 65)
        val nn = deB64(r.getString("nonce"), 32)
        val pc = Computador(r.getString("id"), r.optString("nome", alvo.nome), alvo.host, alvo.porta)
        Quadros.escrever(saida, Quadros.claro(JSONObject().put("t", "revela").put("pub", b64(efemera.publica)).put("nonce", b64(nc))))

        val z = efemera.acordo(publicaN)
        val codigo = Cripto.codigo(z, nc, nn)
        val k = Cripto.chavePar(z, nc, nn)

        val local = CompletableDeferred<Boolean>().also { decisao = it }
        _estado.value = Estado.Pareando(pc.nome, codigo, confirmadoAqui = false)
        socket?.soTimeout = 0
        val remota = escopo.async { runCatching { Quadros.lerClaro(Quadros.ler(entrada)) }.getOrNull() }
        var okLocal: Boolean? = null
        var okRemoto: Boolean? = null
        withTimeoutOrNull(120_000) {
            while (okLocal == null || okRemoto == null) {
                select<Unit> {
                    if (okLocal == null) local.onAwait { ok ->
                        okLocal = ok
                        Quadros.escrever(saida, Quadros.claro(JSONObject().put("t", "decisao").put("ok", ok)))
                        if (!ok) throw Falha("Pareamento cancelado.")
                        _estado.value = Estado.Pareando(pc.nome, codigo, confirmadoAqui = true)
                    }
                    if (okRemoto == null) remota.onAwait { v ->
                        val ok = v?.optString("t") == "decisao" && v.optBoolean("ok")
                        okRemoto = ok
                        if (!ok) throw Falha("O pareamento foi recusado no computador.")
                    }
                }
            }
        } ?: throw Falha("O tempo para confirmar o código acabou.")
        socket?.soTimeout = 15_000
        Chaves(k, nc, nn, pc)
    }

    /**
     * Amarra o socket à rede local certa: com Wi-Fi "sem internet", o Android manda o tráfego
     * pelos dados móveis e o computador ficaria inalcançável.
     */
    private fun abrirSocket(host: String, porta: Int): Socket {
        val ip = InetAddress.getByName(host)
        // Algumas redes não aceitam o vínculo (EPERM: rede restrita, perfil de trabalho, emulador):
        // aí segue pela rota padrão, que em Wi-Fi comum já é a certa.
        val socket = redePara(ip)?.let { rede -> runCatching { rede.socketFactory.createSocket() }.getOrNull() } ?: Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(ip, porta), 5_000)
        return socket
    }

    private fun redePara(ip: InetAddress): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val redes = cm.allNetworks
        return redes.firstOrNull { n -> cm.getLinkProperties(n)?.linkAddresses?.any { mesmaSubrede(it, ip) } == true }
            ?: redes.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    private fun mesmaSubrede(la: LinkAddress, ip: InetAddress): Boolean {
        val a = la.address
        if (a is Inet4Address != ip is Inet4Address) return false
        val x = a.address
        val y = ip.address
        var bits = la.prefixLength
        var i = 0
        while (bits > 0 && i < x.size) {
            val mascara = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((x[i].toInt() and mascara) != (y[i].toInt() and mascara)) return false
            bits -= 8
            i++
        }
        return true
    }

    private fun encerrarSocket() {
        runCatching { socket?.close() }
        socket = null
        fila?.close()
        fila = null
        dados?.close()
        dados = null
    }

    // =====================================================================================
    // Sessão no ar
    // =====================================================================================

    private suspend fun sessao(
        sock: Socket,
        entrada: DataInputStream,
        saida: DataOutputStream,
        selar: Cripto.Selador,
        abrir: Cripto.Selador,
        pc: Computador,
    ) = coroutineScope {
        // Dados (blocos de 256 KB, capa): no máximo 8 esperando, para não encher a memória se a
        // rede for mais lenta que o disco. Controle: fila própria, que passa na frente.
        val canal = Channel<ByteArray>(capacity = 8)
        val controle = Channel<ByteArray>(Channel.UNLIMITED)
        dados = canal
        fila = controle
        permitidas = emptySet()
        capaEnviada = null
        app.computadorRemoto.conectou { m -> controle.trySend(Quadros.json(m)).isSuccess }
        sock.soTimeout = 20_000 // ping a cada 4 s: silêncio longo é conexão morta
        _estado.value = Estado.Conectado(pc, somNoComputador = false, volume = 1f)
        computadores.marcarUltimo(pc.id)
        silencioso = false

        val escritor = launch(Dispatchers.IO) {
            while (isActive) {
                val claro = controle.tryReceive().getOrNull()
                    ?: select { controle.onReceive { it }; canal.onReceive { it } }
                Quadros.escrever(saida, selar.selar(claro))
            }
        }
        val pedidos = Channel<JSONObject>(Channel.UNLIMITED)
        val servidor = launch(Dispatchers.IO) { servirArquivos(pedidos, canal) }
        val ping = launch {
            while (isActive) {
                enviar(JSONObject().put("t", "ping").put("t0", SystemClock.elapsedRealtime()))
                delay(4_000)
            }
        }
        if (querTocarLa) withContext(Dispatchers.Main) { ativarSaida() }
        try {
            while (isActive) {
                val claro = abrir.abrir(Quadros.ler(entrada))
                when (claro.firstOrNull()) {
                    Quadros.AUDIO -> { app.computadorRemoto.receberAudio(claro, 1); continue }
                    Quadros.CAPA -> { app.computadorRemoto.receberCapa(claro, 1); continue }
                    Quadros.JSON -> Unit
                    else -> continue
                }
                val m = JSONObject(String(claro, 1, claro.size - 1))
                when (m.optString("t")) {
                    "pong" -> {
                        val rtt = SystemClock.elapsedRealtime() - m.optLong("t0")
                        if (rtt in 0..5_000) latenciaMs = (latenciaMs * 3 + rtt) / 4
                    }
                    "estado" -> {
                        (_estado.value as? Estado.Conectado)?.let { e ->
                            val v = m.optDouble("volume", e.volume.toDouble()).toFloat()
                            if (v != e.volume) _estado.value = e.copy(volume = v)
                        }
                        withContext(Dispatchers.Main) {
                            alinhar(m.optString("faixa"), m.optLong("posicaoMs"), m.optBoolean("tocando"), m.optBoolean("carregando"))
                        }
                    }
                    "comando" -> withContext(Dispatchers.Main) { comando(m) }
                    "terminou" -> withContext(Dispatchers.Main) { terminou(m.optString("faixa")) }
                    "ler" -> pedidos.trySend(m)
                    "saida-livre" -> withContext(Dispatchers.Main) {
                        if (saidaAtiva) {
                            desativarSaida()
                            _avisos.tryEmit("${pc.nome} passou a tocar outra música. O celular pausou.")
                        }
                    }
                    "biblioteca" -> launch { enviarBiblioteca() }
                    "letra?" -> launch { enviarLetraDe(m.optString("faixa")) }
                    "capa?" -> launch { enviarCapaDe(m.optString("faixa")) }
                    "biblioteca-pc" -> app.computadorRemoto.receberBiblioteca(m)
                    "letra-pc" -> app.computadorRemoto.receberLetra(m)
                }
            }
        } finally {
            app.computadorRemoto.desconectou()
            ping.cancel()
            servidor.cancel()
            escritor.cancel()
            pedidos.close()
        }
    }

    /** Manda uma mensagem de controle (qualquer thread). Some sem erro se não há sessão. */
    private fun enviar(m: JSONObject) {
        fila?.trySend(Quadros.json(m))
    }

    // ---- bytes do arquivo ----

    private suspend fun servirArquivos(pedidos: Channel<JSONObject>, canal: Channel<ByteArray>) {
        var aberto: Pair<String, Pair<ParcelFileDescriptor, FileChannel>>? = null
        try {
            for (p in pedidos) {
                val id = p.optString("faixa")
                // Só faixas da biblioteca do app (ou as que ele mesmo mandou tocar): nada de
                // arquivos soltos do aparelho.
                if (id !in permitidas && id !in biblioteca()) continue
                if (aberto?.first != id) {
                    aberto?.second?.first?.close()
                    val pfd = context.contentResolver.openFileDescriptor(uriDe(id), "r") ?: continue
                    aberto = id to (pfd to FileInputStream(pfd.fileDescriptor).channel)
                }
                val arquivo = aberto!!.second.second
                val pedido = p.optLong("pedido").toInt()
                var posicao = p.optLong("de")
                var restante = p.optLong("tamanho")
                val buf = ByteBuffer.allocate(BLOCO)
                while (restante > 0) {
                    buf.clear()
                    buf.limit(minOf(BLOCO.toLong(), restante).toInt())
                    val lidos = arquivo.read(buf, posicao)
                    if (lidos <= 0) break
                    val quadro = ByteBuffer.allocate(1 + 4 + 8 + lidos)
                        .put(Quadros.AUDIO).putInt(pedido).putLong(posicao).put(buf.array(), 0, lidos)
                    canal.send(quadro.array())
                    posicao += lidos
                    restante -= lidos
                }
            }
        } catch (_: IOException) {
        } finally {
            aberto?.second?.first?.close()
        }
    }

    private fun uriDe(id: String) = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toLong())

    private fun idDe(item: MediaItem?) = item?.mediaId?.substringBefore('#')

    // ---- player (thread principal) ----

    private val ouvinte = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = enviarTocar()
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = enviarTocar()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = enviarTocar()
        override fun onRepeatModeChanged(repeatMode: Int) = enviarTocar()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            enviar(JSONObject().put("t", if (playWhenReady) "retomar" else "pausar").put("posicaoMs", player.currentPosition))
        }

        override fun onPositionDiscontinuity(antiga: Player.PositionInfo, nova: Player.PositionInfo, reason: Int) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (ajusteInterno) {
                ajusteInterno = false
                return
            }
            // Troca de faixa chega como "tocar"; aqui só busca dentro da mesma faixa.
            if (antiga.mediaItemIndex == nova.mediaItemIndex) {
                enviar(JSONObject().put("t", "buscar").put("posicaoMs", nova.positionMs))
            }
        }
    }

    private fun ativarSaida() {
        if (saidaAtiva) return
        val e = _estado.value as? Estado.Conectado ?: return
        _estado.value = e.copy(somNoComputador = true)
        saidaAtiva = true
        player.addListener(ouvinte)
        PlayerHolder.saidaNoComputador = true
        player.volume = 0f
        player.setHandleAudioBecomingNoisy(false)
        // Mantém o Wi-Fi acordado com a tela apagada: é por ele que o som está saindo.
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        enviarTocar()
    }

    private fun desativarSaida() {
        if (!saidaAtiva) return
        saidaAtiva = false
        player.removeListener(ouvinte)
        // Caiu a conexão com música tocando: pausa antes de devolver o volume, para o som não
        // estourar no alto-falante do celular sem aviso.
        if (!tocarAqui) player.pause()
        PlayerHolder.saidaNoComputador = false
        player.volume = 1f
        player.setHandleAudioBecomingNoisy(true)
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        tocarAqui = false
        (_estado.value as? Estado.Conectado)?.let { _estado.value = it.copy(somNoComputador = false) }
    }

    /** Marcado por [tocarNoCelular]: a música continua, agora pelo celular. */
    @Volatile private var tocarAqui = false

    private fun enviarTocar() {
        val item = player.currentMediaItem ?: return
        val id = idDe(item) ?: return
        val proximoIndice = if (player.repeatMode == Player.REPEAT_MODE_ONE) player.currentMediaItemIndex else player.nextMediaItemIndex
        val proximo = proximoIndice.takeIf { it != C.INDEX_UNSET }?.let { player.getMediaItemAt(it) }
        val posicao = player.currentPosition
        val tocando = player.playWhenReady
        val artwork = item.mediaMetadata.artworkUri
        escopo.launch {
            val atual = meta(item) ?: return@launch
            val prox = proximo?.let { meta(it) }
            permitidas = setOfNotNull(id, prox?.optString("id"))
            enviar(JSONObject().put("t", "tocar").put("faixa", atual).put("posicaoMs", posicao).put("tocando", tocando)
                .put("proxima", prox ?: JSONObject.NULL).put("destaque", destaque))
            if (capaEnviada != id) {
                capaEnviada = id
                enviarCapa(id, artwork)
            }
            letra?.takeIf { it.optString("faixa") == id }?.let { enviar(it) }
        }
    }

    private fun meta(item: MediaItem): JSONObject? {
        val id = idDe(item) ?: return null
        val uri = item.localConfiguration?.uri ?: uriDe(id)
        val colunas = arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.Audio.AudioColumns.DURATION)
        val (tamanho, nomeArquivo, duracao) = runCatching {
            context.contentResolver.query(uri, colunas, null, null, null)?.use { c ->
                if (c.moveToFirst()) Triple(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2)) else null
            }
        }.getOrNull() ?: return null
        val md = item.mediaMetadata
        return JSONObject()
            .put("id", id)
            .put("titulo", md.title?.toString().orEmpty())
            .put("artista", md.artist?.toString().orEmpty())
            .put("album", md.albumTitle?.toString().orEmpty())
            .put("duracaoMs", duracao)
            .put("tamanho", tamanho)
            .put("formato", nomeArquivo.substringAfterLast('.', "mp3"))
    }

    private suspend fun enviarCapa(id: String, artwork: android.net.Uri?) {
        val bytes = artwork?.let { u ->
            runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull()
        }?.takeIf { it.size in 1..(4 * 1024 * 1024) } ?: return
        val idBytes = id.toByteArray()
        val quadro = ByteBuffer.allocate(1 + 2 + idBytes.size + bytes.size)
            .put(Quadros.CAPA).putShort(idBytes.size.toShort()).put(idBytes).put(bytes)
        dados?.send(quadro.array())
    }

    /** Ajuste silencioso: o player mudo daqui segue o computador, nunca o contrário. */
    private fun alinhar(faixa: String, posicaoMs: Long, tocando: Boolean, carregando: Boolean) {
        if (!saidaAtiva || carregando || !tocando || !player.isPlaying) return
        if (idDe(player.currentMediaItem) != faixa) return
        val agora = SystemClock.elapsedRealtime()
        if (agora - ultimoAjuste < 3_000) return
        val alvo = posicaoMs + latenciaMs / 2
        if (abs(alvo - player.currentPosition) > 150 && alvo < player.duration) {
            ajusteInterno = true
            ultimoAjuste = agora
            player.seekTo(alvo)
        }
    }

    private fun comando(m: JSONObject) {
        when (m.optString("acao")) {
            "retomar" -> player.play()
            "pausar" -> player.pause()
            "proxima" -> player.seekToNext()
            "anterior" -> player.seekToPrevious()
            "buscar" -> player.seekTo(m.optLong("posicaoMs"))
            "devolver" -> {
                tocarAqui = true
                tocarNoCelular()
            }
        }
    }

    /** O computador terminou a faixa: se o mudo daqui está quase lá, avança junto. */
    private fun terminou(faixa: String) {
        if (idDe(player.currentMediaItem) != faixa) return
        val falta = player.duration - player.currentPosition
        if (player.duration != C.TIME_UNSET && falta in 0..2_000) {
            if (player.repeatMode == Player.REPEAT_MODE_ONE) player.seekTo(0) else if (player.hasNextMediaItem()) player.seekToNext()
        }
    }

    // ---- biblioteca do celular, navegada pelo computador ----

    private fun enviarBiblioteca() {
        val a = JSONArray()
        biblioteca().values.forEach { m ->
            a.put(
                JSONObject()
                    .put("id", m.id).put("titulo", m.titulo).put("artista", m.artista).put("album", m.album)
                    .put("artistaAlbum", m.artistaAlbum).put("ano", m.ano ?: JSONObject.NULL)
                    .put("faixa", m.faixa ?: JSONObject.NULL).put("disco", m.disco ?: JSONObject.NULL)
                    .put("duracaoMs", m.duracaoMs).put("tamanho", m.tamanhoBytes)
                    .put("formato", m.caminho.substringAfterLast('.', "mp3").lowercase())
                    .put("adicionada", m.adicionadaEmSeg).put("temCapa", app.capas.temCapa(m)),
            )
        }
        enviar(JSONObject().put("t", "biblioteca").put("faixas", a))
    }

    private suspend fun enviarLetraDe(id: String) {
        val m = biblioteca()[id] ?: return
        val l = runCatching { app.letras.carregar(m) }.getOrNull() ?: return
        val a = JSONArray()
        l.linhas.forEach { a.put(JSONObject().put("texto", it.texto).put("t", it.tempoMs ?: JSONObject.NULL)) }
        enviar(JSONObject().put("t", "letra").put("faixa", id).put("sincronizada", l.tipo == com.songv.app.model.TipoLetra.SINCRONIZADA)
            .put("atrasoMs", 0).put("linhas", a))
    }

    private suspend fun enviarCapaDe(id: String) {
        val m = biblioteca()[id] ?: return
        val arquivo = app.capas.garantirArquivo(m) ?: return
        val bytes = runCatching { arquivo.readBytes() }.getOrNull() ?: return
        val idBytes = id.toByteArray()
        dados?.send(ByteBuffer.allocate(1 + 2 + idBytes.size + bytes.size).put(Quadros.CAPA).putShort(idBytes.size.toShort()).put(idBytes).put(bytes).array())
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun deB64(s: String, tamanho: Int): ByteArray {
        val b = Base64.decode(s, Base64.NO_WRAP)
        if (b.size != tamanho) throw Falha("Resposta inválida do computador.")
        return b
    }

    private companion object {
        const val BLOCO = 256 * 1024
    }
}
