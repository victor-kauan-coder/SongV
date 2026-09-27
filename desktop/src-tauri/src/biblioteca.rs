//! A biblioteca do computador: varre a pasta de músicas, lê as tags e guarda um índice em disco
//! para a próxima abertura só reler o que mudou (tamanho ou data de modificação).

use lofty::config::ParseOptions;
use lofty::picture::PictureType;
use lofty::prelude::*;
use lofty::probe::Probe;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::{HashMap, VecDeque};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::UNIX_EPOCH;

const EXTENSOES: [&str; 9] = ["mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma"];
/// Toques e áudios de mensagem ficam de fora, como no app.
const MINIMO_MS: u64 = 30_000;

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Faixa {
    pub id: String,
    #[serde(skip_serializing, default)]
    pub caminho: String,
    pub titulo: String,
    pub artista: String,
    pub album: String,
    pub artista_album: String,
    pub ano: Option<u32>,
    pub faixa: Option<u32>,
    pub disco: Option<u32>,
    pub duracao_ms: u64,
    pub tamanho: u64,
    pub formato: String,
    pub modificado: u64,
    pub adicionada: u64,
}

/// O índice guardado em disco inclui o caminho (que não vai para a interface).
#[derive(Serialize, Deserialize)]
struct Indice {
    pasta: String,
    faixas: Vec<FaixaIndice>,
}

#[derive(Serialize, Deserialize)]
struct FaixaIndice {
    caminho: String,
    #[serde(flatten)]
    faixa: Faixa,
}

pub struct Biblioteca {
    arquivo_indice: PathBuf,
    pub pasta: Mutex<PathBuf>,
    pub faixas: Mutex<Arc<Vec<Faixa>>>,
    capas: Mutex<VecDeque<(String, Arc<Vec<u8>>)>>,
}

pub fn id_de(caminho: &Path) -> String {
    let h = Sha256::digest(caminho.to_string_lossy().as_bytes());
    h[..8].iter().map(|b| format!("{b:02x}")).collect()
}

