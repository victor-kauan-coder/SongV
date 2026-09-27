package com.songv.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.songv.app.letra.IDIOMAS_TRADUCAO
import com.songv.app.letra.TradutorLetra
import com.songv.app.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "configuracoes")

enum class ModoTema(val rotulo: String) { ESCURO("Escuro"), CLARO("Claro"), SISTEMA("Sistema") }

enum class Destaque(val rotulo: String, val argb: Long) {
    LARANJA("Laranja", 0xFFFF6B1A),
    AMBAR("Âmbar", 0xFFFFB020),
    VERMELHO("Vermelho", 0xFFFF4B4B),
    ROSA("Rosa", 0xFFFF5C9E),
    VIOLETA("Violeta", 0xFFA689FF),
    AZUL("Azul", 0xFF4DA0FF),
    VERDE("Verde", 0xFF36D07A),
    PERSONALIZADO("Sua cor", 0xFFFF6B1A),
}

/** Acabamento das superfícies (mini player, barra de abas, cartões e botões). */
enum class EstiloVisual(val rotulo: String, val descricao: String) {
    ATUAL("Atual", "Liso"),
    OPACO("Opaco", "Chapado"),
    FOSCO("Fosco", "Jateado"),
    METALICO("Metálico", "Escovado"),
    VIDRO("Vidro", "Translúcido"),
}

/** Cor de fundo do app, com uma versão para o tema escuro e outra para o claro. */
enum class FundoTema(val rotulo: String, val rotuloClaro: String, val escuro: Long, val claro: Long) {
    GRAFITE("Grafite", "Papel", 0xFF141211, 0xFFF4F1EE),
    PRETO("Preto", "Branco", 0xFF000000, 0xFFFFFFFF),
    NOITE("Noite", "Névoa", 0xFF0E121C, 0xFFEEF1F6),
    FLORESTA("Floresta", "Sálvia", 0xFF0E1511, 0xFFEEF2EC),
    VINHO("Vinho", "Rosé", 0xFF1A0D11, 0xFFF6EDEF),
    TERRA("Terra", "Areia", 0xFF19130D, 0xFFF5EFE6),
    PERSONALIZADO("Sua cor", "Sua cor", 0xFF141211, 0xFFF4F1EE),
}

enum class ExibicaoTraducao(val rotulo: String) { AMBAS("Original + tradução"), SO_TRADUCAO("Só a tradução") }

enum class Ordenacao(val rotulo: String) {
    TITULO("Título"), ARTISTA("Artista"), ALBUM("Álbum"), RECENTES("Adicionadas recentemente"), DURACAO("Duração")
}

data class Preferencias(
    val carregadas: Boolean = false,
    val modoTema: ModoTema = ModoTema.ESCURO,
    val destaque: Destaque = Destaque.LARANJA,
    val corPersonalizada: Int? = null,
    val coresDaCapa: Boolean = true,
    val estilo: EstiloVisual = EstiloVisual.ATUAL,
    val fundo: FundoTema = FundoTema.GRAFITE,
    val corFundoPersonalizada: Int? = null,
    val favoritos: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val historico: List<String> = emptyList(),
    val contagens: Map<String, Int> = emptyMap(),
    val pastaBiblioteca: String = MusicaRepository.PASTA_PADRAO,
    val ignorarCurtas: Boolean = true,
    val ordenacao: Ordenacao = Ordenacao.TITULO,
    val idiomaTraducao: String = idiomaPadrao(),
    val traduzirAutomaticamente: Boolean = false,
    val exibicaoTraducao: ExibicaoTraducao = ExibicaoTraducao.AMBAS,
    val mostrarRomanizacao: Boolean = true,
    val escalaLetra: Float = 1f,
    val buscaOnline: Boolean = true,
    val atrasosLetra: Map<String, Long> = emptyMap(),
    val buscasRecentes: List<String> = emptyList(),
    val tocarNoComputador: Boolean = false,
)

/** Estado de reprodução salvo para retomar de onde parou na próxima abertura. */
data class Sessao(
    val ids: List<String>,
    val indice: Int,
    val posicaoMs: Long,
    val ordemAleatoria: List<Int>?,
    val repeticao: Int,
    val origem: String?,
)

