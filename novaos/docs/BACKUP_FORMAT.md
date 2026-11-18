# NovaOS-Backupformat (`.novabackup`)

Eine Backup-Datei ist ein ZIP-Archiv.

| Eintrag | Inhalt |
| --- | --- |
| `manifest.json` | Formatkennung, Version, Erstellzeit, enthaltene Bereiche, SHA-256-Prüfsummen |
| `config.json` | Gesamte Launcher-Konfiguration (`NovaConfig`): Profile, Layouts, Widgets, Themes, Icons, Gesten, Einstellungen |
| `productivity.json` | Aufgaben, Projekte, Notizen (optional) |
| `files/…` | Hintergrundbilder und eigene Icons, auf die die Konfiguration verweist (optional) |

## manifest.json

```json
{
  "format": "novaos-backup",
  "formatVersion": 1,
  "appVersion": "1.0.0",
  "createdAtMs": 1763470000000,
  "sections": ["LAYOUTS", "WIDGETS", "ICONS", "THEMES", "COLORS", "ANIMATIONS", "GESTURES", "PROFILES", "SETTINGS", "PRODUCTIVITY", "FILES"],
  "configSha256": "…",
  "productivitySha256": "…",
  "files": [{ "path": "wallpapers/1b2c.jpg", "sha256": "…", "size": 123456 }]
}
```

## Prüfungen beim Import

Eine Datei wird abgelehnt, wenn

- sie kein ZIP ist oder `manifest.json` fehlt → „kein NovaOS-Backup“,
- `format` nicht `novaos-backup` ist,
- `formatVersion` größer als die unterstützte Version ist → „nicht unterstützte Version“,
- eine Prüfsumme nicht stimmt → „verändert oder beschädigt“,
- ein im Manifest genannter Teil fehlt,
- `config.json` nicht gelesen werden kann,
- ein Dateipfad unzulässig ist (`..`, absolute Pfade).

Erst wenn alle Prüfungen bestanden sind, kann wiederhergestellt werden.

## Selektive Wiederherstellung

| Bereich | Wirkung |
| --- | --- |
| PROFILES | ersetzt alle Profile und Profilregeln |
| LAYOUTS | Homescreen-Seiten, Dock und Hintergrund je Profil (gleiche Profil-ID bzw. aktives Profil) |
| WIDGETS | Widget-Einstellungen, eigene Widgets, Widget-Vorlagen |
| ICONS | Icon-Stil, Einzel-Icons, Icon-Pakete |
| THEMES | Theme der Profile und eigene Theme-Bibliothek |
| COLORS | nur Farben der Themes |
| ANIMATIONS | nur Animationseinstellungen |
| GESTURES | Gestenbelegung |
| SETTINGS | allgemeine Einstellungen |
| PRODUCTIVITY | Aufgaben, Projekte, Notizen |
| FILES | Bilder |

## Einzel-Exporte

Themes, Widget-Vorlagen, Profile und Icon-Pakete werden als JSON exportiert:

```json
{ "format": "novaos-theme", "formatVersion": 1, "theme": { … } }
```

Formatkennungen: `novaos-theme`, `novaos-widget`, `novaos-profile`, `novaos-iconpack`. Dateien mit falscher Kennung oder fehlendem Inhalt werden abgelehnt.

Implementierung: `core/model/src/main/kotlin/app/novaos/model/Backup.kt`.
