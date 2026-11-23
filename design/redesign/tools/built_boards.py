#!/usr/bin/env python3
"""The canvas boards of what was built (page "built"): emulator screenshots of the painted-mountain POC on Android,
back on the canvas next to the boards they were built from (K09 to K11). The screenshots are from the emulator run of
commit 61b4f60 (android-qa run 288, 21.11.2026), scaled to 540 by 1200, in design/redesign/assets/built, and uploaded
to the canvas as assets; the urls are below.
  python3 design/redesign/tools/built_boards.py            write the boards and update canvas.json
  python3 design/redesign/tools/built_boards.py --check D   also write plain html copies with local images into D
"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from live_boards import board, wide, PROJ, ROOT  # the same look as the stage B page

A = 'design/redesign/assets/built/'
IMG = {
    'open': ('/_blob/933071534e617e5da9cd9cba0b6e9049', A + 'poc-open.jpg'),
    'chosen': ('/_blob/d7365a1f07add454a45f9120ba511382', A + 'poc-chosen.jpg'),
    'zoom': ('/_blob/39ae16bd413fe11fbef1d14a2d0092a9', A + 'poc-zoom.jpg'),
    'day': ('/_blob/d08013025c5872b54eff2fbb6dc7c77c', A + 'poc-day-chosen.jpg'),
    'night': ('/_blob/f54e8355e3e269105c948d4d5e9a7c51', A + 'poc-night.jpg'),
    'nightc': ('/_blob/2e76964da979bee5663264360b5e4854', A + 'poc-night-chosen.jpg'),
    'm0': ('/_blob/cfbfe242a32530b474700902b5910949', A + 'poc-moment-0.jpg'),
    'm150': ('/_blob/0af4c261365375a6d3660a31c6889793', A + 'poc-moment-150.jpg'),
    'm300': ('/_blob/a13134cb14b93db05968815169239964', A + 'poc-moment-300.jpg'),
    'm600': ('/_blob/d7f915d6b894c2fe1d290e0e8a6481a3', A + 'poc-moment-600.jpg'),
    'm900': ('/_blob/94819cc0dccbf6a84bdd936da2a310aa', A + 'poc-moment-900.jpg'),
}
FW = 222; FH = round(FW * 1200 / 540)


def shot(k, cap, sub=''):
    return ('<div class="fr" style="width: %dpx"><div class="th" style="width: %dpx; height: %dpx"><img alt="" src="%s"></div><p class="cap"><b>%s</b>%s</p></div>'
            % (FW, FW, FH, IMG[k][0], cap, ('<br>' + sub) if sub else ''))


def row(items):
    return '<div class="rowx">%s</div>' % ''.join(shot(*i) for i in items)


H00 = 1230; H01 = 760


def n00():
    passed = ['הפתיחה על ההר המצויר, באור של השעה.', 'רגע הבחירה: טבעת, המסך מתכהה בהדרגה, האור יורד במסלול, התקרבות, שלט וגיליון.',
              'מסלול נבחר: קו אור ביום, חרוזי אור בלילה, והשלט בראש המסלול.', 'צביטה וגרירה: הקווים מתעבים מעט, השמות נשארים בגודל קבוע.',
              'במבט הקרוב המסלול הנבחר כולו על המסך, גם בטלפון גבוה.']
    known = ['<b>אמולטור, לא טלפון.</b> התחושה, המגע והקצב על מכשיר אמיתי עוד לא נבדקו.', '<b>בגרסת הבדיקה בלבד.</b> גרסת החנות לא השתנתה.',
             '<b>מבט קרוב אחד.</b> רק ל-12 מתוך 28 המסלולים יש התקרבות ברגע הבחירה (הצד של Tatra ו-Goodaura).',
             '<b>הגיליון אטום כמעט</b> (0.92), בלי הטשטוש שבלוח K10.', '<b>דף הבית שמתחת חשוף לקורא המסך.</b> תנאי לפני שהמסך יוצא מגרסת הבדיקה.',
             '<b>פס השעה למעלה</b> הוא כלי של גרסת הבדיקה, לא עיצוב של המוצר.']
    inner = ('<p class="sec">סגירת מעגל · מה נבנה</p><h1>שלב א באנדרואיד: ההר המצויר</h1>'
             '<p class="lead">צילומים מהאמולטור של גרסת הבדיקה (21.11.2026), אחרי שני סבבי בדיקה מול הלוחות K09 עד K11 שבעמוד "סבב 3". זה מה שנבנה, לא ציור.</p>'
             '<div class="rowx" style="align-items: stretch">'
             '<div class="card" style="flex: 1"><h2>מה עבר בבדיקת המעצב</h2><ul>%s</ul></div>'
             '<div class="card" style="flex: 1"><h2>ביושר</h2><ul>%s</ul></div></div>'
             '<h2 style="margin-top: 6px">רגע הבחירה, חמישה פריימים (Tatra 2, ביום)</h2>%s'
             % (''.join('<li>%s</li>' % x for x in passed), ''.join('<li>%s</li>' % x for x in known),
                row([('m0', 'הנגיעה', 'טבעת במקום האצבע'), ('m150', '150 מ״ש', 'האור מתחיל לרדת, המסך מתכהה'), ('m300', '300 מ״ש', 'חצי מסלול מואר, ההתקרבות מתחילה'),
                     ('m600', '600 מ״ש', 'כל המסלול מואר'), ('m900', '900 מ״ש', 'המבט הקרוב, השלט, והגיליון עולה')])))
    return board('מה נבנה: שלב א באנדרואיד', 1280, H00, wide(1280, H00, inner))


def n01():
    inner = ('<h2>פתיחה, בחירה, צביטה, יום ולילה</h2>%s'
             % row([('open', 'הפתיחה', 'אור שקיעה, לפי השעה'), ('chosen', 'מסלול בלי מבט קרוב', 'Kudebi 1: נשארים במבט הרחוק'), ('zoom', 'צביטה', 'הקווים דקים, השמות קבועים'),
                    ('day', 'נבחר, ביום', 'קו אור, חיצים וקילומטרים'), ('night', 'לילה', 'אורות בכפר'), ('nightc', 'נבחר, בלילה', 'חרוזי אור, Tatra 1 כולו על המסך')]))
    w = 6 * FW + 5 * 16 + 104
    return board('נבנה: פתיחה, בחירה, יום ולילה', w, H01, wide(w, H01, inner))


BOARDS = [
    ('N00-Built.dc.html', n00, 0, 0, 1280, H00, 'מה נבנה: שלב א באנדרואיד'),
    ('N01-BuiltStates.dc.html', n01, 0, H00 + 120, 6 * FW + 5 * 16 + 104, H01, 'פתיחה, בחירה, צביטה, יום ולילה'),
]
NOTES = {'tN': dict(kind='title1', maxW=1600, page='built', w=760, x=0, y=-300, text='נבנה באנדרואיד · שלב א, ההר המצויר (צילומי אמולטור)')}


def main():
    chk = sys.argv[sys.argv.index('--check') + 1] if '--check' in sys.argv else None
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf-8'))
    for name, fn, x, y, w, h, title in BOARDS:
        s = fn(); open(os.path.join(PROJ, name), 'w', encoding='utf-8').write(s)
        j['boards'][name] = dict(x=x, y=y, w=w, h=h, title=title, page='built')
        if name not in j['order']: j['order'].append(name)
        if chk:
            os.makedirs(chk, exist_ok=True); t = s
            for k, (u, f) in IMG.items(): t = t.replace(u, 'file://' + os.path.join(ROOT, f))
            t = re.sub(r'</?x-dc>|</?helmet>|<script src="./support.js"></script>', '', t)
            open(os.path.join(chk, name.replace('.dc.html', '.html')), 'w', encoding='utf-8').write(t)
    j['notes'].update(NOTES)
    if not any(pg['id'] == 'built' for pg in j['pages']):
        i = next(n for n, pg in enumerate(j['pages']) if pg['id'] == 'live') + 1
        j['pages'].insert(i, dict(id='built', name='נבנה · שלב א באנדרואיד'))
    open(p, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('wrote', len(BOARDS), 'boards')


if __name__ == '__main__': main()
