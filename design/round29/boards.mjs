// Round 29, stage 2: the ski-day screens (decision 76, docs/FEATURES.md section i), drawn over real screenshots of both
// apps (Android: the emulator run on main, release android-qa; iPhone: the simulator run, artifact ios-screenshots) and
// over the day's maps from maps.mjs. The day (shots/day.json) is made up: real lift and run lines and heights, invented
// times and speeds; every board says "sample". Each app in its own style (decision 35): Android a full-width bottom sheet,
// straight corners, a persistent notification; iPhone a rounded sheet, Live Activity and Dynamic Island.
// Run from the repo root with the site served (tokens, fonts): node design/round29/boards.mjs <android shots dir> <ios shots dir> [name...]
import fs from 'node:fs';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname), OUT = path.join(HERE, 'shots');
const [ADIR, IDIR] = process.argv.slice(2, 4), want = process.argv.slice(4);
const DAY = JSON.parse(fs.readFileSync(path.join(OUT, 'day.json'), 'utf8'));
const img = f => 'data:image/png;base64,' + fs.readFileSync(f).toString('base64');
const A = f => img(path.join(ADIR, f)), I = f => img(path.join(IDIR, f)), M = f => img(path.join(OUT, f + '.png'));
const AW = 390, IW = 402, AK = AW / 1080, IK = IW / 1206;
// a slice of a screenshot (source pixels), at phone width
const slice = (src, k, w, y0, y1) => `<div style="height:${Math.round((y1 - y0) * k)}px;overflow:hidden"><img src="${src}" style="display:block;width:${w}px;margin-top:${-Math.round(y0 * k)}px"></div>`;
const aS = (f, y0, y1) => slice(A(f), AK, AW, y0, y1), iS = (f, y0, y1) => slice(I(f), IK, IW, y0, y1);
const hm = t => `${Math.floor(t / 3600)}:${String(Math.floor(t % 3600 / 60)).padStart(2, '0')}`;
const dur = (s, en) => { const m = Math.round(s / 60); return en ? (m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`) : (m >= 60 ? `${Math.floor(m / 60)} ש׳ ${m % 60} דק׳` : `${m} דק׳`); };
const n = (x, en) => x.toLocaleString(en ? 'en-US' : 'he-IL');
const runs = DAY.segs.filter(s => s.type === 'run' && !s.connector);
const S = DAY.stats;

// ---- icons (drawn, not letters: the design audit asked for drawn symbols) ----
const ic = {
  lift: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 5l20-3"/><path d="M12 3.5V8"/><rect x="6" y="8" width="12" height="9" rx="1.5"/><path d="M6 12.5h12"/><path d="M9 21h6"/></svg>`,
  ski: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="15" cy="4.5" r="2"/><path d="M5 9l5 2 3-2 2 4-3 3"/><path d="M3 20l18-6"/></svg>`,
  pause: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 10h11v4a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z"/><path d="M16 11h1.5a2.5 2.5 0 0 1 0 5H16"/><path d="M8 3v3M12 3v3"/></svg>`,
  walk: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="13" cy="4" r="2"/><path d="M13 7l-2 6 3 3 1 5M11 13l-3 2-1 5M12 9l4 2"/></svg>`,
  stop: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="6" width="12" height="12" rx="1.5" fill="currentColor"/></svg>`,
  rec: (s = 14) => `<svg width="${s}" height="${s}" viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="6" fill="var(--rec)"/></svg>`,
  pocket: (s = 22) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 4h16v8a8 8 0 0 1-16 0z"/><path d="M8 4v5h8V4"/></svg>`,
  phone: (s = 22) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="6" y="2.5" width="12" height="19" rx="1.5"/><path d="M10.5 18.5h3"/><path d="M9.5 9.5l2 2 3.5-3.5"/></svg>`,
  batt: (s = 22) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2.5" y="7" width="17" height="10" rx="1.5"/><path d="M22 10.5v3"/><path d="M6 10v4M9.5 10v4M13 10v4"/></svg>`,
  share: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 15V3M7 8l5-5 5 5"/><path d="M5 12v8h14v-8"/></svg>`,
  play: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5v15l12-7.5z" fill="currentColor"/></svg>`,
  trash: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>`,
  file: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 2.5h8l4 4v15H6z"/><path d="M14 2.5v4h4"/><path d="M12 11v7M9 15l3 3 3-3"/></svg>`,
  sat: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/></svg>`,
  mtn: (s = 18) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 20l7-12 4 6 3-4 6 10z"/></svg>`,
  peak: (s = 14) => `<svg width="${s}" height="${s}" viewBox="0 0 16 16" aria-hidden="true"><path d="M8 2l6 12H2z" fill="currentColor"/></svg>`,
  dots: (s = 20) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>`,
  chev: (s = 16) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 5l-7 7 7 7"/></svg>`,
  back: (s = 20) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6"/></svg>`,
};

// ---- words ----
const W = {
  he: { start: 'מתחילים יום', rec: 'מקליטים', kmh: 'קמ״ש', km: 'ק״מ', m: 'מ׳', max: 'מרבית', dist: 'גלישה', desc: 'ירידה', runs: 'ירידות',
    onLift: 'על הרכבל', on: 'ב-', stop: 'סיום', hold: 'לחיצה ארוכה לסיום', now: 'עכשיו', sample: 'יום לדוגמה', day: 'יום סקי 2 · שלישי 12.1' },
  en: { start: 'Start day', rec: 'Recording', kmh: 'km/h', km: 'km', m: 'm', max: 'max', dist: 'skied', desc: 'descent', runs: 'runs',
    onLift: 'On the lift', on: 'On ', stop: 'Finish', hold: 'Hold to finish', now: 'now', sample: 'Sample day', day: 'Ski day 2 · Tue 12 Jan' },
};
const sign = (color, label, cls = '') => `<span class="sg ${cls}" style="--sg:var(--p-${color})"><b dir="ltr">${label}</b></span>`;
const runSign = (keys, en) => keys.map((k, i) => sign(runs.concat(DAY.segs).find(s => s.pistes && s.pistes.includes(k))?.colors?.[runs.concat(DAY.segs).find(s => s.pistes && s.pistes.includes(k)).pistes.indexOf(k)] || 'blue', k)).join(`<i class="arr">${en ? '→' : '←'}</i>`);

// ---- the CSS for every board (site tokens; the apps use the same tokens, checked against site.css by their tests) ----
const CSS = `
:root{--rec:#E5322D} :root[data-theme="dark"]{--rec:#FF5A52}
html,body{height:auto;margin:0;background:var(--snow)}
.qph{position:relative;overflow:hidden;background:var(--snow);color:var(--ink);font-family:var(--f-body)}
.qph.and{width:${AW}px} .qph.ios{width:${IW}px}
.sb{position:absolute;top:0;left:0;right:0;height:26px;z-index:50;display:flex;align-items:center;justify-content:space-between;padding:0 18px;font:600 13px/1 Roboto,system-ui,sans-serif;color:var(--ink);direction:ltr}
.ios .sb{height:54px;padding:0 30px;font:600 16px/1 -apple-system,system-ui,sans-serif}
.ios .sb .isl{position:absolute;left:50%;top:11px;width:124px;height:36px;margin-left:-62px;border-radius:20px;background:#000}
.sb .ics{display:flex;gap:6px;align-items:center} .sb .bt{width:22px;height:11px;border:1.5px solid currentColor;border-radius:3px;position:relative}
.sb .bt:after{content:"";position:absolute;inset:1.5px;right:5px;background:currentColor;border-radius:1px}
.sb .rd{display:inline-block;width:8px;height:8px;border-radius:50%;background:var(--rec);margin-inline-end:4px}
.sg{display:inline-flex;align-items:center;height:26px;padding:0 10px 0 16px;background:var(--sg);color:var(--on-board);clip-path:polygon(9px 0,100% 0,100% 100%,9px 100%,0 50%);font-family:var(--f-display);font-size:21px;line-height:1;white-space:nowrap}
.sg b{font-weight:700} .arr{font-style:normal;color:var(--muted);margin:0 4px;font-size:14px}
.k{font-family:var(--f-display);font-weight:700;line-height:.9;letter-spacing:.3px}
.sheet{position:absolute;left:0;right:0;bottom:0;z-index:20;background:var(--paper);border-top:1px solid var(--rule)}
.ios .sheet{left:8px;right:8px;bottom:8px;border:0;border-radius:22px;box-shadow:0 4px 24px var(--shadow)}
.grab{width:40px;height:4px;margin:8px auto 4px;background:var(--rule);border-radius:2px}
.and .grab{border-radius:0}
.qb{display:inline-flex;align-items:center;justify-content:center;gap:8px;height:48px;padding:0 18px;border:1.5px solid var(--ink);background:var(--paper);color:var(--ink);font:700 16px var(--f-body);white-space:nowrap}
.qb.pri{background:var(--ink);color:var(--paper);border-color:var(--ink)}
.qb.rec{background:var(--ink);color:var(--paper)} .qb.rec svg{flex:none}
.ios .qb{border-radius:14px} .ios .qb.pri,.ios .qb.rec{border-radius:14px}
.muted{color:var(--muted)} .sm{font-size:13px} .row{display:flex;align-items:center;gap:10px}
.qtag{display:inline-flex;align-items:center;gap:5px;height:22px;padding:0 7px;font-size:12px;font-weight:600;background:var(--snow);color:var(--muted)}
.ios .qtag{border-radius:6px}
.live{padding:4px 18px 14px}
.live .top{display:flex;align-items:flex-end;justify-content:space-between;gap:12px}
.live .v{display:flex;align-items:baseline;gap:6px} .live .v .k{font-size:88px} .live .v span{font-size:17px;font-weight:600;color:var(--muted)}
.live .qwhere{display:flex;align-items:center;gap:8px;margin:2px 0 10px;font-size:15px;font-weight:600;min-height:30px}
.live .qwhere .ico{display:grid;place-items:center;width:30px;height:30px;background:var(--snow);color:var(--ink)}
.ios .live .qwhere .ico{border-radius:8px}
.st4{display:grid;grid-template-columns:repeat(4,1fr);border-top:1px solid var(--rule);padding-top:10px;margin-bottom:12px}
.st4>div{display:flex;flex-direction:column;gap:2px;padding:0 6px;border-inline-start:1px solid var(--rule)} .st4>div:first-child{border:0;padding-inline-start:0}
.st4 b{font-family:var(--f-display);font-size:30px;line-height:1} .st4 small{font-size:12px;color:var(--muted)}
.rdot{display:inline-flex;align-items:center;gap:6px;font-size:13px;font-weight:700;color:var(--rec)} .rdot:before{content:"";width:9px;height:9px;border-radius:50%;background:var(--rec);box-shadow:0 0 0 4px color-mix(in srgb,var(--rec) 22%,transparent)}
.mapbtn{position:absolute;z-index:6;display:flex;align-items:center;gap:8px;height:44px;padding:0 14px;border:1.5px solid var(--ink);background:var(--paper);color:var(--ink);font:700 15px var(--f-body)}
.ios .mapbtn{border-radius:12px}
.qtop{position:absolute;top:26px;left:0;right:0;z-index:8;display:flex;justify-content:space-between;align-items:center;padding:10px 12px}
.seg{display:flex} .seg span{height:40px;padding:0 14px;display:grid;place-items:center;background:var(--paper);color:var(--ink);font-weight:700;font-size:15px;border:1px solid var(--rule)} .seg span.on{background:var(--ink);color:var(--paper);border-color:var(--ink)}
.homebtn{display:flex;align-items:center;gap:6px;height:40px;padding:0 12px;background:var(--paper);border:1px solid var(--rule);color:var(--glacier);font-weight:700;font-size:15px}
.ititle{position:absolute;top:54px;left:0;right:0;z-index:8;height:58px;display:flex;align-items:center;justify-content:center;font-family:var(--f-display);font-size:32px;font-weight:700}
.ititle .x{position:absolute;inset-inline-end:16px;width:40px;height:40px;border-radius:50%;background:var(--paper);display:grid;place-items:center;box-shadow:0 1px 6px var(--shadow)}
.legend{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--muted)} .legend i{display:block;width:120px;height:8px;background:linear-gradient(90deg,rgb(255,214,120),rgb(245,150,50),rgb(220,50,70),rgb(120,30,150))}
:root[dir="ltr"] .legend i{transform:none} :root[dir="rtl"] .legend i{transform:scaleX(-1)}
.big3{display:grid;grid-template-columns:repeat(3,1fr);gap:0;margin:8px 0 2px}
.big3>div{display:flex;flex-direction:column;gap:3px;padding:0 10px;border-inline-start:1px solid var(--rule)} .big3>div:first-child{border:0;padding-inline-start:0}
.big3 b{font-family:var(--f-display);font-size:46px;line-height:.9} .big3 small{font-size:13px;color:var(--muted)}
.tl{list-style:none;margin:0;padding:0}
.tl li{display:grid;grid-template-columns:44px 30px 1fr auto;align-items:center;gap:8px;min-height:50px;border-bottom:1px solid var(--rule)}
.tl .tm{font-size:13px;font-weight:600;color:var(--muted);direction:ltr;text-align:start}
.tl .ico{width:30px;height:30px;display:grid;place-items:center;color:var(--muted)}
.tl .what{display:flex;flex-direction:column;gap:3px;min-width:0} .tl .what .l2{font-size:12.5px;color:var(--muted)}
.tl .num{text-align:end;display:flex;flex-direction:column;align-items:flex-end;gap:1px} .tl .num b{font-family:var(--f-display);font-size:26px;line-height:1} .tl .num small{font-size:11.5px;color:var(--muted)}
.tl li.lift{min-height:40px} .tl li.lift .what{font-size:14px;color:var(--muted)} .tl li.pause{min-height:36px;background:repeating-linear-gradient(-45deg,transparent 0 6px,color-mix(in srgb,var(--rule) 40%,transparent) 6px 7px)}
.tl li.pause .what{font-size:13.5px;color:var(--muted)}
.card{background:var(--paper);border:1px solid var(--rule);padding:14px 16px}
.ios .card{border-radius:16px;border:0;box-shadow:0 1px 3px var(--shadow)}
.pb{position:relative;height:6px;background:var(--rule)} .pb i{position:absolute;inset-block:0;inset-inline-start:0;background:var(--ink)}
.scrub{position:relative;height:44px} .scrub .tr{position:absolute;inset-inline:0;top:20px;height:4px;background:var(--rule)}
.scrub .seg{position:absolute;top:18px;height:8px}
.scrub .kn{position:absolute;top:10px;width:24px;height:24px;margin-inline-start:-12px;border-radius:50%;background:var(--paper);border:3px solid var(--ink);box-shadow:0 1px 4px var(--shadow)}
.pin{position:absolute;z-index:7;transform:translate(-50%,-50%)}
.pin .h{position:absolute;left:50%;top:50%;width:46px;height:46px;margin:-23px 0 0 -23px;border-radius:50%;background:rgba(31,95,196,.2)}
.pin .d{position:relative;width:18px;height:18px;border-radius:50%;background:var(--glacier);border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.35)}
.bubble{position:absolute;z-index:7;background:var(--ink);color:var(--paper);padding:6px 10px;font-weight:700;font-size:14px;white-space:nowrap}
.ios .bubble{border-radius:10px}
.note{font-size:12px;color:var(--muted);line-height:1.45}
.ban{display:flex;align-items:center;gap:10px;padding:10px 12px;background:var(--paper);border:1px solid var(--rule);font-size:14px;font-weight:600}
.ios .ban{border-radius:14px;border:0;box-shadow:0 2px 10px var(--shadow)}
.ban.warn{border-inline-start:4px solid var(--p-route)} .ban.off{border-inline-start:4px solid var(--dash)}
.perm li{display:grid;grid-template-columns:40px 1fr;gap:12px;align-items:center;padding:10px 0;font-size:15px} .perm li span.i{width:40px;height:40px;display:grid;place-items:center;background:var(--snow)}
.ios .perm li span.i{border-radius:10px}
.lock{position:relative;background:linear-gradient(#24324a,#0e1626 60%,#0a101c);color:#fff;font-family:system-ui,sans-serif}
`;

// a status bar of our own (the app screenshots were taken at 9:30 and 3:16; the boards keep the hour of the story)
const sbar = (p, t, rec, dark) => p === 'and'
  ? `<div class="sb" style="${dark ? 'color:#fff;' : ''}background:${dark ? 'transparent' : 'transparent'}"><span>${rec ? '<span class="rd"></span>' : ''}${t}</span><span class="ics"><svg width="14" height="12" viewBox="0 0 14 12"><path d="M7 11L0 2a11 11 0 0 1 14 0z" fill="currentColor"/></svg><span class="bt"></span></span></div>`
  : `<div class="sb" style="${dark ? 'color:#fff;' : ''}"><span>${t}</span><span class="isl"></span><span class="ics"><svg width="17" height="12" viewBox="0 0 17 12"><g fill="currentColor"><rect x="0" y="8" width="3" height="4" rx="1"/><rect x="4.5" y="5.5" width="3" height="6.5" rx="1"/><rect x="9" y="3" width="3" height="9" rx="1"/><rect x="13.5" y="0" width="3" height="12" rx="1"/></g></svg><span class="bt"></span></span></div>`;
// covers the screenshot's own status bar strip
const cover = (p, dark) => `<div style="position:absolute;top:0;left:0;right:0;height:${p === 'and' ? 26 : 54}px;z-index:49;background:${dark ? 'transparent' : 'transparent'}"></div>`;

// the live panel (the same numbers on both apps; the frame follows each app)
function livePanel(p, en, o = {}) {
  const w = W[en ? 'en' : 'he'];
  const where = o.where ?? (o.lift
    ? `<span class="ico">${ic.lift(18)}</span><span>${w.onLift} <b dir="ltr">${o.lift}</b></span>`
    : `<span class="ico">${ic.ski(18)}</span>${sign('blue', 'Tatra 2')}<span class="muted" style="font-weight:400;font-size:13px">${en ? 'probably' : 'כנראה'}</span>`);
  return `<div class="sheet"><div class="grab"></div><div class="live">
    <div class="top"><div class="v"><b class="k">${o.v ?? 41}</b><span>${w.kmh}</span></div>
      <div style="display:flex;flex-direction:column;align-items:flex-end;gap:8px;padding-bottom:12px"><span class="rdot">${w.rec} · <span dir="ltr">${o.clock ?? '1:01'}</span></span></div></div>
    <div class="qwhere">${where}</div>
    <div class="st4"><div><b>${o.vmax ?? 45}</b><small>${w.max} · ${w.kmh}</small></div><div><b>${o.km ?? '8.9'}</b><small>${w.dist} · ${w.km}</small></div><div><b>${n(o.desc ?? 1656, en)}</b><small>${w.desc} · ${w.m}</small></div><div><b>${o.runs ?? 3}</b><small>${w.runs}</small></div></div>
    ${o.extra || ''}
    <div class="row" style="justify-content:space-between"><span class="note">${o.note ?? (en ? 'Keeps recording with the screen off.' : 'ממשיך להקליט גם כשהמסך כבוי.')}</span>
      <span class="qb" style="height:44px;gap:8px">${ic.stop(16)}${w.stop}</span></div>
  </div></div>`;
}
// the map with the trail (a top-view PNG from maps.mjs) in each app's frame
function mapFrame(p, en, dark, mapPng, h, inner, t) {
  const top = p === 'and'
    ? `<div class="qtop"><span class="seg"><span class="on">${en ? '3D' : 'תלת-ממד'}</span><span>${en ? 'Top view' : 'מבט על'}</span></span><span class="homebtn">${en ? '← Home' : 'בית'} ${en ? '' : ic.back(16)}</span></div>`
    : `<div class="ititle" style="background:var(--paper)"><span>${en ? 'Trail map' : 'מפת מסלולים'}</span><span class="x">${ic.chev(18)}</span></div>`;
  const y0 = p === 'and' ? 26 : 112;
  return `${sbar(p, t, true, false)}<div style="position:absolute;top:${y0}px;left:0;right:0;bottom:0;overflow:hidden"><img src="${M(mapPng)}" style="display:block;width:100%;height:100%;object-fit:cover;object-position:50% 30%"></div>${top}${inner}`;
}
const legend = en => `<div class="legend"><span>${en ? 'slow' : 'איטי'}</span><i></i><span>${en ? 'fast' : 'מהיר'}</span><span style="margin-inline-start:8px;display:inline-flex;align-items:center;gap:4px"><svg width="22" height="6"><path d="M0 3h22" stroke="var(--dash)" stroke-width="3" stroke-dasharray="5 4"/></svg>${en ? 'lift' : 'רכבל'}</span></div>`;

// a small map of one run, from the day's own points
function runMini(seg, w = 120, h = 90) {
  const pts = DAY.track.filter(q => q.t >= seg.t0 && q.t <= seg.t1 && q.k !== 'stop');
  const xs = pts.map(q => q.x), ys = pts.map(q => q.y), x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys), y1 = Math.max(...ys);
  const s = Math.min((w - 16) / (x1 - x0 || 1), (h - 16) / (y1 - y0 || 1)), ox = (w - (x1 - x0) * s) / 2, oy = (h - (y1 - y0) * s) / 2;
  const X = x => (ox + (x - x0) * s).toFixed(1), Y = y => (oy + (y - y0) * s).toFixed(1);
  const col = v => { const R = [[0, [255, 214, 120]], [20, [245, 150, 50]], [40, [220, 50, 70]], [60, [120, 30, 150]]];
    for (let i = 1; i < R.length; i++) if (v <= R[i][0]) { const f = (v - R[i - 1][0]) / (R[i][0] - R[i - 1][0]); return `rgb(${R[i - 1][1].map((c, j) => Math.round(c + (R[i][1][j] - c) * f))})`; } return 'rgb(120,30,150)'; };
  let l = '';
  for (let i = 1; i < pts.length; i++) l += `<line x1="${X(pts[i - 1].x)}" y1="${Y(pts[i - 1].y)}" x2="${X(pts[i].x)}" y2="${Y(pts[i].y)}" stroke="${pts[i].k === 'off' ? 'var(--ink)' : col(pts[i].v)}" stroke-width="4" stroke-linecap="round"${pts[i].k === 'off' ? ' stroke-dasharray="5 4"' : ''}/>`;
  const f = seg.vmaxAt ? `<circle cx="${X(seg.vmaxAt.x)}" cy="${Y(seg.vmaxAt.y)}" r="7" fill="var(--paper)" stroke="var(--ink)" stroke-width="2"/><path d="M${X(seg.vmaxAt.x)} ${+Y(seg.vmaxAt.y) - 3.5}l3 6h-6z" fill="var(--ink)"/>` : '';
  return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" style="display:block;background:var(--snow)" aria-hidden="true">${l}${f}</svg>`;
}
const ROW = (en, seg) => {
  if (seg.type === 'lift') return `<li class="lift"><span class="tm">${hm(seg.t0)}</span><span class="ico">${ic.lift(18)}</span><span class="what"><span><b dir="ltr">${seg.name}</b> · ${en ? 'bottom to top' : 'מהתחתונה לעליונה'}</span></span><span class="num"><small>${dur(seg.t1 - seg.t0, en)}${seg.wait ? (en ? ` · queue ${seg.wait} min` : ` · תור ${seg.wait} דק׳`) : ''}</small></span></li>`;
  if (seg.type === 'pause') return `<li class="pause"><span class="tm">${hm(seg.t0)}</span><span class="ico">${ic.pause(18)}</span><span class="what">${en ? (seg.name === 'lunch' ? 'Lunch' : 'Break') : seg.label}</span><span class="num"><small>${dur(seg.t1 - seg.t0, en)}</small></span></li>`;
  if (seg.type === 'climb') return `<li class="pause"><span class="tm">${hm(seg.t0)}</span><span class="ico">${ic.walk(18)}</span><span class="what">${en ? `Climb on foot · +${seg.rise} m` : `עלייה ברגל · +${seg.rise} מ׳`}</span><span class="num"><small>${dur(seg.t1 - seg.t0, en)}</small></span></li>`;
  if (seg.connector) return '';
  return `<li><span class="tm">${hm(seg.t0)}</span><span class="ico" style="color:var(--ink)">${ic.ski(18)}</span><span class="what"><span class="row" style="gap:4px;flex-wrap:wrap">${seg.pistes.map((k, i) => sign(seg.colors[i], k)).join(`<i class="arr">${en ? '→' : '←'}</i>`)}</span>
    <span class="l2">${dur(seg.t1 - seg.t0, en)} · ${n(seg.desc, en)} ${en ? 'm down' : 'מ׳ ירידה'}${seg.offpiste ? (en ? ' · part off the runs' : ' · קטע מחוץ למסלולים') : ''}</span></span>
    <span class="num"><b>${seg.vmax}</b><small>${en ? 'max km/h' : 'מרבית קמ״ש'}</small></span></li>`;
};

