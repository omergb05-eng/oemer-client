# Ömer Client – Version 7.0

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

## Musik einrichten – Anleitung (auch im Spiel unter Musik → Anleitung)


### YouTube


**So läuft Musik über YouTube (kostenlos)**

Der Ömer-Player läuft im Browser, Minecraft steuert ihn. Du brauchst dafür kein Premium.  
1. Im Ömer-Menü auf „Musik“ gehen und oben „YouTube“ wählen.
2. Einen Song starten oder „Player im Browser öffnen“ klicken. Ein Browser-Tab mit dem Ömer-Player öffnet sich.
3. Im Tab einmal auf „Musik starten“ klicken (sonst erlaubt der Browser keinen Ton). Danach den Tab offen lassen, er darf im Hintergrund laufen.
4. Oben links im Tab steht „mit Minecraft verbunden“. Jetzt steuerst du alles aus Minecraft: Play/Pause, Weiter, Zurück, Lautstärke (Pfeil hoch/runter, Tasten änderbar).

**Songs finden**

Ohne Schlüssel: einen YouTube-Link in das Suchfeld einfügen und „Los“ klicken. Das geht immer.  
Mit Schlüssel (siehe unten): im Suchfeld einfach den Namen eintippen, z. B. „Madison Beer Make You Mine“.  
Bei jedem Song: ▶ spielen, ＋ in die Warteschlange, ♥ zu „Meine Songs“, ☰ in eine Playlist.  

**YouTube-Schlüssel holen (kostenlos, nur für die Suche)**

Jeder braucht seinen eigenen Schlüssel. Er kostet nichts und dauert etwa 5 Minuten.  
1. Knopf „Schlüssel holen“ klicken (oder console.cloud.google.com öffnen) und mit einem Google-Konto anmelden.
2. Oben auf „Projekt auswählen“ klicken, „Neues Projekt“ wählen, einen Namen eintippen (z. B. Ömer Musik) und „Erstellen“ klicken.
3. Im Menü „APIs & Dienste“ → „Bibliothek“ öffnen, nach „YouTube Data API v3“ suchen und auf „Aktivieren“ klicken.
4. Dann „APIs & Dienste“ → „Anmeldedaten“ → „Anmeldedaten erstellen“ → „API-Schlüssel“. Es erscheint ein Schlüssel, der mit AIza beginnt.
5. Den Schlüssel kopieren und im Ömer-Menü unter Musik in das Feld „YouTube-API-Schlüssel“ einfügen. Er wird automatisch gespeichert.
6. Empfohlen: In der Google-Konsole beim Schlüssel „Schlüssel einschränken“ wählen und unter „API-Einschränkungen“ nur „YouTube Data API v3“ erlauben.
> ⚠ Den Schlüssel nie öffentlich zeigen (Screenshots, Discord, Videos). Wer ihn hat, verbraucht dein Tageslimit.

Das Tageslimit ist kostenlos und reicht für etwa 100 Suchen pro Tag (eine Suche kostet 100 von 10.000 Einheiten).  

### Jedes Lied


**Warum manche Songs nicht gehen**

Bei manchen Videos (oft offizielle Videos von Labels) hat der Besitzer das Einbetten verboten. Im Ömer-Player steht dann „darf nicht eingebettet werden“. Dagegen gibt es zwei Wege.  

**Weg 1: Automatischer Ersatz (braucht den YouTube-Schlüssel)**

Der Client sucht selbst eine andere Version des Songs (z. B. Lyrics-Video) und spielt die stattdessen. Du musst nichts tun, es steht nur „Original gesperrt – spiele stattdessen …“.  

**Weg 2: Echte YouTube-Seite mit der Erweiterung „Ömer Bridge“ (jedes Lied)**

