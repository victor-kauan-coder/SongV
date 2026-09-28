//! SongV para computador: um player completo com a biblioteca do computador, que também navega
//! e toca a biblioteca do celular pela rede local e serve de saída de som para ele ("Tocar em…").
//! Rede, criptografia, biblioteca e arquivos ficam aqui; a interface (ui/) toca no `<audio>`.

mod biblioteca;
mod cripto;
mod descoberta;
mod faixas;
mod letra;
mod pares;
mod quadros;
mod sessao;

use serde::Serialize;
use serde_json::{json, Value};
use std::io::{Read, Seek, SeekFrom};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicU16, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tauri::http::{header, Request, Response, StatusCode};
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::sync::{mpsc, oneshot, Notify};

pub struct Nucleo {
    app: AppHandle,
    id: String,
    nome: String,
    porta: AtomicU16,
    pares: Mutex<pares::Pares>,
    pareamento_aberto: AtomicBool,
    decisao: Mutex<Option<oneshot::Sender<bool>>>,
    /// Sessão atual: (geração, fila de saída em texto claro, aviso para encerrar).
    sessao: Mutex<Option<(u64, mpsc::UnboundedSender<Vec<u8>>, Arc<Notify>)>>,
    geracao: AtomicU64,
    /// (id, nome) do celular conectado.
    celular: Mutex<Option<(String, String)>>,
    faixas: Arc<faixas::Faixas>,
    versao_capa: AtomicU32,
    mdns: Mutex<Option<mdns_sd::ServiceDaemon>>,
    biblioteca: Arc<biblioteca::Biblioteca>,
    varrendo: AtomicBool,
    /// Pedido da biblioteca do celular esperando a resposta.
    pedido_biblioteca: Mutex<Option<oneshot::Sender<Value>>>,
}

impl Nucleo {
    /// Manda uma mensagem ao celular conectado. Falso se não há sessão.
    fn enviar(&self, v: Value) -> bool {
        match self.sessao.lock().unwrap().as_ref() {
            Some((_, tx, _)) => tx.send(quadros::json(&v)).is_ok(),
            None => false,
        }
    }

    /// Manda um quadro já montado (`tipo ‖ carga`, em claro: a sessão cifra).
    fn enviar_quadro(&self, q: Vec<u8>) -> bool {
        match self.sessao.lock().unwrap().as_ref() {
            Some((_, tx, _)) => tx.send(q).is_ok(),
            None => false,
        }
    }

    fn emitir<S: Serialize + Clone>(&self, evento: &str, dados: S) {
        let _ = self.app.emit(evento, dados);
    }
}

// ------------------------------------------------------------------------------ comandos

#[derive(Serialize)]
struct Info {
    nome: String,
    porta: u16,
    ips: Vec<String>,
    pares: Vec<pares::Par>,
    celular: Option<String>,
    pasta: String,
    versao: &'static str,
}

#[tauri::command]
fn info(n: State<Arc<Nucleo>>) -> Info {
    Info {
        nome: n.nome.clone(),
        porta: n.porta.load(Ordering::Relaxed),
        ips: descoberta::enderecos(),
        pares: n.pares.lock().unwrap().lista.clone(),
        celular: n.celular.lock().unwrap().as_ref().map(|c| c.1.clone()),
        pasta: n.biblioteca.pasta.lock().unwrap().to_string_lossy().to_string(),
        versao: env!("CARGO_PKG_VERSION"),
    }
}

#[tauri::command]
fn permitir_pareamento(n: State<Arc<Nucleo>>, aberto: bool) {
    n.pareamento_aberto.store(aberto, Ordering::Relaxed);
    if !aberto {
        if let Some(tx) = n.decisao.lock().unwrap().take() {
            let _ = tx.send(false);
        }
    }
}

#[tauri::command]
fn decidir_pareamento(n: State<Arc<Nucleo>>, aceitar: bool) {
    if let Some(tx) = n.decisao.lock().unwrap().take() {
        let _ = tx.send(aceitar);
    }
}

#[tauri::command]
fn esquecer(n: State<Arc<Nucleo>>, id: String) -> Vec<pares::Par> {
    n.pares.lock().unwrap().esquecer(&id);
    let conectado = n.celular.lock().unwrap().as_ref().is_some_and(|c| c.0 == id);
    if conectado {
        if let Some((_, _, fim)) = n.sessao.lock().unwrap().as_ref() {
            fim.notify_one();
        }
    }
    n.pares.lock().unwrap().lista.clone()
}

