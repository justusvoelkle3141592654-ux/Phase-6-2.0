# Gero – KI-gestützter Vokabeltrainer

Selbst gehosteter Vokabeltrainer mit Karteikarten-Prinzip (6 Stufen bis ins Langzeitgedächtnis).
Vokabeln werden per Foto aus dem Vokabelheft erkannt, Antworten prüft ein schnelles KI-Modell.

> **Stand:** Das Projekt wird in Schritten aufgebaut. Siehe [Umsetzungsstand](#umsetzungsstand).

## Voraussetzungen

- Node.js **22.12 oder neuer** (empfohlen: aktuelle LTS-Version)
- npm (kommt mit Node.js)

## Installation

```bash
git clone https://github.com/justusvoelkle3141592654-ux/Gero.git
cd Gero
npm install
cp .env.example .env   # optional, Standardwerte funktionieren
```

## Starten

### Entwicklung

```bash
npm run dev
```

- Oberfläche: <http://localhost:5173> (mit automatischem Neuladen)
- Server/API: <http://localhost:3000/api/health>

Die Entwicklungs-Oberfläche leitet alle `/api`-Aufrufe an den Server weiter. Sie ist auch vom
Handy im selben WLAN erreichbar: `http://<IP-des-Rechners>:5173`.

### Produktion

```bash
npm run build
npm start
```

Der Server liefert dann Oberfläche und API gemeinsam auf <http://localhost:3000> aus.

## Tests

```bash
npm test          # alle Tests (Server, Web, Shared)
npm run typecheck # TypeScript-Prüfung aller Pakete
```

Nach einer Änderung an `apps/server/src/db/schema.ts` eine neue Migration erzeugen:

```bash
npm run db:generate -w @gero/server
```

## Konfiguration

Alle Einstellungen laufen über Umgebungsvariablen oder eine `.env`-Datei im Projektordner
(Vorlage: `.env.example`).

| Variable            | Standard     | Bedeutung                                                            |
| ------------------- | ------------ | -------------------------------------------------------------------- |
| `PORT`              | `3000`       | Port des Servers                                                     |
| `HOST`              | `0.0.0.0`    | Netzwerkadresse, auf der der Server lauscht                          |
| `DATA_DIR`          | `data`       | Ordner für Datenbank, Fotos und erzeugte Schlüssel                   |
| `TRUST_PROXY`       | `false`      | `true`, wenn ein Reverse-Proxy davor HTTPS übernimmt                 |
| `CORS_ORIGINS`      | –            | Zusätzlich erlaubte Origins, kommagetrennt (Android-App ist erlaubt) |
| `LOG_LEVEL`         | `info`       | `fatal`, `error`, `warn`, `info`, `debug`, `trace` oder `silent`     |
| `REGISTRATION_CODE` | wird erzeugt | Code, den man zum Registrieren braucht (siehe unten)                 |
| `APP_SECRET`        | wird erzeugt | Hauptschlüssel für die Verschlüsselung gespeicherter API-Schlüssel   |

## Accounts

Jede Person legt sich selbst einen Account an (E-Mail + Passwort). Damit sich nicht jeder
registrieren kann, braucht man dafür den **Registrierungscode** des Servers. Es werden keine
E-Mails verschickt.

- Ist `REGISTRATION_CODE` nicht gesetzt, erzeugt der Server beim ersten Start einen Code
  (z. B. `U4JM-E3E5-KVHH`), speichert ihn in `data/secrets.json` und schreibt ihn bei jedem Start
  ins Log (`Registrierungscode: …`). Groß-/Kleinschreibung spielt bei der Eingabe keine Rolle.
- Dasselbe gilt für `APP_SECRET`. Mit diesem Schlüssel werden später die API-Schlüssel der
  KI-Anbieter verschlüsselt. **`data/secrets.json` sichern und nicht verlieren**, sonst müssen
  alle API-Schlüssel neu eingegeben werden. Die Datei ist nur für den Besitzer lesbar (Rechte 600).
- Anmeldung im Browser über ein Cookie (`gero_session`, HttpOnly). Die Android-App bekommt
  stattdessen einen Token. Sitzungen laufen nach 90 Tagen ohne Nutzung ab.
- Anmelden, Registrieren und Passwort ändern sind auf 10 Versuche pro Minute begrenzt.
- Wer das Passwort ändert, wird auf allen anderen Geräten abgemeldet.

## Einrichtungsassistent

Nach der Registrierung führt ein Assistent in zehn Schritten durch die Einrichtung:

1. Sprache (Deutsch/Englisch)
2. Name (optional)
3. Zeitzone (legt fest, wann ein neuer Tag beginnt)
4. KI-Anbieter für die Antwortprüfung (voreingestellt: Pollinations.ai)
5. Modell für die Antwortprüfung (voreingestellt: `openai/gpt-oss-20b`)
6. KI für die Tageszusammenfassung
7. Bilderkennung (bildfähiges Modell)
8. Zeitlimit der KI-Prüfung (Standard: 2 Sekunden)
9. Lernen: Intervalle der Stufen 2–6 (Standard: 5 / 10 / 20 / 40 / 80 Tage) und Verhalten bei falschen Antworten
10. Übersicht und Fertig

Die Schritte 4–7 zeigen vorerst nur die Voreinstellung. Anbieterwahl, „Verbindung testen“
und die Auswahl des Bildmodells kommen mit Schritt 5 des Umsetzungsplans. Alle Werte lassen sich
später in den Einstellungen ändern.

## Projektstruktur

```
apps/server       Fastify-Server: API, Datenbank (SQLite), Stufenlogik, KI-Anbieter
apps/server/drizzle  Datenbank-Migrationen (werden beim Start automatisch ausgeführt)
apps/web          React-Oberfläche (Vite), später auch Grundlage der Android-App
packages/shared   Gemeinsame Typen und Konstanten für Server und Oberfläche
```

## Umsetzungsstand

- [x] 1. Grundgerüst: Server, Oberfläche mit 5 Tabs (Desktop + Handy), Deutsch/Englisch, Tests
- [x] 2. Accounts: Registrierung mit Code, Anmelden, Abmelden, Passwort ändern,
      Einrichtungsassistent (10 Schritte), schlichtes Schwarz-Weiß-Design
- [ ] 3. Vokabelpakete
- [ ] 4. Stufensystem und Lernen (ohne KI)
- [ ] 5. KI-Anbieter und Einrichtungsassistent
- [ ] 6. KI-Antwortprüfung
- [ ] 7. Hochladen und Erkennung
- [ ] 8. Startseite mit Tageszusammenfassung
- [ ] 9. Android-App (APK) mit Erinnerungen
- [ ] 10. Betrieb (Docker, systemd, HTTPS)
