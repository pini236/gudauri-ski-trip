#!/usr/bin/env python3
"""The end of a short or empty recording (Pini, 25.11.2026, via the manager): he tapped record away from the resort,
stopped after a few seconds, and got one right-aligned line in an empty sheet ("too empty"). Here: what there was
(how long, whether a location was found, where), what a real day gives (the sample day on the mountain), and two
clear actions. Placed under the boards on the "הערות על גרסה 56" page. Design only, no handoff until Pini approves.
Usage: python3 design/redesign/tools/em_boards.py"""
import os, json
import v56_boards as V
from v56_boards import pic, ic, wb, li, PROJ, PAGE, daylines
import peak_boards as PB
import gap4_boards as G4
import day4 as D

ECSS = """
.H .emx{position:absolute;top:52px;inset-inline-start:12px;width:44px;height:44px;border-radius:22px;display:flex;align-items:center;justify-content:center}
.H .emtag{position:absolute;top:58px;inset-inline-end:12px;height:32px;border-radius:16px;display:flex;align-items:center;gap:7px;padding:0 12px;font-size:13px;font-weight:500;white-space:nowrap}
.H .emf{position:absolute;inset:0;opacity:.75}
.H .ems{position:absolute;left:0;right:0;bottom:0;border-radius:28px 28px 0 0;padding:10px 20px 30px;box-sizing:border-box;display:flex;flex-direction:column;align-items:center;gap:16px}
.H .ems .gr{width:38px;height:5px;border-radius:3px;background:var(--line)}
.H .ems .hd{display:flex;flex-direction:column;align-items:center;gap:4px;text-align:center}
.H .ems .hd b{font-size:24px;font-weight:700;line-height:1.2}
.H .ems .hd span{font-size:15px;color:var(--ink2)}
.H .emr{display:flex;width:100%;border-radius:18px;border:1px solid var(--line)}
.H .emr .c{flex:1;display:flex;flex-direction:column;align-items:center;gap:5px;padding:12px 4px 11px;text-align:center}
.H .emr .c+.c{border-inline-start:1px solid var(--line)}
.H .emr .c svg{color:var(--ink2)}
.H .emr .c b{font-size:16px;font-weight:700;line-height:1.2;white-space:nowrap}
.H .emr .c b.t{font-variant-numeric:tabular-nums;direction:ltr;font-size:18px}
.H .emr .c.no b,.H .emr .c.no svg{color:var(--warn)}
.H .emr .c span{font-size:12.5px;color:var(--ink2)}
.H .emp{width:100%;display:flex;align-items:center;gap:12px;border-radius:18px;padding:12px 14px;box-sizing:border-box;background:var(--acc-soft,rgba(107,63,212,.09));border:1px solid rgba(107,63,212,.22)}
.H .night .emp{background:rgba(183,156,255,.12);border-color:rgba(183,156,255,.28)}
.H .emp .w{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.H .emp .w b{font-size:15.5px;font-weight:700;display:flex;align-items:center;gap:8px}
.H .emp .w span{font-size:13.5px;color:var(--ink2);white-space:nowrap}
.H .emp .w span bdi{font-variant-numeric:tabular-nums;color:var(--ink);font-weight:700}
.H .emp .ch{color:var(--acc);flex:none}
.H .emb{display:flex;gap:10px;width:100%}
.H .emb button{height:52px;border-radius:26px;font-size:16px;font-weight:700;flex:1;font-family:inherit}
.H .emb .del{background:none;border:1.5px solid var(--line);color:var(--red)}
.H .emb .back{background:var(--acc);color:var(--onacc);border:0}
"""
G4.MB.CSS = G4.MB.CSS + ECSS

P_CLK = '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.500V12l3 2"/>'
P_PIN = '<path d="M12 21s-6.500-5.600-6.500-11A6.500 6.500 0 0 1 18.500 10c0 5.400-6.500 11-6.500 11z"/><circle cx="12" cy="10" r="2.300"/>'
P_PINX = '<path d="M12 21s-6.500-5.600-6.500-11A6.500 6.500 0 0 1 18.500 10c0 5.400-6.500 11-6.500 11z"/><path d="m9.800 7.800 4.400 4.400m0-4.400-4.400 4.400"/>'
P_MTN = '<path d="M3 19 9.500 8l3.500 5.500L15.500 10 21 19z"/>'
P_X = '<path d="M6 6l12 12M18 6 6 18"/>'
P_CHV = '<path d="M15 6l-6 6 6 6"/>'


def end_empty(night=False, rec='0:42', found=True, where='out', when='היום · 16:02'):
    """the sheet at the end of a recording with no descents: three facts, the sample day, two buttons"""
    light = 'night' if night else 'morning'; m = 'night' if night else 'day'
    t_end = D.SEG[-1]['t1']
    o = pic('hero', light, None) + '<div class="emf">%s</div>' % daylines(t_end, m, 2.6)
    o += '<button class="emx glass" aria-label="חזרה להר">%s</button>' % ic(P_X, 20, 1.9)
    o += '<span class="emtag glass">יום על ההר %s</span>' % V.EG
    loc = ('<div class="c">%s<b>נמצא</b><span>מיקום</span></div>' % ic(P_PIN, 20, 1.7) if found else
           '<div class="c no">%s<b>לא נמצא</b><span>מיקום</span></div>' % ic(P_PINX, 20, 1.7))
    place = {'out': 'מחוץ לגודאורי', 'in': 'בגודאורי', 'none': '—'}[where]
    facts = ('<div class="emr"><div class="c">%s<b class="t">%s</b><span>הוקלט</span></div>%s'
             '<div class="c">%s<b>%s</b><span>איפה</span></div></div>' % (ic(P_CLK, 20, 1.7), rec, loc, ic(P_MTN, 20, 1.7), place))
    n, mx, drop = len(D.DESC), round(D.ST['max_kmh']), '{:,}'.format(int(round(D.ST['drop_m'], -1)))
    peek = ('<button class="emp" aria-label="יום לדוגמה"><span class="w"><b>יום על ההר %s</b>'
            '<span><bdi>%d</bdi> ירידות · <bdi>%d</bdi> קמ״ש · <bdi>%s</bdi> מ׳ ירידה</span></span><span class="ch">%s</span></button>'
            % (V.EG, n, mx, drop, ic(P_CHV, 22, 2)))
    btns = '<div class="emb"><button class="back">חזרה להר</button><button class="del">מחיקה</button></div>'
    o += ('<div class="ems glass"><span class="gr"></span><div class="hd"><b>הקלטה בלי ירידות</b><span>%s</span></div>%s%s%s</div>'
          % (when, facts, peek, btns))
    return o


