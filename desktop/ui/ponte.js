// Ponte com o Rust (comandos e eventos do Tauri), endereços songv:// e utilidades comuns.

export const tauri = window.__TAURI__;
export const invoke = tauri ? (cmd, args) => tauri.core.invoke(cmd, args) : async () => null;
export const ouvir = tauri ? (ev, fn) => tauri.event.listen(ev, fn) : async () => () => {};

// No Windows o WebView2 expõe protocolos próprios como http://<nome>.localhost.
const BASE = navigator.userAgent.includes("Windows") ? "http://songv.localhost/" : "songv://localhost/";
const e = encodeURIComponent;

export const url = {
  local: (id) => `${BASE}local/${e(id)}`,
  celular: (id) => `${BASE}audio/${e(id)}`,
  capaLocal: (id) => `${BASE}capa-local/${e(id)}`,
  capaCelular: (id, v) => `${BASE}capa/${e(id)}?v=${v}`,
};

export const $ = (id) => document.getElementById(id);

export function tempo(ms) {
  if (!Number.isFinite(ms) || ms < 0) ms = 0;
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const seg = String(s % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${seg}` : `${m}:${seg}`;
}

export function duracaoLonga(ms) {
  const min = Math.round(ms / 60000);
  return min >= 60 ? `${Math.floor(min / 60)} h ${min % 60} min` : `${min} min`;
}

export const plural = (n, um, varios = um + "s") => `${n} ${n === 1 ? um : varios}`;

/** Sem acentos e em minúsculas: "voce" encontra "Você". */
export const normalizar = (s) => (s || "").normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();

export function iniciais(texto) {
  return (texto || "")
    .split(/[\s\-_]+/)
    .filter((p) => /^[\p{L}\p{N}]/u.test(p))
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join("");
}

const TONS = ["#4a2e22", "#2d3b34", "#2b3346", "#4a2b30", "#3b3352", "#4b3d1f", "#23393e", "#3a3431"];
export function tom(semente) {
  let h = 0;
  for (const ch of semente || "") h = (h * 31 + ch.charCodeAt(0)) | 0;
  return TONS[Math.abs(h) % TONS.length];
}

export function el(tag, props = {}, ...filhos) {
  const n = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v == null || v === false) continue;
    if (k === "class") n.className = v;
    else if (k === "style") n.style.cssText = v;
    else if (k.startsWith("on")) n.addEventListener(k.slice(2), v);
    else if (k in n && k !== "list") n[k] = v;
    else n.setAttribute(k, v === true ? "" : v);
  }
  n.append(...filhos.flat().filter((f) => f != null && f !== false));
  return n;
}

let avisoTimer = 0;
export function avisar(texto) {
  const a = $("aviso");
  a.textContent = texto;
  a.classList.add("visivel");
  clearTimeout(avisoTimer);
  avisoTimer = setTimeout(() => a.classList.remove("visivel"), 3800);
}

// ---- capas do celular: pedidas sob demanda, chegam pelo evento "capa" ----

const versoesCapa = new Map();
const esperando = new Map();
const pedidas = new Set();

export function capaDoCelular(id, img) {
  const v = versoesCapa.get(id);
  if (v) {
    img.src = url.capaCelular(id, v);
    return;
  }
  if (!esperando.has(id)) esperando.set(id, new Set());
  esperando.get(id).add(img);
  if (!pedidas.has(id)) {
    pedidas.add(id);
    invoke("pedir_celular", { pedido: "capa", faixa: id });
  }
}

export function capaChegou(id, versao) {
  versoesCapa.set(id, versao);
  for (const img of esperando.get(id) || []) img.src = url.capaCelular(id, versao);
  esperando.delete(id);
}

export function esquecerCapasDoCelular() {
  versoesCapa.clear();
  esperando.clear();
  pedidas.clear();
}

const observador = new IntersectionObserver((entradas) => {
  for (const en of entradas) {
    if (!en.isIntersecting) continue;
    observador.unobserve(en.target);
    const { fonte, id } = en.target.dataset;
    const img = en.target.querySelector("img");
    if (fonte === "celular") capaDoCelular(id, img);
    else img.src = url.capaLocal(id);
  }
}, { rootMargin: "200px" });

/**
 * Capa de uma faixa: capa gerada (cor do álbum, iniciais, sulcos) com a imagem por cima quando
 * existir. A imagem só é pedida quando a capa aparece na tela.
 */
export function capa(fonte, faixa, rotulo, classe = "") {
  const semente = faixa?.album || faixa?.titulo || rotulo || "SongV";
  const img = el("img", {
    alt: "",
    decoding: "async",
    crossOrigin: "anonymous", // o palco lê a cor da capa (o protocolo songv:// libera CORS)
    onload: (ev) => ev.target.classList.add("carregada"),
    onerror: (ev) => ev.target.remove(),
  });
  const c = el("div", { class: `capa ${classe}`, style: `--capa-cor:${tom(semente)}` }, el("span", { class: "iniciais", "aria-hidden": "true" }, iniciais(rotulo || semente)), img);
  if (faixa) {
    c.dataset.fonte = fonte;
    c.dataset.id = faixa.id;
    observador.observe(c);
  }
  return c;
}
