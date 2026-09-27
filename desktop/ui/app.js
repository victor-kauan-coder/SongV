// SongV para computador — telas, bibliotecas (deste computador e do celular conectado),
// aparência, pareamento e eventos do Rust.

import { $, invoke, ouvir, el, capa, tempo, duracaoLonga, plural, normalizar, avisar, capaChegou, esquecerCapasDoCelular, tauri } from "./ponte.js";
import { iniciarLetra } from "./letra.js";
import {
  player, itemAtual, tocarLista, proxima, anterior, alternar, buscar, alternarAleatorio, alternarRepetir,
  receberDoCelular, comandoDoCelular, sairDoReceptor, letraDoCelular, recentes, iniciarPlayer,
} from "./player.js";

const corpo = document.body;
const conteudo = $("conteudo");

const estado = {
  tela: "inicio",
  param: null,
  pilha: [],
  fonte: "pc",
  bib: { pc: [], celular: [] },
  derivados: { pc: null, celular: null },
  info: null,
  celular: null,
  varredura: null, // { lidas, total } enquanto lê a pasta
  busca: "",
  pareando: null, // intervalo do prazo
};

// ------------------------------------------------------------------ dados derivados

function derivar(fonte) {
  if (estado.derivados[fonte]) return estado.derivados[fonte];
  const faixas = estado.bib[fonte];
  const albuns = new Map();
  const artistas = new Map();
  for (const f of faixas) {
    const dono = f.artistaAlbum || f.artista;
    const chave = f.album ? `${normalizar(dono)}|${normalizar(f.album)}` : `avulsa|${f.id}`;
    if (!albuns.has(chave)) albuns.set(chave, { chave, titulo: f.album || f.titulo, artista: dono, ano: f.ano, faixas: [] });
    const a = albuns.get(chave);
    a.faixas.push(f);
    a.ano = a.ano || f.ano;
    const nome = f.artista || "Artista desconhecido";
    if (!artistas.has(nome)) artistas.set(nome, { nome, faixas: [] });
    artistas.get(nome).faixas.push(f);
  }
  const porFaixa = (x, y) => (x.disco || 1) - (y.disco || 1) || (x.faixa || 999) - (y.faixa || 999) || x.titulo.localeCompare(y.titulo, "pt-BR");
  const lista = [...albuns.values()];
  for (const a of lista) {
    a.faixas.sort(porFaixa);
    a.duracaoMs = a.faixas.reduce((s, f) => s + f.duracaoMs, 0);
    a.adicionada = Math.max(...a.faixas.map((f) => f.adicionada || 0));
  }
  const d = {
    faixas: [...faixas].sort((x, y) => x.titulo.localeCompare(y.titulo, "pt-BR")),
    albuns: lista.sort((x, y) => x.titulo.localeCompare(y.titulo, "pt-BR")),
    artistas: [...artistas.values()].sort((x, y) => x.nome.localeCompare(y.nome, "pt-BR")),
    porId: new Map(faixas.map((f) => [f.id, f])),
  };
  estado.derivados[fonte] = d;
  return d;
}

function definirBiblioteca(fonte, faixas) {
  estado.bib[fonte] = faixas || [];
  estado.derivados[fonte] = null;
  $("tocar").disabled = !itemAtual() && !estado.bib.pc.length && !estado.bib.celular.length;
  $(fonte === "pc" ? "conta-pc" : "conta-celular").textContent = plural(estado.bib[fonte].length, "faixa");
  if (fonte === estado.fonte || estado.tela === "config") desenhar();
}

const nomeFonte = (fonte) => (fonte === "celular" ? estado.celular || "Celular" : "Este computador");

// ------------------------------------------------------------------ navegação

