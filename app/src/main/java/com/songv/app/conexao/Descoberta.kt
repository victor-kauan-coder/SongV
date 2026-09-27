package com.songv.app.conexao

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Procura computadores com o SongV na rede local (mDNS `_songv._tcp`). Só roda enquanto a folha
 * "Tocar em…" está aberta. O Android resolve um serviço de cada vez, então os achados entram
 * numa fila.
 */
class Descoberta(context: Context) {

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val _achados = MutableStateFlow<List<Computador>>(emptyList())
    val achados: StateFlow<List<Computador>> = _achados

    private var ouvinte: NsdManager.DiscoveryListener? = null
    private val fila = ArrayDeque<NsdServiceInfo>()
    private var resolvendo = false

    @Synchronized
    fun iniciar() {
        if (ouvinte != null) return
        _achados.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(tipo: String) {}
            override fun onDiscoveryStopped(tipo: String) {}
            override fun onStartDiscoveryFailed(tipo: String, erro: Int) { parar() }
            override fun onStopDiscoveryFailed(tipo: String, erro: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) = enfileirar(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _achados.update { lista -> lista.filterNot { it.nome == info.serviceName } }
            }
        }
        ouvinte = l
        runCatching { nsd.discoverServices(TIPO, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { ouvinte = null }
    }

    @Synchronized
    fun parar() {
        ouvinte?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        ouvinte = null
        fila.clear()
    }

    @Synchronized
    private fun enfileirar(info: NsdServiceInfo) {
        fila.addLast(info)
        resolverProximo()
    }

    @Synchronized
    private fun resolverProximo() {
        if (resolvendo) return
        val info = fila.removeFirstOrNull() ?: return
        resolvendo = true
        @Suppress("DEPRECATION")
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, erro: Int) = terminou(null)
                override fun onServiceResolved(i: NsdServiceInfo) = terminou(i)
            })
        }.onFailure { terminou(null) }
    }

    @Synchronized
    private fun terminou(info: NsdServiceInfo?) {
        resolvendo = false
        @Suppress("DEPRECATION")
        val host = info?.host?.hostAddress
        if (info != null && host != null) {
            val id = info.attributes["id"]?.decodeToString().orEmpty()
            val c = Computador(id = id, nome = info.serviceName, host = host, porta = info.port)
            _achados.update { lista -> lista.filterNot { it.id == id || it.nome == c.nome } + c }
        }
        resolverProximo()
    }

    private companion object {
        const val TIPO = "_songv._tcp"
    }
}
