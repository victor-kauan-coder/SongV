# Novidades

Todas as mudanças relevantes do SongV. O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

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

[2.0.0]: https://github.com/victor-kauan-coder/SongV/releases/tag/v2.0.0
