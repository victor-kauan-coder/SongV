//! Letras dos arquivos do computador: SYLT (sincronizada) e USLT do ID3v2, `.lrc` ao lado da
//! música e LRC gravado dentro do USLT — a mesma ordem de preferência do app Android.

use serde::Serialize;
use std::io::{Read, Seek, SeekFrom};
use std::path::Path;

#[derive(Clone, Serialize, Debug, PartialEq)]
pub struct Linha {
    pub t: Option<u64>,
    pub texto: String,
}

#[derive(Clone, Serialize, Debug)]
pub struct Letra {
    pub sincronizada: bool,
    pub linhas: Vec<Linha>,
}

pub fn ler(caminho: &Path) -> Option<Letra> {
    let (sylt, uslt) = if caminho.extension().is_some_and(|e| e.eq_ignore_ascii_case("mp3")) {
        std::fs::File::open(caminho).ok().and_then(|mut f| id3(&mut f)).unwrap_or((None, None))
    } else {
        (None, None)
    };
    if let Some(l) = sylt {
        return Some(Letra { sincronizada: true, linhas: l });
    }
    if let Some(texto) = std::fs::read(caminho.with_extension("lrc")).ok().map(|b| String::from_utf8_lossy(&b).into_owned()) {
        if let Some(l) = lrc(&texto) {
            return Some(Letra { sincronizada: true, linhas: l });
        }
    }
    let simples = uslt.or_else(|| letra_da_tag(caminho))?;
    if let Some(l) = lrc(&simples) {
        return Some(Letra { sincronizada: true, linhas: l });
    }
    let linhas: Vec<Linha> = simples.lines().map(|l| Linha { t: None, texto: l.trim().to_string() }).collect();
    (!linhas.iter().all(|l| l.texto.is_empty())).then_some(Letra { sincronizada: false, linhas })
}

/// Letra simples de outros formatos (FLAC `LYRICS`, M4A `©lyr`, OGG…).
fn letra_da_tag(caminho: &Path) -> Option<String> {
    use lofty::prelude::*;
    let arquivo = lofty::read_from_path(caminho).ok()?;
    let tag = arquivo.primary_tag().or_else(|| arquivo.first_tag())?;
    tag.get_string(ItemKey::UnsyncLyrics)
        .or_else(|| tag.get_string(ItemKey::Lyrics))
        .map(str::to_string)
        .filter(|s| !s.trim().is_empty())
}

// ------------------------------------------------------------------------------------ LRC

/// `[mm:ss.xx]texto`, várias marcações por linha, `[offset:±ms]`. Nada sincronizado → None.
pub fn lrc(texto: &str) -> Option<Vec<Linha>> {
    let mut deslocamento: i64 = 0;
    let mut linhas = Vec::new();
    for bruta in texto.lines() {
        let mut resto = bruta.trim();
        let mut tempos = Vec::new();
        while let Some(fim) = resto.strip_prefix('[').and_then(|r| r.find(']')) {
            let dentro = &resto[1..fim + 1];
            if let Some(v) = dentro.strip_prefix("offset:") {
                deslocamento = v.trim().parse().unwrap_or(0);
            } else if let Some(ms) = tempo_lrc(dentro) {
                tempos.push(ms);
            } else {
                break; // [ar:...], [ti:...]: metadado, não é linha
            }
            resto = resto[fim + 2..].trim_start();
        }
        for t in tempos {
            linhas.push(Linha { t: Some(t), texto: resto.trim().to_string() });
        }
    }
    if linhas.is_empty() {
        return None;
    }
    // Offset positivo = letra adiantada.
    for l in &mut linhas {
        l.t = l.t.map(|t| (t as i64 - deslocamento).max(0) as u64);
    }
    linhas.sort_by_key(|l| l.t);
    Some(linhas)
}

fn tempo_lrc(s: &str) -> Option<u64> {
    let (m, resto) = s.split_once(':')?;
    let minutos: u64 = m.trim().parse().ok()?;
    let (seg, frac) = resto.split_once(['.', ':']).unwrap_or((resto, "0"));
    let segundos: u64 = seg.trim().parse().ok()?;
    let frac = frac.trim();
    if frac.is_empty() || !frac.chars().all(|c| c.is_ascii_digit()) {
        return None;
    }
    let ms = match frac.len() {
        1 => frac.parse::<u64>().ok()? * 100,
        2 => frac.parse::<u64>().ok()? * 10,
        _ => frac[..3].parse::<u64>().ok()?,
    };
    Some(minutos * 60_000 + segundos * 1000 + ms)
}

// ------------------------------------------------------------------------------------ ID3v2

const LIMITE_TAG: usize = 16 * 1024 * 1024;

