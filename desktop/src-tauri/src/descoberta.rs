//! Anúncio na rede local (mDNS / DNS-SD): o celular acha o computador sem digitar IP.

use mdns_sd::{ServiceDaemon, ServiceInfo};

pub const TIPO: &str = "_songv._tcp.local.";

pub fn anunciar(id: &str, nome: &str, porta: u16) -> Option<ServiceDaemon> {
    let daemon = ServiceDaemon::new().ok()?;
    let host = format!("songv-{}.local.", &id[..8]);
    let propriedades = [("id", id), ("v", "1")];
    let info = ServiceInfo::new(TIPO, nome, &host, "", porta, &propriedades[..]).ok()?.enable_addr_auto();
    daemon.register(info).ok()?;
    Some(daemon)
}

/// IPv4 locais, para mostrar na tela (a conexão manual pelo celular usa um deles).
pub fn enderecos() -> Vec<String> {
    let mut ips: Vec<String> = if_addrs::get_if_addrs()
        .unwrap_or_default()
        .into_iter()
        .filter(|i| !i.is_loopback())
        .filter_map(|i| match i.ip() {
            std::net::IpAddr::V4(v4) if !v4.is_link_local() => Some(v4.to_string()),
            _ => None,
        })
        .collect();
    // Redes domésticas e o hotspot do celular primeiro; adaptadores virtuais (WSL, VPN) depois.
    ips.sort_by_key(|ip| !(ip.starts_with("192.168.") || ip.starts_with("10.")));
    ips.dedup();
    ips
}
