package com.songv.app.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.songv.app.SongVApp
import androidx.media3.session.MediaSession
import com.songv.app.ui.MainActivity

/**
 * A única instância de [ExoPlayer] e [MediaSession] do app, compartilhada entre o
 * [PlayerViewModel] (UI) e o [MusicPlaybackService] (notificação, tela de bloqueio, Bluetooth).
 */
object PlayerHolder {

    @Volatile private var player: ExoPlayer? = null

    /** O som está saindo pelo computador: o player daqui toca mudo e ninguém deve devolver o volume. */
    @Volatile var saidaNoComputador = false

    /** Volume a aplicar no player local ([v] = volume desejado de 0 a 1). */
    fun volumeLocal(v: Float) = if (saidaNoComputador) 0f else v
    @Volatile private var sessao: MediaSession? = null

    fun player(context: Context): ExoPlayer = player ?: synchronized(this) {
        player ?: ExoPlayer.Builder(context.applicationContext)
            // Pede foco de áudio (pausa quando outro app toca, abaixa o volume em navegação/GPS).
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            // Pausa ao desconectar o fone, em vez de tocar alto no alto-falante.
            .setHandleAudioBecomingNoisy(true)
            // Mantém a CPU acordada com a tela apagada (arquivos locais não precisam de wifi lock).
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // Faixas do computador (songvpc://) chegam pela conexão cifrada; o resto, do aparelho.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(FonteDoComputador.Fabrica(context.applicationContext, (context.applicationContext as SongVApp).computadorRemoto)),
            )
            .build()
            .also { player = it }
    }

    fun sessao(context: Context): MediaSession = sessao ?: synchronized(this) {
        sessao ?: MediaSession.Builder(context.applicationContext, player(context))
            .setSessionActivity(
                PendingIntent.getActivity(
                    context.applicationContext,
                    0,
                    Intent(context.applicationContext, MainActivity::class.java)
                        .setAction(MainActivity.ACAO_ABRIR_PLAYER)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
            .also { sessao = it }
    }
}
