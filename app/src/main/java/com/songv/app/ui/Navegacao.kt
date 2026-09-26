package com.songv.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.songv.app.model.Musica

/** Três destinos: Início (descobrir), Buscar (encontrar) e Biblioteca (organizar). */
enum class Aba(val rotulo: String, val icone: ImageVector, val iconeAtivo: ImageVector) {
    INICIO("Início", Icons.Outlined.Home, Icons.Rounded.Home),
    BUSCAR("Buscar", Icons.Outlined.Search, Icons.Rounded.Search),
    BIBLIOTECA("Biblioteca", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
}

/** Seções da aba Biblioteca. */
enum class FiltroBiblioteca(val rotulo: String) { MUSICAS("Faixas"), ALBUNS("Álbuns"), ARTISTAS("Artistas"), PLAYLISTS("Playlists") }

/** Telas de detalhe empilhadas sobre a aba atual (a barra inferior e o mini player continuam visíveis). */
sealed interface Rota {
    data class Album(val chave: String) : Rota
    data class Artista(val nome: String) : Rota
    data class Playlist(val id: String) : Rota
    data object Favoritas : Rota
    data object Configuracoes : Rota
}

/**
 * Estado de navegação do app: aba, pilha de detalhes, player/fila abertos e as folhas de ação.
 * Sobrevive a rotação (ver [Saver]) — antes, girar a tela jogava o usuário de volta ao início.
 */
@Stable
class Navegador {
    var aba by mutableStateOf(Aba.INICIO)
    var pilha by mutableStateOf(listOf<Rota>())
        private set
    var playerAberto by mutableStateOf(false)
    var filaAberta by mutableStateOf(false)
    var filtro by mutableStateOf(FiltroBiblioteca.MUSICAS)

    /** Folha de ações de uma faixa (tocar a seguir, playlist, álbum…). */
    var menuMusica by mutableStateOf<Musica?>(null)

    /** Faixas aguardando o usuário escolher em qual playlist entram. */
    var paraPlaylist by mutableStateOf<List<Musica>?>(null)

    /** true quando a última navegação foi para frente — decide o sentido da animação. */
    var avancou = true
        private set

    val rotaAtual: Rota? get() = pilha.lastOrNull()

    fun abrir(rota: Rota) {
        avancou = true
        pilha = pilha + rota
        filaAberta = false
        playerAberto = false
    }

    fun trocarAba(nova: Aba) {
        avancou = nova.ordinal >= aba.ordinal
        aba = nova
        pilha = emptyList()
    }

    /** Volta um nível. Devolve false quando não há mais para onde voltar (o sistema fecha o app). */
    fun voltar(): Boolean {
        avancou = false
        when {
            menuMusica != null -> menuMusica = null
            paraPlaylist != null -> paraPlaylist = null
            filaAberta -> filaAberta = false
            playerAberto -> playerAberto = false
            pilha.isNotEmpty() -> pilha = pilha.dropLast(1)
            aba != Aba.INICIO -> aba = Aba.INICIO
            else -> return false
        }
        return true
    }

    companion object {
        val Saver = Saver<Navegador, List<String>>(
            save = { n ->
                listOf(n.aba.name, n.playerAberto.toString(), n.filaAberta.toString()) + n.pilha.map { r ->
                    when (r) {
                        is Rota.Album -> "album:${r.chave}"
                        is Rota.Artista -> "artista:${r.nome}"
                        is Rota.Playlist -> "playlist:${r.id}"
                        Rota.Favoritas -> "favoritas:"
                        Rota.Configuracoes -> "configuracoes:"
                    }
                }
            },
            restore = { l ->
                Navegador().apply {
                    aba = Aba.valueOf(l[0])
                    playerAberto = l[1].toBoolean()
                    filaAberta = l[2].toBoolean()
                    pilha = l.drop(3).mapNotNull { s ->
                        val valor = s.substringAfter(':')
                        when (s.substringBefore(':')) {
                            "album" -> Rota.Album(valor)
                            "artista" -> Rota.Artista(valor)
                            "playlist" -> Rota.Playlist(valor)
                            "favoritas" -> Rota.Favoritas
                            "configuracoes" -> Rota.Configuracoes
                            else -> null
                        }
                    }
                }
            },
        )
    }
}
