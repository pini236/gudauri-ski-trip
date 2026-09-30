import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

test('vercel.json תקין ומכיל את כותרות האבטחה', () => {
  const cfg = JSON.parse(readFileSync('site/vercel.json', 'utf8'));
  const all = cfg.headers.find((h: any) => h.source === '/(.*)').headers.map((h: any) => h.key);
  expect(all).toEqual(expect.arrayContaining(['X-Content-Type-Options', 'Referrer-Policy', 'X-Robots-Tag']));
  const data = cfg.headers.find((h: any) => h.source === '/data/(.*)');
  expect(data.headers[0].key).toBe('Cache-Control');
});

test('סרטונים: כל סרטון שייך למסלול קיים, בקישור תקין וללא כפילות', () => {
  const seed = JSON.parse(readFileSync('site/data/videos-seed.json', 'utf8'));
  const runs = JSON.parse(readFileSync('site/data/runs-and-lifts.json', 'utf8'));
  const named = new Set(runs.pistes.filter((p: any) => p.named).map((p: any) => p.key));
  const seen = new Set<string>();
  for (const v of seed) {
    expect(named.has(v.piste), v.piste).toBeTruthy();
    expect(v.url).toMatch(/^https:\/\/www\.youtube\.com\/watch\?v=[\w-]{11}$/);
    expect(v.title.length).toBeGreaterThan(0);
    const id = v.piste + '|' + v.url;
    expect(seen.has(id), id).toBeFalsy();
    seen.add(id);
  }
  expect(seed.length).toBeGreaterThan(20);
});
