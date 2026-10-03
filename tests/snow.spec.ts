import { test, expect, Page } from '@playwright/test';

// The snow on the white cards (round 16, js/snow.js): a pile that covers the card's coloured top border from end to
// end, so no line peeks out under it (Pini, on the canvas, 3.10.2026). Checked on the drawing itself: every point of
// the border is inside the snow.
async function uncovered(page: Page, sel: string) {
  return page.locator(sel).first().evaluate(el => {
    const svg = el.querySelector(':scope>.snowcap') as SVGSVGElement | null;
    if (!svg) return { bt: 0, miss: -1 };
    const body = svg.querySelector('.sn-body') as SVGPathElement, pt = svg.createSVGPoint();
    // the drawing starts B pixels above the card's padding, so the border runs from B - bt to B inside it
    const bt = parseFloat(getComputedStyle(el).borderTopWidth), B = -parseFloat(svg.style.top), w = (el as HTMLElement).offsetWidth;
    let miss = 0;
    for (let x = .5; x < w; x++) for (const y of [B - bt + .5, B - bt / 2, B - .5]) { pt.x = x; pt.y = y; if (!body.isPointInFill(pt)) { miss++; break; } }
    return { bt, miss };
  });
}

for (const [name, url, sel] of [
  ['לוח העונה בדף הבית', '/', '#seasonBoard'],
  ['הכרטיס "למה להתחבר"', '/#signin', '#signinPage .ac-card[data-snow]'],
  ['הכרטיס "הצטרפות בקוד"', '/#group', '#grCode'],
  ['"בקצרה" בדף הפרטיות', '/privacy.html', '.pv-short:visible'],
]) {
  test(`השלג מכסה את המסגרת מקצה לקצה: ${name}`, async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-10-20T09:00:00Z'));
    await page.goto(url);
    await expect(page.locator(sel).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator(sel).first().locator(':scope>.snowcap')).toBeAttached();
    const r = await uncovered(page, sel);
    expect(r.bt).toBeGreaterThan(0);
    expect(r.miss, 'עמודות שבהן המסגרת מציצה מתחת לשלג').toBe(0);
  });
}
