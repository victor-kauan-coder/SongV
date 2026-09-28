# Novidades

Todas as mudanças relevantes do SongV. O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [Não lançado]

### Novidades
- **SongV para computador 1.1.0 — interface nova**, no jeito dos players de desktop: **Sua biblioteca** à esquerda (álbuns e artistas, filtro, ordem por recentes ou A–Z, chips para trocar entre o computador e o celular), **Tocando agora + A seguir** à direita e a busca em pílula no topo, com resultados enquanto você digita (Melhor resultado, Músicas, Artistas, Álbuns).
- Páginas de **álbum, artista e Todas as faixas** com cabeçalho na cor da capa, play grande, barra fixa ao rolar e tabela de faixas (duplo clique toca, artista e álbum viram links). Início com saudação, atalhos e fileiras de recentes; **Navegar por tudo** em blocos coloridos.
- **Dispositivos** na barra do player e na pílula do celular: ver quem está conectado, parear e o endereço para digitar no celular.
- **Tela cheia** com a letra; **voltar/avançar** (também pelos botões laterais do mouse e Alt+setas); Ctrl+K ou Ctrl+F vai para a busca; Ctrl+setas trocam de faixa; botão de sem som.

## [2.2.0] — 2026-09-27

### Novidades
- **SongV para computador (Windows)**: player completo com a biblioteca do computador — capas, letras sincronizadas (SYLT, `.lrc`), busca sem acentos, álbuns, artistas, aleatório e repetir — com as mesmas cores de destaque, cores de fundo e estilos do app.
- **Conectar ao computador** (Configurações › Computador): na mesma rede, mesmo **sem internet**, o computador navega e toca as músicas do celular. Reconecta sozinho ao abrir o app.
- **Tocar em…** no player: o som do celular sai no computador, com capa e letra (e tradução) na tela grande; o celular segue no controle e volta a tocar nele mesmo sem parar a música. Endereço manual para quando a descoberta automática não funciona (hotspot).
- Segurança: pareamento com código de 6 dígitos (ECDH P-256 com compromisso), tudo cifrado com AES-256-GCM, chaves no Keystore do Android e no Gerenciador de Credenciais do Windows.

### Correções
- O timer de sono não devolve mais o volume do celular enquanto o som está saindo no computador.

## [2.1.0] — 2026-09-27

### Novidades
- **Cor do fundo**: Grafite, Preto, Noite, Floresta, Vinho, Terra (Papel, Branco, Névoa, Sálvia, Rosé e Areia no tema claro) ou qualquer cor, sempre ajustada para o texto continuar legível. Os tons das superfícies acompanham o fundo.
- **Estilo do app**: Atual, **Opaco** (cores chapadas), **Fosco** (acrílico jateado), **Metálico** (alumínio escovado) e **Vidro** (painéis translúcidos). No Vidro e no Fosco, o conteúdo que passa por trás do mini player e da barra de abas é desfocado (Android 12L+; antes disso, um véu translúcido).
- **Logo no app**: o V-colcheia aparece ao lado do nome, na cor de destaque escolhida.
- **Trocar a capa** de uma faixa (menu da faixa) ou do álbum inteiro (tela do álbum) por uma imagem da galeria, com **Voltar à capa original**. A imagem é endireitada, recortada em quadrado e guardada uma vez só, mesmo para o álbum todo; notificação e tela de bloqueio mostram a capa nova na hora.

### Correções
- Na grade "Tocadas recentemente", as capas podiam aparecer trocadas depois que a ordem mudava.

## [2.0.1] — 2026-09-27

### Correções
- **Busca de letra online** não achava músicas que existem na LRCLIB. Agora:
  - a duração do arquivo não descarta mais resultados (clipes e versões com intro mais longa ficavam de fora pelo filtro de ±4 s);
  - títulos vindos do YouTube são limpos (`(Official Music Video)`, `| Legendado`, `- Clipe Oficial`, `feat.`…) e nomes de canal viram artista (`ImagineDragonsVEVO` → Imagine Dragons, `Banda - Topic` → Banda);
  - "Artista - Música" no título é tentado nas duas leituras;
  - as consultas rodam em paralelo, com nova tentativa quando a LRCLIB responde 429/5xx;
  - os resultados recebem nota por título, artista, duração e sincronia, e a versão sincronizada é preferida.
- Quando não há internet validada (rede local sem saída), o app não bloqueia mais a busca antes de tentar.
- A biblioteca se atualiza sozinha quando músicas são copiadas, apagadas ou alteradas com o app aberto.

### Novidades
- **Escolher outra versão da letra**: pesquisa manual por título e artista, com selos de *Recomendada*, *Sincronizada* e diferença de duração.
- Botão para atualizar a pasta da biblioteca na tela inicial vazia.

## [2.0.0] — 2026-09-26

Reformulação completa: nova identidade visual, nova navegação, letras tratadas como legendas e dezenas de correções.

