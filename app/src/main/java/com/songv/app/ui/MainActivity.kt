package com.songv.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.songv.app.player.MusicPlaybackService
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.screens.ArtistaScreen
import com.songv.app.ui.screens.BibliotecaScreen
import com.songv.app.ui.screens.ConfiguracoesScreen
import com.songv.app.ui.screens.CriarPlaylistDialog
import com.songv.app.ui.screens.FilaScreen
import com.songv.app.ui.screens.PermissaoScreen
import com.songv.app.ui.screens.PlayerScreen
import com.songv.app.ui.screens.PlaylistScreen
import com.songv.app.ui.theme.RoxoDarkPrimaryPublico
import com.songv.app.ui.theme.SongVTheme

/**
 * Cada tela sobreposta à Biblioteca (que fica sempre no fundo da pilha, implícita). Usamos uma
 * pilha real ([List] como backstack) em vez de uma única variável, para que "voltar" sempre volte
 * pra tela anterior de fato — por exemplo Playlist → Player → Fila, voltar da Fila cai no Player,
 * e voltar do Player cai de volta na Playlist, não direto na Biblioteca.
 */
private sealed class Tela {
    data object Player : Tela()
    data object Fila : Tela()
    data object Configuracoes : Tela()
    data class Artista(val nome: String) : Tela()
    data class PlaylistDetalhe(val id: String) : Tela()
}

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private fun permissaoNecessaria(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun temPermissao(): Boolean {
        return ContextCompat.checkSelfPermission(this, permissaoNecessaria()) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inicia o [MusicPlaybackService] a partir da Activity, no [onCreate] — ponto do ciclo de
     * vida em que o app está garantidamente em primeiro plano. Iniciar o foreground service a
     * partir daqui (em vez de reagir a "a música começou a tocar" em algum listener de background)
     * evita o [android.app.ForegroundServiceStartNotAllowedException] que o Android 12+ pode
     * lançar quando um app tenta subir um foreground service fora do primeiro plano — exceção que,
     * se não capturada, derruba o processo inteiro sem aviso.
     */
    private fun iniciarServicoDeNotificacao() {
        try {
            val intent = Intent(this, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // A reprodução em si não depende do serviço: sem ele, só não teremos notificação.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Precisa ser chamado antes de super.onCreate. A splash nativa fica visível até
        // preferenciasCarregadas virar true, evitando qualquer flash do tema padrão antes do
        // tema salvo pelo usuário ser aplicado.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !viewModel.estado.value.preferenciasCarregadas }

        super.onCreate(savedInstanceState)

        // Ativa modo edge-to-edge: conteudo cobre toda a tela incluindo status/nav bars
        enableEdgeToEdge()

        iniciarServicoDeNotificacao()

        setContent {
            var permissaoConcedida by remember { mutableStateOf(temPermissao()) }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { concedida ->
                permissaoConcedida = concedida
                if (concedida) viewModel.carregarBiblioteca()
            }

            // Notificação de mídia (capa, título, play/pause) exige permissão explícita a partir
            // do Android 13. Sem ela o app funciona normalmente, só não mostra a notificação.
            val launcherNotificacao = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* concedida ou não, a reprodução continua funcionando normalmente */ }

            LaunchedEffect(permissaoConcedida) {
                if (permissaoConcedida) {
                    viewModel.carregarBiblioteca()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val jaTemNotificacao = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!jaTemNotificacao) {
                            launcherNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }

            val estado by viewModel.estado.collectAsState()

            // Pilha de navegação: a Biblioteca é sempre a base implícita (índice -1, por assim
            // dizer). Cada push empilha uma tela nova; "voltar" sempre faz pop, revelando
            // exatamente a tela anterior — nunca pula direto pra Biblioteca.
            var pilha by remember { mutableStateOf<List<Tela>>(emptyList()) }
            val telaAtual = pilha.lastOrNull()

            fun empilhar(tela: Tela) {
                pilha = pilha + tela
            }
            fun voltar() {
                if (pilha.isNotEmpty()) pilha = pilha.dropLast(1)
            }

            var mostrarCriarPlaylist by remember { mutableStateOf(false) }

            // Gesto/botão físico de voltar do Android segue a mesma pilha: fecha diálogos abertos
            // primeiro, senão faz pop da tela sobreposta atual, senão comportamento padrão do
            // sistema (minimizar o app) quando já está na Biblioteca.
            BackHandler(enabled = mostrarCriarPlaylist) { mostrarCriarPlaylist = false }
            BackHandler(enabled = !mostrarCriarPlaylist && pilha.isNotEmpty()) { voltar() }

            val corPersonalizada = estado.corPersonalizadaArgb?.let { Color(it) } ?: RoxoDarkPrimaryPublico

            SongVTheme(tema = estado.tema, corPersonalizada = corPersonalizada, modoLuminosidade = estado.modoLuminosidade) {
                if (!permissaoConcedida) {
                    PermissaoScreen(onSolicitarPermissao = { launcher.launch(permissaoNecessaria()) })
                } else {
                    BibliotecaScreen(
                        estado = estado,
                        onMusicaClick = { indice ->
                            viewModel.tocarMusica(indice)
                            empilhar(Tela.Player)
                        },
                        onAtualizarClick = { viewModel.carregarBiblioteca() },
                        onConfiguracoesClick = { empilhar(Tela.Configuracoes) },
                        onFaixaAtualClick = { empilhar(Tela.Player) },
                        onFavoritoClick = { id -> viewModel.alternarFavorito(id) },
                        onBuscaChange = { termo -> viewModel.definirTermoBusca(termo) },
                        onArtistaClick = { nomeArtista -> empilhar(Tela.Artista(nomeArtista)) },
                        onPlaylistClick = { idPlaylist -> empilhar(Tela.PlaylistDetalhe(idPlaylist)) },
                        onCriarPlaylistClick = { mostrarCriarPlaylist = true }
                    )

                    AnimatedVisibility(
                        visible = telaAtual is Tela.Player,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(400)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        )
                    ) {
                        PlayerScreen(
                            estado = estado,
                            onVoltar = { voltar() },
                            onPlayPause = viewModel::alternarPlayPause,
                            onProxima = viewModel::proxima,
                            onAnterior = viewModel::anterior,
                            onSeek = viewModel::buscarPosicao,
                            onFavoritoClick = { id -> viewModel.alternarFavorito(id) },
                            onEmbaralharClick = viewModel::alternarEmbaralhado,
                            onRepeticaoClick = viewModel::alternarModoRepeticao,
                            onFilaClick = { empilhar(Tela.Fila) },
                            onTraduzirLetra = { musica -> viewModel.traduzirLetraAtual(musica) },
                            onAlternarTraducao = { viewModel.alternarMostrarTraducao() },
                            onEscolherIdiomaTraducao = { codigo -> viewModel.definirIdiomaTraducao(codigo) }
                        )
                    }

                    AnimatedVisibility(
                        visible = telaAtual is Tela.Fila,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(400)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        )
                    ) {
                        FilaScreen(
                            faixaAtual = estado.faixaAtual,
                            proximasNaFila = estado.proximasNaFila,
                            indiceFilaAtual = estado.indiceFilaAtual,
                            onVoltar = { voltar() },
                            onTocarIndice = { indice -> viewModel.tocarNaFilaAtual(indice) },
                            onMover = { de, para -> viewModel.moverNaFila(de, para) }
                        )
                    }

                    val telaArtista = telaAtual as? Tela.Artista
                    AnimatedVisibility(
                        visible = telaArtista != null,
                        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300))
                    ) {
                        if (telaArtista != null) {
                            val musicasDoArtista = estado.musicasPorArtista.firstOrNull { it.first == telaArtista.nome }?.second ?: emptyList()
                            ArtistaScreen(
                                nomeArtista = telaArtista.nome,
                                musicas = musicasDoArtista,
                                onVoltar = { voltar() },
                                onTocarTudo = {
                                    viewModel.tocarLista(musicasDoArtista, 0)
                                    empilhar(Tela.Player)
                                },
                                onMusicaClick = { indiceLocal ->
                                    viewModel.tocarLista(musicasDoArtista, indiceLocal)
                                    empilhar(Tela.Player)
                                }
                            )
                        }
                    }

                    val telaPlaylist = telaAtual as? Tela.PlaylistDetalhe
                    AnimatedVisibility(
                        visible = telaPlaylist != null,
                        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300))
                    ) {
                        val playlist = telaPlaylist?.let { tp -> estado.playlists.firstOrNull { it.id == tp.id } }
                        if (playlist != null) {
                            val musicasDaPlaylist = estado.musicasDaPlaylist(playlist)
                            PlaylistScreen(
                                playlist = playlist,
                                musicas = musicasDaPlaylist,
                                todasAsMusicas = estado.biblioteca,
                                onVoltar = { voltar() },
                                onTocarTudo = {
                                    viewModel.tocarLista(musicasDaPlaylist, 0)
                                    empilhar(Tela.Player)
                                },
                                onMusicaClick = { indiceLocal ->
                                    viewModel.tocarLista(musicasDaPlaylist, indiceLocal)
                                    empilhar(Tela.Player)
                                },
                                onRemoverMusica = { idMusica -> viewModel.removerMusicaDaPlaylist(playlist.id, idMusica) },
                                onAdicionarMusica = { idMusica -> viewModel.adicionarMusicaNaPlaylist(playlist.id, idMusica) },
                                onRenomear = { novoNome -> viewModel.renomearPlaylist(playlist.id, novoNome) },
                                onExcluir = {
                                    viewModel.excluirPlaylist(playlist.id)
                                    voltar()
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = telaAtual is Tela.Configuracoes,
                        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300))
                    ) {
                        ConfiguracoesScreen(
                            temaAtual = estado.tema,
                            corPersonalizadaAtual = corPersonalizada,
                            modoLuminosidade = estado.modoLuminosidade,
                            onVoltar = { voltar() },
                            onTemaEscolhido = { tema -> viewModel.definirTema(tema) },
                            onCorPersonalizadaEscolhida = { cor -> viewModel.definirCorPersonalizada(cor.toArgb()) },
                            onAlternarLuminosidade = { viewModel.alternarLuminosidade() }
                        )
                    }

                    if (mostrarCriarPlaylist) {
                        CriarPlaylistDialog(
                            onConfirmar = { nome -> viewModel.criarPlaylist(nome) },
                            onFechar = { mostrarCriarPlaylist = false }
                        )
                    }
                }
            }
        }
    }
}
