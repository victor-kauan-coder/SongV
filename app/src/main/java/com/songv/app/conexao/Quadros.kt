package com.songv.app.conexao

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Quadros na conexão TCP: `u32` big-endian com o tamanho + conteúdo (igual a `quadros.rs`).
 * Claro só no aperto de mão (`0x00 ‖ JSON`); depois, `tipo ‖ carga` vai sempre cifrado.
 */
object Quadros {
    const val CLARO: Byte = 0
    const val JSON: Byte = 1
    const val AUDIO: Byte = 2
    const val CAPA: Byte = 3

    private const val MAXIMO = 8 * 1024 * 1024

    fun ler(entrada: DataInputStream): ByteArray {
        val tamanho = entrada.readInt()
        if (tamanho <= 0 || tamanho > MAXIMO) throw IOException("quadro com tamanho inválido ($tamanho)")
        return ByteArray(tamanho).also { entrada.readFully(it) }
    }

    fun escrever(saida: DataOutputStream, dados: ByteArray) {
        saida.writeInt(dados.size)
        saida.write(dados)
        saida.flush()
    }

    fun claro(o: JSONObject) = byteArrayOf(CLARO) + o.toString().toByteArray()

    fun json(o: JSONObject) = byteArrayOf(JSON) + o.toString().toByteArray()

    fun lerClaro(q: ByteArray): JSONObject {
        if (q.isEmpty() || q[0] != CLARO) throw IOException("esperava um quadro claro")
        return JSONObject(String(q, 1, q.size - 1))
    }
}