fn segundos(t: std::io::Result<std::time::SystemTime>) -> u64 {
    t.ok().and_then(|t| t.duration_since(UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0)
}

impl Biblioteca {
    pub fn nova(pasta_config: &Path, pasta_padrao: PathBuf) -> Self {
        let arquivo_indice = pasta_config.join("biblioteca.json");
        let indice: Option<Indice> = std::fs::read(&arquivo_indice).ok().and_then(|b| serde_json::from_slice(&b).ok());
        let (pasta, faixas) = match indice {
            Some(i) => (PathBuf::from(i.pasta), i.faixas.into_iter().map(|f| Faixa { caminho: f.caminho, ..f.faixa }).collect()),
            None => (pasta_padrao, Vec::new()),
        };
        Self {
            arquivo_indice,
            pasta: Mutex::new(pasta),
            faixas: Mutex::new(Arc::new(faixas)),
            capas: Mutex::new(VecDeque::new()),
        }
    }

    pub fn faixa(&self, id: &str) -> Option<Faixa> {
        self.faixas.lock().unwrap().iter().find(|f| f.id == id).cloned()
    }

    /// Relê a pasta (em segundo plano: chame de uma thread bloqueante). `progresso(lidas, total)`.
    pub fn varrer(&self, progresso: impl Fn(usize, usize)) -> Arc<Vec<Faixa>> {
        let pasta = self.pasta.lock().unwrap().clone();
        let anteriores: HashMap<String, Faixa> = self.faixas.lock().unwrap().iter().map(|f| (f.caminho.clone(), f.clone())).collect();

        let mut arquivos = Vec::new();
        let mut pilha = vec![pasta.clone()];
        while let Some(dir) = pilha.pop() {
            let Ok(entradas) = std::fs::read_dir(&dir) else { continue };
            for e in entradas.flatten() {
                let p = e.path();
                let nome = e.file_name();
                if nome.to_string_lossy().starts_with('.') {
                    continue;
                }
                match e.file_type() {
                    Ok(t) if t.is_dir() => pilha.push(p),
                    Ok(t) if t.is_file() => {
                        let ext = p.extension().map(|x| x.to_string_lossy().to_ascii_lowercase()).unwrap_or_default();
                        if EXTENSOES.contains(&ext.as_str()) {
                            arquivos.push(p);
                        }
                    }
                    _ => {}
                }
            }
        }

        let total = arquivos.len();
        let mut faixas = Vec::with_capacity(total);
        for (i, p) in arquivos.iter().enumerate() {
            if i % 20 == 0 {
                progresso(i, total);
            }
            let Ok(meta) = std::fs::metadata(p) else { continue };
            let caminho = p.to_string_lossy().to_string();
            let modificado = segundos(meta.modified());
            let f = match anteriores.get(&caminho) {
                Some(f) if f.modificado == modificado && f.tamanho == meta.len() => Some(f.clone()),
                _ => {
                    let criada = segundos(meta.created());
                    ler(p, &caminho, meta.len(), modificado, if criada == 0 { modificado } else { criada })
                }
            };
            if let Some(f) = f.filter(|f| f.duracao_ms >= MINIMO_MS) {
                faixas.push(f);
            }
        }
        progresso(total, total);
        let faixas = Arc::new(faixas);
        *self.faixas.lock().unwrap() = faixas.clone();
        self.salvar(&pasta, &faixas);
        faixas
    }

    fn salvar(&self, pasta: &Path, faixas: &[Faixa]) {
        let indice = Indice {
            pasta: pasta.to_string_lossy().to_string(),
            faixas: faixas.iter().map(|f| FaixaIndice { caminho: f.caminho.clone(), faixa: f.clone() }).collect(),
        };
        if let Ok(b) = serde_json::to_vec(&indice) {
            let tmp = self.arquivo_indice.with_extension("json.tmp");
            if std::fs::write(&tmp, b).is_ok() {
                let _ = std::fs::rename(&tmp, &self.arquivo_indice);
            }
        }
    }

    /// Capa embutida (frontal, se houver) ou `cover.jpg`/`folder.jpg` da pasta. Cache de 48 capas.
    pub fn capa(&self, id: &str) -> Option<Arc<Vec<u8>>> {
        if let Some((_, c)) = self.capas.lock().unwrap().iter().find(|(i, _)| i == id) {
            return Some(c.clone());
        }
        let f = self.faixa(id)?;
        let dados = Arc::new(capa_do_arquivo(Path::new(&f.caminho))?);
        let mut capas = self.capas.lock().unwrap();
        capas.push_back((id.to_string(), dados.clone()));
        while capas.len() > 48 {
            capas.pop_front();
        }
        Some(dados)
    }
}

fn ler(p: &Path, caminho: &str, tamanho: u64, modificado: u64, adicionada: u64) -> Option<Faixa> {
    let arquivo = Probe::open(p).ok()?.options(ParseOptions::new().read_cover_art(false)).read().ok()?;
    let duracao_ms = arquivo.properties().duration().as_millis() as u64;
    let tag = arquivo.primary_tag().or_else(|| arquivo.first_tag());
    let texto = |v: Option<std::borrow::Cow<'_, str>>| v.map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
    let nome = p.file_stem().map(|s| s.to_string_lossy().to_string()).unwrap_or_default();
    Some(Faixa {
        id: id_de(p),
        caminho: caminho.to_string(),
        titulo: tag.and_then(|t| texto(t.title())).unwrap_or(nome),
        artista: tag.and_then(|t| texto(t.artist())).unwrap_or_else(|| "Artista desconhecido".into()),
        album: tag.and_then(|t| texto(t.album())).unwrap_or_default(),
        artista_album: tag.and_then(|t| t.get_string(ItemKey::AlbumArtist)).map(str::to_string).unwrap_or_default(),
        ano: tag.and_then(|t| t.date()).map(|d| d.year as u32).filter(|&a| a > 0),
        faixa: tag.and_then(|t| t.track()).filter(|&n| n > 0),
        disco: tag.and_then(|t| t.disk()).filter(|&n| n > 0),
        duracao_ms,
        tamanho,
        formato: p.extension().map(|e| e.to_string_lossy().to_ascii_lowercase()).unwrap_or_default(),
        modificado,
        adicionada,
    })
}

fn capa_do_arquivo(p: &Path) -> Option<Vec<u8>> {
    let embutida = Probe::open(p).ok().and_then(|pr| pr.read().ok()).and_then(|arquivo| {
        let tag = arquivo.primary_tag().or_else(|| arquivo.first_tag())?;
        let fotos = tag.pictures();
        fotos.iter().find(|f| f.pic_type() == PictureType::CoverFront).or_else(|| fotos.first()).map(|f| f.data().to_vec())
    });
    embutida.or_else(|| {
        let pasta = p.parent()?;
        ["cover.jpg", "folder.jpg", "cover.png", "front.jpg", "Cover.jpg", "Folder.jpg"]
            .iter()
            .find_map(|n| std::fs::read(pasta.join(n)).ok())
    })
}
