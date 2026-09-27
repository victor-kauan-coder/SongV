package com.songv.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.songv.app.data.EstiloVisual
import com.songv.app.data.EstiloVisual.ATUAL
import com.songv.app.data.EstiloVisual.FOSCO
import com.songv.app.data.EstiloVisual.METALICO
import com.songv.app.data.EstiloVisual.OPACO
import com.songv.app.data.EstiloVisual.VIDRO
import java.util.Random
import kotlin.math.max

/**
 * O acabamento das superfícies. A cor sólida de cada superfície continua vindo do ColorScheme;
 * o acabamento decide como ela é "fabricada":
 *
 * - **Atual**: grafite liso.
 * - **Opaco**: cor chapada, puxada para o destaque, sem brilho nem sombra.
 * - **Fosco**: acrílico jateado — translúcido, granulado, borda clara fina.
 * - **Metálico**: alumínio escovado — degradê vertical, riscos horizontais e bisel.
 * - **Vidro**: painel translúcido com reflexo e aresta brilhante sobre uma luz ambiente.
 *
 * Tudo é desenhado com `drawWithCache` e duas texturas pequenas geradas uma vez: nada de
 * imagens no APK e nada recalculado a cada quadro.
 */
@Immutable
data class Acabamento(val estilo: EstiloVisual, val escuro: Boolean, val sinal: Color, val fundo: Color) {
    /** Estilos que mostram o fundo por trás das superfícies. */
    val translucido: Boolean get() = estilo == VIDRO || estilo == FOSCO
}

val LocalAcabamento = staticCompositionLocalOf { Acabamento(ATUAL, true, Marca.Laranja, Marca.Grafite1) }

val acabamento: Acabamento
    @Composable @ReadOnlyComposable get() = LocalAcabamento.current

// ---- Texturas (geradas uma vez, 64 KB cada) ----

private fun textura(gerar: (x: Int, y: Int, r: Random) -> Float): ShaderBrush {
    val lado = 128
    val r = Random(7)
    val px = IntArray(lado * lado) { i -> android.graphics.Color.argb((gerar(i % lado, i / lado, r) * 255).toInt(), 255, 255, 255) }
    val bmp = Bitmap.createBitmap(px, lado, lado, Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(bmp, TileMode.Repeated, TileMode.Repeated))
}

/** Grão de acrílico jateado. */
private val granulado by lazy { textura { _, _, r -> r.nextFloat() } }

/** Riscos horizontais de metal escovado: cada linha tem um brilho, com um pouco de ruído. */
private val escovado by lazy {
    val linhas = Random(11).let { r -> FloatArray(128) { r.nextFloat() } }
    textura { _, y, r -> linhas[y] * 0.8f + r.nextFloat() * 0.2f }
}

/** A textura é branca; no tema claro vira preta, senão só clarearia o papel. */
private fun tintaTextura(escuro: Boolean) = if (escuro) null else ColorFilter.tint(Color.Black, BlendMode.SrcIn)