def phones():
    return {'EM1.dc.html': ('סוף הקלטה בלי ירידות · מחוץ לאתר, ביום', end_empty(), 'day'),
            'EM2.dc.html': ('סוף הקלטה בלי ירידות · מחוץ לאתר, בלילה', end_empty(True, when='היום · 21:14'), 'night'),
            'EM3.dc.html': ('סוף הקלטה בלי ירידות · לא נמצא מיקום', end_empty(rec='0:18', found=False, where='none'), 'day'),
            'EM4.dc.html': ('סוף הקלטה בלי ירידות · בגודאורי, ליד הרכבל', end_empty(rec='6:10', where='in', when='היום · 09:41'), 'day')}


HEM = 720


def table_rows(rows):
    return ('<table style="border-collapse: collapse; width: 100%%; font-size: 14.5px; line-height: 1.45">%s</table>'
            % ''.join('<tr style="border-top: 1px solid #D9DEE4"><td style="padding: 7px 10px; vertical-align: top; width: 26%%"><b>%s</b></td><td style="padding: 7px 10px; vertical-align: top">%s</td></tr>' % r for r in rows))


def em00():
    what = li('<b>מה היה:</b> שלוש עובדות בשורה אחת, במרכז: כמה זמן הוקלט, האם נמצא מיקום, ואיפה. בלי משפט הסבר.',
              '<b>מה מקבלים ביום אמיתי:</b> ההר מאחור מראה את היום לדוגמה בקווי אור, ובגיליון כרטיס "יום על ההר" עם המספרים של היום לדוגמה. נגיעה בו פותחת את היום לדוגמה המלא (הקיים, ב"הימים שלי").',
              '<b>שתי פעולות:</b> "חזרה להר" (הראשית) ו"מחיקה". מחיקה בשתי נגיעות, כמו ב"הימים שלי".',
              '<b>מאוזן:</b> הכל במרכז, ברוחב מלא, ובמרווחים קבועים של 16. הגיליון בגובה התוכן.',
              '<b>מתי:</b> בסוף כל הקלטה בלי ירידה, בכל אורך. הקלטה כזו לא נכנסת לשיאים, ונשמרת ב"הימים שלי" עד שמוחקים אותה.')
    texts = table_rows([
        ('כותרת', 'הקלטה בלי ירידות'),
        ('שורה מתחת', 'היום · {שעה}'),
        ('שלוש העובדות', '{זמן} / הוקלט · נמצא, לא נמצא / מיקום · מחוץ לגודאורי, בגודאורי / איפה'),
        ('הכרטיס', 'יום על ההר · דוגמה · {n} ירידות · {v} קמ״ש · {m} מ׳ ירידה'),
        ('כפתורים', 'חזרה להר · מחיקה'),
        ('קורא מסך', 'הכרטיס: "יום לדוגמה". ה-X: "חזרה להר".')])
    inner = ('<p class="sec">הערות על גרסה 56 · סוף הקלטה בלי ירידות · לפיני</p><h1>הקלטה קצרה או ריקה: מה היה, ומה יש ביום אמיתי</h1>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1.2"><h2>מה במסך</h2>%s</div>'
             '<div class="card" style="flex: 1"><h2>הטקסטים</h2>%s<p class="cap">"דוגמה" ו"מחיקה" קיימים. השאר חדשים, לסשן התוכן אחרי האישור.</p></div></div>'
             '<div class="card"><h2>הלוחות</h2>%s</div>' % (what, texts, li(
                 'EM1 ו-EM2: מחוץ לאתר, ביום ובלילה (המקרה של פיני).',
                 'EM3: לא נמצא מיקום (בתוך בניין). העובדה "לא נמצא" בכתום, ואין "איפה".',
                 'EM4: בגודאורי, עמד ליד הרכבל ולא ירד.')))
    wb('EM00-EmptyEnd.dc.html', 'סוף הקלטה בלי ירידות', 1280, HEM, inner)


def main():
    ph = phones()
    for fn, (title, body, fx) in ph.items():
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang='he')
    em00()
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith('EM'): del j['boards'][fn]
    j['order'] = [f for f in j['order'] if not f.startswith('EM')]
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    y = max(v['y'] + v['h'] for v in j['boards'].values() if v.get('page') == PAGE) + 260
    V.PLACE.clear()
    V.PLACE.append(('EM00-EmptyEnd.dc.html', 0, y, 1280, HEM, 'סוף הקלטה בלי ירידות')); y += HEM + 140
    for i, (c, t) in enumerate([('EM1', 'מחוץ לאתר, ביום'), ('EM2', 'מחוץ לאתר, בלילה'), ('EM3', 'לא נמצא מיקום'), ('EM4', 'בגודאורי, ליד הרכבל')]):
        V.PLACE.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
    V.place_all(); print('ok')


if __name__ == '__main__':
    main()
