// Runs once in Vercel's build (buildCommand in vercel.json), then removes itself so it is not served.
// Writes the short commit id into data-version on the pages that load the measurement (js/telemetry.js reads it
// as app_version); everywhere else, such as a local `npm start`, the version is "web".
const fs = require('fs'), path = require('path');
const v = (process.env.VERCEL_GIT_COMMIT_SHA || 'dev').slice(0, 7);
const games = fs.readdirSync('games', { withFileTypes: true })
  .filter(d => d.isDirectory() && fs.existsSync(path.join('games', d.name, 'index.html')))
  .map(d => path.join('games', d.name, 'index.html'));
const pages = ['index.html'].concat(games);
for (const f of pages) {
  const s = fs.readFileSync(f, 'utf8');
  const t = s.replace(/<html([^>]*)>/, (m, a) => '<html' + a.replace(/ data-version="[^"]*"/, '') + ' data-version="' + v + '">');
  if (t !== s) fs.writeFileSync(f, t);
}
console.log('stamped', pages.length, 'pages with', v);
fs.unlinkSync(__filename);
