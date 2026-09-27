// O player do computador. Dois modos:
//  - próprio: a fila é daqui; as faixas vêm da pasta do computador ou da biblioteca do celular
//    (os bytes chegam pela rede, cifrados);
//  - receptor: o celular escolheu este computador em "Tocar em…" e manda na fila — os botões
//    daqui viram comandos para ele.

import { $, invoke, url, tempo, capa, avisar } from "./ponte.js";
import { mostrarLetra, sincronizarLetra, letraDe } from "./letra.js";

const audio = $("audio");
const corpo = document.body;
const marcar = (nome, sim) => { corpo.dataset[nome] = sim ? "sim" : "nao"; };

export const player = {
  fila: [], // [{ fonte: "pc" | "celular", f }]
  ordem: [], // índices da fila na ordem em que vão tocar
  pos: -1, // posição em `ordem`
  aleatorio: false,
  repetir: 0, // 0 = não, 1 = a fila, 2 = a faixa
  receptor: null, // { celular, faixa, proxima } enquanto o celular manda o som
  ouvintes: new Set(),
};

const avisarMudanca = () => player.ouvintes.forEach((fn) => fn());

export function itemAtual() {
  if (player.receptor) return { fonte: "celular", f: player.receptor.faixa, receptor: true };
  return player.fila[player.ordem[player.pos]] ?? null;
}

const meta = (f) => f && ({ id: f.id, titulo: f.titulo, artista: f.artista, album: f.album, duracaoMs: f.duracaoMs, tamanho: f.tamanho, formato: f.formato });

function embaralhar(n, primeiro) {
  const r = [...Array(n).keys()].filter((i) => i !== primeiro);
  for (let i = r.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [r[i], r[j]] = [r[j], r[i]];
  }
  return primeiro >= 0 ? [primeiro, ...r] : r;
}

// ---------------------------------------------------------------- fila própria

/** Toca uma lista (álbum, busca, todas as faixas) a partir de `inicio`. */
export function tocarLista(fonte, faixas, inicio = 0, { aleatorio = player.aleatorio } = {}) {
  if (!faixas.length) return;
  sairDoReceptor(true);
  player.fila = faixas.map((f) => ({ fonte, f }));
  player.aleatorio = aleatorio;
  player.ordem = aleatorio ? embaralhar(faixas.length, inicio) : [...faixas.keys()];
  player.pos = aleatorio ? 0 : inicio;
  mostrarModos();
  carregar(true);
}

function carregar(tocar, posicaoMs = 0) {
  const item = itemAtual();
  if (!item) return;
  $("tocar").disabled = false;
  const { fonte, f } = item;
  if (fonte === "celular") {
    const prox = proximoItem();
    invoke("preparar_celular", { atual: meta(f), proxima: prox?.fonte === "celular" ? meta(prox.f) : null });
    audio.src = url.celular(f.id);
  } else {
    audio.src = url.local(f.id);
  }
  if (posicaoMs) audio.currentTime = posicaoMs / 1000;
  mostrarFaixa(item);
  carregarLetra(item);
  lembrar(item);
  if (tocar) tentarTocar();
  avisarMudanca();
}

function carregarLetra({ fonte, f }) {
  mostrarLetra(null, "", true);
  if (fonte === "celular") {
    invoke("pedir_celular", { pedido: "letra", faixa: f.id });
    return;
  }
  invoke("letra_local", { id: f.id }).then((l) => {
    if (itemAtual()?.f.id !== f.id) return;
    if (l) mostrarLetra({ faixa: f.id, atrasoMs: 0, ...l });
    else mostrarLetra(null, "Sem letra para esta faixa.");
  });
}

/** Letra que chegou do celular (a faixa que ele manda tocar, ou uma da biblioteca dele). */
export function letraDoCelular(l) {
  const atual = itemAtual();
  if (atual?.f.id !== l.faixa) return;
  if (l.linhas?.length) mostrarLetra(l);
  else mostrarLetra(null, "Sem letra para esta faixa.");
  sincronizarLetra(audio.currentTime * 1000, true);
}

