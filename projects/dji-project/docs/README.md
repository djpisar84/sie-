# Wyodrębnianie danych DJI — Spis treści

Repozytorium zawiera dane dotyczące dronów DJI wyodrębnionych z eksportu Cursor IDE.

## Znalezione projekty DJI

1. **DJI Mini 4 Pro** — Hacks
   - Kod: params_v148_set.py (dekoder ramek SET protokołu Drone Hacks v1.48)
   - Reverse engineering z capture_v148_full69.pcap
   - Mini 4 Pro FW 01.00.1100
   - Komunikacja DroneHacks (src=0x03) <-> FC (dst=0x0A)
   - UUID: 019d82df-ca42-7324-8c10-063ef0f0ce4d

2. **DJI RC2** — Hacks
   - UUID: 019d7f13-69c5-71e9-a0c9-a93f49802da8
   - Brak dokumentacji (projekt pusty)

## Struktura katalogów

- `docs/dji-data/projects/` — Pełne projekty DJI (Markdown)
- `docs/dji-data/hacks/` — Wyodrębnioo kody hacków i skrypty

## Katalogi źródłowe

Oryginalne dane z eksportu Cursor:
- docs/claude-exports/md/projects_019d82df-ca42-7324-8c10-063ef0f0ce4d.md — DJI Mini 4 Pro
- docs/claude-exports/md/projects_019d7f13-69c5-71e9-a0c9-a93f49802da8.md — DJI RC2
