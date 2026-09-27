//! Criptografia do canal celular ↔ computador (ver docs/desktop.md).
//!
//! - Pareamento: ECDH P-256 com *commitment* + código de 6 dígitos comparado pelo usuário.
//! - Sessão: AES-256-GCM, uma chave por direção, nonce = prefixo da direção ‖ contador.
//!
//! O app Android implementa exatamente as mesmas funções (`Cripto.kt`); os testes dos dois lados
//! usam os mesmos vetores.

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use hkdf::Hkdf;
use p256::ecdh::EphemeralSecret;
use p256::{EncodedPoint, PublicKey};
use rand::rngs::OsRng;
use rand::RngCore;
use sha2::{Digest, Sha256};

pub const PREFIXO_C2N: [u8; 4] = *b"C2N\0";
pub const PREFIXO_N2C: [u8; 4] = *b"N2C\0";

pub fn aleatorio(tamanho: usize) -> Vec<u8> {
    let mut b = vec![0u8; tamanho];
    OsRng.fill_bytes(&mut b);
    b
}

pub fn hkdf(ikm: &[u8], sal: &[u8], info: &str, tamanho: usize) -> Vec<u8> {
    let mut saida = vec![0u8; tamanho];
    Hkdf::<Sha256>::new(Some(sal), ikm)
        .expand(info.as_bytes(), &mut saida)
        .expect("tamanho de saída do HKDF válido");
    saida
}

/// SHA-256(chave pública ‖ nonce): o celular se compromete com a chave antes de ver a nossa.
pub fn compromisso(publica: &[u8], nonce: &[u8]) -> Vec<u8> {
    let mut h = Sha256::new();
    h.update(publica);
    h.update(nonce);
    h.finalize().to_vec()
}

fn sal(nonce_c: &[u8], nonce_n: &[u8]) -> Vec<u8> {
    [nonce_c, nonce_n].concat()
}

/// Chave do par, guardada depois que o usuário confirma o código nos dois aparelhos.
pub fn chave_par(z: &[u8], nonce_c: &[u8], nonce_n: &[u8]) -> Vec<u8> {
    hkdf(z, &sal(nonce_c, nonce_n), "songv-par", 32)
}

/// Código de 6 dígitos mostrado nos dois aparelhos.
pub fn codigo(z: &[u8], nonce_c: &[u8], nonce_n: &[u8]) -> String {
    let b = hkdf(z, &sal(nonce_c, nonce_n), "songv-codigo", 4);
    format!("{:06}", u32::from_be_bytes([b[0], b[1], b[2], b[3]]) % 1_000_000)
}

/// Chaves de uma sessão: (celular→notebook, notebook→celular). Novas a cada conexão.
pub fn chaves_sessao(k: &[u8], nonce_c: &[u8], nonce_n: &[u8]) -> (Vec<u8>, Vec<u8>) {
    let s = sal(nonce_c, nonce_n);
    (hkdf(k, &s, "songv-c2n", 32), hkdf(k, &s, "songv-n2c", 32))
}

/// Par de chaves efêmero do pareamento.
pub struct Efemera {
    secreto: EphemeralSecret,
    /// SEC1 não comprimido (65 bytes), o mesmo formato que o Android envia.
    pub publica: Vec<u8>,
}

impl Efemera {
    pub fn nova() -> Self {
        let secreto = EphemeralSecret::random(&mut OsRng);
        let publica = EncodedPoint::from(secreto.public_key()).as_bytes().to_vec();
        Self { secreto, publica }
    }

    /// Segredo compartilhado (coordenada x, 32 bytes — igual ao `KeyAgreement` "ECDH" do Java).
    pub fn acordo(&self, publica_outro: &[u8]) -> Result<Vec<u8>, String> {
        let outro = PublicKey::from_sec1_bytes(publica_outro).map_err(|_| "chave pública inválida".to_string())?;
        Ok(self.secreto.diffie_hellman(&outro).raw_secret_bytes().to_vec())
    }
}

