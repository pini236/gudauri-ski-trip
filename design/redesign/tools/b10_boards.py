#!/usr/bin/env python3
"""B10 of review-release.md: the button column and the red record button have no words (26.11.2026).
The first-run hints (hn_boards.py, HN00 to HN10) already name record (hint 3), the picture/live switch (5) and "אני" (6).
What they leave: the layers button, "איפה אני" and the compass (and any later column button, e.g. the hour button of HR).
Recommendation: a long press on any icon-only button shows its name in a small pill beside it. No new text, no permanent labels.
A new canvas page "כפתורים בלי מילים (ב10)". Design only. Handoff: design/handoff/2026-11-26-redesign-b10/.
Usage: python3 design/redesign/tools/b10_boards.py [canvas.json path, default the repo copy]"""
import os, sys, json
import hn_boards as HN
from hn_boards import screen, T
from v56_boards import wb, li, PROJ, mtn, top, ctl_sw, ic, P_HOME, P_SRCH
import v56b_boards as VB
from v56b_boards import rc
import peak_boards as PB
import gap4_boards as G4

PAGE = 'b10'

TCSS = """
@keyframes bnin{0%{opacity:0;transform:translateY(-50%) scale(.92)}100%{opacity:1;transform:translateY(-50%)}}
.H .bname{position:absolute;transform:translateY(-50%);height:32px;border-radius:16px;background:var(--ink);color:var(--bg);display:flex;align-items:center;padding:0 12px;font-size:13.5px;font-weight:600;white-space:nowrap;box-shadow:0 6px 16px rgba(10,25,45,.28);animation:bnin .15s ease-out both;z-index:5}
.H .press{position:absolute;border-radius:50%;background:rgba(14,34,51,.16);pointer-events:none;z-index:4}
.H .night .press{background:rgba(234,241,248,.22)}
.H .bword{position:absolute;font-size:11px;font-weight:700;color:var(--ink);text-align:center;width:64px;text-shadow:0 0 6px var(--bg),0 0 3px var(--bg)}
"""
G4.MB.CSS = G4.MB.CSS + TCSS

# the column (gap4 .mc: left 12, bottom 84, gap 8): switch 12,568,44,88 · layers 12,664,44,44 · where 12,716,44,44
COL = {'switch': (12, 568, 44, 88), 'layers': (12, 664, 44, 44), 'where': (12, 716, 44, 44)}
NAME = {'layers': 'שכבות', 'where': 'איפה אני', 'photo': 'תמונה', 'live': 'חי'}


def press(x, y, w, h):
    """the finger held on the button: a soft disc 8 larger than the control"""
    return '<div class="press" style="left: %dpx; top: %dpx; width: %dpx; height: %dpx"></div>' % (x - 4, y - 4, w + 8, h + 8)


def tip(text, cy, start=None, end=None):
    """the name pill, 10 from the control, toward the screen centre; vertically centred on the control"""
    side = 'left: %dpx' % start if start is not None else 'right: %dpx' % end
    return '<div class="bname" role="tooltip" style="%s; top: %dpx">%s</div>' % (side, cy, text)


def held(key, text=None, rtl=True):
    x, y, w, h = COL[key]
    if not rtl:
        x = 390 - x - w
    cy = y + h / 2
    t = tip(text or NAME[key], cy, start=x + w + 10) if rtl else tip(text or NAME[key], cy, end=390 - x + 10)
    return press(x, y, w, h) + t


def en_screen():
    """English, left to right: the column on the right, the pill to its left"""
    o = mtn() + top(text='Planned opening 20.12.2026 · by <bdi>MTA</bdi>', eg=False, dot='sd q').replace('left: 64px', 'left: 12px; right: 64px')
    o = o.replace('left: 12px; padding: 0', 'right: 12px; left: auto; padding: 0')
    o += ctl_sw('pic').replace('class="mc"', 'class="mc" style="left: auto; right: 12px"')
    o += ('<div class="dock3 glass"><button class="vl">%s Village</button><div class="find">%s<span>Where to ski?</span></div>%s</div>'
          % (ic(P_HOME, 20), ic(P_SRCH, 18), rc().replace('aria-label="%s"' % VB.WORD, 'aria-label="Record skiing"')))
    o += ('<button class="ctile glass" style="left: 12px; top: 636px"><span class="cn">%d</span>'
          '<span class="c"><b>days to opening</b><span>Lifts start <bdi>20.12</bdi></span></span></button>' % HN.DAYS)
    return o + held('layers', 'Layers', rtl=False)


