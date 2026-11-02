import { launch, open, MYTRIP } from '../../round16/lib.mjs';
const b = await launch();
for (const [n, o] of [['home-empty-day', { theme: 'day' }], ['home-empty-night', { theme: 'night' }], ['home-trip-day', { theme: 'day', trip: MYTRIP }], ['home-trip-night', { theme: 'night', trip: MYTRIP }]]) {
  const { ctx, page } = await open(b, '/', { ...o, wait: 1500 });
  await page.screenshot({ path: `/tmp/base-${n}.png`, fullPage: true });
  console.log(n, await page.evaluate(() => document.documentElement.scrollHeight));
  await ctx.close();
}
await b.close();