/// (SYLT, USLT) da tag ID3v2. Lê só a tag, uma vez.
fn id3<R: Read + Seek>(f: &mut R) -> Option<(Option<Vec<Linha>>, Option<String>)> {
    let mut cab = [0u8; 10];
    f.read_exact(&mut cab).ok()?;
    if &cab[..3] != b"ID3" || !(2..=4).contains(&cab[3]) {
        return None;
    }
    let versao = cab[3];
    let flags = cab[5];
    let tamanho = sync_safe(&cab[6..10]) as usize;
    if tamanho > LIMITE_TAG {
        return None;
    }
    let mut corpo = vec![0u8; tamanho];
    f.seek(SeekFrom::Start(10)).ok()?;
    let lidos = f.read(&mut corpo).ok()?;
    corpo.truncate(lidos);
    if versao < 4 && flags & 0x80 != 0 {
        corpo = desincronizar(&corpo);
    }
    let mut pos = 0usize;
    if versao >= 3 && flags & 0x40 != 0 && corpo.len() >= 4 {
        pos = if versao == 4 { sync_safe(&corpo[..4]) as usize } else { u32::from_be_bytes(corpo[..4].try_into().ok()?) as usize + 4 };
    }
    let cab_frame = if versao == 2 { 6 } else { 10 };
    let (mut sylt, mut uslt) = (None, None);
    while pos + cab_frame <= corpo.len() {
        let fh = &corpo[pos..pos + cab_frame];
        if fh[0] == 0 {
            break;
        }
        let id_len = if versao == 2 { 3 } else { 4 };
        let id = std::str::from_utf8(&fh[..id_len]).ok()?;
        if !id.bytes().all(|b| b.is_ascii_uppercase() || b.is_ascii_digit()) {
            break;
        }
        let tam = match versao {
            2 => u32::from_be_bytes([0, fh[3], fh[4], fh[5]]) as usize,
            3 => u32::from_be_bytes(fh[4..8].try_into().ok()?) as usize,
            _ => sync_safe(&fh[4..8]) as usize,
        };
        let flags_frame = if versao == 2 { 0 } else { u16::from_be_bytes([fh[8], fh[9]]) };
        let inicio = pos + cab_frame;
        if tam == 0 || inicio + tam > corpo.len() {
            break;
        }
        let id = match id {
            "SLT" => "SYLT",
            "ULT" => "USLT",
            outro => outro,
        };
        if (id == "SYLT" && sylt.is_none()) || (id == "USLT" && uslt.is_none()) {
            if let Some(dados) = desfazer_flags(&corpo[inicio..inicio + tam], versao, flags_frame) {
                if id == "SYLT" {
                    sylt = frame_sylt(&dados);
                } else {
                    uslt = frame_uslt(&dados);
                }
            }
        }
        pos = inicio + tam;
    }
    Some((sylt, uslt))
}

/// Desfaz as flags do frame. Comprimido ou criptografado: ignora (raríssimo em letras).
fn desfazer_flags(bruto: &[u8], versao: u8, f: u16) -> Option<Vec<u8>> {
    match versao {
        3 => {
            if f & 0x00C0 != 0 {
                return None;
            }
            let extra = if f & 0x0020 != 0 { 1 } else { 0 };
            bruto.get(extra..).map(<[u8]>::to_vec)
        }
        4 => {
            if f & 0x000C != 0 {
                return None;
            }
            let extra = (if f & 0x0040 != 0 { 1 } else { 0 }) + (if f & 0x0001 != 0 { 4 } else { 0 });
            let d = bruto.get(extra..)?;
            Some(if f & 0x0002 != 0 { desincronizar(d) } else { d.to_vec() })
        }
        _ => Some(bruto.to_vec()),
    }
}

/// SYLT: encoding, idioma(3), formato de tempo, tipo, descrição, [texto + tempo(4)]…
/// Só milissegundos (formato 2), que é o que o agente grava.
fn frame_sylt(d: &[u8]) -> Option<Vec<Linha>> {
    if d.len() < 7 || d[4] != 2 {
        return None;
    }
    let enc = d[0];
    let mut pos = terminador(d, 6, enc) + tam_terminador(enc);
    let mut linhas = Vec::new();
    while pos < d.len() {
        let fim = terminador(d, pos, enc);
        let texto = decodificar(&d[pos..fim], enc);
        pos = fim + tam_terminador(enc);
        if pos + 4 > d.len() {
            break;
        }
        let t = u32::from_be_bytes(d[pos..pos + 4].try_into().ok()?) as u64;
        pos += 4;
        linhas.push(Linha { t: Some(t), texto: texto.replace('\u{feff}', "").trim().to_string() });
    }
    linhas.sort_by_key(|l| l.t);
    linhas.iter().any(|l| !l.texto.is_empty()).then_some(linhas)
}

