package com.songv.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.songv.app.R
import com.songv.app.ui.MainActivity

/**
 * Serviço em foreground que mantém o áudio tocando com a tela bloqueada ou o app em segundo
 * plano, e expõe os controles na notificação de mídia do sistema (play/pause/próxima/anterior),
 * com capa, título e artista — no mesmo estilo de apps como o Spotify.
 *
 * Usa o ExoPlayer e a MediaSession compartilhados via [PlayerHolder]: é o mesmo player que o
 * [PlayerViewModel] comanda a partir da UI, então tudo que a UI toca aparece automaticamente
 * refletido na notificação, e os botões da notificação controlam a reprodução real do app.
 *
 * A notificação é construída manualmente aqui (em vez de depender só do comportamento implícito
 * do [MediaSessionService]) para garantir que ela sempre apareça de forma previsível em qualquer
 * versão do Android, com canal de notificação criado explicitamente e `startForeground` chamado
 * no momento certo — reagindo a cada mudança de estado do player (tocando, pausado, trocou de
 * faixa) através de um [Player.Listener] próprio.
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var playerListener: Player.Listener? = null

    companion object {
        private const val ID_CANAL_NOTIFICACAO = "songv_reproducao"
        private const val ID_NOTIFICACAO = 1001

        private const val ACAO_TOCAR = "com.songv.app.ACAO_TOCAR"
        private const val ACAO_PAUSAR = "com.songv.app.ACAO_PAUSAR"
        private const val ACAO_PROXIMA = "com.songv.app.ACAO_PROXIMA"
        private const val ACAO_ANTERIOR = "com.songv.app.ACAO_ANTERIOR"
    }

    override fun onCreate() {
        super.onCreate()
        criarCanalDeNotificacao()

        val session = PlayerHolder.obterMediaSession(this)
        mediaSession = session

        // Toda vez que o estado de reprodução muda (tocar, pausar, trocar de faixa), reconstrói
        // e reemite a notificação com as informações atuais — é isso que faz a capa, título e
        // botão de play/pause na notificação ficarem sempre sincronizados com o que está tocando.
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                atualizarNotificacao(session)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                atualizarNotificacao(session)
            }
        }
        playerListener = listener
        session.player.addListener(listener)

        // Se o player já estiver tocando quando o serviço sobe (ex: serviço recriado pelo
        // Android com o app ainda tocando em segundo plano), mostra a notificação imediatamente.
        if (session.player.mediaItemCount > 0) {
            atualizarNotificacao(session)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun criarCanalDeNotificacao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gerenciador = getSystemService(NotificationManager::class.java) ?: return
        val canalExistente = gerenciador.getNotificationChannel(ID_CANAL_NOTIFICACAO)
        if (canalExistente == null) {
            val canal = NotificationChannel(
                ID_CANAL_NOTIFICACAO,
                "Reprodução de música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução do SongV"
                setShowBadge(false)
            }
            gerenciador.createNotificationChannel(canal)
        }
    }

    /**
     * Monta a notificação com a capa, título e artista da faixa atual (via [MediaSession.player])
     * e coloca o serviço em foreground enquanto a música toca. Quando pausada, a notificação
     * continua visível (dispensável pelo usuário) mas o serviço sai de foreground, permitindo que
     * o Android a recolha se precisar de memória — igual o comportamento do Spotify.
     */
    private fun atualizarNotificacao(session: MediaSession) {
        val player = session.player
        val metadata = player.mediaMetadata

        val intentAbrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val capa = metadata.artworkData?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }

        val acaoPlayPause = if (player.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pausar",
                criarPendingIntentAcao(ACAO_PAUSAR)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Tocar",
                criarPendingIntentAcao(ACAO_TOCAR)
            )
        }

        val notificacao: Notification = NotificationCompat.Builder(this, ID_CANAL_NOTIFICACAO)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(metadata.title?.toString() ?: "SongV")
            .setContentText(metadata.artist?.toString() ?: "")
            .setLargeIcon(capa)
            .setContentIntent(intentAbrirApp)
            .setOngoing(player.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Anterior",
                    criarPendingIntentAcao(ACAO_ANTERIOR)
                )
            )
            .addAction(acaoPlayPause)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Próxima",
                    criarPendingIntentAcao(ACAO_PROXIMA)
                )
            )
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        if (player.isPlaying) {
            startForeground(ID_NOTIFICACAO, notificacao)
        } else {
            // Sai do modo foreground mas mantém a notificação visível, pausada — mesmo
            // comportamento do Spotify quando você pausa a música.
            stopForeground(STOP_FOREGROUND_DETACH)
            val gerenciador = getSystemService(NotificationManager::class.java)
            gerenciador?.notify(ID_NOTIFICACAO, notificacao)
        }
    }

    private fun criarPendingIntentAcao(acao: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(acao)
        return PendingIntent.getService(
            this,
            acao.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            ACAO_TOCAR -> player?.play()
            ACAO_PAUSAR -> player?.pause()
            ACAO_PROXIMA -> player?.seekToNextMediaItem()
            ACAO_ANTERIOR -> player?.seekToPreviousMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        // Não libera o PlayerHolder aqui: o Android pode recriar este serviço a qualquer momento
        // enquanto o processo do app continua vivo, e o player deve seguir tocando nesse meio
        // tempo. A liberação de verdade só acontece quando o processo do app é finalizado.
        playerListener?.let { mediaSession?.player?.removeListener(it) }
        mediaSession = null
        super.onDestroy()
    }
}
