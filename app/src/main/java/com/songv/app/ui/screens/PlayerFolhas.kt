package com.songv.app.ui.screens

import com.songv.app.ui.theme.acabamento
import com.songv.app.ui.theme.corDeFolha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.songv.app.letra.CandidatoLetra
import com.songv.app.letra.LetraRepository
import com.songv.app.ui.components.BotaoPilula
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.songv.app.data.ExibicaoTraducao
import com.songv.app.data.Preferencias
import com.songv.app.letra.IDIOMAS_TRADUCAO
import com.songv.app.letra.TradutorLetra
import com.songv.app.letra.nomeIdioma
import com.songv.app.player.EstadoLetra
import com.songv.app.player.PlayerViewModel
import com.songv.app.player.TimerSono
import com.songv.app.ui.components.formatarTempo
import com.songv.app.ui.theme.EstilosSongV
import com.songv.app.ui.theme.LocalCoresSongV
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolhaBase(titulo: String, onFechar: () -> Unit, conteudo: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = acabamento.corDeFolha(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            conteudo()
        }
    }
}

@Composable
private fun LinhaComChave(titulo: String, descricao: String, marcado: Boolean, onMudar: (Boolean) -> Unit) {
    val cores = LocalCoresSongV.current
    Row(
        Modifier.fillMaxWidth().clickable { onMudar(!marcado) }.padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = marcado,
            onCheckedChange = onMudar,
            colors = SwitchDefaults.colors(checkedTrackColor = cores.sinal, checkedThumbColor = cores.noSinal),
        )
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Chip(texto: String, selecionado: Boolean, onClick: () -> Unit) {
    val cores = LocalCoresSongV.current
    FilterChip(
        selected = selecionado,
        onClick = onClick,
        label = { Text(texto) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = cores.sinal, selectedLabelColor = cores.noSinal),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TraducaoSheet(vm: PlayerViewModel, prefs: Preferencias, estadoLetra: EstadoLetra, onFechar: () -> Unit) {
    FolhaBase("Tradução da letra", onFechar) {
        LinhaComChave(
            titulo = "Traduzir as letras",
            descricao = "Fica ligada nas próximas faixas também",
            marcado = prefs.traduzirAutomaticamente,
            onMudar = { if (it) vm.ativarTraducao() else vm.desativarTraducao() },
        )
        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Rotulo("Traduzir para")
        FlowRow(
            Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IDIOMAS_TRADUCAO.forEach { idioma ->
                Chip(idioma.nome, prefs.traduzirAutomaticamente && TradutorLetra.mesmoIdioma(idioma.codigo, prefs.idiomaTraducao)) {
                    vm.ativarTraducao(idioma.codigo)
                }
            }
        }
        Rotulo("Como mostrar")
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExibicaoTraducao.entries.forEach { e ->
                Chip(e.rotulo, prefs.exibicaoTraducao == e) { vm.definirExibicaoTraducao(e) }
            }
        }
        Spacer(Modifier.height(8.dp))
        LinhaComChave(
            titulo = "Mostrar pronúncia",
            descricao = "Romanização para letras em coreano, japonês, chinês, russo…",
            marcado = prefs.mostrarRomanizacao,
            onMudar = vm::definirRomanizacao,
        )
        val origem = estadoLetra.traducao?.idiomaOrigem
        Text(
            buildString {
                if (origem != null) append("Idioma detectado: ${nomeIdioma(origem)}. ")
                append("Tradução automática do Google Tradutor — precisa de internet na primeira vez; depois fica salva no aparelho.")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun TimerSheet(vm: PlayerViewModel, timer: TimerSono.Estado, onFechar: () -> Unit) {
    FolhaBase("Timer de sono", onFechar) {
        if (timer.ativo) {
            val restante by produceState(timer.restanteMs(), timer) {
                while (true) {
                    value = timer.restanteMs()
                    delay(1_000)
                }
            }
            Text(
                if (timer.fimDaFaixa) "Pausa quando a faixa atual acabar" else "Pausa em ${formatarTempo(restante)}",
                style = MaterialTheme.typography.headlineSmall.merge(EstilosSongV.numeros),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ItemFolha(Icons.Rounded.TimerOff, "Desligar timer") { vm.cancelarTimer(); onFechar() }
            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        } else {
            Text(
                "O volume abaixa devagar nos últimos segundos antes de pausar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
        }
        listOf(15, 30, 45, 60, 90).forEach { min ->
            ItemFolha(Icons.Rounded.Timer, "$min minutos") { vm.timerEmMinutos(min); onFechar() }
        }
        ItemFolha(Icons.Rounded.Timer, "Quando esta faixa acabar") { vm.timerFimDaFaixa(); onFechar() }
    }
}

@Composable
fun OpcoesLetraSheet(
    vm: PlayerViewModel,
    estadoLetra: EstadoLetra,
    buscaOnline: Boolean,
    onImportar: () -> Unit,
    onFechar: () -> Unit,
) {
    val prefs by vm.preferencias.collectAsState()
    FolhaBase("Letra", onFechar) {
        estadoLetra.letra?.fonte?.let { fonte ->
            Text(
                "Fonte: ${fonte.rotulo}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
        }
        if (buscaOnline) {
            if (estadoLetra.letra?.linhas.isNullOrEmpty()) {
                ItemFolha(Icons.Rounded.CloudDownload, "Buscar letra online") { vm.buscarLetraOnline(); onFechar() }
            }
            ItemFolha(Icons.Rounded.Search, "Escolher outra versão…") { vm.abrirPesquisaLetra(); onFechar() }
        }
        ItemFolha(Icons.Rounded.FileOpen, "Importar arquivo .lrc") { onImportar(); onFechar() }
        if (vm.temLetraSalva()) {
            ItemFolha(Icons.Rounded.Restore, "Voltar para a letra do arquivo") { vm.removerLetraSalva(); onFechar() }
        }
        Rotulo("Tamanho do texto")
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = MaterialTheme.typography.bodySmall)
            val cores = LocalCoresSongV.current
            Slider(
                value = prefs.escalaLetra,
                onValueChange = vm::definirEscalaLetra,
                valueRange = 0.8f..1.4f,
                steps = 5,
                colors = SliderDefaults.colors(thumbColor = cores.sinal, activeTrackColor = cores.sinal),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            Text("A", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ItemFolha(icone: ImageVector, texto: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(texto, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Pesquisa manual de letra: quando a busca automática erra (tags vindas do YouTube, versão ao
 * vivo, artista escrito diferente), dá para ajustar título e artista e escolher a versão.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PesquisaLetraSheet(vm: PlayerViewModel, onFechar: () -> Unit) {
    val sugestao = remember { vm.sugestaoPesquisaLetra() }
    var titulo by rememberSaveable { mutableStateOf(sugestao?.titulo.orEmpty()) }
    var artista by rememberSaveable { mutableStateOf(sugestao?.artista.orEmpty()) }
    var rodada by remember { mutableIntStateOf(0) }
    var resultado by remember { mutableStateOf<LetraRepository.Pesquisa?>(null) }
    val foco = LocalFocusManager.current

    LaunchedEffect(rodada) {
        resultado = null
        if (titulo.isNotBlank()) resultado = vm.pesquisarLetras(titulo, artista)
    }

    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = acabamento.corDeFolha(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text("Buscar letra", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Ajuste o título e o artista se a busca automática errou. As letras vêm da LRCLIB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            val buscar: () -> Unit = {
                foco.clearFocus()
                rodada++
            }
            OutlinedTextField(
                value = titulo,
                onValueChange = { titulo = it },
                label = { Text("Título") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = artista,
                onValueChange = { artista = it },
                label = { Text("Artista") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { buscar() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                BotaoPilula("Buscar", onClick = buscar, icone = Icons.Rounded.Search)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 460.dp)) {
                when (val r = resultado) {
                    null -> CircularProgressIndicator(
                        color = LocalCoresSongV.current.sinal,
                        modifier = Modifier.align(Alignment.Center).size(32.dp),
                    )
                    LetraRepository.Pesquisa.SemConexao -> AvisoPesquisa("Sem internet", "Conecte-se para buscar letras.", null)
                    LetraRepository.Pesquisa.Falha -> AvisoPesquisa("A LRCLIB não respondeu", "Pode ser instabilidade momentânea.") { rodada++ }
                    is LetraRepository.Pesquisa.Ok -> if (r.candidatos.isEmpty()) {
                        AvisoPesquisa("Nada encontrado", "Tente só o título, sem detalhes como ao vivo ou o nome do álbum.", null)
                    } else {
                        LazyColumn {
                            items(r.candidatos, key = { it.id }) { c ->
                                LinhaCandidato(c, recomendada = c == r.candidatos.first() && c.aceitavel) { vm.aplicarLetraEscolhida(c) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvisoPesquisa(titulo: String, texto: String, onTentar: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onTentar != null) TextButton(onClick = onTentar) { Text("Tentar de novo") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinhaCandidato(c: CandidatoLetra, recomendada: Boolean, onEscolher: () -> Unit) {
    val cores = LocalCoresSongV.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Usar esta letra", onClick = onEscolher)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(c.faixa, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(c.artista, c.album).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (recomendada) Selo("Recomendada", cores.sinal, cores.noSinal)
            when {
                c.temSincronia -> Selo("Sincronizada", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                c.instrumental -> Selo("Instrumental", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Selo("Sem sincronia", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val duracao = formatarTempo((c.duracaoSeg * 1000).toLong())
            val diferenca = c.diferencaSeg
            Selo(
                when {
                    diferenca == null -> duracao
                    diferenca <= 2 -> "$duracao · mesma duração"
                    else -> "$duracao · ${diferenca}s de diferença"
                },
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Selo(texto: String, fundo: Color, frente: Color) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall.merge(EstilosSongV.numeros),
        color = frente,
        modifier = Modifier.background(fundo, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
