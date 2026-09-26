package com.songv.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.songv.app.R

/**
 * Archivo, uma grotesca com vários eixos de largura: a largura normal cuida da interface, a
 * condensada cabe mais palavras por linha na letra e a expandida dá o tom de etiqueta de
 * aparelho de som à marca e aos títulos grandes.
 */
val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
    Font(R.font.archivo_extrabold, FontWeight.ExtraBold),
)

val ArchivoCondensada = FontFamily(Font(R.font.archivo_condensed_bold, FontWeight.Bold))

val ArchivoExpandida = FontFamily(
    Font(R.font.archivo_expanded_extrabold, FontWeight.ExtraBold),
    Font(R.font.archivo_expanded_black, FontWeight.Black),
)

private fun estilo(familia: FontFamily, peso: FontWeight, tamanho: Int, altura: Int, espacamento: Float) = TextStyle(
    fontFamily = familia,
    fontWeight = peso,
    fontSize = tamanho.sp,
    lineHeight = altura.sp,
    letterSpacing = espacamento.sp,
)

val TipografiaSongV = Typography(
    displayLarge = estilo(ArchivoExpandida, FontWeight.Black, 44, 48, -1.2f),
    displayMedium = estilo(ArchivoExpandida, FontWeight.Black, 36, 40, -0.9f),
    displaySmall = estilo(ArchivoExpandida, FontWeight.ExtraBold, 30, 34, -0.6f),
    headlineLarge = estilo(Archivo, FontWeight.ExtraBold, 30, 36, -0.6f),
    headlineMedium = estilo(Archivo, FontWeight.ExtraBold, 26, 32, -0.4f),
    headlineSmall = estilo(Archivo, FontWeight.Bold, 22, 28, -0.3f),
    titleLarge = estilo(Archivo, FontWeight.Bold, 20, 26, -0.2f),
    titleMedium = estilo(Archivo, FontWeight.SemiBold, 16, 22, -0.1f),
    titleSmall = estilo(Archivo, FontWeight.SemiBold, 14, 20, 0f),
    bodyLarge = estilo(Archivo, FontWeight.Normal, 16, 24, 0.1f),
    bodyMedium = estilo(Archivo, FontWeight.Normal, 14, 20, 0.1f),
    bodySmall = estilo(Archivo, FontWeight.Normal, 12, 16, 0.2f),
    labelLarge = estilo(Archivo, FontWeight.SemiBold, 14, 20, 0.1f),
    labelMedium = estilo(Archivo, FontWeight.SemiBold, 12, 16, 0.3f),
    labelSmall = estilo(Archivo, FontWeight.SemiBold, 11, 16, 0.4f),
)

object EstilosSongV {
    /** Linhas da letra: condensada e pesada, mesma fonte ativa ou não (só a opacidade muda, sem reflow). */
    val letra = estilo(ArchivoCondensada, FontWeight.Bold, 28, 35, -0.3f)
    val traducao = estilo(Archivo, FontWeight.Medium, 17, 23, 0f)
    val marca = estilo(ArchivoExpandida, FontWeight.Black, 24, 28, -0.8f)
    /** Números alinhados (tempos, posições) não "dançam" enquanto mudam. */
    val numeros = TextStyle(fontFeatureSettings = "tnum")
}
