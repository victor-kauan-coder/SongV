//! Celulares pareados e a identidade deste computador.
//!
//! A lista (id + nome) fica em `pares.json` na pasta de configuração do app; a chave de cada par
//! vai para o cofre do sistema (Gerenciador de Credenciais no Windows, Keychain no macOS,
//! Secret Service no Linux) e nunca é gravada em arquivo.

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

const SERVICO: &str = "SongV";

#[derive(Clone, Serialize, Deserialize)]
pub struct Par {
    pub id: String,
    pub nome: String,
}

pub struct Pares {
    arquivo: PathBuf,
    pub lista: Vec<Par>,
}

fn cofre(id: &str) -> Result<keyring::Entry, String> {
    keyring::Entry::new(SERVICO, &format!("celular-{id}")).map_err(|e| e.to_string())
}

impl Pares {
    pub fn carregar(pasta: &PathBuf) -> Self {
        let arquivo = pasta.join("pares.json");
        let lista = std::fs::read(&arquivo).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default();
        Self { arquivo, lista }
    }

    fn salvar(&self) {
        if let Ok(b) = serde_json::to_vec_pretty(&self.lista) {
            let temporario = self.arquivo.with_extension("json.tmp");
            if std::fs::write(&temporario, b).is_ok() {
                let _ = std::fs::rename(&temporario, &self.arquivo);
            }
        }
    }

    pub fn chave(&self, id: &str) -> Option<Vec<u8>> {
        if !self.lista.iter().any(|p| p.id == id) {
            return None;
        }
        cofre(id).ok()?.get_secret().ok().filter(|k| k.len() == 32)
    }

    pub fn guardar(&mut self, par: Par, chave: &[u8]) -> Result<(), String> {
        cofre(&par.id)?.set_secret(chave).map_err(|e| format!("não consegui guardar a chave: {e}"))?;
        self.lista.retain(|p| p.id != par.id);
        self.lista.push(par);
        self.salvar();
        Ok(())
    }

    pub fn esquecer(&mut self, id: &str) {
        if let Ok(e) = cofre(id) {
            let _ = e.delete_credential();
        }
        self.lista.retain(|p| p.id != id);
        self.salvar();
    }
}

/// Id aleatório deste computador, criado na primeira execução.
pub fn identidade(pasta: &PathBuf) -> String {
    let arquivo = pasta.join("identidade");
    if let Ok(id) = std::fs::read_to_string(&arquivo) {
        let id = id.trim().to_string();
        if id.len() == 32 && id.chars().all(|c| c.is_ascii_hexdigit()) {
            return id;
        }
    }
    let id = crate::cripto::hex(&crate::cripto::aleatorio(16));
    let _ = std::fs::write(&arquivo, &id);
    id
}