private fun idiomaPadrao(): String {
    val sistema = Locale.getDefault().language
    return IDIOMAS_TRADUCAO.firstOrNull { TradutorLetra.mesmoIdioma(it.codigo, sistema) }?.codigo ?: "pt"
}

class PreferenciasRepository(private val context: Context) {

    private object K {
        val MODO_TEMA = stringPreferencesKey("modo_tema")
        val MODO_LUMINOSIDADE_ANTIGO = stringPreferencesKey("modo_luminosidade")
        val DESTAQUE = stringPreferencesKey("destaque")
        val COR_CUSTOM = intPreferencesKey("cor_personalizada")
        val CORES_CAPA = booleanPreferencesKey("cores_da_capa")
        val ESTILO = stringPreferencesKey("estilo_visual")
        val FUNDO = stringPreferencesKey("fundo")
        val COR_FUNDO = intPreferencesKey("cor_fundo_personalizada")
        val FAVORITOS = stringSetPreferencesKey("musicas_favoritas")
        val PLAYLISTS = stringPreferencesKey("playlists_json")
        val PLAYLISTS_ANTIGAS = stringSetPreferencesKey("playlists_serializadas")
        val HISTORICO = stringPreferencesKey("historico")
        val CONTAGENS = stringPreferencesKey("contagens")
        val PASTA = stringPreferencesKey("pasta_biblioteca")
        val IGNORAR_CURTAS = booleanPreferencesKey("ignorar_curtas")
        val ORDENACAO = stringPreferencesKey("ordenacao")
        val IDIOMA = stringPreferencesKey("idioma_traducao")
        val AUTO_TRADUZIR = booleanPreferencesKey("traduzir_auto")
        val EXIBICAO = stringPreferencesKey("exibicao_traducao")
        val ROMANIZACAO = booleanPreferencesKey("romanizacao")
        val ESCALA_LETRA = floatPreferencesKey("escala_letra")
        val BUSCA_ONLINE = booleanPreferencesKey("busca_online")
        val ATRASOS = stringPreferencesKey("atrasos_letra")
        val BUSCAS = stringPreferencesKey("buscas_recentes")
        val SESSAO = stringPreferencesKey("sessao")
        val TOCAR_NO_COMPUTADOR = booleanPreferencesKey("tocar_no_computador")
    }

    val preferencias: Flow<Preferencias> = context.dataStore.data.map { p ->
        Preferencias(
            carregadas = true,
            modoTema = p[K.MODO_TEMA]?.let { enumOu(it, ModoTema.ESCURO) }
                ?: if (p[K.MODO_LUMINOSIDADE_ANTIGO] == "CLARO") ModoTema.CLARO else ModoTema.ESCURO,
            destaque = p[K.DESTAQUE]?.let { enumOu(it, Destaque.LARANJA) } ?: Destaque.LARANJA,
            corPersonalizada = p[K.COR_CUSTOM],
            coresDaCapa = p[K.CORES_CAPA] ?: true,
            estilo = p[K.ESTILO]?.let { enumOu(it, EstiloVisual.ATUAL) } ?: EstiloVisual.ATUAL,
            fundo = p[K.FUNDO]?.let { enumOu(it, FundoTema.GRAFITE) } ?: FundoTema.GRAFITE,
            corFundoPersonalizada = p[K.COR_FUNDO],
            favoritos = p[K.FAVORITOS] ?: emptySet(),
            playlists = p[K.PLAYLISTS]?.let(::lerPlaylists) ?: p[K.PLAYLISTS_ANTIGAS]?.let(::migrarPlaylistsAntigas).orEmpty(),
            historico = p[K.HISTORICO]?.let(::lerLista).orEmpty(),
            contagens = p[K.CONTAGENS]?.let { lerMapa(it) { v -> (v as Number).toInt() } }.orEmpty(),
            pastaBiblioteca = p[K.PASTA] ?: MusicaRepository.PASTA_PADRAO,
            ignorarCurtas = p[K.IGNORAR_CURTAS] ?: true,
            ordenacao = p[K.ORDENACAO]?.let { enumOu(it, Ordenacao.TITULO) } ?: Ordenacao.TITULO,
            idiomaTraducao = p[K.IDIOMA] ?: idiomaPadrao(),
            traduzirAutomaticamente = p[K.AUTO_TRADUZIR] ?: false,
            exibicaoTraducao = p[K.EXIBICAO]?.let { enumOu(it, ExibicaoTraducao.AMBAS) } ?: ExibicaoTraducao.AMBAS,
            mostrarRomanizacao = p[K.ROMANIZACAO] ?: true,
            escalaLetra = p[K.ESCALA_LETRA] ?: 1f,
            buscaOnline = p[K.BUSCA_ONLINE] ?: true,
            atrasosLetra = p[K.ATRASOS]?.let { lerMapa(it) { v -> (v as Number).toLong() } }.orEmpty(),
            buscasRecentes = p[K.BUSCAS]?.let(::lerLista).orEmpty(),
            tocarNoComputador = p[K.TOCAR_NO_COMPUTADOR] ?: false,
        )
    }

