# NovaOS 1.0 – Funktionsübersicht

Status-Legende:
- **umgesetzt**: im Code vorhanden und in automatisierten Tests ohne Absturz gerendert bzw. geprüft
- **eingeschränkt**: umgesetzt, aber Android setzt Grenzen (siehe Spalte)

Kein Eintrag ist auf einem echten Gerät getestet (siehe [LIMITATIONS.md](LIMITATIONS.md)).

## Launcher Core

| Funktion | Status | Hinweis |
| --- | --- | --- |
| Home-Rolle, Standard-Startbildschirm per RoleManager | umgesetzt | Bestätigung durch Android-Dialog |
| App-Liste über LauncherApps inkl. Arbeitsprofil, Live-Aktualisierung bei (De-)Installation | umgesetzt | |
| App-Start mit Startanimation je Animationsstil | umgesetzt | `ActivityOptions` |
| App-Info, Deinstallieren (Systemdialog) | umgesetzt | |
| App-Verknüpfungen (Shortcuts) im App-Menü, auf Homescreen ablegen | eingeschränkt | nur als Standard-Launcher (Android-Vorgabe) |
| Home-Taste springt zur Startseite / schließt Overlays | umgesetzt | |
| Eigene Einstellungs-App mit eigenem Icon (gleiche APK) | umgesetzt | |

## Homescreen

| Funktion | Status |
| --- | --- |
| Beliebig viele Seiten, Startseite wählbar, Seiten verschieben/löschen | umgesetzt |
| Raster 3–7 Spalten × 4–9 Zeilen, automatische Neuanordnung | umgesetzt |
| Freier Layoutmodus (Position/Größe unabhängig vom Raster) | umgesetzt |
| Bearbeitungsmodus: Ziehen, Größe ändern, auf Nachbarseite schieben | umgesetzt |
| Ordner (App auf App ziehen), umbenennen, herausnehmen, 4 Vorschau-Stile | umgesetzt |
| Dock: Position, Form, Hintergrund, Höhe, Transparenz, Anzahl, Namen | umgesetzt |
| Seitenübergänge: Schieben, Überblenden, Zoomen, Tiefe, Würfel, Punkte | umgesetzt |
| Seitenindikator: Punkte, Linie, Punktzahlen | umgesetzt |
| Endloses Blättern, Scrollverhalten | umgesetzt |
| NovaOS-Statuszeile (Datum, Akku) | umgesetzt |
| Hintergrundeffekte: Punktraster, Vignette, Linien | umgesetzt |
| App-Badges aus Benachrichtigungen | umgesetzt (mit Benachrichtigungszugriff) |

## Nova Customization Engine

| Bereich | Einstellbar |
| --- | --- |
| Farben / Theme-Editor | 11 Farben je Palette (hell + dunkel), Verläufe, Deckkraft, Kontrast mit WCAG-Prüfung, HEX-Eingabe, Presets, gemerkte Farben, dynamische Wallpaper-Farben |
| Typografie-Studio | 5 Textrollen (Uhr, Überschrift, Text, Widgets, App-Namen): Schrift, Größe, Gewicht, Buchstaben-/Zeilenabstand, Farbe, Transparenz; Punktmatrix-Stufe, Punktschrift, Punkt-App-Namen |
| Icon-Studio | 9 Formen, Original/Monochrom/Punkte, Symbolgröße, Farben, Kontur, Schatten, Transparenz, Punktraster; Einzel-Icons; Bild importieren; Icon aus Symbol erstellen; eigener App-Name; Icon-Pakete speichern/anwenden/exportieren/importieren |
| Liquid-Glass-Studio | Transparenz, Unschärfe, Reflexion, Tiefe, Rahmen, Schatten, Glanz, Materialstärke, Verläufe, Abdunklung; abschaltbar |
| Animation Studio | 7 Stile, Geschwindigkeit, Dauer, Stil je Bereich (10 Bereiche), Live-Vorschau, Performance-Modus |
| Live-Vorschau | echte verkleinerte Homescreen-Vorschau in den Studios und im Assistenten |

## Nova Widget Engine (27 Typen)

| Kategorie | Widgets | Datenquelle |
| --- | --- | --- |
| System | Uhr, Wetter, Kalender, Akku, Speicher, Arbeitsspeicher, Bildschirmzeit, Systemstatus | BatteryManager, StatFs, ActivityManager, Open-Meteo, CalendarContract, UsageStatsManager |
| Kommunikation | Nachrichten, Favoritenkontakte, Anrufe, Kommunikation | NotificationListener, ContactsContract (Anruf über ACTION_DIAL) |
| Produktivität | Aufgaben, Projekte, Notizen, Erinnerungen, Kalenderübersicht, Fortschritt | lokale Daten, CalendarContract |
| Medien | Musiksteuerung, Albumcover, Warteschlange, Medien-Apps | MediaSessionManager |
| Apps | App-Schnellzugriffe, Favoriten, App-Informationen, Verknüpfungen | LauncherApps, PackageManager |
| Eigene | Widget Creator | – |

