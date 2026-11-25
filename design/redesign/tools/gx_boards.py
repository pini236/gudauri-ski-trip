#!/usr/bin/env python3
"""The search screen, how much text (Pini via the manager, 25.11.2026): the title pill and the steps row repeat what the
pulse already says. Three versions, by day and by night, under the record-button boards on the "הערות על גרסה 56" page.
  1 as today   2 without the steps row   3 only the pulse and "מחפש מיקום" (the manager's recommendation)
Design only. Usage: python3 design/redesign/tools/gx_boards.py"""
import os, json
import v56_boards as V
from v56_boards import pic, veil, rings, ic, P_AIM, recp, steps, hold, wb, li, q, PROJ, PAGE
import peak_boards as PB

LONG = 'בחוץ, מתחת לשמיים פתוחים,<br>זה לוקח בדרך כלל פחות מדקה'


def screen(v, night):
    cx, cy = 195, 330 if v == 1 else 360
    o = pic('hero', 'night' if night else 'morning', None) + veil(0, 844, 'var(--veil)')
    o += rings(cx, cy) + '<div class="gsc glass" style="left: %dpx; top: %dpx">%s</div>' % (cx, cy, ic(P_AIM, 40, 1.6))
    sub = '' if v == 3 else '<span>%s</span>' % LONG
    o += '<div class="gst" style="top: %dpx"><b>מחפש מיקום</b>%s</div>' % (cy + 150, sub)
    if v in (1, 2): o += recp()
    if v == 1: o += steps(0)
    return o + hold()


HGX = 640


def gx00():
    rows = [('1', 'כמו היום', 'הגלולה "מעקב גלישה" עם הזמן, שורת השלבים, הפעימה, "מחפש מיקום" ושורה מתחת.'),
            ('2', 'בלי שורת השלבים', 'הגלולה עם הזמן, הפעימה, "מחפש מיקום" והשורה מתחת.'),
            ('3', 'רק הפעימה ו"מחפש מיקום" (ההמלצה)', 'בלי גלולה, בלי שלבים ובלי שורה מתחת. כשנמצא מיקום, המסך עובר לבד למסך החי.')]
    tr = ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 9px 10px; width: 5%%"><b>%s</b></td><td style="padding: 9px 10px; width: 30%%"><b>%s</b></td><td style="padding: 9px 10px">%s</td></tr>' % r for r in rows)
    tbl = '<table style="border-collapse: collapse; width: 100%%; font-size: 15.5px; line-height: 1.45">%s</table>' % tr
    inner = ('<p class="sec">הערות על גרסה 56 · מסך החיפוש</p><h1>מסך החיפוש: כמה טקסט צריך</h1>'
             '<div class="card">%s</div><div class="card"><h2>ההמלצה: 3</h2>%s%s</div>' % (tbl, li(
                 'הפעימה כבר אומרת "מחפש", והכפתור שנגעת בו אמר "הקלטת גלישה". הגלולה והשלבים חוזרים על זה.',
                 'השורה "בחוץ זה לוקח בדרך כלל פחות מדקה" מקבלת מקום טבעי רק אם החיפוש מתארך: אחרי 30 שניות בלי מיקום היא מופיעה מתחת ל"מחפש מיקום". קודם לכן היא מיותרת.',
                 'הזמן שעבר לא מוצג בזמן החיפוש. הוא מתחיל להיראות במסך החי, וסופר מהנגיעה כמו היום.',
                 '"החזקה לסיום" נשאר בכל הגרסאות, כדי שאפשר לוותר.'),
                 q(1, '<b>איזו גרסה:</b> 1, 2 או 3? <b>(ההמלצה: 3)</b>')))
    wb('GX00-Search.dc.html', 'מסך החיפוש: כמה טקסט', 1280, HGX, inner)


def main():
    t = {1: 'כמו היום', 2: 'בלי שורת השלבים', 3: 'רק הפעימה (ההמלצה)'}
    files = []
    for v in (1, 2, 3):
        for night in (False, True):
            fn = 'GX%d%s.dc.html' % (v, 'n' if night else 'd')
            PB.phone(fn, 'מסך החיפוש · %d, %s' % (v, 'בלילה' if night else 'ביום'), screen(v, night), night=night,
                     fixed='night' if night else 'day', lang='he')
            files.append((fn, '%d · %s, %s' % (v, t[v], 'בלילה' if night else 'ביום')))
    gx00()
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith('GX'): del j['boards'][fn]
    y = max(v['y'] + v['h'] for v in j['boards'].values() if v.get('page') == PAGE) + 260
    V.PLACE.clear(); V.PLACE.append(('GX00-Search.dc.html', 0, y, 1280, HGX, 'מסך החיפוש: כמה טקסט'))
    y += HGX + 140
    for i, (fn, tt) in enumerate(files):
        V.PLACE.append((fn, 470 * i, y, 390, 844, tt))
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    V.place_all(); print('ok')


if __name__ == '__main__':
    main()
