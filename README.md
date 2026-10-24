# WordFlow – KI-gestützter Vokabeltrainer

Selbst gehosteter Vokabeltrainer für den Browser und als Android-App. Fotos aus dem
handgeschriebenen Vokabelheft werden per KI in Vokabelpakete umgewandelt und nach dem
Karteikasten-Prinzip (6 Stufen) als umdrehbare Karteikarten abgefragt. Ein schnelles KI-Modell
prüft getippte Antworten. Mehrere Accounts auf einem Server, die Daten sind getrennt.

## Schnellstart mit Docker

```bash
git clone https://github.com/justusvoelkle3141592654-ux/Phase-6-2.0.git wordflow
cd wordflow
docker compose up -d
```

Dann <http://localhost:3000> öffnen (im Heimnetz `http://<IP-des-Servers>:3000`), als Superuser
`admin@wordflow.com` / `admin` anmelden, dem Einrichtungsassistenten folgen und unter
Einstellungen → Accounts die Accounts der Lernenden anlegen.

Für ein lokales Test-Setup kann `OFFLINE_MODE=true` in `.env` gesetzt werden. Dann gibt es
zusätzlich einen Standard-Offline-Account (`offline@local.test` / `offline`), und der Login-Dialog
bietet eine Auswahl aller lokalen Accounts.