function ir(tela, param = null, { voltar = false } = {}) {
  if (!voltar && (tela !== estado.tela || param !== estado.param)) estado.pilha.push([estado.tela, estado.param]);
  estado.tela = tela;
  estado.param = param;
  corpo.dataset.tela = tela;
  corpo.dataset.palco = "nao";
  $("botao-letra").setAttribute("aria-pressed", "false");
  for (const b of document.querySelectorAll("[data-ir]")) {
    const atual = b.dataset.ir === tela || (b.dataset.ir === "albuns" && tela === "album") || (b.dataset.ir === "artistas" && tela === "artista");
    if (atual) b.setAttribute("aria-current", "page");
    else b.removeAttribute("aria-current");
  }
  desenhar();
  conteudo.scrollTop = 0;
}

function voltar() {
  const anterior = estado.pilha.pop();
  if (anterior) ir(anterior[0], anterior[1], { voltar: true });
}

function escolherFonte(fonte) {
  estado.fonte = fonte;
  corpo.dataset.fonte = fonte;
  for (const b of document.querySelectorAll(".fontes button[data-fonte]")) b.setAttribute("aria-pressed", b.dataset.fonte === fonte);
  if (["album", "artista"].includes(estado.tela)) ir("albuns");
  else desenhar();
}

function desenhar() {
  const telas = { inicio, buscar: telaBuscar, albuns: telaAlbuns, album: telaAlbum, artistas: telaArtistas, artista: telaArtista, faixas: telaFaixas, config: telaConfig };
  conteudo.replaceChildren(...[].concat((telas[estado.tela] || inicio)()));
  marcarTocando();
}

// ------------------------------------------------------------------ peças

function cabecalho(titulo, subtitulo, ...acoes) {
  return el("header", { class: "cabecalho" },
    el("div", {}, el("h1", {}, titulo), subtitulo ? el("p", {}, subtitulo) : null),
    acoes.length ? el("div", { class: "acoes", style: "margin:0" }, ...acoes) : null);
}

const icone = {
  tocar: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.5 5.6v12.8a1 1 0 0 0 1.53.85l10.1-6.4a1 1 0 0 0 0-1.7l-10.1-6.4A1 1 0 0 0 8.5 5.6z"/></svg>',
  aleatorio: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M17 4.5 20.5 8 17 11.5V9h-1.6a3 3 0 0 0-2.5 1.34L8.8 16.4A5 5 0 0 1 4.6 18.6H3v-2h1.6a3 3 0 0 0 2.5-1.34l4.1-6.06A5 5 0 0 1 15.4 7H17zM3 7h1.6a5 5 0 0 1 3.7 1.66L7 10.2A3 3 0 0 0 4.6 9H3zm14 7.5 3.5 3.5-3.5 3.5V19h-1.6a5 5 0 0 1-3.7-1.66l1.3-1.54A3 3 0 0 0 15.4 17H17z"/></svg>',
  buscar: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10.5 3a7.5 7.5 0 0 1 5.96 12.06l4.24 4.23-1.41 1.42-4.24-4.24A7.5 7.5 0 1 1 10.5 3zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11z"/></svg>',
  pasta: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h7l2 2h9v12H3z"/></svg>',
};

function botao(texto, aoClicar, { cheio = false, svg = null } = {}) {
  const b = el("button", { type: "button", class: `tecla${cheio ? " cheia" : ""}`, onclick: aoClicar });
  if (svg) b.insertAdjacentHTML("beforeend", svg);
  b.append(texto);
  return b;
}

function cartaoAlbum(fonte, a) {
  return el("button", { type: "button", class: "cartao", onclick: () => ir("album", a.chave) },
    capa(fonte, a.faixas[0], a.titulo),
    el("span", {}, el("b", {}, a.titulo), el("small", {}, [a.artista, a.ano].filter(Boolean).join(" · "))));
}

function cartaoArtista(fonte, ar) {
  return el("button", { type: "button", class: "cartao redondo", onclick: () => ir("artista", ar.nome) },
    capa(fonte, ar.faixas[0], ar.nome),
    el("span", {}, el("b", {}, ar.nome), el("small", {}, plural(ar.faixas.length, "faixa"))));
}