function proximoItem() {
  const { ordem, pos, repetir } = player;
  if (repetir === 2) return itemAtual();
  const i = pos + 1 < ordem.length ? ordem[pos + 1] : repetir === 1 ? ordem[0] : null;
  return i == null ? null : player.fila[i];
}

export function proxima() {
  if (player.receptor) return invoke("comando", { acao: "proxima" });
  if (!player.fila.length) return;
  if (player.pos + 1 < player.ordem.length) player.pos++;
  else if (player.repetir === 1) player.pos = 0;
  else return parar();
  carregar(true);
}

export function anterior() {
  if (player.receptor) return invoke("comando", { acao: "anterior" });
  if (audio.currentTime > 3 || player.pos <= 0) {
    audio.currentTime = 0;
    return;
  }
  player.pos--;
  carregar(true);
}

function parar() {
  audio.pause();
  audio.currentTime = 0;
}

export function alternarAleatorio() {
  if (player.receptor) return;
  const atual = player.ordem[player.pos];
  player.aleatorio = !player.aleatorio;
  if (player.fila.length) {
    player.ordem = player.aleatorio ? embaralhar(player.fila.length, atual) : [...player.fila.keys()];
    player.pos = player.aleatorio ? 0 : atual;
  }
  mostrarModos();
}

export function alternarRepetir() {
  if (player.receptor) return;
  player.repetir = (player.repetir + 1) % 3;
  mostrarModos();
}

function mostrarModos() {
  $("aleatorio").setAttribute("aria-pressed", player.aleatorio);
  const r = $("repetir");
  r.setAttribute("aria-pressed", player.repetir > 0);
  r.dataset.modo = player.repetir === 2 ? "um" : "fila";
  r.setAttribute("aria-label", ["Repetir: desligado", "Repetir a fila", "Repetir a faixa"][player.repetir]);
  try { localStorage.setItem("modos", JSON.stringify({ a: player.aleatorio, r: player.repetir })); } catch {}
}

// ---------------------------------------------------------------- comandos comuns

function tentarTocar() {
  audio.play().catch(() => {});
}

export function alternar() {
  if (!itemAtual()) return;
  if (audio.paused) {
    tentarTocar();
    if (player.receptor) invoke("comando", { acao: "retomar" });
  } else {
    audio.pause();
    if (player.receptor) invoke("comando", { acao: "pausar" });
  }
}

export function buscar(ms) {
  if (!itemAtual()) return;
  audio.currentTime = ms / 1000;
  if (player.receptor) invoke("comando", { acao: "buscar", posicaoMs: Math.round(ms) });
  sincronizarLetra(ms, true);
}

// ---------------------------------------------------------------- modo receptor

/** "tocar" vindo do celular: ele passou a mandar o som para cá. */
export function receberDoCelular(msg, nomeCelular) {
  const f = msg.faixa;
  const entrando = !player.receptor;
  const mesma = player.receptor?.faixa.id === f.id;
  player.receptor = { celular: nomeCelular, faixa: f, proxima: msg.proxima || null };
  marcar("receptor", true);
  $("painel-origem").textContent = `Do ${nomeCelular}`;
  if (!mesma) {
    audio.src = url.celular(f.id);
    audio.currentTime = (msg.posicaoMs || 0) / 1000;
    mostrarFaixa({ fonte: "celular", f });
    if (letraDe() !== f.id) mostrarLetra(null, "", true);
  } else if (Math.abs(audio.currentTime * 1000 - (msg.posicaoMs || 0)) > 1500) {
    audio.currentTime = msg.posicaoMs / 1000;
  }
  if (msg.tocando) tentarTocar();
  else audio.pause();
  if (entrando) corpo.dataset.palco = "sim";
  informar();
  avisarMudanca();
}

export function comandoDoCelular(tipo, payload) {
  if (!player.receptor) return;
  if (tipo === "pausar") audio.pause();
  else if (tipo === "retomar") tentarTocar();
  else if (tipo === "buscar" && Math.abs(audio.currentTime * 1000 - payload.posicaoMs) > 400) audio.currentTime = payload.posicaoMs / 1000;
  else if (tipo === "volume") {
    audio.volume = Math.min(1, Math.max(0, payload.volume));
    $("volume").value = Math.round(audio.volume * 100);
    $("volume").style.setProperty("--p", `${audio.volume * 100}%`);
  }
}

