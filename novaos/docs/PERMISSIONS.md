# Berechtigungen von NovaOS

NovaOS braucht keine Root-Rechte, nutzt keine Bedienungshilfen und keine versteckten Systemberechtigungen. Jede Freigabe wird erst angefragt, wenn du die Funktion verwendest. Den aktuellen Stand zeigt **Einstellungen → Berechtigungen**.

## Ohne Nachfrage (normale Berechtigungen)

| Berechtigung | Zweck |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Wetter von Open-Meteo, WLAN-Status im Kontrollzentrum |
| `REQUEST_DELETE_PACKAGES` | Systemdialog „Deinstallieren“ öffnen (Android fragt selbst nach) |
| `SET_WALLPAPER` | Optional den NovaOS-Hintergrund auch als System-Hintergrund setzen |

## Laufzeitberechtigungen (Dialog)

| Berechtigung | Zweck | Ohne Freigabe |
| --- | --- | --- |
| `READ_CALENDAR` | Kalender- und Agenda-Widget | Monatsansicht ohne Termine |
| `READ_CONTACTS` | Favoritenkontakte, Anruf-Widget | Widget zeigt Hinweis |
| `ACCESS_COARSE_LOCATION` | Wetter am aktuellen Ort | Ort manuell eingeben |
| `ACCESS_FINE_LOCATION` | WLAN-Name für Profilregeln (Android-Vorgabe) | WLAN-Regeln greifen nicht |
| `BLUETOOTH_CONNECT` | Profilwechsel bei verbundenem Gerät | Bluetooth-Regeln greifen nicht |
| `POST_NOTIFICATIONS` | Erinnerungen aus Aufgaben | Keine Erinnerungen |

## Sonderfreigaben (Android-Einstellungen)

| Freigabe | Zweck |
| --- | --- |
| Standard-Startbildschirm (Home-Rolle, `RoleManager`) | NovaOS als Launcher; Voraussetzung für App-Verknüpfungen (Shortcuts) |
| Benachrichtigungszugriff (`NotificationListenerService`) | Nova Notification Center, Nachrichten- und Kommunikations-Widgets, Musiksteuerung, App-Badges |
| Nutzungsdatenzugriff (`PACKAGE_USAGE_STATS`) | Bildschirmzeit-Widget |
| Systemeinstellungen ändern (`WRITE_SETTINGS`) | Helligkeit und automatisches Drehen im Kontrollzentrum |
| „Nicht stören“-Zugriff (`ACCESS_NOTIFICATION_POLICY`) | Nicht-stören-Kachel |
| Geräteadministrator (nur Richtlinie `force-lock`) | Bildschirm per Doppeltippen oder Kachel sperren |

**Hinweis zum Geräteadministrator:** Solange er aktiv ist, lässt Android NovaOS nicht deinstallieren. Vorher unter **Einstellungen → Berechtigungen → Geräteadministrator entfernen** deaktivieren.

## Was nicht geht (Android-Grenzen)

- WLAN, Bluetooth, Flugmodus, NFC, Standort und Hotspot direkt schalten: Seit Android 10 ist das für normale Apps gesperrt. Die Kacheln öffnen das offizielle Systempanel.
- Die Android-Statusleiste, den Sperrbildschirm und den Benachrichtigungsschatten ersetzen: NovaOS bietet eigene Oberflächen innerhalb des Launchers. Die Android-Oberflächen bleiben bestehen.
