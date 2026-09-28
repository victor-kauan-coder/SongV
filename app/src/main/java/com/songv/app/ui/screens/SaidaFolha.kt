package com.songv.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.songv.app.conexao.Computador
import com.songv.app.conexao.ConexaoComputador.Estado
import com.songv.app.player.PlayerViewModel
import com.songv.app.ui.theme.EstilosSongV
import com.songv.app.ui.theme.LocalCoresSongV
import com.songv.app.ui.theme.acabamento
import com.songv.app.ui.theme.corDeFolha

private const val PORTA_PADRAO = 47800

/**
 * "Tocar em…": este celular ou um computador com o SongV na mesma rede. A busca na rede só roda
 * enquanto a folha está aberta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaidaSheet(vm: PlayerViewModel, onFechar: () -> Unit) {
    val estado by vm.saida.collectAsState()
    val pareados by vm.computadoresPareados.collectAsState()
    val achados by vm.computadoresAchados.collectAsState()
    var endereco by rememberSaveable { mutableStateOf(false) }
    val cores = LocalCoresSongV.current

    DisposableEffect(Unit) {
        vm.procurarComputadores(true)
        onDispose { vm.procurarComputadores(false) }
    }

    // Pareados primeiro (com o endereço atual, se apareceram na rede), depois os novos.
    val naRede = achados.map { it.id }.toSet()
    val lista = remember(pareados, achados) {
        val atualizados = pareados.map { p -> achados.firstOrNull { it.id == p.id }?.let { a -> p.copy(host = a.host, porta = a.porta, nome = a.nome) } ?: p }
        atualizados + achados.filter { a -> pareados.none { it.id == a.id } }
    }

    ModalBottomSheet(
        onDismissRequest = onFechar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = acabamento.corDeFolha(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Tocar em", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Computadores com o SongV aberto na mesma rede. Funciona sem internet, e tudo vai cifrado.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
            )

            val conectado = estado as? Estado.Conectado
            val somLa = conectado?.somNoComputador == true
            ItemSaida(Icons.Rounded.Smartphone, "Este celular", null, selecionado = !somLa) {
                if (somLa) vm.tocarNoCelular()
                onFechar()
            }
            lista.forEach { c ->
                val ligado = conectado?.computador?.id == c.id
                val aqui = ligado && somLa
                val conectando = (estado as? Estado.Conectando)?.nome == c.nome
                ItemSaida(
                    Icons.Rounded.Computer,
                    c.nome,
                    when {
                        aqui -> "Tocando aqui"
                        ligado -> "Conectado · toque para o som sair nele"
                        conectando -> "Conectando…"
                        c.pareado && c.id in naRede -> "Pareado · na rede"
                        c.pareado -> "Pareado · ${c.host}"
                        else -> "Novo · vai mostrar um código para conferir"
                    },
                    selecionado = aqui,
                    carregando = conectando,
                ) { if (!aqui) vm.tocarEm(c) }
            }
            if (achados.isEmpty()) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cores.sinal)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Procurando na rede… Abra o SongV no computador.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ItemSaida(Icons.Rounded.Add, "Adicionar pelo endereço", "O IP e a porta aparecem na tela do computador", selecionado = false) {
                endereco = true
            }

            conectado?.let { e ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${e.computador.nome} também pode tocar as músicas deste celular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.desconectarComputador(); onFechar() }) { Text("Desconectar") }
                }
            }
            conectado?.takeIf { it.somNoComputador }?.let { e ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                var volume by remember(e.computador.id) { mutableFloatStateOf(e.volume) }
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Slider(
                        value = volume,
                        onValueChange = { volume = it; vm.volumeComputador(it) },
                        colors = SliderDefaults.colors(thumbColor = cores.sinal, activeTrackColor = cores.sinal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Volume no ${e.computador.nome}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 64.dp),
                )
            }
        }
    }

    if (endereco) {
        EnderecoDialogo(
            onConectar = { host, porta ->
                endereco = false
                vm.tocarEm(Computador(id = "", nome = host, host = host, porta = porta))
            },
            onFechar = { endereco = false },
        )
    }
}

@Composable
private fun ItemSaida(
    icone: ImageVector,
    titulo: String,
    subtitulo: String?,
    selecionado: Boolean,
    carregando: Boolean = false,
    onClick: () -> Unit,
) {
    val cores = LocalCoresSongV.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icone, contentDescription = null, tint = if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitulo?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            carregando -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = cores.sinal)
            selecionado -> Icon(Icons.Rounded.Check, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun EnderecoDialogo(onConectar: (String, Int) -> Unit, onFechar: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    fun interpretar(): Pair<String, Int>? {
        val t = texto.trim().removePrefix("http://")
        if (t.isEmpty()) return null
        val host = t.substringBeforeLast(':', t).trim()
        val porta = if (':' in t) t.substringAfterLast(':').toIntOrNull() else PORTA_PADRAO
        return if (host.isNotEmpty() && porta != null && porta in 1..65535) host to porta else null
    }
    val valido = interpretar() != null
    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Adicionar pelo endereço") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Digite o endereço que aparece no SongV do computador, em Dispositivos (o botão do celular, no topo).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(64) },
                    placeholder = { Text("192.168.0.12:$PORTA_PADRAO") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.merge(EstilosSongV.numeros),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { interpretar()?.let { (h, p) -> onConectar(h, p) } }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { interpretar()?.let { (h, p) -> onConectar(h, p) } }, enabled = valido) { Text("Conectar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
}

/** O código de 6 dígitos do pareamento. Aparece em qualquer tela enquanto o pareamento acontece. */
@Composable
fun PareamentoDialogo(vm: PlayerViewModel) {
    val estado by vm.saida.collectAsState()
    val p = estado as? Estado.Pareando ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Parear com ${p.nome}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Confira se o mesmo código aparece no computador. Se for diferente, cancele.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${p.codigo.take(3)} ${p.codigo.drop(3)}",
                    style = EstilosSongV.marca.merge(EstilosSongV.numeros).copy(fontSize = MaterialTheme.typography.displaySmall.fontSize),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                )
                if (p.confirmadoAqui) {
                    Text(
                        "Aguardando a confirmação no computador…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.confirmarPareamento(true) }, enabled = !p.confirmadoAqui) { Text("Os códigos são iguais") }
        },
        dismissButton = { TextButton(onClick = { vm.confirmarPareamento(false) }) { Text("Cancelar") } },
    )
}