    // ---- Aparência ----

    suspend fun definirModoTema(modo: ModoTema) = editar { it[K.MODO_TEMA] = modo.name }
    suspend fun definirDestaque(d: Destaque) = editar { it[K.DESTAQUE] = d.name }
    suspend fun definirCorPersonalizada(argb: Int) = editar {
        it[K.COR_CUSTOM] = argb
        it[K.DESTAQUE] = Destaque.PERSONALIZADO.name
    }
    suspend fun definirCoresDaCapa(v: Boolean) = editar { it[K.CORES_CAPA] = v }
    suspend fun definirEstilo(e: EstiloVisual) = editar { it[K.ESTILO] = e.name }
    suspend fun definirFundo(f: FundoTema) = editar { it[K.FUNDO] = f.name }
    suspend fun definirCorFundoPersonalizada(argb: Int) = editar {
        it[K.COR_FUNDO] = argb
        it[K.FUNDO] = FundoTema.PERSONALIZADO.name
    }

    // ---- Biblioteca ----

    suspend fun definirPasta(pasta: String) = editar { it[K.PASTA] = pasta }
    suspend fun definirIgnorarCurtas(v: Boolean) = editar { it[K.IGNORAR_CURTAS] = v }
    suspend fun definirOrdenacao(o: Ordenacao) = editar { it[K.ORDENACAO] = o.name }

    suspend fun alternarFavorito(id: String) = editar { p ->
        val atuais = p[K.FAVORITOS] ?: emptySet()
        p[K.FAVORITOS] = if (id in atuais) atuais - id else atuais + id
    }

    /** Histórico (mais recente primeiro, sem repetição, até 60) e contagem de reproduções. */
    suspend fun registrarReproducao(id: String) = editar { p ->
        val historico = listOf(id) + (p[K.HISTORICO]?.let(::lerLista).orEmpty() - id)
        p[K.HISTORICO] = JSONArray(historico.take(60)).toString()
        val contagens = p[K.CONTAGENS]?.let { lerMapa(it) { v -> (v as Number).toInt() } }.orEmpty().toMutableMap()
        contagens[id] = (contagens[id] ?: 0) + 1
        p[K.CONTAGENS] = JSONObject(contagens as Map<*, *>).toString()
    }

    suspend fun registrarBusca(termo: String) = editar { p ->
        val limpo = termo.trim()
        if (limpo.length < 2) return@editar
        val atuais = p[K.BUSCAS]?.let(::lerLista).orEmpty().filterNot { it.equals(limpo, ignoreCase = true) }
        p[K.BUSCAS] = JSONArray((listOf(limpo) + atuais).take(8)).toString()
    }

    suspend fun limparBuscas() = editar { it.remove(K.BUSCAS) }

    // ---- Playlists ----

    suspend fun salvarPlaylists(transformar: (List<Playlist>) -> List<Playlist>) = editar { p ->
        val atuais = p[K.PLAYLISTS]?.let(::lerPlaylists) ?: p[K.PLAYLISTS_ANTIGAS]?.let(::migrarPlaylistsAntigas).orEmpty()
        p[K.PLAYLISTS] = escreverPlaylists(transformar(atuais))
        p.remove(K.PLAYLISTS_ANTIGAS)
    }

    // ---- Letra ----

