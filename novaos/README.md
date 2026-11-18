# NovaOS 1.0

Ein eigenständiger, stark personalisierbarer Android-Launcher: Nothing-inspirierte Punktmatrix, dezente Liquid-Glass-Flächen und Android-Bedienbarkeit. Kotlin und Jetpack Compose, ab Android 12.

## Dokumentation

| Dokument | Inhalt |
| --- | --- |
| [docs/INSTALL.md](docs/INSTALL.md) | Installation, Standardlauncher, Prüfliste |
| [docs/BUILD.md](docs/BUILD.md) | Build-Anleitung, Tests, Signatur |
| [docs/FEATURES.md](docs/FEATURES.md) | Übersicht aller implementierten Funktionen |
| [docs/PERMISSIONS.md](docs/PERMISSIONS.md) | Berechtigungen und wofür sie dienen |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | Bekannte Einschränkungen und Testabdeckung |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module, Datenfluss, Energie |
| [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Designsystem |
| [docs/BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md) | Dokumentiertes Backupformat |
| [docs/PHASE1_ANFORDERUNGEN.md](docs/PHASE1_ANFORDERUNGEN.md) | Verbindliche Produktanforderungen |

## Schnellstart

```bash
cd novaos
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties
./gradlew assembleDebug
./gradlew test
```

## Lizenzen

Schriften: SIL Open Font License 1.1 (`docs/licenses`). Wetterdaten: Open-Meteo.com (CC BY 4.0).
