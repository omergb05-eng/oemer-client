# Ömer Client – Version 2

Fabric-Mod für **Minecraft 1.21.11**.

## Was drin ist
- Button **„Ömer Client“** im Startscreen direkt unter Multiplayer
- Eigenes Menü: schwarzer Screen, oben rechts **Settings · Skins · Layout · Graphics**
- Cleanes weißes, durchsichtiges Design mit Akzentfarbe
- Kit Select (Normal World, CPVP, Sword, Axe, UHC, SMP, Mace)
- **Fullbright** – alles hell, Taste **G**
- **Free Cam** – frei fliegen, aber **nicht durch Blöcke**, Taste **F4**
  (WASD, Leertaste hoch, Shift runter, Sprinten = schneller, 4 Tempo-Stufen)
- Menü auch im Spiel: Taste **Rechte Shift** oder Button im ESC-Menü
- Material List, Cape, Totem, Sounds, Look-Modus haben schon Schalter,
  kommen aber erst in den nächsten Versionen („bald“)
- Layout: Transparenz und Akzentfarbe wirken sofort
- Alles wird in `.minecraft/config/oemerclient.json` gespeichert

## .jar bauen mit GitHub
1. Auf github.com einloggen → **New repository** → Name z. B. `oemer-client` → **Create**.
2. **„uploading an existing file“** → alle Dateien und Ordner aus dem ZIP hochladen
   (wichtig: auch der Ordner `.github`) → **Commit changes**.
3. Oben auf **Actions** gehen → der Build startet von selbst (ca. 3–5 Minuten).
4. Wenn er grün ist: auf den Build klicken → unten bei **Artifacts** `OemerClient-jar` herunterladen.
5. Im ZIP liegt `oemer-client-2.0.0.jar` → in den `mods`-Ordner, zusammen mit **Fabric API**.

Wenn der Build rot wird: auf den roten Schritt klicken, Fehlermeldung kopieren und an Claude schicken.

## Hinweis
Die Free Cam ist angelehnt an die Freecam-Mod (MIT-Lizenz), siehe `THIRD_PARTY_NOTICES.md`.
Auf manchen Servern ist Free Cam verboten – vorher die Regeln checken.
