// node design/round38/shots2.mjs  (CHROME=path)
import { chromium } from 'playwright';
const HERE = new URL('./', import.meta.url).pathname;
const b = await chromium.launch({ executablePath: process.env.CHROME });
const S = [['search','he','day'],['live','he','day'],['live-one','he','day'],['search-one','he','day'],['weak','he','day'],['search','he','night'],['live-one','he','night'],['weak','he','night'],['search','en','day'],['live','en','day'],['live-one','en','day'],['weak','en','night']];
for (const [s,l,t] of S) {
  const p = await b.newPage({ viewport: { width: 390, height: 844 } });
  await p.goto(`file://${HERE}live.html?s=${s}&l=${l}&t=${t}`); await p.waitForTimeout(250);
  await p.screenshot({ path: `${HERE}shots/live-${s}-${l}-${t}.png` }); await p.close();
}
await b.close();
