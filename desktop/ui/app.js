// SongV para computador — navegação, biblioteca lateral, telas, aparência, pareamento e eventos
// do Rust. As bibliotecas são duas: a deste computador e a do celular conectado.

import { $, invoke, ouvir, el, capa, tempo, duracaoLonga, plural, normalizar, avisar, capaChegou, esquecerCapasDoCelular, tauri, tom, corDaCapa } from "./ponte.js";
import { iniciarLetra } from "./letra.js";
import {
  player, itemAtual, tocarLista, proxima, anterior, alternar, buscar, alternarAleatorio, alternarRepetir,
  receberDoCelular, comandoDoCelular, sairDoReceptor, letraDoCelular, recentes, iniciarPlayer, proximas, pularPara,
} from "./player.js";

const corpo = document.body;
const conteudo = $("conteudo");
const principal = document.querySelector(".principal");

const estado = {
  tela: "inicio",
  param: null,
  historico: [],
  indice: -1,
  fonte: "pc",
  bib: { pc: [], celular: [] },
  derivados: { pc: null, celular: null },
  info: null,
  celular: null,
  varredura: null, // { lidas, total } enquanto lê a pasta
  pareando: null, // intervalo do prazo
  lateral: { tipo: null, ordem: "recentes", filtro: "" },
};

let pagina = null; // a coleção da tela aberta (o play grande e a barra fixa tocam ela)

// ------------------------------------------------------------------ dados derivados

const chaveAlbum = (f) => (f.album ? `${normalizar(f.artistaAlbum || f.artista)}|${normalizar(f.album)}` : `avulsa|${f.id}`);

function derivar(fonte) {
  if (estado.derivados[fonte]) return estado.derivados[fonte];
  const faixas = estado.bib[fonte];
  const albuns = new Map();
  const artistas = new Map();
  for (const f of faixas) {
    const chave = chaveAlbum(f);
    if (!albuns.has(chave)) albuns.set(chave, { chave, titulo: f.album || f.titulo, artista: f.artistaAlbum || f.artista || "Artista desconhecido", ano: f.ano, faixas: [] });
    const a = albuns.get(chave);
    a.faixas.push(f);
    a.ano = a.ano || f.ano;
    const nome = f.artista || "Artista desconhecido";
    if (!artistas.has(nome)) artistas.set(nome, { nome, faixas: [] });
    artistas.get(nome).faixas.push(f);
  }
  const porFaixa = (x, y) => (x.disco || 1) - (y.disco || 1) || (x.faixa || 999) - (y.faixa || 999) || x.titulo.localeCompare(y.titulo, "pt-BR");
  const recente = (xs) => Math.max(0, ...xs.map((f) => f.adicionada || 0));
  const lista = [...albuns.values()];
  for (const a of lista) {
    a.faixas.sort(porFaixa);
    a.duracaoMs = a.faixas.reduce((s, f) => s + f.duracaoMs, 0);
    a.adicionada = recente(a.faixas);
  }
  const listaArtistas = [...artistas.values()];
  for (const ar of listaArtistas) ar.adicionada = recente(ar.faixas);
  const d = {
    faixas: [...faixas].sort((x, y) => x.titulo.localeCompare(y.titulo, "pt-BR")),
    albuns: lista.sort((x, y) => x.titulo.localeCompare(y.titulo, "pt-BR")),
    artistas: listaArtistas.sort((x, y) => x.nome.localeCompare(y.nome, "pt-BR")),
    porId: new Map(faixas.map((f) => [f.id, f])),
    porChave: albuns,
    porArtista: artistas,
  };
  estado.derivados[fonte] = d;
  return d;
}

function definirBiblioteca(fonte, faixas) {
  estado.bib[fonte] = faixas || [];
  estado.derivados[fonte] = null;
  if (fonte === estado.fonte || estado.tela === "config") desenhar();
  if (fonte === estado.fonte) desenharLateral();
}

const nomeFonte = (fonte) => (fonte === "celular" ? estado.celular || "Celular" : "Este computador");

// Coleções tocáveis: o que um cartão, um item da lateral ou um cabeçalho representam.
function colAlbum(fonte, a) {
  return { origem: `album:${fonte}:${a.chave}`, nome: a.titulo, faixas: a.faixas, fonte, ir: () => ir("album", a.chave), capa: () => capa(fonte, a.faixas[0], a.titulo) };
}
function colArtista(fonte, ar) {
  return { origem: `artista:${fonte}:${ar.nome}`, nome: ar.nome, faixas: ar.faixas, fonte, ir: () => ir("artista", ar.nome), capa: () => capa(fonte, ar.faixas[0], ar.nome), redondo: true };
}
function colTodas(fonte) {
  return { origem: `faixas:${fonte}`, nome: "Todas as faixas", faixas: derivar(fonte).faixas, fonte, ir: () => ir("faixas"), capa: () => tileTodas() };
}

function tocarColecao(c, inicio = null, aleatorio = player.aleatorio) {
  if (!c.faixas.length) return;
  const i = inicio ?? (aleatorio ? Math.floor(Math.random() * c.faixas.length) : 0);
  tocarLista(c.fonte, c.faixas, i, { aleatorio, origem: { chave: c.origem, nome: c.nome } });
}

const tocandoDaqui = (origem) => !player.receptor && player.origem?.chave === origem;

// ------------------------------------------------------------------ peças

const ICONES = {
  tocar: "M8.5 5.6v12.8a1 1 0 0 0 1.53.85l10.1-6.4a1 1 0 0 0 0-1.7l-10.1-6.4A1 1 0 0 0 8.5 5.6z",
  pausar: "M7 5h3.5v14H7zm6.5 0H17v14h-3.5z",
  aleatorio: "M17 4.5 20.5 8 17 11.5V9h-1.6a3 3 0 0 0-2.5 1.34L8.8 16.4A5 5 0 0 1 4.6 18.6H3v-2h1.6a3 3 0 0 0 2.5-1.34l4.1-6.06A5 5 0 0 1 15.4 7H17zM3 7h1.6a5 5 0 0 1 3.7 1.66L7 10.2A3 3 0 0 0 4.6 9H3zm14 7.5 3.5 3.5-3.5 3.5V19h-1.6a5 5 0 0 1-3.7-1.66l1.3-1.54A3 3 0 0 0 15.4 17H17z",
  pasta: "M3 5h7l2 2h9v12H3z",
  nota: "M12 3v10.55A4 4 0 1 0 14 17V7h4V3z",
  relogio: "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm0 2a8 8 0 1 1 0 16 8 8 0 0 1 0-16zm-1 3v6l5 3 1-1.7-4-2.3V7z",
  som: "M4 9v6h4l5 4V5L8 9H4zm12.5 3a4.5 4.5 0 0 0-2.5-4v8a4.5 4.5 0 0 0 2.5-4zM14 3.2v2.1a7 7 0 0 1 0 13.4v2.1a9 9 0 0 0 0-17.6z",
  computador: "M3 4h18a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1h-7v2h3v2H7v-2h3v-2H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zm1 2v9h16V6z",
  celular: "M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zm0 3v13h8V5zm3 14.5v1h2v-1z",
};

