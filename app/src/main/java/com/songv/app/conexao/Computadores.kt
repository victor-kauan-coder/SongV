package com.songv.app.conexao

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Um computador com o SongV: achado na rede (mDNS), digitado à mão ou já pareado. */
data class Computador(
    val id: String,
    val nome: String,
    val host: String,
    val porta: Int,
    val pareado: Boolean = false,
)

/**
 * Computadores pareados e a identidade deste celular.
 *
 * A chave de cada par é cifrada com uma chave AES que mora no **Android Keystore** (não sai do
 * hardware seguro); nas preferências fica só o texto cifrado.
 */
class Computadores(private val context: Context) {

    private val prefs = context.getSharedPreferences("computadores", Context.MODE_PRIVATE)
    private val _pareados = MutableStateFlow(ler())
    val pareados: StateFlow<List<Computador>> = _pareados

    val meuId: String = prefs.getString("meu_id", null) ?: Cripto.hex(Cripto.aleatorio(16)).also {
        prefs.edit().putString("meu_id", it).apply()
    }

    /** O nome que a pessoa deu ao aparelho ("Galaxy do Victor"), não o código do modelo. */
    val meuNome: String
        get() = listOf(
            { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) },
            { Settings.Secure.getString(context.contentResolver, "bluetooth_name") },
        ).firstNotNullOfOrNull { ler -> runCatching(ler).getOrNull()?.takeIf { it.isNotBlank() } }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    private fun lista(): JSONArray = runCatching { JSONArray(prefs.getString("lista", "[]")) }.getOrDefault(JSONArray())

    private fun ler(): List<Computador> {
        val a = lista()
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Computador(o.getString("id"), o.getString("nome"), o.getString("host"), o.getInt("porta"), pareado = true)
        }
    }

    private fun gravar(a: JSONArray) {
        prefs.edit().putString("lista", a.toString()).apply()
        _pareados.value = ler()
    }

    fun chave(id: String): ByteArray? {
        val a = lista()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getString("id") == id) return runCatching { decifrar(o.getString("chave")) }.getOrNull()
        }
        return null
    }

    /** Pareado com o mesmo endereço (conexão manual a um computador já conhecido). */
    fun porEndereco(host: String, porta: Int) = _pareados.value.firstOrNull { it.host == host && it.porta == porta }

    fun guardar(c: Computador, chave: ByteArray) {
        val a = JSONArray()
        val atual = lista()
        for (i in 0 until atual.length()) {
            val o = atual.getJSONObject(i)
            if (o.getString("id") != c.id) a.put(o)
        }
        a.put(JSONObject().put("id", c.id).put("nome", c.nome).put("host", c.host).put("porta", c.porta).put("chave", cifrar(chave)))
        gravar(a)
    }

    /** O computador mudou de IP (DHCP) ou de nome: atualiza sem mexer na chave. */
    fun atualizar(id: String, nome: String, host: String, porta: Int) {
        val a = lista()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getString("id") == id) o.put("nome", nome).put("host", host).put("porta", porta)
        }
        gravar(a)
    }

    fun marcarUltimo(id: String) = prefs.edit().putString("ultimo", id).apply()

    fun ultimo(): Computador? = prefs.getString("ultimo", null)?.let { id -> _pareados.value.firstOrNull { it.id == id } }

    fun esquecer(id: String) {
        val a = JSONArray()
        val atual = lista()
        for (i in 0 until atual.length()) {
            val o = atual.getJSONObject(i)
            if (o.getString("id") != id) a.put(o)
        }
        gravar(a)
    }

    // ---- Keystore ----

    private fun chaveMestra(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun cifrar(claro: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, chaveMestra()) }
        return Base64.encodeToString(c.iv + c.doFinal(claro), Base64.NO_WRAP)
    }

    private fun decifrar(texto: String): ByteArray {
        val b = Base64.decode(texto, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, chaveMestra(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        }
        return c.doFinal(b, 12, b.size - 12)
    }

    private companion object {
        const val ALIAS = "songv_computadores"
    }
}