/// Posição do `<audio>` quando o computador é a saída do celular (1×/s e a cada mudança).
#[tauri::command]
fn estado(n: State<Arc<Nucleo>>, faixa: String, posicao_ms: u64, tocando: bool, carregando: bool, volume: f64) {
    n.enviar(json!({
        "t": "estado", "faixa": faixa, "posicaoMs": posicao_ms,
        "tocando": tocando, "carregando": carregando, "volume": volume,
    }));
}

/// Botões daqui quando o celular está no controle: quem mexe na fila é ele.
#[tauri::command]
fn comando(n: State<Arc<Nucleo>>, acao: String, posicao_ms: Option<u64>, volume: Option<f64>) -> bool {
    n.enviar(json!({"t": "comando", "acao": acao, "posicaoMs": posicao_ms, "volume": volume}))
}

#[tauri::command]
fn terminou(n: State<Arc<Nucleo>>, faixa: String) {
    n.enviar(json!({"t": "terminou", "faixa": faixa}));
}

/// O computador vai tocar a própria fila: o celular deixa de usá-lo como saída.
#[tauri::command]
fn saida_livre(n: State<Arc<Nucleo>>) {
    n.enviar(json!({"t": "saida-livre"}));
}

// ---- biblioteca do computador ----

#[tauri::command]
fn biblioteca(n: State<Arc<Nucleo>>) -> Arc<Vec<biblioteca::Faixa>> {
    n.biblioteca.faixas.lock().unwrap().clone()
}

/// Relê a pasta em segundo plano; avisa com "biblioteca-progresso" e "biblioteca".
#[tauri::command]
fn atualizar_biblioteca(n: State<Arc<Nucleo>>) {
    varrer(n.inner().clone());
}

fn varrer(n: Arc<Nucleo>) {
    if n.varrendo.swap(true, Ordering::AcqRel) {
        return;
    }
    tauri::async_runtime::spawn_blocking(move || {
        let faixas = n.biblioteca.varrer(|lidas, total| n.emitir("biblioteca-progresso", json!({"lidas": lidas, "total": total})));
        n.varrendo.store(false, Ordering::Release);
        n.emitir("biblioteca", faixas.len());
    });
}

#[tauri::command]
async fn escolher_pasta(app: AppHandle, n: State<'_, Arc<Nucleo>>) -> Result<Option<String>, String> {
    use tauri_plugin_dialog::DialogExt;
    let (tx, rx) = oneshot::channel();
    app.dialog().file().set_title("Pasta das músicas").pick_folder(move |p| {
        let _ = tx.send(p);
    });
    let Some(escolha) = rx.await.map_err(|e| e.to_string())? else { return Ok(None) };
    let caminho = escolha.into_path().map_err(|e| e.to_string())?;
    *n.biblioteca.pasta.lock().unwrap() = caminho.clone();
    varrer(n.inner().clone());
    Ok(Some(caminho.to_string_lossy().to_string()))
}

#[tauri::command]
async fn letra_local(n: State<'_, Arc<Nucleo>>, id: String) -> Result<Option<letra::Letra>, String> {
    let Some(f) = n.biblioteca.faixa(&id) else { return Ok(None) };
    tauri::async_runtime::spawn_blocking(move || letra::ler(std::path::Path::new(&f.caminho)))
        .await
        .map_err(|e| e.to_string())
}

// ---- biblioteca do celular ----

#[tauri::command]
async fn biblioteca_celular(n: State<'_, Arc<Nucleo>>) -> Result<Value, String> {
    let (tx, rx) = oneshot::channel();
    *n.pedido_biblioteca.lock().unwrap() = Some(tx);
    if !n.enviar(json!({"t": "biblioteca"})) {
        return Err("Nenhum celular conectado.".into());
    }
    match tokio::time::timeout(Duration::from_secs(20), rx).await {
        Ok(Ok(v)) => Ok(v),
        _ => Err("O celular não respondeu.".into()),
    }
}

