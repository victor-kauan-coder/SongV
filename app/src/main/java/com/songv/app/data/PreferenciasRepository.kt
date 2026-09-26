package com.songv.app.data

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.songv.app.model.Playlist
import com.songv.app.ui.theme.ModoLuminosidade
import com.songv.app.ui.theme.TemaApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.Preferences // ADD THIS

// Extension property to create the DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "configuracoes")

class PreferenciasRepository(private val context: Context) {

    private object Keys {
        val TEMA = stringPreferencesKey("tema_app")
        val COR_CUSTOM = intPreferencesKey("cor_personalizada")
        val FAVORITOS = stringSetPreferencesKey("musicas_favoritas")
        val LUMINOSIDADE = stringPreferencesKey("modo_luminosidade")
        // Cada playlist é serializada como "id::nome::id1,id2,id3" (sem separadores conflitantes,
        // já que ids de música e nomes de playlist não contêm "::" nem quebra de linha).
        val PLAYLISTS = stringSetPreferencesKey("playlists_serializadas")
    }

    private companion object {
        const val SEP_CAMPO = "::"
        const val SEP_MUSICA = ","
    }

    // Flow for the selected theme
    val temaFlow: Flow<TemaApp> = context.dataStore.data.map { pref ->
        val nomeTema = pref[Keys.TEMA] ?: TemaApp.ROXO_DARK.name
        try {
            TemaApp.valueOf(nomeTema)
        } catch (e: Exception) {
            TemaApp.ROXO_DARK
        }
    }

    // Flow for the custom ARGB color
    val corCustomFlow: Flow<Int?> = context.dataStore.data.map { pref ->
        pref[Keys.COR_CUSTOM]
    }

    // Flow for the set of favorite music IDs
    val favoritosFlow: Flow<Set<String>> = context.dataStore.data.map { pref ->
        pref[Keys.FAVORITOS] ?: emptySet()
    }

    // Flow para o modo de luminosidade (claro/escuro), independente da cor escolhida
    val luminosidadeFlow: Flow<ModoLuminosidade> = context.dataStore.data.map { pref ->
        when (pref[Keys.LUMINOSIDADE]) {
            ModoLuminosidade.CLARO.name -> ModoLuminosidade.CLARO
            else -> ModoLuminosidade.ESCURO
        }
    }

    // Flow para a lista de playlists do usuário
    val playlistsFlow: Flow<List<Playlist>> = context.dataStore.data.map { pref ->
        val brutas = pref[Keys.PLAYLISTS] ?: emptySet()
        brutas.mapNotNull { desserializarPlaylist(it) }
            .sortedBy { it.id } // ordem de criação, já que o id é baseado em timestamp
    }

    suspend fun salvarTema(tema: TemaApp) {
        context.dataStore.edit { pref ->
            pref[Keys.TEMA] = tema.name
        }
    }

    suspend fun salvarCorCustom(argb: Int) {
        context.dataStore.edit { pref ->
            pref[Keys.COR_CUSTOM] = argb
        }
    }

    suspend fun alternarFavorito(idMusica: String) {
        context.dataStore.edit { pref ->
            val atuais = pref[Keys.FAVORITOS] ?: emptySet()
            val novos = if (atuais.contains(idMusica)) {
                atuais - idMusica
            } else {
                atuais + idMusica
            }
            pref[Keys.FAVORITOS] = novos
        }
    }

    suspend fun alternarLuminosidade() {
        context.dataStore.edit { pref ->
            val atual = pref[Keys.LUMINOSIDADE]
            pref[Keys.LUMINOSIDADE] = if (atual == ModoLuminosidade.CLARO.name) {
                ModoLuminosidade.ESCURO.name
            } else {
                ModoLuminosidade.CLARO.name
            }
        }
    }

    // ---- Playlists ----

    suspend fun criarPlaylist(nome: String): Playlist {
        val nova = Playlist(id = System.currentTimeMillis().toString(), nome = nome, musicasIds = emptyList())
        context.dataStore.edit { pref ->
            val atuais = pref[Keys.PLAYLISTS] ?: emptySet()
            pref[Keys.PLAYLISTS] = atuais + serializarPlaylist(nova)
        }
        return nova
    }

    suspend fun renomearPlaylist(idPlaylist: String, novoNome: String) {
        atualizarPlaylist(idPlaylist) { it.copy(nome = novoNome) }
    }

    suspend fun excluirPlaylist(idPlaylist: String) {
        context.dataStore.edit { pref ->
            val atuais = pref[Keys.PLAYLISTS] ?: emptySet()
            pref[Keys.PLAYLISTS] = atuais.filterNot { desserializarPlaylist(it)?.id == idPlaylist }.toSet()
        }
    }

    suspend fun adicionarMusicaNaPlaylist(idPlaylist: String, idMusica: String) {
        atualizarPlaylist(idPlaylist) { playlist ->
            if (idMusica in playlist.musicasIds) playlist
            else playlist.copy(musicasIds = playlist.musicasIds + idMusica)
        }
    }

    suspend fun removerMusicaDaPlaylist(idPlaylist: String, idMusica: String) {
        atualizarPlaylist(idPlaylist) { playlist ->
            playlist.copy(musicasIds = playlist.musicasIds - idMusica)
        }
    }

    private suspend fun atualizarPlaylist(idPlaylist: String, transformar: (Playlist) -> Playlist) {
        context.dataStore.edit { pref ->
            val atuais = pref[Keys.PLAYLISTS] ?: emptySet()
            val novas = atuais.map { bruta ->
                val playlist = desserializarPlaylist(bruta) ?: return@map bruta
                if (playlist.id == idPlaylist) serializarPlaylist(transformar(playlist)) else bruta
            }.toSet()
            pref[Keys.PLAYLISTS] = novas
        }
    }

    private fun serializarPlaylist(playlist: Playlist): String {
        return "${playlist.id}$SEP_CAMPO${playlist.nome}$SEP_CAMPO${playlist.musicasIds.joinToString(SEP_MUSICA)}"
    }

    private fun desserializarPlaylist(bruta: String): Playlist? {
        val partes = bruta.split(SEP_CAMPO, limit = 3)
        if (partes.size < 2) return null
        val id = partes[0]
        val nome = partes[1]
        val musicasIds = partes.getOrNull(2)?.takeIf { it.isNotBlank() }?.split(SEP_MUSICA) ?: emptyList()
        return Playlist(id = id, nome = nome, musicasIds = musicasIds)
    }
}