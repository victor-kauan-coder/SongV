package com.songv.app.player

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.songv.app.R

/**
 * Mantém a música tocando em segundo plano e publica a notificação de mídia.
 *
 * A versão anterior montava a notificação na mão e o app chamava `startForegroundService` ao
 * abrir — mas o serviço só entrava em foreground quando algo estava tocando. Com nada tocando,
 * o Android derrubava o app alguns segundos depois de aberto ("did not call startForeground").
 * Agora a sessão é registrada no serviço e o próprio Media3 cuida da notificação e do
 * foreground no momento certo, com o estilo de mídia do sistema (capa, barra de progresso nos
 * controles do Android 13+, tela de bloqueio).
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.canal_reproducao)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_songv) },
        )
        addSession(PlayerHolder.sessao(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = PlayerHolder.sessao(this)

    /** App removido dos recentes: continua se estiver tocando, senão encerra o serviço. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PlayerHolder.player(this)
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }
}
