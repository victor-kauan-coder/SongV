// Sem janela de console no Windows nas versões de lançamento.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    songv_desktop_lib::run()
}
