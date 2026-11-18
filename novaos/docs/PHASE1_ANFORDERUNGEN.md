# NovaOS 1.0 – Phase 1: Verbindliche Produktanforderungen

Ergebnis der Benutzerfragen. Grundlage: Master-Prompt und `NovaOS_Designkonzept.pdf`.

## Design

| Thema | Entscheidung |
| --- | --- |
| Rolle des Designkonzepts | Verfeinerte Richtung + Inspiration. Stimmung und Aufbau orientieren sich am Bild, Details dürfen neu gestaltet werden. Größere Abweichungen werden vorher gezeigt. |
| Freie Wahl | Der Benutzer wählt seinen Look frei (Einrichtungsassistent, Themes, Einstellungen). |
| Punktmatrix | Stark: Uhr, Zahlen, Überschriften, App-Namen, Punkt-Icons, Punkt-Raster im Hintergrund. Einstellbar. |
| Hell/Dunkel | Standard Dunkel; heller Modus voll unterstützt. |
| Akzentfarbe | Standard Rot. |
| Icon-Stil | Wählbar in den Einstellungen; Standard: Original farbig in einheitlicher Form. |
| Liquid Glass | Standard Dezent; abschaltbar. |
| Animation | Standard Smooth. |

## Funktionen

| Thema | Entscheidung |
| --- | --- |
| Einstellungen | Eigenes App-Icon „NovaOS Einstellungen“ (zweite Launcher-Activity in derselben APK). |
| Standardlauncher | Anfrage über RoleManager (Home-Rolle). Android verlangt die Bestätigung durch den Benutzer. |
| Homescreen-Raster | Standard 4 × 6; freier Layoutmodus zusätzlich. |
| Widgets Version 1 | Uhr, Wetter, Kalender, Akku, Speicher, Systemstatus, Musiksteuerung, Aufgaben/Projekte/Notizen. |
| Wetter | Open-Meteo (ohne API-Schlüssel); Standort per Freigabe oder manuell eingegebener Ort. |
| Kontrollzentrum | Eigenes Panel. Direkt: Taschenlampe, Helligkeit, Lautstärke, Medien, Nicht stören (Freigabe), Rotation (Freigabe). WLAN/Bluetooth/Flugmodus öffnen das offizielle Android-Panel. Einschränkungen werden angezeigt. Standard-Layout: Nothing Grid. |
| Benachrichtigungen | Eigenes Center über Notification Listener API mit Benutzerfreigabe. Android-Schatten bleibt bestehen. |
| App Drawer | Vertikales Raster mit Suche, Filter-Chips, Buchstabenleiste. |
| Geste Wischen nach unten | Öffnet das NovaOS-Kontrollzentrum. |
| Profile | Manueller Wechsel + automatisch nach Uhrzeit/Wochentag, Bluetooth-Gerät, WLAN-Netz (jeweils mit den nötigen Android-Berechtigungen). |
| Themes | Nur lokale Bibliothek + Import/Export. Kein Online-Tab in Version 1.0. |

## Technik und Auslieferung

| Thema | Entscheidung |
| --- | --- |
| Quellcode | Unterordner `novaos/` in diesem Repository. |
| Mindestversion | Android 12 (API 31). |
| Sprache | Deutsch + Englisch, folgt der Systemsprache. |
| Zielgerät | Nothing Phone (3a) oder CMF – genaues Modell und Android-Version noch offen. |
| Signatur | Eigener Release-Keystore; wird nicht ins Repository eingecheckt. |
| Lieferung | APK als Download in der Sitzung. |

## Offen

- Genaues Modell und Android-Version des Zielgeräts.
- Test auf dem Gerät (inkl. Standardlauncher nach Neustart) muss der Benutzer durchführen.
