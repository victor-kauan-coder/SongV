package com.songv.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.songv.app.ui.theme.ModoLuminosidade
import com.songv.app.ui.theme.TemaApp

/**
 * Tela de Configurações: aparência (paleta de cores, claro/escuro) e outras preferências do app.
 * Ficam aqui, fora da tela inicial, pra deixar a Home limpa e focada em navegar pela música.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesScreen(
    temaAtual: TemaApp,
    corPersonalizadaAtual: Color,
    modoLuminosidade: ModoLuminosidade,
    onVoltar: () -> Unit,
    onTemaEscolhido: (TemaApp) -> Unit,
    onCorPersonalizadaEscolhida: (Color) -> Unit,
    onAlternarLuminosidade: () -> Unit
) {
    var secaoAbertaAparencia by remember { mutableStateOf(true) }
    var mostrarSeletorCorLivre by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                CabecalhoSecao(
                    titulo = "Aparência",
                    subtitulo = "Cor do app e modo claro/escuro",
                    icone = Icons.Filled.Palette,
                    expandida = secaoAbertaAparencia,
                    onClick = { secaoAbertaAparencia = !secaoAbertaAparencia }
                )
            }

            item {
                AnimatedVisibility(visible = secaoAbertaAparencia, enter = expandVertically(), exit = shrinkVertically()) {
                    Column {
                        ItemLuminosidade(
                            modoLuminosidade = modoLuminosidade,
                            onClick = onAlternarLuminosidade
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Paleta de cores",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Escolha a cor de destaque do app. O modo claro/escuro acima continua funcionando com qualquer paleta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(16.dp))

                            if (mostrarSeletorCorLivre) {
                                SeletorCorLivre(
                                    corInicial = corPersonalizadaAtual,
                                    onCorEscolhida = onCorPersonalizadaEscolhida
                                )
                                Spacer(Modifier.height(12.dp))
                                TextButton(onClick = { mostrarSeletorCorLivre = false }) {
                                    Text("Voltar às paletas prontas")
                                }
                            } else {
                                GradeTemas(
                                    temaAtual = temaAtual,
                                    corPersonalizada = corPersonalizadaAtual,
                                    onTemaClick = onTemaEscolhido,
                                    onPersonalizadoClick = { mostrarSeletorCorLivre = true },
                                    altura = 480.dp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }

            item {
                CabecalhoSecao(
                    titulo = "Sobre",
                    subtitulo = "SongV — player de música local",
                    icone = Icons.Filled.Info,
                    expandida = null,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun CabecalhoSecao(
    titulo: String,
    subtitulo: String,
    icone: ImageVector,
    expandida: Boolean?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = expandida != null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (expandida != null) {
            Icon(
                if (expandida) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ItemLuminosidade(modoLuminosidade: ModoLuminosidade, onClick: () -> Unit) {
    val escuro = modoLuminosidade == ModoLuminosidade.ESCURO
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (escuro) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Modo escuro", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (escuro) "Ativado" else "Desativado — usando modo claro",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(checked = escuro, onCheckedChange = { onClick() })
    }
}