const molde = document.createElement("template");
function svg(nome, classe = "") {
  molde.innerHTML = `<svg viewBox="0 0 24 24" aria-hidden="true"${classe ? ` class="${classe}"` : ""}><path d="${ICONES[nome]}"/></svg>`;
  return molde.content.firstChild;
}

function botao(texto, aoClicar, { cheio = false, clara = false, icone = null } = {}) {
  return el("button", { type: "button", class: `tecla${cheio ? " cheia" : ""}${clara ? " clara" : ""}`, onclick: aoClicar }, icone ? svg(icone) : null, texto);
}

const tileTodas = (classe = "") => el("div", { class: `capa-todas tile-todas ${classe}` }, svg("nota"));

/** Play redondo no sinal: toca a coleção ou, se ela já está tocando, pausa/retoma. */
function botaoPlay(c, classe = "") {
  return el("button", {
    type: "button",
    class: `play-grande ${classe}`,
    "data-origem": c.origem,
    "data-nome": c.nome,
    "aria-label": `Tocar ${c.nome}`,
    onclick: (e) => {
      e.stopPropagation();
      if (tocandoDaqui(c.origem)) alternar();
      else tocarColecao(c);
    },
  }, svg("tocar", "i-tocar"), svg("pausar", "i-pausar"));
}

function cartao(c, sub) {
  return el("div", { class: `cartao${c.redondo ? " redondo-capa" : ""}`, onclick: c.ir },
    el("div", { class: "capa-caixa" }, c.capa(), botaoPlay(c, "flutuante")),
    el("button", { type: "button", class: "cartao-titulo", title: c.nome }, el("b", {}, c.nome)),
    el("small", {}, sub));
}

const subAlbum = (a) => [a.ano, a.artista].filter(Boolean).join(" • ");

function secao(titulo, itens, destino = null) {
  if (!itens.length) return null;
  return el("section", { class: "secao" },
    el("div", { class: "secao-cabeca" },
      el("h2", {}, titulo),
      destino ? el("button", { type: "button", class: "mostrar-tudo", onclick: () => ir(destino) }, "Mostrar tudo") : null),
    el("div", { class: "grade fileira" }, itens));
}

const barras = () => el("span", { class: "tocando-agora", "aria-hidden": "true" }, el("i"), el("i"), el("i"));

let selecionada = null;

/** Tabela de faixas: duplo clique (ou Enter) toca a coleção a partir dali. */
function tabela(c, { comAlbum = true, comCapa = true, numeroDoAlbum = false, cabecalho = true, limite = 0 } = {}) {
  const t = el("div", { class: `tabela${comAlbum ? "" : " sem-album"}`, role: "list" });
  if (cabecalho) {
    t.append(el("div", { class: "linha-faixa cabeca-tabela", "aria-hidden": "true" },
      el("span", { class: "n" }, "#"), el("span", {}, "Título"), el("span", { class: "col-album" }, "Álbum"), svg("relogio")));
  }
  const fonte = c.fonte;
  (limite ? c.faixas.slice(0, limite) : c.faixas).forEach((f, i) => {
    const tocar = () => {
      const atual = itemAtual();
      if (tocandoDaqui(c.origem) && atual?.f.id === f.id) alternar();
      else tocarColecao(c, i);
    };
    const link = (texto, destino) => el("button", { type: "button", class: "link-sutil", onclick: (e) => { e.stopPropagation(); destino(); } }, texto);
    t.append(el("div", {
      class: "linha-faixa",
      role: "listitem",
      tabindex: "0",
      "data-id": f.id,
      "data-fonte": fonte,
      ondblclick: tocar,
      onkeydown: (e) => { if (e.key === "Enter" && e.target === e.currentTarget) tocar(); },
      onclick: (e) => { selecionada?.classList.remove("selecionada"); selecionada = e.currentTarget; selecionada.classList.add("selecionada"); },
    },
    el("span", { class: "n numeros" },
      el("span", { class: "num" }, String(numeroDoAlbum ? f.faixa || i + 1 : i + 1)),
      barras(),
      el("button", { type: "button", class: "tocar-linha", "aria-label": `Tocar ${f.titulo}`, onclick: (e) => { e.stopPropagation(); tocar(); } }, svg("tocar", "i-tocar"), svg("pausar", "i-pausar"))),
    el("span", { class: "titulo" },
      comCapa ? capa(fonte, f, f.album || f.titulo) : null,
      el("span", {}, el("b", {}, f.titulo), el("small", {}, f.artista ? link(f.artista, () => ir("artista", f.artista)) : "Artista desconhecido"))),
    el("span", { class: "col-album" }, f.album ? link(f.album, () => ir("album", chaveAlbum(f))) : "—"),
    el("span", { class: "dur numeros" }, tempo(f.duracaoMs))));
  });
  return t;
}

/** Cabeçalho em degradê com a cor da capa + corpo com o play grande. */
function paginaColecao(c, { tipo, titulo = c.nome, capaEl, meta, cor }, ...filhos) {
  pagina = c;
  const partes = meta.filter(Boolean).map((m, i) => (i ? el("span", { class: "ponto" }, m) : m));
  return [
    el("header", { class: `cabeca${c.redondo ? " redondo-capa" : ""}`, "data-cor": cor },
      capaEl,
      el("div", { class: "cabeca-texto" },
        el("span", { class: "tipo" }, tipo),
        el("h1", { class: titulo.length > 28 || Math.max(...titulo.split(/\s+/).map((p) => p.length)) > 10 ? "longo" : "" }, titulo),
        el("div", { class: "meta" }, partes))),
    el("div", { class: "corpo-pagina" },
      el("div", { class: "acoes-pagina" },
        botaoPlay(c),
        el("button", {
          type: "button", class: "tecla-icone", "aria-label": `Tocar ${c.nome} em ordem aleatória`, title: "Aleatório",
          onclick: () => tocarColecao(c, null, true),
        }, svg("aleatorio"))),
      ...filhos),
  ];
}

function vazioDaFonte() {
  if (estado.fonte === "celular") {
    return el("div", { class: "vazio" }, el("h2", {}, `Nada no ${nomeFonte("celular")}`), el("p", {}, "O SongV do celular não tem músicas na pasta configurada."));
  }
  const v = estado.varredura;
  if (v) {
    return el("div", { class: "vazio" }, el("h2", {}, "Lendo suas músicas…"),
      el("p", { class: "numeros" }, v.total ? `${v.lidas} de ${v.total} arquivos` : "Procurando arquivos de áudio"));
  }
  return el("div", { class: "vazio" },
    el("h2", {}, "Nenhuma música neste computador"),
    el("p", {}, `O SongV procura em ${estado.info?.pasta || "Músicas"}. Escolha outra pasta ou conecte o celular para tocar as músicas dele.`),
    el("div", { class: "acoes" }, botao("Escolher pasta", escolherPasta, { cheio: true, icone: "pasta" }), botao("Conectar celular", () => abrirDispositivos($("botao-celular")))));
}

// ------------------------------------------------------------------ navegação

function ir(tela, param = null, { substituir = false } = {}) {
  const atual = estado.historico[estado.indice];
  if (!atual || atual[0] !== tela || atual[1] !== param) {
    if (substituir && atual) estado.historico[estado.indice] = [tela, param];
    else {
      estado.historico = estado.historico.slice(0, estado.indice + 1);
      estado.historico.push([tela, param]);
      estado.indice = estado.historico.length - 1;
    }
  }
  mostrar(tela, param);
}