/** Lista de faixas: clicar toca a lista a partir dali. */
function listaFaixas(fonte, faixas, { numeros = false, comAlbum = true } = {}) {
  const lista = el("div", { class: `lista${comAlbum ? "" : " sem-album"}`, role: "list" });
  lista.append(el("div", { class: "linha-faixa cabeca-lista", "aria-hidden": "true" },
    el("span", { class: "n" }, "#"), el("span", {}, "Título"), el("span", { class: "album" }, "Álbum"), el("span", { class: "dur" }, "Duração")));
  faixas.forEach((f, i) => {
    lista.append(el("button", {
      type: "button",
      class: "linha-faixa",
      role: "listitem",
      "data-id": f.id,
      "data-fonte": fonte,
      onclick: () => tocarLista(fonte, faixas, i),
    },
    el("span", { class: "n numeros" }, numeros ? String(f.faixa || i + 1) : String(i + 1)),
    el("span", { class: "titulo" }, comAlbum ? capa(fonte, f, f.album || f.titulo) : null, el("span", {}, el("b", {}, f.titulo), el("small", {}, f.artista))),
    el("span", { class: "album" }, f.album || "—"),
    el("span", { class: "dur numeros" }, tempo(f.duracaoMs))));
  });
  return lista;
}

/** Destaca a faixa tocando nas listas visíveis. */
function marcarTocando() {
  const atual = itemAtual();
  for (const l of conteudo.querySelectorAll(".linha-faixa[data-id]")) {
    const sim = !!atual && l.dataset.id === atual.f.id && l.dataset.fonte === atual.fonte;
    l.classList.toggle("ativa", sim);
    const n = l.querySelector(".n");
    if (sim && !n.querySelector(".tocando-agora")) {
      n.dataset.antes = n.textContent;
      n.replaceChildren(el("span", { class: "tocando-agora", "aria-label": "Tocando" }, el("i"), el("i"), el("i")));
    } else if (!sim && n.dataset.antes) {
      n.textContent = n.dataset.antes;
      delete n.dataset.antes;
    }
  }
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
    el("div", { class: "acoes" }, botao("Escolher pasta", escolherPasta, { cheio: true, svg: icone.pasta }), botao("Parear um celular", () => ir("config"))));
}

// ------------------------------------------------------------------ telas

function inicio() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const partes = [cabecalho("Início", fonte === "celular" ? `Músicas do ${nomeFonte("celular")}` : null)];
  if (!d.faixas.length) return [...partes, vazioDaFonte()];

  const tocadas = recentes().filter((r) => r.fonte === fonte).map((r) => d.porId.get(r.id)).filter(Boolean).slice(0, 12);
  if (tocadas.length) {
    partes.push(el("section", { class: "secao" }, el("h2", {}, "Tocadas recentemente"),
      el("div", { class: "faixa-horizontal" }, tocadas.map((f, i) => el("button", {
        type: "button", class: "cartao", onclick: () => tocarLista(fonte, tocadas, i),
      }, capa(fonte, f, f.album || f.titulo), el("span", {}, el("b", {}, f.titulo), el("small", {}, f.artista)))))));
  }
  const novos = [...d.albuns].sort((a, b) => b.adicionada - a.adicionada).slice(0, 14);
  const titulo = (texto, destino) => el("div", { class: "titulo-secao" }, el("h2", {}, texto),
    destino ? el("button", { type: "button", class: "tecla-texto", onclick: () => ir(destino) }, "Ver tudo") : null);
  partes.push(el("section", { class: "secao" }, titulo("Adicionados recentemente", "albuns"), el("div", { class: "faixa-horizontal" }, novos.map((a) => cartaoAlbum(fonte, a)))));
  const artistas = [...d.artistas].sort((a, b) => b.faixas.length - a.faixas.length).slice(0, 14);
  partes.push(el("section", { class: "secao" }, titulo("Artistas", "artistas"), el("div", { class: "faixa-horizontal" }, artistas.map((ar) => cartaoArtista(fonte, ar)))));
  return partes;
}

function telaAlbuns() {
  const d = derivar(estado.fonte);
  return [
    cabecalho("Álbuns", `${plural(d.albuns.length, "álbum", "álbuns")} · ${nomeFonte(estado.fonte)}`),
    d.albuns.length ? el("div", { class: "grade" }, d.albuns.map((a) => cartaoAlbum(estado.fonte, a))) : vazioDaFonte(),
  ];
}

