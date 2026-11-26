#!/usr/bin/env python3
"""B9 of review-release.md: text without a frame (26.11.2026). Complements the hints round (HN1 countdown tile,
HN10 search pill): the only gap left is the search hint line after 30 s. Design only.
Handoff: design/handoff/2026-11-26-redesign-b9/. Usage: python3 design/redesign/tools/b9_boards.py"""
import os, json
import hn_boards  # noqa: F401  (adds the hint CSS, incl. .gpill, to the shared board CSS)
import v56_boards as V
from v56_boards import pic, wb, li, PROJ, veil, rings, P_AIM, ic, hold
import peak_boards as PB
import gap4_boards as G4

PAGE = 'b9'
CSS = """
.H .gfr{position:absolute;left:50%;transform:translateX(-50%);border-radius:20px;display:flex;flex-direction:column;align-items:center;gap:3px;padding:10px 18px 11px;box-sizing:border-box;max-width:300px;width:max-content;text-align:center}
.H .gfr b{font-size:16px;font-weight:700;line-height:1.25;white-space:nowrap}
.H .gfr span{font-size:13.5px;line-height:1.4;color:var(--ink2)}
.H .gp2{position:absolute;left:50%;transform:translateX(-50%);height:34px;border-radius:17px;display:flex;align-items:center;padding:0 14px;font-size:13.5px;white-space:nowrap;color:var(--ink2)}
"""
G4.MB.CSS = G4.MB.CSS + CSS
TITLE = 'מחפש מיקום'
HINT = 'בחוץ זה לוקח בדרך כלל פחות מדקה.'   # app.search_hint, unchanged


def search(night=False, alt=False):
    light = 'night' if night else 'morning'; cx, cy = 195, 360
    o = pic('hero', light, None) + veil(0, 844, 'var(--veil)') + rings(cx, cy)
    o += '<div class="gsc glass" style="left: %dpx; top: %dpx">%s</div>' % (cx, cy, ic(P_AIM, 40, 1.6))
    if alt:   # two pills, one under the other
        o += '<div class="gpill glass" style="top: %dpx">%s</div>' % (cy + 132, TITLE)
        o += '<div class="gp2 glass" style="top: %dpx">%s</div>' % (cy + 180, HINT)
    else:     # the HN10 pill grows into one frame with both lines (the frame's top stays where the pill was)
        o += '<div class="gfr glass" style="top: %dpx"><b>%s</b><span>%s</span></div>' % (cy + 132, TITLE, HINT)
    return o + hold()