function home(p, en, dark) {
  // the home page of each app, a new sign "start day" at the top of the signs
  const lab = en ? 'Start day' : 'מתחילים יום';
  const sgn = (w) => `<div style="position:relative;height:${p === 'and' ? 72 : 76}px;background:transparent">
    <div style="position:absolute;inset-inline-end:${p === 'and' ? 18 : 24}px;top:6px;width:${w}px;height:${p === 'and' ? 60 : 62}px;background:var(--rec);clip-path:polygon(${en ? '0 0,calc(100% - 28px) 0,100% 50%,calc(100% - 28px) 100%,0 100%' : '28px 0,100% 0,100% 100%,28px 100%,0 50%'});display:flex;align-items:center;gap:12px;padding:0 ${en ? '40px 0 18px' : '18px 0 40px'};justify-content:${en ? 'flex-start' : 'flex-end'};color:#fff">
    <span style="width:16px;height:16px;border-radius:50%;background:#fff;box-shadow:0 0 0 4px rgba(255,255,255,.35)"></span><b class="k" style="font-size:40px">${lab}</b></div></div>`;
  if (p === 'and') return `${aS(dark ? 'store-8-home-night.png' : 'store-7-home.png', 0, 1290)}${sgn(332)}${aS(dark ? 'store-8-home-night.png' : 'store-7-home.png', 1290, 1920)}`;
  return `${iS(dark ? 'home-he-night.png' : (en ? 'home-en-day.png' : 'home-he-day.png'), 0, 1545)}${sgn(332)}${iS(dark ? 'home-he-night.png' : (en ? 'home-en-day.png' : 'home-he-day.png'), 1545, 2622)}`;
}

