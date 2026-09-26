package com.songv.app.player

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.songv.app.data.MusicaRepository
import com.songv.app.data.PreferenciasRepository
import com.songv.app.data.ResultadoTraducao
import com.songv.app.data.TradutorLetraService
import com.songv.app.model.Musica
import com.songv.app.model.Playlist
import com.songv.app.ui.theme.ModoLuminosidade
import com.songv.app.ui.theme.TemaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Constrói o [MediaItem] completo desta música: URI de reprodução + metadata (título, artista,
 * capa). É a metadata que faz a notificação de mídia do sistema (estilo Spotify) mostrar a capa,
 * o título e o artista da faixa, além de habilitar os botões de play/pause/próxima na notificação
 * — sem isso, o MediaSession não tem o que exibir e a notificação fica genérica ou nem aparece.
 *
 * A conversão da capa em JPEG é um trabalho de CPU relativamente caro quando repetido para uma
 * biblioteca inteira; [CacheMediaItem] garante que cada música só passa por isso uma vez, mesmo
 * que apareça em várias listas (biblioteca, uma playlist, os resultados de busca, etc).
 */
private object CacheMediaItem {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, MediaItem>()

    fun obterOuCriar(musica: Musica): MediaItem {
        return cache.getOrPut(musica.id) { musica.construirMediaItem() }
    }
}

private fun Musica.construirMediaItem(): MediaItem {
    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(titulo)
        .setArtist(artista)

    capa?.let { bitmap ->
        try {
            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            }
            metadataBuilder.setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        } catch (e: Exception) {
            // Sem capa na notificação não é um erro que deva interromper a reprodução.
        }
    }

    return MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

private fun Musica.paraMediaItem(): MediaItem = CacheMediaItem.obterOuCriar(this)

/** Modo de repetição da fila, espelhando os modos do ExoPlayer de forma legível na UI. */
enum class ModoRepeticao { DESLIGADO, REPETIR_TUDO, REPETIR_UMA }

/**
 * Estado observável da tela: biblioteca carregada + faixa/estado de reprodução atuais.
 *
 * [biblioteca] é sempre o acervo completo de músicas do dispositivo (não muda ao tocar uma
 * playlist ou artista específico). [filaReproducao] é a lista efetivamente carregada no
 * ExoPlayer no momento — pode ser a biblioteca inteira, uma playlist, ou as faixas de um artista.
 * [indiceFilaAtual] é sempre relativo a [filaReproducao].
 */
