# Installation auf dem Handy

## 1. APK installieren

1. `NovaOS-1.0.0-release.apk` auf das Handy laden (z. B. über den Download hier im Chat).
2. Datei in der Dateien-App antippen.
3. Falls Android fragt: Installation aus dieser Quelle erlauben (**Einstellungen → Apps → Spezieller App-Zugriff → Unbekannte Apps installieren**).
4. Installieren.

Die Debug-APK (`…-debug.apk`) hat eine eigene Paket-ID (`app.novaos.launcher.debug`) und kann parallel installiert werden.

## 2. Als Standard-Startbildschirm festlegen

- Beim ersten Start führt der Einrichtungsassistent durch alle Schritte. Im letzten Schritt fragt Android, ob NovaOS Standard-Startbildschirm werden soll.
- Später: **NovaOS Einstellungen → Allgemein → NovaOS als Standard festlegen**
- oder in Android: **Einstellungen → Apps → Standard-Apps → Startbildschirm-App → NovaOS**.

Nach einem Neustart startet Android automatisch den gewählten Standard-Startbildschirm.

## 3. Optionale Freigaben

Siehe [PERMISSIONS.md](PERMISSIONS.md). Empfohlen: Benachrichtigungszugriff (für Benachrichtigungen, Musik, Badges).

## 4. Prüfliste für den ersten Test

| Nr. | Prüfen | Erwartung |
| --- | --- | --- |
| 1 | Home-Taste | NovaOS erscheint |
| 2 | Neustart des Handys | NovaOS erscheint nach dem Entsperren |
| 3 | Nach oben wischen | App Drawer mit allen Apps und Icons |
| 4 | Nach unten wischen | Nova Control Center |
| 5 | Taschenlampen-Kachel | Licht geht an/aus |
| 6 | Nach rechts wischen (erste Seite) | Notification Center |
| 7 | Langes Drücken auf freie Fläche | Schnellmenü |
| 8 | „Homescreen bearbeiten“, Widget ziehen und Größe ändern | Widget bewegt sich und rastet ein |
| 9 | Theme wechseln (Einstellungen → Themes) | Homescreen ändert sich sofort |
| 10 | Wetter-Ort setzen | Wetter-Widget zeigt Temperatur |
| 11 | Backup erstellen, Einstellung ändern, Backup wiederherstellen | Zustand wie vorher |
| 12 | Doppeltippen (nach Geräteadministrator-Freigabe) | Bildschirm wird gesperrt |

## 5. Deinstallieren

Falls der Geräteadministrator aktiviert wurde: zuerst **NovaOS Einstellungen → Berechtigungen → Geräteadministrator entfernen**, dann normal deinstallieren.

## 6. Updates

Neue Release-Versionen lassen sich nur über die bestehende Installation einspielen, wenn sie mit demselben Schlüssel signiert sind. Bewahre `novaos-release.jks` und `keystore.properties` sicher auf. Ohne diesen Schlüssel muss NovaOS vor einem Update deinstalliert werden; die Einstellungen gehen dabei verloren (vorher Backup erstellen).
