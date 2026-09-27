package com.songv.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.palette.graphics.Palette
import com.songv.app.id3.Id3Parser
import com.songv.app.model.Musica
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Capas dos álbuns, em três camadas:
 *
 * 1. **Arquivo em cache** (512 px, JPEG) — gerado uma vez por faixa a partir da imagem embutida.
 *    Serve às listas e à notificação/tela de bloqueio (via [uriArtwork]).
 * 2. **Memória** (LruCache limitado a ~1/8 da heap) — miniaturas já decodificadas.
 * 3. **Alta resolução** (até 1200 px) — decodificada direto da imagem original, só para a
 *    capa grande do player. Antes o app decodificava tudo a no máximo ~300 px e a capa do
 *    player aparecia borrada.
 *
 * Faixas sem capa ganham uma capa gerada na UI (ver `CapaArte`), nunca um ícone genérico.
 *
 * Capas escolhidas à mão ficam em `filesDir/capas_usuario` (não somem quando o sistema limpa o
 * cache) e têm prioridade sobre a embutida. O arquivo de música nunca é alterado.
 */
class CapaRepository(private val context: Context) {

    private val pasta = File(context.cacheDir, "capas").apply { mkdirs() }

    private val memoria = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val paletas = LruCache<String, Int>(64)
    private val gerando = Mutex()

    private val pastaUsuario = File(context.filesDir, "capas_usuario").apply { mkdirs() }
    private val indiceUsuario = File(pastaUsuario, "indice.json")
    private val editandoIndice = Mutex()
    private val _personalizadas = MutableStateFlow(lerIndice())

    /** id da faixa → nome do arquivo da capa escolhida à mão. */
    val personalizadas: StateFlow<Map<String, String>> = _personalizadas

    private fun personalizada(id: String): File? = _personalizadas.value[id]?.let { File(pastaUsuario, it) }

    fun temCapa(m: Musica) = m.temCapa || m.id in _personalizadas.value

    private fun arquivo(id: String, modificado: Long) = File(pasta, "${id}_$modificado.jpg")
    private fun arquivo(m: Musica) = arquivo(m.id, m.modificadoSeg)
    private fun marcadorSemCapa(id: String, modificado: Long) = File(pasta, "${id}_$modificado.none")
    private fun marcadorSemCapa(m: Musica) = marcadorSemCapa(m.id, m.modificadoSeg)
    private fun chave(m: Musica, grande: Boolean) =
        "${m.id}_${m.modificadoSeg}_${_personalizadas.value[m.id].orEmpty()}_${if (grande) "g" else "m"}"

    /**
     * Artwork da MediaSession (notificação, tela de bloqueio, Bluetooth, Android Auto). É um
     * content:// do [CapaProvider]: a interface do sistema roda em outro processo e não consegue
     * abrir arquivos privados do app.
     */
    fun uriArtwork(m: Musica): Uri? {
        val propria = _personalizadas.value[m.id]
        if (!m.temCapa && propria == null) return null
        // O nome da capa própria entra na URI para o sistema não reaproveitar a imagem antiga em cache.
        val sufixo = propria?.let { "/" + it.substringBefore('.') }.orEmpty()
        return Uri.parse("content://${CapaProvider.AUTORIDADE}/capa/${m.id}/${m.modificadoSeg}$sufixo")
    }

    fun emMemoria(m: Musica, grande: Boolean): Bitmap? = memoria.get(chave(m, grande))

    fun semCapaConhecida(m: Musica) = m.id !in _personalizadas.value && (!m.temCapa || marcadorSemCapa(m).exists())

    suspend fun carregar(m: Musica, grande: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val k = chave(m, grande)
        memoria.get(k)?.let { return@withContext it }
        if (semCapaConhecida(m)) return@withContext null
        val propria = personalizada(m.id)
        val bitmap = if (propria != null) {
            decodificarArquivo(propria, if (grande) 1200 else 256)
        } else if (grande) {
            bytesOriginais(m.caminho)?.let { decodificar(it, 1200) } ?: garantirArquivo(m)?.let { decodificarArquivo(it, 512) }
        } else {
            garantirArquivo(m)?.let { decodificarArquivo(it, 256) }
        }
        bitmap?.also { memoria.put(k, it) }
    }

    /** Gera o arquivo de 512 px se ainda não existe. Devolve null se a faixa não tem capa. */
    suspend fun garantirArquivo(m: Musica): File? =
        if (!temCapa(m)) null else garantirArquivo(m.id, m.modificadoSeg, m.caminho)

    suspend fun garantirArquivo(id: String, modificado: Long, caminho: String): File? = withContext(Dispatchers.IO) {
        personalizada(id)?.takeIf { it.exists() }?.let { return@withContext it }
        val destino = arquivo(id, modificado)
        if (destino.exists()) return@withContext destino
        if (marcadorSemCapa(id, modificado).exists()) return@withContext null
        gerando.withLock {
            if (destino.exists()) return@withLock destino
            val bitmap = bytesOriginais(caminho)?.let { decodificar(it, 512) }
            if (bitmap == null) {
                runCatching { marcadorSemCapa(id, modificado).createNewFile() }
                return@withLock null
            }
            runCatching {
                val temporario = File(pasta, destino.name + ".tmp")
                temporario.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                temporario.renameTo(destino)
            }
            destino.takeIf { it.exists() }
        }
    }

