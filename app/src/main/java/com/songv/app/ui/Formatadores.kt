package com.songv.app.ui

import java.util.Locale

/** Formata milissegundos como "m:ss", padrão usado em players de música. */
fun formatarTempo(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSegundos = ms / 1000
    val minutos = totalSegundos / 60
    val segundos = totalSegundos % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutos, segundos)
}
