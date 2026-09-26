package com.songv.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Identificador de cada tema selecionável nas configurações do app. */
enum class TemaApp(val label: String) {
    CLARO("Claro"),
    ESCURO("Escuro"),
    ROXO_DARK("Roxo Dark"),
    VERDE("Verde"),
    AZUL("Azul"),
    SUNSET("Sunset"),
    ROSA("Rosa"),
    AMBAR("Âmbar"),
    CIANO("Ciano"),
    NOITE_DOURADA("Noite Dourada"),
    VIOLETA_PROFUNDO("Violeta Profundo"),
    PRATA_MINIMAL("Prata Minimal"),
    PERSONALIZADO("Personalizado")
}

/**
 * Modo de luminosidade, independente da cor de destaque escolhida. Alternável a qualquer
 * momento pelo botão de sol/lua na tela inicial, sem alterar o [TemaApp] selecionado.
 */
enum class ModoLuminosidade { CLARO, ESCURO }

// ---- Claro ----
private val ClaroPrimary = Color(0xFF6750A4)
private val ClaroBackground = Color(0xFFFFFBFE)
private val ClaroSurface = Color(0xFFF5F1FA)

val EsquemaClaroCores = lightColorScheme(
    primary = ClaroPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF625B71),
    background = ClaroBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = ClaroSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
)

// ---- Escuro neutro ----
private val EscuroBackground = Color(0xFF121212)
private val EscuroSurface = Color(0xFF1E1E1E)

val EsquemaEscuroCores = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    secondary = Color(0xFFCCC2DC),
    background = EscuroBackground,
    onBackground = Color(0xFFE6E1E5),
    surface = EscuroSurface,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2930),
)

// ---- Roxo Dark (tema principal do app) ----
private val RoxoDarkBackground = Color(0xFF120B1F) // roxo quase preto
private val RoxoDarkSurface = Color(0xFF1E1230)
private val RoxoDarkPrimary = Color(0xFF9D4EDD)   // roxo vibrante de destaque
private val RoxoDarkAccent = Color(0xFFC77DFF)

val EsquemaRoxoDarkCores = darkColorScheme(
    primary = RoxoDarkPrimary,
    onPrimary = Color.White,
    secondary = RoxoDarkAccent,
    onSecondary = Color.White,
    background = RoxoDarkBackground,
    onBackground = Color(0xFFEDE3FF),
    surface = RoxoDarkSurface,
    onSurface = Color(0xFFEDE3FF),
    surfaceVariant = Color(0xFF2D1B47),
    tertiary = Color(0xFFFF6EC7),
)

// ---- Verde (estilo Spotify) ----
private val VerdeBackground = Color(0xFF0D1210)
private val VerdeSurface = Color(0xFF182420)
private val VerdePrimary = Color(0xFF1DB954)

val EsquemaVerdeCores = darkColorScheme(
    primary = VerdePrimary,
    onPrimary = Color.Black,
    secondary = Color(0xFF1ED760),
    background = VerdeBackground,
    onBackground = Color(0xFFE8F5EC),
    surface = VerdeSurface,
    onSurface = Color(0xFFE8F5EC),
    surfaceVariant = Color(0xFF223028),
)

// ---- Azul ----
private val AzulBackground = Color(0xFF0B1220)
private val AzulSurface = Color(0xFF141F33)
private val AzulPrimary = Color(0xFF4FA3FF)

val EsquemaAzulCores = darkColorScheme(
    primary = AzulPrimary,
    onPrimary = Color.Black,
    secondary = Color(0xFF8AC7FF),
    background = AzulBackground,
    onBackground = Color(0xFFE3EEFF),
    surface = AzulSurface,
    onSurface = Color(0xFFE3EEFF),
    surfaceVariant = Color(0xFF1D2C47),
)

