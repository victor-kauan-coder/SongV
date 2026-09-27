package com.songv.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.lerp
import com.songv.app.data.EstiloVisual
import com.songv.app.data.FundoTema
import com.songv.app.ui.theme.acabamento
import com.songv.app.ui.theme.ajustarFundo
import com.songv.app.ui.theme.corDoFundo
import com.songv.app.ui.theme.fundoDoApp
import com.songv.app.ui.theme.realce
import com.songv.app.ui.theme.superficie
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.songv.app.BuildConfig
import com.songv.app.data.Destaque
import com.songv.app.data.ModoTema
import com.songv.app.letra.IDIOMAS_TRADUCAO
import com.songv.app.letra.nomeIdioma
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.Navegador
import com.songv.app.ui.theme.LocalCoresSongV
import com.songv.app.ui.theme.corDoDestaque
import kotlin.math.roundToInt

private const val PERFIL_GITHUB = "https://github.com/victor-kauan-coder"
private const val REPOSITORIO = "https://github.com/victor-kauan-coder/SongV"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfiguracoesScreen(vm: PlayerViewModel, nav: Navegador, contentPadding: PaddingValues) {
    val prefs by vm.preferencias.collectAsState()
    val bib by vm.biblioteca.collectAsState()
    val contexto = LocalContext.current
    val pastas by produceState(emptyList<Pair<String, Int>>(), bib.musicas.size) { value = vm.pastasDisponiveis() }
    var seletorCor by rememberSaveable { mutableStateOf(false) }
    var seletorFundo by rememberSaveable { mutableStateOf(false) }
    val cores = LocalCoresSongV.current
    val pareados by vm.computadoresPareados.collectAsState()
    val saida by vm.saida.collectAsState()
    fun abrir(url: String) = runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.voltar() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
                Text("Configurações", style = MaterialTheme.typography.headlineSmall)
            }
        }

        // ---- Aparência ----
        item { Secao("Aparência") }
        item {
            Text("Tema", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModoTema.entries.forEach { m ->
                    FilterChip(
                        selected = prefs.modoTema == m,
                        onClick = { vm.definirModoTema(m) },
                        label = { Text(m.rotulo) },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cores.sinal, selectedLabelColor = cores.noSinal),
                    )
                }
            }
        }
        item {
            Text("Estilo", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            Text(
                "O acabamento do mini player, da barra de abas, dos cartões e dos botões.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EstiloVisual.entries.forEach { e ->
                    AmostraEstilo(e, selecionado = prefs.estilo == e) { vm.definirEstilo(e) }
                }
            }
        }
        item {
            val escuro = cores.escuro
            Text("Cor do fundo", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            Text(
                if (escuro) "Tons escuros; as superfícies acompanham a cor escolhida." else "Tons claros; as superfícies acompanham a cor escolhida.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FundoTema.entries.forEach { f ->
                    val cor = corDoFundo(f, prefs.corFundoPersonalizada, escuro)
                    AmostraCor(
                        cor = cor,
                        rotulo = if (escuro) f.rotulo else f.rotuloClaro,
                        selecionada = prefs.fundo == f,
                        personalizada = f == FundoTema.PERSONALIZADO && prefs.corFundoPersonalizada == null,
                        contorno = lerp(cor, MaterialTheme.colorScheme.onSurface, 0.3f),
                        onClick = { if (f == FundoTema.PERSONALIZADO) seletorFundo = true else vm.definirFundo(f) },
                    )
                }
            }
        }
        item {
            Text("Cor de destaque", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            Text(
                "Usada no botão de play, na faixa tocando e nos modos ligados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Destaque.entries.forEach { d ->
                    val cor = corDoDestaque(d, prefs.corPersonalizada)
                    AmostraCor(
                        cor = cor,
                        rotulo = d.rotulo,
                        selecionada = prefs.destaque == d,
                        personalizada = d == Destaque.PERSONALIZADO,
                        onClick = { if (d == Destaque.PERSONALIZADO) seletorCor = true else vm.definirDestaque(d) },
                    )
                }
            }
        }
        item {
            LinhaChave(
                "Cores da capa no player",
                "O fundo do player acompanha a capa de cada faixa",
                prefs.coresDaCapa,
                vm::definirCoresDaCapa,
            )
        }

        // ---- Biblioteca ----
        item { Secao("Biblioteca") }
        item {
            Text("Pasta das músicas", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "Onde o SongV procura áudio. O agente salva em Music.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(4.dp))
            OpcaoPasta("Todas as pastas", pastas.sumOf { it.second }, prefs.pastaBiblioteca.isBlank()) { vm.definirPasta("") }
            val lista = (pastas.map { it.first } + prefs.pastaBiblioteca).filter { it.isNotBlank() }.distinct()
            lista.forEach { p ->
                OpcaoPasta(p, pastas.firstOrNull { it.first == p }?.second ?: 0, prefs.pastaBiblioteca.equals(p, ignoreCase = true)) { vm.definirPasta(p) }
            }
        }
        item {
            LinhaChave(
                "Ignorar áudios curtos",
                "Esconde arquivos com menos de 30 segundos (toques, áudios de mensagem)",
                prefs.ignorarCurtas,
                vm::definirIgnorarCurtas,
            )
        }
        item {
            LinhaAcao(Icons.Rounded.Refresh, "Atualizar biblioteca", "${bib.musicas.size} faixas · ${bib.albuns.size} álbuns · ${bib.artistas.size} artistas") {
                vm.carregarBiblioteca(forcar = true)
            }
        }

        // ---- Letras ----
        item { Secao("Letras e tradução") }
        item {
            LinhaChave(
                "Buscar letras online",
                "Quando a faixa não tem letra, oferece buscar na LRCLIB (gratuita, sem conta)",
                prefs.buscaOnline,
                vm::definirBuscaOnline,
            )
        }
        item {
            var aberto by remember { mutableStateOf(false) }
            Box {
                LinhaAcao(Icons.Rounded.Tune, "Idioma da tradução", nomeIdioma(prefs.idiomaTraducao)) { aberto = true }
                DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                    IDIOMAS_TRADUCAO.forEach { idioma ->
                        DropdownMenuItem(
                            text = { Text(idioma.nome) },
                            onClick = { aberto = false; vm.ativarTraducao(idioma.codigo) },
                            trailingIcon = { if (idioma.codigo == prefs.idiomaTraducao) Icon(Icons.Rounded.Check, contentDescription = "Selecionado") },
                        )
                    }
                }
            }
        }
        item {
            LinhaChave(
                "Traduzir automaticamente",
                "Cada faixa com letra já abre traduzida",
                prefs.traduzirAutomaticamente,
                { if (it) vm.ativarTraducao() else vm.desativarTraducao() },
            )
        }

        // ---- Computador ----
        item { Secao("Computador") }
        item {
            LinhaChave(
                "Tocar no computador",
                "No player, “Tocar em” manda o som para um computador com o SongV na mesma rede — mesmo sem internet",
                prefs.tocarNoComputador,
                vm::definirTocarNoComputador,
            )
        }
        if (prefs.tocarNoComputador) {
            items(pareados, key = { "pc_${it.id}" }) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(20.dp))
                    val ligado = (saida as? com.songv.app.conexao.ConexaoComputador.Estado.Conectado)?.computador?.id == c.id
                    Column(Modifier.weight(1f)) {
                        Text(c.nome, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (ligado) "Conectado · pode tocar as músicas deste celular" else "Pareado · ${c.host}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { if (ligado) vm.desconectarComputador() else vm.conectarComputador(c) }) {
                        Text(if (ligado) "Desconectar" else "Conectar")
                    }
                    TextButton(onClick = { vm.esquecerComputador(c.id) }) { Text("Esquecer") }
                }
            }
            item {
                LinhaAcao(Icons.Rounded.Download, "Baixar o SongV para computador", "Windows · na página de versões do GitHub") { abrir("$REPOSITORIO/releases") }
            }
        }

        // ---- Sobre ----
        item { Secao("Sobre") }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                MarcaSongV()
                Text(
                    "Versão ${BuildConfig.VERSION_NAME} · player offline com letra sincronizada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val sinal = cores.sinal
            Row(
                Modifier.fillMaxWidth().clickable { abrir(PERFIL_GITHUB) }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            append("Feito por ")
                            withStyle(SpanStyle(color = sinal)) { append("Victor K") }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text("github.com/victor-kauan-coder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Abrir no navegador", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        item {
            LinhaAcao(Icons.Rounded.Folder, "Código-fonte e novidades", "Repositório, wiki e versões no GitHub") { abrir(REPOSITORIO) }
        }
        item {
            Text(
                "Letras online: LRCLIB · Tradução: Google Tradutor · Fonte: Archivo (SIL Open Font License 1.1) · Desfoque: Haze (Apache 2.0)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (seletorCor) {
        SeletorCorDialog(
            inicial = corDoDestaque(Destaque.PERSONALIZADO, prefs.corPersonalizada),
            onAplicar = { vm.definirCorPersonalizada(it.toArgb()); seletorCor = false },
            onFechar = { seletorCor = false },
        )
    }
    if (seletorFundo) {
        val escuro = cores.escuro
        SeletorCorDialog(
            inicial = prefs.corFundoPersonalizada?.let { Color(it) } ?: Color(0xFF3A2A6B),
            titulo = "Cor do fundo",
            rotuloPrevia = if (escuro) "Como fica no tema escuro" else "Como fica no tema claro",
            previa = { ajustarFundo(it, escuro) },
            onAplicar = { vm.definirCorFundoPersonalizada(it.toArgb()); seletorFundo = false },
            onFechar = { seletorFundo = false },
        )
    }
}

@Composable
private fun Secao(titulo: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            titulo,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun LinhaChave(titulo: String, descricao: String, marcado: Boolean, onMudar: (Boolean) -> Unit) {
    val cores = LocalCoresSongV.current
    Row(
        Modifier.fillMaxWidth().clickable { onMudar(!marcado) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(marcado, onMudar, colors = SwitchDefaults.colors(checkedTrackColor = cores.sinal, checkedThumbColor = cores.noSinal))
    }
}

@Composable
private fun LinhaAcao(icone: androidx.compose.ui.graphics.vector.ImageVector, titulo: String, descricao: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OpcaoPasta(nome: String, quantidade: Int, selecionada: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selecionada, onClick, colors = RadioButtonDefaults.colors(selectedColor = LocalCoresSongV.current.sinal))
        Text(nome, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (quantidade > 0) {
            Text("$quantidade", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

@Composable
private fun AmostraCor(
    cor: Color,
    rotulo: String,
    selecionada: Boolean,
    personalizada: Boolean,
    onClick: () -> Unit,
    contorno: Color = Color.Transparent,
) {
    Column(
        Modifier.width(76.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .border(2.dp, if (selecionada) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                .padding(4.dp)
                .border(1.dp, contorno, CircleShape)
                .background(
                    if (personalizada && !selecionada) Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
                    else Brush.linearGradient(listOf(cor, cor)),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selecionada) Icon(Icons.Rounded.Check, contentDescription = "Selecionada", tint = if (cor.luminanceSimples() > 0.5f) Color.Black else Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(rotulo, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private fun Color.luminanceSimples() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** Seletor HSV: quadrado de saturação/brilho + faixa de matiz. */
@Composable
private fun SeletorCorDialog(
    inicial: Color,
    onAplicar: (Color) -> Unit,
    onFechar: () -> Unit,
    titulo: String = "Sua cor",
    rotuloPrevia: String = "Prévia do destaque",
    previa: (Color) -> Color = { it },
) {
    val hsv = remember(inicial) { FloatArray(3).also { android.graphics.Color.colorToHSV(inicial.toArgb(), it) } }
    var matiz by remember { mutableFloatStateOf(hsv[0]) }
    var saturacao by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.3f)) }
    var brilho by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.5f)) }
    val cor = Color(android.graphics.Color.HSVToColor(floatArrayOf(matiz, saturacao, brilho)))
    val puro = Color(android.graphics.Color.HSVToColor(floatArrayOf(matiz, 1f, 1f)))
    val densidade = LocalDensity.current

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(titulo) },
        text = {
            Column {
                var tamanho by remember { mutableStateOf(IntSize.Zero) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Brush.horizontalGradient(listOf(Color.White, puro)))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .onSizeChanged { tamanho = it }
                        .pointerInput(Unit) {
                            fun aplicar(x: Float, y: Float) {
                                saturacao = (x / size.width).coerceIn(0f, 1f)
                                brilho = (1f - y / size.height).coerceIn(0.25f, 1f)
                            }
                            detectTapGestures { aplicar(it.x, it.y) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { c, _ ->
                                saturacao = (c.position.x / size.width).coerceIn(0f, 1f)
                                brilho = (1f - c.position.y / size.height).coerceIn(0.25f, 1f)
                            }
                        },
                ) {
                    val raio = with(densidade) { 10.dp.toPx() }
                    Box(
                        Modifier
                            .offset { IntOffset((saturacao * tamanho.width - raio).roundToInt(), ((1 - brilho) * tamanho.height - raio).roundToInt()) }
                            .size(20.dp)
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
                Spacer(Modifier.height(16.dp))
                var largura by remember { mutableStateOf(0) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .onSizeChanged { largura = it.width }
                        .pointerInput(Unit) { detectTapGestures { matiz = (it.x / size.width).coerceIn(0f, 1f) * 360f } }
                        .pointerInput(Unit) { detectDragGestures { c, _ -> matiz = (c.position.x / size.width).coerceIn(0f, 1f) * 360f } },
                ) {
                    val meio = with(densidade) { 6.dp.toPx() }
                    Box(
                        Modifier
                            .offset { IntOffset((matiz / 360f * largura - meio).roundToInt(), 0) }
                            .size(12.dp, 28.dp)
                            .border(2.dp, Color.White, CircleShape),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).background(previa(cor), CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(rotuloPrevia, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAplicar(cor) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
}

/** Miniatura do estilo: o fundo, um cartão e o botão de play desenhados com o próprio acabamento. */
@Composable
private fun AmostraEstilo(estilo: EstiloVisual, selecionado: Boolean, onClick: () -> Unit) {
    val atual = acabamento
    val a = remember(estilo, atual) { atual.copy(estilo = estilo) }
    val formaCartao = MaterialTheme.shapes.small
    Column(
        Modifier.width(92.dp).clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "Usar o estilo ${estilo.rotulo}", onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp, 100.dp)
                .border(2.dp, if (selecionado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(3.dp)
                .clip(MaterialTheme.shapes.small)
                .fundoDoApp(a),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(46.dp, 14.dp)
                    .superficie(a, formaCartao, MaterialTheme.colorScheme.surfaceContainer),
            )
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(6.dp)
                    .fillMaxWidth()
                    .height(30.dp)
                    .superficie(a, formaCartao, MaterialTheme.colorScheme.surfaceContainerHigh, sobreConteudo = true)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(18.dp)
                        .background(LocalCoresSongV.current.sinal, CircleShape)
                        .realce(a, CircleShape),
                )
            }
            if (selecionado) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selecionado",
                    tint = LocalCoresSongV.current.noSinal,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).background(LocalCoresSongV.current.sinal, CircleShape).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(estilo.rotulo, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(estilo.descricao, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
