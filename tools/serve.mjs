// A local server for the site that sends the same headers as Vercel (site/vercel.json), so the tests see the
// content security policy (R-3) and the other headers exactly as the deployed site does. No caching, clean URLs
// (/privacy serves privacy.html), like Vercel's cleanUrls, and gzip. Redirects are left to Vercel (the tests stub them).
//
//     node tools/serve.mjs [port]          (npm start, and the tests' web server)
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const root = join(fileURLToPath(new URL('.', import.meta.url)), '..', 'site');
const port = Number(process.argv[2] || 4173);
const conf = JSON.parse(await readFile(join(root, 'vercel.json'), 'utf8'));
// Vercel's "source" patterns as used in vercel.json: literal text with (.*) groups
const rules = conf.headers.map(h => ({
  re: new RegExp('^' + h.source.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').split('\\(\\.\\*\\)').join('(.*)') + '$'),
  headers: h.headers,
}));
const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon',
  '.mp3': 'audio/mpeg', '.ogg': 'audio/ogg', '.wav': 'audio/wav', '.m4a': 'audio/mp4', '.txt': 'text/plain; charset=utf-8',
  '.woff2': 'font/woff2', '.apk': 'application/vnd.android.package-archive',
};

async function file(p) { try { const s = await stat(p); return s.isFile() ? p : s.isDirectory() ? file(join(p, 'index.html')) : null; } catch { return null; } }

createServer(async (req, res) => {
  const path = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const local = normalize(join(root, path));
  if (local !== root && !local.startsWith(root + sep)) { res.writeHead(403).end(); return; }
  const found = await file(local) || (extname(local) ? null : await file(local + '.html'));
  const headers = { 'Cache-Control': 'no-store' };
  for (const r of rules) if (r.re.test(path)) for (const h of r.headers) headers[h.key] = h.value;
  if (!found) { res.writeHead(404, { ...headers, 'Content-Type': 'text/plain; charset=utf-8' }).end('Not found'); return; }
  headers['Content-Type'] ??= TYPES[extname(found).toLowerCase()] || 'application/octet-stream';
  headers['Cache-Control'] = 'no-store';
  let body = await readFile(found);
  // compressed like on Vercel, so timings measured here are honest (text only; pictures and sound are compressed already)
  if (/gzip/.test(req.headers['accept-encoding'] || '') && /text|json|javascript|svg/.test(headers['Content-Type'])) {
    body = gzipSync(body); headers['Content-Encoding'] = 'gzip'; headers['Vary'] = 'Accept-Encoding';
  }
  res.writeHead(200, headers).end(req.method === 'HEAD' ? undefined : body);
}).listen(port, '127.0.0.1', () => console.log(`site on http://127.0.0.1:${port}`));
