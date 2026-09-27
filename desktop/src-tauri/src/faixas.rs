//! Os bytes das faixas, vindos do celular, em memória.
//!
//! Só existem duas faixas por vez: a que está tocando e a próxima. O arquivo chega em blocos de
//! 1 MB (dois pedidos em voo) e o `<audio>` do WebView lê por `songv://audio/<id>` com Range —
//! quando pede um trecho que ainda não chegou, a resposta espera por ele.

use serde::Deserialize;
use std::collections::{HashMap, VecDeque};
use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::sync::Notify;

const BLOCO: u64 = 1024 * 1024;
const EM_VOO: u64 = 2;
/// Faixas maiores que isso (um FLAC de uma hora) não cabem no orçamento de memória.
const MAXIMO: u64 = 400 * 1024 * 1024;

#[derive(Clone, Deserialize, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Meta {
    pub id: String,
    #[serde(default)]
    pub titulo: String,
    #[serde(default)]
    pub artista: String,
    #[serde(default)]
    pub album: String,
    #[serde(default)]
    pub duracao_ms: u64,
    pub tamanho: u64,
    #[serde(default)]
    pub formato: String,
}

pub struct Faixa {
    pub id: String,
    pub tamanho: u64,
    pub mime: &'static str,
    dados: Mutex<Vec<u8>>,
    pronto_ate: AtomicU64,
    aviso: Notify,
    cancelada: AtomicBool,
    baixando: AtomicBool,
}

impl Faixa {
    fn nova(m: &Meta) -> Option<Arc<Self>> {
        if m.tamanho == 0 || m.tamanho > MAXIMO {
            return None;
        }
        Some(Arc::new(Self {
            id: m.id.clone(),
            tamanho: m.tamanho,
            mime: mime(&m.formato),
            dados: Mutex::new(vec![0u8; m.tamanho as usize]),
            pronto_ate: AtomicU64::new(0),
            aviso: Notify::new(),
            cancelada: AtomicBool::new(false),
            baixando: AtomicBool::new(false),
        }))
    }

    pub fn pronto_ate(&self) -> u64 {
        self.pronto_ate.load(Ordering::Acquire)
    }

    /// Espera até os bytes `[0, ate)` chegarem. Falso se demorar demais ou a faixa sair de cena.
    pub async fn esperar(&self, ate: u64) -> bool {
        let ate = ate.min(self.tamanho);
        loop {
            let aviso = self.aviso.notified();
            if self.pronto_ate() >= ate {
                return true;
            }
            if self.cancelada.load(Ordering::Relaxed) {
                return false;
            }
            if tokio::time::timeout(Duration::from_secs(20), aviso).await.is_err() {
                return false;
            }
        }
    }

    pub fn trecho(&self, de: u64, ate: u64) -> Vec<u8> {
        let d = self.dados.lock().unwrap();
        d[de as usize..ate.min(self.tamanho) as usize].to_vec()
    }

    fn receber(&self, inicio: u64, bytes: &[u8]) {
        let fim = inicio + bytes.len() as u64;
        if fim > self.tamanho {
            return;
        }
        self.dados.lock().unwrap()[inicio as usize..fim as usize].copy_from_slice(bytes);
        // Os blocos chegam em ordem (uma conexão TCP, o celular responde um pedido de cada vez).
        let atual = self.pronto_ate();
        if inicio <= atual && fim > atual {
            self.pronto_ate.store(fim, Ordering::Release);
        }
        self.aviso.notify_waiters();
    }
}

fn mime(formato: &str) -> &'static str {
    match formato.to_ascii_lowercase().as_str() {
        "flac" => "audio/flac",
        "m4a" | "mp4" | "aac" | "alac" => "audio/mp4",
        "ogg" | "oga" | "opus" => "audio/ogg",
        "wav" => "audio/wav",
        _ => "audio/mpeg",
    }
}

#[derive(Default)]
pub struct Faixas {
    mapa: Mutex<HashMap<String, Arc<Faixa>>>,
    pedidos: Mutex<HashMap<u32, Arc<Faixa>>>,
    proximo_pedido: AtomicU32,
    /// Capas vindas do celular (faixa tocando e as da biblioteca navegada): as 200 mais recentes.
    capas: Mutex<VecDeque<(String, Arc<Vec<u8>>)>>,
}

impl Faixas {
    pub fn obter(&self, id: &str) -> Option<Arc<Faixa>> {
        self.mapa.lock().unwrap().get(id).cloned()
    }

    /// Mantém só a faixa atual e a próxima; devolve as que precisam começar a baixar, em ordem.
    pub fn preparar(&self, atual: &Meta, proxima: Option<&Meta>) -> Vec<Arc<Faixa>> {
        let mut mapa = self.mapa.lock().unwrap();
        let manter: Vec<&str> = std::iter::once(atual.id.as_str()).chain(proxima.map(|p| p.id.as_str())).collect();
        mapa.retain(|id, f| {
            let fica = manter.contains(&id.as_str());
            if !fica {
                f.cancelada.store(true, Ordering::Relaxed);
                f.aviso.notify_waiters();
            }
            fica
        });
        self.pedidos.lock().unwrap().retain(|_, f| !f.cancelada.load(Ordering::Relaxed));
        let mut novas = Vec::new();
        for m in std::iter::once(atual).chain(proxima) {
            if !mapa.contains_key(&m.id) {
                if let Some(f) = Faixa::nova(m) {
                    mapa.insert(m.id.clone(), f);
                }
            }
            if let Some(f) = mapa.get(&m.id) {
                if !f.baixando.swap(true, Ordering::AcqRel) {
                    novas.push(f.clone());
                }
            }
        }
        novas
    }

