# Gym Tracker

Gym-Tracker fürs Handy: Sätze loggen (mit Supersätzen, Notizen und Pausenzeit je Übung), Vorlagen, Wochenplan mit Erinnerung, Trainingsvergleich, Kalender, Fortschritt, Körpermaße, Erholung, Essen mit Makros, Wasser, Supplements, Lebensmittel-Datenbank, Barcode-Scan, Rechner, App-Icon und Daten-Export, eigene Übungen, Gewichtsvorschlag, Ziele, Abzeichen, Fortschrittsfotos und Farbwahl.

Neu in 1.8.0:

- **Rückgängig**: Nach dem Löschen (Satz, Übung, Training, Mahlzeit, Vorlage und mehr) erscheint unten ein Knopf „Rückgängig“.
- **Pausen-Timer im Sperrbildschirm**: Die laufende Pause erscheint als Benachrichtigung mit Countdown. Abschaltbar in den Einstellungen.
- **Health Connect**: Schritte, Gewicht, Schlaf und Ruhepuls kommen automatisch. Fertige Trainings landen auf Wunsch als Krafttraining in Health Connect.
- **Backups**: Zusätzlich zum Auto-Backup hebt die App die Stände der letzten 14 Tage auf (Dokumente/GymTracker/Verlauf). Ein Hinweis erinnert daran, ab und zu ein Backup in Google Drive zu sichern.
- **Englisch**: In den Einstellungen unter „Sprache“ umschaltbar. Auf Handys mit anderer Sprache als Deutsch startet die App auf Englisch.

## Aufbau

- `www/index.html`: die ganze App (HTML, CSS, JavaScript)
- `www/lang-en.js`: englische Texte, erzeugt aus `i18n/en.tsv` mit `node scripts/build-lang.js`
- `plugins/gym-native/`: eigenes Android-Plugin (Pausen-Timer, Health Connect)
- `android-res/`: App-Icon
- `scripts/prepare-android.sh`: erzeugt das Android-Projekt, übernimmt Icon und Versionsnummer

Die App wird mit [Capacitor](https://capacitorjs.com) in eine Android-App verpackt. Sie braucht mindestens Android 8.

## APK bauen

Das macht GitHub Actions bei jedem Push von selbst (Workflow `.github/workflows/android.yml`):

- Auf `main` entsteht für jede neue Versionsnummer in `package.json` ein **Release** mit der APK. Du findest es auf der Startseite des Repositorys rechts unter **Releases**.
- Auf anderen Branches gibt es die APK unter **Actions** → Lauf → **Artifacts**.

Lokal: `npm ci`, dann `npm run android`, dann im Ordner `android` `./gradlew assembleDebug`.

## Signaturschlüssel (wichtig für Updates ohne Datenverlust)

Android installiert eine neue APK nur über die alte, wenn beide mit demselben Schlüssel signiert sind. Der Schlüssel liegt deshalb **nicht im Repository**, sondern als Secret:

1. Auf GitHub im Repository **Settings → Secrets and variables → Actions → New repository secret**.
2. Name: `ANDROID_KEYSTORE_BASE64`, Wert: der Base64-Text des Schlüssels.
3. Speichern. Ab jetzt signiert jeder Bau gleich.

Ohne dieses Secret bricht der Bau auf `main` ab, damit keine APK mit fremdem Schlüssel entsteht. Gib den Schlüssel nicht weiter und bewahre eine Kopie sicher auf.

## Hinweise

- Alle Daten liegen lokal auf dem Handy. Über Einstellungen (Regler-Symbol oben rechts) kannst du ein Backup speichern und wieder einlesen.
- Erinnerungen, Barcode-Scan, Health Connect und der Timer im Sperrbildschirm funktionieren nur in der installierten App. Die Produktsuche braucht Internet (Open Food Facts).
- Health Connect ist ab Android 14 eingebaut. Auf älteren Handys bietet die App an, es aus dem Play Store zu installieren.
- Für den Play Store braucht es eine signierte Release-Version und einen eigenen Paketnamen in `capacitor.config.json`.
