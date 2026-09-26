package com.songv.app.model

/**
 * Representa uma linha de letra.
 * @param texto O texto da linha.
 * @param tempoMs Timestamp em milissegundos de onde a linha começa a tocar.
 *                Null quando a letra é apenas estática (veio de USLT, sem sincronização).
 */
data class LinhaLetra(
    val texto: String,
    val tempoMs: Long?
)

/** Tipo de letra disponível numa música, usado pra decidir como a UI deve exibir. */
enum class TipoLetra {
    SINCRONIZADA,  // veio do frame SYLT — dá pra destacar linha atual e navegar por toque
    SIMPLES,       // veio do frame USLT — só texto corrido, sem tempo
    AUSENTE        // não tinha nem SYLT nem USLT
}
