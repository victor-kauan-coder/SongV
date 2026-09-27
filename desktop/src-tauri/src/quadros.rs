//! Quadros na conexão TCP: `u32 big-endian` com o tamanho + conteúdo.
//!
//! Conteúdo claro (só no aperto de mão): `0x00 ‖ JSON`. Depois da sessão, todo quadro é
//! cifrado e o texto claro é `tipo ‖ carga` (ver os `TIPO_*`).

use serde_json::Value;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};

pub const TIPO_CLARO: u8 = 0;
pub const TIPO_JSON: u8 = 1;
pub const TIPO_AUDIO: u8 = 2;
pub const TIPO_CAPA: u8 = 3;

/// Maior quadro aceito. Blocos de áudio têm 256 KB; capas raramente passam de 2 MB.
const MAXIMO: usize = 8 * 1024 * 1024;

pub async fn ler<R: AsyncRead + Unpin>(r: &mut R) -> Result<Vec<u8>, String> {
    let tamanho = r.read_u32().await.map_err(|e| format!("conexão encerrada: {e}"))? as usize;
    if tamanho == 0 || tamanho > MAXIMO {
        return Err(format!("quadro com tamanho inválido ({tamanho})"));
    }
    let mut dados = vec![0u8; tamanho];
    r.read_exact(&mut dados).await.map_err(|e| format!("conexão encerrada: {e}"))?;
    Ok(dados)
}

pub async fn escrever<W: AsyncWrite + Unpin>(w: &mut W, dados: &[u8]) -> Result<(), String> {
    w.write_u32(dados.len() as u32).await.map_err(|e| e.to_string())?;
    w.write_all(dados).await.map_err(|e| e.to_string())?;
    w.flush().await.map_err(|e| e.to_string())
}

pub fn claro(v: &Value) -> Vec<u8> {
    let mut b = vec![TIPO_CLARO];
    b.extend_from_slice(v.to_string().as_bytes());
    b
}

pub fn json(v: &Value) -> Vec<u8> {
    let mut b = vec![TIPO_JSON];
    b.extend_from_slice(v.to_string().as_bytes());
    b
}

/// Lê um quadro claro do aperto de mão.
pub fn ler_claro(q: &[u8]) -> Result<Value, String> {
    match q.split_first() {
        Some((&TIPO_CLARO, resto)) => serde_json::from_slice(resto).map_err(|_| "JSON inválido".into()),
        _ => Err("esperava um quadro claro".into()),
    }
}

#[cfg(test)]
mod testes {
    use super::*;
    use serde_json::json;

    #[tokio::test]
    async fn escreve_e_le_o_mesmo_quadro() {
        let (mut a, mut b) = tokio::io::duplex(1024);
        let q = claro(&json!({"t": "ola"}));
        escrever(&mut a, &q).await.unwrap();
        let lido = ler(&mut b).await.unwrap();
        assert_eq!(ler_claro(&lido).unwrap()["t"], "ola");
    }

    #[tokio::test]
    async fn recusa_quadro_gigante() {
        let (mut a, mut b) = tokio::io::duplex(64);
        a.write_u32(u32::MAX).await.unwrap();
        assert!(ler(&mut b).await.is_err());
    }
}
