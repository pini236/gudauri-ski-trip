#!/usr/bin/env python3
"""The run picker in the bottom bar (27.11.2026, Pini): "לאן יורדים?" with a magnifier looks like a text field,
but it opens the shelf of runs. Three alternatives that read as "pick a run from a list", and a recommendation.
A new canvas page "הסרגל: בחירת מסלול". Design only. Handoff: design/handoff/2026-11-27-redesign-dock-runs/.
Usage: python3 design/redesign/tools/dk_boards.py [canvas.json path, default the repo copy] [--preview <dir> <assets dir>]"""
import os, sys, json, re
import ux_boards as UX
from ux_boards import ic, P_GRID, mountain, ctl_sw, rc, wb, li, PROJ, PB, G4, P
from v56_boards import P_SRCH

PAGE = 'dock'
NRUN = len([p for p in P.D['pistes'] if p.get('named')])
BYC = {c: len([p for p in P.D['pistes'] if p.get('named') and p['color'] == c]) for c in ('green', 'blue', 'red', 'black')}
P_UP = '<path d="m6 14.500 6-6 6 6"/>'

DCSS = """
.H .dock3 .rn{flex:1;min-width:0;height:44px;border-radius:22px;border:1px solid var(--line);background:var(--tile);display:flex;align-items:center;gap:9px;padding:0 12px 0 10px;font-size:15px;font-weight:700;color:var(--ink);white-space:nowrap}
.H .dock3 .rn .cnt{font-size:12.5px;font-weight:700;color:var(--ink2);height:22px;min-width:26px;padding:0 6px;box-sizing:border-box;border-radius:11px;background:var(--line);display:inline-flex;align-items:center;justify-content:center;font-variant-numeric:tabular-nums}
.H .dock3 .rn .up{margin-inline-start:auto;display:flex;color:var(--ink2)}
.H .pd{display:grid;grid-template-columns:8px 8px;gap:3px;flex:none}
.H .pd i{width:8px;height:8px;border-radius:50%}
.H .dock3 .cb{flex:1;min-width:0;height:44px;border-radius:14px;border:1px solid var(--line);background:var(--tile);display:flex;align-items:center;justify-content:center;gap:6px;font-size:15px;font-weight:700;font-variant-numeric:tabular-nums}
.H .dock3 .cb i{width:12px;height:12px;border-radius:50%;flex:none}
.H .peek{position:absolute;left:0;right:0;bottom:80px;height:44px;display:flex;gap:10px;justify-content:center;overflow:hidden}
.H .peek .pc2{width:176px;height:64px;flex:none;border-radius:20px 20px 0 0;display:flex;align-items:flex-start;gap:8px;padding:11px 14px 0;box-sizing:border-box;font-size:14.5px;font-weight:700;direction:ltr;border-bottom:0}
.H .peek .pc2.s{opacity:.72}
.H .pk{position:absolute;left:50%;transform:translateX(-50%);bottom:128px;height:26px;padding:0 12px;border-radius:13px;display:flex;align-items:center;gap:6px;font-size:12.5px;font-weight:700}
.H .old{position:absolute;left:12px;right:12px;top:106px;border-radius:16px;padding:8px 12px;font-size:13px;line-height:1.4}
"""
G4.MB.CSS = G4.MB.CSS + DCSS

DOTS = '<span class="pd" aria-hidden="true"><i style="background: var(--green)"></i><i style="background: var(--blue)"></i><i style="background: var(--red)"></i><i style="background: var(--black)"></i></span>'


def menu_btn(L='he'):
    return '<button class="mn">%s %s</button>' % (ic(P_GRID, 19, 2), 'Menu' if L == 'en' else 'תפריט')


def dock_today():
    return '<div class="dock3 glass">%s<div class="find">%s<span>לאן יורדים?</span></div>%s</div>' % (menu_btn(), ic(P_SRCH, 18), rc())


def dock_a(L='he', rec=None):
    w = 'Runs' if L == 'en' else 'מסלולים'
    return ('<div class="dock3 glass">%s<button class="rn" aria-label="%s">%s<span>%s</span><span class="cnt">%d</span><span class="up">%s</span></button>%s</div>'
            % (menu_btn(L), w, DOTS, w, NRUN, ic(P_UP, 18, 2.2), rec if rec is not None else rc()))


def dock_b():
    cols = ''.join('<button class="cb" aria-label="%s, %d"><i style="background: var(--%s)"></i>%d</button>' % (PB.HE[c], BYC[c], c, BYC[c]) for c in ('green', 'blue', 'red', 'black'))
    return '<div class="dock3 glass" style="gap: 6px">%s%s%s</div>' % (menu_btn(), cols, rc())


