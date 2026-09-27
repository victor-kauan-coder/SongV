package com.songv.app.player

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.songv.app.SongVApp
import com.songv.app.data.Destaque
import com.songv.app.data.ExibicaoTraducao
import com.songv.app.data.ModoTema
import com.songv.app.data.Ordenacao
import com.songv.app.data.Preferencias
import com.songv.app.data.Sessao
import com.songv.app.data.comparadorAlfabetico
import com.songv.app.data.normalizarBusca
import com.songv.app.letra.BuscaLetra
import com.songv.app.letra.CandidatoLetra
import com.songv.app.letra.LetraRepository
import com.songv.app.letra.TradutorLetra
import com.songv.app.letra.nomeIdioma
import com.songv.app.model.Album
import com.songv.app.model.Artista
import com.songv.app.model.Letra
import com.songv.app.model.Musica
import com.songv.app.model.Playlist
import com.songv.app.model.TipoLetra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

// ---- Estados expostos para a UI ----

data class EstadoBiblioteca(
    val carregando: Boolean = true,
    val pronta: Boolean = false,
    val lidas: Int = 0,
    val total: Int = 0,
    val musicas: List<Musica> = emptyList(),
    val albuns: List<Album> = emptyList(),
    val artistas: List<Artista> = emptyList(),
    val porId: Map<String, Musica> = emptyMap(),
    val erro: String? = null,
)

/** Uma entrada da fila. [chave] é única mesmo se a mesma música estiver duas vezes na fila. */
data class ItemFila(val chave: String, val musica: Musica, val janela: Int)

enum class ModoRepeticao { DESLIGADO, TUDO, UMA }

data class EstadoReproducao(
    /** Na ordem em que vai tocar — já considerando o modo aleatório. */
    val fila: List<ItemFila> = emptyList(),
    val posicao: Int = -1,
    val tocando: Boolean = false,
    val carregando: Boolean = false,
    val aleatorio: Boolean = false,
    val repeticao: ModoRepeticao = ModoRepeticao.DESLIGADO,
    /** De onde a fila veio ("Álbum · Nome", "Favoritas"…), mostrado no topo do player. */
    val origem: String? = null,
) {
    val atual: Musica? get() = fila.getOrNull(posicao)?.musica
    val aSeguir: List<ItemFila> get() = if (posicao >= 0) fila.subList(posicao + 1, fila.size) else emptyList()
}

data class Progresso(val posicaoMs: Long = 0, val duracaoMs: Long = 0)

data class EstadoLetra(
    val musicaId: String? = null,
    /** null enquanto carrega. */
    val letra: Letra? = null,
    val buscando: Boolean = false,
    val traducao: TradutorLetra.Traducao? = null,
    val traduzindo: Boolean = false,
    val avisoTraducao: String? = null,
    /** Folha de pesquisa manual de letra aberta (título/artista editáveis + lista de versões). */
    val pesquisaAberta: Boolean = false,
)

data class Mensagem(val texto: String, val acao: String? = null, val aoAgir: (() -> Unit)? = null)