/// A resposta chega pelos eventos "letra" e "capa".
#[tauri::command]
fn pedir_celular(n: State<Arc<Nucleo>>, pedido: String, faixa: String) -> bool {
    let t = match pedido.as_str() {
        "letra" => "letra?",
        "capa" => "capa?",
        _ => return false,
    };
    n.enviar(json!({"t": t, "faixa": faixa}))
}

/// Vai tocar uma faixa do celular aqui: começa a baixar a atual (e depois a próxima).
#[tauri::command]
fn preparar_celular(n: State<Arc<Nucleo>>, atual: faixas::Meta, proxima: Option<faixas::Meta>) {
    sessao::baixar(n.inner(), &atual, proxima.as_ref());
}

// ------------------------------------------------------------------------------ protocolo

/// `songv://audio/<id>` (faixa vinda do celular), `songv://local/<id>` (arquivo daqui),
/// `songv://capa/<id>` e `songv://capa-local/<id>`. Áudio sempre com Range.
async fn servir(n: Arc<Nucleo>, req: Request<Vec<u8>>) -> Response<Vec<u8>> {
    let caminho = req.uri().path().trim_start_matches('/').to_string();
    let (tipo, id) = caminho.split_once('/').unwrap_or(("", ""));
    let range = req.headers().get(header::RANGE).and_then(|v| v.to_str().ok()).map(str::to_string);
    let resposta = Response::builder().header(header::ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    let nada = |status| Response::builder().status(status).body(Vec::new()).unwrap();
    match tipo {
        "capa" | "capa-local" => {
            let img = if tipo == "capa" { n.faixas.capa(id) } else {
                let (b, id) = (n.biblioteca.clone(), id.to_string());
                tauri::async_runtime::spawn_blocking(move || b.capa(&id)).await.ok().flatten()
            };
            match img {
                Some(img) => {
                    let mime = if img.starts_with(b"\x89PNG") { "image/png" } else { "image/jpeg" };
                    resposta.header(header::CONTENT_TYPE, mime).header(header::CACHE_CONTROL, "max-age=3600").body(img.to_vec()).unwrap()
                }
                None => nada(StatusCode::NOT_FOUND),
            }
        }
        "audio" => {
            let Some(f) = n.faixas.obter(id) else { return nada(StatusCode::NOT_FOUND) };
            let (de, ate_pedido) = intervalo(range.as_deref(), f.tamanho);
            if de >= f.tamanho {
                return fora_do_arquivo(f.tamanho);
            }
            // Pedido aberto ("bytes=X-"): entrega o que já chegou, no mínimo 256 KB.
            let ate = match ate_pedido {
                Some(a) => a + 1,
                None => (de + 256 * 1024).max(f.pronto_ate()).min(de + 4 * 1024 * 1024),
            }
            .min(f.tamanho);
            if !f.esperar(ate).await {
                return nada(StatusCode::SERVICE_UNAVAILABLE);
            }
            parcial(resposta, f.mime, de, ate, f.tamanho, f.trecho(de, ate))
        }
        "local" => {
            let Some(f) = n.biblioteca.faixa(id) else { return nada(StatusCode::NOT_FOUND) };
            let (de, ate_pedido) = intervalo(range.as_deref(), f.tamanho);
            if de >= f.tamanho {
                return fora_do_arquivo(f.tamanho);
            }
            let ate = ate_pedido.map(|a| a + 1).unwrap_or(de + 2 * 1024 * 1024).min(f.tamanho);
            let caminho = f.caminho.clone();
            let lido = tauri::async_runtime::spawn_blocking(move || -> std::io::Result<Vec<u8>> {
                let mut arq = std::fs::File::open(caminho)?;
                arq.seek(SeekFrom::Start(de))?;
                let mut buf = vec![0u8; (ate - de) as usize];
                arq.read_exact(&mut buf)?;
                Ok(buf)
            })
            .await;
            match lido {
                Ok(Ok(buf)) => parcial(resposta, mime_de(&f.formato), de, ate, f.tamanho, buf),
                _ => nada(StatusCode::NOT_FOUND),
            }
        }
        _ => nada(StatusCode::NOT_FOUND),
    }
}

fn parcial(r: tauri::http::response::Builder, mime: &str, de: u64, ate: u64, total: u64, corpo: Vec<u8>) -> Response<Vec<u8>> {
    r.status(StatusCode::PARTIAL_CONTENT)
        .header(header::CONTENT_TYPE, mime)
        .header(header::ACCEPT_RANGES, "bytes")
        .header(header::CONTENT_RANGE, format!("bytes {}-{}/{}", de, ate - 1, total))
        .body(corpo)
        .unwrap()
}

fn fora_do_arquivo(total: u64) -> Response<Vec<u8>> {
    Response::builder()
        .status(StatusCode::RANGE_NOT_SATISFIABLE)
        .header(header::CONTENT_RANGE, format!("bytes */{total}"))
        .body(Vec::new())
        .unwrap()
}

fn mime_de(formato: &str) -> &'static str {
    match formato {
        "flac" => "audio/flac",
        "m4a" | "aac" => "audio/mp4",
        "ogg" | "oga" | "opus" => "audio/ogg",
        "wav" => "audio/wav",
        "wma" => "audio/x-ms-wma",
        _ => "audio/mpeg",
    }
}

