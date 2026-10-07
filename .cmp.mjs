import { chromium } from '@playwright/test';
const S = process.argv[2];
const cases = [['home-phone','#home',390,844],['home-desk','#home',1280,800],['map-desk','#map',1280,800],['map-phone','#map',390,844],['run-desk','#map/run/Tatra%201',1280,800],['about-desk','#about',1280,800],['games-phone','#games',390,844]];
const b = await chromium.launch();
for (const [port, tag] of [[4174,'old'],[4173,'new']]) for (const [n,h,w,hh] of cases) {
  const ctx = await b.newContext({ viewport:{width:w,height:hh}, reducedMotion:'reduce', locale:'he-IL' });
  const p = await ctx.newPage();
  await p.clock.setFixedTime(new Date('2026-12-20T09:00:00Z'));
  await p.goto(`http://localhost:${port}/${h}`);
  await p.waitForSelector('html[data-model]');await p.waitForTimeout(h.startsWith('#map')?6000:1500);
  await p.screenshot({ path: `${S}/cmp-${n}-${tag}.png` });
  await ctx.close();
}
await b.close();