/** O celular voltou a tocar nele mesmo (ou caiu): para o som daqui. */
export function sairDoReceptor(avisarCelular = false) {
  if (!player.receptor) return;
  if (avisarCelular) invoke("saida_livre");
  player.receptor = null;
  marcar("receptor", false);
  audio.pause();
  audio.removeAttribute("src");
  audio.load();
  // O load() descarta o evento "pause" ainda na fila: marca na mão.
  marcar("tocando", false);
  marcar("carregando", false);
  if (!avisarCelular) corpo.dataset.palco = "nao";
  const item = itemAtual();
  if (item) {
    // A fila daqui continua onde estava, pausada.
    carregar(false);
  } else {
    marcar("faixa", false);
    $("painel-titulo").textContent = "Nada tocando";
    $("painel-artista").textContent = "Escolha uma música";
    mostrarLetra(null);
  }
  avisarMudanca();
}

let ultimoInforme = 0;
function informar() {
  const r = player.receptor;
  if (!r) return;
  ultimoInforme = performance.now();
  invoke("estado", {
    faixa: r.faixa.id,
    posicaoMs: Math.round(audio.currentTime * 1000),
    tocando: !audio.paused,
    carregando: audio.readyState < 3 && !audio.paused,
    volume: audio.volume,
  });
}
setInterval(() => { if (performance.now() - ultimoInforme > 900) informar(); }, 1000);

// ---------------------------------------------------------------- tela

function trocarCapa(id, fonte, f, classe) {
  const nova = capa(fonte, f, f.album || f.titulo, classe);
  nova.id = id;
  $(id).replaceWith(nova);
  return nova;
}

function mostrarFaixa({ fonte, f }) {
  marcar("faixa", true);
  $("painel-titulo").textContent = f.titulo || "Sem título";
  $("painel-artista").textContent = f.artista || "";
  $("palco-titulo").textContent = f.titulo || "Sem título";
  $("palco-artista").textContent = [f.artista, f.album].filter(Boolean).join(" · ");
  $("total").textContent = tempo(f.duracaoMs);
  document.title = `${f.titulo} — SongV`;
  trocarCapa("painel-capa", fonte, f, "");
  const grande = trocarCapa("palco-capa", fonte, f, "capa-grande");
  document.documentElement.style.setProperty("--tinta", "#1b1512");
  grande.querySelector("img")?.addEventListener("load", (e) => { tingir(e.target); sessaoDeMidia(f, e.target.src); });
  sessaoDeMidia(f, null);
}

/** Tinta do palco: a cor mais viva da capa, escurecida até o texto branco ter folga. */
function tingir(img) {
  try {
    const c = document.createElement("canvas");
    c.width = c.height = 24;
    const g = c.getContext("2d", { willReadFrequently: true });
    g.drawImage(img, 0, 0, 24, 24);
    const px = g.getImageData(0, 0, 24, 24).data;
    let melhor = null;
    let peso = -1;
    for (let i = 0; i < px.length; i += 4) {
      const cor = [px[i], px[i + 1], px[i + 2]];
      const max = Math.max(...cor);
      const sat = max === 0 ? 0 : (max - Math.min(...cor)) / max;
      const p = sat * 2 + max / 255;
      if (p > peso) { peso = p; melhor = cor; }
    }
    if (!melhor) return;
    const t = melhor.map((v, i) => Math.round(v * 0.32 + [17, 14, 13][i] * 0.68));
    document.documentElement.style.setProperty("--tinta", `rgb(${t.join(" ")})`);
  } catch {
    // Capa sem CORS: fica a tinta padrão.
  }
}

function sessaoDeMidia(f, arte) {
  if (!("mediaSession" in navigator)) return;
  navigator.mediaSession.metadata = new MediaMetadata({
    title: f.titulo, artist: f.artista, album: f.album,
    artwork: arte ? [{ src: arte, sizes: "512x512" }] : [],
  });
}

