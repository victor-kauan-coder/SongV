# SongV

App Android nativo (Kotlin + Jetpack Compose), estilo Spotify, 100% offline. Toca os MP3s
gerados pelo agente (capa e letra sincronizada embutidas via ID3) sem backend e sem rede.

## Por que existe um parser ID3 manual

Bibliotecas prontas (ex: `mp3agic`) leem `USLT` (letra simples) mas não `SYLT` (letra
sincronizada com timestamp por linha). O agente (`server.py`) grava os MP3s com `mutagen`,
salvando como **ID3v2.3**, com os frames:

- `TIT2` — título
- `TPE1` — artista
- `APIC` — capa (tipo 3, "cover front")
- `SYLT` — letra sincronizada (formato de tempo = milissegundos, idioma "por")
- `USLT` — letra simples (fallback, quando não achou letra sincronizada na LRCLIB)

Todos gravados com encoding UTF-8. `Id3Parser.kt` lê os bytes diretamente do arquivo pra
cobrir exatamente esse conjunto de frames, incluindo a diferença entre tamanho de frame
sync-safe (v2.4) e tamanho normal de 32 bits (v2.3, que é o que o agente usa).

## Estrutura

```
app/src/main/java/com/songv/app/
├── id3/       → Id3Parser.kt, Id3Frames.kt (parser manual de ID3v2)
├── model/     → Musica.kt, LinhaLetra.kt
├── data/      → MusicaRepository.kt (varredura via MediaStore)
├── player/    → PlayerViewModel.kt (ExoPlayer), MusicPlaybackService.kt (notificação/background)
└── ui/        → MainActivity.kt, theme/, screens/ (Biblioteca, Player, Permissão, Seletor de Tema)
```

## Temas

**Dez opções de tema**, sempre acessíveis pelo ícone de paleta na barra superior:
Claro, Escuro, **Roxo Dark** (identidade principal do app), Verde (estilo Spotify), Azul,
Sunset, Rosa, Âmbar, Ciano, e **Personalizado** — um seletor de cor livre (matiz + saturação/
valor, arraste para escolher) que gera um esquema de cores escuro em torno da cor escolhida.
A escolha de tema (incluindo a cor personalizada) **persiste entre aberturas do app** via
DataStore.

## Funcionalidades

- **Busca**: ícone de lupa na biblioteca abre um campo de busca que filtra por título ou
  artista em tempo real.
- **Favoritos**: toque no coração (na lista ou na tela do player) para favoritar/desfavoritar.
  Os favoritos persistem entre aberturas e têm uma aba dedicada na biblioteca.
- **Fila de reprodução**: ícone de fila na tela do player mostra a faixa atual e o que vem a
  seguir; pressione e segure a alça de arraste para reordenar, ou toque numa faixa da fila
  para pular direto para ela.
- **Shuffle / Repeat**: botões na tela do player alternam embaralhar e os três modos de
  repetição (desligado / repetir tudo / repetir uma).
- **Letra sincronizada centralizada e fluida**: a linha ativa fica centralizada verticalmente
  no meio da tela (não perto do topo) e "salta" com uma animação de mola (spring) tanto na
  escala quanto na cor — em vez de uma transição linear mecânica.

## Como compilar

Este ambiente de geração não tem acesso à rede do Google Maven / Gradle, então o projeto
**não foi compilado neste ambiente** — foi revisado manualmente (balanceamento de chaves,
imports, tipos, assinaturas de função batendo entre tela e chamador) mas o build real
acontece no seu Android Studio. **A primeira versão do projeto já foi compilada com sucesso
no Android Studio** (BUILD SUCCESSFUL) antes desta rodada de mudanças; se aparecer algum erro
novo depois de atualizar os arquivos, me manda o log do Build Output que eu conserto.

1. Abra a pasta `SongV/` no Android Studio (Koala ou mais recente) — ele vai baixar o
   Gradle wrapper e sincronizar as dependências automaticamente.
2. Ou via linha de comando, com o Android SDK e JDK 17 instalados:
   ```
   cd SongV
   ./gradlew assembleDebug
   ```
   O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.
3. Instale num aparelho com Android 8.0+ (`minSdk 26`) e conceda a permissão de áudio
   quando solicitado.

## Limitações conhecidas (por design, conforme combinado)

- **Sem streaming/rede**: só toca o que já está no celular.
- **Varredura manual**: pasta padrão é `Music`. Se adicionar músicas depois, é preciso
  tocar no botão de atualizar (ícone de refresh na barra superior).
- **Letra sincronizada depende do agente**: nem toda música vai ter `SYLT` — quando só
  existe `USLT`, a letra aparece estática sem destaque; quando não existe nenhuma, a
  seção de letra some e fica só player + capa.
- **Sem pasta customizável ainda**: hoje fixa em "Music" — dá pra adicionar uma tela de
  configurações pra trocar isso numa próxima iteração.
- **Bounce da letra**: por pedido, é um efeito de escala/salto na linha inteira (spring
  animation), não um preenchimento palavra por palavra estilo karaokê.

## Próximos passos sugeridos

- Tela de configurações pra trocar a pasta varrida.
- Ordenar a biblioteca por outros critérios (artista, data de adição).
- Efeito karaokê palavra por palavra, se quiser evoluir o bounce atual.
- Miniplayer com swipe para pular faixa.