    suspend fun definirIdiomaTraducao(codigo: String) = editar { it[K.IDIOMA] = codigo }
    suspend fun definirTraduzirAutomaticamente(v: Boolean) = editar { it[K.AUTO_TRADUZIR] = v }
    suspend fun definirExibicaoTraducao(e: ExibicaoTraducao) = editar { it[K.EXIBICAO] = e.name }
    suspend fun definirRomanizacao(v: Boolean) = editar { it[K.ROMANIZACAO] = v }
    suspend fun definirEscalaLetra(v: Float) = editar { it[K.ESCALA_LETRA] = v.coerceIn(0.8f, 1.4f) }
    suspend fun definirBuscaOnline(v: Boolean) = editar { it[K.BUSCA_ONLINE] = v }
    suspend fun definirTocarNoComputador(v: Boolean) = editar { it[K.TOCAR_NO_COMPUTADOR] = v }

    suspend fun definirAtrasoLetra(id: String, ms: Long) = editar { p ->
        val mapa = p[K.ATRASOS]?.let { lerMapa(it) { v -> (v as Number).toLong() } }.orEmpty().toMutableMap()
        if (ms == 0L) mapa.remove(id) else mapa[id] = ms
        p[K.ATRASOS] = JSONObject(mapa as Map<*, *>).toString()
    }

    // ---- Sessão ----

    suspend fun lerSessao(): Sessao? = runCatching {
        val bruto = context.dataStore.data.first()[K.SESSAO] ?: return null
        val o = JSONObject(bruto)
        Sessao(
            ids = o.getJSONArray("ids").let { a -> List(a.length()) { a.getString(it) } },
            indice = o.getInt("indice"),
            posicaoMs = o.getLong("posicao"),
            ordemAleatoria = o.optJSONArray("ordem")?.let { a -> List(a.length()) { a.getInt(it) } },
            repeticao = o.optInt("repeticao"),
            origem = if (o.isNull("origem")) null else o.optString("origem"),
        )
    }.getOrNull()

    suspend fun salvarSessao(s: Sessao) = editar { p ->
        p[K.SESSAO] = JSONObject()
            .put("ids", JSONArray(s.ids))
            .put("indice", s.indice)
            .put("posicao", s.posicaoMs)
            .put("ordem", s.ordemAleatoria?.let { JSONArray(it) } ?: JSONObject.NULL)
            .put("repeticao", s.repeticao)
            .put("origem", s.origem ?: JSONObject.NULL)
            .toString()
    }

    // ---- Utilitários ----

    private suspend fun editar(bloco: (MutablePreferences) -> Unit) {
        context.dataStore.edit { bloco(it) }
    }

    private inline fun <reified E : Enum<E>> enumOu(nome: String, padrao: E): E =
        runCatching { enumValueOf<E>(nome) }.getOrDefault(padrao)

    private fun lerLista(json: String): List<String> = runCatching {
        JSONArray(json).let { a -> List(a.length()) { a.getString(it) } }
    }.getOrDefault(emptyList())

    private fun <V> lerMapa(json: String, converter: (Any) -> V): Map<String, V> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { converter(o.get(it)) }
    }.getOrDefault(emptyMap())

    private fun lerPlaylists(json: String): List<Playlist> = runCatching {
        val a = JSONArray(json)
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Playlist(o.getString("id"), o.getString("nome"), o.getJSONArray("musicas").let { m -> List(m.length()) { m.getString(it) } })
        }
    }.getOrDefault(emptyList())

    private fun escreverPlaylists(lista: List<Playlist>): String = JSONArray().apply {
        lista.forEach { put(JSONObject().put("id", it.id).put("nome", it.nome).put("musicas", JSONArray(it.musicasIds))) }
    }.toString()

    /** Formato antigo: "id::nome::id1,id2" num StringSet (quebrava com "::" ou "," no nome). */
    private fun migrarPlaylistsAntigas(antigas: Set<String>): List<Playlist> = antigas.mapNotNull { bruta ->
        val partes = bruta.split("::", limit = 3)
        if (partes.size < 2) return@mapNotNull null
        Playlist(partes[0], partes[1], partes.getOrNull(2)?.takeIf { it.isNotBlank() }?.split(",").orEmpty())
    }.sortedBy { it.id }
}
