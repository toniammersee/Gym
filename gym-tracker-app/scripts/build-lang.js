// Baut www/lang-en.js aus i18n/en.tsv (Deutsch<TAB>Englisch, eine Zeile pro Text).
// Aufruf im Ordner gym-tracker-app:  node scripts/build-lang.js
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..');
const dict = {};
for (const line of fs.readFileSync(path.join(root, 'i18n/en.tsv'), 'utf8').split('\n')) {
  if (!line.trim() || line.startsWith('#')) continue;
  const i = line.indexOf('\t');
  if (i < 0) continue;
  const de = line.slice(0, i), en = line.slice(i + 1);
  if (de && en && de !== en) dict[de] = en;
}
fs.writeFileSync(path.join(root, 'www/lang-en.js'),
  '/* Automatisch erzeugt aus i18n/en.tsv mit scripts/build-lang.js. Nicht von Hand ändern. */\nwindow.GT_EN=' + JSON.stringify(dict) + ';\n');
console.log(Object.keys(dict).length + ' Einträge');