/// USLT: encoding, idioma(3), descrição terminada, texto.
fn frame_uslt(d: &[u8]) -> Option<String> {
    if d.len() < 5 {
        return None;
    }
    let enc = d[0];
    let inicio = terminador(d, 4, enc) + tam_terminador(enc);
    let s = decodificar(d.get(inicio..)?, enc).replace('\u{feff}', "");
    let s = s.trim_matches(|c| c == '\0' || c == ' ' || c == '\n' || c == '\r').to_string();
    (!s.trim().is_empty()).then_some(s)
}

fn sync_safe(b: &[u8]) -> u32 {
    ((b[0] as u32 & 0x7F) << 21) | ((b[1] as u32 & 0x7F) << 14) | ((b[2] as u32 & 0x7F) << 7) | (b[3] as u32 & 0x7F)
}

fn desincronizar(d: &[u8]) -> Vec<u8> {
    let mut saida = Vec::with_capacity(d.len());
    let mut i = 0;
    while i < d.len() {
        saida.push(d[i]);
        if d[i] == 0xFF && d.get(i + 1) == Some(&0) {
            i += 1;
        }
        i += 1;
    }
    saida
}

fn tam_terminador(enc: u8) -> usize {
    if enc == 1 || enc == 2 { 2 } else { 1 }
}

fn terminador(b: &[u8], inicio: usize, enc: u8) -> usize {
    let passo = tam_terminador(enc);
    let mut i = inicio;
    while i + passo <= b.len() {
        if b[i] == 0 && (passo == 1 || b[i + 1] == 0) {
            return i;
        }
        i += passo;
    }
    b.len()
}

fn decodificar(b: &[u8], enc: u8) -> String {
    match enc {
        0 => b.iter().map(|&c| c as char).collect(),
        1 | 2 => {
            let (dados, be) = match b {
                [0xFE, 0xFF, resto @ ..] => (resto, true),
                [0xFF, 0xFE, resto @ ..] => (resto, false),
                _ => (b, enc == 2),
            };
            let unidades: Vec<u16> = dados
                .chunks_exact(2)
                .map(|c| if be { u16::from_be_bytes([c[0], c[1]]) } else { u16::from_le_bytes([c[0], c[1]]) })
                .collect();
            String::from_utf16_lossy(&unidades)
        }
        _ => String::from_utf8_lossy(b).into_owned(),
    }
}

#[cfg(test)]
mod testes {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn lrc_com_varias_marcacoes_e_offset() {
        let l = lrc("[ar:Banda]\n[offset:500]\n[00:10.00][00:30.50]Refrão\n[00:20.5]Verso\n").unwrap();
        assert_eq!(l.len(), 3);
        assert_eq!(l[0], Linha { t: Some(9_500), texto: "Refrão".into() });
        assert_eq!(l[1], Linha { t: Some(20_000), texto: "Verso".into() });
        assert_eq!(l[2].t, Some(30_000));
        assert!(lrc("só texto\nsem tempo").is_none());
    }

    /// Monta uma tag ID3v2.3 com SYLT (UTF-8… não existe na 2.3; usa Latin-1) e USLT.
    fn tag_de_teste() -> Vec<u8> {
        let mut sylt = vec![0u8, b'p', b'o', b'r', 2, 1, 0];
        for (texto, t) in [("Primeira", 1000u32), ("Segunda", 2500)] {
            sylt.extend_from_slice(texto.as_bytes());
            sylt.push(0);
            sylt.extend_from_slice(&t.to_be_bytes());
        }
        let mut uslt = vec![0u8, b'p', b'o', b'r', 0];
        uslt.extend_from_slice(b"Letra simples");
        let mut frames = Vec::new();
        for (id, dados) in [(b"SYLT", &sylt), (b"USLT", &uslt)] {
            frames.extend_from_slice(id);
            frames.extend_from_slice(&(dados.len() as u32).to_be_bytes());
            frames.extend_from_slice(&[0, 0]);
            frames.extend_from_slice(dados);
        }
        let n = frames.len() as u32;
        let tamanho = [(n >> 21) as u8 & 0x7F, (n >> 14) as u8 & 0x7F, (n >> 7) as u8 & 0x7F, n as u8 & 0x7F];
        let mut tag = b"ID3\x03\x00\x00".to_vec();
        tag.extend_from_slice(&tamanho);
        tag.extend_from_slice(&frames);
        tag
    }

    #[test]
    fn le_sylt_e_uslt_do_id3() {
        let (sylt, uslt) = id3(&mut Cursor::new(tag_de_teste())).unwrap();
        let sylt = sylt.unwrap();
        assert_eq!(sylt.len(), 2);
        assert_eq!(sylt[1], Linha { t: Some(2500), texto: "Segunda".into() });
        assert_eq!(uslt.as_deref(), Some("Letra simples"));
    }

    #[test]
    fn utf16_com_bom() {
        assert_eq!(decodificar(&[0xFF, 0xFE, b'A', 0, b'e', 0], 1), "Ae");
        assert_eq!(decodificar(&[0xFE, 0xFF, 0, b'A'], 1), "A");
    }
}
