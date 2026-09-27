package com.songv.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.songv.app.SongVApp
import com.songv.app.player.MusicPlaybackService
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.components.EscolherPlaylistSheet
import com.songv.app.ui.components.LocalBiblioteca
import com.songv.app.ui.components.LocalCapas
import com.songv.app.ui.components.LocalCapasProprias
import com.songv.app.ui.components.MenuMusicaSheet
import com.songv.app.ui.components.MiniPlayer
import com.songv.app.ui.screens.AlbumScreen
import com.songv.app.ui.screens.ArtistaScreen
import com.songv.app.ui.screens.BibliotecaScreen
import com.songv.app.ui.screens.BuscaScreen
import com.songv.app.ui.screens.ConfiguracoesScreen
import com.songv.app.ui.screens.FilaScreen
import com.songv.app.ui.screens.InicioScreen
import com.songv.app.ui.screens.PermissaoScreen
import com.songv.app.ui.screens.PesquisaLetraSheet
import com.songv.app.ui.screens.PlayerScreen
import com.songv.app.ui.screens.PlaylistScreen
import com.songv.app.ui.theme.LocalCoresSongV
import com.songv.app.ui.theme.SongVTheme
import com.songv.app.ui.theme.acabamento
import com.songv.app.ui.theme.estiloDesfoque
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import com.songv.app.ui.theme.fundoDoApp
import com.songv.app.ui.theme.superficie
import com.songv.app.ui.theme.corDoDestaque

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()
    private val pedidoAbrirPlayer = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // A splash só sai quando o tema salvo carregou: nada de piscar o tema padrão.
        splash.setKeepOnScreenCondition { !vm.preferencias.value.carregadas }
        enableEdgeToEdge()

        // Serviço comum (não foreground): o Media3 promove a foreground sozinho quando começa a tocar.
        runCatching { startService(Intent(this, MusicPlaybackService::class.java)) }

        if (intent?.action == ACAO_ABRIR_PLAYER) pedidoAbrirPlayer.value = true
        setContent { App(vm, pedidoAbrirPlayer) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACAO_ABRIR_PLAYER) pedidoAbrirPlayer.value = true
    }

    override fun onStop() {
        super.onStop()
        vm.salvarSessao()
    }

    companion object {
        const val ACAO_ABRIR_PLAYER = "com.songv.app.ABRIR_PLAYER"
    }
}

private fun permissaoDeAudio() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun App(vm: PlayerViewModel, pedidoAbrirPlayer: MutableState<Boolean>) {
    val prefs by vm.preferencias.collectAsState()
    val nav = rememberSaveable(saver = Navegador.Saver) { Navegador() }
    val contexto = LocalContext.current
    val activity = contexto as ComponentActivity

    LaunchedEffect(pedidoAbrirPlayer.value) {
        if (pedidoAbrirPlayer.value) {
            nav.filaAberta = false
            nav.playerAberto = true
            pedidoAbrirPlayer.value = false
        }
    }

    SongVTheme(
        modo = prefs.modoTema,
        destaque = corDoDestaque(prefs.destaque, prefs.corPersonalizada),
        fundo = prefs.fundo,
        corFundo = prefs.corFundoPersonalizada,
        estilo = prefs.estilo,
    ) {
        // Ícones da barra de status claros sobre o player (sempre escuro) e conforme o tema no resto.
        val escuro = LocalCoresSongV.current.escuro || nav.playerAberto || nav.filaAberta
        LaunchedEffect(escuro) {
            val estilo = if (escuro) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
            activity.enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
        }

        var concedida by remember { mutableStateOf(temPermissao(contexto)) }
        var negadaDeVez by rememberSaveable { mutableStateOf(false) }
        val pedirNotificacao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val pedirAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            concedida = ok
            negadaDeVez = !ok && !activity.shouldShowRequestPermissionRationale(permissaoDeAudio())
        }

        // Voltou das configurações do sistema? Confere de novo.
        val dono = LocalLifecycleOwner.current
        DisposableEffect(dono) {
            val observador = LifecycleEventObserver { _, evento ->
                if (evento == Lifecycle.Event.ON_RESUME) concedida = temPermissao(contexto)
            }
            dono.lifecycle.addObserver(observador)
            onDispose { dono.lifecycle.removeObserver(observador) }
        }

        LaunchedEffect(concedida) {
            if (!concedida) return@LaunchedEffect
            vm.carregarBiblioteca() // não faz nada se já carregou (girar a tela não recarrega)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Surface (e não Box) para o texto herdar onBackground — fora dele a cor padrão é preta.
        Surface(Modifier.fillMaxSize().fundoDoApp(acabamento), color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
            if (!concedida) {
                PermissaoScreen(
                    negadaDeVez = negadaDeVez,
                    onPermitir = { pedirAudio.launch(permissaoDeAudio()) },
                    onAbrirConfiguracoes = {
                        contexto.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null)),
                        )
                    },
                )
            } else {
                val bib by vm.biblioteca.collectAsState()
                val capas = (contexto.applicationContext as SongVApp).capas
                val capasProprias by capas.personalizadas.collectAsState()
                CompositionLocalProvider(
                    LocalCapas provides capas,
                    LocalCapasProprias provides capasProprias,
                    LocalBiblioteca provides bib.porId,
                ) {
                    Casca(vm, nav)
                }
            }
        }
    }
}

