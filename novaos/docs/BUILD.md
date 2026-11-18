# Build-Anleitung

## Voraussetzungen

- JDK 17 oder neuer (getestet mit JDK 21)
- Android SDK mit `platforms;android-35` und `build-tools;35.0.0`
- Internetzugang für Gradle-Abhängigkeiten (Google Maven, Maven Central)

```bash
# Beispiel: SDK-Kommandozeilenwerkzeuge
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
echo "sdk.dir=/pfad/zum/android-sdk" > local.properties
```

## Befehle (im Ordner `novaos/`)

| Aufgabe | Befehl |
| --- | --- |
| Debug-APK | `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` |
| Release-APK (signiert) | `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Unit-Tests Kernlogik | `./gradlew :core:model:test` |
| Robolectric-Tests (Launcher, Einstellungen, Widgets, Renderer, Backup) | `./gradlew :app:testDebugUnitTest` |
| Alle Tests | `./gradlew test` |
| Auf Gerät installieren (USB-Debugging) | `adb install -r app/build/outputs/apk/release/app-release.apk` |

## Signatur der Release-APK

Die Release-Signatur wird aus `keystore.properties` im Ordner `novaos/` gelesen (nicht im Repository):

```properties
storeFile=signing/novaos-release.jks
storePassword=…
keyAlias=novaos
keyPassword=…
```

Fehlt die Datei, entsteht eine unsignierte Release-APK. Einen eigenen Schlüssel erzeugen:

```bash
keytool -genkeypair -keystore signing/novaos-release.jks -storetype PKCS12 \
  -alias novaos -keyalg RSA -keysize 4096 -validity 10000
```

## Projektstruktur

Siehe [ARCHITECTURE.md](ARCHITECTURE.md).
