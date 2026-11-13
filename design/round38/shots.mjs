// Round 38: schematic of the live-screen "searching for location" state. node design/round38/shots.mjs  (needs chromium path patch like round37)
import { chromium } from 'playwright';
const HERE = new URL('./', import.meta.url).pathname;
const b = await chromium.launch({ executablePath: process.env.CHROME });
const p = await b.newPage({ viewport: { width: 1000, height: 720 }, deviceScaleFactor: 1 });
await p.goto('file://' + HERE + 'mock.html'); await p.waitForTimeout(300);
await p.screenshot({ path: HERE + 'shots/searching.png', clip: { x: 0, y: 0, width: 1000, height: 720 } });
await b.close();
