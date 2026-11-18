# Architektur

Kotlin, Jetpack Compose, Material 3 als technische Basis, MVVM, Repository-Pattern, manuelle Dependency Injection (`AppContainer`), DataStore mit JSON (kotlinx.serialization), Coroutines. Room wird nicht benötigt: Die Konfiguration ist ein zusammenhängendes Dokument und wird als Ganzes gespeichert, gesichert und wiederhergestellt.

## Module

| Modul | Aufgabe | Wichtige Dateien |
| --- | --- | --- |
| `core:model` (reines Kotlin/JVM) | Datenmodell und plattformunabhängige Logik: Themes, Layout, Raster-Engine, Widgets, Gesten-Klassifizierung, Profilregeln, Presets, Backupformat, Punktschrift, App-Suche, Benachrichtigungs-Gruppierung | `Theme.kt`, `Layout.kt`, `Widgets.kt`, `Gestures.kt`, `Profiles.kt`, `Backup.kt`, `DotFont.kt` |
| `core:design` | Design Engine: Theme-Auflösung, Schriften, Punktmatrix-Darstellung, Glasflächen, Animationsstile, Bedienelemente | `NovaTheme.kt`, `DotMatrix.kt`, `Surfaces.kt`, `Motion.kt`, `Components.kt` |
| `core:data` | Repositories: Konfiguration (DataStore), Produktivität, Wetter-Cache, Apps (LauncherApps), Icon-Renderer, Wallpaper Engine, Dateiablage, Backup über SAF | `Stores.kt`, `AppsRepository.kt`, `IconRenderer.kt`, `WallpaperRenderer.kt`, `BackupManager.kt` |
| `engine:system` | Systemintegration: Notification Listener, Media Sessions, Kontrollzentrum-Aktionen, Geräteadministrator, Systemwerte, Wetter (Open-Meteo), Kalender, Kontakte, Erinnerungen, Profilkontext, Home-Rolle, Berechtigungsübersicht | `Notifications.kt`, `ControlActions.kt`, `SystemInfo.kt`, `DataSources.kt`, `Platform.kt` |
| `engine:widgets` | Nova Widget Engine: Rahmen, 27 Widget-Typen, Renderer für Widget-Creator-Entwürfe | `NovaWidget.kt`, `SystemWidgets.kt`, `PersonalWidgets.kt`, `MediaAppWidgets.kt`, `CustomWidget.kt` |
| `app` | Launcher Core und Oberflächen: Activities, ViewModel, Homescreen, Overlays, Gesten, Einstellungen mit Studios, Einrichtungsassistent | `MainActivity.kt`, `SettingsActivity.kt`, `NovaController.kt`, `ui/launcher/*`, `ui/settings/*`, `ui/setup/*` |

Abhängigkeiten: `app` → `engine:widgets` → `engine:system` → `core:data` → `core:model`; `engine:widgets` → `core:design` → `core:model`.

## Datenfluss

1. `ConfigRepository` liefert `NovaConfig` als `StateFlow` (eine JSON-Datei im App-Speicher; beschädigte Dateien werden gesichert und durch Standardwerte ersetzt).
2. Alle Änderungen laufen über `NovaController` (`update { … }`), der jede neue Konfiguration normalisiert (`ConfigMigrations.normalize`).
3. Launcher (`MainActivity`) und Einstellungen (`SettingsActivity`) laufen im selben Prozess und beobachten denselben Flow. Änderungen in einem Studio sind deshalb sofort auf dem Homescreen sichtbar.
4. `NovaTheme` löst das Theme des aktiven Profils (Modus, Kontrast, dynamische Farbe, Performance-Modus) in Compose-Werte auf.

## Energie und Leistung

- Kein Hintergrunddienst. Akku-Broadcast, Kontrollzentrum-Beobachter, Profilkontext und App-Widget-Host laufen nur zwischen `onStart` und `onStop`.
- Wetter höchstens alle 30 Minuten, nur wenn der Homescreen sichtbar ist.
- Hintergrund und Glas-Unschärfe werden einmal berechnet und zwischengespeichert (LRU).
- App-Icons werden im Hintergrund gerendert und zwischengespeichert (24 MB LRU).
- Uhren aktualisieren minütlich (Sekunden nur auf Wunsch).
- Release-Build mit R8 (Code- und Ressourcen-Verkleinerung); `profileinstaller` installiert die mitgelieferten Baseline-Profile der Compose-Bibliotheken.