private fun temPermissao(contexto: android.content.Context) =
    ContextCompat.checkSelfPermission(contexto, permissaoDeAudio()) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Casca(vm: PlayerViewModel, nav: Navegador) {
    val rep by vm.reproducao.collectAsState()
    val prefs by vm.preferencias.collectAsState()
    val bib by vm.biblioteca.collectAsState()
    val avisos = remember { SnackbarHostState() }
    val estados = rememberSaveableStateHolder()
    var margemInferior by remember { mutableStateOf(PaddingValues(0.dp)) }
    val acab = acabamento
    val vidro = remember { HazeState() }
    val desfoque = acab.estiloDesfoque(MaterialTheme.colorScheme.surfaceContainerLow)
    val capasProprias = LocalCapasProprias.current

    val escolherImagem = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val alvo = nav.trocarCapa
        nav.trocarCapa = null
        if (uri != null && alvo != null) vm.definirCapa(alvo, uri)
    }
    LaunchedEffect(nav.trocarCapa) {
        if (nav.trocarCapa != null) {
            escolherImagem.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    LaunchedEffect(Unit) {
        vm.mensagens.collect { m ->
            val r = avisos.showSnackbar(m.texto, m.acao, duration = if (m.acao != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) m.aoAgir?.invoke()
        }
    }

    BackHandler(
        enabled = nav.filaAberta || nav.playerAberto || nav.pilha.isNotEmpty() || nav.aba != Aba.INICIO,
    ) { nav.voltar() }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                val atual = rep.atual
                AnimatedVisibility(atual != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    if (atual != null) {
                        MiniPlayer(
                            musica = atual,
                            tocando = rep.tocando,
                            progresso = vm.progresso,
                            onAbrir = { nav.playerAberto = true },
                            onPlayPause = vm::alternarPlayPause,
                            onProxima = vm::proxima,
                            onAnterior = vm::anterior,
                            modifier = Modifier.padding(bottom = 6.dp),
                            fundo = if (acab.translucido) Modifier.hazeChild(vidro, MaterialTheme.shapes.medium) else Modifier,
                        )
                    }
                }
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .then(if (acab.translucido) Modifier.hazeChild(vidro) else Modifier)
                        .superficie(acab, RectangleShape, MaterialTheme.colorScheme.surfaceContainerLow, sobreConteudo = true),
                ) {
                    Aba.entries.forEach { aba ->
                        val ativa = nav.aba == aba
                        NavigationBarItem(
                            selected = ativa,
                            onClick = { nav.trocarAba(aba) },
                            icon = { Icon(if (ativa) aba.iconeAtivo else aba.icone, contentDescription = null) },
                            label = { Text(aba.rotulo) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        SideEffect { margemInferior = padding }
        val alvo: Any = nav.rotaAtual ?: nav.aba
        AnimatedContent(
            targetState = alvo,
            modifier = Modifier.fillMaxSize().then(if (acab.translucido) Modifier.haze(vidro, desfoque) else Modifier),
            transitionSpec = {
                if (initialState is Aba && targetState is Aba) {
                    fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))
                } else {
                    val frente = nav.avancou
                    (slideInHorizontally(tween(260)) { if (frente) it / 5 else -it / 5 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { if (frente) -it / 8 else it / 8 } + fadeOut(tween(160)))
                }
            },
            label = "navegacao",
        ) { destino ->
            estados.SaveableStateProvider(destino.toString()) {
                when (destino) {
                    Aba.INICIO -> InicioScreen(vm, nav, padding)
                    Aba.BUSCAR -> BuscaScreen(vm, nav, padding)
                    Aba.BIBLIOTECA -> BibliotecaScreen(vm, nav, padding)
                    is Rota.Album -> AlbumScreen(vm, nav, destino.chave, padding)
                    is Rota.Artista -> ArtistaScreen(vm, nav, destino.nome, padding)
                    is Rota.Playlist -> PlaylistScreen(vm, nav, destino.id, padding)
                    Rota.Favoritas -> PlaylistScreen(vm, nav, null, padding)
                    Rota.Configuracoes -> ConfiguracoesScreen(vm, nav, padding)
                }
            }
        }
    }

    AnimatedVisibility(
        visible = nav.playerAberto,
        enter = slideInVertically(tween(340)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(240)),
    ) {
        PlayerScreen(vm, nav)
    }
    AnimatedVisibility(
        visible = nav.filaAberta,
        enter = slideInVertically(tween(300)) { it },
        exit = slideOutVertically(tween(250)) { it },
    ) {
        FilaScreen(vm, nav)
    }

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(
            avisos,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (nav.playerAberto || nav.filaAberta) Modifier.navigationBarsPadding().padding(bottom = 96.dp) else Modifier.padding(margemInferior)),
        ) { dados ->
            Snackbar(
                dados,
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
            )
        }
    }

    val estadoLetra by vm.letra.collectAsState()
    if (estadoLetra.pesquisaAberta) {
        PesquisaLetraSheet(vm, onFechar = vm::fecharPesquisaLetra)
    }

    nav.menuMusica?.let { m ->
        val album = bib.albuns.firstOrNull { a -> a.musicas.any { it.id == m.id } }
        MenuMusicaSheet(
            musica = m,
            favoritada = m.id in prefs.favoritos,
            onFechar = { nav.menuMusica = null },
            onTocarAseguir = { vm.tocarAseguir(listOf(m)) },
            onAdicionarFila = { vm.adicionarAFila(listOf(m)) },
            onAdicionarPlaylist = { nav.paraPlaylist = listOf(m) },
            onFavoritar = { vm.alternarFavorito(m.id) },
            onIrAlbum = album?.let { a -> { nav.abrir(Rota.Album(a.chave)) } },
            onIrArtista = { nav.abrir(Rota.Artista(m.artistas.first())) },
            onTrocarCapa = { nav.trocarCapa = listOf(m) },
            onRestaurarCapa = if (m.id in capasProprias) ({ vm.restaurarCapa(listOf(m)) }) else null,
        )
    }
    nav.paraPlaylist?.let { musicas ->
        EscolherPlaylistSheet(
            musicas = musicas,
            playlists = prefs.playlists,
            onFechar = { nav.paraPlaylist = null },
            onEscolher = { p -> vm.adicionarNaPlaylist(p.id, musicas.map { it.id }) },
            onCriar = { nome -> vm.criarPlaylist(nome, musicas.map { it.id }) },
        )
    }
}
