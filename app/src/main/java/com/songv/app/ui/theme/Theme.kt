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

@Composable
fun SongVTheme(
    modo: ModoTema = ModoTema.ESCURO,
    destaque: Color = Marca.Laranja,
    content: @Composable () -> Unit,
) {
    val escuro = when (modo) {
        ModoTema.ESCURO -> true
        ModoTema.CLARO -> false
        ModoTema.SISTEMA -> isSystemInDarkTheme()
    }
    val esquema = remember(destaque, escuro) { if (escuro) esquemaEscuro(destaque) else esquemaClaro(destaque) }
    val cores = remember(destaque, escuro) { coresSongV(destaque, escuro) }
    CompositionLocalProvider(LocalCoresSongV provides cores) {
        MaterialTheme(colorScheme = esquema, typography = TipografiaSongV, shapes = FormasSongV, content = content)
    }
}
