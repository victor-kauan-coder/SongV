package com.songv.app.player

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Timer de sono: pausa depois de N minutos (abaixando o volume nos últimos segundos) ou ao fim
 * da faixa atual. Vive fora do ViewModel para continuar valendo com o app em segundo plano.
 */
object TimerSono {

    data class Estado(val terminaEm: Long? = null, val fimDaFaixa: Boolean = false) {
        val ativo: Boolean get() = terminaEm != null || fimDaFaixa
        fun restanteMs(): Long = terminaEm?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0) } ?: 0
    }

    private const val FADE_MS = 6_000L

    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var ouvinte: Player.Listener? = null

    fun emMinutos(player: ExoPlayer, minutos: Int) {
        cancelar(player)
        val total = minutos * 60_000L
        _estado.value = Estado(terminaEm = SystemClock.elapsedRealtime() + total)
        job = escopo.launch {
            delay((total - FADE_MS).coerceAtLeast(0))
            val passos = 30
            for (i in 1..passos) {
                player.volume = 1f - i / passos.toFloat()
                delay(FADE_MS / passos)
            }
            player.pause()
            player.volume = 1f
            _estado.value = Estado()
        }
    }

    fun aoFimDaFaixa(player: ExoPlayer) {
        cancelar(player)
        player.pauseAtEndOfMediaItems = true
        ouvinte = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) cancelar(player)
            }
        }.also(player::addListener)
        _estado.value = Estado(fimDaFaixa = true)
    }

    fun cancelar(player: ExoPlayer) {
        job?.cancel()
        job = null
        ouvinte?.let(player::removeListener)
        ouvinte = null
        player.pauseAtEndOfMediaItems = false
        player.volume = 1f
        _estado.value = Estado()
    }
}