Der Song läuft dann direkt auf youtube.com in einem Tab, mit deinem Login. Minecraft steuert den Tab. Das geht in Chrome, Edge, Brave und Opera (nicht in Firefox oder Safari).  
1. Im Ömer-Menü unter Musik bei „Wiedergabe“ die Option „YouTube-Seite (Erweiterung, jedes Lied)“ wählen.
2. Auf „Erweiterung Ömer Bridge installieren“ klicken. Der Ordner „oemer-extension“ öffnet sich.
3. Im Browser die Adresse chrome://extensions öffnen (Edge: edge://extensions, Brave: brave://extensions).
4. Oben rechts „Entwicklermodus“ einschalten.
5. „Entpackte Erweiterung laden“ klicken und den Ordner „oemer-extension“ auswählen. „Ömer Bridge“ erscheint in der Liste und ist eingeschaltet.
6. In Minecraft einen Song starten. Ein YouTube-Tab öffnet sich. Unten links im Tab steht „Ömer Client verbunden ✔“.
7. Den Tab offen lassen. Pause, Weiter, Zurück und Lautstärke gehen jetzt aus Minecraft.
> ⚠ Werbung läuft normal. „Überspringen“ klickst du selbst im Tab. Mit YouTube Premium gibt es dort keine Werbung.

> ⚠ Die Erweiterung steuert nur Tabs, die Ömer Client selbst geöffnet hat. Deine anderen YouTube-Tabs bleiben unberührt.

Nach einem Client-Update: nochmal „Erweiterung installieren“ klicken und in chrome://extensions bei Ömer Bridge auf das Neu-laden-Symbol klicken.  
Chrome zeigt beim Start manchmal „Erweiterungen im Entwicklermodus deaktivieren“. Das mit „Beibehalten“ oder X wegklicken, nicht deaktivieren.  

### Spotify


**So verbindest du Spotify**

Du brauchst Spotify Premium (nur damit erlaubt Spotify das Steuern von außen). Jeder Freund erstellt seine eigene kleine Spotify-App, das dauert 3 Minuten und kostet nichts.  
1. Knopf „Anleitung: App erstellen“ klicken (developer.spotify.com/dashboard) und mit deinem Spotify-Konto anmelden. Bedingungen bestätigen.
2. „Create app“ klicken. App name z. B. „Ömer Client“, eine Beschreibung ist egal.
3. Bei „Redirect URIs“ genau diese Adresse eintragen und „Add“ klicken: http://127.0.0.1:43821/callback
4. Bei „Which API/SDKs are you planning to use?“ „Web API“ ankreuzen, Bedingungen akzeptieren und „Save“ klicken.
5. Die App öffnen, auf „Settings“ gehen und die „Client ID“ kopieren. Das „Client secret“ brauchst du nicht.
6. Im Ömer-Menü unter Musik oben „Spotify“ wählen, die Client-ID einfügen und „Mit Spotify verbinden“ klicken.
7. Im Browser bei Spotify auf „Zustimmen“ klicken. In Minecraft steht dann „Verbunden ✔“.
8. Spotify auf einem Gerät öffnen (PC-App, Handy oder open.spotify.com) und kurz einen Song starten. Danach steuerst du es aus Minecraft: Play/Pause, Weiter, Zurück, Lautstärke, Playlists.
> ⚠ Spotify muss auf irgendeinem Gerät offen sein, sonst gibt es „Kein aktives Gerät“.

> ⚠ Neue Spotify-Apps sind im Entwicklermodus. Dort darf nur der Besitzer und wer im Dashboard unter „User Management“ mit seiner Spotify-E-Mail eingetragen ist. Am einfachsten erstellt deshalb jeder seine eigene App.


### Probleme


**YouTube / Ömer-Player**

Im Browser steht „Minecraft ist nicht offen“: Minecraft muss laufen. Die Seite neu laden. Firewall oder VPN dürfen 127.0.0.1 nicht blockieren.  
Kein Ton: Im Tab einmal auf „Musik starten“ klicken und den Tab nicht stummschalten.  
„darf nicht eingebettet werden“: Siehe Reiter „Jedes Lied“.  
„Für die Suche: kostenlosen YouTube-Schlüssel eintragen“: Der Schlüssel fehlt. Link einfügen geht trotzdem.  
„Suche ging nicht: API key not valid“: Der Schlüssel ist falsch kopiert (Leerzeichen?) oder eingeschränkt. Neu einfügen.  
„Suche ging nicht: … has not been used / is disabled“: Die „YouTube Data API v3“ wurde nicht aktiviert. In der Google-Konsole aktivieren und 2 bis 5 Minuten warten.  
„Suche ging nicht: quota“: Das Tageslimit ist verbraucht. Es geht gegen 9 Uhr deutscher Zeit wieder.  
„Suche ging nicht: … blocked / referer“: Beim Schlüssel nur die API einschränken, nicht auf Websites oder IP-Adressen.  

