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
| Splash | `drawable/ic_splash.xml` | marca completa recortada em círculo |
| Logo dentro do app | `drawable/logo_songv.xml` | só o V-colcheia, branco, tingido na cor de destaque (`LogoSongV`) |

A geometria cabe na zona segura de 66 dp dos ícones adaptativos; a pauta ocupa o fundo inteiro, então ela desliza por baixo do V nas animações de ícone do launcher.

**Marca em texto:** V-colcheia + "Song**V**" em Archivo Expandida Black, os dois V na cor de destaque escolhida (`MarcaSongV` em `InicioScreen.kt`). Dentro do app a logo nunca leva o quadrado laranja do ícone: ela é tinta, não etiqueta.

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
- **Fundo escolhível.** Grafite, Preto, Noite, Floresta, Vinho, Terra (e as versões claras Papel, Branco, Névoa, Sálvia, Rosé, Areia) ou uma cor livre, que `ajustarFundo()` leva até luminância ≤ 0,025 no escuro ou ≥ 0,82 no claro. A escada de superfícies (`surfaceContainer*`, `outline*`) é derivada do fundo por `lerp` em direção à cor do texto, então Grafite reproduz os tons originais e qualquer outro fundo mantém os mesmos degraus.

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
- **Acabamentos** (`ui/theme/Acabamento.kt`): a mesma cor de superfície "fabricada" de cinco jeitos, aplicados no mini player, barra de abas, cartões, botões secundários e folhas:

  | Estilo | Superfície | Fundo das telas | Botões cheios |
  |---|---|---|---|
  | Atual | cor lisa | liso | — |
  | Opaco | cor chapada puxada 10% para o destaque, sem sombra | liso | — |
  | Fosco | translúcida, clareada, grão, borda clara de 1 dp | três luzes difusas (destaque e dois vizinhos de matiz) | grão |
  | Metálico | degradê vertical + riscos escovados + bisel claro/escuro | degradê vertical + escovado | brilho anodizado |
  | Vidro | 30–45% de opacidade, reflexo diagonal, aresta brilhante | luzes difusas mais fortes | verniz na metade de cima |

  No Vidro e no Fosco, o que rola por trás do mini player e da barra de abas é desfocado com Haze (Android 12L+; antes disso, véu mais denso). A sombra some nos estilos translúcidos e no Opaco — sob vidro ela aparece através e suja o painel. Texturas (grão e escovado) são dois bitmaps de 128 px gerados uma vez; nada de imagem no APK.
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

## 6. SongV para computador

Mesmo mundo, outra superfície (`desktop/ui/`), na gramática de player de desktop que o usuário já
conhece: painéis-cartão escuros sobre um fundo quase preto, a cor da capa vazando nos cabeçalhos,
um único sinal (o play). O modo é **operar** (biblioteca, fila) com um momento de **experiência**:
o palco da letra.

- **Estrutura:** topo (logo, voltar/avançar, Início, busca em pílula "O que você quer ouvir?", Navegar, pílula do celular com LED, Configurações); três painéis — **Sua biblioteca** à esquerda, conteúdo no centro, **Tocando agora + A seguir** à direita (fecha pelo botão de fila); barra do player embaixo (capa e título · transporte e tempo · letra, fila, dispositivos, volume, tela cheia).
- **Sua biblioteca:** chips de origem (Computador / celular conectado) e de tipo (Álbuns / Artistas), busca e ordem (Recentes = tocados por último, depois adicionados; ou A–Z). "Todas as faixas" fica fixa no topo; artistas têm capa redonda; o que está tocando ganha o alto-falante no sinal.
- **Páginas:** Início (saudação pela hora, 8 atalhos, fileiras "Tocados/Adicionados recentemente" e "Seus artistas" com "Mostrar tudo"), Navegar por tudo (blocos coloridos com capa inclinada), Busca ao vivo (Melhor resultado + Músicas + Artistas + Álbuns), Álbum, Artista (Músicas + Discografia) e Todas as faixas. Álbum/artista/todas: cabeçalho em degradê com a cor dominante da capa (`corDaCapa`, luminância ≤ 0,09 para o branco por cima), título em Archivo Expandida, play grande no sinal e barra fixa com play + título quando ele sai de vista.
- **Tabela de faixas:** duplo clique ou Enter toca a partir dali; no passar do mouse o número vira play; a faixa atual fica no sinal com as barrinhas (ou o número no sinal, se pausada). Artista e álbum na linha são links.
- **Cartões:** capa + título + legenda; play flutuante aparece no passar do mouse e vira pausa quando aquela coleção está tocando.
- **Palco:** a letra-legenda sobre a cor da capa (luminância 0,1–0,2): linha ativa branca, as que vêm em preto translúcido, as que passaram em branco esmaecido. Cobre o painel central; em tela cheia cobre a janela, deixando a barra do player. Abre sozinho quando o celular manda o som.
- **Dispositivos:** popover que sai da pílula do celular ou do botão de dispositivos: este computador, o celular conectado (e "Ver as músicas dele"), o pareamento com prazo e o endereço para digitar no celular.
- **Tokens:** `--fundo`, `--sinal`, `--sinal-texto` (contraste ≥ 4,5:1 calculado como no app) e `--no-sinal` vêm das configurações; a janela é o fundo escurecido, os painéis são o fundo. Os acabamentos só definem variáveis (`--sup-cor`, `--sup-imagem`, `--sup-filtro`, `--sup-sombra`, `--ambiente`) aplicadas aos painéis — as miniaturas de estilo nas configurações usam as mesmas regras.
- **Play:** o grande é círculo no sinal; o da barra é círculo claro com ícone escuro; triângulos centrados pelo baricentro. Com nada tocando, o play toca a biblioteca.
- **Janelas menores:** abaixo de 1100 px a biblioteca vira trilho de capas e o painel da direita sai; abaixo de 860 px a barra esconde volume, fila e tela cheia.

## 7. Estrutura de navegação

Três destinos na barra inferior — **Início** (descobrir), **Buscar** (encontrar), **Biblioteca** (organizar: Faixas, Álbuns, Artistas, Playlists). Detalhes (álbum, artista, playlist, configurações) empilham sobre a aba mantendo barra e mini player visíveis. Player e fila são camadas por cima de tudo. O "voltar" do sistema desfaz exatamente um nível.