@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SongVApp
    private val prefs = app.preferencias
    private val capas = app.capas

    val player: ExoPlayer = PlayerHolder.player(application)

    val preferencias: StateFlow<Preferencias> =
        prefs.preferencias.stateIn(viewModelScope, SharingStarted.Eagerly, Preferencias())

    private val _biblioteca = MutableStateFlow(EstadoBiblioteca())
    val biblioteca: StateFlow<EstadoBiblioteca> = _biblioteca.asStateFlow()

    private val _reproducao = MutableStateFlow(EstadoReproducao())
    val reproducao: StateFlow<EstadoReproducao> = _reproducao.asStateFlow()

    private val _letra = MutableStateFlow(EstadoLetra())
    val letra: StateFlow<EstadoLetra> = _letra.asStateFlow()

    val timer: StateFlow<TimerSono.Estado> = TimerSono.estado

    private val _mensagens = MutableSharedFlow<Mensagem>(extraBufferCapacity = 8)
    val mensagens: SharedFlow<Mensagem> = _mensagens.asSharedFlow()

    /**
     * Posição da faixa, num fluxo separado do resto do estado: só as telas que mostram progresso
     * o observam, e ele só roda enquanto alguém observa. Antes a posição morava no estado geral
     * e o app inteiro recompunha 5 vezes por segundo.
     */
    val progresso: StateFlow<Progresso> = flow {
        while (true) {
            emit(Progresso(player.currentPosition.coerceAtLeast(0), duracaoAtual()))
            delay(if (player.isPlaying) 80 else 400)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), Progresso())

    // mediaId → música, para as faixas que este ViewModel colocou no player.
    private val registro = HashMap<String, Musica>()
    private val contador = AtomicLong(System.currentTimeMillis())
    private var jobBiblioteca: Job? = null
    private var jobLetra: Job? = null
    private var jobTraducao: Job? = null
    private var sessaoVerificada = false
    private var ultimoRegistrado: String? = null
    private var chaveEmContagem: String? = null
    private var jobRegistro: Job? = null

    private val ouvinte = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            ) {
                sincronizarFila()
            } else {
                sincronizarEstado()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                aoTrocarFaixa()
                salvarSessao()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                if (player.isPlaying) registrarReproducao() else salvarSessao()
            }
        }
    }

    /**
     * Relê a biblioteca sozinho quando o Android indexa, apaga ou altera músicas (arquivos
     * copiados com o app aberto, downloads do agente…). Várias mudanças seguidas viram uma só
     * releitura, e ela é barata: o índice em disco evita reler faixas que não mudaram.
     */
    private var jobReleitura: Job? = null
    private val observadorMidia = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!_biblioteca.value.pronta) return
            jobReleitura?.cancel()
            jobReleitura = viewModelScope.launch {
                delay(2_500)
                carregarBiblioteca(forcar = true, silencioso = true)
            }
        }
    }

    init {
        player.addListener(ouvinte)
        runCatching {
            application.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observadorMidia)
        }
        sincronizarFila()
        viewModelScope.launch {
            // Enquanto toca, salva a sessão de tempos em tempos (queda de bateria, app morto pelo sistema…).
            while (isActive) {
                delay(20_000)
                if (player.isPlaying) salvarSessao()
            }
        }
    }

    // =========================================================================
    // Biblioteca
    // =========================================================================

    /** Lê a biblioteca. Sem [forcar], não faz nada se já carregou — girar a tela não reinicia nada. */
    fun carregarBiblioteca(forcar: Boolean = false, silencioso: Boolean = false) {
        if (jobBiblioteca?.isActive == true) return
        if (!forcar && _biblioteca.value.pronta) return
        jobBiblioteca = viewModelScope.launch {
            val p = preferencias.first { it.carregadas }
            // Releitura automática não mostra a barra de progresso: a lista antiga continua na tela até a nova chegar.
            if (!silencioso) _biblioteca.update { it.copy(carregando = true, erro = null, lidas = 0, total = 0) }
            try {
                val inicio = System.currentTimeMillis()
                val musicas = app.musicas.varrer(p.pastaBiblioteca, p.ignorarCurtas) { lidas, total ->
                    if (lidas % 20 == 0 || lidas == total) _biblioteca.update { it.copy(lidas = lidas, total = total) }
                }
                val agrupada = withContext(Dispatchers.Default) { agrupar(musicas) }
                _biblioteca.value = agrupada
                android.util.Log.i("SongV", "Biblioteca: ${musicas.size} faixas em ${System.currentTimeMillis() - inicio} ms")
                // Nunca mexe no que está tocando: só resolve as faixas da fila com a biblioteca nova.
                sincronizarFila()
                aoTrocarFaixa()
                restaurarSessaoSeVazio()
                app.escopo.launch(Dispatchers.IO) { capas.preparar(musicas) }
            } catch (e: Exception) {
                _biblioteca.update {
                    it.copy(carregando = false, pronta = true, erro = "Não foi possível ler a biblioteca (${e.message ?: "erro desconhecido"}).")
                }
            }
        }
    }

    suspend fun pastasDisponiveis() = app.musicas.pastasDisponiveis()

    private fun agrupar(musicas: List<Musica>): EstadoBiblioteca {
        val ordenadas = musicas.sortedWith(compareBy(comparadorAlfabetico) { it.titulo })

        // Mesmo nome de álbum + mesmo artista do álbum (ou mesma pasta, quando a tag não existe)
        // = um álbum. Assim coletâneas com vários artistas não viram dez "álbuns" diferentes.
        val albuns = musicas.filter { it.album.isNotBlank() }
            .groupBy { m -> normalizarBusca(m.album) + "|" + (m.artistaAlbum.takeIf { it.isNotBlank() }?.let(::normalizarBusca) ?: m.pasta) }
            .map { (chave, faixas) ->
                val ordem = faixas.sortedWith(
                    compareBy<Musica>({ it.disco ?: 1 }, { it.faixa ?: Int.MAX_VALUE }).thenBy(comparadorAlfabetico) { it.titulo },
                )
                val principais = faixas.map { it.artistas.first() }.distinct()
                val artista = faixas.first().artistaAlbum.ifBlank {
                    if (principais.size > 2) "Vários artistas" else principais.joinToString(", ")
                }
                Album(chave, faixas.first().album, artista, faixas.mapNotNull { it.ano }.maxOrNull(), ordem)
            }
            .sortedWith(compareBy(comparadorAlfabetico) { it.titulo })

        val faixasPorArtista = LinkedHashMap<String, MutableList<Musica>>()
        val nomeExibido = HashMap<String, String>()
        ordenadas.forEach { m ->
            m.artistas.forEach { a ->
                val k = normalizarBusca(a)
                nomeExibido.putIfAbsent(k, a)
                faixasPorArtista.getOrPut(k) { mutableListOf() } += m
            }
        }
        val albunsPorArtista = HashMap<String, MutableList<Album>>()
        albuns.forEach { al ->
            al.musicas.flatMap { it.artistas }.map(::normalizarBusca).distinct().forEach { k ->
                albunsPorArtista.getOrPut(k) { mutableListOf() } += al
            }
        }
        val artistas = faixasPorArtista.map { (k, faixas) ->
            Artista(nomeExibido.getValue(k), faixas, albunsPorArtista[k].orEmpty().sortedByDescending { it.ano ?: 0 })
        }.sortedWith(compareBy(comparadorAlfabetico) { it.nome })

        return EstadoBiblioteca(
            carregando = false,
            pronta = true,
            musicas = ordenadas,
            albuns = albuns,
            artistas = artistas,
            porId = musicas.associateBy { it.id },
            erro = null,
        )
    }

    fun ordenar(musicas: List<Musica>, ordem: Ordenacao): List<Musica> = when (ordem) {
        Ordenacao.TITULO -> musicas
        Ordenacao.ARTISTA -> musicas.sortedWith(compareBy(comparadorAlfabetico) { it.artista })
        Ordenacao.ALBUM -> musicas.sortedWith(compareBy<Musica, String>(comparadorAlfabetico) { it.album }.thenBy { it.faixa ?: 0 })
        Ordenacao.RECENTES -> musicas.sortedByDescending { it.adicionadaEmSeg }
        Ordenacao.DURACAO -> musicas.sortedByDescending { it.duracaoMs }
    }

    // =========================================================================
    // Controles
    // =========================================================================

    /**
     * Substitui a fila por [musicas] e toca a partir de [indice]. Com [aleatorio] (ou com o modo
     * aleatório já ligado) a faixa escolhida toca primeiro e o resto vem embaralhado.
     */
    fun tocar(musicas: List<Musica>, indice: Int = 0, origem: String? = null, aleatorio: Boolean = false) {
        if (musicas.isEmpty()) return
        val embaralhar = aleatorio || player.shuffleModeEnabled
        val inicio = if (aleatorio) Random.nextInt(musicas.size) else indice.coerceIn(0, musicas.lastIndex)
        app.escopo.launch(Dispatchers.IO) { capas.garantirArquivo(musicas[inicio]) }

        registro.clear()
        player.shuffleModeEnabled = false
        player.setMediaItems(musicas.map(::criarItem), inicio, 0L)
        if (embaralhar) {
            aplicarOrdem(ordemComPrimeiro(inicio, musicas.size))
            player.shuffleModeEnabled = true
        }
        player.prepare()
        player.play()
        _reproducao.update { it.copy(origem = origem) }
    }

    fun alternarPlayPause() {
        when {
            player.mediaItemCount == 0 -> _biblioteca.value.musicas.takeIf { it.isNotEmpty() }?.let { tocar(it, origem = "Biblioteca", aleatorio = true) }
            player.isPlaying -> player.pause()
            else -> {
                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition(player.currentMediaItemIndex)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
    }

    fun proxima() = player.seekToNext()

    /** Como nos players comuns: depois de 3 s volta ao começo da faixa; antes disso vai para a anterior. */
    fun anterior() = player.seekToPrevious()

    fun buscar(ms: Long) = player.seekTo(ms)

    fun alternarAleatorio() {
        if (player.shuffleModeEnabled) {
            player.shuffleModeEnabled = false
        } else if (player.mediaItemCount > 0) {
            // A ordem aleatória padrão pode deixar faixas "antes" da atual (e elas nunca tocariam).
            // Montamos a nossa: atual primeiro, todo o resto embaralhado depois.
            aplicarOrdem(ordemComPrimeiro(player.currentMediaItemIndex, player.mediaItemCount))
            player.shuffleModeEnabled = true
        } else {
            player.shuffleModeEnabled = true
        }
    }

    fun alternarRepeticao() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---- Fila ----

    fun tocarItem(item: ItemFila) {
        val janela = janelaDaChave(item.chave) ?: return
        player.seekTo(janela, 0L)
        player.play()
    }

    /** Coloca [musicas] logo depois da faixa atual — inclusive no modo aleatório. */
    fun tocarAseguir(musicas: List<Musica>) {
        if (musicas.isEmpty()) return
        if (player.mediaItemCount == 0) return tocar(musicas, origem = "Fila")
        val itens = musicas.map(::criarItem)
        if (!player.shuffleModeEnabled) {
            player.addMediaItems(player.currentMediaItemIndex + 1, itens)
        } else {
            val ordem = ordemAtual().toMutableList()
            val n = player.mediaItemCount
            player.addMediaItems(n, itens)
            ordem.addAll(ordem.indexOf(player.currentMediaItemIndex) + 1, (n until n + itens.size).toList())
            aplicarOrdem(ordem)
        }
        avisar(if (musicas.size == 1) "Vai tocar a seguir" else "${musicas.size} faixas vão tocar a seguir")
    }

    fun adicionarAFila(musicas: List<Musica>) {
        if (musicas.isEmpty()) return
        if (player.mediaItemCount == 0) return tocar(musicas, origem = "Fila")
        val ordem = ordemAtual()
        val n = player.mediaItemCount
        player.addMediaItems(musicas.map(::criarItem))
        if (player.shuffleModeEnabled) aplicarOrdem(ordem + (n until n + musicas.size))
        avisar(if (musicas.size == 1) "Adicionada ao fim da fila" else "${musicas.size} faixas adicionadas à fila")
    }

    fun removerDaFila(item: ItemFila) {
        janelaDaChave(item.chave)?.let(player::removeMediaItem)
    }

    /** Move uma faixa da fila; [de] e [para] são posições na ordem de reprodução. */
    fun moverNaFila(de: Int, para: Int) {
        val ordem = ordemAtual()
        if (de !in ordem.indices || para !in ordem.indices || de == para) return
        if (player.shuffleModeEnabled) {
            aplicarOrdem(ordem.toMutableList().apply { add(para, removeAt(de)) })
        } else {
            player.moveMediaItem(ordem[de], ordem[para])
        }
    }

    fun limparAseguir() {
        val ordem = ordemAtual()
        val posicao = ordem.indexOf(player.currentMediaItemIndex)
        if (posicao < 0) return
        if (!player.shuffleModeEnabled) {
            player.removeMediaItems(player.currentMediaItemIndex + 1, player.mediaItemCount)
        } else {
            ordem.drop(posicao + 1).sortedDescending().forEach(player::removeMediaItem)
        }
    }

    // ---- Timer ----

    fun timerEmMinutos(minutos: Int) {
        TimerSono.emMinutos(player, minutos)
        avisar("O SongV pausa em $minutos minutos")
    }

    fun timerFimDaFaixa() {
        TimerSono.aoFimDaFaixa(player)
        avisar("O SongV pausa quando esta faixa acabar")
    }

    fun cancelarTimer() = TimerSono.cancelar(player)

    // =========================================================================
    // Letra, tradução e sincronia
    // =========================================================================

    private fun aoTrocarFaixa() {
        val m = _reproducao.value.atual
        if (m?.id == _letra.value.musicaId && _letra.value.letra != null) return
        jobLetra?.cancel()
        jobTraducao?.cancel()
        _letra.value = EstadoLetra(musicaId = m?.id)
        if (m == null) return
        jobLetra = viewModelScope.launch {
            val letra = app.letras.carregar(m)
            _letra.update { if (it.musicaId == m.id) it.copy(letra = letra) else it }
            if (preferencias.value.traduzirAutomaticamente && letra.linhas.isNotEmpty()) {
                traduzir(m, letra, preferencias.value.idiomaTraducao)
            }
        }
    }

    /** Liga a tradução (e a deixa ligada nas próximas faixas) no idioma escolhido. */
    fun ativarTraducao(idioma: String = preferencias.value.idiomaTraducao) {
        viewModelScope.launch {
            prefs.definirIdiomaTraducao(idioma)
            prefs.definirTraduzirAutomaticamente(true)
        }
        val m = _reproducao.value.atual ?: return
        val letra = _letra.value.letra?.takeIf { it.linhas.isNotEmpty() } ?: return
        traduzir(m, letra, idioma)
    }

    fun desativarTraducao() {
        jobTraducao?.cancel()
        viewModelScope.launch { prefs.definirTraduzirAutomaticamente(false) }
        _letra.update { it.copy(traducao = null, traduzindo = false, avisoTraducao = null) }
    }

    private fun traduzir(m: Musica, letra: Letra, idioma: String) {
        jobTraducao?.cancel()
        jobTraducao = viewModelScope.launch {
            _letra.update { it.copy(traduzindo = true, avisoTraducao = null) }
            val resultado = app.tradutor.traduzir(letra.linhas.map { it.texto }, idioma)
            if (_letra.value.musicaId != m.id) return@launch
            when (resultado) {
                is TradutorLetra.Resultado.Sucesso ->
                    _letra.update { it.copy(traducao = resultado.traducao, traduzindo = false) }
                is TradutorLetra.Resultado.MesmoIdioma ->
                    _letra.update { it.copy(traducao = null, traduzindo = false, avisoTraducao = "A letra já está em ${nomeIdioma(idioma)}") }
                TradutorLetra.Resultado.SemConexao -> {
                    _letra.update { it.copy(traduzindo = false) }
                    avisar("Sem internet para traduzir. A letra original continua aqui.")
                }
                TradutorLetra.Resultado.Falha -> {
                    _letra.update { it.copy(traduzindo = false) }
                    avisar("A tradução não respondeu agora.", "Tentar de novo") { traduzir(m, letra, idioma) }
                }
            }
        }
    }

    fun buscarLetraOnline() {
        val m = _reproducao.value.atual ?: return
        if (_letra.value.buscando) return
        viewModelScope.launch {
            _letra.update { it.copy(buscando = true) }
            val r = app.letras.buscarOnline(m)
            _letra.update { it.copy(buscando = false) }
            tratarResultadoBusca(m, r)
        }
    }

    private fun tratarResultadoBusca(m: Musica, r: LetraRepository.ResultadoBusca) {
        when (r) {
            is LetraRepository.ResultadoBusca.Encontrada -> {
                aplicarLetraNova(m, r.letra)
                val diferenca = r.diferencaSeg ?: 0
                when {
                    r.letra.tipo != TipoLetra.SINCRONIZADA -> avisar("Letra encontrada, mas sem sincronia", "Outras versões") { abrirPesquisaLetra() }
                    diferenca > 3 -> avisar(
                        "Letra sincronizada de uma versão com ${diferenca}s de diferença. Se sair do tempo, ajuste em Sincronia.",
                        "Outras versões",
                    ) { abrirPesquisaLetra() }
                    else -> avisar("Letra sincronizada encontrada")
                }
            }
            LetraRepository.ResultadoBusca.Instrumental -> avisar("Esta faixa está marcada como instrumental na LRCLIB.")
            LetraRepository.ResultadoBusca.NaoEncontrada ->
                avisar("Não achei uma letra confiável para “${m.titulo}”.", "Pesquisar") { abrirPesquisaLetra() }
            LetraRepository.ResultadoBusca.SemConexao -> avisar("Sem internet. Conecte-se para buscar a letra.")
            LetraRepository.ResultadoBusca.Falha -> avisar("A LRCLIB não respondeu.", "Tentar de novo") { buscarLetraOnline() }
        }
    }

    // ---- Pesquisa manual de letra (editar título/artista e escolher a versão) ----

    fun abrirPesquisaLetra() = _letra.update { it.copy(pesquisaAberta = true) }
    fun fecharPesquisaLetra() = _letra.update { it.copy(pesquisaAberta = false) }

    /** Título e artista limpos da faixa atual, para preencher a pesquisa. */
    fun sugestaoPesquisaLetra(): BuscaLetra.Consulta? =
        _reproducao.value.atual?.let { BuscaLetra.consulta(it.titulo, it.artista) }

    suspend fun pesquisarLetras(titulo: String, artista: String): LetraRepository.Pesquisa {
        val m = _reproducao.value.atual ?: return LetraRepository.Pesquisa.Falha
        return app.letras.pesquisar(titulo, artista, m)
    }

    fun aplicarLetraEscolhida(c: CandidatoLetra) {
        val m = _reproducao.value.atual ?: return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { app.letras.aplicar(m, c) }
            fecharPesquisaLetra()
            if (r is LetraRepository.ResultadoBusca.Encontrada) {
                aplicarLetraNova(m, r.letra)
                avisar(if (r.letra.tipo == TipoLetra.SINCRONIZADA) "Letra aplicada" else "Letra aplicada (sem sincronia)")
            } else {
                tratarResultadoBusca(m, r)
            }
        }
    }

    fun importarLetra(uri: Uri) {
        val m = _reproducao.value.atual ?: return
        viewModelScope.launch {
            val letra = app.letras.importar(m, uri)
            if (letra == null) {
                avisar("Esse arquivo não parece conter uma letra.")
            } else {
                aplicarLetraNova(m, letra)
                avisar("Letra importada")
            }
        }
    }

    fun removerLetraSalva() {
        val m = _reproducao.value.atual ?: return
        viewModelScope.launch {
            app.letras.removerSalva(m)
            val letra = app.letras.carregar(m)
            aplicarLetraNova(m, letra)
            avisar("Voltando para a letra do arquivo")
        }
    }

    private fun aplicarLetraNova(m: Musica, letra: Letra) {
        jobTraducao?.cancel()
        _letra.update { if (it.musicaId == m.id) it.copy(letra = letra, traducao = null, avisoTraducao = null, traduzindo = false) else it }
        _biblioteca.update { b ->
            val nova = m.copy(tipoLetra = letra.tipo)
            b.copy(musicas = b.musicas.map { if (it.id == m.id) nova else it }, porId = b.porId + (m.id to nova))
        }
        if (preferencias.value.traduzirAutomaticamente && letra.linhas.isNotEmpty()) traduzir(m, letra, preferencias.value.idiomaTraducao)
    }

    fun temLetraSalva(): Boolean = _reproducao.value.atual?.let(app.letras::temLetraSalva) ?: false

    /** Adianta (+) ou atrasa (−) a letra desta faixa, em milissegundos. Fica salvo por faixa. */
    fun ajustarAtraso(deltaMs: Long) {
        val m = _reproducao.value.atual ?: return
        val atual = preferencias.value.atrasosLetra[m.id] ?: 0L
        viewModelScope.launch { prefs.definirAtrasoLetra(m.id, if (deltaMs == 0L) 0L else atual + deltaMs) }
    }

    // =========================================================================
    // Favoritos, playlists, preferências
    // =========================================================================

    fun alternarFavorito(id: String) {
        viewModelScope.launch { prefs.alternarFavorito(id) }
    }

    fun criarPlaylist(nome: String, ids: List<String> = emptyList()) {
        val limpo = nome.trim().ifEmpty { return }
        viewModelScope.launch {
            prefs.salvarPlaylists { it + Playlist(System.currentTimeMillis().toString(), limpo, ids.distinct()) }
        }
        avisar(if (ids.isEmpty()) "Playlist “$limpo” criada" else "Adicionada à nova playlist “$limpo”")
    }

    fun renomearPlaylist(id: String, nome: String) {
        val limpo = nome.trim().ifEmpty { return }
        viewModelScope.launch { prefs.salvarPlaylists { l -> l.map { if (it.id == id) it.copy(nome = limpo) else it } } }
    }

    fun excluirPlaylist(id: String) {
        val removida = preferencias.value.playlists.firstOrNull { it.id == id } ?: return
        val posicao = preferencias.value.playlists.indexOf(removida)
        viewModelScope.launch { prefs.salvarPlaylists { l -> l.filterNot { it.id == id } } }
        avisar("Playlist “${removida.nome}” excluída", "Desfazer") {
            viewModelScope.launch {
                prefs.salvarPlaylists { l -> l.toMutableList().apply { add(posicao.coerceAtMost(size), removida) } }
            }
        }
    }

    fun adicionarNaPlaylist(idPlaylist: String, ids: List<String>) {
        val playlist = preferencias.value.playlists.firstOrNull { it.id == idPlaylist } ?: return
        val novos = ids.filterNot { it in playlist.musicasIds }.distinct()
        if (novos.isEmpty()) return avisar("Já está em “${playlist.nome}”")
        viewModelScope.launch {
            prefs.salvarPlaylists { l -> l.map { if (it.id == idPlaylist) it.copy(musicasIds = it.musicasIds + novos) else it } }
        }
        avisar("Adicionada a “${playlist.nome}”")
    }

    fun removerDaPlaylist(idPlaylist: String, idMusica: String) {
        viewModelScope.launch {
            prefs.salvarPlaylists { l -> l.map { if (it.id == idPlaylist) it.copy(musicasIds = it.musicasIds - idMusica) else it } }
        }
    }

    fun moverNaPlaylist(idPlaylist: String, de: Int, para: Int) {
        viewModelScope.launch {
            prefs.salvarPlaylists { l ->
                l.map { p ->
                    if (p.id != idPlaylist || de !in p.musicasIds.indices || para !in p.musicasIds.indices) p
                    else p.copy(musicasIds = p.musicasIds.toMutableList().apply { add(para, removeAt(de)) })
                }
            }
        }
    }

    fun definirModoTema(m: ModoTema) = viewModelScope.launch { prefs.definirModoTema(m) }
    fun definirDestaque(d: Destaque) = viewModelScope.launch { prefs.definirDestaque(d) }
    fun definirCorPersonalizada(argb: Int) = viewModelScope.launch { prefs.definirCorPersonalizada(argb) }
    fun definirCoresDaCapa(v: Boolean) = viewModelScope.launch { prefs.definirCoresDaCapa(v) }
    fun definirOrdenacao(o: Ordenacao) = viewModelScope.launch { prefs.definirOrdenacao(o) }
    fun definirExibicaoTraducao(e: ExibicaoTraducao) = viewModelScope.launch { prefs.definirExibicaoTraducao(e) }
    fun definirRomanizacao(v: Boolean) = viewModelScope.launch { prefs.definirRomanizacao(v) }
    fun definirEscalaLetra(v: Float) = viewModelScope.launch { prefs.definirEscalaLetra(v) }
    fun definirBuscaOnline(v: Boolean) = viewModelScope.launch { prefs.definirBuscaOnline(v) }
    fun registrarBusca(termo: String) = viewModelScope.launch { prefs.registrarBusca(termo) }
    fun limparBuscas() = viewModelScope.launch { prefs.limparBuscas() }

    fun definirPasta(pasta: String) = viewModelScope.launch {
        prefs.definirPasta(pasta)
        preferencias.first { it.pastaBiblioteca == pasta }
        carregarBiblioteca(forcar = true)
    }

    fun definirIgnorarCurtas(v: Boolean) = viewModelScope.launch {
        prefs.definirIgnorarCurtas(v)
        preferencias.first { it.ignorarCurtas == v }
        carregarBiblioteca(forcar = true)
    }

    fun avisar(texto: String, acao: String? = null, aoAgir: (() -> Unit)? = null) {
        _mensagens.tryEmit(Mensagem(texto, acao, aoAgir))
    }

    // =========================================================================
    // Sincronização com o player
    // =========================================================================

    private fun criarItem(m: Musica): MediaItem {
        val chave = "${m.id}#${contador.incrementAndGet()}"
        registro[chave] = m
        return MediaItem.Builder()
            .setMediaId(chave)
            .setUri(m.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(m.titulo)
                    .setArtist(m.artista)
                    .setAlbumTitle(m.album.ifBlank { null })
                    .setAlbumArtist(m.artistaAlbum.ifBlank { null })
                    .setArtworkUri(capas.uriArtwork(m))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            )
            .build()
    }

    private fun musicaDaChave(chave: String): Musica? =
        registro[chave] ?: _biblioteca.value.porId[chave.substringBefore('#')]?.also { registro[chave] = it }

    private fun janelaDaChave(chave: String): Int? =
        (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == chave }

    /** Janelas na ordem em que vão tocar (considera o modo aleatório; ignora a repetição). */
    private fun ordemAtual(): List<Int> = ordemDe(player.currentTimeline, player.shuffleModeEnabled)

    private fun ordemDe(tl: Timeline, aleatorio: Boolean): List<Int> {
        if (tl.isEmpty) return emptyList()
        val ordem = ArrayList<Int>(tl.windowCount)
        var i = tl.getFirstWindowIndex(aleatorio)
        while (i != C.INDEX_UNSET && ordem.size < tl.windowCount) {
            ordem += i
            i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, aleatorio)
        }
        return ordem
    }

    private fun ordemComPrimeiro(primeiro: Int, total: Int): List<Int> =
        listOf(primeiro) + (0 until total).filter { it != primeiro }.shuffled()

    private fun aplicarOrdem(ordem: List<Int>) {
        if (ordem.size != player.mediaItemCount) return
        player.setShuffleOrder(DefaultShuffleOrder(ordem.toIntArray(), Random.nextLong()))
    }

    private fun sincronizarFila() {
        val ordem = ordemAtual()
        val atual = player.currentMediaItemIndex
        val itens = ArrayList<ItemFila>(ordem.size)
        for (j in ordem) {
            val chave = player.getMediaItemAt(j).mediaId
            val m = musicaDaChave(chave) ?: continue
            itens += ItemFila(chave, m, j)
        }
        _reproducao.update { estadoComFlags(it.copy(fila = itens, posicao = itens.indexOfFirst { i -> i.janela == atual })) }
    }

    private fun sincronizarEstado() {
        _reproducao.update { estadoComFlags(it) }
    }

    private fun estadoComFlags(e: EstadoReproducao) = e.copy(
        tocando = player.isPlaying,
        carregando = player.playbackState == Player.STATE_BUFFERING,
        aleatorio = player.shuffleModeEnabled,
        repeticao = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> ModoRepeticao.TUDO
            Player.REPEAT_MODE_ONE -> ModoRepeticao.UMA
            else -> ModoRepeticao.DESLIGADO
        },
    )

    private fun duracaoAtual(): Long =
        player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: _reproducao.value.atual?.duracaoMs ?: 0L

    /**
     * Conta a reprodução (histórico e "mais tocadas") só depois que a faixa tocou de verdade:
     * metade da duração, entre 10 e 30 segundos — a mesma ideia do scrobble do Last.fm. Assim,
     * pular faixas não infla o ranking.
     */
    private fun registrarReproducao() {
        val chave = player.currentMediaItem?.mediaId ?: return
        if (chave == ultimoRegistrado || (jobRegistro?.isActive == true && chaveEmContagem == chave)) return
        jobRegistro?.cancel()
        chaveEmContagem = chave
        jobRegistro = viewModelScope.launch {
            val alvo = (duracaoAtual() / 2).coerceIn(10_000, 30_000)
            var ouvido = 0L
            while (ouvido < alvo) {
                delay(1_000)
                if (player.currentMediaItem?.mediaId != chave) return@launch
                if (player.isPlaying) ouvido += 1_000
            }
            ultimoRegistrado = chave
            val id = chave.substringBefore('#')
            app.escopo.launch { prefs.registrarReproducao(id) }
        }
    }

    // ---- Sessão (retomar de onde parou) ----

    fun salvarSessao() {
        val n = player.mediaItemCount
        if (n == 0) return
        val sessao = Sessao(
            ids = (0 until n).map { player.getMediaItemAt(it).mediaId.substringBefore('#') },
            indice = player.currentMediaItemIndex,
            posicaoMs = player.currentPosition.coerceAtLeast(0),
            ordemAleatoria = if (player.shuffleModeEnabled) ordemAtual() else null,
            repeticao = player.repeatMode,
            origem = _reproducao.value.origem,
        )
        app.escopo.launch { prefs.salvarSessao(sessao) }
    }

    private suspend fun restaurarSessaoSeVazio() {
        if (sessaoVerificada) return
        sessaoVerificada = true
        if (player.mediaItemCount > 0) return
        val s = prefs.lerSessao() ?: return
        val porId = _biblioteca.value.porId
        val musicas = s.ids.mapNotNull { porId[it] }
        if (musicas.isEmpty()) return
        val completa = musicas.size == s.ids.size
        val indice = if (completa) s.indice.coerceIn(0, musicas.lastIndex)
        else musicas.indexOfFirst { it.id == s.ids.getOrNull(s.indice) }.coerceAtLeast(0)

        registro.clear()
        player.setMediaItems(musicas.map(::criarItem), indice, if (completa || musicas[indice].id == s.ids.getOrNull(s.indice)) s.posicaoMs else 0L)
        val ordem = s.ordemAleatoria
        if (completa && ordem != null && ordem.size == musicas.size) {
            aplicarOrdem(ordem)
            player.shuffleModeEnabled = true
        }
        player.repeatMode = s.repeticao
        player.prepare() // pronta para tocar, mas pausada
        _reproducao.update { it.copy(origem = s.origem) }
    }

    override fun onCleared() {
        salvarSessao()
        player.removeListener(ouvinte)
        runCatching { getApplication<Application>().contentResolver.unregisterContentObserver(observadorMidia) }
        super.onCleared()
    }
}