function mover(passo) {
  const i = estado.indice + passo;
  if (i < 0 || i >= estado.historico.length) return;
  estado.indice = i;
  mostrar(...estado.historico[i]);
}

function mostrar(tela, param) {
  const mesmaBusca = tela === "buscar" && estado.tela === "buscar";
  estado.tela = tela;
  estado.param = param;
  if (!mesmaBusca) alternarPalco(false);
  const campo = $("busca");
  if (tela !== "buscar") campo.value = "";
  else if (campo.value !== param) campo.value = param;
  for (const b of document.querySelectorAll("[data-ir]")) {
    const atual = b.dataset.ir === tela || (b.dataset.ir === "navegar" && tela === "buscar");
    if (atual) b.setAttribute("aria-current", "page");
    else b.removeAttribute("aria-current");
  }
  $("voltar").disabled = estado.indice <= 0;
  $("avancar").disabled = estado.indice >= estado.historico.length - 1;
  desenhar();
  conteudo.scrollTop = 0;
}

function escolherFonte(fonte) {
  if (fonte === estado.fonte) return;
  estado.fonte = fonte;
  if (["album", "artista", "buscar"].includes(estado.tela)) ir("inicio");
  else desenhar();
  desenharLateral();
}

function desenhar() {
  const telas = { inicio: telaInicio, navegar: telaNavegar, buscar: telaBuscar, albuns: telaAlbuns, album: telaAlbum, artistas: telaArtistas, artista: telaArtista, faixas: telaFaixas, config: telaConfig };
  pagina = null;
  selecionada = null;
  conteudo.replaceChildren(...[].concat((telas[estado.tela] || telaInicio)()).filter(Boolean));
  tingirCabeca();
  ajustarFileiras();
  vigiarBarraFixa();
  marcarTocando();
  marcarLateral();
}

/** Cabeçalho com a cor da capa (a gerada primeiro, a da imagem quando ela chegar). */
function tingirCabeca() {
  const cab = conteudo.querySelector(".cabeca");
  const base = cab?.dataset.cor
    || (estado.tela === "inicio" ? "color-mix(in oklab, var(--sinal) 26%, var(--fundo))" : "color-mix(in oklab, var(--texto) 5%, var(--fundo))");
  principal.style.setProperty("--cor-cabeca", base);
  const img = cab?.querySelector(".capa img");
  img?.addEventListener("load", () => {
    const cor = corDaCapa(img);
    if (cor && conteudo.contains(img)) principal.style.setProperty("--cor-cabeca", cor);
  });
}

/** Fileiras do Início e da busca: uma linha só; o que não cabe fica para "Mostrar tudo". */
function ajustarFileiras() {
  const colunas = Math.max(2, Math.floor((conteudo.clientWidth - 48 + 24 + 4) / 180));
  conteudo.style.setProperty("--colunas", colunas);
  for (const g of conteudo.querySelectorAll(".grade.fileira")) [...g.children].forEach((c, i) => { c.hidden = i >= colunas; });
}
new ResizeObserver(ajustarFileiras).observe(conteudo);

// A barra fixa aparece quando o play grande sai de vista.
const vigia = new IntersectionObserver(([e]) => {
  $("barra-fixa").classList.toggle("visivel", !e.isIntersecting && e.boundingClientRect.bottom < (e.rootBounds?.top ?? 0) + 64);
}, { root: conteudo, rootMargin: "-64px 0px 0px 0px" });

function vigiarBarraFixa() {
  vigia.disconnect();
  $("barra-fixa").classList.remove("visivel");
  const alvo = conteudo.querySelector(".acoes-pagina .play-grande");
  if (!pagina || !alvo) return;
  $("fixa-titulo").textContent = pagina.nome;
  const fp = $("fixa-play");
  fp.dataset.origem = pagina.origem;
  fp.dataset.nome = pagina.nome;
  vigia.observe(alvo);
}
$("fixa-play").addEventListener("click", () => {
  if (!pagina) return;
  if (tocandoDaqui(pagina.origem)) alternar();
  else tocarColecao(pagina);
});

/** Destaca o que está tocando: linhas das tabelas, plays das coleções, itens da lateral. */
function marcarTocando() {
  const atual = itemAtual();
  const tocando = corpo.dataset.tocando === "sim";
  for (const l of document.querySelectorAll(".linha-faixa[data-id]")) {
    l.classList.toggle("ativa", !!atual && l.dataset.id === atual.f.id && l.dataset.fonte === atual.fonte);
  }
  for (const b of document.querySelectorAll(".play-grande[data-origem]")) {
    const pausavel = tocando && tocandoDaqui(b.dataset.origem);
    b.classList.toggle("pausavel", pausavel);
    b.setAttribute("aria-label", `${pausavel ? "Pausar" : "Tocar"} ${b.dataset.nome || ""}`.trim());
  }
  const chave = player.receptor ? null : player.origem?.chave;
  for (const i of $("bib-lista").children) i.classList.toggle("tocando", !!chave && i.dataset.chave === chave);
  mostrarFila();
}

let assinaturaFila = "";
function mostrarFila() {
  const lista = proximas(8);
  const assinatura = lista.map(({ item, pos }) => `${pos}:${item.fonte}:${item.f.id}`).join(",");
  if (assinatura === assinaturaFila) return;
  assinaturaFila = assinatura;
  $("dir-fila").replaceChildren(...lista.map(({ item, pos }) => el("button", {
    type: "button", class: "item-fila", role: "listitem", disabled: pos < 0, onclick: () => pularPara(pos),
  }, capa(item.fonte, item.f, item.f.album || item.f.titulo), el("span", {}, el("b", {}, item.f.titulo), el("small", {}, item.f.artista || "")))));
}

// ------------------------------------------------------------------ biblioteca (lateral)

function chip(texto, marcado, aoClicar) {
  return el("button", { type: "button", class: "chip", "aria-pressed": String(marcado), onclick: aoClicar }, texto);
}