def b900():
    rows = [
        ('חיפוש מיקום, 30 השניות הראשונות', '"מחפש מיקום" עם הילה על ההר', 'בגלולה (HN10, סבב הרמזים)', '<b style="white-space: nowrap; color: #0F5C31">מכוסה</b>'),
        ('חיפוש מיקום, אחרי 30 שנ׳', 'השורה "בחוץ זה לוקח בדרך כלל פחות מדקה." נשארת צפה מתחת לגלולה', '<b>הגלולה גדלה למסגרת אחת עם שתי השורות</b> (B91, B92)', '<b style="white-space: nowrap; color: #3D2A9E">חדש כאן</b>'),
        ('הספירה לפתיחה', 'מספר ענק ושורה עם הילה', 'אריח קטן במסגרת (HN1)', '<b style="white-space: nowrap; color: #0F5C31">מכוסה</b>'),
        ('כרטיס הסקי', 'המחיר הגדול ושורות ההסבר בראש הדף', 'עומד בכלל: זה ראש של דף שלם, וההר מאחוריו מוסתר בהצללה (Q09). בלי שינוי', '<b style="white-space: nowrap; color: #0F5C31">בלי שינוי</b>'),
        ('המהירות בהקלטה', 'המספר בתוך הטבעת', 'בתוך עיגול הזכוכית של הטבעת, כבר במסגרת', '<b style="white-space: nowrap; color: #0F5C31">בלי שינוי</b>'),
    ]
    tbl = ('<table style="border-collapse: collapse; width: 100%; font-size: 14.5px; line-height: 1.45"><tr style="color: #56606C; font-size: 13px">'
           '<td style="padding: 6px 10px">המסך</td><td style="padding: 6px 10px">היום</td><td style="padding: 6px 10px">ההכרעה</td><td style="padding: 6px 10px"></td></tr>'
           + ''.join('<tr style="border-top: 1px solid #D9DEE4">%s</tr>' % ''.join('<td style="padding: 8px 10px; vertical-align: top">%s</td>' % c for c in r) for r in rows) + '</table>')
    spec = li('<b>אותה זכוכית, אותו מקום:</b> הראש של המסגרת נשאר ב-132 מתחת למרכז הפעימה, כמו הגלולה. רדיוס 20, ריפוד 10 ו-18, רוחב עד 300.',
              '<b>שתי שורות:</b> "מחפש מיקום" 16 מודגש; מתחת השורה הקיימת 13.5 בצבע <span dir="ltr">ink2</span>. בלי מחרוזת חדשה.',
              '<b>המעבר ב-30 שנ׳:</b> המסגרת גדלה למטה ב-250 מ״ש והשורה נגלית; בהפחתת תנועה: בלי גדילה, מופיעה מיד.',
              '<b>ההילה יורדת</b> מכל הטקסט במסך. ההר המוצלל, הפעימה ו"החזקה לסיום" לא משתנים.')
    asks = li('<b>השורה של 30 השניות נכנסת לאותה מסגרת עם "מחפש מיקום"</b> (B91), או גלולה שנייה מתחתיה (B93)? (מסגרת אחת: דבר אחד שקורה, במקום אחד)',
              '<b>כרטיס הסקי נשאר כמו שהוא</b>, כי זה ראש של דף שלם על הר מוצלל? (כן)')
    inner = ('<p class="sec">ב9 · טקסט במסגרת · 26.11.2026 · ממתין לפיני</p>'
             '<h1>טקסט על ההר רק בתוך מסגרת</h1>'
             '<p class="lead">סבב הרמזים כבר הכניס את "מחפש מיקום" לגלולה ואת הספירה לאריח. נשאר דבר אחד: השורה שמופיעה אחרי 30 שניות של חיפוש.</p>'
             '<div class="card"><h2>כל המקומות מדוח המוכנות</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.15"><h2>המסגרת של החיפוש</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>לפיני</h2>%s<p class="cap">בלי מחרוזות חדשות. נמדד בכלי העיצוב בלבד. המסירה: <span dir="ltr">design/handoff/2026-11-26-redesign-b9/</span></p></div></div>'
             % (tbl, spec, asks))
    wb('B900-Frame.dc.html', 'ב9 · טקסט במסגרת', 1280, 920, inner)


def main():
    ph = {'B91.dc.html': ('אחרי 30 שנ׳ של חיפוש: שתי השורות במסגרת אחת, ביום (ההמלצה)', search(), None),
          'B92.dc.html': ('אותו דבר בלילה', search(True), 'night'),
          'B93.dc.html': ('החלופה: גלולה שנייה מתחת', search(alt=True), None)}
    for fn, (title, body, fx) in ph.items():
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx)
    b900()
    p = os.environ.get('CANVAS_JSON') or os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith('B9'): del j['boards'][fn]
    j['order'] = [f for f in j['order'] if not f.startswith('B9')]
    place = [('B900-Frame.dc.html', 0, 0, 1280, 920, 'ב9 · טקסט במסגרת')]
    for i, (c, t) in enumerate([('B91', 'מסגרת אחת, ביום (ההמלצה)'), ('B92', 'מסגרת אחת, בלילה'), ('B93', 'החלופה: שתי גלולות')]):
        place.append(('%s.dc.html' % c, 470 * i, 920 + 140, 390, 844, t))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name='ב9 · טקסט במסגרת'))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, w=1000, y=-200, text='ב9 · טקסט במסגרת (26.11.2026)')
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('ok')


if __name__ == '__main__':
    main()