- Widget-Editor: Größe, Position, Form, Eckenradius, Hintergrund (Glas/Fläche/Verlauf/Punkte/keiner), Transparenz, Farben, Schrift, Textgröße, Rahmen, Schatten, Innenabstand, Layout, Informationsdichte, Animation, Tipp-Verhalten, Punktmatrix, typspezifische Optionen.
- Widget Creator: Elemente Text, Punkttext, Symbol, Infofeld (14 Datenfelder), Fortschritt (7 Quellen), Fläche; ziehen und skalieren; Farben, Größen, Ausrichtung, Tipp-Aktion.
- Vorlagen: 18 eingebaute in 8 Kategorien; eigene speichern, exportieren, importieren.
- Android-App-Widgets anderer Apps: hinzufügen (AppWidgetHost, Bind- und Konfigurationsdialog), entfernen.
- **Wiedergabelisten:** Android erlaubt keinen allgemeinen Zugriff auf Playlists anderer Apps. Umgesetzt ist die Warteschlange der laufenden Medien-App, sofern die App sie bereitstellt.

## Nova Control Center

- Eigenes Panel (Wischen nach unten), 5 Layouts: Compact, Nothing Grid, Glass Panel, Android Classic, Custom.
- Kacheln hinzufügen/entfernen/sortieren, Breite (1×/2×/4×) und Höhe, Farbe aktiver Kacheln, Transparenz, Rundung, eigene App-Schnellzugriffe.
- Direkt schaltbar: Taschenlampe, Nicht stören\*, Auto-Drehen\*, Helligkeit (Regler/Auto)\*, Lautstärke, Medien, Sperren\*, Profilwechsel. (\* mit Freigabe, Kachel zeigt Schloss-Symbol)
- WLAN, Bluetooth, Flugmodus, Internet, NFC, Standort, Hotspot: **öffnen das Android-Systempanel** (Android-Vorgabe).

## Nova Notification Center

Gruppierung (App, Zeit, Wichtigkeit, keine), Sortierung, Vorschau, Zeitangaben, Wischen zum Entfernen, „Alle löschen“, Schnellaktionen, Direktantwort, „Verwalten“ (Android-Benachrichtigungseinstellungen der App). Gestaltung: Kartenradius, Abstand, Hintergrund, Schrift, Icon-Größe, Transparenz, Animation.

## Nova App Drawer

Suche (Umlaut-Normalisierung, Treffer auch bei ausgelassenen Buchstaben), Websuche, Favoriten, Kategorien (automatisch + manuell), eigene Drawer-Ordner, Arbeitsprofil-Filter, Buchstabenleiste, Sortierung (A–Z, Z–A, meistgenutzt, zuletzt installiert, eigene Reihenfolge), Apps ausblenden, 3 Stile (Raster, Seiten, Liste), Raster-/Icon-/Abstandseinstellungen, 4 Suchleisten-Designs, Hintergrund, Transparenz, Animationsstil.

## Nova Gesture Engine

9 Gesten (Wischen ↑ ↓ ← →, Doppeltippen, langes Drücken, zwei Finger ↑ ↓, Zusammenziehen) mit 12 Aktionen, darunter App starten und Widget öffnen. Gesten aus System-Gestenbereichen werden ignoriert; Konflikthinweise werden angezeigt. „Bildschirm sperren“ über den Geräteadministrator.

## Themes, Profile, Wallpaper, Assistent, Einstellungen, Backup

| Bereich | Umfang |
| --- | --- |
| Themes | 9 eingebaute (NovaOS, Nothing Dots, Liquid Glass, Monochrome, Dark Premium, Minimal Android, Red Accent, Silver Edition, Experimental); lokaler Marketplace mit Suche, Vorschau, Installieren, Bearbeiten, Erstellen, Export/Import |
| Profile | Standard, Work, Gaming, Minimal, Media + beliebig viele eigene; duplizieren, umbenennen, löschen, exportieren, importieren; automatische Regeln nach Zeit/Tagen, Bluetooth-Gerät, WLAN mit Priorität |
| Wallpaper | eigene Bilder (Fotoauswahl), 7 generierte NovaOS-Designs, einfarbig, System-Hintergrund; Zoom, Position, Unschärfe, Helligkeit, Kontrast, Farbüberlagerung; pro Seite; als System-Hintergrund setzen |
| Einrichtungsassistent | 12 Schritte mit Vorschau, jederzeit zurück, am Ende Home-Rolle |
| Einstellungen | 22 Kategorien, Suche, Live-Vorschau |
| Backup | siehe [BACKUP_FORMAT.md](BACKUP_FORMAT.md) |
| Sprachen | Deutsch und Englisch nach Systemsprache (Auswahlwerte wie Formen- oder Stilnamen sind derzeit nur deutsch) |