function desenharLateral() {
  $("bib-fontes").replaceChildren(...(estado.celular
    ? [["pc", "Computador"], ["celular", estado.celular]].map(([f, nome]) => chip(nome, estado.fonte === f, () => escolherFonte(f)))
    : []));
  const lat = estado.lateral;
  $("bib-tipos").replaceChildren(...[["albuns", "Álbuns"], ["artistas", "Artistas"]].map(([t, nome]) =>
    chip(nome, lat.tipo === t, () => { lat.tipo = lat.tipo === t ? null : t; guardarLateral(); desenharLateral(); })));
  $("bib-ordem-rotulo").textContent = lat.ordem === "recentes" ? "Recentes" : "A–Z";

  const fonte = estado.fonte;
  const d = derivar(fonte);
  // "Recentes": o que tocou por último primeiro, depois o que chegou por último.
  const posRecente = new Map();
  recentes().filter((r) => r.fonte === fonte).forEach((r, i) => {
    const f = d.porId.get(r.id);
    if (!f) return;
    const ka = `album:${fonte}:${chaveAlbum(f)}`;
    const kr = `artista:${fonte}:${f.artista || "Artista desconhecido"}`;
    if (!posRecente.has(ka)) posRecente.set(ka, i);
    if (!posRecente.has(kr)) posRecente.set(kr, i);
  });
  let itens = [];
  if (lat.tipo !== "artistas") itens.push(...d.albuns.map((a) => ({ c: colAlbum(fonte, a), sub: `Álbum • ${a.artista}`, quando: a.adicionada })));
  if (lat.tipo !== "albuns") itens.push(...d.artistas.map((ar) => ({ c: colArtista(fonte, ar), sub: "Artista", quando: ar.adicionada })));
  const termo = normalizar(lat.filtro.trim());
  if (termo) itens = itens.filter((x) => normalizar(`${x.c.nome} ${x.sub}`).includes(termo));
  if (lat.ordem === "recentes") {
    itens.sort((x, y) => (posRecente.get(x.c.origem) ?? 1e9) - (posRecente.get(y.c.origem) ?? 1e9) || y.quando - x.quando);
  } else {
    itens.sort((x, y) => x.c.nome.localeCompare(y.c.nome, "pt-BR"));
  }
  if (!lat.tipo && !termo && d.faixas.length) itens.unshift({ c: colTodas(fonte), sub: `Playlist • ${plural(d.faixas.length, "música")}` });

  const lista = $("bib-lista");
  if (!itens.length) {
    lista.replaceChildren(termo
      ? el("p", { class: "bib-vazia" }, el("b", {}, "Nada encontrado"), `Nada com “${lat.filtro.trim()}” na sua biblioteca.`)
      : el("p", { class: "bib-vazia" }, el("b", {}, "Biblioteca vazia"), fonte === "pc" ? "Escolha a pasta de músicas nas configurações." : "O celular não mandou nenhuma música."));
    return;
  }
  lista.replaceChildren(...itens.map(({ c, sub }) => el("button", {
    type: "button", class: `item-bib${c.redondo ? " redondo-capa" : ""}`, role: "listitem", title: c.nome, "data-chave": c.origem, onclick: c.ir,
  }, c.origem.startsWith("faixas:") ? el("div", { class: "capa-todas" }, svg("nota")) : c.capa(), el("span", {}, el("b", {}, c.nome), el("small", {}, sub)), svg("som", "alto-falante"))));
  marcarLateral();
  marcarTocando();
}

function marcarLateral() {
  const f = estado.fonte;
  const chave = { album: `album:${f}:${estado.param}`, artista: `artista:${f}:${estado.param}`, faixas: `faixas:${f}` }[estado.tela];
  for (const i of $("bib-lista").children) {
    if (chave && i.dataset.chave === chave) i.setAttribute("aria-current", "true");
    else i.removeAttribute("aria-current");
  }
}

function guardarLateral() {
  try { localStorage.setItem("lateral", JSON.stringify({ tipo: estado.lateral.tipo, ordem: estado.lateral.ordem })); } catch {}
}

$("bib-ordem").addEventListener("click", () => {
  estado.lateral.ordem = estado.lateral.ordem === "recentes" ? "alfabetica" : "recentes";
  guardarLateral();
  desenharLateral();
});
$("bib-busca").querySelector(".icone-busca").addEventListener("click", (e) => {
  e.preventDefault();
  $("bib-busca").classList.add("aberta");
  $("bib-filtro").focus();
});
$("bib-filtro").addEventListener("input", (e) => { estado.lateral.filtro = e.target.value; desenharLateral(); });
$("bib-filtro").addEventListener("blur", (e) => { if (!e.target.value) $("bib-busca").classList.remove("aberta"); });

// ------------------------------------------------------------------ telas

function telaInicio() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const h = new Date().getHours();
  const saudacao = el("h1", { class: "saudacao" }, h >= 5 && h < 12 ? "Bom dia" : h >= 12 && h < 18 ? "Boa tarde" : "Boa noite");
  if (!d.faixas.length) return el("div", { class: "corpo-pagina" }, saudacao, vazioDaFonte());

  const tocados = [];
  for (const r of recentes()) {
    const f = r.fonte === fonte && d.porId.get(r.id);
    const a = f && d.porChave.get(chaveAlbum(f));
    if (a && !tocados.includes(a)) tocados.push(a);
  }
  const novos = [...d.albuns].sort((a, b) => b.adicionada - a.adicionada);
  const atalhos = [colTodas(fonte), ...[...new Set([...tocados, ...novos])].slice(0, 7).map((a) => colAlbum(fonte, a))];
  const artistas = [...d.artistas].sort((a, b) => b.faixas.length - a.faixas.length);

  return el("div", { class: "corpo-pagina" },
    saudacao,
    el("div", { class: "atalhos" }, atalhos.map((c) => el("div", { class: "atalho", onclick: c.ir },
      c.origem.startsWith("faixas:") ? tileTodas() : c.capa(),
      el("button", { type: "button", class: "atalho-titulo" }, el("b", {}, c.nome)),
      botaoPlay(c, "flutuante")))),
    secao("Tocados recentemente", tocados.slice(0, 12).map((a) => cartao(colAlbum(fonte, a), subAlbum(a)))),
    secao("Adicionados recentemente", novos.slice(0, 12).map((a) => cartao(colAlbum(fonte, a), subAlbum(a))), "albuns"),
    secao(fonte === "celular" ? `Artistas no ${nomeFonte(fonte)}` : "Seus artistas", artistas.slice(0, 12).map((ar) => cartao(colArtista(fonte, ar), "Artista")), "artistas"));
}

const CORES_TILE = ["#b0452d", "#7a4fb3", "#1f6f5c", "#b3316b", "#2d5fa8", "#8c6a12", "#5b3fa0", "#a8431f", "#2f7a3d", "#9b2d4d", "#3d5f8f", "#7a5a2e"];

function telaNavegar() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const tile = (nome, cor, aoClicar, f) => el("button", { type: "button", class: "tile-navegar", style: `--c:${cor}`, onclick: aoClicar },
    el("span", {}, nome), f ? capa(fonte, f, f.album || f.titulo) : null);
  const recente = [...d.albuns].sort((a, b) => b.adicionada - a.adicionada)[0];
  const artistas = [...d.artistas].sort((a, b) => b.faixas.length - a.faixas.length).slice(0, 12);
  return el("div", { class: "pagina-simples" },
    el("h1", {}, "Navegar por tudo"),
    d.faixas.length
      ? el("div", { class: "navegar-grade" },
        tile("Todas as faixas", "color-mix(in oklab, var(--sinal), #2b1a6b 45%)", () => ir("faixas"), d.faixas[0]),
        tile("Álbuns", CORES_TILE[0], () => ir("albuns"), recente?.faixas[0]),
        tile("Artistas", CORES_TILE[1], () => ir("artistas"), artistas[0]?.faixas[0]),
        artistas.map((ar, i) => tile(ar.nome, CORES_TILE[(i + 2) % CORES_TILE.length], () => ir("artista", ar.nome), ar.faixas[0])))
      : vazioDaFonte());
}

