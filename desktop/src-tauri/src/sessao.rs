//! A conexão com o celular: aperto de mão (pareamento ou reconexão), canal cifrado e as
//! mensagens de reprodução. Protocolo descrito em docs/desktop.md.

use crate::cripto::{self, Efemera, Selador, PREFIXO_C2N, PREFIXO_N2C};
use crate::faixas::Meta;
use crate::pares::Par;
use crate::{descoberta, quadros, Nucleo};
use base64::engine::general_purpose::STANDARD as B64;
use base64::Engine;
use serde_json::{json, Value};
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncRead, AsyncWrite};
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::{mpsc, oneshot, Notify};
use tokio::time::timeout;

pub const PORTA: u16 = 47800;

pub async fn escutar(n: Arc<Nucleo>) {
    let ouvinte = match TcpListener::bind(("0.0.0.0", PORTA)).await {
        Ok(o) => o,
        // Porta ocupada (outra cópia aberta?): qualquer uma serve, o mDNS anuncia a certa.
        Err(_) => match TcpListener::bind(("0.0.0.0", 0)).await {
            Ok(o) => o,
            Err(e) => {
                n.emitir("erro", format!("Não consegui abrir a porta de rede: {e}"));
                return;
            }
        },
    };
    let porta = ouvinte.local_addr().map(|a| a.port()).unwrap_or(PORTA);
    n.porta.store(porta, Ordering::Relaxed);
    *n.mdns.lock().unwrap() = descoberta::anunciar(&n.id, &n.nome, porta);
    n.emitir("rede", json!({ "porta": porta, "ips": descoberta::enderecos() }));
    loop {
        match ouvinte.accept().await {
            Ok((sock, _)) => {
                let _ = sock.set_nodelay(true);
                let n = n.clone();
                tauri::async_runtime::spawn(async move {
                    if let Err(e) = atender(n, sock).await {
                        eprintln!("sessão encerrada: {e}");
                    }
                });
            }
            Err(_) => tokio::time::sleep(Duration::from_millis(200)).await,
        }
    }
}

fn b64(v: &Value, campo: &str, tamanho: usize) -> Result<Vec<u8>, String> {
    let b = B64.decode(v[campo].as_str().unwrap_or("")).map_err(|_| format!("{campo} inválido"))?;
    if b.len() != tamanho {
        return Err(format!("{campo} com tamanho errado"));
    }
    Ok(b)
}

fn id_valido(v: &Value) -> Result<String, String> {
    let id = v["id"].as_str().unwrap_or("");
    if (8..=64).contains(&id.len()) && id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
        Ok(id.to_string())
    } else {
        Err("id inválido".into())
    }
}

async fn enviar_claro<W: AsyncWrite + Unpin>(w: &mut W, v: Value) -> Result<(), String> {
    quadros::escrever(w, &quadros::claro(&v)).await
}

async fn ler_claro<R: AsyncRead + Unpin>(r: &mut R, segundos: u64) -> Result<Value, String> {
    let q = timeout(Duration::from_secs(segundos), quadros::ler(r)).await.map_err(|_| "o celular não respondeu")??;
    quadros::ler_claro(&q)
}