### Identidade
- Novo ícone: o **V é uma colcheia** escrita sobre um pentagrama, em grafite sobre laranja. Inclui ícone monocromático (Android 13+), ícone de notificação e splash.
- Visual **hi-fi analógico**: grafite quente + laranja-sinal, tipografia **Archivo** (normal, condensada e expandida), cantos contidos e "LEDs" nos botões de modo.
- Temas **Escuro, Claro e Sistema** + 7 cores de destaque e seletor livre, com contraste calculado automaticamente.

### Novidades
- Navegação em três abas: **Início**, **Buscar** e **Biblioteca** (Faixas, Álbuns, Artistas, Playlists).
- **Tradução bilíngue** linha a linha, **romanização** e opção "só a tradução"; a tradução continua ligada entre faixas e fica salva no aparelho.
- **Buscar letra online** (LRCLIB), **importar `.lrc`**, **ajuste de sincronia** por faixa e **tamanho do texto** da letra.
- Suporte a **LRC** (arquivo ao lado da música e LRC dentro do `USLT`), com várias marcações de tempo por linha e `[offset:]`.
- Indicador animado em **introduções e trechos instrumentais**; toque numa linha para pular até ela; a letra para de rolar sozinha quando você rola com o dedo.
- Tela de **álbum**, **artista** e **playlist** com capa grande e fundo tingido pela capa; **mosaico** nas playlists.
- **Início** com tocadas recentemente, mais tocadas, álbuns, adicionadas recentemente e artistas.
- **Busca sem acentos** em faixas, álbuns, artistas e playlists, com buscas recentes.
- **Fila** com tocar a seguir, adicionar à fila, arrastar para reordenar (também no aleatório), deslizar para remover e limpar.
- **Timer de sono**, **equalizador do sistema**, **retomar de onde parou**, **compartilhar** e **detalhes do arquivo**.
- Mini player com progresso, play/pause, próxima e **arrastar para trocar de faixa**; carrossel de capas no player.
- Configurações de **pasta da biblioteca** (detectada automaticamente) e **ignorar áudios curtos**.
- Crédito ao autor na tela Sobre.

### Correções
- O projeto não compilava (ícones `AutoMirrored` sem import).
- O app podia ser encerrado pelo sistema segundos depois de aberto: o serviço era iniciado em primeiro plano sem notificação quando nada tocava.
- Girar a tela ou tocar em "atualizar" reiniciava a música e a fila.
- A biblioteca não carregava no Android 8 e 9 (coluna `RELATIVE_PATH` inexistente).
- A tradução falhava com frequência: disparava uma requisição por linha em paralelo e estourava o limite do serviço.
- A mensagem de erro da tradução ficava na tela para sempre.
- Letra sem sincronia aparecia quase invisível (22% de opacidade).
- A linha ativa mudava de tamanho de fonte e o texto "pulava" de linha.
- Antes da primeira linha, a primeira linha já aparecia como ativa.
- Capa do player borrada (decodificada a ~300 px e ampliada) e capa errada em arquivos com várias imagens.
- Capas claras deixavam o título do player ilegível; a paleta era extraída na thread principal.
- A fila mostrava a ordem errada no modo aleatório; "tocar a seguir" no aleatório não tocava a seguir.
- Reordenar a fila movia a faixa errada depois do primeiro passo.
- Excluir uma playlist voltava duas telas e não pedia confirmação.
- Nomes de playlist com `::` ou `,` corrompiam a playlist.
- "Adicionadas recentemente" mostrava as primeiras em ordem alfabética.
- O ícone de play do mini player não era clicável.
- A busca prometia ignorar acentos, mas não ignorava.
- Faixas sem tag de álbum viravam um "álbum" com o nome da pasta.
- Sem foco de áudio: o SongV tocava por cima de outros apps e continuava alto ao desconectar o fone.
- Voltar para a faixa anterior não reiniciava a faixa atual primeiro.
- Negar a permissão de vez deixava o botão sem efeito.
- A notificação e a tela de bloqueio não recebiam a capa.

### Desempenho
- Índice da biblioteca em disco: aberturas seguintes quase instantâneas; leitura das tags em paralelo e numa única leitura por arquivo (de ~1 s para ~18 ms por faixa no armazenamento compartilhado).
- Capas sob demanda com cache em disco e em memória, em vez de todas as capas em memória.
- A posição da música não recompõe mais o app inteiro 5 vezes por segundo.
- APK de release com R8: de ~20 MB para ~3 MB.

## [1.0.0]

Primeira versão: biblioteca da pasta Music, player com letra `SYLT`/`USLT`, favoritos, playlists, fila e temas de cor.

[2.2.0]: https://github.com/victor-kauan-coder/SongV/releases/tag/v2.2.0
[2.1.0]: https://github.com/victor-kauan-coder/SongV/releases/tag/v2.1.0
[2.0.1]: https://github.com/victor-kauan-coder/SongV/releases/tag/v2.0.1
[2.0.0]: https://github.com/victor-kauan-coder/SongV/releases/tag/v2.0.0
