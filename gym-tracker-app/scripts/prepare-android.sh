#!/usr/bin/env bash
# Erzeugt das Android-Projekt und passt es an (Icon, Mindest-Android-Version).
# Aufruf aus dem Ordner gym-tracker-app:  bash scripts/prepare-android.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[ -d android ] || npx cap add android

# App-Icon übernehmen
if [ -d android-res ]; then
  cp -r android-res/. android/app/src/main/res/
fi

# Health Connect braucht mindestens Android 8 (API 26)
sed -i -E 's/minSdkVersion = [0-9]+/minSdkVersion = 26/' android/variables.gradle

# Versionsnummer aus package.json in die App schreiben
VERSION=$(node -p "require('./package.json').version")
CODE=$(node -p "const [a,b,c]=require('./package.json').version.split('.').map(Number);a*10000+b*100+c")
sed -i -E "s/versionCode [0-9]+/versionCode $CODE/; s/versionName \"[^\"]*\"/versionName \"$VERSION\"/" android/app/build.gradle

npx cap sync android
