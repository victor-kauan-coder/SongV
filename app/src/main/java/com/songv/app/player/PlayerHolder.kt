package com.songv.app.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * Guarda a única instância de [ExoPlayer] e [MediaSession] do app, compartilhada entre o
 * [PlayerViewModel] (que comanda a reprodução a partir da UI) e o [MusicPlaybackService] (que
 * expõe essa mesma sessão pro sistema operar a notificação de mídia — capa, título, artista e
 * botões de play/pause/próxima/anterior, igual o Spotify).
 *
 * Sem esse compartilhamento, cada lado acabaria criando seu próprio ExoPlayer desconectado do
 * outro: a UI tocaria música num player que o serviço de notificação nunca veria, e a notificação
 * nunca apareceria (ou apareceria vazia). Aqui garantimos que existe um único player de verdade.
 */
object PlayerHolder {

    @Volatile
    private var exoPlayer: ExoPlayer? = null

    @Volatile
    private var mediaSession: MediaSession? = null

    /** Retorna o ExoPlayer compartilhado, criando-o na primeira chamada. */
    fun obterExoPlayer(context: Context): ExoPlayer {
        return exoPlayer ?: synchronized(this) {
            exoPlayer ?: ExoPlayer.Builder(context.applicationContext).build().also {
                exoPlayer = it
            }
        }
    }

    /** Retorna a MediaSession compartilhada (criada em torno do mesmo ExoPlayer), criando-a na primeira chamada. */
    fun obterMediaSession(context: Context): MediaSession {
        val player = obterExoPlayer(context)
        return mediaSession ?: synchronized(this) {
            mediaSession ?: MediaSession.Builder(context.applicationContext, player).build().also {
                mediaSession = it
            }
        }
    }

    /** Libera player e sessão. Chamado quando o app é encerrado de vez (não a cada troca de tela). */
    fun liberar() {
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
