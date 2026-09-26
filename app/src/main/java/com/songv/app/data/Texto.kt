package com.songv.app.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

private val MARCAS_DIACRITICAS = Regex("\\p{Mn}+")

/** Minúsculas e sem acentos: "Você" e "voce" viram a mesma coisa na busca. */
fun normalizarBusca(texto: String): String =
    MARCAS_DIACRITICAS.replace(Normalizer.normalize(texto, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

/** Ordenação alfabética que ignora acentos e maiúsculas ("Ávila" fica junto de "Avião"). */
val comparadorAlfabetico: Comparator<String> = Collator.getInstance(Locale("pt", "BR")).apply {
    strength = Collator.PRIMARY
}.let { collator -> Comparator { a, b -> collator.compare(a, b) } }
