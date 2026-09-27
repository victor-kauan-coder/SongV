package com.songv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.songv.app.ui.components.BotaoPilula

/**
 * Pedido de acesso aos arquivos de áudio. Se o usuário negou de vez, o sistema não mostra mais
 * o diálogo — então oferecemos abrir as configurações do app (antes o botão simplesmente não
 * fazia nada nesse caso).
 */
@Composable
fun PermissaoScreen(negadaDeVez: Boolean, onPermitir: () -> Unit, onAbrirConfiguracoes: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        LogoSongV(Modifier.size(88.dp))
        Spacer(Modifier.height(24.dp))
        MarcaSongV(logo = false)
        Spacer(Modifier.height(16.dp))
        Text("Suas músicas, suas letras.", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            if (negadaDeVez) {
                "O acesso aos arquivos de áudio foi negado. Para o SongV encontrar suas músicas, permita em Configurações › Permissões › Música e áudio."
            } else {
                "O SongV precisa ler os arquivos de áudio do aparelho para montar sua biblioteca com capas e letras. Nada sai do celular."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        if (negadaDeVez) {
            BotaoPilula("Abrir configurações", onAbrirConfiguracoes, icone = Icons.Rounded.Settings)
            TextButton(onClick = onPermitir) { Text("Tentar de novo") }
        } else {
            BotaoPilula("Permitir acesso às músicas", onPermitir)
        }
    }
}