/// Uma direção do canal cifrado. O contador nunca volta: repetição ou reordenação não decifra.
pub struct Selador {
    cifra: Aes256Gcm,
    prefixo: [u8; 4],
    contador: u64,
}

impl Selador {
    pub fn novo(chave: &[u8], prefixo: [u8; 4]) -> Self {
        Self { cifra: Aes256Gcm::new_from_slice(chave).expect("chave de 32 bytes"), prefixo, contador: 0 }
    }

    fn proximo_nonce(&mut self) -> [u8; 12] {
        let mut n = [0u8; 12];
        n[..4].copy_from_slice(&self.prefixo);
        n[4..].copy_from_slice(&self.contador.to_be_bytes());
        self.contador += 1;
        n
    }

    pub fn selar(&mut self, claro: &[u8]) -> Vec<u8> {
        let n = self.proximo_nonce();
        self.cifra.encrypt(&Nonce::from(n), claro).expect("AES-GCM não falha ao cifrar")
    }

    pub fn abrir(&mut self, cifrado: &[u8]) -> Result<Vec<u8>, String> {
        let n = self.proximo_nonce();
        self.cifra
            .decrypt(&Nonce::from(n), cifrado)
            .map_err(|_| "quadro não autêntico".to_string())
    }
}

pub fn hex(b: &[u8]) -> String {
    b.iter().map(|x| format!("{x:02x}")).collect()
}

#[cfg(test)]
mod testes {
    use super::*;

    // Vetores fixos: os mesmos estão em app/src/test/.../conexao/CriptoTest.kt.
    const Z: [u8; 32] = [7u8; 32];
    const NC: [u8; 32] = [1u8; 32];
    const NN: [u8; 32] = [2u8; 32];

    #[test]
    fn vetores_iguais_aos_do_android() {
        let k = chave_par(&Z, &NC, &NN);
        let (c2n, n2c) = chaves_sessao(&k, &NC, &NN);
        let mut s = Selador::novo(&c2n, PREFIXO_C2N);
        let quadro = s.selar(b"\x01{\"t\":\"ping\"}");
        assert_eq!(hex(&k), K_ESPERADA);
        assert_eq!(codigo(&Z, &NC, &NN), CODIGO_ESPERADO);
        assert_eq!(hex(&quadro), QUADRO_ESPERADO);
        assert_ne!(c2n, n2c);
    }

    // Calculados de forma independente (Python: hmac/hashlib + cryptography.AESGCM).
    const K_ESPERADA: &str = "c3b60285360b1260164f69fae59493e1a9ead4191e478f535356fd1e30bb78f4";
    const CODIGO_ESPERADO: &str = "634289";
    const QUADRO_ESPERADO: &str = "10f370b5f663b4f9ba7a4a656a44acda70164cae4f6b867c3cc9735b85";

    #[test]
    fn ida_e_volta_e_rejeita_repeticao() {
        let chave = aleatorio(32);
        let mut a = Selador::novo(&chave, PREFIXO_N2C);
        let mut b = Selador::novo(&chave, PREFIXO_N2C);
        let q1 = a.selar(b"um");
        let q2 = a.selar(b"dois");
        assert_eq!(b.abrir(&q1).unwrap(), b"um");
        assert!(b.abrir(&q1).is_err(), "repetir o quadro não pode decifrar");
        let _ = q2;
    }

    #[test]
    fn ecdh_dos_dois_lados_da_o_mesmo_codigo() {
        let (a, b) = (Efemera::nova(), Efemera::nova());
        let za = a.acordo(&b.publica).unwrap();
        let zb = b.acordo(&a.publica).unwrap();
        assert_eq!(za, zb);
        assert_eq!(a.publica.len(), 65);
        assert_eq!(codigo(&za, &NC, &NN), codigo(&zb, &NC, &NN));
    }

    #[test]
    fn compromisso_detecta_troca_de_chave() {
        let a = Efemera::nova();
        let c = compromisso(&a.publica, &NC);
        assert_ne!(c, compromisso(&Efemera::nova().publica, &NC));
    }
}