def labels():
    """the alternative not recommended: a word beside every column button, always on"""
    o = ''
    for key, word in (('switch', 'תמונה / חי'), ('layers', 'שכבות'), ('where', 'איפה אני')):
        x, y, w, h = COL[key]
        o += '<div class="bword" style="left: %dpx; top: %dpx; width: auto; text-align: start; white-space: nowrap">%s</div>' % (x + w + 8, y + h / 2 - 8, word)
    return o


def phones():
    return {
        'BT1.dc.html': ('נגיעה ארוכה בשכבות: השם בגלולה', screen() + held('layers'), None),
        'BT2.dc.html': ('נגיעה ארוכה ב"איפה אני", בלילה', screen('night') + held('where'), 'night'),
        'BT3.dc.html': ('באנגלית: Layers, משמאל לכפתור', en_screen(), 'day', 'en'),
        'BT4.dc.html': ('החלופה שלא מומלצת: מילה קבועה ליד כל כפתור', screen() + labels(), None),
    }


HB = 1240


def bt00():
    cover = ('<table style="border-collapse: collapse; width: 100%; font-size: 14.5px; line-height: 1.45"><tr style="color: #56606C; font-size: 13px">'
             '<td style="padding: 6px 10px">כפתור בלי מילה</td><td style="padding: 6px 10px">מה מסביר אותו</td><td style="padding: 6px 10px">עוד צריך?</td></tr>'
             + ''.join('<tr style="border-top: 1px solid #D9DEE4">%s</tr>' % ''.join('<td style="padding: 8px 10px; vertical-align: top">%s</td>' % c for c in r) for r in [
                 ('ההקלטה (האדום)', 'רמז 3 (HN3), בפתיחה השנייה', 'לא'),
                 ('המתג תמונה / חי', 'רמז 5 (HN5), והגלולה Z03 כשמנסים לסובב תמונה', 'לא'),
                 ('"אני"', 'רמז 6 (HN6)', 'לא'),
                 ('שכבות', 'נגיעה פותחת שורת עדשות עם שמות (שמש, פתוח, שיפוע, שלג)', 'רק השם, לפני שנוגעים'),
                 ('"איפה אני"', 'נגיעה ראשונה מבקשת הרשאת מיקום מהמערכת, ואז נקודה על ההר', 'רק השם, לפני שנוגעים'),
                 ('המצפן', 'מופיע רק אחרי שסובבו, ומחזיר למבט הפתיחה (S08)', 'רק השם'),
             ]) + '</table>')
    opts = li('<b>א. נגיעה ארוכה מראה את השם (מומלץ).</b> כמו בכל אפליקציה באנדרואיד. גלולה קטנה ליד הכפתור, והפעולה עצמה לא קורית. בלי טקסט חדש, בלי עומס על ההר. (BT1 עד BT3)',
              '<b>ב. מילה קבועה ליד כל כפתור.</b> מבינים מיד, אבל שלוש מילים צפות על ההר כל הזמן, בלי מסגרת (נגד הכלל של ב9), ומכסות מסלולים. (BT4)',
              '<b>ג. עוד שני רמזים בתור (שכבות, "איפה אני").</b> שמונה רמזים ייגמרו רק בפתיחה הרביעית. הנגיעה בשניהם מסבירה את עצמה, אז רמז מיותר.')
    spec = li('<b>על מה:</b> כל כפתור סמל על ההר: שני חצאי המתג ("תמונה", "חי"), שכבות, "איפה אני", המצפן, ההקלטה ו"אני". גם כפתור חדש שיתווסף לעמודה (למשל השעה, HR) מקבל את זה בלי עיצוב נוסף.',
              '<b>מתי:</b> נגיעה ארוכה של המערכת (בערך 0.5 שנ׳), עם רטט קצר אחד. הפעולה של הכפתור לא קורית. הגלולה נשארת כל זמן שהאצבע עליו, ועוד 1.5 שנ׳ אחרי שהורמה.',
              '<b>הגלולה:</b> גובה 32, רדיוס 16, רקע <span dir="ltr">ink</span>, טקסט <span dir="ltr">bg</span> ב-13.5 משקל 600, ריפוד 0 ו-12, צל <span dir="ltr">0 6 16 rgba(10,25,45,.28)</span>. 10 מהכפתור, לכיוון מרכז המסך, ממורכזת לגובה הכפתור. כפתור בסרגל התחתון או ב"אני": מעליו או מתחתיו, באותו מרחק. לפחות 12 מקצה המסך.',
              '<b>תנועה:</b> מופיעה ב-150 מ״ש (גדלה מ-0.92), נעלמת ב-150. בהפחתת תנועה: הופעה בלבד.',
              '<b>קורא מסך:</b> בלי שינוי. לכל כפתור כבר יש שם (<span dir="ltr">contentDescription</span>), והגלולה לא נקראת.',
              '<b>הטקסט:</b> המחרוזות הקיימות, בלי חדשות: <span dir="ltr">app.map_layers_a11y</span>, <span dir="ltr">loc.button</span>, <span dir="ltr">app.mode_photo</span>, <span dir="ltr">app.mode_live</span>, <span dir="ltr">app.rec_sign</span>, <span dir="ltr">app.pass_me</span>, ולמצפן <span dir="ltr">map.compass</span>.',
              '<b>ביחד עם הרמזים:</b> רמז פתוח נסגר בכל נגיעה, ולכן גם בנגיעה ארוכה. נגיעה ארוכה לא מסמנת רמז כנראה (לא השתמשו בכפתור).',
              '<b>משמאל לימין:</b> העמודה בצד ימין, והגלולה משמאל לה (BT3).')
    asks = li('<b>נגיעה ארוכה מראה את שם הכפתור, ואין מילים קבועות?</b> (ההמלצה: כן)',
              '<b>ואם לא רוצים גם את זה:</b> הרמזים מספיקים לבד. שכבות ו"איפה אני" מסבירים את עצמם בנגיעה הראשונה, וזה לא חוסם את השחרור.')
    inner = ('<p class="sec">כפתורים בלי מילים (ב10) · לגרסה בחנות · 26.11.2026 · ממתין לפיני</p>'
             '<h1>הרמזים כבר מסבירים את רוב הכפתורים. לשאר: נגיעה ארוכה מראה את השם</h1>'
             '<p class="lead">רמזים 3, 5 ו-6 מציגים את ההקלטה, את המתג ואת "אני". נשארו שכבות, "איפה אני" והמצפן, ושלושתם מסבירים את עצמם בנגיעה. מה שחסר הוא רק דרך לדעת מה הכפתור עושה לפני שנוגעים בו.</p>'
             '<div class="card"><h2>מה כבר מכוסה</h2>%s</div>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>שלוש דרכים</h2>%s</div>'
             '<div class="card" style="flex: 1.2"><h2>א, בפירוט</h2>%s</div></div>'
             '<div class="card"><h2>לפיני</h2>%s<p class="cap">אין מחרוזות חדשות ואין שינוי בחוזה. נמדד בכלי העיצוב, לא בטלפון. המסירה: <span dir="ltr">design/handoff/2026-11-26-redesign-b10/</span></p></div>'
             % (cover, opts, spec, asks))
    wb('BT00-NoWords.dc.html', 'כפתורים בלי מילים', 1280, HB, inner)


def main():
    cj = sys.argv[1] if len(sys.argv) > 1 else os.path.join(PROJ, 'canvas.json')
    ph = phones()
    for fn, v in ph.items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang=lang)
    bt00()
    j = json.load(open(cj, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith('BT'): del j['boards'][fn]
    j['order'] = [f for f in j['order'] if not f.startswith('BT')]
    place = [('BT00-NoWords.dc.html', 0, 0, 1280, HB, 'כפתורים בלי מילים: מה מכוסה ומה ההמלצה')]
    y = HB + 140
    for i, (c, t) in enumerate([('BT1', 'נגיעה ארוכה: שכבות'), ('BT2', 'נגיעה ארוכה: "איפה אני", בלילה'),
                                ('BT3', 'באנגלית: Layers'), ('BT4', 'לא מומלץ: מילים קבועות')]):
        place.append(('%s.dc.html' % c, 470 * i, y, 390, 844, t))
    for fn, x, y_, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y_, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name='כפתורים בלי מילים (ב10)'))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, w=1000, y=-200, text='כפתורים בלי מילים (ב10, 26.11.2026)')
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('ok', len(ph), cj)


if __name__ == '__main__':
    main()