const BOARDS = {
  // ---------- A. starting ----------
  'a-and-home': ['and', 'he', 'day', () => home('and', false, false)],
  'a-ios-home-night': ['ios', 'he', 'night', () => home('ios', false, true)],
  'a-and-map': ['and', 'he', 'day', () => `${aS('store-1-map.png', 0, 1920)}
    <span class="qb rec" style="position:absolute;z-index:6;right:16px;bottom:${Math.round(330 * AK)}px;height:52px;padding:0 18px;font-size:17px">${ic.rec(16)}${W.he.start}</span>`],
  'a-ios-map-en': ['ios', 'en', 'day', () => `${iS('map3d-en.png', 0, 2622)}
    <span class="qb rec" style="position:absolute;z-index:6;left:136px;bottom:${Math.round(510 * IK)}px;height:50px;padding:0 18px;font-size:17px">${ic.rec(16)}${W.en.start}</span>`],
  'a-and-suggest': ['and', 'he', 'day', () => `${aS('store-1-map.png', 0, 1920)}<div style="position:absolute;inset:0;z-index:15;background:rgba(13,21,34,.28)"></div>
    <div class="sheet" style="padding:0 18px 22px"><div class="grab"></div>
      <div class="row" style="gap:12px;margin:10px 0 4px"><span style="width:44px;height:44px;display:grid;place-items:center;background:var(--rec);color:#fff">${ic.mtn(24)}</span><div><b class="k" style="font-size:34px">בגודאורי</b><div class="muted" style="font-size:15px">מקליטים את היום?</div></div></div>
      <div class="row" style="margin-top:14px"><span class="qb rec" style="flex:1">${ic.rec(14)}מתחילים יום</span><span class="qb" style="flex:none">לא עכשיו</span></div></div>`],
  'a-ios-suggest-night': ['ios', 'he', 'night', () => `${iS('map3d-night-he.png', 0, 2622)}<div style="position:absolute;inset:0;z-index:15;background:rgba(0,0,0,.35)"></div>
    <div class="sheet" style="padding:0 18px 22px"><div class="grab"></div>
      <div class="row" style="gap:12px;margin:10px 0 4px"><span style="width:44px;height:44px;border-radius:12px;display:grid;place-items:center;background:var(--rec);color:#fff">${ic.mtn(24)}</span><div><b class="k" style="font-size:34px">בגודאורי</b><div class="muted" style="font-size:15px">מקליטים את היום?</div></div></div>
      <div class="row" style="margin-top:14px"><span class="qb rec" style="flex:1">${ic.rec(14)}מתחילים יום</span><span class="qb" style="flex:none;border-color:var(--rule)">לא עכשיו</span></div></div>`],
  'a-and-perm': ['and', 'he', 'day', () => `${aS('store-1-map.png', 0, 1920)}<div style="position:absolute;inset:0;z-index:15;background:rgba(13,21,34,.28)"></div>
    <div class="sheet" style="padding:0 20px 22px"><div class="grab"></div><b class="k" style="font-size:36px;display:block;margin:8px 0 6px">מקליטים את היום</b>
      <ul class="perm" style="list-style:none;margin:0;padding:0">
        <li><span class="i">${ic.pocket(22)}</span><span>הטלפון בכיס, המסך כבוי</span></li>
        <li><span class="i">${ic.phone(22)}</span><span>המסלול נשאר רק בטלפון</span></li>
        <li><span class="i">${ic.batt(22)}</span><span>לוקח סוללה. מטען נייד עוזר</span></li></ul>
      <p class="note" style="margin:6px 0 12px">הטלפון ישאל על המיקום. מספיק "בזמן השימוש באפליקציה".</p>
      <span class="qb pri" style="width:100%;box-sizing:border-box">המשך</span></div>`],
  'a-ios-perm-en': ['ios', 'en', 'day', () => `${iS('map3d-en.png', 0, 2622)}<div style="position:absolute;inset:0;z-index:15;background:rgba(13,21,34,.3)"></div>
    <div class="sheet" style="padding:0 20px 22px"><div class="grab"></div><b class="k" style="font-size:36px;display:block;margin:8px 0 6px">Recording your day</b>
      <ul class="perm" style="list-style:none;margin:0;padding:0">
        <li><span class="i">${ic.pocket(22)}</span><span>Phone in your pocket, screen off</span></li>
        <li><span class="i">${ic.phone(22)}</span><span>Your track stays on this phone</span></li>
        <li><span class="i">${ic.batt(22)}</span><span>It uses battery. A power bank helps</span></li></ul>
      <p class="note" style="margin:6px 0 12px">Your iPhone will ask for location. "While Using the App" is enough.</p>
      <span class="qb pri" style="width:100%;box-sizing:border-box">Continue</span></div>`],

  // ---------- B. while recording ----------
  'b-and-live': ['and', 'he', 'day', () => `<div style="height:844px">${mapFrame('and', false, false, 'm-live', 520, livePanel('and', false, {}), '10:19')}</div>`],
  'b-ios-live': ['ios', 'he', 'day', () => `<div style="height:874px">${mapFrame('ios', false, false, 'm-live', 520, livePanel('ios', false, {}), '10:19')}</div>`],
  'b-and-live-lift-night-en': ['and', 'en', 'night', () => `<div style="height:844px">${mapFrame('and', true, true, 'm-lift-night', 520, livePanel('and', true, { v: 9, lift: 'Shino', note: 'Top station in 6 min' }), '10:30')}</div>`],
  'b-ios-live-night-en': ['ios', 'en', 'night', () => `<div style="height:874px">${mapFrame('ios', true, true, 'm-live-night', 520, livePanel('ios', true, {}), '10:19')}</div>`],
  'b-and-notif': ['and', 'he', 'night', () => `<div class="lock" style="height:640px;padding:34px 12px 0;direction:rtl">
      <div style="text-align:center;font:300 64px/1 Roboto,system-ui;direction:ltr">10:19</div><div style="text-align:center;font-size:15px;opacity:.8;margin:6px 0 26px">שלישי, 12 בינואר</div>
      <div style="background:#26334b;border-radius:24px;padding:14px 16px;font-family:var(--f-body)">
        <div class="row" style="justify-content:space-between;font-size:12.5px;opacity:.8"><span class="row" style="gap:6px"><span style="width:18px;height:18px;border-radius:50%;background:var(--rec);display:inline-block"></span>Gudi · מקליט · <span dir="ltr">1:01</span></span><span>${ic.chev(14)}</span></div>
        <div class="row" style="justify-content:space-between;margin-top:8px"><div><b class="k" style="font-size:42px">41 קמ״ש</b><div style="font-size:14px;opacity:.9;margin-top:2px">ב-Tatra 2 · מרבית 45 · 3 ירידות</div></div>
          <span style="width:56px;height:56px;border-radius:14px;background:rgba(255,255,255,.08);display:grid;place-items:center">${ic.ski(28)}</span></div>
        <div class="row" style="margin-top:12px;gap:8px"><span style="flex:1;height:40px;border-radius:20px;background:rgba(255,255,255,.12);display:grid;place-items:center;font-weight:700">פתיחה</span><span style="flex:1;height:40px;border-radius:20px;background:rgba(255,90,82,.22);color:#ffb3ae;display:grid;place-items:center;font-weight:700">סיום</span></div>
      </div>
      <p style="text-align:center;font-size:12px;opacity:.6;margin-top:18px">התראה קבועה כל עוד מקליטים. "סיום" שואל לפני שעוצר.</p></div>`],
  'b-ios-lock': ['ios', 'he', 'night', () => `<div class="lock" style="height:720px;padding:60px 14px 0;direction:rtl">
      <div style="position:absolute;left:50%;top:11px;margin-left:-62px;width:124px;height:36px;border-radius:20px;background:#000"></div>
      <div style="text-align:center;font-size:16px;opacity:.85">שלישי, 12 בינואר</div><div style="text-align:center;font:700 86px/1 -apple-system,system-ui;direction:ltr;margin:2px 0 24px">10:19</div>
      <div style="background:rgba(30,40,60,.72);backdrop-filter:blur(10px);border-radius:24px;padding:14px 16px">
        <div class="row" style="justify-content:space-between"><span class="rdot" style="color:#ff8a83">מקליט · <span dir="ltr">1:01</span></span><span style="font-size:13px;opacity:.75">Gudi</span></div>
        <div class="row" style="justify-content:space-between;align-items:flex-end;margin-top:6px"><div class="row" style="align-items:baseline;gap:6px"><b class="k" style="font-size:64px">41</b><span style="font-size:15px;opacity:.8">קמ״ש</span></div>
          <div style="text-align:end;font-size:14px;line-height:1.5;opacity:.92">${sign('blue', 'Tatra 2')}<div>מרבית 45 · 3 ירידות</div></div></div>
        <div class="pb" style="margin-top:12px;background:rgba(255,255,255,.18);border-radius:3px"><i style="width:58%;background:#fff;border-radius:3px"></i></div>
        <div class="row" style="justify-content:space-between;font-size:12px;opacity:.7;margin-top:5px"><span>2,700 מ׳</span><span>2,170 מ׳</span></div>
      </div>
      <div style="margin:22px 0 8px;font-size:13px;opacity:.7;text-align:center">Dynamic Island</div>
      <div class="row" style="justify-content:center;gap:14px;direction:ltr">
        <div style="height:36px;border-radius:20px;background:#000;display:flex;align-items:center;gap:56px;padding:0 14px"><span class="row" style="gap:6px;color:#ff8a83;font-weight:700"><span style="width:8px;height:8px;border-radius:50%;background:var(--rec)"></span>41</span><span style="font-size:12px;opacity:.85">km/h</span></div>
        <div style="height:36px;width:36px;border-radius:20px;background:#000;display:grid;place-items:center"><span style="width:10px;height:10px;border-radius:50%;background:var(--rec)"></span></div></div>
      <div style="margin:14px auto 0;width:360px;border-radius:40px;background:#000;padding:16px 20px;direction:rtl">
        <div class="row" style="justify-content:space-between"><b class="k" style="font-size:44px">41 <span style="font-size:20px">קמ״ש</span></b><span style="font-size:14px">${sign('blue', 'Tatra 2')}</span></div>
        <div class="row" style="justify-content:space-between;font-size:13px;opacity:.8;margin-top:6px"><span>מרבית 45</span><span>8.9 ק״מ</span><span>3 ירידות</span><span style="color:#ff8a83;font-weight:700">סיום</span></div></div>
    </div>`],

  // ---------- C. end of the day ----------
  'c-and-summary': ['and', 'he', 'day', () => summary('and', false)],
  'c-ios-summary-night-en': ['ios', 'en', 'night', () => summary('ios', true)],
  'c-and-timeline': ['and', 'he', 'day', () => timeline('and', false)],
  'c-ios-timeline-en': ['ios', 'en', 'day', () => timeline('ios', true)],
  'c-and-replay': ['and', 'he', 'day', () => replay('and', false)],
  'c-ios-replay': ['ios', 'he', 'day', () => replay('ios', false)],
  'c-and-run': ['and', 'he', 'day', () => runCard('and', false)],
  'c-ios-records-en': ['ios', 'en', 'day', () => records('ios', true)],
  'c-and-records': ['and', 'he', 'day', () => records('and', false)],

  // ---------- D. history, delete, export ----------
  'd-and-history': ['and', 'he', 'day', () => history('and', false)],
  'd-ios-history-en': ['ios', 'en', 'day', () => history('ios', true)],

  // ---------- F. the experience (FEATURES, "the experience is the moat") ----------
  'f-and-fall': ['and', 'he', 'day', () => fall('and', false)],
  'f-ios-fall-en': ['ios', 'en', 'day', () => fall('ios', true)],
  'f-and-liftsum': ['and', 'he', 'night', () => liftSum('and', false)],
  'f-ios-liftsum': ['ios', 'he', 'night', () => liftSum('ios', false)],
  'f-and-story': ['and', 'he', 'day', () => story('and', false)],
  'f-ios-story-en': ['ios', 'en', 'day', () => story('ios', true)],

  // ---------- G. one run, measured on its own (FEATURES 1א) ----------
  'g-and-start': ['and', 'he', 'day', () => `${aS('store-2-run.png', 0, 1920)}
    <span class="qb rec" style="position:absolute;z-index:6;left:16px;top:${Math.round(1180 * AK)}px;height:48px;padding:0 16px;font-size:16px">${ic.rec(14)}מודדים ירידה</span>
    <span class="bubble" style="left:16px;top:${Math.round(1180 * AK) - 44}px">רק הירידה הזו, בלי כל היום</span>`],
  'g-ios-start-en': ['ios', 'en', 'day', () => `${iS('map3d-en.png', 0, 2622)}
    <span class="qb rec" style="position:absolute;z-index:6;left:120px;bottom:${Math.round(510 * IK)}px;height:50px;padding:0 18px;font-size:17px">${ic.rec(16)}Measure a run</span>`],
  'g-and-live': ['and', 'he', 'day', () => `<div style="height:844px">${mapFrame('and', false, false, 'm-one-live', 520, livePanel('and', false, { v: 52, vmax: 58, km: '0.6', desc: 160, runs: 1, clock: '0:41', where: `<span class="ico">${ic.ski(18)}</span>${sign('red', 'Kudebi 1')}<span class="muted" style="font-weight:400;font-size:13px">כנראה · ירידה אחת</span>`, note: 'נעצר לבד בתחתית.' }), '13:35')}</div>`],
  'g-and-card': ['and', 'he', 'day', () => oneCard('and', false)],
  'g-ios-card-night-en': ['ios', 'en', 'night', () => oneCard('ios', true)],

  // ---------- H. the day's map: on the main map, or a map of its own ----------
  'h-and-main': ['and', 'he', 'day', () => dayMap('and', false, 'm-cmp-main', 'א. על המפה הראשית', 'כל המסלולים בצבעים שלהם, והשובל מעליהם.')],
  'h-and-day': ['and', 'he', 'day', () => dayMap('and', false, 'm-cmp-day', 'ב. מפת היום (מומלץ)', 'ההר והמסלולים באפור עמום; רק השובל שלך בצבע.')],
  'h-ios-day-night-en': ['ios', 'en', 'night', () => dayMap('ios', true, 'm-cmp-day-night', 'Your day’s map', 'The runs in faded grey; only your trail in colour.')],

  // ---------- E. states, on the live panel ----------
  'e-and-gps': ['and', 'he', 'day', () => stateBoard('and', false, 'gps')],
  'e-and-denied': ['and', 'he', 'day', () => stateBoard('and', false, 'denied')],
  'e-and-out': ['and', 'he', 'day', () => stateBoard('and', false, 'out')],
  'e-and-auto': ['and', 'he', 'day', () => stateBoard('and', false, 'auto')],
  'e-and-off': ['and', 'he', 'day', () => stateBoard('and', false, 'off')],
  'e-and-climb': ['and', 'he', 'day', () => stateBoard('and', false, 'climb')],
  'e-and-approx': ['and', 'he', 'day', () => stateBoard('and', false, 'approx')],
  'e-and-resume': ['and', 'he', 'day', () => stateBoard('and', false, 'resume')],
  'e-and-mid': ['and', 'he', 'day', () => stateBoard('and', false, 'mid')],
  'e-ios-gps-en': ['ios', 'en', 'day', () => stateBoard('ios', true, 'gps')],
  'e-ios-off-night-en': ['ios', 'en', 'night', () => stateBoard('ios', true, 'off')],
};

