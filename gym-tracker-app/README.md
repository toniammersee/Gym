# Gym Tracker

Gym-Tracker fürs Handy: Sätze loggen (mit Supersätzen, Notizen und Pausenzeit je Übung), Vorlagen, Wochenplan mit Erinnerung, Trainingsvergleich, Kalender, Fortschritt, Körpermaße, Erholung, Essen mit Makros, Wasser, Supplements, Lebensmittel-Datenbank, Barcode-Scan, Rechner, App-Icon und Daten-Export, eigene Übungen, Gewichtsvorschlag, Ziele, Abzeichen, Fortschrittsfotos und Farbwahl.

Die App ist eine Web-Seite (`www/index.html`), die mit [Capacitor](https://capacitorjs.com) in eine Android-App verpackt wird.

## APK über GitHub bauen (nur mit dem Handy)

1. Neues Repository auf GitHub anlegen.
2. Diese ZIP-Datei (`gym-tracker-app.zip`) in den Hauptordner des Repositorys hochladen.
3. Die Datei `android.yml` unter `.github/workflows/android.yml` im Repository anlegen (Inhalt einfügen). Sie gehört nicht in die ZIP.
4. Im Tab **Actions** läuft der Bau von selbst. Danach unter **Artifacts** die Datei `gym-tracker-debug-apk` herunterladen, entpacken und `gym-tracker.apk` installieren.

## Updates ohne Datenverlust

Die Datei `debug.keystore` in der ZIP ist der feste Signaturschlüssel. Dadurch signiert jeder Bau die App gleich, und du kannst eine neue APK direkt über die alte installieren. Alle Daten bleiben erhalten. Lösche die Datei nicht und gib sie nicht weiter.
Wichtig: Wenn du von einer früheren Version ohne diesen Schlüssel wechselst, musst du die alte App einmal deinstallieren. Speichere vorher in den Einstellungen ein Backup und lies es danach wieder ein.

## Hinweise

- Alle Daten liegen lokal auf dem Handy. Über Einstellungen (Regler-Symbol oben rechts) kannst du ein Backup speichern und wieder einlesen.
- Erinnerungen und Barcode-Scan funktionieren nur in der installierten App. Die Produktsuche braucht Internet (Open Food Facts).
- Die Datei `android-res/` enthält das App-Icon. Der Workflow kopiert es in das Android-Projekt.
- Für den Play Store braucht es eine signierte Release-Version und einen eigenen Paketnamen in `capacitor.config.json`.