async fn atender(n: Arc<Nucleo>, sock: TcpStream) -> Result<(), String> {
    let (mut r, mut w) = sock.into_split();
    let ola = ler_claro(&mut r, 10).await?;
    if ola["t"] != "ola" || ola["v"].as_u64() != Some(1) {
        enviar_claro(&mut w, json!({"t": "erro", "motivo": "versao"})).await?;
        return Err("versão de protocolo diferente".into());
    }
    let id_celular = id_valido(&ola)?;
    let nome_celular: String = ola["nome"].as_str().unwrap_or("Celular").chars().take(60).collect();

    let (k, nc, nn, par_novo) = match ola["modo"].as_str() {
        Some("parear") => {
            let (k, nc, nn) = parear(&n, &mut r, &mut w, &ola, &nome_celular).await?;
            (k, nc, nn, true)
        }
        Some("sessao") => {
            let chave = n.pares.lock().unwrap().chave(&id_celular);
            let Some(k) = chave else {
                enviar_claro(&mut w, json!({"t": "erro", "motivo": "desconhecido"})).await?;
                return Err("celular não pareado".into());
            };
            let nc = b64(&ola, "nonce", 32)?;
            let nn = cripto::aleatorio(32);
            enviar_claro(&mut w, json!({"t": "ola", "v": 1, "id": n.id, "nome": n.nome, "nonce": B64.encode(&nn)})).await?;
            (k, nc, nn, false)
        }
        _ => return Err("modo desconhecido".into()),
    };

    // Confirmação de chave: cada lado manda "pronto" cifrado; se não decifra, a chave é outra.
    let (c2n, n2c) = cripto::chaves_sessao(&k, &nc, &nn);
    let mut abrir = Selador::novo(&c2n, PREFIXO_C2N);
    let mut selar = Selador::novo(&n2c, PREFIXO_N2C);
    quadros::escrever(&mut w, &selar.selar(&quadros::json(&json!({"t": "pronto"})))).await?;
    let q = timeout(Duration::from_secs(15), quadros::ler(&mut r)).await.map_err(|_| "sem confirmação")??;
    let claro = abrir.abrir(&q)?;
    let pronto: Value = serde_json::from_slice(claro.get(1..).unwrap_or_default()).unwrap_or_default();
    if claro.first() != Some(&quadros::TIPO_JSON) || pronto["t"] != "pronto" {
        return Err("confirmação inválida".into());
    }
    if par_novo {
        let guardado = n.pares.lock().unwrap().guardar(Par { id: id_celular.clone(), nome: nome_celular.clone() }, &k);
        if let Err(e) = guardado {
            n.emitir("pareamento", json!({"etapa": "falhou", "celular": nome_celular, "motivo": e}));
            return Err(e);
        }
        n.emitir("pareamento", json!({"etapa": "concluido", "celular": nome_celular}));
    }

    // Sessão no ar. Uma por vez: um celular novo derruba o anterior.
    let geracao = n.geracao.fetch_add(1, Ordering::Relaxed) + 1;
    let (tx, mut rx) = mpsc::unbounded_channel::<Vec<u8>>();
    let fim = Arc::new(Notify::new());
    if let Some((_, _, antigo)) = n.sessao.lock().unwrap().replace((geracao, tx, fim.clone())) {
        antigo.notify_one();
    }
    *n.celular.lock().unwrap() = Some((id_celular.clone(), nome_celular.clone()));
    n.faixas.limpar();
    n.faixas.limpar_capas();
    n.emitir("conexao", json!({"conectado": true, "celular": nome_celular}));

    let escritor = tauri::async_runtime::spawn(async move {
        while let Some(claro) = rx.recv().await {
            if quadros::escrever(&mut w, &selar.selar(&claro)).await.is_err() {
                break;
            }
        }
    });

    let resultado = loop {
        tokio::select! {
            _ = fim.notified() => break Ok(()),
            q = quadros::ler(&mut r) => {
                let claro = match q.and_then(|q| abrir.abrir(&q)) {
                    Ok(c) => c,
                    Err(e) => break Err(e),
                };
                tratar(&n, &claro);
            }
        }
    };
    escritor.abort();

    let mut sessao = n.sessao.lock().unwrap();
    if sessao.as_ref().map(|s| s.0) == Some(geracao) {
        *sessao = None;
        drop(sessao);
        *n.celular.lock().unwrap() = None;
        n.faixas.limpar();
        n.emitir("conexao", json!({"conectado": false}));
    }
    resultado
}