function telaAlbum() {
  const fonte = estado.fonte;
  const a = derivar(fonte).albuns.find((x) => x.chave === estado.param);
  if (!a) return [cabecalho("Álbum não encontrado", "Ele pode ter saído da biblioteca.")];
  const detalhe = [a.ano, plural(a.faixas.length, "faixa"), duracaoLonga(a.duracaoMs)].filter(Boolean).join(" · ");
  const artista = derivar(fonte).artistas.find((x) => x.nome === a.artista);
  return [
    el("div", { class: "topo-colecao" },
      capa(fonte, a.faixas[0], a.titulo),
      el("div", {},
        el("h1", {}, a.titulo),
        el("p", { class: "quem" }, artista ? el("button", { type: "button", onclick: () => ir("artista", a.artista) }, a.artista) : a.artista),
        el("p", { class: "detalhe numeros" }, detalhe),
        el("div", { class: "acoes" },
          botao("Tocar", () => tocarLista(fonte, a.faixas, 0, { aleatorio: false }), { cheio: true, svg: icone.tocar }),
          botao("Aleatório", () => tocarLista(fonte, a.faixas, Math.floor(Math.random() * a.faixas.length), { aleatorio: true }), { svg: icone.aleatorio })))),
    listaFaixas(fonte, a.faixas, { numeros: true, comAlbum: false }),
  ];
}

function telaArtistas() {
  const d = derivar(estado.fonte);
  return [
    cabecalho("Artistas", `${plural(d.artistas.length, "artista")} · ${nomeFonte(estado.fonte)}`),
    d.artistas.length ? el("div", { class: "grade" }, d.artistas.map((ar) => cartaoArtista(estado.fonte, ar))) : vazioDaFonte(),
  ];
}

function telaArtista() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const ar = d.artistas.find((x) => x.nome === estado.param);
  if (!ar) return [cabecalho("Artista não encontrado")];
  const albuns = d.albuns.filter((a) => a.faixas.some((f) => f.artista === ar.nome));
  return [
    el("div", { class: "topo-colecao" },
      el("div", { class: "cartao redondo", style: "pointer-events:none" }, capa(fonte, ar.faixas[0], ar.nome)),
      el("div", {},
        el("h1", {}, ar.nome),
        el("p", { class: "detalhe" }, `${plural(albuns.length, "álbum", "álbuns")} · ${plural(ar.faixas.length, "faixa")}`),
        el("div", { class: "acoes" },
          botao("Tocar tudo", () => tocarLista(fonte, ar.faixas, 0, { aleatorio: false }), { cheio: true, svg: icone.tocar }),
          botao("Aleatório", () => tocarLista(fonte, ar.faixas, Math.floor(Math.random() * ar.faixas.length), { aleatorio: true }), { svg: icone.aleatorio })))),
    albuns.length > 1 ? el("section", { class: "secao" }, el("h2", {}, "Álbuns"), el("div", { class: "grade" }, albuns.map((a) => cartaoAlbum(fonte, a)))) : null,
    el("section", { class: "secao" }, el("h2", {}, "Faixas"), listaFaixas(fonte, ar.faixas)),
  ].filter(Boolean);
}

function telaFaixas() {
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const total = d.faixas.reduce((s, f) => s + f.duracaoMs, 0);
  return [
    cabecalho("Faixas", `${plural(d.faixas.length, "faixa")} · ${duracaoLonga(total)} · ${nomeFonte(fonte)}`,
      ...(d.faixas.length ? [
        botao("Tocar tudo", () => tocarLista(fonte, d.faixas, 0, { aleatorio: false }), { cheio: true, svg: icone.tocar }),
        botao("Aleatório", () => tocarLista(fonte, d.faixas, Math.floor(Math.random() * d.faixas.length), { aleatorio: true }), { svg: icone.aleatorio }),
      ] : [])),
    d.faixas.length ? listaFaixas(fonte, d.faixas) : vazioDaFonte(),
  ];
}