function header(p, en, title, sub, t, dark) {
  return `${sbar(p, t, false, dark)}<div style="padding:${p === 'and' ? 34 : 60}px 18px 6px"><div class="row" style="justify-content:space-between"><span class="row" style="gap:6px;color:var(--glacier);font-weight:700;font-size:15px">${en ? '' : ic.back(16)}${en ? '← ' : ''}${en ? 'Days' : 'הימים שלי'}</span>
    <span class="row" style="gap:14px;color:var(--ink)">${ic.share(20)}${ic.dots(20)}</span></div>
    <b class="k" style="display:block;font-size:44px;margin-top:10px">${title}</b><div class="muted" style="font-size:14px;margin-top:2px">${sub}</div></div>`;
}
function summary(p, en) {
  const w = W[en ? 'en' : 'he'];
  return `<div style="height:${p === 'and' ? 844 : 874}px">${header(p, en, en ? 'Tuesday on the mountain' : 'שלישי על ההר', `${w.day} · <span dir="ltr">${hm(S.start)}–${hm(S.end)}</span>`, '14:30', en)}
    <div style="padding:0 18px"><div class="big3"><div><b>${S.vmax}</b><small>${en ? 'top speed, est.' : 'מרבית, הערכה'}</small></div><div><b>${S.runs}</b><small>${w.runs}</small></div><div><b>${S.skiKm}</b><small>${en ? 'km skied' : 'ק״מ גלישה'}</small></div></div>
      <div class="row muted sm" style="gap:14px;margin:6px 0 10px"><span>${n(S.desc, en)} ${en ? 'm descent' : 'מ׳ ירידה'}</span><span>${S.lifts} ${en ? 'lifts' : 'רכבלים'}</span><span>${en ? 'skiing' : 'גלישה'} ${dur(S.skiMin * 60, en)}</span></div></div>
    <div style="position:relative;height:330px;overflow:hidden;border-block:1px solid var(--rule)"><img src="${M(en ? 'm-day-night' : 'm-day')}" style="display:block;width:100%;margin-top:-60px">
      <span class="bubble" style="top:14px;inset-inline-start:14px">${en ? 'You flew here' : 'פה טסת'} · ${S.vmax} · <span dir="ltr">${S.vmaxRun} · ${hm(runs.find(r => r.vmax === S.vmax).vmaxAt.t)}</span></span></div>
    <div style="padding:10px 18px">${legend(en)}<div class="row" style="margin-top:12px;gap:8px"><span class="qb pri" style="flex:1">${ic.play(16)}${en ? 'Replay in 3D' : 'צפייה חוזרת בתלת-ממד'}</span><span class="qb" style="flex:none">${ic.share(18)}</span></div>
    <p class="note" style="margin-top:10px">${w.sample}: ${en ? 'real lift and run lines, invented times and speeds' : 'הקווים והגבהים אמיתיים, השעות והמהירויות מומצאות'}.</p></div></div>`;
}
function timeline(p, en) {
  const rows = DAY.segs.map(s => ROW(en, s)).join('');
  return `${header(p, en, en ? 'The day, in order' : 'היום, לפי הסדר', `${W[en ? 'en' : 'he'].day}`, '14:31')}<div style="padding:4px 18px 18px"><ul class="tl">${rows}</ul>
    <p class="note" style="margin-top:10px">${en ? 'Tap a run to see it on the map. Off-run stretches are marked, not guessed.' : 'נגיעה בירידה פותחת אותה במפה. קטע מחוץ למסלולים מסומן, לא מנוחש.'}</p></div>`;
}
function replay(p, en) {
  const shot = p === 'and' ? aS('store-2-run.png', 0, 1140) : iS('map3d-run-he.png', 0, 1760);
  const dot = p === 'and' ? [497 * AK, 520 * AK] : [573 * IK, 1417 * IK];
  const r = runs.find(s => s.pistes.includes('Tatra 2'));
  const pct = (r.t0 + 150 - S.start) / (S.end - S.start) * 100;
  const segBars = DAY.segs.filter(s => s.type === 'run' && !s.connector).map(s => `<i class="seg" style="inset-inline-start:${(s.t0 - S.start) / (S.end - S.start) * 100}%;width:${Math.max((s.t1 - s.t0) / (S.end - S.start) * 100, 0.8)}%;background:var(--ink)"></i>`).join('');
  return `<div style="height:${p === 'and' ? 650 : 820}px">${shot}
    <div class="pin" style="left:${dot[0]}px;top:${dot[1]}px"><span class="h"></span><span class="d" style="display:block"></span></div>
    <span class="bubble" style="left:${dot[0] + 16}px;top:${dot[1] - 46}px">43 קמ״ש · <span dir="ltr">${hm(r.t0 + 150)}</span></span>
    <div class="sheet" style="padding:6px 18px 18px"><div class="grab"></div>
      <div class="row" style="justify-content:space-between;margin:4px 0 2px"><span class="row" style="gap:6px">${sign('blue', 'Tatra 1')}<i class="arr">←</i>${sign('blue', 'Tatra 2')}</span><span class="muted sm">ירידה 3 מתוך 10</span></div>
      <div class="scrub" dir="ltr"><span class="tr"></span>${segBars}<span class="kn" style="inset-inline-start:${pct}%"></span></div>
      <div class="row muted sm" style="justify-content:space-between;direction:ltr"><span>${hm(S.start)}</span><span>12:00</span><span>${hm(S.end)}</span></div>
      <div class="row" style="justify-content:center;gap:10px;margin-top:10px"><span class="qb" style="width:48px;padding:0">${ic.play(16)}</span><span class="qb" style="padding:0 12px">×4</span><span class="qb" style="padding:0 12px">×16</span><span class="qb pri" style="padding:0 12px">${ic.share(16)}סרטון</span></div>
      <p class="note" style="margin:8px 0 0;text-align:center">גרירה על הציר מזיזה את הנקודה לכל רגע ביום. הקווים הכהים הם הירידות.</p></div></div>`;
}
function runCard(p, en) {
  const r = runs.find(s => s.pistes.includes('Kudebi 1'));
  const rows = (lab, val) => `<div class="row" style="justify-content:space-between;padding:9px 0;border-bottom:1px solid var(--rule);font-size:15px"><span class="muted">${lab}</span><b>${val}</b></div>`;
  return `${header(p, en, 'ירידה 7', `${W.he.day} · <span dir="ltr">${hm(r.t0)}</span>`, '14:34')}
    <div style="padding:0 18px 18px"><div class="row" style="gap:6px;margin-bottom:12px">${sign('red', 'Kudebi 1')}<span class="muted sm">אדום</span></div>
    <div style="display:grid;grid-template-columns:1fr 150px;gap:14px;align-items:start">
      <div class="big3" style="grid-template-columns:1fr 1fr;margin:0"><div><b>${r.vmax}</b><small>מרבית · קמ״ש</small></div><div><b>${r.avg}</b><small>ממוצע · קמ״ש</small></div></div>
      ${runMini(r, 150, 120)}</div>
    <div style="margin-top:12px">${rows('זמן', dur(r.t1 - r.t0))}${rows('ירידה', `${n(r.desc)} מ׳`)}${rows('אורך', `${(r.len / 1000).toFixed(1)} ק״מ`)}${rows('הקטע התלול שעברת', `${r.steep}°`)}${rows('המהירות המרבית נמדדה', `<span dir="ltr">${hm(r.vmaxAt.t)}</span>`)}</div>
    <div class="row" style="margin-top:14px;gap:8px"><span class="qb" style="flex:1">${ic.play(16)}צפייה חוזרת</span><span class="qb" style="flex:1">המסלול במפה</span></div>
    <p class="note" style="margin-top:10px">המשולש במפה הקטנה: המקום שבו נמדדה המהירות המרבית. היא נספרת רק אם נשמרה לפחות שתי שניות.</p></div>`;
}
function records(p, en) {
  const share = `<div style="position:relative;width:${p === 'and' ? 354 : 366}px;height:${p === 'and' ? 440 : 455}px;margin:0 auto;background:var(--paper);box-shadow:0 2px 12px var(--shadow);overflow:hidden${p === 'ios' ? ';border-radius:18px' : ''}">
    <div style="height:42px;background:var(--glacier);color:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 14px"><b class="k" style="font-size:26px">${en ? 'Gudauri · Tue 12 Jan' : 'גודאורי · שלישי 12.1'}</b><span style="font-weight:700;font-size:13px;direction:ltr">Gudi</span></div>
    <img src="${M('m-share')}" style="display:block;width:100%;height:260px;object-fit:cover">
    <div class="big3" style="padding:10px 14px 0"><div><b>${S.vmax}</b><small>${en ? 'top km/h' : 'מרבית קמ״ש'}</small></div><div><b>${S.runs}</b><small>${en ? 'runs' : 'ירידות'}</small></div><div><b>${S.skiKm}</b><small>${en ? 'km' : 'ק״מ'}</small></div></div>
    <div style="position:absolute;bottom:0;inset-inline:0;height:6px;background:repeating-linear-gradient(90deg,var(--ink) 0 10px,transparent 10px 16px);opacity:.25"></div></div>`;
  const rec = (lab, val, sub, isNew) => `<div class="row" style="justify-content:space-between;padding:10px 0;border-bottom:1px solid var(--rule)"><span><span style="font-size:15px">${lab}</span><br><span class="muted sm">${sub}</span></span><span class="row" style="gap:8px">${isNew ? `<span class="qtag" style="background:var(--rec);color:#fff">${en ? 'New' : 'חדש'}</span>` : ''}<b class="k" style="font-size:32px">${val}</b></span></div>`;
  return `${header(p, en, en ? 'Personal bests' : 'השיאים שלי', en ? 'All recorded days, on this phone' : 'מכל הימים שהוקלטו, בטלפון', '14:36')}
    <div style="padding:0 18px">${rec(en ? 'Top speed' : 'מהירות מרבית', `${S.vmax} ${en ? 'km/h' : 'קמ״ש'}`, `<span dir="ltr">Kudebi 1</span> · ${en ? 'today' : 'היום'}`, true)}${rec(en ? 'Longest run' : 'הירידה הארוכה', `${(S.longest.len / 1000).toFixed(1)} ${en ? 'km' : 'ק״מ'}`, `<span dir="ltr">${S.longest.pistes.join(' ')}</span> · ${en ? 'today' : 'היום'}`, true)}${rec(en ? 'Most runs in a day' : 'הכי הרבה ירידות ביום', `${S.runs}`, en ? 'Monday 11 Jan: 9' : 'שני 11.1: 9', true)}</div>
    <div style="padding:14px 18px 6px"><div class="muted sm" style="margin-bottom:8px">${en ? 'Share the day' : 'שיתוף היום'}</div>${share}
    <div class="row" style="margin-top:12px;gap:8px"><span class="qb pri" style="flex:1">${ic.share(16)}${en ? 'Share' : 'שיתוף'}</span></div>
    <p class="note" style="margin:8px 0 14px">${en ? 'The picture holds the day, no names and no account.' : 'בתמונה רק היום עצמו, בלי שם ובלי חשבון.'}</p></div>`;
}
function history(p, en) {
  const days = [['שלישי 12.1', 'Tue 12 Jan', S.runs, S.vmax, S.skiKm], ['שני 11.1', 'Mon 11 Jan', 9, 58, 18.4], ['ראשון 10.1', 'Sun 10 Jan', 0, 0, 0]];
  const it = (d, i) => i === 2 ? '' : `<div class="row" style="gap:12px;padding:12px 0;border-bottom:1px solid var(--rule)">${runMini(i ? runs[3] : runs[6], 64, 52)}<div style="flex:1"><b style="font-size:16px">${en ? d[1] : d[0]}</b><div class="muted sm">${d[2]} ${en ? 'runs' : 'ירידות'} · ${d[4]} ${en ? 'km' : 'ק״מ'} · ${en ? 'top' : 'מרבית'} ${d[3]}</div></div><span style="color:var(--muted)">${ic.dots(20)}</span></div>`;
  const menu = p === 'and'
    ? `<div style="position:absolute;z-index:30;inset-inline-end:28px;top:200px;width:230px;background:var(--paper);box-shadow:0 6px 24px var(--shadow);padding:6px 0">
        <div class="row" style="padding:12px 16px">${ic.share(18)}שיתוף התמונה</div><div class="row" style="padding:12px 16px">${ic.file(18)}ייצוא GPX</div><div class="row" style="padding:12px 16px;color:var(--p-red)">${ic.trash(18)}מחיקת היום</div></div>`
    : `<div style="position:absolute;z-index:30;inset-inline:8px;bottom:8px"><div style="background:var(--paper);border-radius:14px;overflow:hidden">
        <div style="text-align:center;padding:12px;font-size:13px;color:var(--muted);border-bottom:1px solid var(--rule)">Tue 12 Jan</div>
        <div style="text-align:center;padding:16px;font-size:18px;color:var(--glacier);border-bottom:1px solid var(--rule)">Share picture</div>
        <div style="text-align:center;padding:16px;font-size:18px;color:var(--glacier);border-bottom:1px solid var(--rule)">Export GPX</div>
        <div style="text-align:center;padding:16px;font-size:18px;color:var(--p-red)">Delete day</div></div>
        <div style="margin-top:8px;background:var(--paper);border-radius:14px;text-align:center;padding:16px;font-size:18px;font-weight:600;color:var(--glacier)">Cancel</div></div>
       <div style="position:absolute;inset:0;z-index:25;background:rgba(0,0,0,.3)"></div>`;
  return `<div style="height:${p === 'and' ? 700 : 760}px">${sbar(p, '19:40', false)}<div style="padding:${p === 'and' ? 34 : 60}px 18px 6px"><b class="k" style="font-size:44px">${en ? 'My days' : 'הימים שלי'}</b><div class="muted" style="font-size:14px">${en ? 'Recorded days stay on this phone only' : 'הימים שהוקלטו נשמרים רק בטלפון'}</div></div>
    <div style="padding:0 18px">${days.map(it).join('')}
    <div class="row" style="justify-content:space-between;margin-top:14px"><span class="muted sm">${en ? '2 days · 4.1 MB' : '2 ימים · 4.1MB'}</span><span style="color:var(--p-red);font-weight:700;font-size:14px">${en ? 'Delete all' : 'מחיקת הכל'}</span></div>
    <p class="note" style="margin-top:10px">${en ? 'GPX is a file you choose where to send. Nothing is uploaded.' : 'GPX הוא קובץ, ובוחרים לאן לשלוח אותו. שום דבר לא עולה לשרת.'}</p></div>${menu}</div>`;
}
function stateBoard(p, en, kind) {
  const L = {
    gps: [en ? 'Weak GPS · accuracy 40 m' : 'GPS חלש · דיוק 40 מ׳', en ? 'Recording goes on; speed is not counted until the signal is good.' : 'ההקלטה ממשיכה; מהירות לא נספרת עד שהקליטה טובה.', 'warn', ic.sat(20)],
    denied: [en ? 'No location permission' : 'אין הרשאה למיקום', en ? 'Settings → Location → While using.' : 'הגדרות ← מיקום ← בזמן השימוש באפליקציה.', 'warn', ic.sat(20), en ? 'Open settings' : 'פתיחת ההגדרות'],
    out: [en ? 'You are outside Gudauri' : 'מחוץ לגודאורי', en ? 'Paused. It goes on when you are back on the mountain.' : 'ההקלטה בהשהיה, וממשיכה כשחוזרים להר.', 'off', ic.mtn(20)],
    auto: [en ? 'No movement for 55 min' : 'בלי תנועה 55 דק׳', en ? 'The day ends by itself in 5 min.' : 'היום ייסגר לבד בעוד 5 דק׳.', 'off', ic.pause(20), en ? 'Keep recording' : 'ממשיכים להקליט'],
    off: [en ? 'Off the runs' : 'מחוץ למסלולים', en ? 'No line on the map here, so no name.' : 'אין כאן קו במפה, ולכן אין שם.', 'off', ic.ski(20)],
    approx: [en ? 'Precise location needed' : 'צריך מיקום מדויק', en ? 'With approximate location the day can’t be measured.' : 'עם מיקום משוער אי אפשר למדוד את היום.', 'warn', ic.sat(20), en ? 'Allow precise' : 'מיקום מדויק'],
    resume: [en ? 'The day was cut off' : 'היום נקטע', en ? 'The phone closed the app at 12:41. Carry on from there?' : 'הטלפון סגר את האפליקציה ב-12:41. להמשיך מאותה נקודה?', 'warn', ic.pause(20), en ? 'Carry on' : 'להמשיך'],
    mid: [en ? 'Off the lift at a point not on the map' : 'ירדת מהרכבל בנקודה שלא ממופה', en ? 'No mid station there in the map data.' : 'אין שם תחנת ביניים בנתוני המפה.', 'off', ic.lift(20)],
    climb: [en ? 'Going up, no lift known' : 'עלייה, בלי רכבל ידוע', en ? 'Marked as a climb, not as a lift.' : 'מסומן כעלייה, לא כרכבל.', 'off', ic.walk(20)],
  }[kind];
  const where = kind === 'off' ? `<span class="ico">${ic.ski(18)}</span><span class="row" style="gap:6px"><svg width="26" height="8"><path d="M0 4h26" stroke="var(--ink)" stroke-width="3" stroke-dasharray="6 4"/></svg>${en ? 'Off the runs' : 'מחוץ למסלולים'}</span>`
    : kind === 'climb' ? `<span class="ico">${ic.walk(18)}</span><span>${en ? 'Climbing · +18 m' : 'עלייה · +18 מ׳'}</span>`
    : kind === 'out' || kind === 'resume' ? `<span class="ico">${ic.pause(18)}</span><span class="muted">${en ? 'Paused' : 'בהשהיה'}</span>` : kind === 'mid' ? `<span class="ico">${ic.lift(18)}</span><span>${en ? 'Off <b dir="ltr">Goodaura</b>, between stations' : 'ירדת מ-<b dir="ltr">Goodaura</b>, בין התחנות'}</span>` : null;
  const ban = `<div class="ban ${L[2]}" style="position:absolute;z-index:22;inset-inline:12px;top:${p === 'and' ? 90 : 124}px">${L[3]}<div style="flex:1"><div>${L[0]}</div><div class="note" style="font-weight:400">${L[1]}</div></div>${L[4] ? `<span class="qb" style="height:36px;padding:0 10px;font-size:13px">${L[4]}</span>` : ''}</div>`;
  const live = kind === 'denied' || kind === 'approx' ? `<div class="sheet" style="padding:0 18px 18px"><div class="grab"></div><b class="k" style="font-size:34px;display:block;margin:8px 0">${kind === 'approx' ? (en ? 'Precise location, please' : 'צריך מיקום מדויק') : (en ? 'Can’t record without location' : 'בלי מיקום אין הקלטה')}</b><p class="note" style="margin:0 0 12px;font-size:14px">${L[1]}</p><span class="qb pri" style="width:100%;box-sizing:border-box">${L[4]}</span></div>`
    : livePanel(p, en, { v: kind === 'gps' ? '—' : kind === 'out' || kind === 'auto' || kind === 'resume' || kind === 'mid' ? 0 : kind === 'climb' ? 3 : 34, where, runs: kind === 'off' || kind === 'climb' ? 5 : 3 });
  const png = kind === 'off' ? (en ? 'm-off-night' : 'm-off') : 'm-live';
  return `<div style="height:${p === 'and' ? 760 : 800}px">${mapFrame(p, en, false, png, 470, ban + live, kind === 'off' ? '11:52' : '10:19')}</div>`;
}


