package com.songv.app.model

/**
 * Uma linha de letra. [tempoMs] é null em letra sem sincronia. Texto vazio numa letra
 * sincronizada marca um trecho instrumental (a tela mostra um indicador de pausa).
 */
data class LinhaLetra(
    val texto: String,
    val tempoMs: Long?,
)

enum class TipoLetra { SINCRONIZADA, SIMPLES, AUSENTE }

/** De onde a letra exibida veio — mostrado discretamente na tela de letra. */
enum class FonteLetra(val rotulo: String) {
    EMBUTIDA("Embutida no arquivo"),
    ARQUIVO_LRC("Arquivo .lrc"),
    ONLINE("LRCLIB"),
    IMPORTADA("Importada"),
}

data class Letra(
    val linhas: List<LinhaLetra>,
    val tipo: TipoLetra,
    val fonte: FonteLetra?,
) {
    companion object {
        val AUSENTE = Letra(emptyList(), TipoLetra.AUSENTE, null)
    }
}
