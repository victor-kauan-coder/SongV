package com.songv.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.songv.app.data.Destaque
import com.songv.app.data.EstiloVisual
import com.songv.app.data.FundoTema
import com.songv.app.data.ModoTema

/** Cantos contidos, de equipamento — nada de pílulas em tudo. */
val FormasSongV = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

fun corDoDestaque(destaque: Destaque, personalizada: Int?): Color =
    if (destaque == Destaque.PERSONALIZADO && personalizada != null) Color(personalizada) else Color(destaque.argb)

/** Cor de fundo para o tema atual; a cor livre é levada para uma faixa em que o texto continua legível. */
fun corDoFundo(fundo: FundoTema, personalizada: Int?, escuro: Boolean): Color =
    if (fundo == FundoTema.PERSONALIZADO && personalizada != null) {
        ajustarFundo(Color(personalizada), escuro)
    } else {
        Color(if (escuro) fundo.escuro else fundo.claro)
    }

@Composable
fun SongVTheme(
    modo: ModoTema = ModoTema.ESCURO,
    destaque: Color = Marca.Laranja,
    fundo: FundoTema = FundoTema.GRAFITE,
    corFundo: Int? = null,
    estilo: EstiloVisual = EstiloVisual.ATUAL,
    content: @Composable () -> Unit,
) {
    val escuro = when (modo) {
        ModoTema.ESCURO -> true
        ModoTema.CLARO -> false
        ModoTema.SISTEMA -> isSystemInDarkTheme()
    }
    val base = remember(fundo, corFundo, escuro) { corDoFundo(fundo, corFundo, escuro) }
    val esquema = remember(destaque, escuro, base) { if (escuro) esquemaEscuro(destaque, base) else esquemaClaro(destaque, base) }
    val cores = remember(destaque, escuro) { coresSongV(destaque, escuro) }
    val acabamento = remember(estilo, escuro, destaque, base) { Acabamento(estilo, escuro, destaque, base) }
    CompositionLocalProvider(LocalCoresSongV provides cores, LocalAcabamento provides acabamento) {
        MaterialTheme(colorScheme = esquema, typography = TipografiaSongV, shapes = FormasSongV, content = content)
    }
}