function oneCard(p, en) {
  const r = runs.find(s => s.pistes.includes('Kudebi 1'));
  const rows = (lab, val) => `<div class="row" style="justify-content:space-between;padding:9px 0;border-bottom:1px solid var(--rule);font-size:15px"><span class="muted">${lab}</span><b>${val}</b></div>`;
  return `${sbar(p, hm(r.t1 + 20), false, en)}<div style="padding:${p === 'and' ? 34 : 60}px 18px 0"><b class="k" style="display:block;font-size:44px">${en ? 'Your run' : 'הירידה שלך'}</b>
      <div class="row" style="gap:8px;margin:6px 0 10px">${sign('red', 'Kudebi 1')}<span class="muted sm">${en ? 'stopped by itself at the bottom' : 'נעצר לבד בתחתית'} · <span dir="ltr">${hm(r.t1)}</span></span></div>
      <div class="big3"><div><b>${r.vmax}</b><small>${en ? 'top, est. · km/h' : 'מרבית, הערכה · קמ״ש'}</small></div><div><b>${r.avg}</b><small>${en ? 'average · km/h' : 'ממוצע · קמ״ש'}</small></div><div><b>${dur(r.t1 - r.t0, en)}</b><small>${en ? 'time' : 'זמן'}</small></div></div></div>
    <div style="position:relative;height:250px;overflow:hidden;border-block:1px solid var(--rule);margin-top:8px"><img src="${M(en ? 'm-one-night' : 'm-one')}" style="display:block;width:100%;margin-top:-90px">
      <span class="bubble" style="top:12px;inset-inline-start:14px">${en ? 'You flew here' : 'פה טסת'} · ${r.vmax} · <span dir="ltr">${hm(r.vmaxAt.t)}</span></span></div>
    <div style="padding:6px 18px 18px">${rows(en ? 'Descent' : 'ירידה', `${n(r.desc, en)} ${en ? 'm' : 'מ׳'}`)}${rows(en ? 'Length' : 'אורך', `${(r.len / 1000).toFixed(1)} ${en ? 'km' : 'ק״מ'}`)}
      <div class="row" style="margin-top:14px;gap:8px"><span class="qb rec" style="flex:1">${ic.rec(14)}${en ? 'Measure another' : 'עוד ירידה'}</span><span class="qb" style="flex:none">${ic.share(18)}</span></div>
      <p class="note" style="margin-top:10px">${en ? 'Saved in My days, like a short day.' : 'נשמר ב"הימים שלי", כמו יום קצר.'}</p></div>`;
}
function dayMap(p, en, png, title, sub) {
  return `${sbar(p, '16:02', false, en)}<div style="padding:${p === 'and' ? 34 : 60}px 18px 8px"><b class="k" style="display:block;font-size:40px">${title}</b><div class="muted" style="font-size:14px;margin-top:2px">${sub}</div></div>
    <div style="position:relative;height:560px;overflow:hidden;border-block:1px solid var(--rule)"><img src="${M(png)}" style="display:block;width:100%"></div>
    <div style="padding:10px 18px 18px">${legend(en)}</div>`;
}

