package com.songv.app

import android.app.Application
import com.songv.app.conexao.ConexaoComputador
import com.songv.app.data.CapaRepository
import com.songv.app.data.MusicaRepository
import com.songv.app.data.PreferenciasRepository
import com.songv.app.letra.LetraRepository
import com.songv.app.letra.TradutorLetra
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Guarda os repositórios (um de cada por processo) e um escopo para gravações que não podem ser canceladas com a tela. */
class SongVApp : Application() {
    val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferencias by lazy { PreferenciasRepository(this) }
    val musicas by lazy { MusicaRepository(this) }
    val capas by lazy { CapaRepository(this) }
    val letras by lazy { LetraRepository(this) }
    val tradutor by lazy { TradutorLetra(this) }
    val computador by lazy { ConexaoComputador(this) }
}
