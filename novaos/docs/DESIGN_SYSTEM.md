# Designsystem (Phase 2)

Grundlage: [PHASE1_ANFORDERUNGEN.md](PHASE1_ANFORDERUNGEN.md) und `NovaOS_Designkonzept.pdf`.

## Standard-Look „NovaOS“

| Element | Wert |
| --- | --- |
| Modus | Dunkel (hell vollständig vorhanden) |
| Hintergrund / Oberfläche | `#000000` / `#16161A`, Oberflächen-Deckkraft 72 % |
| Text / sekundär | `#F2F2F2` / `#9A9AA0` |
| Akzent | Rot `#E5202E` |
| Punktmatrix | Stark: Uhr, Zahlen, Überschriften (Doto), Punktraster im Hintergrund, Punkt-Seitenzahlen |
| Glas | Dezent: Transparenz 45 %, Unschärfe 18 dp, Reflexion 25 %, heller Rand |
| Icons | Original farbig in Squircle-Form |
| Animation | Smooth (320 ms, FastOutSlowIn) |
| Hintergrund | generierte Chrom-Kugeln mit rotem Lichtpunkt |

## Bausteine

- **Nova Dot Matrix**: eigene 5×7-Punktschrift (Ziffern, A–Z, Umlaute, Satzzeichen), mit schwach leuchtenden „aus“-Punkten wie bei einer echten Anzeige.
- **Schriften (SIL OFL)**: Space Grotesk (Text), Doto (Punkt-Überschriften), Space Mono, JetBrains Mono, Inter.
- **Oberflächen**: Glas, Fläche, Verlauf, Punkte, keiner – einheitlich für Widgets, Dock, Drawer, Kontrollzentrum, Benachrichtigungen, Ordner.
- **Abschnittsmarke**: roter Punkt vor Großbuchstaben-Überschrift (aus dem Designkonzept).
- **Bedienelemente**: Schalter mit Punkten, Schieberegler mit Punktspur, Chips, Farbwahl mit HEX-Eingabe.
- **Fortschritt**: Punktring und Punktreihe statt durchgezogener Balken.

## Designverteilung

Nothing-inspiriert (Punktmatrix, Monochrom, Geometrie) bildet die Grundlage, Liquid Glass ergänzt Tiefe in Karten und Panels, Android-Bedienmuster (Wischen nach oben für Apps, langes Drücken, Schnellpanels) sorgen für Vertrautheit. Alle drei Anteile sind über Themes und Studios verschiebbar – bis hin zu „Glas aus“ oder „Punktmatrix aus“.