// ---- Sunset (laranja/vermelho quente) ----
private val SunsetBackground = Color(0xFF1A0E0A)
private val SunsetSurface = Color(0xFF2B1712)
private val SunsetPrimary = Color(0xFFFF6B4A)

val EsquemaSunsetCores = darkColorScheme(
    primary = SunsetPrimary,
    onPrimary = Color.Black,
    secondary = Color(0xFFFFA26B),
    background = SunsetBackground,
    onBackground = Color(0xFFFFE8E0),
    surface = SunsetSurface,
    onSurface = Color(0xFFFFE8E0),
    surfaceVariant = Color(0xFF3D231C),
)

// ---- Rosa ----
private val RosaBackground = Color(0xFF1A0E16)
private val RosaSurface = Color(0xFF2B1726)
private val RosaPrimary = Color(0xFFFF5DA2)

val EsquemaRosaCores = darkColorScheme(
    primary = RosaPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFFFF8FC4),
    background = RosaBackground,
    onBackground = Color(0xFFFFE0F0),
    surface = RosaSurface,
    onSurface = Color(0xFFFFE0F0),
    surfaceVariant = Color(0xFF3D1F35),
)

// ---- Âmbar ----
private val AmbarBackground = Color(0xFF1C1608)
private val AmbarSurface = Color(0xFF2E2510)
private val AmbarPrimary = Color(0xFFFFB300)

val EsquemaAmbarCores = darkColorScheme(
    primary = AmbarPrimary,
    onPrimary = Color.Black,
    secondary = Color(0xFFFFCC66),
    background = AmbarBackground,
    onBackground = Color(0xFFFFF3D6),
    surface = AmbarSurface,
    onSurface = Color(0xFFFFF3D6),
    surfaceVariant = Color(0xFF3D3115),
)

// ---- Ciano ----
private val CianoBackground = Color(0xFF08191A)
private val CianoSurface = Color(0xFF10282A)
private val CianoPrimary = Color(0xFF2DD9D9)

val EsquemaCianoCores = darkColorScheme(
    primary = CianoPrimary,
    onPrimary = Color.Black,
    secondary = Color(0xFF7BEAEA),
    background = CianoBackground,
    onBackground = Color(0xFFD6FCFC),
    surface = CianoSurface,
    onSurface = Color(0xFFD6FCFC),
    surfaceVariant = Color(0xFF163A3C),
)

// ============================================================================
// Temas PREMIUM — paletas mais sofisticadas, com fundos quase pretos, contraste
// refinado e cor de destaque única (sem gradientes gritantes), inspiradas em
// apps de streaming de referência no mercado.
// ============================================================================

// ---- Noite Dourada (dourado/champagne, estilo elegante) ----
private val NoiteDouradaBackground = Color(0xFF0F0C08)
private val NoiteDouradaSurface = Color(0xFF1A140A)
private val NoiteDouradaPrimary = Color(0xFFD4AF6A)

val EsquemaNoiteDouradaCores = darkColorScheme(
    primary = NoiteDouradaPrimary,
    onPrimary = Color(0xFF241C0C),
    secondary = Color(0xFFEBD9AE),
    onSecondary = Color(0xFF241C0C),
    background = NoiteDouradaBackground,
    onBackground = Color(0xFFF3E7CE),
    surface = NoiteDouradaSurface,
    onSurface = Color(0xFFF3E7CE),
    surfaceVariant = Color(0xFF241E10),
    tertiary = Color(0xFFB08D4F),
)

// ---- Violeta Profundo (roxo escuro + acento metálico lilás) ----
private val VioletaProfundoBackground = Color(0xFF0C0A14)
private val VioletaProfundoSurface = Color(0xFF171129)
private val VioletaProfundoPrimary = Color(0xFFA78BFA)

