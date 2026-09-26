package com.songv.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Identidade "hi-fi analógico": superfícies em grafite quente (alumínio anodizado de aparelho
 * de som) e um laranja-sinal reservado para o que é ação ou estado ativo — o botão de play, a
 * faixa tocando, o LED de um modo ligado.
 */
object Marca {
    val Laranja = Color(0xFFFF6B1A)
    val Tinta = Color(0xFF1C130E) // texto/ícone sobre o laranja

    // Grafite (modo escuro)
    val Grafite0 = Color(0xFF0E0C0B)
    val Grafite1 = Color(0xFF141211) // fundo
    val Grafite2 = Color(0xFF1A1716)
    val Grafite3 = Color(0xFF211D1B)
    val Grafite4 = Color(0xFF2A2522)
    val Grafite5 = Color(0xFF342E2A)
    val TextoClaro = Color(0xFFF3EEE9)
    val TextoClaroSecundario = Color(0xFFB9AFA7)

    // Papel (modo claro) — cinza quente, não creme.
    val Papel0 = Color(0xFFFFFFFF)
    val Papel1 = Color(0xFFF4F1EE) // fundo
    val Papel2 = Color(0xFFECE7E3)
    val Papel3 = Color(0xFFE4DEDA)
    val Papel4 = Color(0xFFDAD3CE)
    val TextoEscuro = Color(0xFF1B1613)
    val TextoEscuroSecundario = Color(0xFF5C524B)

    /** O palco do player é sempre escuro, em qualquer tema: a capa é quem dá a cor. */
    val Palco = Color(0xFF110E0D)
}

/** Cores de marca que não existem no ColorScheme do Material. */
@Immutable
data class CoresSongV(
    /** Preenchimento de ação principal (botão de play, LED). É a cor de destaque pura. */
    val sinal: Color,
    val noSinal: Color,
    /** Destaque ajustado para ter contraste de texto sobre o palco escuro do player. */
    val sinalNoPalco: Color,
    val escuro: Boolean,
)

val LocalCoresSongV = staticCompositionLocalOf {
    CoresSongV(Marca.Laranja, Marca.Tinta, Marca.Laranja, escuro = true)
}

fun contraste(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
}

/** Clareia ou escurece [cor] aos poucos até atingir [alvo] de contraste contra [fundo]. */
fun ajustarContraste(cor: Color, fundo: Color, alvo: Float): Color {
    val direcao = if (fundo.luminance() > 0.4f) Color.Black else Color.White
    var c = cor
    var passos = 0
    while (contraste(c, fundo) < alvo && passos < 24) {
        c = lerp(c, direcao, 0.07f)
        passos++
    }
    return c
}

private fun textoSobre(cor: Color): Color =
    if (contraste(Marca.Tinta, cor) >= contraste(Color.White, cor)) Marca.Tinta else Color.White

fun coresSongV(destaque: Color, escuro: Boolean) = CoresSongV(
    sinal = destaque,
    noSinal = textoSobre(destaque),
    sinalNoPalco = ajustarContraste(destaque, Marca.Palco, 4.5f),
    escuro = escuro,
)

fun esquemaEscuro(destaque: Color): ColorScheme {
    val primaria = ajustarContraste(destaque, Marca.Grafite1, 4.5f)
    return darkColorScheme(
        primary = primaria,
        onPrimary = textoSobre(primaria),
        primaryContainer = lerp(Marca.Grafite3, destaque, 0.28f),
        onPrimaryContainer = lerp(destaque, Color.White, 0.72f),
        secondary = lerp(primaria, Marca.TextoClaro, 0.35f),
        onSecondary = Marca.Tinta,
        secondaryContainer = lerp(Marca.Grafite4, destaque, 0.2f),
        onSecondaryContainer = Marca.TextoClaro,
        tertiary = Marca.TextoClaroSecundario,
        onTertiary = Marca.Grafite1,
        background = Marca.Grafite1,
        onBackground = Marca.TextoClaro,
        surface = Marca.Grafite1,
        onSurface = Marca.TextoClaro,
        surfaceVariant = Marca.Grafite4,
        onSurfaceVariant = Marca.TextoClaroSecundario,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = Marca.Grafite0,
        surfaceContainerLow = Marca.Grafite2,
        surfaceContainer = Marca.Grafite3,
        surfaceContainerHigh = Marca.Grafite4,
        surfaceContainerHighest = Marca.Grafite5,
        surfaceBright = Color(0xFF3B3430),
        surfaceDim = Marca.Grafite1,
        inverseSurface = Marca.TextoClaro,
        inverseOnSurface = Marca.Grafite3,
        inversePrimary = ajustarContraste(destaque, Marca.TextoClaro, 3f),
        outline = Color(0xFF6E645D),
        outlineVariant = Color(0xFF3A3330),
        error = Color(0xFFFF8A75),
        onError = Color(0xFF330C04),
        scrim = Color.Black,
    )
}

fun esquemaClaro(destaque: Color): ColorScheme {
    val primaria = ajustarContraste(destaque, Marca.Papel1, 4.5f)
    return lightColorScheme(
        primary = primaria,
        onPrimary = textoSobre(primaria),
        primaryContainer = lerp(Marca.Papel0, destaque, 0.2f),
        onPrimaryContainer = ajustarContraste(destaque, lerp(Marca.Papel0, destaque, 0.2f), 7f),
        secondary = lerp(primaria, Marca.TextoEscuro, 0.3f),
        onSecondary = Color.White,
        secondaryContainer = lerp(Marca.Papel2, destaque, 0.18f),
        onSecondaryContainer = Marca.TextoEscuro,
        tertiary = Marca.TextoEscuroSecundario,
        onTertiary = Color.White,
        background = Marca.Papel1,
        onBackground = Marca.TextoEscuro,
        surface = Marca.Papel1,
        onSurface = Marca.TextoEscuro,
        surfaceVariant = Marca.Papel3,
        onSurfaceVariant = Marca.TextoEscuroSecundario,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = Marca.Papel0,
        surfaceContainerLow = Color(0xFFF9F7F5),
        surfaceContainer = Marca.Papel2,
        surfaceContainerHigh = Marca.Papel3,
        surfaceContainerHighest = Marca.Papel4,
        surfaceBright = Marca.Papel0,
        surfaceDim = Color(0xFFDDD6D1),
        inverseSurface = Marca.Grafite3,
        inverseOnSurface = Marca.TextoClaro,
        inversePrimary = ajustarContraste(destaque, Marca.Grafite3, 4.5f),
        outline = Color(0xFF8D827A),
        outlineVariant = Color(0xFFD5CDC7),
        error = Color(0xFFB3261E),
        onError = Color.White,
        scrim = Color.Black,
    )
}
