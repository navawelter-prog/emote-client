# Emote Client (Fabric, Minecraft 1.21.11)

Eine Client-Mod mit Emote-Menü. Wer die Mod hat, sieht die Emotes der anderen, die sie auch haben,
auf jedem Server. Dafür verbinden sich alle Clients mit einem kleinen **Relay-Server**.

```
Spieler A (Mod) ──┐                      ┌── Spieler B (Mod)
                  ├── WebSocket ── Relay ┤
Spieler C (Mod) ──┘                      └── ...
```

Der Relay gruppiert Spieler nach Minecraft-Server-Adresse. Emotes werden nur an Spieler auf
demselben Server weitergegeben. Spieler ohne die Mod sehen nichts.

## 1. Relay-Server starten

Du brauchst Node.js 18 oder neuer, sonst nichts.

```
cd relay
node server.js
```

Optional mit Passwort und anderem Port:

```
RELAY_TOKEN=mein-geheimes-passwort PORT=8765 node server.js
```
(Windows PowerShell: `$env:RELAY_TOKEN="mein-geheimes-passwort"; node server.js`)

Selbsttest: `node test.js`

**Erreichbar machen:** Auf deinem PC brauchst du für Freunde außerhalb eine Portfreigabe (8765) oder einen
Tunnel wie playit.gg. Auf Dauer lohnt sich ein kleiner VPS. Dort kannst du den Relay mit `pm2` oder
einem systemd-Dienst dauerhaft laufen lassen.

## 2. Mod bauen

Voraussetzung: **JDK 21**.

```
./gradlew build        (Windows: gradlew.bat build)
```

Die fertige Datei liegt danach in `build/libs/emote-client-0.1.0.jar`
(nicht die `-sources.jar`). Alternativ das Projekt in IntelliJ IDEA öffnen und Gradle importieren.

Beim ersten Bauen lädt Gradle Minecraft und Fabric herunter, das dauert ein paar Minuten.

**Wichtig:** Die Versionen in `gradle.properties` (Loader, Loom, Fabric API) habe ich aus
aktuellen Quellen gesucht, konnte sie aber ohne Internet im Build nicht gegenprüfen. Wenn Gradle
meldet, dass eine Version nicht existiert, trage die empfohlenen Werte von https://fabricmc.net/develop ein.

## 3. Mod benutzen

1. `emote-client-0.1.0.jar` und die **Fabric API** für 1.21.11 in den mods-Ordner legen
   (in der Modrinth-App: Profil öffnen, Mod hinzufügen).
2. Minecraft einmal starten. Danach liegt `config/emoteclient.json` im Profilordner.
3. Dort `relayUrl` auf die Adresse deines Relays setzen, zum Beispiel `ws://dein-server.de:8765`,
   und bei gesetztem `RELAY_TOKEN` denselben Wert bei `token` eintragen.
4. Im Spiel mit **G** das Emote-Menü öffnen (Taste änderbar unter Steuerung > Emote Client).
   Ein Emote endet, sobald du dich bewegst, oder über "Emote beenden".

## Eigene Emotes hinzufügen

In `src/main/java/dev/nava/emoteclient/emote/Emotes.java` ein weiteres `register(...)` ergänzen.
Die Funktion bekommt die Zeit `t` in Sekunden und setzt Drehwinkel (Radiant) für Kopf, Körper, Arme und Beine.
Danach in beiden Dateien unter `src/main/resources/assets/emoteclient/lang/` den Namen als
`emote.emoteclient.<id>` eintragen. Die eingebauten Posen sind Startwerte zum Feintunen.

## Der Tanz aus deinem Video

Das Emote **Tanz** (`dance`) steckt als Keyframe-Datei in
`src/main/resources/assets/emoteclient/emotes/dance.emote` (15 Bilder pro Sekunde, läuft in Schleife,
bis du dich bewegst). Das Minecraft-Modell hat nur Kopf, Körper, zwei Arme und zwei Beine ohne Ellbogen
oder Knie, deshalb ist der Tanz eine vereinfachte Version: abwechselnde Beinkicks nach hinten, Hände an
der Brust, seitlich ausgestreckte Arme und die Jazz-Hände nach oben.

**Wichtig:** Ich konnte die Haltung nicht automatisch aus dem Video auslesen, weil das dafür nötige
MediaPipe-Modell hier nicht heruntergeladen werden konnte. Die Posen habe ich deshalb von Hand aus den
Videobildern abgelesen (`tools/handkeyed_dance.py`, alle 0,25 s eine Pose). Das ist eine Annäherung.

Genauer geht es so, auf deinem PC:

```
pip install mediapipe numpy scipy opencv-python
python tools/video_to_emote.py dein_tanz.mp4 src/main/resources/assets/emoteclient/emotes/dance.emote --model pose_landmarker_full.task
```
Das Modell `pose_landmarker_full.task` lädst du von
https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
(ffmpeg muss installiert sein). Dieses Skript konnte ich ohne das Modell nicht testen.
Mit demselben Skript kannst du aus jedem weiteren Video ein neues Emote machen. Eine neue `.emote`-Datei
registrierst du in `Emotes.java` mit `KeyframeEmote.load("id", "/assets/emoteclient/emotes/datei.emote")`.

## Bekannte Grenzen (ehrlich)

- **Nicht kompiliert getestet.** Der Relay ist getestet, der Mod-Code nicht, weil ich hier kein Minecraft
  herunterladen konnte. Die Klassennamen habe ich gegen die 1.21.11-Mappings geprüft
  (`AvatarRenderer`, `AvatarRenderState`, `player.PlayerModel`, `KeyMapping.Category`, `Identifier`).
  Falls Gradle oder das Spiel einen Fehler zeigt, schick mir die Meldung, dann behebe ich sie.
- **Keine Identitätsprüfung.** Der Relay glaubt dem Client seine UUID. Wer will, kann sich also als anderer
  Spieler ausgeben. Fürs Ausprobieren mit Freunden und mit `RELAY_TOKEN` ist das okay, öffentlich sollte man
  die UUID über die Mojang-Session prüfen (`hasJoined`). Das wäre der nächste Schritt.
- Nur Posen per Code. Eigene Animationen aus Dateien (JSON-Keyframes) sind ein möglicher Ausbau.
- Manche Server verbieten Client-Mods. Prüfe vorher die Regeln.