**Erweiterung Ömer Bridge**

Unten links im Tab steht „Ömer Client nicht erreichbar – ist Minecraft offen?“: Minecraft starten. Dann geht es von selbst weiter.  
Es steht „Ömer Bridge: aus“: Unter Musik bei „Wiedergabe“ die Option „YouTube-Seite“ wählen.  
Es erscheint kein Badge im Tab: Die Erweiterung ist nicht geladen oder aus. In chrome://extensions prüfen. Der Tab muss von Ömer Client geöffnet sein (Adresse enthält omer=1).  
Es steht „einmal ins Video klicken“: Der Browser blockt das automatische Abspielen. Einmal im Video klicken, dann läuft es.  
Der falsche Browser geht auf: Es öffnet sich dein Standardbrowser. Die Erweiterung muss in genau diesem Browser installiert sein.  

**Spotify**

„Erst die Client-ID eintragen“: Die Client-ID fehlt. Siehe Reiter „Spotify“, Schritt 5.  
Im Browser „INVALID_CLIENT: Invalid redirect URI“: In der Spotify-App muss genau http://127.0.0.1:43821/callback stehen, mit „Add“ bestätigt und gespeichert.  
Im Browser Fehler bei der Anmeldung („User not registered“): Du bist nicht der Besitzer der App und nicht im „User Management“ eingetragen. Eigene App erstellen.  
„Port 43821 ist belegt“: Ein anderes Minecraft oder Programm benutzt den Port. Das andere schließen.  
„Zeit abgelaufen – nochmal verbinden“: Nach dem Klick hast du etwa zwei Minuten Zeit, im Browser zuzustimmen.  
„Spotify erlaubt Steuern nur mit Premium“: Das Steuern von außen geht nur mit Premium.  
„Kein aktives Gerät – öffne Spotify und spiel kurz etwas ab“: Spotify muss irgendwo laufen. Song kurz starten, dann nochmal versuchen.  
„Spotify antwortet 429“: Zu viele Anfragen. Ein paar Sekunden warten.  

**Für Freunde**

Jeder braucht seinen eigenen YouTube-Schlüssel und seine eigene Spotify-App. Schlüssel und Client-ID nicht weitergeben.  
Der Ordner „oemer-extension“ gehört zu deinem Minecraft-Ordner. Jeder installiert ihn bei sich selbst.  

## Neu in 8.1

- **Schematic**: Ghost-Blöcke mit der echten Textur (und Drehung/Zustand), durchsichtig und leicht pulsierend. Blau = fehlt, rot = falsch, grün = passt. Reichweite „Unbegrenzt“ (Standard) – auch riesige Bilder (z. B. 256×256) laden. Nahe Blöcke mit Textur, ferne als Farbflächen.
- **Musik**: Fehler beim Abspielen (Video nicht einbettbar) überspringen nicht mehr heimlich auf einen anderen Song. Taste Hoch/Runter = Musik lauter/leiser (auch im Spiel).
- **Ömer Bridge**: Browser-Erweiterung, die den echten YouTube-Tab von Minecraft aus steuert (auch gesperrte Songs). Anleitung im Spiel unter Musik → Anleitung.
- **Armor HUD** (Mods): Rüstung mit Haltbarkeit am Bildschirmrand.
- **Tasten**: Änderungen unter Einstellungen → Tasten gelten jetzt in allen Kits.