    /** Pré-gera as capas da biblioteca em segundo plano, para listas e notificação ficarem instantâneas. */
    suspend fun preparar(musicas: List<Musica>) {
        for (m in musicas) {
            if (!arquivo(m).exists() && !semCapaConhecida(m)) garantirArquivo(m)
        }
    }

    /** Cor de fundo do player, extraída da capa (escura e com contraste garantido na UI). */
    suspend fun corDominante(m: Musica): Int? = withContext(Dispatchers.Default) {
        val k = chave(m, false)
        paletas.get(k)?.let { return@withContext it }
        val bitmap = carregar(m, grande = false) ?: return@withContext null
        val paleta = Palette.from(bitmap).maximumColorCount(16).generate()
        val cor = (paleta.darkVibrantSwatch ?: paleta.vibrantSwatch ?: paleta.darkMutedSwatch ?: paleta.dominantSwatch)?.rgb
        cor?.also { paletas.put(k, it) }
    }

    // ---- Capas escolhidas à mão ----

    /**
     * Usa [imagem] (seletor de fotos) como capa das faixas [ids]: corrige a rotação da câmera,
     * recorta o centro em quadrado e guarda um JPEG de até 1200 px. Um arquivo só serve a todas
     * as faixas — um álbum inteiro não ocupa espaço repetido.
     */
    suspend fun definirPersonalizada(ids: List<String>, imagem: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(imagem)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext false
        val bitmap = decodificar(bytes, 1200)?.let { endireitar(it, bytes) }?.let(::recortarQuadrado) ?: return@withContext false
        val nome = "c_${System.currentTimeMillis()}.jpg"
        val ok = runCatching {
            val temporario = File(pastaUsuario, "$nome.tmp")
            temporario.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            temporario.renameTo(File(pastaUsuario, nome))
        }.getOrDefault(false)
        if (ok) salvarIndice { it + ids.associateWith { nome } }
        ok
    }

    suspend fun removerPersonalizada(ids: List<String>) = withContext(Dispatchers.IO) {
        salvarIndice { it - ids.toSet() }
    }

    private suspend fun salvarIndice(mudar: (Map<String, String>) -> Map<String, String>) = editandoIndice.withLock {
        val novo = mudar(_personalizadas.value)
        val temporario = File(pastaUsuario, "indice.json.tmp")
        temporario.writeText(JSONObject(novo as Map<*, *>).toString())
        temporario.renameTo(indiceUsuario)
        _personalizadas.value = novo
        // Apaga imagens que nenhuma faixa usa mais.
        val usadas = novo.values.toSet()
        pastaUsuario.listFiles { f -> f.name.startsWith("c_") && f.name !in usadas }?.forEach { it.delete() }
    }

    private fun lerIndice(): Map<String, String> = runCatching {
        val o = JSONObject(indiceUsuario.readText())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun endireitar(b: Bitmap, bytes: ByteArray): Bitmap {
        val graus = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (graus == 0f) return b
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(graus) }, true)
    }

    private fun recortarQuadrado(b: Bitmap): Bitmap {
        if (b.width == b.height) return b
        val lado = minOf(b.width, b.height)
        return Bitmap.createBitmap(b, (b.width - lado) / 2, (b.height - lado) / 2, lado, lado)
    }

    private fun bytesOriginais(caminho: String): ByteArray? {
        if (caminho.endsWith(".mp3", ignoreCase = true)) Id3Parser.lerCapa(caminho)?.let { return it }
        // Reserva para M4A/FLAC/OGG e para tags que o parser não entende.
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(caminho)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodificar(bytes: ByteArray, alvoPx: Int): Bitmap? = runCatching {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        val opcoes = BitmapFactory.Options().apply { inSampleSize = amostragem(medidas.outWidth, medidas.outHeight, alvoPx) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)?.let { reduzir(it, alvoPx) }
    }.getOrNull()

    private fun decodificarArquivo(f: File, alvoPx: Int): Bitmap? = runCatching {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, medidas)
        val opcoes = BitmapFactory.Options().apply { inSampleSize = amostragem(medidas.outWidth, medidas.outHeight, alvoPx) }
        BitmapFactory.decodeFile(f.path, opcoes)?.let { reduzir(it, alvoPx) }
    }.getOrNull()

    /** Maior potência de 2 que ainda deixa a imagem com pelo menos [alvo] px no menor lado. */
    private fun amostragem(largura: Int, altura: Int, alvo: Int): Int {
        var n = 1
        while (minOf(largura, altura) / (n * 2) >= alvo) n *= 2
        return n
    }

    /** Ajuste fino depois do inSampleSize, para não guardar bitmaps maiores que o necessário. */
    private fun reduzir(b: Bitmap, alvoPx: Int): Bitmap {
        val menor = minOf(b.width, b.height)
        if (menor <= alvoPx * 1.2f) return b
        val escala = alvoPx.toFloat() / menor
        return Bitmap.createScaledBitmap(b, (b.width * escala).toInt(), (b.height * escala).toInt(), true)
    }
}
