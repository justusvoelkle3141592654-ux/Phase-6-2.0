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

## Konfiguration

Alle Einstellungen laufen über Umgebungsvariablen oder eine `.env`-Datei im Projektordner
(Vorlage: `.env.example`).

| Variable       | Standard  | Bedeutung                                                            |
| -------------- | --------- | -------------------------------------------------------------------- |
| `PORT`         | `3000`    | Port des Servers                                                     |
| `HOST`         | `0.0.0.0` | Netzwerkadresse, auf der der Server lauscht                          |
| `DATA_DIR`     | `data`    | Ordner für Datenbank, Fotos und erzeugte Schlüssel                   |
| `TRUST_PROXY`  | `false`   | `true`, wenn ein Reverse-Proxy davor HTTPS übernimmt                 |
| `CORS_ORIGINS` | –         | Zusätzlich erlaubte Origins, kommagetrennt (Android-App ist erlaubt) |
| `LOG_LEVEL`    | `info`    | `fatal`, `error`, `warn`, `info`, `debug`, `trace` oder `silent`     |

## Projektstruktur

```
apps/server       Fastify-Server: API, Datenbank, Stufenlogik, KI-Anbieter
apps/web          React-Oberfläche (Vite), später auch Grundlage der Android-App
packages/shared   Gemeinsame Typen und Konstanten für Server und Oberfläche
```

## Umsetzungsstand

- [x] 1. Grundgerüst: Server, Oberfläche mit 5 Tabs (Desktop + Handy), Deutsch/Englisch, Tests
- [ ] 2. Accounts
- [ ] 3. Vokabelpakete
- [ ] 4. Stufensystem und Lernen (ohne KI)
- [ ] 5. KI-Anbieter und Einrichtungsassistent
- [ ] 6. KI-Antwortprüfung
- [ ] 7. Hochladen und Erkennung
- [ ] 8. Startseite mit Tageszusammenfassung
- [ ] 9. Android-App (APK) mit Erinnerungen
- [ ] 10. Betrieb (Docker, systemd, HTTPS)