    pub fn limpar(&self) {
        for f in self.mapa.lock().unwrap().drain().map(|(_, f)| f) {
            f.cancelada.store(true, Ordering::Relaxed);
            f.aviso.notify_waiters();
        }
        self.pedidos.lock().unwrap().clear();
    }

    pub fn limpar_capas(&self) {
        self.capas.lock().unwrap().clear();
    }

    /// Baixa a faixa inteira, pedindo blocos pelo `enviar` (que manda `ler` ao celular).
    pub async fn baixar(&self, f: &Arc<Faixa>, enviar: impl Fn(serde_json::Value) -> bool) -> bool {
        let mut pedido_ate = 0u64;
        while pedido_ate < f.tamanho {
            if f.cancelada.load(Ordering::Relaxed) {
                return false;
            }
            // No máximo EM_VOO blocos pedidos e ainda não recebidos.
            if pedido_ate >= f.pronto_ate() + EM_VOO * BLOCO {
                if !f.esperar(pedido_ate - (EM_VOO - 1) * BLOCO).await {
                    return false;
                }
                continue;
            }
            let tamanho = BLOCO.min(f.tamanho - pedido_ate);
            let pedido = self.proximo_pedido.fetch_add(1, Ordering::Relaxed);
            self.pedidos.lock().unwrap().insert(pedido, f.clone());
            let ok = enviar(serde_json::json!({
                "t": "ler", "pedido": pedido, "faixa": f.id, "de": pedido_ate, "tamanho": tamanho,
            }));
            if !ok {
                return false;
            }
            pedido_ate += tamanho;
        }
        f.esperar(f.tamanho).await
    }

    /// Bloco de áudio: `u32 pedido ‖ u64 início ‖ bytes`.
    pub fn receber_audio(&self, carga: &[u8]) {
        if carga.len() < 12 {
            return;
        }
        let pedido = u32::from_be_bytes(carga[0..4].try_into().unwrap());
        let inicio = u64::from_be_bytes(carga[4..12].try_into().unwrap());
        let faixa = self.pedidos.lock().unwrap().get(&pedido).cloned();
        if let Some(f) = faixa {
            f.receber(inicio, &carga[12..]);
        }
    }

    /// Capa: `u16 tamanho do id ‖ id ‖ imagem`. Devolve o id.
    pub fn receber_capa(&self, carga: &[u8]) -> Option<String> {
        let n = u16::from_be_bytes(carga.get(0..2)?.try_into().ok()?) as usize;
        let id = String::from_utf8(carga.get(2..2 + n)?.to_vec()).ok()?;
        let mut capas = self.capas.lock().unwrap();
        capas.retain(|(i, _)| *i != id);
        capas.push_back((id.clone(), Arc::new(carga[2 + n..].to_vec())));
        while capas.len() > 200 {
            capas.pop_front();
        }
        Some(id)
    }

    pub fn capa(&self, id: &str) -> Option<Arc<Vec<u8>>> {
        self.capas.lock().unwrap().iter().find(|(i, _)| i == id).map(|(_, c)| c.clone())
    }
}

#[cfg(test)]
mod testes {
    use super::*;

    fn meta(id: &str, tamanho: u64) -> Meta {
        Meta { id: id.into(), titulo: String::new(), artista: String::new(), album: String::new(), duracao_ms: 0, tamanho, formato: "mp3".into() }
    }

    #[tokio::test]
    async fn baixa_em_blocos_e_libera_quem_espera() {
        let faixas = Arc::new(Faixas::default());
        let tamanho = 3 * BLOCO + 10;
        let novas = faixas.preparar(&meta("a", tamanho), None);
        assert_eq!(novas.len(), 1);
        let f = novas[0].clone();
        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel();
        let fs = faixas.clone();
        let ff = f.clone();
        let tarefa = tokio::spawn(async move { fs.baixar(&ff, |v| tx.send(v).is_ok()).await });
        // "Celular": responde cada pedido com bytes = posição % 251.
        while let Some(p) = rx.recv().await {
            let de = p["de"].as_u64().unwrap();
            let n = p["tamanho"].as_u64().unwrap();
            let mut carga = (p["pedido"].as_u64().unwrap() as u32).to_be_bytes().to_vec();
            carga.extend_from_slice(&de.to_be_bytes());
            carga.extend((de..de + n).map(|i| (i % 251) as u8));
            faixas.receber_audio(&carga);
            if de + n >= tamanho {
                break;
            }
        }
        assert!(tarefa.await.unwrap());
        assert_eq!(f.pronto_ate(), tamanho);
        assert_eq!(f.trecho(BLOCO, BLOCO + 3), vec![(BLOCO % 251) as u8, ((BLOCO + 1) % 251) as u8, ((BLOCO + 2) % 251) as u8]);
    }

    #[test]
    fn so_guarda_atual_e_proxima() {
        let faixas = Faixas::default();
        faixas.preparar(&meta("a", 10), Some(&meta("b", 10)));
        faixas.preparar(&meta("b", 10), Some(&meta("c", 10)));
        assert!(faixas.obter("a").is_none());
        assert!(faixas.obter("b").is_some() && faixas.obter("c").is_some());
        // "b" já estava baixando: não volta a ser pedida.
        assert_eq!(faixas.preparar(&meta("b", 10), Some(&meta("c", 10))).len(), 0);
    }
}
