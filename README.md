# Ömer Client – Version 4

Fabric-Mod für **Minecraft 1.21.11** (braucht **Fabric API**).

## Menü
- Button **„Ömer Client“** im Startscreen direkt unter Multiplayer, im ESC-Menü oben links
  und im Spiel mit **Rechte Shift**
- Neues Design: schwarzer, animierter Hintergrund mit Lichtern und Partikeln, abgerundete Glas-Karten,
  gleitende Tab-Markierung, Tooltips beim Drüberfahren
- Oben rechts **Settings · Skins · Layout · Graphics**, jeder Tab mit eigenen Unter-Reitern:

| Tab | Unter-Reiter |
|---|---|
| Settings | Allgemein · Kits · Material List |
| Skins | Cape · Cosmetics · Totem · Sounds |
| Layout | Design · HUD |
| Graphics | Looks |

## Funktionen
- **Fullbright** (Taste **G**) und **Free Cam** (Taste **F4**, fliegt nicht durch Blöcke, 4 Tempo-Stufen)
- **Kit Select + Kit-HUD**: zeigt im Spiel, wie viele Kit-Items du hast (CPVP: Totems, Kristalle, Obsidian, Anker …;
  Sword, Axe, UHC, SMP, Mace; Normal World zeigt Koordinaten, Richtung, FPS). Rot = leer.
- **Material List**: `.litematic` oder `.schem` in `schematics/` legen → Liste aller Blöcke im HUD
  (grün = genug im Inventar) und ein eigener Screen zum Abhaken, mit Stacks/Kisten-Angabe
- **Eigenes Cape**: 4 Ömer-Designs, eigene Bilder aus `capes/` oder im **Cape-Editor** selbst malen
  (Pinsel, Eimer, Radierer, Pipette, 18 Farben + Akzentfarbe, heller/dunkler, Spiegeln,
  Rückgängig Strg+Z / Wiederholen Strg+Y, Speichern Strg+S, Klassisch 10x16 oder HD 20x32)
- **Cosmetics**: Heiligenschein (leuchtet, dreht sich), Krone, Zylinder, Flügel (schlagen),
  Hasenohren, Teufelshörner – 8 Farben, Animation an/aus, 3D-Vorschau im Menü
- **Custom Totem**: Bild aus `totems/`
- **Custom Sounds**: jeder Sound hat einen **eigenen Unterordner** in `sounds/`
  (`treffer`, `kritisch`, `totem`, `schaden`, `levelup`, `xp`, `klick`) – nur echte **.ogg**-Dateien
- **Look-Modus**: dein Lieblingsbild aus `looks/` als Hintergrund vom Menü und vom Startscreen, abdunkelbar
- Layout: Akzentfarbe, Transparenz, Partikel, Blur im Spiel, HUD-Größe und -Ecken
- Alles wird in `.minecraft/config/oemerclient.json` gespeichert

Alle Ordner liegen in `.minecraft/config/oemerclient/` und lassen sich im Menü mit einem Klick öffnen.
Cape, Cosmetics, Totem und Sounds siehst bzw. hörst nur du (Client-Mod).

## .jar bekommen
GitHub baut die Mod bei jedem Hochladen automatisch und testet sie in einem echten Minecraft.
1. Auf GitHub im Repository oben auf **Actions** gehen.
2. Den neuesten grünen Lauf öffnen → unten bei **Artifacts** `OemerClient-jar` herunterladen.
3. Im ZIP liegt `oemer-client-4.0.0.jar` → in den `mods`-Ordner, zusammen mit **Fabric API**.

Wenn der Build rot wird: auf den roten Schritt klicken, Fehlermeldung kopieren und an Claude schicken.

## Hinweis
Die Free Cam ist angelehnt an die Freecam-Mod (MIT-Lizenz), siehe `THIRD_PARTY_NOTICES.md`.
Auf manchen Servern ist Free Cam verboten – vorher die Regeln checken.
