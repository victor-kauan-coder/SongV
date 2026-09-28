# SongV no computador

O SongV para computador é um **player completo** para Windows, com a mesma cara do app: toca as
músicas guardadas no computador e, com o celular conectado na mesma rede, também **as músicas do
celular**. E o celular pode mandar o som dele para o computador, como escolher uma caixa no Spotify
Connect: no player do celular, **Tocar em…** › o nome do computador.

Tudo funciona **sem internet** — basta os dois estarem na mesma rede local (um Wi-Fi sem internet ou
o roteador do próprio celular). Nada passa por servidor nenhum.

## O que dá para fazer

| No computador | No celular |
|---|---|
| Tocar a pasta de músicas do computador (MP3, FLAC, M4A, OGG, Opus, WAV) com capas e letras sincronizadas (SYLT, `.lrc`) | Mandar o som para o computador em **Tocar em…** e voltar para o celular sem parar a música |
| Navegar e tocar a **biblioteca do celular** conectado (capas, letras e o arquivo original vêm pela rede) | Continuar no controle: fila, aleatório, timer, fone Bluetooth e notificação seguem funcionando |
| Ver a letra grande, como legenda, com tradução e romanização quando o celular manda o som | Conectar/desconectar e esquecer computadores em **Configurações › Computador** |
| Cor de destaque, cor do fundo e os estilos Atual, Opaco, Fosco, Metálico e Vidro | |
| Interface de player de desktop: biblioteca à esquerda, fila à direita, busca ao vivo, páginas de álbum e artista, tela cheia com letra | |

## Como funciona

```
 CELULAR                                             COMPUTADOR
 ┌──────────────────────────┐   mDNS _songv._tcp    ┌──────────────────────────────┐
 │ ExoPlayer (fila, tempo)  │ ◄──── descoberta ──── │ anuncia o nome do computador │
 │ ConexaoComputador ───────┼── TCP + AES-256-GCM ─►│ sessão (Rust, tokio)         │
 │   biblioteca, capas,     │◄── biblioteca / ler ──│ biblioteca do computador     │
 │   letras, bytes          │── dados / capa ──────►│ songv://local  songv://audio │
 │   "Tocar em…": comandos  │── tocar/pausar/buscar►│ WebView2 <audio> decodifica  │
 └──────────────────────────┘                       └──────────────────────────────┘
```

Duas coisas independentes:

- **Conectado.** O celular fica conectado ao computador (reconecta sozinho ao abrir o app, se a opção
  estiver ligada). O computador pede a lista de faixas, as capas, as letras e os bytes das faixas que
  vai tocar. O som do celular continua saindo do celular.
- **Som no computador** ("Tocar em…"). O ExoPlayer do celular continua sendo a fonte da verdade e toca
  a fila em **volume 0**; cada mudança (faixa, play/pausa, busca, aleatório) vira uma mensagem e o
  computador toca o **arquivo original**. Notificação, tela de bloqueio, fone Bluetooth e timer de sono
  continuam funcionando sem mudança. Voltar para o celular é instantâneo: ele já está na posição certa,
  só volta o volume.

Se, com o som do celular saindo nele, alguém escolher outra música **no computador**, o computador
avisa o celular, que pausa e volta a ser a própria saída.

### Qualidade

Os bytes do arquivo vão do celular para o computador exatamente como estão e são decodificados lá
(motor de mídia do WebView2/Chromium). Não há recompressão: a qualidade é a do arquivo.

### Sincronia

O computador informa a posição a cada segundo; o celular mede a latência (ping) e, se o player mudo
dele se afastar mais de 150 ms, é **o celular que se ajusta** (busca silenciosa). O som do computador
nunca pula por correção. Medido no teste: ~0,1 s de diferença.

## Segurança

| Ameaça | Proteção |
|---|---|
| Alguém na mesma rede escutar | Tudo — comandos, biblioteca, letras, capas e áudio — vai cifrado com **AES-256-GCM**, com contador de nonce por direção (repetição ou reordenação derruba a sessão). |
| Um aparelho se passar pelo outro | **Pareamento com código de 6 dígitos**: troca de chaves **ECDH P-256** com *commitment* (o celular se compromete com a chave antes de ver a do computador, então um intermediário não consegue fabricar códigos iguais). Os dois mostram o código e você confirma nos dois. |
| Pareamento indesejado | O computador só aceita pareamento novo durante os 3 minutos depois de clicar em **Parear**. |
| Chave vazada do disco | A chave de cada par fica no **Keystore do Android** e no **Gerenciador de Credenciais do Windows**, nunca em texto. |
| Computador pedir outros arquivos | O celular só entrega faixas da biblioteca do próprio app (a pasta configurada), nunca arquivos soltos. |
| Rastro no computador | Faixas vindas do celular ficam **só na memória** (a atual e a próxima). Nada de cache em disco. |

A cada conexão, as chaves de sessão são novas (HKDF da chave do par com nonces aleatórios dos dois
lados). Dá para **esquecer** o par dos dois lados a qualquer momento.

## Desempenho e armazenamento