/// Pareamento: ECDH com compromisso + código de 6 dígitos confirmado nos dois aparelhos.
async fn parear<R, W>(n: &Arc<Nucleo>, r: &mut R, w: &mut W, ola: &Value, nome: &str) -> Result<(Vec<u8>, Vec<u8>, Vec<u8>), String>
where
    R: AsyncRead + Unpin,
    W: AsyncWrite + Unpin,
{
    if !n.pareamento_aberto.load(Ordering::Relaxed) {
        enviar_claro(w, json!({"t": "erro", "motivo": "pareamento-fechado"})).await?;
        return Err("pareamento fechado".into());
    }
    let compromisso_celular = b64(ola, "compromisso", 32)?;
    let efemera = Efemera::nova();
    let nn = cripto::aleatorio(32);
    enviar_claro(w, json!({
        "t": "ola", "v": 1, "id": n.id, "nome": n.nome,
        "pub": B64.encode(&efemera.publica), "nonce": B64.encode(&nn),
    }))
    .await?;

    let revela = ler_claro(r, 30).await?;
    if revela["t"] != "revela" {
        return Err("esperava a chave do celular".into());
    }
    let publica_celular = b64(&revela, "pub", 65)?;
    let nc = b64(&revela, "nonce", 32)?;
    if cripto::compromisso(&publica_celular, &nc) != compromisso_celular {
        return Err("a chave do celular não confere com o compromisso".into());
    }
    let z = efemera.acordo(&publica_celular)?;
    let codigo = cripto::codigo(&z, &nc, &nn);
    let k = cripto::chave_par(&z, &nc, &nn);

    let (tx, mut rx) = oneshot::channel();
    *n.decisao.lock().unwrap() = Some(tx);
    n.emitir("pareamento", json!({"etapa": "codigo", "celular": nome, "codigo": codigo}));

    let leitura = quadros::ler(r);
    tokio::pin!(leitura);
    let prazo = tokio::time::sleep(Duration::from_secs(120));
    tokio::pin!(prazo);
    let (mut local, mut remota) = (None::<bool>, None::<bool>);
    while local.is_none() || remota.is_none() {
        tokio::select! {
            d = &mut rx, if local.is_none() => {
                let ok = d.unwrap_or(false);
                local = Some(ok);
                enviar_claro(w, json!({"t": "decisao", "ok": ok})).await?;
                if !ok { break; }
            }
            q = &mut leitura, if remota.is_none() => {
                let v = quadros::ler_claro(&q?)?;
                let ok = v["t"] == "decisao" && v["ok"] == true;
                remota = Some(ok);
                if !ok { break; }
            }
            _ = &mut prazo => break,
        }
    }
    n.decisao.lock().unwrap().take();
    if local != Some(true) || remota != Some(true) {
        let motivo = match (local, remota) {
            (_, Some(false)) => "Recusado no celular.",
            (Some(false), _) => "Pareamento cancelado.",
            _ => "O tempo para confirmar acabou.",
        };
        n.emitir("pareamento", json!({"etapa": "falhou", "celular": nome, "motivo": motivo}));
        return Err(motivo.into());
    }
    Ok((k, nc, nn))
}

fn tratar(n: &Arc<Nucleo>, claro: &[u8]) {
    let Some((&tipo, carga)) = claro.split_first() else { return };
    match tipo {
        quadros::TIPO_JSON => {
            let Ok(v) = serde_json::from_slice::<Value>(carga) else { return };
            let t = v["t"].as_str().unwrap_or("").to_string();
            match t.as_str() {
                "ping" => {
                    n.enviar(json!({"t": "pong", "t0": v["t0"]}));
                }
                "tocar" => tocar(n, v),
                "sair" => {
                    n.faixas.limpar();
                    n.emitir("sair", v);
                }
                "biblioteca" => {
                    if let Some(tx) = n.pedido_biblioteca.lock().unwrap().take() {
                        let _ = tx.send(v["faixas"].clone());
                    }
                }
                "pausar" | "retomar" | "buscar" | "volume" | "letra" | "saida" => n.emitir(&t, v),
                // O celular navegando e tocando as músicas deste computador.
                "biblioteca-pc?" => {
                    let faixas = n.biblioteca.faixas.lock().unwrap().clone();
                    n.enviar(json!({"t": "biblioteca-pc", "nome": n.nome, "faixas": &*faixas}));
                }
                "capa-pc?" | "letra-pc?" | "ler-pc" => {
                    let (n, t, v) = (n.clone(), t.clone(), v.clone());
                    tauri::async_runtime::spawn_blocking(move || servir_do_pc(&n, &t, &v));
                }
                _ => {}
            }
        }
        quadros::TIPO_AUDIO => n.faixas.receber_audio(carga),
        quadros::TIPO_CAPA => {
            if let Some(id) = n.faixas.receber_capa(carga) {
                let versao = n.versao_capa.fetch_add(1, Ordering::Relaxed) + 1;
                n.emitir("capa", json!({"id": id, "versao": versao}));
            }
        }
        _ => {}
    }
}