/// "bytes=inicio-fim" → (início, fim inclusivo se informado).
fn intervalo(range: Option<&str>, tamanho: u64) -> (u64, Option<u64>) {
    let Some(r) = range.and_then(|r| r.strip_prefix("bytes=")) else { return (0, None) };
    let (a, b) = r.split_once('-').unwrap_or((r, ""));
    match (a.trim().parse::<u64>(), b.trim().parse::<u64>()) {
        (Ok(a), Ok(b)) => (a, Some(b.min(tamanho.saturating_sub(1)))),
        (Ok(a), Err(_)) => (a, None),
        // "bytes=-N": os últimos N bytes.
        (Err(_), Ok(n)) => (tamanho.saturating_sub(n), None),
        _ => (0, None),
    }
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .register_asynchronous_uri_scheme_protocol("songv", |ctx, req, responder| {
            let n = ctx.app_handle().state::<Arc<Nucleo>>().inner().clone();
            tauri::async_runtime::spawn(async move { responder.respond(servir(n, req).await) });
        })
        .setup(|app| {
            let pasta = app.path().app_config_dir()?;
            std::fs::create_dir_all(&pasta)?;
            let nome = hostname::get().ok().and_then(|h| h.into_string().ok()).unwrap_or_else(|| "Computador".into());
            let musicas = dirs::audio_dir().or_else(|| dirs::home_dir().map(|h| h.join("Music"))).unwrap_or_else(|| PathBuf::from("."));
            let n = Arc::new(Nucleo {
                app: app.handle().clone(),
                id: pares::identidade(&pasta),
                nome,
                porta: AtomicU16::new(0),
                pares: Mutex::new(pares::Pares::carregar(&pasta)),
                pareamento_aberto: AtomicBool::new(false),
                decisao: Mutex::new(None),
                sessao: Mutex::new(None),
                geracao: AtomicU64::new(0),
                celular: Mutex::new(None),
                faixas: Arc::new(faixas::Faixas::default()),
                versao_capa: AtomicU32::new(0),
                mdns: Mutex::new(None),
                biblioteca: Arc::new(biblioteca::Biblioteca::nova(&pasta, musicas)),
                varrendo: AtomicBool::new(false),
                pedido_biblioteca: Mutex::new(None),
            });
            app.manage(n.clone());
            // Abre com o índice salvo e confere a pasta em segundo plano.
            varrer(n.clone());
            tauri::async_runtime::spawn(sessao::escutar(n));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            info,
            permitir_pareamento,
            decidir_pareamento,
            esquecer,
            estado,
            comando,
            terminou,
            saida_livre,
            biblioteca,
            atualizar_biblioteca,
            escolher_pasta,
            letra_local,
            biblioteca_celular,
            pedir_celular,
            preparar_celular,
        ])
        .run(tauri::generate_context!())
        .expect("não consegui iniciar o SongV");
}

#[cfg(test)]
mod testes {
    use super::intervalo;

    #[test]
    fn le_cabecalho_range() {
        assert_eq!(intervalo(Some("bytes=0-"), 100), (0, None));
        assert_eq!(intervalo(Some("bytes=10-19"), 100), (10, Some(19)));
        assert_eq!(intervalo(Some("bytes=10-500"), 100), (10, Some(99)));
        assert_eq!(intervalo(Some("bytes=-20"), 100), (80, None));
        assert_eq!(intervalo(None, 100), (0, None));
    }
}
