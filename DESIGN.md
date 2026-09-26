# SongV — sistema visual

> **Hi-fi analógico.** Superfícies em grafite quente, como o alumínio anodizado de um aparelho de som, e um único laranja-sinal reservado para o que é ação ou estado ativo — o botão de play, a faixa tocando, a luz de um modo ligado. A capa do álbum é a protagonista; a letra é tratada como legenda de cinema.

O código é a fonte da verdade: as cores vivem em [`ui/theme/Cores.kt`](app/src/main/java/com/songv/app/ui/theme/Cores.kt), a tipografia em [`Tipografia.kt`](app/src/main/java/com/songv/app/ui/theme/Tipografia.kt) e as formas em [`Theme.kt`](app/src/main/java/com/songv/app/ui/theme/Theme.kt). Este documento explica as decisões.

---

## 1. Marca

<img src="docs/imagens/logo.png" width="128" align="right" alt="Ícone do SongV">

**O V é uma colcheia.** O logo é um V caligráfico escrito sobre um pentagrama:

- o traço **esquerdo** é largo, como o golpe de uma pena, e afina ao chegar embaixo;
- o traço **direito** é a **haste** de uma nota, com a **bandeirola** de colcheia no topo;
- o vértice do V é a **cabeça da nota**, apoiada na última linha da pauta;
- um contorno na cor do fundo interrompe as linhas da pauta ao redor do V, como numa partitura impressa.

| Camada | Arquivo | Conteúdo |
|---|---|---|
| Fundo adaptativo | `drawable/ic_launcher_background.xml` | laranja com luz radial + pauta creme a 55% |
| Frente adaptativa | `drawable/ic_launcher_foreground.xml` | V-colcheia grafite com contorno laranja |
| Monocromático (Android 13+) | `drawable/ic_launcher_monochrome.xml` | só a silhueta do V-colcheia |
| Notificação | `drawable/ic_stat_songv.xml` | silhueta branca 24 dp |
| Splash / permissão | `drawable/ic_splash.xml` | marca completa recortada em círculo |

A geometria cabe na zona segura de 66 dp dos ícones adaptativos; a pauta ocupa o fundo inteiro, então ela desliza por baixo do V nas animações de ícone do launcher.

**Marca em texto:** "Song**V**" em Archivo Expandida Black, com o V em laranja-sinal (`MarcaSongV` em `InicioScreen.kt`).

---

## 2. Cor

Estratégia **contida**: neutros quentes + um acento. O laranja aparece pouco e sempre significa alguma coisa.

### Tokens de marca (`object Marca`)

| Token | Hex | Uso |
|---|---|---|
| `Laranja` | `#FF6B1A` | sinal: botão de play, faixa ativa, LED de modo, chips selecionados |
| `Tinta` | `#1C130E` | texto e ícones **sobre** o laranja (contraste 6,7:1) |
| `Grafite0…5` | `#0E0C0B` → `#342E2A` | superfícies do tema escuro, do mais fundo ao mais alto |
| `TextoClaro` / `…Secundario` | `#F3EEE9` / `#B9AFA7` | texto no escuro (15:1 e 8,5:1 sobre o fundo) |
| `Papel0…4` | `#FFFFFF` → `#DAD3CE` | superfícies do tema claro — cinza quente, **não** creme |
| `TextoEscuro` / `…Secundario` | `#1B1613` / `#5C524B` | texto no claro |
| `Palco` | `#110E0D` | fundo do player, sempre escuro |

### Regras

- **Contraste calculado, não chutado.** `ajustarContraste()` clareia ou escurece qualquer cor de destaque — inclusive a escolhida no seletor livre — até atingir 4,5:1 contra o fundo. Por isso o laranja vira `#C2410C`-ish nos textos do tema claro, mas continua `#FF6B1A` nos preenchimentos.
- **O player é um palco.** Em qualquer tema ele é escuro; a cor dominante da capa é escurecida (`tomDePalco`) até o texto branco sempre ter contraste, e desce em gradiente até o `Palco`.
- **Destaques alternativos** (Âmbar, Vermelho, Rosa, Violeta, Azul, Verde, Sua cor) trocam só o sinal; os neutros quentes continuam.
- Legendas de tradução usam o sinal misturado 42% com branco — ligadas à marca, mas sem competir com a linha original.

---

## 3. Tipografia

Uma família, três larguras: **Archivo** (SIL OFL 1.1), com instâncias estáticas geradas a partir da fonte variável (fontes variáveis por recurso são instáveis no Android 8–9).

| Papel | Família | Peso | Tamanho | Onde |
|---|---|---|---|---|
| Marca / display | Archivo **Expandida** | 900 | 24–44 sp | logo em texto, números do ranking |
| Títulos de tela | Archivo | 800 | 26–30 sp | "Biblioteca", "Buscar" |
| Títulos e rótulos | Archivo | 600–700 | 14–22 sp | seções, nomes de faixas |
| Corpo | Archivo | 400 | 12–16 sp | subtítulos, descrições |
| **Letra** | Archivo **Condensada** | 700 | 28 sp × escala | linhas da letra (cabem mais palavras) |
| Legenda (tradução) | Archivo | 500 | 17 sp × escala | tradução e romanização |

- Números de tempo usam **algarismos tabulares** (`tnum`) para não "dançarem" enquanto mudam.
- A linha ativa da letra **não muda de tamanho de fonte** — só de opacidade e uma escala visual leve. Trocar o tamanho reflui o texto e faz a lista pular.

---

## 4. Forma, profundidade e movimento

- **Cantos contidos, de equipamento:** 4 / 6 / 10 / 16 / 24 dp. Capas pequenas com 6 dp, capas grandes com 10 dp. Pílulas só para ações e filtros.
- **Profundidade tonal** (camadas de grafite), sombra só onde há objeto físico: a capa no player e o mini player.
- **Movimento com propósito:** navegação em eixo horizontal (entrar/voltar), player e fila sobem de baixo, a letra rola com `FastOutSlowIn` de ~520 ms, o botão de play afunda com mola ao ser tocado. Nada de animação decorativa em loop, exceto o indicador de "tocando agora" — que é estado.

---

## 5. Componentes de assinatura

| Componente | Arquivo | O que o torna do SongV |
|---|---|---|
| **LED de modo** | `BotaoModo` (`Comuns.kt`) | ponto laranja sob o ícone quando aleatório/repetir estão ligados, como a luz de um aparelho |
| **Painel rotulado** | `PainelAcoes` (`PlayerScreen.kt`) | Letra · Fila · Timer · Equalizador como teclas com rótulo, não ícones soltos |
| **Letra-legenda** | `LetraView.kt` | original + romanização + tradução empilhadas por linha; indicador de pausa nos trechos instrumentais; esmaecimento nas bordas |
| **Capa gerada** | `CapaGerada` (`Capa.kt`) | faixas sem capa ganham cor derivada do álbum, iniciais em Archivo Expandida e sulcos de vinil |
| **Mosaico de playlist** | `MosaicoCapas` | 2×2 de álbuns diferentes |
| **Barra de progresso** | `BarraProgresso` | fina, engrossa ao arrastar, tempos tabulares |

---

## 6. Estrutura de navegação

Três destinos na barra inferior — **Início** (descobrir), **Buscar** (encontrar), **Biblioteca** (organizar: Faixas, Álbuns, Artistas, Playlists). Detalhes (álbum, artista, playlist, configurações) empilham sobre a aba mantendo barra e mini player visíveis. Player e fila são camadas por cima de tudo. O "voltar" do sistema desfaz exatamente um nível.
