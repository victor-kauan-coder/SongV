package com.songv.app.data

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.songv.app.SongVApp
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Entrega as capas (JPEG de 512 px) para quem está fora do app: notificação de mídia, tela de
 * bloqueio, controles de mídia do Android 13+, Bluetooth e Android Auto. Somente leitura e só
 * capas — nenhum outro arquivo é exposto.
 *
 * URI: content://com.songv.app.capas/capa/<id do MediaStore>/<data de modificação>[/<capa própria>]
 */
class CapaProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("As capas são somente leitura")
        val partes = uri.pathSegments
        // O 4º segmento (opcional) só identifica a versão de uma capa escolhida à mão.
        if (partes.size !in 3..4 || partes[0] != "capa") throw FileNotFoundException(uri.toString())
        val id = partes[1].toLongOrNull() ?: throw FileNotFoundException(uri.toString())
        val modificado = partes[2].toLongOrNull() ?: throw FileNotFoundException(uri.toString())
        val app = context?.applicationContext as? SongVApp ?: throw FileNotFoundException(uri.toString())

        val caminho = app.contentResolver.query(
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
            arrayOf(MediaStore.Audio.Media.DATA), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: throw FileNotFoundException(uri.toString())

        val arquivo = runBlocking { app.capas.garantirArquivo(id.toString(), modificado, caminho) }
            ?: throw FileNotFoundException("Faixa sem capa")
        return ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0

    companion object {
        const val AUTORIDADE = "com.songv.app.capas"
    }
}