def dock_c():
    cards = ''.join('<div class="pc2 glass %s">%s<span>%s</span></div>' % (cl, PB.sign(k), k) for k, cl in (('Goodaura 1', 's'), ('Tatra 2', ''), ('Soliko 1', 's')))
    return ('<div class="pk glass">%s %d מסלולים</div><div class="peek">%s</div>' % (ic(P_UP, 15, 2.4), NRUN, cards)
            + '<div class="dock3 glass" style="background: var(--glass)">%s<span style="flex: 1"></span>%s</div>' % (menu_btn(), rc()))


def col(bottom=84):
    return ctl_sw('pic').replace('class="mc"', 'class="mc" style="bottom: %dpx"' % bottom)


def en_root():
    return UX.root_en() + ctl_sw('pic').replace('class="mc"', 'class="mc" style="left: auto; right: 12px"')


def phones():
    return {
        'DK1.dc.html': ('היום: נראה כמו שדה חיפוש', mountain() + col() + dock_today(), None),
        'DK2.dc.html': ('א (מומלץ): כפתור "מסלולים"', mountain() + col() + dock_a(), None),
        'DK3.dc.html': ('א בלילה, בזמן הקלטה', mountain() + col() + dock_a(rec=rc('live')), 'night'),
        'DK4.dc.html': ('A in English: Runs', en_root() + dock_a('en'), 'day', 'en'),
        'DK5.dc.html': ('ב: ארבעת הצבעים', mountain() + col() + dock_b(), None),
        'DK6.dc.html': ('ג: המסלולים מציצים מלמטה', mountain() + col(136) + dock_c(), None),
    }


HB = 1180


def dk00():
    opts = li('<b>א. כפתור "מסלולים" (מומלץ).</b> מילה אחת שאומרת מה נפתח, ארבע נקודות בצבעי המסלולים, המספר 28, וחץ למעלה שאומר "נפתח מכאן". מילוי ומשקל של כפתור, כמו "תפריט", ולא של שדה. (DK2 עד DK4)',
              '<b>ב. ארבעת הצבעים.</b> ארבעה כפתורים קטנים, אחד לכל צבע, עם מספר המסלולים. נגיעה פותחת את הרשימה בצבע הזה. מהיר למי שמכיר, אבל בלי אף מילה, וארבע מטרות צפופות ליד כפתור ההקלטה. (DK5)',
              '<b>ג. המסלולים מציצים מלמטה.</b> ראשי שלושה כרטיסים מעל הסרגל, עם לשונית "28 מסלולים". רואים שזו רשימה עוד לפני שנוגעים, אבל זה מכסה עוד 60 נקודות מההר כל הזמן, ודוחף את עמודת הכפתורים למעלה. (DK6)')
    why = li('<b>זכוכית מגדלת, מילוי אפור וטקסט אפור הם הסימנים של שדה הקלדה.</b> מי שנוגע מצפה למקלדת, ומקבל כרטיסים.',
             '<b>"לאן יורדים?" היא שאלה, לא שם.</b> לפי הכלל שאושר במעבר חוויית המשתמש, כל שם אומר מה נפתח: "תפריט", "משחקים", ועכשיו "מסלולים".',
             '<b>אין חיפוש בהקלדה</b> (החלטה 50), ולכן אין סיבה שהכניסה תיראה כמו חיפוש.')
    spec = li('<b>הכפתור:</b> גובה 44, רדיוס 22, רקע <span dir="ltr">tile</span>, מסגרת 1 <span dir="ltr">line</span>, ממלא את הרוחב שבין "תפריט" להקלטה. טקסט 15 במשקל 700 בצבע <span dir="ltr">ink</span>.',
              '<b>בפנים, מההתחלה לסוף:</b> ארבע נקודות 8 בשתי שורות (ירוק, כחול, אדום, שחור); "מסלולים"; המספר בגלולה קטנה (גובה 22, רקע <span dir="ltr">line</span>, 12.5 במשקל 700); ובקצה חץ למעלה 18 בצבע <span dir="ltr">ink2</span>.',
              '<b>נגיעה:</b> פותחת את המדף, בדיוק כמו היום. כשהמדף פתוח הסרגל נעלם, כמו היום.',
              '<b>בזמן הקלטה</b> כפתור ההקלטה מתרחב עם השעון (DK3): המספר נשאר, ואם אין מקום (אנגלית, טקסט גדול) המספר יורד ראשון, ואחריו הנקודות. המילה והחץ נשארים תמיד.',
              '<b>המספר</b> הוא המסלולים עם שם בנתונים (היום 28). בעדשת "פתוח" עם דיווח עדכני: מספר הפתוחים.',
              '<b>הרמז השני</b> ("כל המסלולים ברשימה אחת") מצביע על הכפתור הזה, בלי שינוי בטקסט.',
              '<b>קורא מסך:</b> "מסלולים, 28. פתיחת הרשימה".')
    asks = ''.join('<p class="q"><span class="qn">%d</span><span>%s</span></p>' % (i + 1, t) for i, t in enumerate([
        '<b>איזו חלופה?</b> (ההמלצה: א)',
        '<b>המילה: "מסלולים"</b> במקום "לאן יורדים?". (כן; הניסוח הסופי דרך סשן התוכן)',
        '<b>המספר 28 על הכפתור?</b> (כן; הוא מה שהופך אותו מכותרת לרשימה)']))
    inner = ('<p class="sec">הסרגל התחתון: בחירת מסלול · 27.11.2026 · ממתין לפיני</p>'
             '<h1>"לאן יורדים?" נראה כמו שדה חיפוש, אבל פותח רשימה. שלוש דרכים לתקן</h1>'
             '<p class="lead">הכניסה לרשימת המסלולים צריכה להיראות כמו כפתור שפותח רשימה, ולהיקרא בשם של מה שיש בה.</p>'
             '<div class="rowx" style="align-items: stretch"><div class="card" style="flex: 1"><h2>למה זה מבלבל</h2>%s</div>'
             '<div class="card" style="flex: 1.25"><h2>שלוש חלופות</h2>%s</div></div>'
             '<div class="card"><h2>א, בפירוט</h2>%s</div>'
             '<div class="card"><h2>לפיני</h2>%s<p class="cap">מספרי המסלולים מהנתונים של האתר (28 עם שם: %d ירוקים, %d כחולים, %d אדומים, %d שחורים). נמדד בכלי העיצוב, לא בטלפון. '
             'המסירה: <span dir="ltr">design/handoff/2026-11-27-redesign-dock-runs/</span></p></div>'
             % (why, opts, spec, asks, BYC['green'], BYC['blue'], BYC['red'], BYC['black']))
    wb('DK00-Runs.dc.html', 'הסרגל: בחירת מסלול', 1280, HB, inner)