function fall(p, en) {
  // a likely fall, asked with a smile, with "I didn't fall" that deletes it; in the timeline and on the run card
  const r = runs.find(x => x.pistes.includes('Kikilo 2')), t = r.t0 + 50;
  const snow = `<svg width="64" height="40" viewBox="0 0 64 40" aria-hidden="true">${[[8,30,3],[16,18,2.5],[24,8,3.5],[32,22,2],[40,6,3],[48,16,2.5],[56,28,3],[28,34,2],[44,32,2.2],[12,10,2]].map(([x,y,r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="var(--sn3)"/>`).join('')}<path d="M32 38c-6-8-2-16 0-20 2 4 6 12 0 20z" fill="var(--sn2)"/></svg>`;
  const rows = DAY.segs.filter(x => x.t0 >= r.t0 - 700 && x.t0 <= r.t1 + 60).map(x => ROW(en, x)).join('');
  const fallRow = `<li style="grid-template-columns:44px 30px 1fr;background:color-mix(in srgb,var(--glacier) 7%,var(--paper));min-height:auto;padding:10px 0"><span class="tm">${hm(t)}</span><span class="ico" style="color:var(--glacier)">${ic.ski(18)}</span>
    <span class="what" style="gap:8px"><span class="row" style="gap:10px">${snow}<b style="font-size:16px">${en ? 'Did you kiss the snow here?' : 'נראה שנשקת לשלג כאן?'}</b></span>
    <span class="row" style="gap:8px"><span class="qb" style="height:36px;padding:0 12px;font-size:14px">${en ? 'Yes, I fell' : 'כן, נפלתי'}</span><span class="qb" style="height:36px;padding:0 12px;font-size:14px;border-color:var(--rule)">${en ? 'I didn’t fall' : 'לא נפלתי'}</span></span>
    <span class="note">${en ? 'A guess from the phone’s motion. Not a safety feature.' : 'ניחוש לפי תנועת הטלפון. לא כלי בטיחות.'}</span></span></li>`;
  const html = rows.replace(/(<li><span class="tm">[^<]*<\/span><span class="ico" style="color:var\(--ink\)">[\s\S]*?Kikilo 2[\s\S]*?<\/li>)/, '$1' + fallRow);
  return `${header(p, en, en ? 'The day, in order' : 'היום, לפי הסדר', W[en ? 'en' : 'he'].day, '14:31')}<div style="padding:4px 18px 18px"><ul class="tl">${html}</ul></div>`;
}
function liftSum(p, en) {
  // the moment everyone takes the phone out: on the lift, the run you just did
  const r = runs.find(x => x.pistes.includes('Kudebi 1'));
  if (p === 'and') return `<div class="lock" style="height:560px;padding:34px 12px 0;direction:rtl">
      <div style="text-align:center;font:300 64px/1 Roboto,system-ui;direction:ltr">${hm(r.t1 + 120)}</div><div style="text-align:center;font-size:15px;opacity:.8;margin:6px 0 26px">שלישי, 12 בינואר</div>
      <div style="background:#26334b;border-radius:24px;padding:14px 16px">
        <div class="row" style="justify-content:space-between;font-size:12.5px;opacity:.8"><span class="row" style="gap:6px"><span style="width:18px;height:18px;border-radius:50%;background:var(--rec);display:inline-block"></span>Gudi · על הרכבל Kudebi</span><span>${ic.chev(14)}</span></div>
        <div style="font-size:13px;opacity:.75;margin-top:10px">הירידה שהרגע עשית</div>
        <div class="row" style="justify-content:space-between;margin-top:4px">${sign('red', 'Kudebi 1')}<b class="k" style="font-size:40px">${r.vmax} קמ״ש</b></div>
        <div style="font-size:14px;opacity:.9;margin-top:6px">${dur(r.t1 - r.t0)} · ${n(r.desc)} מ׳ ירידה · ירידה 7 היום</div>
        <div style="font-size:14px;margin-top:8px;color:#FFD98A">המהירה ביותר שלך עד היום</div></div>
      <p style="text-align:center;font-size:12px;opacity:.6;margin-top:18px">מופיע כשהאפליקציה מזהה שעלית לרכבל; בירידה רק המהירות.</p></div>`;
  return `<div class="lock" style="height:560px;padding:60px 14px 0;direction:rtl">
      <div style="position:absolute;left:50%;top:11px;margin-left:-62px;width:124px;height:36px;border-radius:20px;background:#000"></div>
      <div style="text-align:center;font-size:16px;opacity:.85">שלישי, 12 בינואר</div><div style="text-align:center;font:700 86px/1 -apple-system,system-ui;direction:ltr;margin:2px 0 24px">${hm(r.t1 + 120)}</div>
      <div style="background:rgba(30,40,60,.72);border-radius:24px;padding:14px 16px">
        <div class="row" style="justify-content:space-between"><span class="row" style="gap:6px;font-size:14px">${ic.lift(16)}על הרכבל <b dir="ltr">Kudebi</b></span><span style="font-size:13px;opacity:.75">Gudi</span></div>
        <div style="font-size:13px;opacity:.75;margin-top:10px">הירידה שהרגע עשית</div>
        <div class="row" style="justify-content:space-between;align-items:flex-end;margin-top:4px">${sign('red', 'Kudebi 1')}<div class="row" style="align-items:baseline;gap:6px"><b class="k" style="font-size:56px">${r.vmax}</b><span style="opacity:.8">קמ״ש</span></div></div>
        <div class="row" style="justify-content:space-between;font-size:13.5px;opacity:.9;margin-top:4px"><span>${dur(r.t1 - r.t0)}</span><span>${n(r.desc)} מ׳ ירידה</span><span style="color:#FFD98A">שיא חדש</span></div></div></div>`;
}
function story(p, en) {
  // the day as one card, like the boarding pass: numbers, map, moments, ready for WhatsApp
  const q = DAY.segs.filter(x => x.type === 'lift').reduce((a, b) => b.wait > a.wait ? b : a);
  const azrieli = Math.round(S.desc / 187);
  const moments = en
    ? [['Your first red', '<span dir="ltr">Kudebi 1</span> · 13:34'], ['Longest run', `${(S.longest.len / 1000).toFixed(1)} km on <span dir="ltr">Tatra 1</span>`], ['Longest queue', `${q.wait} min for <span dir="ltr">${q.name}</span>`], [`${azrieli}× Azrieli Tower`, `${n(S.desc, true)} m down today`], ['Snow kisses', '1']]
    : [['האדום הראשון שלך', '<span dir="ltr">Kudebi 1</span> · 13:34'], ['הירידה הארוכה', `${(S.longest.len / 1000).toFixed(1)} ק״מ ב-<span dir="ltr">Tatra 1</span>`], ['הכי הרבה בתור', `${q.wait} דק׳ ל-<span dir="ltr">${q.name}</span>`], [`פי ${azrieli} ממגדל עזריאלי`, `${n(S.desc)} מ׳ ירידה היום`], ['נשיקות לשלג', '1']];
  const w = p === 'and' ? 354 : 366;
  const card = `<div style="position:relative;width:${w}px;margin:0 auto;background:var(--bp-paper);color:var(--bp-ink);box-shadow:0 2px 14px var(--shadow);transform:rotate(-1.2deg)${p === 'ios' ? ';border-radius:16px;overflow:hidden' : ''}">
    <div style="height:44px;background:var(--bp-strip);color:var(--bp-on-strip);display:flex;align-items:center;justify-content:space-between;padding:0 14px"><b class="k" style="font-size:26px">${en ? 'SKI DAY · GUDAURI' : 'יום סקי · גודאורי'}</b><span style="font-weight:700;font-size:13px;direction:ltr">Tue 12 Jan</span></div>
    <div style="display:grid;grid-template-columns:1fr 1fr 1fr;padding:12px 14px 6px">${[[S.vmax, en ? 'top km/h' : 'מרבית קמ״ש'], [S.runs, en ? 'runs' : 'ירידות'], [S.skiKm, en ? 'km' : 'ק״מ']].map(([v, l]) => `<div><b class="k" style="font-size:44px;display:block">${v}</b><small style="font-size:12px;color:var(--bp-muted)">${l}</small></div>`).join('')}</div>
    <img src="${M('m-share')}" style="display:block;width:100%;height:170px;object-fit:cover;border-block:1px dashed var(--rule)">
    <ul style="list-style:none;margin:0;padding:6px 14px 12px">${moments.map(([a, b]) => `<li class="row" style="justify-content:space-between;padding:6px 0;border-bottom:1px dotted var(--rule);font-size:14px"><b>${a}</b><span style="color:var(--bp-muted)">${b}</span></li>`).join('')}</ul>
    <div style="height:10px;background:radial-gradient(circle at 6px 0,transparent 5px,var(--bp-paper) 5.5px) 0 0/12px 10px repeat-x;transform:translateY(9px)"></div></div>`;
  return `${header(p, en, en ? 'Your day' : 'היום שלך', en ? 'Ready for WhatsApp' : 'מוכן לוואטסאפ', '16:05')}<div style="padding:10px 18px 22px">${card}
    <div class="row" style="margin-top:22px;gap:8px"><span class="qb pri" style="flex:1">${ic.share(16)}${en ? 'Share' : 'שיתוף'}</span><span class="qb" style="flex:1">${ic.play(16)}${en ? 'Short film' : 'סרטון קצר'}</span></div>
    <p class="note" style="margin-top:10px">${en ? 'Milestones are against yourself only, no ranking between people.' : 'ציוני הדרך מול עצמך בלבד, בלי דירוג בין אנשים.'}</p></div>`;
}

// ---- photograph ----
const b = await launch();
const { ctx, page: p0 } = await open(b, '/', { w: 402, h: 874, wait: 300 });
const page = await ctx.newPage();
for (const [name, [p, lang, theme, fn]] of Object.entries(BOARDS)) {
  if (want.length && !want.includes(name)) continue;
  const en = lang === 'en';
  await page.setContent(`<!doctype html><html lang="${lang}" dir="${en ? 'ltr' : 'rtl'}" data-theme="${theme === 'night' ? 'dark' : 'light'}"><head><meta charset="utf-8">
<link rel="stylesheet" href="http://127.0.0.1:4199/css/site.css">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Karantina:wght@700&family=IBM+Plex+Sans+Hebrew:wght@400;500;600;700&family=IBM+Plex+Sans:wght@400;600;700&display=swap">
<style>${CSS}${en ? 'body,.qph{font-family:"IBM Plex Sans",system-ui,sans-serif}' : ''}</style></head><body><div class="qph ${p}">${fn()}</div></body></html>`, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(250);
  await page.locator('.qph').screenshot({ path: path.join(OUT, name + '.png') });
}
await ctx.close(); await b.close();
console.log('ok', Object.keys(BOARDS).length);
