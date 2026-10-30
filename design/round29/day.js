/* Round 29: a made-up but plausible ski day (decision 76), built inside the real site from its own data: every lift and
   run line is the real line in site/data/runs-and-lifts.json, every height comes from the site's terrain model
   (site/data/terrain.json, GudRelief), with the site's projection. The times, speeds and pauses are invented and labelled
   as a sample on every board. maps.mjs injects this file into the site, calls R29.day() and R29.draw(...), and saves the
   day as shots/day.json for the boards. Nothing here goes into the site or the apps. */
(function () {
'use strict';
const lat0 = 42.51, lon0 = 44.495, kx = 111320 * Math.cos(lat0 * Math.PI / 180), ky = 111320;
const P = ([la, lo]) => [(lo - lon0) * kx, -(la - lat0) * ky];
const $ = (s, r) => (r || document).querySelector(s);
const NS = 'http://www.w3.org/2000/svg';
let M = null, D = null, DAY = null;

async function data() {
  if (M) return;
  const [t, d] = await Promise.all([fetch('data/terrain.json').then(r => r.json()), fetch('data/runs-and-lifts.json').then(r => r.json())]);
  M = GudRelief.load(t); D = d;
}
const lift = n => D.lifts.find(l => l.name === n);
const piste = k => D.pistes.find(p => p.key === k);
const ptsOf = g => g.map(P);
const lenOf = a => a.reduce((s, p, i) => i ? s + Math.hypot(p[0] - a[i - 1][0], p[1] - a[i - 1][1]) : 0, 0);
const near = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);
// the lines of a piste as one downhill line (its segs are joined end to end in the order that keeps going down)
function runLine(key) {
  const p = piste(key);
  let segs = (p.segs || []).map(s => ptsOf(s.g)).filter(s => s.length > 1);
  if (!segs.length) segs = [ptsOf(p.g || [])];
  segs = segs.map(s => M.elev(...s[0]) < M.elev(...s[s.length - 1]) ? s.slice().reverse() : s);
  // greedy chain from the highest start
  segs.sort((a, b) => M.elev(...b[0]) - M.elev(...a[0]));
  const out = segs.shift().slice();
  while (segs.length) {
    const end = out[out.length - 1];
    let bi = 0, bd = 1e9;
    segs.forEach((s, i) => { const d = near(end, s[0]); if (d < bd) { bd = d; bi = i; } });
    if (bd > 400) break;
    out.push(...segs.splice(bi, 1)[0]);
  }
  return out;
}
// a part of a line, from fraction a to b of its length
function part(line, a, b) {
  const L = lenOf(line), A = a * L, B = b * L, out = []; let acc = 0;
  for (let i = 0; i < line.length; i++) {
    if (i) acc += near(line[i], line[i - 1]);
    if (acc >= A && acc <= B) out.push(line[i]);
  }
  return out.length > 1 ? out : line;
}
// resample every ~step metres
function resample(line, step) {
  const out = [line[0]]; let carry = 0;
  for (let i = 1; i < line.length; i++) {
    const [x0, y0] = line[i - 1], [x1, y1] = line[i], d = near(line[i - 1], line[i]);
    let t = step - carry;
    while (t <= d) { out.push([x0 + (x1 - x0) * t / d, y0 + (y1 - y0) * t / d]); t += step; }
    carry = d - (t - step);
  }
  out.push(line[line.length - 1]);
  return out;
}
// a seeded random, so the day is the same every time
let seed = 29;
const rnd = () => { seed = (seed * 16807) % 2147483647; return (seed - 1) / 2147483646; };

/* The day: 11 rides up, 10 runs down, a coffee and a lunch, one stretch off the runs and one climb on foot.
   [type, name, extra]. Runs can be a chain of pistes (a switch from one run to another in the middle). */
const PLAN = [
  ['lift', 'Goodaura', { at: '09:12', wait: 3 }],
  ['run', ['Goodaura 1'], {}],
  ['lift', 'Goodaura', { wait: 6 }],
  ['run', ['Tatra 1'], {}],
  ['lift', 'Shino', { wait: 2 }],
  ['run', ['Tatra 1', 'Tatra 2'], { switchAt: 0.22 }],
  ['lift', 'Shino', { wait: 1 }],
  ['pause', 'coffee', { min: 24, label: 'הפסקה' }],
  ['run', ['Goodaura 1', 'Goodaura 2'], { switchAt: 0.30 }],
  ['lift', 'New Goodaura', { wait: 4 }],
  ['run', ['u1217683225'], { connector: true }],
  ['lift', 'Kikilo', { wait: 2 }],
  ['run', ['Kikilo 1'], {}],
  ['lift', 'Kikilo', { wait: 1 }],
  ['run', ['Kikilo 2'], { offpiste: [0.35, 0.62] }],
  ['climb', 'walk', { rise: 38, min: 9 }],
  ['lift', 'Kikilo', { wait: 3 }],
  ['pause', 'lunch', { min: 56, label: 'ארוחת צהריים' }],
  ['lift', 'Kudebi', { wait: 7 }],
  ['run', ['Kudebi 1'], { fast: true }],
  ['lift', 'Kudebi', { wait: 5 }],
  ['run', ['Kudebi 2'], {}],
  ['lift', 'Khada', { wait: 2 }],
  ['run', ['Khada'], {}],
  ['lift', 'Khada', { wait: 1 }],
  ['run', ['Goodaura 1'], { last: true, from: 0.18 }],
];
const LIFT_V = { gondola: 5.0, chair_lift: 2.4, platter: 2.8 };

async function day() {
  await data();
  if (DAY) return DAY;
  seed = 29;
  let t = 9 * 3600 + 12 * 60;
  const track = [], segs = [];
  const push = (x, y, z, v, kind, idx) => track.push({ t: Math.round(t), x: +x.toFixed(1), y: +y.toFixed(1), z: Math.round(z), v: +v.toFixed(1), k: kind, s: idx });
  let runNo = 0;
  for (const [type, name, o] of PLAN) {
    const idx = segs.length;
    if (type === 'lift') {
      t += (o.wait || 0) * 60 * 1.8;
      const L = lift(name), line = ptsOf(L.g), up = M.elev(...line[0]) < M.elev(...line[line.length - 1]) ? line : line.slice().reverse();
      const r = resample(up, 12), v = LIFT_V[L.kind] || 2.4, t0 = t;
      r.forEach((p, i) => { if (i) t += near(p, r[i - 1]) / v; push(p[0], p[1], M.elev(...p), v * 3.6, 'lift', idx); });
      const z0 = M.elev(...up[0]), z1 = M.elev(...up[up.length - 1]);
      segs.push({ type, name, kind: L.kind, t0, t1: Math.round(t), wait: Math.round((o.wait || 0) * 1.8), rise: Math.round(z1 - z0), z0: Math.round(z0), z1: Math.round(z1), len: Math.round(lenOf(up)) });
    } else if (type === 'pause') {
      const t0 = t; t += o.min * 60;
      const last = track[track.length - 1]; push(last.x, last.y, last.z, 0, 'stop', idx);
      segs.push({ type, name, label: o.label, t0, t1: Math.round(t) });
    } else if (type === 'climb') {
      const last = track[track.length - 1], t0 = t;
      for (let i = 1; i <= 8; i++) { t += o.min * 60 / 8; push(last.x - i * 4, last.y - i * 6, last.z + o.rise * i / 8, 1.0 + rnd() * 0.4, 'climb', idx); }
      segs.push({ type, name, t0, t1: Math.round(t), rise: o.rise });
    } else {
      // a run: the pistes in order, the first one cut where the switch happens
      let line = [];
      name.forEach((k, j) => {
        let l = runLine(k);
        if (j === 0 && o.from) l = part(l, o.from, 1);
        if (name.length > 1 && j === 0) l = part(l, 0, o.switchAt ? o.switchAt + 0.05 : 1);
        if (j > 0) { const end = line[line.length - 1]; let bi = 0, bd = 1e9; l.forEach((p, i) => { const d = near(end, p); if (d < bd) { bd = d; bi = i; } }); l = l.slice(bi); }
        line.push(...l);
      });
      // start where the last ride ended (a short slide over to the run)
      const last = track[track.length - 1];
      if (last) line.unshift([last.x, last.y]);
      const r = resample(line, 10), t0 = t, startIdx = track.length;
      let vmax = 0, vmaxAt = null, prevV = 4, desc = 0, steep = 0;
      const pistesOn = [];
      for (let i = 0; i < r.length; i++) {
        const p = r[i], z = M.elev(...p);
        let v = prevV;
        if (i) {
          const d = near(p, r[i - 1]), dz = M.elev(...r[i - 1]) - z, sl = Math.atan2(Math.max(dz, 0), d) * 180 / Math.PI;
          steep = Math.max(steep, sl);
          // speed follows the slope, smoothed, with a little noise; a few stops on the way
          let kmh = (o.fast ? 26 : 19) + sl * (o.fast ? 1.7 : 1.25) + (rnd() - 0.5) * 6;
          if (rnd() < 0.012) kmh = 5;
          kmh = Math.max(8, Math.min(o.fast ? 71 : 57, kmh));
          v = prevV + (kmh / 3.6 - prevV) * 0.22;
          t += d / Math.max(v, 1.2);
          if (dz > 0) desc += dz;
        }
        prevV = v;
        let kind = 'run';
        const f = i / (r.length - 1);
        if (o.offpiste && f > o.offpiste[0] && f < o.offpiste[1]) kind = 'off';
        push(p[0], p[1], z, v * 3.6, kind, idx);
        if (v * 3.6 > vmax && kind === 'run') { vmax = v * 3.6; vmaxAt = { x: p[0], y: p[1], t: Math.round(t) }; }
      }
      const pts = track.slice(startIdx), moving = pts.filter(q => q.v > 5);
      const avg = moving.reduce((s, q) => s + q.v, 0) / Math.max(moving.length, 1);
      runNo++;
      segs.push({ type: 'run', pistes: name.filter(k => !/^u\d/.test(k)), connector: !!o.connector, no: o.connector ? null : runNo, offpiste: !!o.offpiste,
        t0, t1: Math.round(t), vmax: Math.round(vmax), vmaxAt, avg: Math.round(avg), desc: Math.round(desc), len: Math.round(lenOf(r)),
        steep: Math.round(steep), colors: name.map(k => piste(k).color) });
      if (o.connector) runNo--;
    }
  }
  const runs = segs.filter(s => s.type === 'run' && !s.connector);
  const fastest = runs.reduce((a, b) => b.vmax > a.vmax ? b : a);
  const longest = runs.reduce((a, b) => b.len > a.len ? b : a);
  DAY = {
    note: 'Sample day for round 29: real lift and run lines and real heights (site/data), invented times and speeds.',
    date: '2027-01-12', track, segs,
    stats: {
      start: track[0].t, end: track[track.length - 1].t,
      runs: runs.length, lifts: segs.filter(s => s.type === 'lift').length,
      vmax: fastest.vmax, vmaxRun: fastest.pistes.join(' → '),
      skiKm: +(runs.reduce((s, r) => s + r.len, 0) / 1000).toFixed(1),
      desc: runs.reduce((s, r) => s + r.desc, 0),
      liftUp: segs.filter(s => s.type === 'lift').reduce((s, l) => s + l.rise, 0),
      longest: { pistes: longest.pistes, len: longest.len },
      skiMin: Math.round(runs.reduce((s, r) => s + r.t1 - r.t0, 0) / 60),
      liftMin: Math.round(segs.filter(s => s.type === 'lift').reduce((s, l) => s + l.t1 - l.t0, 0) / 60),
    },
  };
  return DAY;
}

// speed to colour: a sequential ramp of its own (not the run colours, not the slope colours), slow light to fast deep
const RAMP = [[0, [255, 214, 120]], [20, [245, 150, 50]], [40, [220, 50, 70]], [60, [120, 30, 150]]];
function speedColor(v) {
  for (let i = 1; i < RAMP.length; i++) if (v <= RAMP[i][0]) {
    const [a, ca] = RAMP[i - 1], [b, cb] = RAMP[i], f = (v - a) / (b - a);
    return `rgb(${ca.map((c, j) => Math.round(c + (cb[j] - c) * f)).join(',')})`;
  }
  return `rgb(${RAMP[RAMP.length - 1][1].join(',')})`;
}

function frame(x0, y0, x1, y1, pad = 0.12) {
  const svg = $('#map'); svg.setAttribute('preserveAspectRatio', 'xMidYMid meet'); const w = x1 - x0, h = y1 - y0, r = svg.getBoundingClientRect(), ar = r.width / r.height;
  let W = w * (1 + 2 * pad), H = h * (1 + 2 * pad);
  if (W / H > ar) H = W / ar; else W = H * ar;
  svg.setAttribute('viewBox', `${(x0 + x1) / 2 - W / 2} ${(y0 + y1) / 2 - H / 2} ${W} ${H}`);
}
async function topView() {
  const b = $('#viewsw [data-view="2d"]');
  if (b && b.getAttribute('aria-pressed') !== 'true') { b.click(); await new Promise(r => setTimeout(r, 900)); }
}

/* Draw on the site's own top-view map. o: { upto: seconds (live: the trail so far and the dot), mode: 'live'|'speed'|'replay',
   box: [x0,y0,x1,y1] to frame, dim: fade the runs, scale: stroke width in px } */
async function draw(o = {}) {
  await day(); await topView();
  const svg = $('#map');
  svg.querySelectorAll('.r29').forEach(e => e.remove());
  const box = o.box || (() => { const xs = DAY.track.map(p => p.x), ys = DAY.track.map(p => p.y); return [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)]; })();
  frame(...box, o.pad ?? 0.1);
  await new Promise(r => setTimeout(r, 300));
  const vb = svg.viewBox.baseVal, px = vb.width / svg.getBoundingClientRect().width; // metres per css px
  const g = document.createElementNS(NS, 'g'); g.setAttribute('class', 'r29');
  if (o.dim !== false) [...svg.children].forEach(c => { const cl = c.getAttribute('class') || ''; if (!/relief|r29/.test(cl)) c.style.opacity = o.dimTo ?? '.42'; });
  const pts = DAY.track.filter(p => o.upto == null || p.t <= o.upto);
  const W = (o.scale || 4) * px;
  const add = (tag, attrs) => { const e = document.createElementNS(NS, tag); for (const k in attrs) e.setAttribute(k, attrs[k]); g.appendChild(e); return e; };
  // a white casing under the whole trail, so it reads over the runs
  add('path', { d: 'M' + pts.map(p => p.x + ' ' + p.y).join('L'), fill: 'none', stroke: o.night ? 'rgba(13,21,34,.85)' : '#fff', 'stroke-width': W * 2.1, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' });
  for (let i = 1; i < pts.length; i++) {
    const a = pts[i - 1], b = pts[i];
    if (a.s !== b.s && b.k === 'stop') continue;
    let col, dash = null, w = W;
    if (b.k === 'lift') { col = o.night ? '#9EB1C9' : '#5A6B82'; dash = `${W * 1.4} ${W * 1.2}`; w = W * 0.7; }
    else if (b.k === 'climb') { col = o.night ? '#9EB1C9' : '#5A6B82'; dash = `${W * 0.5} ${W * 0.9}`; w = W * 0.8; }
    else if (b.k === 'off') { col = o.night ? '#CAD6E5' : '#13233A'; dash = `${W * 1.6} ${W * 1.1}`; w = W * 0.9; }
    else col = o.mode === 'speed' || o.mode === 'replay' ? speedColor(b.v) : (o.night ? '#8DB9FF' : '#1F5FC4');
    const e = add('line', { x1: a.x, y1: a.y, x2: b.x, y2: b.y, stroke: col, 'stroke-width': w, 'stroke-linecap': dash ? 'butt' : 'round' });
    if (dash) e.setAttribute('stroke-dasharray', dash);
  }
  // the fastest point of the day (speed map), and the dot (live, replay)
  if (o.mode === 'speed') {
    const f = DAY.segs.filter(s => s.type === 'run').reduce((a, b) => b.vmax > a.vmax ? b : a);
    add('circle', { cx: f.vmaxAt.x, cy: f.vmaxAt.y, r: W * 2.2, fill: '#fff', stroke: '#13233A', 'stroke-width': W * 0.6 });
    add('path', { d: `M${f.vmaxAt.x} ${f.vmaxAt.y - W * 1.2}l${W * 0.9} ${W * 1.9}h${-W * 1.8}z`, fill: '#13233A' });
  }
  if (o.dot) {
    const p = pts[pts.length - 1];
    add('circle', { cx: p.x, cy: p.y, r: W * 4.2, fill: o.night ? 'rgba(141,185,255,.22)' : 'rgba(31,95,196,.18)' });
    add('circle', { cx: p.x, cy: p.y, r: W * 1.9, fill: o.night ? '#8DB9FF' : '#1F5FC4', stroke: '#fff', 'stroke-width': W * 0.8 });
  }
  svg.appendChild(g);
  return { box, px };
}
// a box around the parts of the day between two times (for framing)
function boxOf(t0, t1, padM = 0) {
  const p = DAY.track.filter(q => q.t >= t0 && q.t <= t1), xs = p.map(q => q.x), ys = p.map(q => q.y);
  return [Math.min(...xs) - padM, Math.min(...ys) - padM, Math.max(...xs) + padM, Math.max(...ys) + padM];
}
window.R29 = { day, draw, boxOf, speedColor, get DAY() { return DAY; } };
})();
