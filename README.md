# Ömer Client – Version 6.2

Fabric-Mod für **Minecraft 1.21.11** (braucht **Fabric API**).

## Menü
- Button **„Ömer Client“** im Startscreen direkt unter Multiplayer, im ESC-Menü oben links
  und im Spiel mit **Rechte Shift**
- Links die Leiste **Mods · Settings · Design · Musik · Skin · Kits**, oben eine **Suche**

| Bereich | Inhalt |
|---|---|
| Mods | Jede Mod als Kachel mit An/Aus-Schalter – Klick öffnet ihre Einstellungen und Tasten |
| Settings | Wetter, HUD-Orte, Menü, alle Ömer-Tasten |
| Design | Foto-Vorlagen, **Datei wählen**, ältere Designs, Grafik Low/Mid/High/Super High, Reset Settings |
| Musik | **YouTube** (kostenlos) oder **Spotify** – oben umschalten |
| Skin | Cape · Totem · Cosmetics · Sounds |
| Kits | Eigene Minecraft-Einstellungen + Tasten pro Kit, **Speichern**-Button |

## Mods
Fullbright, Free Cam, **Just Zoom** (Taste C, Mausrad), Custom Crosshair, Material List, Kit-HUD,
**AppleSkin**, **Clear Glass**, **Dark GUI**, **Distant Horizons** (Original-Mod eingebaut, an/aus),
**Low Fire**, **Low Shield**, **No Darkness**, **No Fog**, **No Pumpkin Blur**, **Wetter aus**,
**Note Block Tuner**, **NoBedrock**, **Shulker Box Tooltip**, **Smaller Totem**, **TNT Timer**,
**No Explosion Particles**, **Transparente Netherportale**, **Own Custom Name** (Farbe, Verlauf oder Rainbow – nur du siehst ihn: Tab-Liste, Chat, Namensschild in F5),
Musik-HUD, Cosmetics, Custom Totem, Custom Cape, Custom Sounds.

## Kits
Jedes Kit (Survival, CPVP, Sword, Axe, UHC, SMP, Mace) speichert deine **kompletten Minecraft-Einstellungen**
(Tasten, FOV, Maus, Grafik …). Beispiel: im Kit CPVP ist Hotbar 5 = Q, in Survival ist Q = Fallen lassen.
Kit anklicken = wechseln, **Speichern** merkt sich die aktuellen Einstellungen. Tasten: K = nächstes Kit, eigene Taste pro Kit.

## Musik
- **YouTube**: Link oder Playlist-Link einfügen (mit kostenlosem YouTube-API-Schlüssel auch suchen),
  ♥ = Meine Songs, Playlists, Warteschlange. Abgespielt wird über den offiziellen YouTube-Player in einem Browser-Tab
  (öffnet sich automatisch, einmal „Musik starten“ klicken) – gesteuert wird aus Minecraft (Tasten + HUD). Ohne Premium.
- **Spotify** bleibt wie in v5.

## Löschen
- **Musik:** ✕ bei jedem Song in *Meine Songs*, in Playlists und in der Warteschlange · „Alle Songs hier löschen“ · „Warteschlange leeren“
- **Playlists im Spiel:** Namen eintippen → **＋ Erstellen**, bei einem Song auf **☰** → Playlist wählen, Playlist anklicken = Songs ansehen/löschen/gezielt abspielen
- **Eigene Dateien:** kleines **✕** oben rechts auf deinen eigenen Hintergründen, Capes, Totems und Cosmetics (mit „Wirklich löschen?“), dazu Crosshair, Schematic und „Gespeichertes löschen“ pro Kit
- Der Player im Browser hat die ✕ ebenfalls, dazu **Playlists erstellen** und Songs per **☰** hinzufügen

## Material List
`.litematic` oder `.schem` in `schematics/`. **Sneaken + Rechtsklick mit Spitzhacke auf eine Kiste** markiert sie,
einmal öffnen – dann zählt ihr Inhalt in der Liste mit, und die Kiste leuchtet.

## Skin
- **Cape** und **Totem**: Vorlagen + eigene Bilder (**Datei wählen**), Totems in 256x256 (nicht mehr verpixelt)
- **Cosmetics**: keine eingebauten – deine eigenen PNGs (mit Transparenz) als Bild ausrüsten: Kopf, Rücken, Begleiter.
  Breite Bilder auf dem Rücken werden zu Flügeln (links/rechts geteilt, schlagen)

Alle Ordner liegen in `.minecraft/config/oemerclient/`. Cape, Cosmetics, Totem und Sounds siehst bzw. hörst nur du.

## .jar bekommen
1. Auf GitHub im Repository oben auf **Actions** gehen.
2. Den neuesten grünen Lauf öffnen → unten bei **Artifacts** `OemerClient-jar` herunterladen.
3. Im ZIP liegt `oemer-client-6.2.0.jar` → in den `mods`-Ordner, zusammen mit **Fabric API**.

## Hinweis
Free Cam basiert auf der Freecam-Mod (MIT), Distant Horizons ist LGPL-3.0, Fotos von Unsplash – siehe `THIRD_PARTY_NOTICES.md`.
Auf manchen Servern sind Free Cam, Zoom & Co. verboten – vorher die Regeln checken.