Die **Android-App** liegt fertig unter [`release/`](release/) bzw. bei den GitHub-Releases
(siehe [Android-App](#android-app)).

## Betrieb

### Docker Compose

`docker-compose.yml` startet WordFlow auf Port 3000. Daten (Datenbank, Fotos, `secrets.json`) liegen
im Volume `wordflow-data`. Einstellungen kommen aus einer `.env` neben der Compose-Datei
(Vorlage `.env.example`, siehe [Konfiguration](#konfiguration)).

```bash
docker compose up -d                        # WordFlow
docker compose --profile ollama up -d       # zusätzlich Ollama für lokale KI
docker compose --profile https up -d        # zusätzlich Caddy mit HTTPS
```

- **Ollama im Compose-Netz:** Modelle laden mit
  `docker compose exec ollama ollama pull <modell>`, in WordFlow unter Einstellungen → KI einen
  Anbieter „Eigener Server“ mit Adresse `http://ollama:11434` und Format „Ollama“ anlegen.
- **HTTPS:** In `deploy/Caddyfile` die eigene Domain eintragen (DNS muss auf den Server zeigen,
  Ports 80 und 443 erreichbar), in `.env` `TRUST_PROXY=true` setzen. Caddy holt die Zertifikate
  automatisch.

### Direkt mit Node.js

Voraussetzungen: Node.js **22.12 oder neuer** mit npm. Für die nativen Module (SQLite, Argon2)
werden normalerweise fertige Binärdateien geladen; passt keine, braucht npm Python, make und einen
C++-Compiler.

```bash
git clone https://github.com/justusvoelkle3141592654-ux/Phase-6-2.0.git wordflow
cd wordflow
npm ci
npm run build
cp .env.example .env   # optional, die Standardwerte funktionieren
npm start
```

Der Server liefert Oberfläche und API gemeinsam auf <http://localhost:3000> aus und liest die
`.env` im Projektordner selbst.

### Als systemd-Dienst

Vorlage: `deploy/wordflow.service` (erwartet das Projekt unter `/opt/wordflow` und einen
Systembenutzer `wordflow`).

```bash
sudo useradd --system --home /opt/wordflow --shell /usr/sbin/nologin wordflow
sudo git clone https://github.com/justusvoelkle3141592654-ux/Phase-6-2.0.git /opt/wordflow
cd /opt/wordflow && sudo npm ci && sudo npm run build
sudo mkdir -p /opt/wordflow/data && sudo chown -R wordflow:wordflow /opt/wordflow/data
sudo cp deploy/wordflow.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now wordflow
```

### HTTPS über einen Reverse-Proxy

Für den Zugriff aus dem Internet gehört ein Reverse-Proxy mit HTTPS davor, z. B. Caddy:

```
wordflow.example.org {
	reverse_proxy localhost:3000
}
```

Dazu `TRUST_PROXY=true` setzen, damit WordFlow HTTPS erkennt (das Sitzungs-Cookie wird dann mit
`Secure` gesetzt). Im Heimnetz geht es auch ohne HTTPS; die Android-App erlaubt dort Klartext-HTTP.

### Aktualisieren

```bash
git pull
docker compose up -d --build                 # Docker
npm ci && npm run build && sudo systemctl restart wordflow   # direkte Installation
```

Datenbank-Migrationen laufen beim Start automatisch.

### Sichern

Alles Wichtige liegt im Datenordner (`data/` bzw. Volume `wordflow-data`): `wordflow.db` (Datenbank),
`uploads/` (Fotos) und `secrets.json` (Hauptschlüssel). Am einfachsten sichert der Superuser alles
unter Einstellungen → Datenbank sichern als ZIP und spielt es dort auch wieder ein.
**Ohne `secrets.json` (bzw. `APP_SECRET`) lassen sich gespeicherte API-Schlüssel nicht mehr
entschlüsseln.**

## Entwicklung

```bash
npm install
npm run dev
```

- Oberfläche: <http://localhost:5173> (mit automatischem Neuladen)
- Server/API: <http://localhost:3000/api/health>

Die Entwicklungs-Oberfläche leitet alle `/api`-Aufrufe an den Server weiter. Sie ist auch vom
Handy im selben WLAN erreichbar: `http://<IP-des-Rechners>:5173`.

## Tests

```bash
npm test          # alle Tests (Server, Web, Shared)
npm run typecheck # TypeScript-Prüfung aller Pakete
```

Nach einer Änderung an `apps/server/src/db/schema.ts` eine neue Migration erzeugen:

```bash
npm run db:generate -w @wordflow/server
```

## Konfiguration

Alle Einstellungen laufen über Umgebungsvariablen oder eine `.env`-Datei im Projektordner
(Vorlage: `.env.example`).

| Variable       | Standard     | Bedeutung                                                            |
| -------------- | ------------ | -------------------------------------------------------------------- |
| `PORT`         | `3000`       | Port des Servers                                                     |
| `HOST`         | `0.0.0.0`    | Netzwerkadresse, auf der der Server lauscht                          |
| `DATA_DIR`     | `data`       | Ordner für Datenbank, Fotos und erzeugte Schlüssel                   |
| `TRUST_PROXY`  | `false`      | `true`, wenn ein Reverse-Proxy davor HTTPS übernimmt                 |
| `CORS_ORIGINS` | –            | Zusätzlich erlaubte Origins, kommagetrennt (Android-App ist erlaubt) |
| `LOG_LEVEL`    | `info`       | `fatal`, `error`, `warn`, `info`, `debug`, `trace` oder `silent`     |
| `OFFLINE_MODE` | `false`      | Legt zusätzlich einen Offline-Standardaccount an, listet Accounts    |
| `APP_SECRET`   | wird erzeugt | Hauptschlüssel für die Verschlüsselung gespeicherter API-Schlüssel   |

## Accounts und Superuser

Beim Start legt der Server den **Superuser** an, falls er fehlt: **`admin@wordflow.com` /
`admin`**. Bitte das Passwort nach der ersten Anmeldung unter Einstellungen → Passwort ändern.

- **Nur der Superuser legt Accounts an** (Einstellungen → Accounts): hinzufügen, bearbeiten,
  Passwort setzen, löschen. Eine Selbst-Registrierung gibt es nicht mehr. Ein neues Passwort meldet
  den Account überall ab. Der Superuser selbst lässt sich nicht löschen.
- **Pakete gehören allen:** Nur der Superuser legt Pakete an, ändert und löscht sie und fügt Vokabeln
  hinzu. Alle Accounts sehen alle Pakete und aktivieren die Vokabeln, die sie lernen wollen. Jeder
  Account hat dabei seinen eigenen Lernstand. Im Paket stehen „Meine Vokabeln (aktiv)“ und
  „Vokabeln vom Superuser (inaktiv)“ getrennt.
- **KI-Einstellungen** (Anbieter, Modelle, Zeitlimit) stellt nur der Superuser ein. Sie gelten für alle.
- **Datenbank sichern** (Einstellungen): Export als ZIP mit Datenbank, Fotos und `secrets.json`.
  Der Import ersetzt alle Daten; danach müssen sich alle neu anmelden.
- **Update** (Einstellungen): Holt den neuesten Stand von GitHub (`rcode125/Phase-6-2.0`, Branch
  `master`) und kopiert ihn über das Projekt. `data/`, `.env`, `node_modules/`, `.git/`, `release/`
  und `apps/web/android/` bleiben unberührt. Bei geänderten Abhängigkeiten läuft `npm install`.
  Im Dev-Modus (`npm run dev`) lädt der Server danach selbst neu; mit `NODE_ENV=production` wird
  zusätzlich gebaut, dann den Dienst neu starten. Das Protokoll steht in `data/update.log`.
- Mit `OFFLINE_MODE=true` wird zusätzlich ein Standardkonto `offline@local.test` / `offline`
  angelegt, und die Anmeldeseite listet alle Accounts zur Auswahl (Passwort weiterhin nötig).
  Nur für Geräte im eigenen Netz gedacht, da die E-Mail-Adressen sichtbar sind.
- `APP_SECRET` verschlüsselt die API-Schlüssel der KI-Anbieter. **`data/secrets.json` sichern und
  nicht verlieren**, sonst müssen alle API-Schlüssel neu eingegeben werden.
- Anmeldung im Browser über ein Cookie (`wordflow_session`, HttpOnly). Die Android-App bekommt
  stattdessen einen Token. Sitzungen laufen nach 90 Tagen ohne Nutzung ab.
- Anmelden und Passwort ändern sind auf 10 Versuche pro Minute begrenzt.
- Wer das Passwort ändert, wird auf allen anderen Geräten abgemeldet.

## Einrichtungsassistent

Bei der ersten Anmeldung führt ein Assistent durch die Einrichtung: Sprache, Name, Zeitzone,
Lernen (Intervalle der Stufen 2–6, Standard 5 / 10 / 20 / 40 / 80 Tage, und Verhalten bei falschen
Antworten) und Übersicht. Beim Superuser kommen die KI-Schritte dazu: Anbieter und Modell für die
Antwortprüfung (voreingestellt Pollinations.ai mit `openai/gpt-oss-20b`), Bilderkennung und
Zeitlimit der KI-Prüfung (Standard: 2 Sekunden).

Alle Werte lassen sich später in den Einstellungen ändern.

## KI-Anbieter

Der Superuser richtet die KI für alle ein (Assistent bei der ersten Anmeldung, später unter
Einstellungen → KI). Pro Aufgabe – **Antwortprüfung** und **Bilderkennung** –
gibt es einen eigenen Anbieter und ein eigenes Modell. Die Modelllisten werden live vom Anbieter
geladen; der Modellname kann auch eingetippt werden.

| Anbieter                   | Protokoll                     | Adresse                                 | Schlüssel |
| -------------------------- | ----------------------------- | --------------------------------------- | --------- |
| Pollinations.ai (Standard) | OpenAI-kompatibel             | `https://gen.pollinations.ai/v1`        | optional  |
| Anthropic (Claude)         | Anthropic Messages API        | `https://api.anthropic.com/v1`          | nötig     |
| OpenAI                     | OpenAI                        | `https://api.openai.com/v1`             | nötig     |
| OpenRouter                 | OpenAI-kompatibel             | `https://openrouter.ai/api/v1`          | nötig     |
| Ollama (lokal)             | Ollama                        | `http://localhost:11434`                | –         |
| Ollama (online)            | Ollama                        | `https://ollama.com`                    | nötig     |
| Eigener Server             | OpenAI-kompatibel oder Ollama | frei, z. B. `http://192.168.1.50:11434` | optional  |

- **Standard:** Pollinations.ai mit `openai/gpt-oss-20b` für die Antwortprüfung, ohne
  API-Schlüssel. Laut Pollinations-Doku brauchen Anfragen einen Schlüssel
  (`sk_…` von enter.pollinations.ai); meldet „Verbindung testen“ Fehler 401 oder 402, den Schlüssel
  beim Anbieter eintragen.
- **Bilderkennung:** `openai/gpt-oss-20b` kann keine Bilder lesen. Das Bildmodell wird aus den
  bildfähigen Modellen gewählt (Pollinations/OpenRouter: `input_modalities`, Anthropic:
  `capabilities.image_input`, Ollama: `capabilities` enthält `vision`). Wie gut ein Modell
  Handschrift liest, ist nicht geprüft.
- **Komplett lokal:** Ollama mit einem Text- und einem Bildmodell.
- **API-Schlüssel** liegen nur auf dem Server, verschlüsselt mit AES-256-GCM (Schlüssel aus
  `APP_SECRET`). Die Oberfläche zeigt nur „Gesetzt · …abcd“.
- **Latenz:** Jeder KI-Aufruf wird gemessen (erstes Token, Gesamtzeit, Tokens/s). Median und
  langsamste Werte stehen in den Einstellungen.

## Startseite

- Heute fällige Vokabeln (mit Knopf „Jetzt lernen“), Anzahl je Stufe, gelernte und inaktive
  Vokabeln – funktioniert ohne KI.
- Heute gelernt: Abfragen, Trefferquote, Zeit pro Karte, schwierige Vokabeln.
- „Heute“ richtet sich nach der Zeitzone des Accounts.

## Hochladen und Erkennung

1. Der Superuser öffnet unter **Pakete** ein Paket (oder legt mit „Neues Paket“ eins an). Neben
   „Hinzufügen“ (manuell) gibt es **Hochladen** (Fotos nur ans Paket hängen, ohne Erkennung) und
   **Erkennen** (Fotos aufnehmen oder bis zu 20 aus der Galerie wählen, die KI liest die Vokabeln).
   Fotos eines Pakets lassen sich mit dem ✕ wieder löschen. Die Fotos werden vor dem Hochladen im Browser auf höchstens
   2000 Pixel lange Kante verkleinert (JPEG).
2. Der Server liest jedes Foto mit dem eingestellten **Bildmodell** (Einstellungen → KI →
   Bilderkennung). Ergebnis je Eintrag: Wort, Zusatz (z. B. Stammformen, Genus), Übersetzung(en),
   Sprache. Die Antwort wird mit zod geprüft; bei falschem Format gibt es genau einen zweiten Versuch.
3. In der **Vorschau** steht das Foto neben den Einträgen. Einträge korrigieren, löschen,
   ergänzen, dann als neues Paket oder in ein bestehendes speichern. Die Vokabeln sind danach
   inaktiv.
4. Die Fotos werden gespeichert (`data/uploads/<Account>/`) und sind beim Paket einsehbar.
   Nicht gespeicherte Uploads erscheinen unter „Noch nicht gespeichert“.

Komplett lokal geht das mit Ollama und einem Bildmodell (Anbieter „Ollama (lokal)“).

## Lernen und Stufen

- 6 Stufen. Aktivierte Vokabeln starten auf Stufe 1 und sind sofort fällig.
- Richtig: eine Stufe höher, fällig nach dem Intervall der neuen Stufe (Standard 5 / 10 / 20 / 40 / 80 Tage).
  Richtig auf Stufe 6: gelernt, wird nicht mehr abgefragt.
- Falsch: zurück auf Stufe 1 (oder eine Stufe zurück, einstellbar). Die Vokabel ist danach sofort
  wieder fällig und kommt am Ende derselben Sitzung noch einmal dran.
- „Heute“ richtet sich nach der Zeitzone des Accounts.

**Karteikarten:** Antwort eintippen und mit Enter prüfen lassen, oder die Karte antippen, umdrehen
und selbst bewerten: nach rechts wischen = gewusst, nach links = nicht gewusst (am Computer auch
mit ← und →).

**Antwortprüfung:**

1. Groß-/Kleinschreibung, Leerzeichen, Satzzeichen am Rand und Unicode-Form spielen keine Rolle.
   Alternativen in der Lösung (getrennt mit `,` `;` `/`) zählen einzeln, Teile in Klammern sind optional.
2. Exakter Treffer → richtig.
3. Kleiner Tippfehler → richtig mit Hinweis. Schwellen (Damerau-Levenshtein): bis 4 Zeichen exakt,
   5–8 Zeichen 1 Fehler, ab 9 Zeichen 2 Fehler.
4. Leere Antwort → falsch.
5. Alles andere entscheidet das Prüfmodell (kurzer Prompt, Antwort nur ein Wort: richtig /
   tippfehler / falsch; Synonyme zählen als richtig). **Sagt die KI „falsch“, ist es falsch.**
6. Zeitlimit (Standard 2 Sekunden, in den Einstellungen änderbar). Antwortet die KI nicht
   rechtzeitig, ist sie nicht erreichbar oder unverständlich, entscheidet WordFlow selbst (unklar =
   falsch) und zeigt den Button „Ich hatte recht“. Das Lernen läuft immer weiter.
7. KI-Urteile und Korrekturen werden je Vokabel und normalisierter Antwort gespeichert und beim
   nächsten Mal ohne KI verwendet.
8. Beim Start einer Lernsitzung wird das Prüfmodell vorgewärmt (Ollama: Modell laden mit
   `keep_alive`, sonst Verbindung öffnen).

Ziel sind unter 100 ms bis zum ersten Token und 100–300 ms für die ganze Prüfung. Das hängt von
Modell und Anbieter ab; die gemessenen Werte stehen in den Einstellungen.

## Android-App

Die Android-App ist dieselbe Oberfläche, verpackt mit Capacitor (`apps/web/android`). Sie spricht
mit deinem WordFlow-Server; iOS gibt es nicht.

### APK herunterladen

- **Fertiges APK:** im Repository unter [`release/`](release/) (z. B. `release/WordFlow-1.0.0.apk`) und
  auf GitHub unter **Releases** (Tag `v1.0.0`). Herunterladen, auf dem Handy öffnen und die
  Installation aus „unbekannten Quellen“ erlauben.
- **Automatisch gebaut:** Der Workflow **Android-Release** (`.github/workflows/android-release.yml`)
  baut ein signiertes APK, legt es als `release/WordFlow-<version>.apk` ins Repository und
  veröffentlicht es als GitHub-Release. Er startet
  - mit einem Tag, das mit `v` beginnt: `git tag v1.2.0 && git push origin v1.2.0`
    (Version kommt aus dem Tag), oder
  - von Hand unter Actions → Android-Release → **Run workflow** (Version eingeben oder leer lassen
    für die Version aus `apps/web/package.json`).
- **Updates:** Alle APKs sind mit demselben Schlüssel signiert (`apps/web/android/app/wordflow-release.keystore`),
  deshalb installiert sich eine neuere Version über die alte, die Daten bleiben erhalten. Der
  Schlüssel liegt im Repository – für den privaten Gebrauch in Ordnung, für eine
  Veröffentlichung im Play Store einen eigenen, geheimen Schlüssel verwenden.

### Erste Schritte in der App

1. Server-Adresse eingeben:
   - im Heimnetz z. B. `http://192.168.1.20:3000` (Klartext-HTTP ist in der App erlaubt),
   - über das Internet z. B. `https://wordflow.example.org` (hinter einem Reverse-Proxy mit HTTPS).
2. Anmelden (Accounts legt der Superuser an). Die App bekommt einen Token,
   der in den Capacitor Preferences gespeichert wird; Cookies werden nicht verwendet.
3. Unter **Einstellungen → Erinnerung** eine Uhrzeit wählen. Die App plant lokale
   Benachrichtigungen für die nächsten 30 Tage, an denen Vokabeln fällig sind, und plant sie bei
   jedem Öffnen neu.

„Foto aufnehmen“ öffnet direkt die Kamera, „Aus Galerie“ die Bildauswahl.

### Selbst bauen mit Android Studio

Voraussetzungen: Node.js 22.12+, JDK 21, Android Studio mit Android SDK (API 36).

```bash
npm install
npm run android:sync          # Oberfläche bauen und nach apps/web/android kopieren
npm run android:open -w @wordflow/web   # öffnet das Projekt in Android Studio
```

In Android Studio mit **Run ▶** auf ein angeschlossenes Handy oder einen Emulator installieren,
oder über **Build → Build App Bundle(s) / APK(s) → Build APK(s)** ein APK erzeugen. Ohne Android
Studio geht es auch auf der Kommandozeile: `cd apps/web/android && ./gradlew assembleDebug`
(Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`) bzw. `./gradlew assembleRelease` für das
signierte Release-APK (`app/build/outputs/apk/release/app-release.apk`).

Nach jeder Änderung an der Oberfläche erneut `npm run android:sync` ausführen.

## Projektstruktur

```
apps/server       Fastify-Server: API, Datenbank (SQLite), Stufenlogik, KI-Anbieter
apps/server/drizzle  Datenbank-Migrationen (werden beim Start automatisch ausgeführt)
apps/web          React-Oberfläche (Vite), zugleich Grundlage der Android-App
apps/web/android  Android-Projekt (Capacitor)
packages/shared   Gemeinsame Typen, Konstanten, zod-Schemas, Stufen- und Prüflogik
deploy            systemd-Dienst und Caddyfile
```

## Umsetzungsstand

- [x] 1. Grundgerüst: Server, Oberfläche mit 5 Tabs (Desktop + Handy), Deutsch/Englisch, Tests
- [x] 2. Accounts: Registrierung mit Code, Anmelden, Abmelden, Passwort ändern,
      Einrichtungsassistent (10 Schritte), schlichtes Schwarz-Weiß-Design
- [x] 3. Vokabelpakete: anlegen, bearbeiten, löschen, Sprache (Vorlagen oder frei), Richtung, Aktivieren
- [x] 4. Stufensystem und Lernen (ohne KI): Karteikarten, Tippen optional, Umdrehen, Wischen, lokale Prüfung, Wiederholung
- [x] 5. KI-Anbieter und Einrichtungsassistent: Pollinations, Anthropic, OpenAI, OpenRouter, Ollama (lokal/online), eigener Server
- [x] 6. KI-Antwortprüfung: Zeitlimit mit lokalem Fallback und „Ich hatte recht“, Cache, Latenzstatistik, Vorwärmen
- [x] 7. Hochladen und Erkennung: mehrere Fotos, Kamera/Galerie, Verkleinern, Vorschau mit Korrektur, Fotos bleiben beim Paket
- [x] 8. Startseite: fällige Vokabeln, Stufenübersicht, Tageswerte
- [x] 9. Android-App (APK): Server-Adresse, Token-Anmeldung, Erinnerungen, GitHub-Actions-Build
- [x] 10. Betrieb: Dockerfile, Docker Compose (Ollama und Caddy optional), systemd, HTTPS, README
- [x] 11. Superuser: Accountverwaltung, geteilte Pakete mit eigenem Lernstand je Account, zentrale KI,
      ZIP-Backup/Import, Update von GitHub