function telaBuscar() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const texto = (estado.param || "").trim();
  const termo = normalizar(texto);
  const bate = (...t) => t.some((x) => normalizar(x).includes(termo));
  const faixas = d.faixas.filter((f) => bate(f.titulo, f.artista, f.album)).slice(0, 80);
  const albuns = d.albuns.filter((a) => bate(a.titulo, a.artista));
  const artistas = d.artistas.filter((ar) => bate(ar.nome));
  if (!faixas.length && !albuns.length && !artistas.length) {
    return el("div", { class: "pagina-simples" }, el("div", { class: "vazio" },
      el("h2", {}, `Nada encontrado para “${texto}”`), el("p", {}, `Confira a grafia ou tente outra palavra. A busca olha a biblioteca d${fonte === "celular" ? "o celular" : "este computador"}.`)));
  }
  const cBusca = { origem: `busca:${fonte}:${termo}`, nome: `Busca por “${texto}”`, faixas, fonte };
  const comeca = (s) => normalizar(s).startsWith(termo);
  const artistaTopo = artistas.find((ar) => comeca(ar.nome));
  const albumTopo = albuns.find((a) => comeca(a.titulo));

  let melhor;
  if (artistaTopo || (!albumTopo && !faixas.length && artistas.length)) {
    const ar = artistaTopo || artistas[0];
    melhor = { c: colArtista(fonte, ar), etiqueta: "Artista", sub: plural(ar.faixas.length, "música") };
  } else if (albumTopo || !faixas.length) {
    const a = albumTopo || albuns[0];
    melhor = { c: colAlbum(fonte, a), etiqueta: "Álbum", sub: a.artista };
  } else {
    const f = faixas[0];
    melhor = { c: { ...cBusca, nome: f.titulo, capa: () => capa(fonte, f, f.album || f.titulo), ir: () => tocarColecao(cBusca, 0) }, etiqueta: "Música", sub: f.artista || "" };
  }
  const { c } = melhor;
  return el("div", { class: "corpo-pagina" },
    el("div", { class: "melhor" },
      el("section", {},
        el("div", { class: "secao-cabeca" }, el("h2", {}, "Melhor resultado")),
        el("div", { class: `melhor-cartao${c.redondo ? " redondo-capa" : ""}`, onclick: c.ir },
          c.capa(),
          el("div", {}, el("button", { type: "button", class: "cartao-titulo" }, el("b", {}, c.nome)), el("small", {}, melhor.sub, el("span", { class: "etiqueta" }, melhor.etiqueta))),
          botaoPlay(c, "flutuante"))),
      faixas.length ? el("section", {},
        el("div", { class: "secao-cabeca" }, el("h2", {}, "Músicas")),
        tabela(cBusca, { cabecalho: false, limite: 4 })) : null),
    secao("Artistas", artistas.slice(0, 12).map((ar) => cartao(colArtista(fonte, ar), "Artista"))),
    secao("Álbuns", albuns.slice(0, 12).map((a) => cartao(colAlbum(fonte, a), subAlbum(a)))),
    faixas.length > 4 ? el("section", { class: "secao" }, el("div", { class: "secao-cabeca" }, el("h2", {}, "Todas as músicas encontradas")), tabela(cBusca)) : null);
}

function telaAlbuns() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  return el("div", { class: "pagina-simples" },
    el("h1", {}, "Álbuns"),
    d.albuns.length ? el("div", { class: "grade" }, d.albuns.map((a) => cartao(colAlbum(fonte, a), subAlbum(a)))) : vazioDaFonte());
}

function telaArtistas() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  return el("div", { class: "pagina-simples" },
    el("h1", {}, "Artistas"),
    d.artistas.length ? el("div", { class: "grade" }, d.artistas.map((ar) => cartao(colArtista(fonte, ar), plural(ar.faixas.length, "música")))) : vazioDaFonte());
}

function naoEncontrado(oque) {
  return el("div", { class: "pagina-simples" }, el("div", { class: "vazio" },
    el("h2", {}, `${oque} não encontrado`), el("p", {}, "Ele pode ter saído da biblioteca."), el("div", { class: "acoes" }, botao("Ir para o início", () => ir("inicio")))));
}

function telaAlbum() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const a = d.porChave.get(estado.param);
  if (!a) return naoEncontrado("Álbum");
  const c = colAlbum(fonte, a);
  const artista = d.porArtista.get(a.artista);
  const outros = d.albuns.filter((x) => x !== a && x.artista === a.artista);
  return paginaColecao(c, {
    tipo: a.faixas.length > 1 ? "Álbum" : "Single",
    capaEl: c.capa(),
    cor: tom(a.titulo),
    meta: [
      artista ? el("button", { type: "button", onclick: () => ir("artista", a.artista) }, a.artista) : el("b", {}, a.artista),
      a.ano ? String(a.ano) : null,
      `${plural(a.faixas.length, "música")}, ${duracaoLonga(a.duracaoMs)}`,
    ],
  },
  tabela(c, { comAlbum: false, comCapa: false, numeroDoAlbum: true }),
  secao(`Mais de ${a.artista}`, outros.slice(0, 12).map((x) => cartao(colAlbum(fonte, x), x.ano ? String(x.ano) : "Álbum"))));
}

function telaArtista() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const ar = d.porArtista.get(estado.param);
  if (!ar) return naoEncontrado("Artista");
  const c = colArtista(fonte, ar);
  const albuns = d.albuns.filter((a) => a.faixas.some((f) => (f.artista || "Artista desconhecido") === ar.nome)).sort((x, y) => (y.ano || 0) - (x.ano || 0));
  const musicas = el("section", { class: "secao" }, el("div", { class: "secao-cabeca" }, el("h2", {}, "Músicas")), tabela(c, { cabecalho: false, limite: 5 }));
  if (ar.faixas.length > 5) {
    const mais = el("button", { type: "button", class: "tecla-texto", onclick: () => { musicas.querySelector(".tabela").replaceWith(tabela(c, { cabecalho: false })); mais.remove(); marcarTocando(); } }, "Ver mais");
    musicas.append(mais);
  }
  return paginaColecao(c, {
    tipo: "Artista",
    capaEl: c.capa(),
    cor: tom(ar.faixas[0]?.album || ar.nome),
    meta: [plural(albuns.length, "álbum", "álbuns"), plural(ar.faixas.length, "música")],
  },
  musicas,
  albuns.length ? el("section", { class: "secao" }, el("div", { class: "secao-cabeca" }, el("h2", {}, "Discografia")),
    el("div", { class: "grade" }, albuns.map((a) => cartao(colAlbum(fonte, a), [a.ano, a.faixas.length > 1 ? "Álbum" : "Single"].filter(Boolean).join(" • "))))) : null);
}

function telaFaixas() {
  const fonte = estado.fonte;
  const c = colTodas(fonte);
  if (!c.faixas.length) return el("div", { class: "pagina-simples" }, el("h1", {}, "Todas as faixas"), vazioDaFonte());
  const total = c.faixas.reduce((s, f) => s + f.duracaoMs, 0);
  return paginaColecao(c, {
    tipo: "Playlist",
    capaEl: tileTodas(),
    cor: "color-mix(in oklab, var(--sinal) 40%, #1d1433)",
    meta: [el("b", {}, nomeFonte(fonte)), `${plural(c.faixas.length, "música")}, ${duracaoLonga(total)}`],
  }, tabela(c));
}

// ------------------------------------------------------------------ configurações

