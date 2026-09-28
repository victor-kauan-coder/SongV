// Só para pré-visualizar a tela no navegador (python -m http.server em desktop/ e abra
// /ui/index.html?demo). Não entra no app: o Tauri empacota apenas a pasta ui/.
// Faixas, álbuns e artistas são sintéticos, inventados para o teste.

const albuns = [
  ["Cidade Acesa", "Maré Alta", 2024, ["Ônibus das Seis", "Janela Acesa", "Rádio Antigo", "Fim da Linha", "Minuto a Mais"]],
  ["Neon Hour", "Kaya Seo", 2025, ["Blue Hour", "Cidade da Noite", "Night Bus", "Glass Street"]],
  ["Ruído Branco", "Lua Norte", 2023, ["Estática", "Frequência", "Sinal Fraco", "Antena", "Chiado", "Silêncio"]],
  ["Válvulas", "Os Sintonizadores", 2022, ["Pré-amplificador", "Ganho", "Agulha"]],
];

export function iniciar({ definirBiblioteca, conexao }) {
  let n = 0;
  const faixas = albuns.flatMap(([album, artista, ano, titulos]) =>
    titulos.map((titulo, i) => ({
      id: `demo${n++}`, titulo, artista, album, artistaAlbum: artista, ano, faixa: i + 1, disco: 1,
      duracaoMs: 150000 + ((n * 37000) % 120000), tamanho: 1, formato: "mp3", adicionada: 1000 + n,
    })));
  definirBiblioteca("pc", faixas);
  if (new URLSearchParams(location.search).get("demo") === "celular") {
    conexao({ conectado: true, celular: "Pixel 8" }).then(() => definirBiblioteca("celular", faixas.slice(4, 13).map((f) => ({ ...f, id: `c${f.id}` }))));
  }
}
