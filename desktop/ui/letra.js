// A letra como legenda de cinema: linha ativa acesa, as outras esmaecidas; rola sozinha até
// alguém rolar com a roda (aí espera 4 s); clicar numa linha leva a música até ela.

import { $, el } from "./ponte.js";

let letra = null; // { faixa, sincronizada, atrasoMs, linhas: [{ t, texto, trad?, rom? }] }
let ativa = -1;
let manualAte = 0;
let aoBuscar = () => {};

export function iniciarLetra(buscar) {
  aoBuscar = buscar;
  const caixa = $("letra");
  caixa.addEventListener("wheel", () => { manualAte = performance.now() + 4000; }, { passive: true });
  caixa.addEventListener("click", (e) => {
    const linha = e.target.closest(".linha");
    if (!linha || !letra?.sincronizada) return;
    const t = letra.linhas[Number(linha.dataset.i)]?.t;
    if (t == null) return;
    manualAte = 0;
    aoBuscar(Math.max(0, t - (letra.atrasoMs || 0)));
  });
}

export const letraDe = () => letra?.faixa;

/** `null` = sem letra (mostra `mensagem`) ou, com `carregando`, os três pontinhos. */
export function mostrarLetra(nova, mensagem = "", carregando = false) {
  letra = nova;
  ativa = -1;
  const caixa = $("letra");
  caixa.replaceChildren();
  caixa.classList.toggle("simples", !!nova && !nova.sincronizada);
  if (!nova?.linhas?.length) {
    if (mensagem) caixa.append(el("p", { class: "sem-letra" }, mensagem));
    else if (carregando) caixa.append(el("p", { class: "linha pausa ativa", "aria-label": "Carregando a letra" }, el("i"), el("i"), el("i")));
    return;
  }
  nova.linhas.forEach((l, i) => {
    const p = el("p", { class: "linha", "data-i": i });
    if (nova.sincronizada && !l.texto.trim()) {
      p.classList.add("pausa");
      p.setAttribute("aria-label", "Trecho instrumental");
      p.append(el("i"), el("i"), el("i"));
    } else {
      p.append(l.texto);
      if (l.rom) p.append(el("span", { class: "rom" }, l.rom));
      if (l.trad) p.append(el("span", { class: "trad" }, l.trad));
    }
    caixa.append(p);
  });
  caixa.scrollTop = 0;
}

function indiceAtivo(linhas, ms) {
  let r = -1;
  for (let i = 0; i < linhas.length; i++) {
    const t = linhas[i].t;
    if (t == null) continue;
    if (t <= ms) r = i;
    else break;
  }
  return r;
}

/** Chamado a cada `timeupdate` e depois de buscas. */
export function sincronizarLetra(posicaoMs, forcar = false) {
  if (!letra?.sincronizada || !letra.linhas?.length) return;
  const i = indiceAtivo(letra.linhas, posicaoMs + (letra.atrasoMs || 0));
  if (i === ativa && !forcar) return;
  const caixa = $("letra");
  const linhas = caixa.children;
  if (ativa >= 0 && linhas[ativa]) linhas[ativa].classList.remove("ativa");
  const de = forcar ? 0 : Math.max(0, Math.min(i, ativa) - 1);
  const ate = forcar ? linhas.length - 1 : Math.min(linhas.length - 1, Math.max(i, ativa) + 1);
  for (let k = de; k <= ate; k++) linhas[k].classList.toggle("passada", k < i);
  ativa = i;
  const alvo = linhas[Math.max(i, 0)];
  if (i >= 0 && alvo) alvo.classList.add("ativa");
  if (alvo && performance.now() > manualAte && caixa.clientHeight > 0) {
    const topo = alvo.offsetTop - caixa.clientHeight * 0.4 + alvo.offsetHeight / 2;
    caixa.scrollTo({ top: topo, behavior: forcar ? "auto" : "smooth" });
  }
}
