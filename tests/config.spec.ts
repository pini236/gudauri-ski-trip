import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

test('vercel.json תקין ומכיל את כותרות האבטחה', () => {
  const cfg = JSON.parse(readFileSync('site/vercel.json', 'utf8'));
  const all = cfg.headers.find((h: any) => h.source === '/(.*)').headers.map((h: any) => h.key);
  expect(all).toEqual(expect.arrayContaining(['X-Content-Type-Options', 'Referrer-Policy', 'X-Robots-Tag']));
  const data = cfg.headers.find((h: any) => h.source === '/data/(.*)');
  expect(data.headers[0].key).toBe('Cache-Control');
});