const DESTAQUES = [["Laranja", "#ff6b1a"], ["Âmbar", "#ffb020"], ["Vermelho", "#ff4b4b"], ["Rosa", "#ff5c9e"], ["Violeta", "#a689ff"], ["Azul", "#4da0ff"], ["Verde", "#36d07a"]];
const FUNDOS = [["Grafite", "#141211"], ["Preto", "#000000"], ["Noite", "#0e121c"], ["Floresta", "#0e1511"], ["Vinho", "#1a0d11"], ["Terra", "#19130d"]];
const ESTILOS = [["atual", "Atual", "Liso"], ["opaco", "Opaco", "Chapado"], ["fosco", "Fosco", "Jateado"], ["metalico", "Metálico", "Escovado"], ["vidro", "Vidro", "Translúcido"]];

let aparencia = { destaque: "#ff6b1a", fundo: "#141211", estilo: "atual" };

const rgb = (h) => { const n = parseInt(h.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; };
const hex = (c) => "#" + c.map((v) => Math.round(v).toString(16).padStart(2, "0")).join("");
const mistura = (a, b, t) => a.map((v, i) => v + (b[i] - v) * t);
function luminancia(c) {
  const [r, g, b] = c.map((v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
const contraste = (a, b) => { const [x, y] = [luminancia(a) + 0.05, luminancia(b) + 0.05]; return Math.max(x, y) / Math.min(x, y); };

/** Mesmas regras do app: fundo sempre escuro o bastante, sinal com contraste calculado. */
function aplicarAparencia() {
  let fundo = rgb(aparencia.fundo);
  for (let i = 0; i < 40 && luminancia(fundo) > 0.025; i++) fundo = mistura(fundo, [0, 0, 0], 0.1);
  const sinal = rgb(aparencia.destaque);
  let texto = sinal;
  for (let i = 0; i < 24 && contraste(texto, fundo) < 4.5; i++) texto = mistura(texto, [255, 255, 255], 0.07);
  const tinta = [28, 19, 14];
  const s = document.documentElement.style;
  s.setProperty("--fundo", hex(fundo));
  s.setProperty("--sinal", aparencia.destaque);
  s.setProperty("--sinal-texto", hex(texto));
  s.setProperty("--no-sinal", contraste(tinta, sinal) >= contraste([255, 255, 255], sinal) ? hex(tinta) : "#ffffff");
  corpo.dataset.estilo = aparencia.estilo;
  try { localStorage.setItem("aparencia", JSON.stringify(aparencia)); } catch {}
}

function amostraCor(nome, cor, marcado, aoEscolher, livre = false) {
  const b = el("button", { type: "button", class: `amostra-cor${livre ? " livre" : ""}`, "aria-pressed": String(marcado), style: `--c:${cor}`, onclick: livre ? null : aoEscolher },
    el("span", { class: "bolinha" }), nome);
  if (livre) b.append(el("input", { type: "color", value: cor, "aria-label": nome, onchange: (e) => aoEscolher(e.target.value) }));
  return b;
}

const endereco = () => (estado.info?.porta ? `${estado.info.ips?.[0] || "—"}:${estado.info.porta}` : "—");

function telaConfig() {
  const info = estado.info || {};
  const mudar = (campo, valor) => { aparencia[campo] = valor; aplicarAparencia(); desenhar(); };
  const destaqueLivre = !DESTAQUES.some(([, c]) => c === aparencia.destaque);
  const fundoLivre = !FUNDOS.some(([, c]) => c === aparencia.fundo);
  const linha = (titulo, sub, ...acoes) => el("div", { class: "linha-config" },
    el("span", { class: "rotulo" }, el("b", {}, titulo), sub != null ? el("small", {}, sub) : null), ...acoes);

  const pares = (info.pares || []).map((p) => linha(p.nome, estado.celular === p.nome ? "Conectado agora" : "Pareado",
    el("button", { type: "button", class: "tecla-texto", onclick: async () => { estado.info.pares = await invoke("esquecer", { id: p.id }); desenhar(); } }, "Esquecer")));

  return el("div", { class: "pagina-simples" },
    el("h1", {}, "Configurações"),
    el("div", { class: "config" },
      el("section", {},
        el("h2", {}, "Músicas deste computador"),
        linha("Pasta", el("span", { class: "caminho" }, info.pasta || "Músicas (padrão)"),
          el("span", { class: "acoes" },
            botao("Escolher pasta", escolherPasta, { icone: "pasta" }),
            botao("Atualizar", () => { invoke("atualizar_biblioteca"); avisar("Relendo a pasta…"); }))),
        el("p", { class: "explica" }, `${plural(estado.bib.pc.length, "faixa encontrada", "faixas encontradas")}. Formatos: MP3, FLAC, M4A, OGG, Opus e WAV. Letras: dentro do arquivo ou num .lrc com o mesmo nome.`)),

      el("section", {},
        el("h2", {}, "Aparência"),
        el("p", { class: "explica" }, "As mesmas opções do app do celular."),
        el("p", { class: "rotulo-grupo" }, "Cor de destaque"),
        el("div", { class: "amostras" },
          DESTAQUES.map(([n, c]) => amostraCor(n, c, aparencia.destaque === c, () => mudar("destaque", c))),
          amostraCor("Sua cor", destaqueLivre ? aparencia.destaque : "#ff6b1a", destaqueLivre, (c) => mudar("destaque", c), true)),
        el("p", { class: "rotulo-grupo" }, "Cor do fundo"),
        el("div", { class: "amostras" },
          FUNDOS.map(([n, c]) => amostraCor(n, c, aparencia.fundo === c, () => mudar("fundo", c))),
          amostraCor("Sua cor", fundoLivre ? aparencia.fundo : "#3a2a6b", fundoLivre, (c) => mudar("fundo", c), true)),
        el("p", { class: "rotulo-grupo" }, "Estilo"),
        el("div", { class: "estilos" }, ESTILOS.map(([id, nome, desc]) => el("button", {
          type: "button", class: "amostra", "aria-pressed": String(aparencia.estilo === id), "data-estilo": id, onclick: () => mudar("estilo", id),
        },
        el("span", { class: "janela-mini", "aria-hidden": "true" }, el("span", { class: "superficie" }), el("span", { class: "superficie" }), el("span", { class: "mini-barra" }, el("span", { class: "mini-play" }))),
        el("span", {}, el("b", {}, nome), desc))))),

      el("section", {},
        el("h2", {}, "Celular"),
        el("p", { class: "explica" }, "Com o celular conectado, você toca aqui as músicas dele — e ele pode mandar o som dele para cá em “Tocar em…”. Funciona sem internet, na mesma rede, com tudo cifrado."),
        linha(estado.pareando ? "Aguardando o celular…" : "Parear um celular",
          estado.pareando
            ? el("span", {}, "Fecha em ", el("span", { class: "numeros prazo" }, tempo(restante * 1000)))
            : `No SongV do celular: Configurações › Tocar no computador; depois “Tocar em…” › ${info.nome || "este computador"}.`,
          estado.pareando ? botao("Cancelar", fecharPareamento) : botao("Parear", abrirPareamento, { cheio: true })),
        ...pares,
        linha("Se o celular não achar este computador", "Em “Tocar em…”, toque em “Adicionar pelo endereço” e digite:",
          el("b", { class: "endereco-grande numeros" }, endereco())),
        el("p", { class: "explica" }, "Se o Windows perguntar, permita o SongV em redes privadas.")),

      el("section", {},
        el("h2", {}, "Sobre"),
        el("p", { class: "explica", style: "margin:0" }, `SongV ${info.versao || ""} para computador · feito por Victor K (github.com/victor-kauan-coder). Fonte Archivo (SIL Open Font License 1.1).`))));
}

async function escolherPasta() {
  const pasta = await invoke("escolher_pasta");
  if (pasta) {
    estado.info = { ...(estado.info || {}), pasta };
    estado.varredura = { lidas: 0, total: 0 };
    desenhar();
  }
}

// ------------------------------------------------------------------ dispositivos (popover)

let gatilho = null;
const popover = $("dispositivos");

function linhaDispositivo(icone, nome, sub, atual) {
  return el("div", { class: `disp${atual ? " atual" : ""}` }, svg(icone), el("span", {}, el("b", {}, nome), el("small", {}, sub)));
}

function desenharDispositivos() {
  const info = estado.info || {};
  const partes = [el("h3", {}, "Dispositivos")];
  partes.push(linhaDispositivo("computador", "Este computador", player.receptor ? `Tocando o som do ${player.receptor.celular}` : info.nome || "Tocando aqui", true));
  if (estado.celular) {
    partes.push(el("p", { class: "rotulo-pop" }, "Celular conectado"),
      linhaDispositivo("celular", estado.celular, "As músicas dele estão na sua biblioteca", false),
      el("div", { class: "acoes-pop" }, botao("Ver as músicas dele", () => { popover.hidePopover(); escolherFonte("celular"); ir("inicio"); }, { clara: true })));
  } else if (estado.pareando) {
    partes.push(el("p", { class: "rotulo-pop" }, "Pareando"),
      el("p", {}, "No SongV do celular, toque em “Tocar em…” e escolha ", el("b", {}, info.nome || "este computador"), ". Fecha em ", el("span", { class: "numeros prazo" }, tempo(restante * 1000)), "."),
      el("div", { class: "acoes-pop" }, botao("Cancelar", fecharPareamento)));
  } else {
    partes.push(el("p", { class: "rotulo-pop" }, "Nenhum celular conectado"),
      el("p", {}, "Toque aqui as músicas do celular, ou mande o som dele para este computador. Sem internet, na mesma rede."),
      el("div", { class: "acoes-pop" }, botao("Parear um celular", abrirPareamento, { cheio: true })));
  }
  if (info.porta) partes.push(el("p", { class: "rotulo-pop" }, "Endereço deste computador"), el("p", { class: "numeros endereco" }, endereco()));
  popover.replaceChildren(...partes);
}

function abrirDispositivos(de) {
  gatilho = de;
  if (!popover.matches(":popover-open")) popover.showPopover();
}

// Os dois botões abrem o mesmo popover; o posicionamento segue quem abriu.
for (const b of [$("botao-celular"), $("botao-dispositivo")]) {
  b.setAttribute("popovertarget", "dispositivos");
  b.addEventListener("click", () => { gatilho = b; });
}
popover.addEventListener("beforetoggle", (e) => {
  const aberto = e.newState === "open";
  $("botao-dispositivo").setAttribute("aria-pressed", String(aberto && gatilho === $("botao-dispositivo")));
  if (!aberto) return;
  desenharDispositivos();
  const r = (gatilho || $("botao-celular")).getBoundingClientRect();
  const largura = 340;
  popover.style.left = `${Math.min(Math.max(8, r.right - largura), innerWidth - largura - 8)}px`;
  if (r.top > innerHeight / 2) {
    popover.style.top = "auto";
    popover.style.bottom = `${innerHeight - r.top + 8}px`;
  } else {
    popover.style.bottom = "auto";
    popover.style.top = `${r.bottom + 8}px`;
  }
});

// ------------------------------------------------------------------ pareamento

let restante = 0;

function redesenharPareamento() {
  if (estado.tela === "config") desenhar();
  if (popover.matches(":popover-open")) desenharDispositivos();
}

async function abrirPareamento() {
  await invoke("permitir_pareamento", { aberto: true });
  clearInterval(estado.pareando);
  restante = 180;
  estado.pareando = setInterval(() => {
    restante -= 1;
    for (const p of document.querySelectorAll(".prazo")) p.textContent = tempo(restante * 1000);
    if (restante <= 0) fecharPareamento();
  }, 1000);
  redesenharPareamento();
}

async function fecharPareamento() {
  clearInterval(estado.pareando);
  estado.pareando = null;
  await invoke("permitir_pareamento", { aberto: false });
  redesenharPareamento();
}

function pareamento({ etapa, celular, codigo, motivo }) {
  const d = $("pareamento");
  if (popover.matches(":popover-open")) popover.hidePopover();
  d.classList.toggle("resultado", etapa !== "codigo");
  $("par-cancelar").textContent = etapa === "codigo" ? "Cancelar" : "Fechar";
  $("par-confirmar").disabled = false;
  if (etapa === "codigo") {
    $("par-titulo").textContent = `Parear com ${celular}`;
    $("par-texto").textContent = "Confira se o mesmo código aparece no celular. Se for diferente, cancele.";
    $("par-codigo").textContent = `${codigo.slice(0, 3)} ${codigo.slice(3)}`;
    if (!d.open) d.showModal();
    $("par-confirmar").focus();
  } else if (etapa === "concluido") {
    $("par-titulo").textContent = "Pareado";
    $("par-texto").textContent = `${celular} já pode tocar aqui, e você pode tocar as músicas dele.`;
    $("par-codigo").textContent = "";
    if (!d.open) d.showModal();
    fecharPareamento();
    atualizarInfo();
    setTimeout(() => d.open && d.close(), 2200);
  } else {
    $("par-titulo").textContent = "Não pareou";
    $("par-texto").textContent = motivo || "Tente de novo.";
    $("par-codigo").textContent = "";
    if (!d.open) d.showModal();
  }
}

$("par-confirmar").addEventListener("click", () => {
  invoke("decidir_pareamento", { aceitar: true });
  $("par-texto").textContent = "Aguardando a confirmação no celular…";
  $("par-confirmar").disabled = true;
});
$("par-cancelar").addEventListener("click", () => $("pareamento").close());
$("pareamento").addEventListener("close", () => invoke("decidir_pareamento", { aceitar: false }));

// ------------------------------------------------------------------ conexão com o celular

async function conexao({ conectado, celular }) {
  estado.celular = conectado ? celular : null;
  corpo.dataset.celular = conectado ? "sim" : "nao";
  $("nome-celular-topo").textContent = conectado ? celular : "Conectar celular";
  $("botao-celular").setAttribute("aria-label", conectado ? `${celular} conectado` : "Conectar celular");
  if (conectado) {
    desenharLateral();
    try {
      definirBiblioteca("celular", await invoke("biblioteca_celular"));
      avisar(`${celular} conectado. As músicas dele estão na sua biblioteca.`);
    } catch {}
  } else {
    esquecerCapasDoCelular();
    sairDoReceptor(false);
    const tocavaDoCelular = player.fila.some((i) => i.fonte === "celular");
    definirBiblioteca("celular", []);
    if (estado.fonte === "celular") escolherFonte("pc");
    desenharLateral();
    if (tocavaDoCelular) avisar("O celular desconectou.");
  }
  if (popover.matches(":popover-open")) desenharDispositivos();
  if (estado.tela === "config") atualizarInfo();
}

async function atualizarInfo() {
  estado.info = await invoke("info");
  if (estado.info?.celular && !estado.celular) conexao({ conectado: true, celular: estado.info.celular });
  if (estado.tela === "config" || !estado.bib.pc.length) desenhar();
  if (popover.matches(":popover-open")) desenharDispositivos();
}

// ------------------------------------------------------------------ controles e atalhos

/** Nada carregado: o play toca a biblioteca inteira, em vez de ficar apagado. */
function tocarOuAlternar() {
  if (itemAtual()) return alternar();
  tocarColecao(colTodas(estado.fonte));
}

function alternarPalco(abrir = corpo.dataset.palco !== "sim") {
  const sim = abrir && !!itemAtual();
  corpo.dataset.palco = sim ? "sim" : "nao";
  $("botao-letra").setAttribute("aria-pressed", String(sim));
  if (!sim && corpo.dataset.telaCheia === "sim") alternarTelaCheia(false);
}

async function alternarTelaCheia(sim = corpo.dataset.telaCheia !== "sim") {
  if (sim && !itemAtual()) return;
  corpo.dataset.telaCheia = sim ? "sim" : "nao";
  if (sim) alternarPalco(true);
  try {
    if (tauri) await tauri.window.getCurrentWindow().setFullscreen(sim);
    else if (sim) await document.documentElement.requestFullscreen();
    else if (document.fullscreenElement) await document.exitFullscreen();
  } catch {}
}

function alternarDireita(sim = corpo.dataset.direita !== "sim") {
  corpo.dataset.direita = sim ? "sim" : "nao";
  $("botao-fila").setAttribute("aria-pressed", String(sim));
  try { localStorage.setItem("direita", sim ? "sim" : "nao"); } catch {}
}

$("tocar").addEventListener("click", tocarOuAlternar);
$("anterior").addEventListener("click", anterior);
$("proxima").addEventListener("click", proxima);
$("aleatorio").addEventListener("click", alternarAleatorio);
$("repetir").addEventListener("click", alternarRepetir);
$("botao-letra").addEventListener("click", () => alternarPalco());
$("abrir-palco").addEventListener("click", () => alternarPalco());
$("fechar-palco").addEventListener("click", () => alternarPalco(false));
$("tela-cheia").addEventListener("click", () => alternarTelaCheia());
$("botao-fila").addEventListener("click", () => alternarDireita());
$("fechar-direita").addEventListener("click", () => alternarDireita(false));
$("voltar").addEventListener("click", () => mover(-1));
$("avancar").addEventListener("click", () => mover(1));
$("painel-titulo").addEventListener("click", () => {
  const atual = itemAtual();
  if (!atual) return;
  if (!derivar(atual.fonte).porChave.has(chaveAlbum(atual.f))) return alternarPalco(true);
  if (atual.fonte !== estado.fonte) escolherFonte(atual.fonte);
  ir("album", chaveAlbum(atual.f));
});
for (const b of document.querySelectorAll("[data-ir]")) b.addEventListener("click", () => ir(b.dataset.ir));

$("busca").addEventListener("input", (e) => {
  const texto = e.target.value;
  if (texto.trim()) ir("buscar", texto, { substituir: estado.tela === "buscar" });
  else if (estado.tela === "buscar") ir("navegar", null, { substituir: true });
});
$("busca").addEventListener("focus", () => { if (!["buscar", "navegar"].includes(estado.tela)) ir("navegar"); });

document.addEventListener("keydown", (e) => {
  const audio = $("audio");
  if (e.ctrlKey && (e.code === "KeyF" || e.code === "KeyK")) { e.preventDefault(); $("busca").focus(); $("busca").select(); return; }
  if (e.altKey && e.code === "ArrowLeft") { e.preventDefault(); mover(-1); return; }
  if (e.altKey && e.code === "ArrowRight") { e.preventDefault(); mover(1); return; }
  if (e.code === "Escape") {
    if (corpo.dataset.telaCheia === "sim") alternarTelaCheia(false);
    else if (corpo.dataset.palco === "sim") alternarPalco(false);
    else if (e.target === $("busca")) e.target.blur();
    return;
  }
  if (e.target.closest("input, dialog, textarea, [popover]")) return;
  if (e.target.closest("button") && (e.code === "Space" || e.code === "Enter")) return;
  if (e.code === "Space") { e.preventDefault(); tocarOuAlternar(); }
  else if (e.ctrlKey && e.code === "ArrowRight") proxima();
  else if (e.ctrlKey && e.code === "ArrowLeft") anterior();
  else if (e.code === "ArrowRight" && !e.target.closest(".linha-faixa")) buscar(Math.min((audio.duration || 0) * 1000, audio.currentTime * 1000 + 5000));
  else if (e.code === "ArrowLeft" && !e.target.closest(".linha-faixa")) buscar(Math.max(0, audio.currentTime * 1000 - 5000));
});
document.addEventListener("mouseup", (e) => {
  if (e.button === 3) mover(-1);
  else if (e.button === 4) mover(1);
});

// ------------------------------------------------------------------ eventos do Rust

ouvir("rede", () => atualizarInfo());
ouvir("conexao", ({ payload }) => conexao(payload));
ouvir("pareamento", ({ payload }) => pareamento(payload));
ouvir("tocar", ({ payload }) => { receberDoCelular(payload, estado.celular || "celular"); $("botao-letra").setAttribute("aria-pressed", String(corpo.dataset.palco === "sim")); });
for (const t of ["pausar", "retomar", "buscar", "volume"]) ouvir(t, ({ payload }) => comandoDoCelular(t, payload));
ouvir("letra", ({ payload }) => letraDoCelular(payload));
ouvir("capa", ({ payload }) => capaChegou(payload.id, payload.versao));
ouvir("sair", () => sairDoReceptor(false));
ouvir("erro", ({ payload }) => avisar(payload));
ouvir("biblioteca-progresso", ({ payload }) => {
  estado.varredura = payload.lidas < payload.total ? payload : null;
  if (!estado.bib.pc.length && estado.fonte === "pc" && estado.tela !== "config") desenhar();
});
ouvir("biblioteca", async () => {
  estado.varredura = null;
  definirBiblioteca("pc", await invoke("biblioteca"));
});

// ------------------------------------------------------------------ início

try {
  const a = JSON.parse(localStorage.getItem("aparencia") || "null");
  if (a) aparencia = { ...aparencia, ...a };
  const l = JSON.parse(localStorage.getItem("lateral") || "null");
  if (l) estado.lateral = { ...estado.lateral, ...l };
  if (localStorage.getItem("direita") === "nao") alternarDireita(false);
} catch {}
aplicarAparencia();
iniciarLetra(buscar);
iniciarPlayer();
player.ouvintes.add(marcarTocando);
ir("inicio");
desenharLateral();
if (tauri) {
  definirBiblioteca("pc", await invoke("biblioteca"));
  atualizarInfo();
} else if (new URLSearchParams(location.search).has("demo")) {
  import("../previa/demo.js").then((m) => m.iniciar({ definirBiblioteca, conexao }));
}
