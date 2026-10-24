# Antwortprüfung: lokal vs. KI

**Kurz:** Die KI ist nicht nötig. WordFlow prüft jede Antwort zuerst selbst (lokal).
Die KI wird nur bei Antworten gefragt, die die lokale Prüfung nicht entscheiden kann.
Ohne KI funktioniert alles, Sie müssen dann nur gelegentlich selbst auf **„Ich hatte recht“** klicken.

## Ablauf einer Prüfung

Code: [`apps/server/src/learn/decide.ts`](apps/server/src/learn/decide.ts)

1. **Leere Eingabe** → falsch.
2. **Lokale Prüfung** ([`packages/shared/src/check.ts`](packages/shared/src/check.ts)):
   - Groß-/Kleinschreibung, Leerzeichen und Satzzeichen am Rand werden ignoriert.
   - **Alternativen:** Bei der Lösung `Haus; Gebäude` zählt jede Bedeutung (Trennzeichen `;` `,` `/`).
     Mehrere Bedeutungen auf einmal („Haus, Gebäude“) sind auch richtig.
   - **Optionale Teile in Klammern:** `groß (bedeutend)` → „groß“ und „groß bedeutend“ sind beide richtig.
   - **Tippfehler-Toleranz** (Damerau-Levenshtein):

     | Länge der Lösung | erlaubte Fehler                       |
     | ---------------- | ------------------------------------- |
     | bis 4 Zeichen    | 0 (sonst ginge „Bar“ als „Bär“ durch) |
     | 5–8 Zeichen      | 1                                     |
     | ab 9 Zeichen     | 2                                     |

   - Passt die Antwort exakt oder mit Tippfehler → **richtig**, fertig. Die KI wird gar nicht gefragt.
3. **Gespeicherte Entscheidung:** Wurde genau diese Antwort zu diesem Wort schon einmal bewertet
   (von der KI oder durch „Ich hatte recht“), gilt dieses Urteil. Es gibt dann keinen erneuten KI-Aufruf.
4. **KI** (nur wenn ein Modell für die Aufgabe „check“ eingerichtet ist,
   [`apps/server/src/learn/ai-check.ts`](apps/server/src/learn/ai-check.ts)):
   Sie bekommt Wort, Lösung und Antwort und soll mit „richtig“, „tippfehler“ oder „falsch“ antworten.
   Das Urteil wird gespeichert und beim nächsten Mal wiederverwendet.
5. **Keine KI oder kein Urteil:** Das passiert ohne KI, bei einem Fehler oder wenn die Zeitgrenze
   (Standard 2 Sekunden, einstellbar von 0,5 bis 30 s) überschritten ist.
   Dann zählt die Antwort als **falsch**, und es erscheint der Knopf **„Ich hatte recht“**.
   Dieser Klick wird gespeichert, sodass dieselbe Antwort künftig automatisch als richtig gilt.

Außerdem gibt es eine **Selbstbewertung**, bei der man selbst angibt, ob man es wusste.
Dabei ist weder lokale Prüfung noch KI im Spiel.

## Was bringt die KI also?

Sie hilft nur in einem Fall: wenn eine Antwort richtig ist, aber **nicht in der Lösung steht**.
Typisch sind **Synonyme** oder Umschreibungen, etwa „Gebäude“, wenn nur „Haus“ eingetragen ist,
oder „riesig“ statt „sehr groß“.

|                                        | Nur lokal                             | Lokal + KI                                |
| -------------------------------------- | ------------------------------------- | ----------------------------------------- |
| Exakte Antwort / Tippfehler            | ✅ richtig                            | ✅ richtig (ohne KI-Aufruf)               |
| Synonym, das nicht in der Lösung steht | ❌ falsch → „Ich hatte recht“ klicken | ✅ meist automatisch richtig              |
| Offline / ohne API-Kosten              | ✅                                    | nur mit lokalem Ollama                    |
| Geschwindigkeit                        | sofort                                | bei unklaren Antworten bis zur Zeitgrenze |

## Empfehlung

Wenn die Vokabeln die gängigen Bedeutungen schon als Alternativen enthalten (mit `;`, `,` oder `/` getrennt),
reicht die lokale Prüfung gut aus. Die KI lohnt sich vor allem bei knappen Lösungen und vielen möglichen
Synonymen. Dafür ist ein kleines lokales Modell über Ollama (Compose-Profil `ollama`) ausreichend.