val EsquemaVioletaProfundoCores = darkColorScheme(
    primary = VioletaProfundoPrimary,
    onPrimary = Color(0xFF1B1533),
    secondary = Color(0xFFC9BBFC),
    onSecondary = Color(0xFF1B1533),
    background = VioletaProfundoBackground,
    onBackground = Color(0xFFEDE7FE),
    surface = VioletaProfundoSurface,
    onSurface = Color(0xFFEDE7FE),
    surfaceVariant = Color(0xFF201938),
    tertiary = Color(0xFF7C6AC0),
)

// ---- Prata Minimal (preto profundo + prata, estilo minimalista) ----
private val PrataMinimalBackground = Color(0xFF0A0A0A)
private val PrataMinimalSurface = Color(0xFF161616)
private val PrataMinimalPrimary = Color(0xFFE8E8E8)

val EsquemaPrataMinimalCores = darkColorScheme(
    primary = PrataMinimalPrimary,
    onPrimary = Color(0xFF151515),
    secondary = Color(0xFFB8B8B8),
    onSecondary = Color(0xFF151515),
    background = PrataMinimalBackground,
    onBackground = Color(0xFFF5F5F5),
    surface = PrataMinimalSurface,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF232323),
    tertiary = Color(0xFF9A9A9A),
)

// ============================================================================
// Variantes CLARAS de cada tema de cor — mesma cor de destaque, fundo claro.
// Usadas quando o usuário liga o toggle de luminosidade (sol/lua) mantendo o
// tema de cor selecionado no menu.
// ============================================================================

val EsquemaRoxoDarkClaroCores = lightColorScheme(
    primary = Color(0xFF7B2FD1),
    onPrimary = Color.White,
    secondary = Color(0xFFA855E8),
    background = Color(0xFFFAF6FF),
    onBackground = Color(0xFF241635),
    surface = Color(0xFFF1E9FB),
    onSurface = Color(0xFF241635),
    surfaceVariant = Color(0xFFE6D9F7),
)

val EsquemaVerdeClaroCores = lightColorScheme(
    primary = Color(0xFF15883F),
    onPrimary = Color.White,
    secondary = Color(0xFF1DB954),
    background = Color(0xFFF3FBF5),
    onBackground = Color(0xFF0D2116),
    surface = Color(0xFFE6F5EA),
    onSurface = Color(0xFF0D2116),
    surfaceVariant = Color(0xFFD6EEDC),
)

val EsquemaAzulClaroCores = lightColorScheme(
    primary = Color(0xFF1E6FD9),
    onPrimary = Color.White,
    secondary = Color(0xFF4FA3FF),
    background = Color(0xFFF2F7FF),
    onBackground = Color(0xFF0E1E33),
    surface = Color(0xFFE4EFFE),
    onSurface = Color(0xFF0E1E33),
    surfaceVariant = Color(0xFFD3E4FB),
)

val EsquemaSunsetClaroCores = lightColorScheme(
    primary = Color(0xFFE05334),
    onPrimary = Color.White,
    secondary = Color(0xFFFF6B4A),
    background = Color(0xFFFFF6F3),
    onBackground = Color(0xFF321A10),
    surface = Color(0xFFFEE9E2),
    onSurface = Color(0xFF321A10),
    surfaceVariant = Color(0xFFFAD9CC),
)

val EsquemaRosaClaroCores = lightColorScheme(
    primary = Color(0xFFE2378E),
    onPrimary = Color.White,
    secondary = Color(0xFFFF5DA2),
    background = Color(0xFFFFF4FA),
    onBackground = Color(0xFF33101F),
    surface = Color(0xFFFCE4F0),
    onSurface = Color(0xFF33101F),
    surfaceVariant = Color(0xFFF9D2E7),
)

val EsquemaAmbarClaroCores = lightColorScheme(
    primary = Color(0xFFB37D00),
    onPrimary = Color.White,
    secondary = Color(0xFFFFB300),
    background = Color(0xFFFFFAEF),
    onBackground = Color(0xFF2E2410),
    surface = Color(0xFFFCF0D6),
    onSurface = Color(0xFF2E2410),
    surfaceVariant = Color(0xFFF7E4B8),
)

