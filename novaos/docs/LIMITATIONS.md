# Bekannte Einschränkungen

## 1. Nicht auf einem echten Gerät getestet

Die Entwicklungsumgebung hat keine Hardware-Virtualisierung, ein Android-Emulator lässt sich dort nicht starten. Getestet wurde:

- 33 Unit-Tests der Kernlogik (Raster, Regeln, Gesten, Suche, Punktschrift, Backup inkl. beschädigter Dateien, Themes),
- Robolectric-Tests, die Launcher, alle Overlays, alle Themes, alle Profile, alle 29 Einstellungsseiten, alle 27 Widget-Typen in allen Layouts sowie Icon-Renderer, Wallpaper Engine und Backup über Dateien tatsächlich ausführen.

Robolectric ersetzt kein echtes Gerät. Ungeprüft sind insbesondere:

- echtes Touch-Verhalten (Gesten, Ziehen im Bearbeitungsmodus, Buchstabenleiste),
- flüssige Animationen und Bildrate,
- App-Icons echter Apps (in Robolectric gibt es keine installierten Apps),
- Benachrichtigungen, Mediensteuerung, Kalender und Kontakte mit echten Daten,
- Verhalten als Standard-Launcher nach einem Neustart,
- Android-Widgets anderer Apps,
- das Nothing-Phone-(3a)- bzw. CMF-Gerät selbst.

Bitte nach der Installation die Prüfliste in [INSTALL.md](INSTALL.md) durchgehen und Probleme melden.

## 2. Android-Plattformgrenzen

| Wunsch | Grenze | Umsetzung in NovaOS |
| --- | --- | --- |
| WLAN, Bluetooth, Flugmodus, NFC, Standort, Hotspot schalten | seit Android 10 für Apps gesperrt | Kachel öffnet das offizielle Systempanel |
| Systemweite Statusleiste ersetzen | nicht möglich ohne Systemrechte | eigene NovaOS-Statuszeile im Launcher |
| Benachrichtigungsschatten ersetzen | geschützt | eigenes Notification Center im Launcher; Android-Schatten bleibt |
| Sperrbildschirm ersetzen | geschützt | nicht umgesetzt; optional NovaOS-Hintergrund als System-Hintergrund setzen |
| Bildschirm sperren | nur über Geräteadministrator oder Bedienungshilfen | Geräteadministrator (nur „Sperren“); Bedienungshilfen werden bewusst nicht genutzt |
| Playlists anderer Apps | keine allgemeine API | Warteschlange der laufenden Medien-App |
| App-Verknüpfungen | nur für den Standard-Launcher | Hinweis, solange NovaOS nicht Standard ist |
| Bildschirmzeit | Sonderfreigabe | Hinweis im Widget, solange nicht erteilt |
| Echte Echtzeit-Unschärfe hinter Flächen | Compose hat keinen Backdrop-Blur | vorberechnete, weichgezeichnete Kopie des Hintergrunds (energiesparend); beim System-Hintergrund nur Tönung |
| Profilwechsel im Hintergrund | würde Hintergrunddienst erfordern | Regeln werden geprüft, wenn der Homescreen sichtbar wird |

## 3. Bewusst offen / spätere Ausbaustufen

- Online-Theme-Community (der lokale Marketplace mit Datei-Import/-Export ist umgesetzt).
- Englische Übersetzung der Auswahlwerte (Formen-, Stil- und Kategorienamen sind in beiden Sprachen deutsch).
- Instrumentierte UI-Tests auf Gerät/Emulator und Performance-Messungen (Macrobenchmark).
- Wiedergabe-/Albumcover-Widgets zeigen nur, was die Medien-App über ihre MediaSession bereitstellt.