data class EstadoPlayer(
    val carregandoBiblioteca: Boolean = true,
    val preferenciasCarregadas: Boolean = false,
    val biblioteca: List<Musica> = emptyList(),
    val filaReproducao: List<Musica> = emptyList(),
    val faixaAtual: Musica? = null,
    val indiceFilaAtual: Int = -1,
    val tocando: Boolean = false,
    val posicaoMs: Long = 0L,
    val tema: TemaApp = TemaApp.ROXO_DARK,
    val corPersonalizadaArgb: Int? = null,
    val modoLuminosidade: ModoLuminosidade = ModoLuminosidade.ESCURO,
    val favoritos: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val termoBusca: String = "",
    val embaralhado: Boolean = false,
    val modoRepeticao: ModoRepeticao = ModoRepeticao.DESLIGADO,
    val erro: String? = null,
    /**
     * Biblioteca agrupada por artista, já calculada e armazenada (não é um `get()` computado).
     * É recalculada só quando [biblioteca] muda de fato — ver [PlayerViewModel.carregarBiblioteca]
     * — e não a cada atualização de [posicaoMs] (5x por segundo durante a reprodução), que era o
     * que causava recalcular esse agrupamento sem necessidade e travar a tela inicial.
     */
    val musicasPorArtista: List<Pair<String, List<Musica>>> = emptyList(),
    // ---- Tradução de letra (recurso opcional, sob demanda) ----
    /** Idioma de destino escolhido pelo usuário pra tradução (código ISO 639-1), padrão português. */
    val idiomaTraducao: String = "pt",
    /** true enquanto uma tradução está sendo buscada, pra mostrar um indicador de carregamento. */
    val traduzindo: Boolean = false,
    /** Mensagem de erro da última tentativa de tradução (sem internet, falha de rede, etc). */
    val erroTraducao: String? = null,
    /**
     * Cache simples: id da música + idioma -> linhas traduzidas, na mesma ordem/tamanho de
     * [Musica.letra]. Evita rechamar a API toda vez que o usuário reabre o painel de letra.
     */
    val letrasTraduzidas: Map<String, List<String>> = emptyMap(),
    /** true quando o usuário pediu pra ver a tradução (alterna com o texto original). */
    val mostrandoTraducao: Boolean = false
) {
    /** Chave de cache para a música+idioma atualmente selecionados. */
    fun chaveTraducao(idMusica: String): String = "$idMusica|$idiomaTraducao"
    /** Biblioteca filtrada pelo termo de busca (por título ou artista, sem diferenciar maiúsculas/acentos simples). */
    val bibliotecaFiltrada: List<Musica>
        get() = if (termoBusca.isBlank()) {
            biblioteca
        } else {
            val termo = termoBusca.trim().lowercase()
            biblioteca.filter {
                it.titulo.lowercase().contains(termo) || it.artista.lowercase().contains(termo)
            }
        }

    /** Fila de reprodução na ordem real do player: a faixa atual e tudo que vem depois dela. */
    val proximasNaFila: List<Musica>
        get() = if (indiceFilaAtual in filaReproducao.indices) filaReproducao.drop(indiceFilaAtual + 1) else emptyList()

    /** Lista de nomes de artistas distintos, na mesma ordem de [musicasPorArtista]. */
    val artistas: List<String>
        get() = musicasPorArtista.map { it.first }

    /** Resolve os objetos [Musica] de uma [Playlist] a partir dos ids salvos, preservando a ordem. */
    fun musicasDaPlaylist(playlist: Playlist): List<Musica> {
        val porId = biblioteca.associateBy { it.id }
        return playlist.musicasIds.mapNotNull { porId[it] }
    }
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repositorio = MusicaRepository(application)
    private val preferencias = PreferenciasRepository(application)
    private val tradutor = TradutorLetraService(application)

    val exoPlayer: ExoPlayer = PlayerHolder.obterExoPlayer(application)

    private val _estado = MutableStateFlow(EstadoPlayer())
    val estado: StateFlow<EstadoPlayer> = _estado.asStateFlow()

    private var jobProgresso: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _estado.value = _estado.value.copy(tocando = isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val novoIndice = exoPlayer.currentMediaItemIndex
                val musica = _estado.value.filaReproducao.getOrNull(novoIndice)
                _estado.value = _estado.value.copy(faixaAtual = musica, indiceFilaAtual = novoIndice)
            }
        })
        iniciarLoopProgresso()
        observarPreferencias()
    }

    private fun observarPreferencias() {
        viewModelScope.launch {
            preferencias.temaFlow.collect { tema ->
                _estado.value = _estado.value.copy(tema = tema)
                marcarPreferenciasCarregadasSeProntas()
            }
        }
        viewModelScope.launch {
            preferencias.corCustomFlow.collect { argb ->
                _estado.value = _estado.value.copy(corPersonalizadaArgb = argb)
            }
        }
        viewModelScope.launch {
            preferencias.favoritosFlow.collect { favoritos ->
                _estado.value = _estado.value.copy(favoritos = favoritos)
            }
        }
        viewModelScope.launch {
            preferencias.luminosidadeFlow.collect { modo ->
                _estado.value = _estado.value.copy(modoLuminosidade = modo)
                marcarPreferenciasCarregadasSeProntas()
            }
        }
        viewModelScope.launch {
            preferencias.playlistsFlow.collect { playlists ->
                _estado.value = _estado.value.copy(playlists = playlists)
            }
        }
    }

    /**
     * Sinaliza [EstadoPlayer.preferenciasCarregadas] assim que o tema real (persistido) chegou —
     * usado pela splash screen do sistema para não liberar a UI antes da cor correta estar
     * disponível, evitando o "flash" do tema padrão antes de trocar para o tema salvo.
     */
    private fun marcarPreferenciasCarregadasSeProntas() {
        // A primeira emissão de cada flow já reflete o valor persistido (DataStore emite o valor
        // salvo na primeira coleta, não um default seguido de atualização), então basta saber que
        // já tivemos pelo menos uma emissão.
        if (!_estado.value.preferenciasCarregadas) {
            _estado.value = _estado.value.copy(preferenciasCarregadas = true)
        }
    }

    /** Varre a biblioteca local (pasta "Music" por padrão) e popula a fila do ExoPlayer. */
    fun carregarBiblioteca(pastaFiltro: String? = MusicaRepository.PASTA_PADRAO) {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(carregandoBiblioteca = true, erro = null)
            try {
                val musicas = repositorio.varrer(pastaFiltro)
                // Construir os MediaItems comprime a capa de cada música em JPEG — trabalho de
                // CPU que, feito na main thread pra uma biblioteca inteira de uma vez, travaria a
                // tela; withContext tira isso do caminho da UI.
                val mediaItems = withContext(Dispatchers.Default) { musicas.map { it.paraMediaItem() } }
                exoPlayer.setMediaItems(mediaItems)
                exoPlayer.prepare()
                val agrupadoPorArtista = musicas.groupBy { it.artista }
                    .toSortedMap(compareBy { it.lowercase() })
                    .map { (artista, musicasDoArtista) -> artista to musicasDoArtista }
                _estado.value = _estado.value.copy(
                    carregandoBiblioteca = false,
                    biblioteca = musicas,
                    filaReproducao = musicas,
                    musicasPorArtista = agrupadoPorArtista,
                    erro = if (musicas.isEmpty()) "Nenhum MP3 encontrado na pasta configurada." else null
                )
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(
                    carregandoBiblioteca = false,
                    erro = "Erro ao varrer a biblioteca: ${e.message}"
                )
            }
        }
    }

    /**
     * Toca a música no [indice] absoluto da biblioteca completa. Se a fila atual do ExoPlayer não
     * for a biblioteca inteira (por exemplo, estava tocando uma playlist), a fila é primeiro
     * restaurada para a biblioteca completa antes de buscar o índice.
     */
    fun tocarMusica(indice: Int) {
        val biblioteca = _estado.value.biblioteca
        if (indice !in biblioteca.indices) return

        if (_estado.value.filaReproducao !== biblioteca) {
            val mediaItems = biblioteca.map { it.paraMediaItem() }
            exoPlayer.setMediaItems(mediaItems, indice, 0L)
            exoPlayer.prepare()
            _estado.value = _estado.value.copy(filaReproducao = biblioteca)
        } else {
            exoPlayer.seekTo(indice, 0L)
        }

        exoPlayer.playWhenReady = true
        exoPlayer.play()
        _estado.value = _estado.value.copy(
            faixaAtual = biblioteca[indice],
            indiceFilaAtual = indice
        )
    }

    /**
     * Toca a música no [indice] da [EstadoPlayer.filaReproducao] atual — usada pela tela de Fila,
     * que sempre opera sobre o que está tocando agora (seja a biblioteca inteira, uma playlist ou
     * um artista), diferente de [tocarMusica] que sempre parte da biblioteca completa.
     */
    fun tocarNaFilaAtual(indice: Int) {
        val fila = _estado.value.filaReproducao
        if (indice !in fila.indices) return
        exoPlayer.seekTo(indice, 0L)
        exoPlayer.playWhenReady = true
        exoPlayer.play()
        _estado.value = _estado.value.copy(
            faixaAtual = fila[indice],
            indiceFilaAtual = indice
        )
    }

    fun alternarPlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun proxima() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
    }

    fun anterior() {
        if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
        else exoPlayer.seekTo(0L)
    }

    fun buscarPosicao(ms: Long) {
        exoPlayer.seekTo(ms)
        _estado.value = _estado.value.copy(posicaoMs = ms)
    }

    // ---- Tema e cor personalizada (persistidos) ----

    fun definirTema(tema: TemaApp) {
        viewModelScope.launch { preferencias.salvarTema(tema) }
    }

    fun definirCorPersonalizada(argb: Int) {
        viewModelScope.launch {
            preferencias.salvarCorCustom(argb)
            preferencias.salvarTema(TemaApp.PERSONALIZADO)
        }
    }

    /** Alterna claro/escuro sem alterar o [TemaApp] (cor de destaque) selecionado. */
    fun alternarLuminosidade() {
        viewModelScope.launch { preferencias.alternarLuminosidade() }
    }

    // ---- Favoritos ----

    fun alternarFavorito(idMusica: String) {
        viewModelScope.launch { preferencias.alternarFavorito(idMusica) }
    }

    // ---- Playlists ----

    fun criarPlaylist(nome: String) {
        val nomeLimpo = nome.trim()
        if (nomeLimpo.isEmpty()) return
        viewModelScope.launch { preferencias.criarPlaylist(nomeLimpo) }
    }

    fun renomearPlaylist(idPlaylist: String, novoNome: String) {
        val nomeLimpo = novoNome.trim()
        if (nomeLimpo.isEmpty()) return
        viewModelScope.launch { preferencias.renomearPlaylist(idPlaylist, nomeLimpo) }
    }

    fun excluirPlaylist(idPlaylist: String) {
        viewModelScope.launch { preferencias.excluirPlaylist(idPlaylist) }
    }

    fun adicionarMusicaNaPlaylist(idPlaylist: String, idMusica: String) {
        viewModelScope.launch { preferencias.adicionarMusicaNaPlaylist(idPlaylist, idMusica) }
    }

    fun removerMusicaDaPlaylist(idPlaylist: String, idMusica: String) {
        viewModelScope.launch { preferencias.removerMusicaDaPlaylist(idPlaylist, idMusica) }
    }

    /**
     * Toca uma lista arbitrária de músicas (playlist ou faixas de um artista), substituindo a
     * fila do ExoPlayer por ela e começando pelo [indiceInicial]. A biblioteca completa
     * ([EstadoPlayer.biblioteca]) não é afetada — a tela inicial continua mostrando todas as
     * músicas normalmente, só a reprodução em si passa a seguir essa lista específica.
     */
    fun tocarLista(musicas: List<Musica>, indiceInicial: Int = 0) {
        if (musicas.isEmpty()) return
        val indice = indiceInicial.coerceIn(0, musicas.lastIndex)
        val mediaItems = musicas.map { it.paraMediaItem() }
        exoPlayer.setMediaItems(mediaItems, indice, 0L)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.play()
        _estado.value = _estado.value.copy(
            filaReproducao = musicas,
            faixaAtual = musicas[indice],
            indiceFilaAtual = indice
        )
    }

    // ---- Busca ----

    fun definirTermoBusca(termo: String) {
        _estado.value = _estado.value.copy(termoBusca = termo)
    }

    // ---- Shuffle / repeat ----

    fun alternarEmbaralhado() {
        val novoValor = !exoPlayer.shuffleModeEnabled
        exoPlayer.shuffleModeEnabled = novoValor
        _estado.value = _estado.value.copy(embaralhado = novoValor)
    }

    fun alternarModoRepeticao() {
        val proximo = when (_estado.value.modoRepeticao) {
            ModoRepeticao.DESLIGADO -> ModoRepeticao.REPETIR_TUDO
            ModoRepeticao.REPETIR_TUDO -> ModoRepeticao.REPETIR_UMA
            ModoRepeticao.REPETIR_UMA -> ModoRepeticao.DESLIGADO
        }
        exoPlayer.repeatMode = when (proximo) {
            ModoRepeticao.DESLIGADO -> Player.REPEAT_MODE_OFF
            ModoRepeticao.REPETIR_TUDO -> Player.REPEAT_MODE_ALL
            ModoRepeticao.REPETIR_UMA -> Player.REPEAT_MODE_ONE
        }
        _estado.value = _estado.value.copy(modoRepeticao = proximo)
    }

    // ---- Fila reordenável ----

    /**
     * Move uma faixa da fila (posição absoluta em [EstadoPlayer.filaReproducao]) para outra
     * posição, refletindo no ExoPlayer e no estado local ao mesmo tempo para manter os dois em
     * sincronia.
     */
    fun moverNaFila(deIndice: Int, paraIndice: Int) {
        val listaAtual = _estado.value.filaReproducao
        if (deIndice !in listaAtual.indices || paraIndice !in listaAtual.indices) return

        exoPlayer.moveMediaItem(deIndice, paraIndice)

        val novaLista = listaAtual.toMutableList()
        val item = novaLista.removeAt(deIndice)
        novaLista.add(paraIndice, item)

        val novoIndiceAtual = exoPlayer.currentMediaItemIndex

        _estado.value = _estado.value.copy(
            filaReproducao = novaLista,
            indiceFilaAtual = novoIndiceAtual,
            faixaAtual = novaLista.getOrNull(novoIndiceAtual)
        )
    }

    // ---- Tradução de letra ----

    /** true se o aparelho tem conexão disponível agora — usado pra decidir se mostra o botão de traduzir. */
    fun temConexaoParaTraducao(): Boolean = tradutor.temConexaoDisponivel()

    fun definirIdiomaTraducao(codigo: String) {
        _estado.value = _estado.value.copy(idiomaTraducao = codigo, mostrandoTraducao = false)
    }

    /** Alterna entre mostrar a letra original e a tradução em cache, sem rebuscar nada. */
    fun alternarMostrarTraducao() {
        _estado.value = _estado.value.copy(mostrandoTraducao = !_estado.value.mostrandoTraducao)
    }

    /**
     * Busca a tradução da letra da [musica] atual no idioma selecionado. Se já estiver em cache
     * pra essa combinação música+idioma, só ativa a exibição sem chamar a rede de novo.
     */
    fun traduzirLetraAtual(musica: Musica) {
        if (musica.letra.isEmpty()) return

        val chave = _estado.value.chaveTraducao(musica.id)
        if (_estado.value.letrasTraduzidas.containsKey(chave)) {
            _estado.value = _estado.value.copy(mostrandoTraducao = true, erroTraducao = null)
            return
        }

        viewModelScope.launch {
            _estado.value = _estado.value.copy(traduzindo = true, erroTraducao = null)

            val resultado = tradutor.traduzir(
                linhas = musica.letra.map { it.texto },
                idiomaDestino = _estado.value.idiomaTraducao
            )

            _estado.value = when (resultado) {
                is ResultadoTraducao.Sucesso -> _estado.value.copy(
                    traduzindo = false,
                    mostrandoTraducao = true,
                    letrasTraduzidas = _estado.value.letrasTraduzidas + (chave to resultado.linhasTraduzidas)
                )
                ResultadoTraducao.SemConexao -> _estado.value.copy(
                    traduzindo = false,
                    erroTraducao = "Sem conexão com a internet. Conecte-se ao wifi ou dados móveis para traduzir."
                )
                ResultadoTraducao.Falha -> _estado.value.copy(
                    traduzindo = false,
                    erroTraducao = "Não foi possível traduzir agora. Tente novamente."
                )
            }
        }
    }

    private fun iniciarLoopProgresso() {
        jobProgresso?.cancel()
        jobProgresso = viewModelScope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    _estado.value = _estado.value.copy(posicaoMs = exoPlayer.currentPosition)
                }
                delay(200L) // granularidade suficiente para destacar a linha de letra certa
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        jobProgresso?.cancel()
        // Não chama exoPlayer.release() aqui: o player agora é compartilhado via PlayerHolder e
        // continua vivo através do MusicPlaybackService (foreground service), permitindo que a
        // música continue tocando com a notificação ativa mesmo se este ViewModel for destruído
        // (ex: reconfiguração de tela) ou a Activity for pra segundo plano.
    }
}