/// Bloco de áudio mandado ao celular: pedaços de 256 KB (`u32 pedido ‖ u64 início ‖ bytes`).
const PEDACO: usize = 256 * 1024;

/// Pedidos do celular sobre a biblioteca daqui. Só faixas indexadas: nada de arquivos soltos.
fn servir_do_pc(n: &Arc<Nucleo>, t: &str, v: &Value) {
    let id = v["faixa"].as_str().unwrap_or_default();
    let Some(f) = n.biblioteca.faixa(id) else { return };
    match t {
        "capa-pc?" => {
            // Sem capa (ou grande demais para um quadro) = imagem vazia: o celular desenha a gerada.
            let capa = n.biblioteca.capa(id).filter(|c| c.len() < 7 * 1024 * 1024);
            let mut q = vec![quadros::TIPO_CAPA];
            q.extend_from_slice(&(id.len() as u16).to_be_bytes());
            q.extend_from_slice(id.as_bytes());
            if let Some(c) = capa {
                q.extend_from_slice(&c);
            }
            n.enviar_quadro(q);
        }
        "letra-pc?" => {
            let letra = crate::letra::ler(std::path::Path::new(&f.caminho));
            n.enviar(json!({"t": "letra-pc", "faixa": id, "letra": letra}));
        }
        _ => {
            use std::io::{Read, Seek, SeekFrom};
            let pedido = v["pedido"].as_u64().unwrap_or(0) as u32;
            let mut pos = v["de"].as_u64().unwrap_or(0);
            let fim = (pos + v["tamanho"].as_u64().unwrap_or(0).min(8 * 1024 * 1024)).min(f.tamanho);
            let Ok(mut arq) = std::fs::File::open(&f.caminho) else { return };
            if arq.seek(SeekFrom::Start(pos)).is_err() {
                return;
            }
            let mut buf = vec![0u8; PEDACO];
            while pos < fim {
                let quer = PEDACO.min((fim - pos) as usize);
                let Ok(lidos) = arq.read(&mut buf[..quer]) else { return };
                if lidos == 0 {
                    return;
                }
                let mut q = Vec::with_capacity(13 + lidos);
                q.push(quadros::TIPO_AUDIO);
                q.extend_from_slice(&pedido.to_be_bytes());
                q.extend_from_slice(&pos.to_be_bytes());
                q.extend_from_slice(&buf[..lidos]);
                if !n.enviar_quadro(q) {
                    return;
                }
                pos += lidos as u64;
            }
        }
    }
}

fn tocar(n: &Arc<Nucleo>, v: Value) {
    let Ok(atual) = serde_json::from_value::<Meta>(v["faixa"].clone()) else { return };
    let proxima = serde_json::from_value::<Meta>(v["proxima"].clone()).ok().filter(|p| !p.id.starts_with("pc:"));
    // Uma faixa deste computador na fila do celular: toca o arquivo daqui, não há o que baixar.
    if !atual.id.starts_with("pc:") {
        baixar(n, &atual, proxima.as_ref());
    }
    n.emitir("tocar", v);
}

/// Mantém só a faixa atual e a próxima em memória e baixa do celular o que faltar.
pub fn baixar(n: &Arc<Nucleo>, atual: &Meta, proxima: Option<&Meta>) {
    let novas = n.faixas.preparar(atual, proxima);
    if !novas.is_empty() {
        let n = n.clone();
        tauri::async_runtime::spawn(async move {
            // A atual primeiro; a próxima só depois, para não dividir a banda na hora de começar.
            for f in novas {
                let para = n.clone();
                if !n.faixas.baixar(&f, move |m| para.enviar(m)).await {
                    break;
                }
            }
        });
    }
}
