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

/**
 * Leva uma cor qualquer para a faixa de luminosidade de um fundo legível: bem escura no tema
 * escuro, bem clara no claro. O texto continua com contraste seja qual for a cor escolhida.
 */
fun ajustarFundo(cor: Color, escuro: Boolean): Color {
    var c = cor
    var passos = 0
    if (escuro) {
        while (c.luminance() > 0.025f && passos++ < 40) c = lerp(c, Color.Black, 0.1f)
    } else {
        while (c.luminance() < 0.82f && passos++ < 40) c = lerp(c, Color.White, 0.1f)
    }
    return c
}

/**
 * Os degraus de superfície saem da cor de fundo: cada um é o fundo um pouco mais perto do texto.
 * Com o fundo Grafite, dá os mesmos tons de sempre; com outro fundo, a escada inteira acompanha.
 */
fun esquemaEscuro(destaque: Color, fundo: Color = Marca.Grafite1): ColorScheme {
    fun tom(f: Float) = lerp(fundo, Marca.TextoClaro, f)
    val primaria = ajustarContraste(destaque, fundo, 4.5f)
    return darkColorScheme(
        primary = primaria,
        onPrimary = textoSobre(primaria),
        primaryContainer = lerp(tom(0.055f), destaque, 0.28f),
        onPrimaryContainer = lerp(destaque, Color.White, 0.72f),
        secondary = lerp(primaria, Marca.TextoClaro, 0.35f),
        onSecondary = Marca.Tinta,
        secondaryContainer = lerp(tom(0.094f), destaque, 0.2f),
        onSecondaryContainer = Marca.TextoClaro,
        tertiary = Marca.TextoClaroSecundario,
        onTertiary = fundo,
        background = fundo,
        onBackground = Marca.TextoClaro,
        surface = fundo,
        onSurface = Marca.TextoClaro,
        surfaceVariant = tom(0.094f),
        onSurfaceVariant = Marca.TextoClaroSecundario,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = lerp(fundo, Color.Black, 0.3f),
        surfaceContainerLow = tom(0.026f),
        surfaceContainer = tom(0.055f),
        surfaceContainerHigh = tom(0.094f),
        surfaceContainerHighest = tom(0.136f),
        surfaceBright = tom(0.18f),
        surfaceDim = fundo,
        inverseSurface = Marca.TextoClaro,
        inverseOnSurface = tom(0.055f),
        inversePrimary = ajustarContraste(destaque, Marca.TextoClaro, 3f),
        outline = tom(0.4f),
        outlineVariant = tom(0.17f),
        error = Color(0xFFFF8A75),
        onError = Color(0xFF330C04),
        scrim = Color.Black,
    )
}

fun esquemaClaro(destaque: Color, fundo: Color = Marca.Papel1): ColorScheme {
    fun tom(f: Float) = lerp(fundo, Marca.TextoEscuro, f)
    val primaria = ajustarContraste(destaque, fundo, 4.5f)
    return lightColorScheme(
        primary = primaria,
        onPrimary = textoSobre(primaria),
        primaryContainer = lerp(Marca.Papel0, destaque, 0.2f),
        onPrimaryContainer = ajustarContraste(destaque, lerp(Marca.Papel0, destaque, 0.2f), 7f),
        secondary = lerp(primaria, Marca.TextoEscuro, 0.3f),
        onSecondary = Color.White,
        secondaryContainer = lerp(tom(0.037f), destaque, 0.18f),
        onSecondaryContainer = Marca.TextoEscuro,
        tertiary = Marca.TextoEscuroSecundario,
        onTertiary = Color.White,
        background = fundo,
        onBackground = Marca.TextoEscuro,
        surface = fundo,
        onSurface = Marca.TextoEscuro,
        surfaceVariant = tom(0.074f),
        onSurfaceVariant = Marca.TextoEscuroSecundario,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = Marca.Papel0,
        surfaceContainerLow = lerp(fundo, Color.White, 0.45f),
        surfaceContainer = tom(0.037f),
        surfaceContainerHigh = tom(0.074f),
        surfaceContainerHighest = tom(0.12f),
        surfaceBright = Marca.Papel0,
        surfaceDim = tom(0.106f),
        inverseSurface = Marca.Grafite3,
        inverseOnSurface = Marca.TextoClaro,
        inversePrimary = ajustarContraste(destaque, Marca.Grafite3, 4.5f),
        outline = tom(0.47f),
        outlineVariant = tom(0.143f),
        error = Color(0xFFB3261E),
        onError = Color.White,
        scrim = Color.Black,
    )
}
