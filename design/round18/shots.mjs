// Round 18 (parity between the site and the app): the site as it is today, for the canvas boards. Paused before the
// boards by decision 54; the proposals are still to be drawn on top of the real site (see README.md). The app side
// comes from the emulator QA release (android-qa, qa.zip). Uses the round 16 harness (fake server, made-up names).
// SITE=http://127.0.0.1:4199 node design/round18/shots.mjs [filter]   ->  $OUT (default: <tmp>/gud-r18)
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { launch, open } from '../round16/lib.mjs';

const OUT = (process.env.OUT || path.join(os.tmpdir(), 'gud-r18')) + '/';
fs.mkdirSync(OUT, { recursive: true });
const ONLY = process.argv[2] ? new RegExp(process.argv[2]) : null;

// Trips with both flights filled, so the board shows what the site leaves out (the return flight).
const TRIPS = [
  { id: 't-me', owner_id: 'u-me', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', out_arrives: '20:35:00', ret_date: '2027-01-15', ret_flight: '6H 892', ret_from: 'TBS', ret_to: 'TLV', ret_departs: '01:35:00', ret_arrives: '02:15:00', entered_by: null },
  { id: 't-me2', owner_id: 'u-dan', out_date: '2027-01-10', out_flight: '6H 897', out_from: 'TLV', out_to: 'TBS', out_departs: '16:00:00', out_arrives: '20:35:00', ret_date: '2027-01-15', ret_flight: '6H 892', ret_from: 'TBS', ret_to: 'TLV', ret_departs: '01:35:00', ret_arrives: '02:15:00', entered_by: null },
  { id: 't-ron', owner_id: 'u-ron', out_date: '2027-01-11', out_flight: 'A9 691', out_from: 'TLV', out_to: 'KUT', out_departs: '07:40:00', out_arrives: '11:10:00', ret_date: '2027-01-16', ret_flight: 'A9 692', ret_from: 'KUT', ret_to: 'TLV', ret_departs: '12:30:00', ret_arrives: '14:05:00', entered_by: 'u-me' },
];
const MEETUPS = [{ id: 'm1', station: '158744075b', meet_at: '2027-01-11T05:30:00+00:00', note: null, created_by: 'u-ron' }, { id: 'm2', station: '158744055t', meet_at: '2027-01-11T09:00:00+00:00', note: null, created_by: 'u-dan' }];

const J = body => ({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*', 'access-control-allow-headers': '*' }, body: JSON.stringify(body) });
async function withData(page) {
  await page.route(/supabase\.co\/rest\/v1\/trips/, r => r.fulfill(J(TRIPS)));
  await page.route(/supabase\.co\/rest\/v1\/meetups/, r => r.request().method() === 'GET' ? r.fulfill(J(MEETUPS)) : r.fulfill(J([])));
}
// Signed in with Google but in no group yet: the page that offers to create one.
async function noGroups(p) {
  await p.route(/supabase\.co\/rest\/v1\/(group_members|groups)/, r => r.fulfill(J([])));
  // after the harness's own start script, which writes a group into the cache on every load
  await p.context().addInitScript(() => localStorage.setItem('gud-acct', JSON.stringify({ uid: 'u-me', anon: false, name: 'נועה ניסיון', providers: ['google'], groups: [] })));
  await p.goto(p.url().split('#')[0] + '#group'); await p.reload(); await ready(p);
}
const ready = async p => { await p.waitForSelector('#loading', { state: 'hidden' }).catch(() => {}); await p.waitForTimeout(700); };
async function group(p, tab) {
  await withData(p);
  await p.goto(p.url().split('#')[0] + '#group/g1'); await p.reload(); await ready(p);
  await p.waitForSelector('#grSome:not([hidden])').catch(() => {});
  if (tab) { await p.locator(`#grTabs [data-tab="${tab}"]`).click(); await p.waitForTimeout(500); }
}
// One element (or several, as one box), with a margin around it.
async function el(p, name, sels, pad = 8) {
  const boxes = [];
  for (const s of [].concat(sels)) { const b = await p.locator(s).first().boundingBox(); if (b) boxes.push(b); }
  if (!boxes.length) { console.log('  missing', sels); return; }
  const x = Math.max(0, Math.min(...boxes.map(b => b.x)) - pad), y = Math.max(0, Math.min(...boxes.map(b => b.y)) - pad);
  const r = Math.max(...boxes.map(b => b.x + b.width)) + pad, btm = Math.max(...boxes.map(b => b.y + b.height)) + pad;
  await p.waitForTimeout(300); await p.screenshot({ path: OUT + name + '.png', clip: { x, y, width: Math.min(r, p.viewportSize().width) - x, height: btm - y } }); console.log(name);
}
async function shot(p, name, clip) { await p.waitForTimeout(400); await p.screenshot({ path: OUT + name + '.png', ...(clip ? { clip } : { fullPage: true }) }); console.log(name); }

const STEPS = [
  ['site-group-flights', async p => { await group(p); }],
  ['site-group-meetups', async p => { await group(p, 'meetups'); }],
  ['site-group-members', async p => { await group(p, 'members'); }],
  ['site-group-fill', async p => { await group(p, 'members'); await p.locator('[data-mmenu="u-tal"]').click(); await p.locator('[data-mtrip="u-tal"]').click(); await p.waitForTimeout(300); }],
  ['site-group-create', async p => { await noGroups(p); }],
  ['site-account-two', async p => { await p.goto(p.url().split('#')[0] + '#account'); await ready(p); }, { two: true }],
  ['site-trip-form', async p => { await p.goto(p.url().split('#')[0] + '#trip'); await ready(p); }, { trip: null }],
  ['site-home-day', async p => {}, { url: '/#home' }],
  ['site-home-night', async p => {}, { url: '/#home', theme: 'night' }],
  ['site-home-auto', async p => {}, { url: '/#home', theme: 'auto', now: '2027-01-11T09:00:00+04:00' }],
  ['site-group-night', async p => { await group(p); }, { theme: 'night' }],
  ['site-about-night', async p => { await p.goto(p.url().split('#')[0] + '#about'); await ready(p); }, { theme: 'night' }],
  ['site-map-en', async p => { await p.waitForTimeout(2500); }, { url: '/#map/run/Tatra%202', lang: 'en' }],
  ['site-map-he', async p => { await p.waitForTimeout(2500); }, { url: '/#map/run/Tatra%202' }],
];

// The same component on the site, cut out for the side-by-side boards (row C), sharp (scale 2).
const CROPS = [
  ['c-signs-day', async p => el(p, 'c-signs-day', 'nav.post', 6), { url: '/#home' }],
  ['c-signs-night', async p => el(p, 'c-signs-night', 'nav.post', 6), { url: '/#home', theme: 'night' }],
  ['c-header-day', async p => el(p, 'c-header-day', '.home-top', 6), { url: '/#home' }],
  ['c-header-night', async p => el(p, 'c-header-night', '.home-top', 6), { url: '/#home', theme: 'night' }],
  ['c-header-auto', async p => el(p, 'c-header-auto', '.home-top', 6), { url: '/#home', theme: 'auto', now: '2027-01-11T09:00:00+04:00' }],
  ['c-switch-day', async p => { await p.goto(p.url().split('#')[0] + '#about'); await ready(p); await el(p, 'c-switch-day', ['.ab-row[data-pref="sound"]', '.ab-row[data-pref="haptics"]'], 0); }],
  ['c-switch-night', async p => { await p.goto(p.url().split('#')[0] + '#about'); await ready(p); await el(p, 'c-switch-night', ['.ab-row[data-pref="sound"]', '.ab-row[data-pref="haptics"]'], 0); }, { theme: 'night' }],
  ['c-tabs-day', async p => { await group(p); await el(p, 'c-tabs-day', '#grTabs', 6); }],
  ['c-tabs-night', async p => { await group(p); await el(p, 'c-tabs-night', '#grTabs', 6); }, { theme: 'night' }],
  ['c-invite-day', async p => { await group(p, 'members'); await p.locator('.ac-invite').scrollIntoViewIfNeeded(); await el(p, 'c-invite-day', '.ac-invite', 8); }],
  ['c-empty-night', async p => el(p, 'c-empty-night', '#bpEmpty', 8), { url: '/#home', theme: 'night', trip: null, signed: false }],
  ['c-empty-day', async p => el(p, 'c-empty-day', '#bpEmpty', 8), { url: '/#home', trip: null, signed: false }],
  ['c-ticket-day', async p => el(p, 'c-ticket-day', '.bp.is-front', 8), { url: '/#home' }],
];
for (const c of CROPS) STEPS.push([c[0], c[1], { dsf: 2, ...(c[2] || {}) }, true]);

const b = await launch();
for (const [name, fn, o = {}, crop] of STEPS) {
  if (ONLY && !ONLY.test(name)) continue;
  const { ctx, page } = await open(b, o.url || '/#home', { signed: true, trip: 'trip' in o ? o.trip : { v: 1, sid: 't-me', out: { date: '2027-01-10', flight: '6H 897', from: 'TLV', to: 'TBS', departs: '16:00', arrives: '20:35' }, ret: { date: '2027-01-15', flight: '6H 892', departs: '01:35', arrives: '02:15' }, ski: null }, ...o });
  await fn(page);
  if (!crop) await shot(page, name);
  if (page._errors.length) console.log('  errors:', page._errors.slice(0, 3).join(' | '));
  await ctx.close();
}
await b.close();