- **Tauri 2** (Rust + WebView2 do próprio Windows): instalador de poucos MB e pouca memória, em vez de
  levar um Chromium ou uma JVM junto. Interface em HTML, CSS e JS puros, sem framework.
- Biblioteca do computador lida com `lofty` e guardada num índice: na abertura seguinte, só relê o que
  mudou (tamanho ou data). Capas sob demanda, com cache de 48 em memória.
- Faixas do celular chegam em blocos de 1 MB, com o próximo já pedido; a reprodução começa assim que o
  primeiro bloco chega e a próxima faixa é baixada antes de a atual acabar.
- Fontes Archivo reduzidas ao alfabeto latino (≈100 KB no total).

## Sem internet

- **Mesma rede Wi-Fi, sem internet:** o Android costuma mandar o tráfego pelos dados móveis quando o
  Wi-Fi "não tem internet". O SongV **amarra o socket à rede local** certa, então funciona mesmo assim.
- **Roteador do celular (hotspot):** conecte o computador ao hotspot. Se a descoberta automática não
  achar o computador, use **Adicionar pelo endereço** no celular e digite o IP:porta que aparece em
  **Configurações › Celular** no computador.
- Na primeira execução o Windows pode pedir permissão de rede: permita em **redes privadas**.

## Protocolo (v1)

Quadros: `u32 big-endian` com o tamanho + conteúdo. Antes da sessão, `0x00 ‖ JSON`; depois,
`AES-256-GCM(tipo ‖ carga)` com nonce = `direção(4 bytes) ‖ contador(8 bytes)`.

**Pareamento** (celular = C, computador = N):

1. C → N `{"t":"ola","v":1,"modo":"parear","id","nome","compromisso": SHA-256(pubC ‖ nC)}`
2. N → C `{"t":"ola","id","nome","pub": pubN, "nonce": nN}`
3. C → N `{"t":"revela","pub": pubC, "nonce": nC}` — N confere o compromisso.
4. Os dois calculam `Z = ECDH`, `K = HKDF(Z, nC ‖ nN, "songv-par")` e o código
   `HKDF(Z, nC ‖ nN, "songv-codigo") mod 10⁶`. Cada lado manda `{"t":"decisao","ok":…}` quando o usuário
   decide; com os dois de acordo, K é guardada e a sessão segue como uma reconexão.

**Reconexão:** C → N `{"t":"ola","modo":"sessao","id","nonce": nC}`, N → C `{"t":"ola","id","nome","nonce": nN}`.
Chaves de sessão: `HKDF(K, nC ‖ nN, "songv-c2n")` e `"songv-n2c"`. O primeiro quadro cifrado de cada lado
é `{"t":"pronto"}` — se não decifra, a chave está errada e a conexão cai.

**Mensagens** (JSON, tipo 1):

| De | Mensagem | Conteúdo |
|---|---|---|
| N | `biblioteca` | pede a lista; C responde `biblioteca` com as faixas |
| N | `capa?` / `letra?` | pede capa (C responde com quadro tipo 3) ou letra (C responde `letra`) |
| N | `ler` | pedido, faixa, início, tamanho — C responde com blocos de áudio |
| C | `tocar` | "Tocar em…": faixa, posição, tocando, próxima, cor de destaque |
| C | `pausar` / `retomar` / `buscar` / `volume` / `sair` | estado do player do celular |
| C | `letra` | linhas com tempo, tradução e romanização, atraso da faixa |
| N | `estado` | faixa, posição, tocando, carregando (1×/s e a cada mudança) |
| N | `comando` | `retomar`, `pausar`, `proxima`, `anterior`, `buscar`, `devolver` |
| N | `terminou` / `saida-livre` | a faixa acabou aqui / o computador passou a tocar a própria fila |
| C/N | `ping` / `pong` | latência e sinal de vida (a cada 4 s) |

Quadros binários: tipo 2 = bloco de áudio (`u32 pedido ‖ u64 início ‖ bytes`), tipo 3 = capa
(`u16 tamanho do id ‖ id ‖ imagem`).

## Onde está no código

| Parte | Arquivos |
|---|---|
| Computador — rede, cripto, biblioteca | `desktop/src-tauri/src/` (`sessao.rs`, `cripto.rs`, `quadros.rs`, `pares.rs`, `faixas.rs`, `biblioteca.rs`, `letra.rs`, `descoberta.rs`, `lib.rs`) |
| Computador — interface | `desktop/ui/` (`index.html`, `estilo.css`, `app.js`, `player.js`, `letra.js`, `ponte.js`) |
| Celular | `app/src/main/java/com/songv/app/conexao/` e `ui/screens/SaidaFolha.kt` |
| Testes | `cargo test` em `desktop/src-tauri` (cripto com vetores independentes, quadros, blocos, LRC, SYLT/USLT) e `CriptoTest.kt` no Android (mesmos vetores) |

## Compilar

```bash
cd desktop
npm install
npx tauri build
```

O instalador sai em `desktop/src-tauri/target/release/bundle/nsis/`. Para testar sem compilar a
interface no app, sirva a pasta `desktop/` (`python -m http.server`) e abra `ui/index.html?demo`
(dados sintéticos).