function telaBuscar() {
  const resultados = el("div", {});
  const campo = el("input", {
    type: "search",
    placeholder: "Faixas, álbuns e artistas",
    value: estado.busca,
    "aria-label": "Buscar",
    oninput: (e) => { estado.busca = e.target.value; resultados.replaceChildren(...resultadosBusca()); marcarTocando(); },
  });
  const barra = el("label", { class: "campo" });
  barra.insertAdjacentHTML("beforeend", icone.buscar);
  barra.append(campo);
  resultados.append(...resultadosBusca());
  queueMicrotask(() => campo.focus());
  return [cabecalho("Buscar", nomeFonte(estado.fonte)), barra, resultados];
}

function resultadosBusca() {
  const termo = normalizar(estado.busca.trim());
  if (termo.length < 2) return [];
  const fonte = estado.fonte;
  const d = derivar(fonte);
  const bate = (...t) => t.some((x) => normalizar(x).includes(termo));
  const faixas = d.faixas.filter((f) => bate(f.titulo, f.artista, f.album)).slice(0, 60);
  const albuns = d.albuns.filter((a) => bate(a.titulo, a.artista)).slice(0, 12);
  const artistas = d.artistas.filter((ar) => bate(ar.nome)).slice(0, 12);
  if (!faixas.length && !albuns.length && !artistas.length) {
    return [el("div", { class: "vazio" }, el("h2", {}, "Nada encontrado"), el("p", {}, `Nenhuma faixa, álbum ou artista com “${estado.busca.trim()}”.`))];
  }
  const secao = (titulo, corpoSecao) => el("section", { class: "secao", style: "margin-top:32px" }, el("h2", {}, titulo), corpoSecao);
  return [
    artistas.length ? secao("Artistas", el("div", { class: "faixa-horizontal" }, artistas.map((ar) => cartaoArtista(fonte, ar)))) : null,
    albuns.length ? secao("Álbuns", el("div", { class: "faixa-horizontal" }, albuns.map((a) => cartaoAlbum(fonte, a)))) : null,
    faixas.length ? secao("Faixas", listaFaixas(fonte, faixas)) : null,
  ].filter(Boolean);
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

function telaConfig() {
  const info = estado.info || {};
  const mudar = (campo, valor) => { aparencia[campo] = valor; aplicarAparencia(); desenhar(); };
  const destaqueLivre = !DESTAQUES.some(([, c]) => c === aparencia.destaque);
  const fundoLivre = !FUNDOS.some(([, c]) => c === aparencia.fundo);

  const pares = (info.pares || []).map((p) => el("div", { class: "linha-config" },
    el("span", { class: "rotulo" }, el("b", {}, p.nome), el("small", {}, estado.celular === p.nome ? "Conectado agora" : "Pareado")),
    el("button", { type: "button", class: "tecla-texto", onclick: async () => { estado.info.pares = await invoke("esquecer", { id: p.id }); desenhar(); } }, "Esquecer")));

  return [
    cabecalho("Configurações"),
    el("div", { class: "config" },
      el("section", {},
        el("h2", {}, "Músicas deste computador"),
        el("div", { class: "linha-config" },
          el("span", { class: "rotulo" }, el("b", {}, "Pasta"), el("small", { class: "caminho" }, info.pasta || "Músicas (padrão)")),
          el("span", { class: "acoes", style: "margin:0" },
            botao("Escolher pasta", escolherPasta, { svg: icone.pasta }),
            botao("Atualizar", () => { invoke("atualizar_biblioteca"); avisar("Relendo a pasta…"); }))),
        el("p", { class: "explica" }, `${plural(estado.bib.pc.length, "faixa encontrada", "faixas encontradas")}. Formatos: MP3, FLAC, M4A, OGG, Opus e WAV. Letras: dentro do arquivo ou num .lrc com o mesmo nome.`)),

      el("section", {},
        el("h2", {}, "Aparência"),
        el("p", { class: "explica" }, "As mesmas opções do app do celular."),
        el("b", {}, "Cor de destaque"),
        el("div", { class: "amostras" },
          DESTAQUES.map(([n, c]) => amostraCor(n, c, aparencia.destaque === c, () => mudar("destaque", c))),
          amostraCor("Sua cor", destaqueLivre ? aparencia.destaque : "#ff6b1a", destaqueLivre, (c) => mudar("destaque", c), true)),
        el("b", {}, "Cor do fundo"),
        el("div", { class: "amostras" },
          FUNDOS.map(([n, c]) => amostraCor(n, c, aparencia.fundo === c, () => mudar("fundo", c))),
          amostraCor("Sua cor", fundoLivre ? aparencia.fundo : "#3a2a6b", fundoLivre, (c) => mudar("fundo", c), true)),
        el("b", {}, "Estilo"),
        el("div", { class: "estilos" }, ESTILOS.map(([id, nome, desc]) => el("button", {
          type: "button", class: "amostra", "aria-pressed": String(aparencia.estilo === id), "data-estilo": id, onclick: () => mudar("estilo", id),
        },
        el("span", { class: "janela" }, el("span", { class: "mini-lateral superficie" }), el("span", { class: "mini-painel superficie" }, el("span", { class: "mini-play" }))),
        el("span", {}, el("b", {}, nome), desc))))),

      el("section", {},
        el("h2", {}, "Celular"),
        el("p", { class: "explica" }, "Com o celular conectado, você toca aqui as músicas dele — e ele pode mandar o som dele para cá em “Tocar em…”. Funciona sem internet, na mesma rede, com tudo cifrado."),
        el("div", { class: "linha-config" },
          el("span", { class: "rotulo" },
            el("b", {}, estado.pareando ? "Aguardando o celular…" : "Parear um celular"),
            el("small", {}, estado.pareando
              ? el("span", {}, "Fecha em ", el("span", { id: "prazo", class: "numeros" }, tempo(restante * 1000)))
              : `No SongV do celular: Configurações › Tocar no computador; depois “Tocar em…” › ${info.nome || "este computador"}.`)),
          estado.pareando ? botao("Cancelar", fecharPareamento) : botao("Parear", abrirPareamento, { cheio: true })),
        ...pares,
        el("div", { class: "linha-config" },
          el("span", { class: "rotulo" }, el("b", {}, "Se o celular não achar este computador"), el("small", {}, "Em “Tocar em…”, toque em “Adicionar pelo endereço” e digite:")),
          el("b", { class: "caminho numeros", style: "font-family:var(--larga);font-size:18px" }, info.porta ? `${info.ips?.[0] || "—"}:${info.porta}` : "—")),
        el("p", { class: "explica" }, "Se o Windows perguntar, permita o SongV em redes privadas.")),

      el("section", {},
        el("h2", {}, "Sobre"),
        el("p", { class: "explica", style: "margin:0" }, `SongV ${info.versao || ""} para computador · feito por Victor K (github.com/victor-kauan-coder). Fonte Archivo (SIL Open Font License 1.1).`))),
  ];
}

async function escolherPasta() {
  const pasta = await invoke("escolher_pasta");
  if (pasta) {
    estado.info = { ...(estado.info || {}), pasta };
    estado.varredura = { lidas: 0, total: 0 };
    desenhar();
  }
}

// ------------------------------------------------------------------ pareamento

let restante = 0;

async function abrirPareamento() {
  await invoke("permitir_pareamento", { aberto: true });
  clearInterval(estado.pareando);
  restante = 180;
  estado.pareando = setInterval(() => {
    restante -= 1;
    const p = $("prazo");
    if (p) p.textContent = tempo(restante * 1000);
    if (restante <= 0) fecharPareamento();
  }, 1000);
  if (estado.tela === "config") desenhar();
}

async function fecharPareamento() {
  clearInterval(estado.pareando);
  estado.pareando = null;
  await invoke("permitir_pareamento", { aberto: false });
  if (estado.tela === "config") desenhar();
}

function pareamento({ etapa, celular, codigo, motivo }) {
  const d = $("pareamento");
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
  $("nome-celular").textContent = celular || "Celular";
  if (conectado) {
    $("conta-celular").textContent = "Carregando…";
    try {
      definirBiblioteca("celular", await invoke("biblioteca_celular"));
      avisar(`${celular} conectado: as músicas dele estão em Bibliotecas.`);
    } catch {
      $("conta-celular").textContent = "Conectado";
    }
  } else {
    esquecerCapasDoCelular();
    sairDoReceptor(false);
    const tocavaDoCelular = player.fila.some((i) => i.fonte === "celular");
    definirBiblioteca("celular", []);
    if (estado.fonte === "celular") escolherFonte("pc");
    if (tocavaDoCelular) avisar("O celular desconectou.");
  }
  if (estado.tela === "config") atualizarInfo();
}

async function atualizarInfo() {
  estado.info = await invoke("info");
  if (estado.info?.celular && !estado.celular) conexao({ conectado: true, celular: estado.info.celular });
  if (estado.tela === "config" || !estado.bib.pc.length) desenhar();
}

// ------------------------------------------------------------------ controles e atalhos

// Nada carregado: o play toca a biblioteca inteira, em vez de ficar apagado.
$("tocar").addEventListener("click", () => {
  if (itemAtual()) return alternar();
  const faixas = derivar(estado.fonte).faixas;
  if (faixas.length) tocarLista(estado.fonte, faixas, player.aleatorio ? Math.floor(Math.random() * faixas.length) : 0);
});
$("anterior").addEventListener("click", anterior);
$("proxima").addEventListener("click", proxima);
$("aleatorio").addEventListener("click", alternarAleatorio);
$("repetir").addEventListener("click", alternarRepetir);
function alternarPalco(abrir = corpo.dataset.palco !== "sim") {
  const sim = abrir && !!itemAtual();
  corpo.dataset.palco = sim ? "sim" : "nao";
  $("botao-letra").setAttribute("aria-pressed", String(sim));
}
$("botao-letra").addEventListener("click", () => alternarPalco());
$("abrir-palco").addEventListener("click", () => alternarPalco());
$("fechar-palco").addEventListener("click", () => alternarPalco(false));
for (const b of document.querySelectorAll("[data-ir]")) b.addEventListener("click", () => { estado.pilha = []; ir(b.dataset.ir); });
// Só os botões da barra lateral: o <body> também tem data-fonte (usado pelo CSS).
for (const b of document.querySelectorAll(".fontes button[data-fonte]")) b.addEventListener("click", () => escolherFonte(b.dataset.fonte));

document.addEventListener("keydown", (e) => {
  if (e.target.closest("input, dialog, textarea")) return;
  const audio = $("audio");
  if (e.code === "Space") { e.preventDefault(); alternar(); }
  else if (e.code === "ArrowRight" && !e.altKey) buscar(Math.min((audio.duration || 0) * 1000, audio.currentTime * 1000 + 5000));
  else if (e.code === "ArrowLeft" && !e.altKey) buscar(Math.max(0, audio.currentTime * 1000 - 5000));
  else if (e.code === "Escape" && corpo.dataset.palco === "sim") alternarPalco(false);
  else if ((e.code === "Backspace" || (e.altKey && e.code === "ArrowLeft")) && corpo.dataset.palco !== "sim") voltar();
  else if (e.ctrlKey && e.code === "KeyF") { e.preventDefault(); ir("buscar"); }
});
document.addEventListener("mouseup", (e) => { if (e.button === 3) voltar(); });

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
} catch {}
aplicarAparencia();
iniciarLetra(buscar);
iniciarPlayer();
player.ouvintes.add(marcarTocando);
escolherFonte("pc");
ir("inicio");
estado.pilha = [];
if (tauri) {
  definirBiblioteca("pc", await invoke("biblioteca"));
  atualizarInfo();
} else if (new URLSearchParams(location.search).has("demo")) {
  import("../previa/demo.js").then((m) => m.iniciar({ definirBiblioteca, conexao, estado, desenhar }));
}