FILES = ['DK00-Runs.dc.html'] + ['DK%d.dc.html' % i for i in range(1, 7)]


def write_canvas(cj):
    j = json.load(open(cj, encoding='utf8'))
    clash = [f for f in j['boards'] if f not in FILES and re.match(r'^DK\d', f)]
    assert not clash, clash
    for fn in FILES:
        j['boards'].pop(fn, None)
    j['order'] = [f for f in j['order'] if f not in FILES]
    T = phones()
    place = [('DK00-Runs.dc.html', 0, 0, 1280, HB, 'למה זה מבלבל, שלוש חלופות וההמלצה')]
    for k, fn in enumerate(FILES[1:]):
        place.append((fn, 470 * k, HB + 140, 390, 844, T[fn][0]))
    for fn, x, y, w, h, t in place:
        code = fn.split('.dc.html')[0].split('-')[0]
        j['boards'][fn] = dict(x=x, y=y, w=w, h=h, title='%s (%s)' % (t, code), page=PAGE)
        j['order'].append(fn)
    if not any(pg['id'] == PAGE for pg in j['pages']):
        j['pages'].insert(0, dict(id=PAGE, name='הסרגל: בחירת מסלול (27.11)'))
    j['notes']['t' + PAGE] = dict(kind='title1', maxW=1600, page=PAGE, x=0, y=-300, text='הסרגל התחתון: איך בוחרים מסלול')
    j['launch'] = dict(view='canvas', page=PAGE)
    open(cj, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')


def preview(out, assets):
    os.makedirs(out, exist_ok=True)
    loc = {os.path.splitext(f)[0]: os.path.join(assets, f) for f in os.listdir(assets)}
    for fn in FILES:
        s = open(os.path.join(PROJ, fn), encoding='utf8').read()
        s = s.replace('<script src="./support.js"></script>', '').replace('<x-dc>', '').replace('</x-dc>', '').replace('<helmet>', '').replace('</helmet>', '')
        s = re.sub(r'<script type="text/x-dc".*?</script>', '', s, flags=re.S)
        s = re.sub(r'/_blob/([0-9a-f]{32})', lambda m: 'file://' + loc.get(m.group(1), '/missing/' + m.group(1)), s)
        for mode in ('day', 'night'):
            open(os.path.join(out, fn.replace('.dc.html', '-%s.html' % mode)), 'w', encoding='utf8').write(s.replace('{{mode}}', mode))


def main():
    args = sys.argv[1:]
    pv = None
    if '--preview' in args:
        i = args.index('--preview'); pv = (args[i + 1], args[i + 2]); del args[i:i + 3]
    cj = args[0] if args else os.path.join(PROJ, 'canvas.json')
    for fn, v in phones().items():
        title, body, fx = v[:3]; lang = v[3] if len(v) > 3 else 'he'
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang=lang)
    dk00()
    write_canvas(cj)
    if pv: preview(*pv)
    print('ok', cj, NRUN, BYC)


if __name__ == '__main__':
    main()
