package com.songv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TipografiaSongV = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

/** Cor padrão usada como fallback do seletor de cor livre antes do usuário escolher a dele. */
val RoxoDarkPrimaryPublico = Color(0xFF9D4EDD)

/**
 * @param corPersonalizada cor de destaque usada quando [tema] é [TemaApp.PERSONALIZADO].
 *        Ignorada para os demais temas.
 * @param modoLuminosidade alterna entre a variante clara e escura da cor selecionada em [tema],
 *        de forma totalmente independente — trocar a luminosidade nunca muda a cor de destaque.
 */
@Composable
fun SongVTheme(
    tema: TemaApp,
    corPersonalizada: Color = RoxoDarkPrimaryPublico,
    modoLuminosidade: ModoLuminosidade = ModoLuminosidade.ESCURO,
    content: @Composable () -> Unit
) {
    val escuro = modoLuminosidade == ModoLuminosidade.ESCURO

    val colorScheme = when (tema) {
        TemaApp.CLARO, TemaApp.ESCURO -> if (escuro) EsquemaEscuroCores else EsquemaClaroCores
        TemaApp.ROXO_DARK -> if (escuro) EsquemaRoxoDarkCores else EsquemaRoxoDarkClaroCores
        TemaApp.VERDE -> if (escuro) EsquemaVerdeCores else EsquemaVerdeClaroCores
        TemaApp.AZUL -> if (escuro) EsquemaAzulCores else EsquemaAzulClaroCores
        TemaApp.SUNSET -> if (escuro) EsquemaSunsetCores else EsquemaSunsetClaroCores
        TemaApp.ROSA -> if (escuro) EsquemaRosaCores else EsquemaRosaClaroCores
        TemaApp.AMBAR -> if (escuro) EsquemaAmbarCores else EsquemaAmbarClaroCores
        TemaApp.CIANO -> if (escuro) EsquemaCianoCores else EsquemaCianoClaroCores
        TemaApp.NOITE_DOURADA -> if (escuro) EsquemaNoiteDouradaCores else EsquemaNoiteDouradaClaroCores
        TemaApp.VIOLETA_PROFUNDO -> if (escuro) EsquemaVioletaProfundoCores else EsquemaVioletaProfundoClaroCores
        TemaApp.PRATA_MINIMAL -> if (escuro) EsquemaPrataMinimalCores else EsquemaPrataMinimalClaroCores
        TemaApp.PERSONALIZADO -> if (escuro) {
            montarEsquemaPersonalizado(corPersonalizada)
        } else {
            montarEsquemaPersonalizadoClaro(corPersonalizada)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TipografiaSongV,
        content = content
    )
}