// ---------------------------------------------------------------- histórico ("Tocadas recentemente")

function lembrar({ fonte, f }) {
  try {
    const h = JSON.parse(localStorage.getItem("recentes") || "[]").filter((r) => !(r.id === f.id && r.fonte === fonte));
    h.unshift({ fonte, id: f.id });
    localStorage.setItem("recentes", JSON.stringify(h.slice(0, 24)));
  } catch {}
}

export function recentes() {
  try { return JSON.parse(localStorage.getItem("recentes") || "[]"); } catch { return []; }
}

// ---------------------------------------------------------------- <audio>

const barra = $("barra");
let arrastando = false;

export function iniciarPlayer() {
  try {
    const m = JSON.parse(localStorage.getItem("modos") || "null");
    if (m) { player.aleatorio = !!m.a; player.repetir = m.r | 0; }
    const v = parseFloat(localStorage.getItem("volume"));
    if (v >= 0 && v <= 1) audio.volume = v;
  } catch {}
  mostrarModos();
  const vol = $("volume");
  vol.value = Math.round(audio.volume * 100);
  vol.style.setProperty("--p", `${vol.value}%`);
  vol.addEventListener("input", () => {
    audio.volume = vol.value / 100;
    vol.style.setProperty("--p", `${vol.value}%`);
    try { localStorage.setItem("volume", String(audio.volume)); } catch {}
  });

  barra.addEventListener("input", () => {
    arrastando = true;
    barra.style.setProperty("--p", `${barra.value / 10}%`);
    const d = itemAtual()?.f.duracaoMs || audio.duration * 1000 || 0;
    $("agora").textContent = tempo((barra.value / 1000) * d);
  });
  barra.addEventListener("change", () => {
    arrastando = false;
    const d = itemAtual()?.f.duracaoMs || audio.duration * 1000 || 0;
    buscar((barra.value / 1000) * d);
  });

  audio.addEventListener("timeupdate", () => {
    const pos = audio.currentTime * 1000;
    sincronizarLetra(pos);
    if (arrastando) return;
    const d = itemAtual()?.f.duracaoMs || audio.duration * 1000 || 0;
    $("agora").textContent = tempo(pos);
    const p = d > 0 ? Math.min(1, pos / d) : 0;
    barra.value = Math.round(p * 1000);
    barra.style.setProperty("--p", `${p * 100}%`);
  });
  audio.addEventListener("play", () => { marcar("tocando", true); informar(); avisarMudanca(); });
  audio.addEventListener("pause", () => { marcar("tocando", false); marcar("carregando", false); informar(); avisarMudanca(); });
  audio.addEventListener("waiting", () => marcar("carregando", true));
  audio.addEventListener("playing", () => { marcar("carregando", false); informar(); });
  audio.addEventListener("seeked", () => { sincronizarLetra(audio.currentTime * 1000, true); informar(); });
  audio.addEventListener("error", () => {
    if (!audio.getAttribute("src")) return;
    marcar("carregando", false);
    avisar(itemAtual()?.fonte === "celular" ? "Não consegui receber essa faixa do celular." : "Não consegui abrir esse arquivo.");
  });
  audio.addEventListener("ended", () => {
    const r = player.receptor;
    if (r) {
      // Terminou aqui: avisa o celular e já emenda a próxima (ele confirma em seguida).
      invoke("terminou", { faixa: r.faixa.id });
      if (r.proxima) receberDoCelular({ faixa: r.proxima, posicaoMs: 0, tocando: true, proxima: null }, r.celular);
      return;
    }
    if (player.repetir === 2) {
      audio.currentTime = 0;
      tentarTocar();
    } else {
      proxima();
    }
  });

  if ("mediaSession" in navigator) {
    navigator.mediaSession.setActionHandler("play", () => audio.paused && alternar());
    navigator.mediaSession.setActionHandler("pause", () => !audio.paused && alternar());
    navigator.mediaSession.setActionHandler("previoustrack", anterior);
    navigator.mediaSession.setActionHandler("nexttrack", proxima);
    navigator.mediaSession.setActionHandler("seekto", (d) => buscar(d.seekTime * 1000));
  }
}