val EsquemaCianoClaroCores = lightColorScheme(
    primary = Color(0xFF0D8A8A),
    onPrimary = Color.White,
    secondary = Color(0xFF2DD9D9),
    background = Color(0xFFEFFCFC),
    onBackground = Color(0xFF0A2323),
    surface = Color(0xFFDBF6F6),
    onSurface = Color(0xFF0A2323),
    surfaceVariant = Color(0xFFC3EDED),
)

val EsquemaNoiteDouradaClaroCores = lightColorScheme(
    primary = Color(0xFF9C7A2E),
    onPrimary = Color.White,
    secondary = Color(0xFFD4AF6A),
    background = Color(0xFFFDFAF3),
    onBackground = Color(0xFF2E2610),
    surface = Color(0xFFF6EDD8),
    onSurface = Color(0xFF2E2610),
    surfaceVariant = Color(0xFFEDE0BE),
)

val EsquemaVioletaProfundoClaroCores = lightColorScheme(
    primary = Color(0xFF6B4FCF),
    onPrimary = Color.White,
    secondary = Color(0xFFA78BFA),
    background = Color(0xFFF8F6FF),
    onBackground = Color(0xFF201A38),
    surface = Color(0xFFECE7FB),
    onSurface = Color(0xFF201A38),
    surfaceVariant = Color(0xFFDDD4F6),
)

val EsquemaPrataMinimalClaroCores = lightColorScheme(
    primary = Color(0xFF3A3A3A),
    onPrimary = Color.White,
    secondary = Color(0xFF6E6E6E),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF161616),
    surface = Color(0xFFEDEDED),
    onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFDCDCDC),
)

/** Monta a variante clara do esquema personalizado, preservando a cor de destaque escolhida. */
fun montarEsquemaPersonalizadoClaro(corPrimaria: Color): ColorScheme {
    val onPrimaria = if (corPrimaria.luminance() > 0.5f) Color.Black else Color.White
    return lightColorScheme(
        primary = corPrimaria,
        onPrimary = onPrimaria,
        secondary = corPrimaria.compositeSobre(Color.White, alpha = 0.75f),
        background = Color(0xFFFAFAFC),
        onBackground = Color(0xFF1A1A22),
        surface = Color(0xFFF0F0F5),
        onSurface = Color(0xFF1A1A22),
        surfaceVariant = Color(0xFFE4E4EC),
    )
}

/**
 * Monta um ColorScheme dinâmico a partir de uma cor de destaque escolhida livremente pelo
 * usuário no seletor de cor. Fundo fica sempre escuro (quase preto) para dar contraste com
 * qualquer matiz escolhida; a cor "on primary" é decidida pela luminância da cor primária
 * para garantir texto legível em cima do destaque.
 */
fun montarEsquemaPersonalizado(corPrimaria: Color): ColorScheme {
    val onPrimaria = if (corPrimaria.luminance() > 0.5f) Color.Black else Color.White
    return darkColorScheme(
        primary = corPrimaria,
        onPrimary = onPrimaria,
        secondary = corPrimaria.compositeSobre(Color(0xFF14141C), alpha = 0.75f),
        background = Color(0xFF0E0E14),
        onBackground = Color(0xFFF0F0F5),
        surface = Color(0xFF1A1A24),
        onSurface = Color(0xFFF0F0F5),
        surfaceVariant = Color(0xFF262631),
    )
}

/** Composita esta cor (com a [alpha] dada) sobre um [fundo] opaco, para derivar tons secundários. */
private fun Color.compositeSobre(fundo: Color, alpha: Float): Color {
    return Color(
        red = this.red * alpha + fundo.red * (1 - alpha),
        green = this.green * alpha + fundo.green * (1 - alpha),
        blue = this.blue * alpha + fundo.blue * (1 - alpha),
        alpha = 1f
    )
}
