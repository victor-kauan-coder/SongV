"""
Gera uma biblioteca de demonstração para testar o SongV (e tirar as capturas da documentação).

Tudo é sintético: o áudio são acordes gerados pelo ffmpeg, as capas são desenhadas aqui e as
letras, artistas e álbuns são fictícios. As tags seguem o mesmo formato do agente que alimenta
o app (mutagen, ID3v2.3): TIT2, TPE1, TALB, TPE2, TRCK, TYER, APIC, SYLT e USLT.

Uso:
    pip install mutagen pillow
    python ferramentas/biblioteca_demo.py saida/
    adb push saida/. "/sdcard/Music/SongV Demo/"
    adb shell content call --method scan_volume --uri content://media --arg external_primary

Requer ffmpeg no PATH.
"""
import io
import math
import random
import subprocess
import sys
from pathlib import Path

from mutagen.id3 import APIC, ID3, SYLT, TALB, TIT2, TPE1, TPE2, TRCK, TYER, USLT
from PIL import Image, ImageDraw, ImageFilter


def capa(estilo: str, cores: list[str], semente: int) -> bytes:
    rnd = random.Random(semente)
    tam = 900
    img = Image.new("RGB", (tam, tam), cores[0])
    d = ImageDraw.Draw(img)
    if estilo == "sol":  # sol poente sobre faixas horizontais
        for i in range(14):
            y = tam * 0.55 + i * 28
            d.rectangle([0, y, tam, y + 14], fill=cores[2])
        d.ellipse([tam * 0.18, tam * 0.12, tam * 0.82, tam * 0.76], fill=cores[1])
        for i in range(8):
            y = tam * 0.46 + i * 18
            d.rectangle([0, y, tam, y + 6 + i], fill=cores[0])
    elif estilo == "ruido":  # granulado com bloco sólido
        for _ in range(26000):
            x, y = rnd.randrange(tam), rnd.randrange(tam)
            d.point((x, y), fill=cores[1])
        d.rectangle([tam * 0.12, tam * 0.62, tam * 0.88, tam * 0.7], fill=cores[2])
        img = img.filter(ImageFilter.GaussianBlur(0.6))
    elif estilo == "neon":  # faixas diagonais
        for i in range(-tam, tam * 2, 70):
            d.polygon([(i, 0), (i + 36, 0), (i + 36 - tam, tam), (i - tam, tam)], fill=cores[1 + (i // 70) % 2])
        d.ellipse([tam * 0.3, tam * 0.3, tam * 0.7, tam * 0.7], fill=cores[0])
    elif estilo == "valvula":  # anéis concêntricos
        for i, r in enumerate(range(tam // 2, 20, -38)):
            d.ellipse([tam / 2 - r, tam / 2 - r, tam / 2 + r, tam / 2 + r], fill=cores[1 + i % 2])
        d.ellipse([tam / 2 - 60, tam / 2 - 60, tam / 2 + 60, tam / 2 + 60], fill=cores[0])
    saida = io.BytesIO()
    img.save(saida, "JPEG", quality=90)
    return saida.getvalue()


def audio(caminho: Path, segundos: int, base_hz: float) -> None:
    acorde = [base_hz, base_hz * 5 / 4, base_hz * 3 / 2]
    entradas = []
    for f in acorde:
        entradas += ["-f", "lavfi", "-i", f"sine=frequency={f:.2f}:duration={segundos}"]
    filtro = f"amix=inputs={len(acorde)},volume=0.35,afade=t=in:d=2,afade=t=out:st={segundos - 3}:d=3"
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", *entradas, "-filter_complex", filtro, "-ac", "1", "-b:a", "96k", str(caminho)],
        check=True,
    )


def sincronizada(texto: str, inicio_ms: int = 8000, passo_ms: int = 4200) -> list[tuple[str, int]]:
    """Linhas vazias viram trechos instrumentais (a pausa dura o dobro)."""
    linhas, t = [], inicio_ms
    for linha in texto.strip("\n").split("\n"):
        linhas.append((linha.strip(), t))
        t += passo_ms * (2 if not linha.strip() else 1)
    return linhas


LETRAS = {
    "luz": """
A luz da esquina acende antes de mim
O asfalto guarda o calor do fim do dia
Eu conto os carros como quem conta estrelas
E cada farol parece uma melodia

Se a cidade dorme, eu fico acordado
Ouvindo o rádio que ninguém mais ligou
A luz da esquina acende antes de mim
E o que eu não disse, a noite já cantou
""",
    "onibus": """
O ônibus das seis nunca chega na hora
Mas hoje eu não tenho pressa de ir embora
A janela embaçada desenha um mapa
De tudo que eu queria e ainda não tinha

Segura firme, a curva vem aí
Segura firme, eu já cheguei aqui
""",
    "varanda": """
Na varanda o vento mexe nas cortinas
A tarde inteira cabe num café
Não tem refrão, não tem compasso certo
Só o barulho bom de estar de pé
""",
    "radio": """
Turn the dial until the static breaks
There's a voice out there that nobody takes
I keep a frequency just for you
Somewhere between the red and blue

Radio, radio, don't let me go
Play me the song that I already know
""",
    "tide": """
The tide is low and the lights are on
I count the boats until they're gone
Every wave is a word I couldn't say
So I let the water carry it away
""",
    "cidade": """
밤의 도시가 나를 부르네
불빛 사이로 너를 찾았어
조용한 거리 위에 서서
우리의 노래를 다시 불러

기억해 줘 이 밤을
잊지 마 이 노래를
""",
    "bluehour": """
Blue hour on the rooftop, city holding its breath
Every window a story, every story unsaid
We were young in the neon, we were loud in the rain
Blue hour on the rooftop, call my name again
""",
    "frequencia": """
Ajusta a frequência, deixa o grave entrar
A válvula esquenta antes de tocar
Tem chiado no começo, tem calor no fim

Ajusta a frequência, aumenta pra mim
""",
}

FAIXAS = [
    # arquivo, título, artista, álbum, artista do álbum, faixa, ano, estilo, cores, base_hz, seg, letra, sincronizada
    ("luz_de_esquina", "Luz de Esquina", "Maré Alta", "Cidade Acesa", "Maré Alta", 1, 2024, "sol", ["#15213B", "#FF6B1A", "#2A3A63"], 196.0, 72, "luz", True),
    ("onibus_das_seis", "Ônibus das Seis", "Maré Alta", "Cidade Acesa", "Maré Alta", 2, 2024, "sol", ["#15213B", "#FF6B1A", "#2A3A63"], 220.0, 64, "onibus", True),
    ("varanda", "Varanda", "Maré Alta", "Cidade Acesa", "Maré Alta", 3, 2024, "sol", ["#15213B", "#FF6B1A", "#2A3A63"], 174.6, 58, "varanda", False),
    ("radio_am", "Rádio AM", "Lua Norte", "Ruído Branco", "Lua Norte", 1, 2023, "ruido", ["#E9E6E1", "#1B1B1B", "#D7263D"], 246.9, 66, "radio", True),
    ("low_tide", "Low Tide", "Lua Norte", "Ruído Branco", "Lua Norte", 2, 2023, "ruido", ["#E9E6E1", "#1B1B1B", "#D7263D"], 164.8, 60, "tide", True),
    ("static", "Static", "Lua Norte", "Ruído Branco", "Lua Norte", 3, 2023, "ruido", ["#E9E6E1", "#1B1B1B", "#D7263D"], 130.8, 48, None, False),
    ("bam_ui_dosi", "밤의 도시 (Cidade da Noite)", "Kaya Seo", "Neon Hour", "Kaya Seo", 1, 2025, "neon", ["#12061F", "#FF2E88", "#00D1C1"], 233.1, 62, "cidade", True),
    ("blue_hour", "Blue Hour", "Kaya Seo", "Neon Hour", "Kaya Seo", 2, 2025, "neon", ["#12061F", "#FF2E88", "#00D1C1"], 207.7, 58, "bluehour", True),
    ("frequencia", "Frequência", "Os Sintonia", "Válvula", "Os Sintonia", 1, 2022, "valvula", ["#1F2A1F", "#E8D9B5", "#6B8F5E"], 185.0, 70, "frequencia", True),
    ("faixa_oculta", "Faixa Oculta", "Os Sintonia", "Válvula", "Os Sintonia", 2, 2022, None, None, 155.6, 44, None, False),
    ("demo_7", "Demo 7", "Maré Alta feat. Lua Norte", "", "", 0, 2025, None, None, 261.6, 40, None, False),
]


def main() -> None:
    destino = Path(sys.argv[1] if len(sys.argv) > 1 else "biblioteca_demo")
    destino.mkdir(parents=True, exist_ok=True)
    capas: dict[str, bytes] = {}
    for i, (arq, titulo, artista, album, art_album, faixa, ano, estilo, cores, hz, seg, letra, sinc) in enumerate(FAIXAS):
        mp3 = destino / f"{arq}.mp3"
        audio(mp3, seg, hz)
        tags = ID3()
        tags.add(TIT2(encoding=3, text=titulo))
        tags.add(TPE1(encoding=3, text=artista))
        if album:
            tags.add(TALB(encoding=3, text=album))
            tags.add(TPE2(encoding=3, text=art_album))
            tags.add(TRCK(encoding=3, text=str(faixa)))
        tags.add(TYER(encoding=3, text=str(ano)))
        if estilo:
            chave = album or titulo
            capas.setdefault(chave, capa(estilo, cores, semente=len(chave)))
            tags.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Cover", data=capas[chave]))
        if letra:
            texto = LETRAS[letra]
            if sinc:
                tags.add(SYLT(encoding=3, lang="por", format=2, type=1, desc="", text=sincronizada(texto)))
            tags.add(USLT(encoding=3, lang="por", desc="", text=texto.strip()))
        tags.save(mp3, v2_version=3)
        print(f"{i + 1:2d}. {mp3.name}")


if __name__ == "__main__":
    main()
