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

// ---- cor da capa: cabeçalhos e palco se tingem com ela ----

const lum = (c) => {
  const [r, g, b] = c.map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

/**
 * A cor que mais "manda" na capa (a mais frequente, com peso para as vivas), levada para a faixa
 * de luminância [min, max] para o texto branco por cima continuar legível. `null` se a imagem não
 * puder ser lida.
 */
export function corDaCapa(img, min = 0.02, max = 0.09) {
  try {
    const c = document.createElement("canvas");
    c.width = c.height = 24;
    const g = c.getContext("2d", { willReadFrequently: true });
    g.drawImage(img, 0, 0, 24, 24);
    const px = g.getImageData(0, 0, 24, 24).data;
    const baldes = new Map();
    for (let i = 0; i < px.length; i += 4) {
      const cor = [px[i], px[i + 1], px[i + 2]];
      const k = (cor[0] >> 5) * 64 + (cor[1] >> 5) * 8 + (cor[2] >> 5);
      const alto = Math.max(...cor);
      const sat = alto === 0 ? 0 : (alto - Math.min(...cor)) / alto;
      const b = baldes.get(k) || { peso: 0, soma: [0, 0, 0], n: 0 };
      b.peso += 0.25 + sat;
      b.n++;
      cor.forEach((v, j) => { b.soma[j] += v; });
      baldes.set(k, b);
    }
    let melhor = null;
    for (const b of baldes.values()) if (!melhor || b.peso > melhor.peso) melhor = b;
    let cor = melhor.soma.map((v) => v / melhor.n);
    for (let i = 0; i < 40 && lum(cor) > max; i++) cor = cor.map((v) => v * 0.9);
    for (let i = 0; i < 40 && lum(cor) < min; i++) cor = cor.map((v) => v + (255 - v) * 0.06);
    return `rgb(${cor.map(Math.round).join(" ")})`;
  } catch {
    return null; // capa sem CORS
  }
}