## Neu in 8.0
- **Schematic-Platzierung** (Mods → Schematic-Platzierung): `.litematic` oder `.schem` wählen, per „Dort setzen“ in die Welt stellen, verschieben, drehen (90°), spiegeln. Fehlende Blöcke erscheinen als durchsichtiger Geist, falsche Blöcke rot, richtige grün. **Ebene für Ebene:** nur eine Ebene / bis zu / ab einer Ebene zeigen, mit Bild ↑ / Bild ↓ (frei belegbar) hoch und runter. Es werden nur Blockarten verglichen (nicht die Ausrichtung von Treppen o. ä.).
- **Clipper** (Mods → Clipper): nimmt im Hintergrund auf. **F9** = die letzten 30 s / 1 min / 2 min als Video speichern, in einen Ordner, den du selbst wählst. MP4 (läuft überall) oder AVI (sofort fertig). Ohne Ton.
- **Ömer-Ladescreen:** beim Start von Minecraft und beim Betreten einer Welt / Dimension (Mods → Ömer-Ladescreen zum Ausschalten).
- **Ömer-Symbol:** kleines Mond-Haus-Symbol eine Zeile über dem Namen – bei dir, bei Freunden mit dem Client und in der Tab-Liste (Mods → Ömer-Symbol).

## Freunde (online) – neu in 7.0
Im Menü gibt es links den Bereich **Freunde** (Reiter Freunde · Chat · Konto).

- **Konto:** E-Mail + Passwort. Jeder Username gibt es nur 1×. Das Passwort wird nie auf dem PC gespeichert (nur eine Anmelde-Marke), „Passwort vergessen?“ schickt eine Mail.
- **Freunde:** per Username hinzufügen → Anfrage → annehmen/ablehnen/entfernen. Grüner Punkt = online.
- **Chat:** mit Freunden im Menü schreiben (nicht im Minecraft-Chat). Optional kommt eine kurze Meldung im Spiel.
- **Cape & Totem teilen:** Freunde sehen dein Cape auf deinem Spieler in der Welt; dein Totem sehen sie in der Freundesliste (die Totem-Animation selbst sieht nur der Besitzer – das lässt Minecraft nicht anders zu).
- **Musik:** Freunde sehen unter deinem Namen, was du hörst. Pro Freund ein/aus („Meine Musik: an/aus“) und global unter Konto. **♪ Mitlauschen** startet den Song deines Freundes bei dir an derselben Stelle (nur YouTube).

### Einmalig einrichten (Firebase, kostenlos)
1. https://console.firebase.google.com → Projekt erstellen.
2. *Build → Authentication → Get started → Sign-in method → E-Mail/Passwort* einschalten.
3. *Build → Realtime Database → Create database* (Standort egal, Start im **gesperrten Modus**), dann Reiter *Rules* → Inhalt von `firebase/database.rules.json` einfügen → Publish.
4. *Projekteinstellungen (Zahnrad) → Allgemein → „Web-API-Schlüssel“* kopieren; die Datenbank-Adresse steht oben in der Realtime Database (`https://….firebasedatabase.app`).
5. Im Spiel: Freunde → Konto → *Server-Einstellungen* → beides eintragen. Oder beides in `online/OnlineDefaults.java` einbauen, dann müssen Freunde nichts eintippen.

Die Regeln sind im Emulator getestet (`ci/rules-test.mjs`): Fremde sehen nichts, nur Freunde sehen Skins/Musik, Chat nur zwischen Freunden.
Wichtig: Ohne Mojang-Prüfung kann ein Username theoretisch jeder belegen, der ihn zuerst registriert – für einen Freundeskreis ist das okay.

## .jar bekommen
1. Auf GitHub im Repository oben auf **Actions** gehen.
2. Den neuesten grünen Lauf öffnen → unten bei **Artifacts** `OemerClient-jar` herunterladen.
3. Im ZIP liegt `oemer-client-6.2.0.jar` → in den `mods`-Ordner, zusammen mit **Fabric API**.

## Hinweis
Free Cam basiert auf der Freecam-Mod (MIT), Distant Horizons ist LGPL-3.0, Fotos von Unsplash – siehe `THIRD_PARTY_NOTICES.md`.
Auf manchen Servern sind Free Cam, Zoom & Co. verboten – vorher die Regeln checken.
