package com.songv.app.ui.screens

import androidx.compose.foundation.clickable
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
            ItemFolha(Icons.Rounded.CloudDownload, if (estadoLetra.letra?.linhas.isNullOrEmpty()) "Buscar letra online" else "Buscar outra versão online") {
                vm.buscarLetraOnline(); onFechar()
            }
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
