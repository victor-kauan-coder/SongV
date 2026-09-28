package com.songv.app.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.songv.app.conexao.ComputadorRemoto
import java.io.IOException

/**
 * Lê uma faixa do computador (`songvpc://faixa/<id>`) em blocos pedidos pela conexão cifrada.
 * O ExoPlayer chama [read] na thread de carga, então esperar a rede aqui é o esperado.
 */
@OptIn(UnstableApi::class)
class FonteDoComputador(private val remoto: ComputadorRemoto) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var id = ""
    private var total = 0L
    private var pos = 0L
    private var fim = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        id = dataSpec.uri.lastPathSegment ?: throw IOException("Endereço de faixa inválido")
        total = remoto.tamanho(id) ?: throw IOException("A faixa não está mais disponível no computador")
        pos = dataSpec.position
        if (pos > total) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        fim = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(total, pos + dataSpec.length) else total
        uri = dataSpec.uri
        transferStarted(dataSpec)
        return fim - pos
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (pos >= fim) return C.RESULT_END_OF_INPUT
        val indice = pos / ComputadorRemoto.BLOCO
        val bloco = remoto.bloco(id, indice, total)
        val dentro = (pos - indice * ComputadorRemoto.BLOCO).toInt()
        val n = minOf(length.toLong(), fim - pos, (bloco.size - dentro).toLong()).toInt()
        System.arraycopy(bloco, dentro, buffer, offset, n)
        pos += n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (uri != null) {
            uri = null
            transferEnded()
        }
    }

    /** Arquivos do aparelho pelo caminho de sempre; `songvpc://` pelo computador. */
    class Fabrica(context: Context, private val remoto: ComputadorRemoto) : DataSource.Factory {
        private val padrao = DefaultDataSource.Factory(context)
        override fun createDataSource(): DataSource = Roteadora(padrao.createDataSource(), FonteDoComputador(remoto))
    }

    private class Roteadora(private val padrao: DataSource, private val pc: DataSource) : DataSource {
        private var atual: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            padrao.addTransferListener(transferListener)
            pc.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val fonte = if (dataSpec.uri.scheme == ComputadorRemoto.ESQUEMA) pc else padrao
            atual = fonte
            return fonte.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int) = atual?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        override fun getUri(): Uri? = atual?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = atual?.responseHeaders ?: emptyMap()

        override fun close() {
            try {
                atual?.close()
            } finally {
                atual = null
            }
        }
    }
}