private fun girarMatiz(cor: Color, graus: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(cor.toArgb(), hsv)
    hsv[0] = (hsv[0] + graus + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// ---- Fundo das telas ----

/** Vidro e fosco pedem luz por trás — sem ela, um painel translúcido é só um cinza. */
fun Modifier.fundoDoApp(a: Acabamento): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val raio = max(w, h)
    val luzes = when (a.estilo) {
        VIDRO, FOSCO -> {
            val k = (if (a.estilo == VIDRO) 1f else 0.7f) * (if (a.escuro) 1f else 0.75f)
            listOf(
                Brush.radialGradient(listOf(a.sinal.copy(alpha = 0.32f * k), Color.Transparent), Offset(w * 0.08f, h * 0.06f), raio * 0.62f),
                Brush.radialGradient(listOf(girarMatiz(a.sinal, -35f).copy(alpha = 0.22f * k), Color.Transparent), Offset(w * 0.98f, h * 0.42f), raio * 0.55f),
                Brush.radialGradient(listOf(girarMatiz(a.sinal, -80f).copy(alpha = 0.2f * k), Color.Transparent), Offset(w * 0.15f, h * 0.92f), raio * 0.6f),
            )
        }
        METALICO -> listOf(
            Brush.verticalGradient(
                listOf(
                    lerp(a.fundo, Color.White, if (a.escuro) 0.06f else 0.5f),
                    a.fundo,
                    lerp(a.fundo, Color.Black, if (a.escuro) 0.35f else 0.05f),
                ),
            ),
        )
        ATUAL, OPACO -> emptyList()
    }
    val filtro = tintaTextura(a.escuro)
    onDrawBehind {
        drawRect(a.fundo)
        luzes.forEach { drawRect(it) }
        if (a.estilo == METALICO) drawRect(escovado, alpha = if (a.escuro) 0.035f else 0.03f, colorFilter = filtro)
    }
}

// ---- Superfícies ----

/**
 * Pinta uma superfície no acabamento escolhido. [cor] é a cor sólida do estilo Atual.
 * [sobreConteudo] vale para o que flutua sobre listas (mini player, barra de abas): ali o vidro
 * fica mais denso, senão o texto rolando por baixo brigaria com o de cima.
 */
fun Modifier.superficie(a: Acabamento, forma: Shape, cor: Color, sobreConteudo: Boolean = false): Modifier = drawWithCache {
    val contorno = forma.createOutline(size, layoutDirection, this)
    val linha = Stroke(1.dp.toPx())
    val branco = Color.White
    val e = a.escuro
    when (a.estilo) {
        ATUAL -> onDrawBehind { drawOutline(contorno, cor) }
        OPACO -> {
            val chapada = lerp(cor, a.sinal, if (e) 0.1f else 0.07f)
            onDrawBehind { drawOutline(contorno, chapada) }
        }
        FOSCO -> {
            val base = lerp(cor, branco, if (e) 0.05f else 0.35f).copy(alpha = if (sobreConteudo) 0.62f else if (e) 0.66f else 0.6f)
            val borda = branco.copy(alpha = if (e) 0.08f else 0.6f)
            val filtro = tintaTextura(e)
            onDrawBehind {
                drawOutline(contorno, base)
                drawOutline(contorno, granulado, alpha = if (e) 0.05f else 0.04f, colorFilter = filtro)
                drawOutline(contorno, borda, style = linha)
            }
        }
        METALICO -> {
            val metal = Brush.verticalGradient(
                listOf(lerp(cor, branco, if (e) 0.12f else 0.4f), cor, lerp(cor, Color.Black, if (e) 0.22f else 0.07f)),
            )
            val bisel = Brush.verticalGradient(
                listOf(branco.copy(alpha = if (e) 0.24f else 0.9f), Color.Transparent, Color.Black.copy(alpha = if (e) 0.45f else 0.12f)),
            )
            val filtro = tintaTextura(e)
            onDrawBehind {
                drawOutline(contorno, metal)
                drawOutline(contorno, escovado, alpha = if (e) 0.06f else 0.05f, colorFilter = filtro)
                drawOutline(contorno, bisel, style = linha)
            }
        }
        VIDRO -> {
            // Sobre listas, o desfoque (Haze, ver estiloDesfoque) já apaga o texto de trás.
            val base = cor.copy(alpha = if (sobreConteudo) 0.45f else if (e) 0.3f else 0.4f)
            val reflexo = Brush.linearGradient(
                listOf(branco.copy(alpha = if (e) 0.1f else 0.4f), Color.Transparent),
                start = Offset.Zero,
                end = Offset(size.width * 0.5f, size.height),
            )
            val aresta = Brush.linearGradient(
                listOf(branco.copy(alpha = if (e) 0.4f else 0.95f), branco.copy(alpha = 0.05f), branco.copy(alpha = if (e) 0.18f else 0.6f)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            onDrawBehind {
                drawOutline(contorno, base)
                drawOutline(contorno, reflexo)
                drawOutline(contorno, aresta, style = linha)
            }
        }
    }
}

/** Brilho por cima dos botões cheios (play, ação principal): anodizado no metálico, verniz no vidro. */
fun Modifier.realce(a: Acabamento, forma: Shape): Modifier = when (a.estilo) {
    ATUAL, OPACO -> this
    else -> drawWithCache {
        val contorno = forma.createOutline(size, layoutDirection, this)
        val brilho = when (a.estilo) {
            METALICO -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.16f)))
            VIDRO -> Brush.verticalGradient(0f to Color.White.copy(alpha = 0.34f), 0.5f to Color.White.copy(alpha = 0.06f), 0.51f to Color.Transparent)
            else -> null
        }
        onDrawWithContent {
            drawContent()
            brilho?.let { drawOutline(contorno, it) }
            if (a.estilo == FOSCO) drawOutline(contorno, granulado, alpha = 0.07f, colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn))
        }
    }
}

/**
 * Desfoque atrás das superfícies que flutuam sobre as listas. No Android 12L+ é desfoque de
 * verdade; antes disso o Haze só pinta um véu, então o véu fica mais denso.
 */
fun Acabamento.estiloDesfoque(cor: Color) = dev.chrisbanes.haze.HazeStyle(
    tint = cor.copy(alpha = if (android.os.Build.VERSION.SDK_INT >= 32) 0.3f else 0.8f),
    blurRadius = if (estilo == FOSCO) 30.dp else 22.dp,
    noiseFactor = if (estilo == FOSCO) 0.22f else 0f,
)

/** Cor das folhas (bottom sheets): ficam sobre o escurecimento, então só levemente translúcidas. */
fun Acabamento.corDeFolha(base: Color): Color = when (estilo) {
    ATUAL, METALICO -> base
    OPACO -> lerp(base, sinal, if (escuro) 0.06f else 0.05f)
    FOSCO -> lerp(base, Color.White, if (escuro) 0.04f else 0.3f).copy(alpha = 0.96f)
    VIDRO -> base.copy(alpha = 0.92f)
}

/** Sombra só onde há matéria sólida: sob vidro ou acrílico ela aparece através e suja o painel. */
fun Acabamento.sombra(padrao: androidx.compose.ui.unit.Dp) = if (translucido || estilo == OPACO) 0.dp else padrao
