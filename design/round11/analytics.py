#!/usr/bin/env python3
"""Round 11, second page: the switch for usage statistics and error reports on the site (decision 37, docs/GROWTH.md).
The about and settings page as it is on the site, with one new row under "language". Off = nothing is sent.
Run from the repo root: python3 design/round11/analytics.py [canvas folder]."""
import json, pathlib, runpy, sys

root = pathlib.Path(__file__).resolve().parent.parent.parent
B = runpy.run_path(str(root / 'design/round11/build.py'))
C, DISP, page, FONTS_INTL = B['C'], B['DISP'], B['page'], B['FONTS_INTL']
row, BODY_HE = B['settings_row'], B['BODY_HE']


def toggle_row(label, sub, on, mark=False):
    sw = (f'<span style="position: relative; flex: none; width: 52px; height: 30px; background: {C["green"] if on else C["rule"]}">'
          f'<span style="position: absolute; top: 3px; {"left" if on else "right"}: 3px; width: 24px; height: 24px; background: #fff"></span></span>')
    inner = (f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 64px; padding: 10px 0; border-bottom: 1px solid {C["rule"]}">'
             f'<span style="display: flex; flex-direction: column; gap: 2px"><b style="font-size: 16px">{label}</b><small style="font-size: 13px; color: {C["muted"]}">{sub}</small></span>{sw}</div>')
    return f'<div style="outline: 2px dashed {C["gold"]}; outline-offset: 2px">{inner}</div>' if mark else inner


def an1(on=True):
    return (f'<div lang="he" dir="rtl" style="width: 390px; height: 844px; position: relative; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {BODY_HE}">'
            f'<div style="padding: 12px 16px 6px; display: flex; justify-content: space-between; align-items: center">'
            f'<h1 style="margin: 0; {DISP} font-size: 44px; line-height: 1">אודות והגדרות</h1>'
            f'<a href="#" style="font-weight: 700; color: {C["blue"]}; text-decoration: none">בית</a></div>'
            f'<div style="padding: 0 20px"><h2 style="{DISP} font-size: 30px; margin: 14px 0 4px">הגדרות</h2>'
            + toggle_row('צליל', 'בכרטיס הטיסה ובמשחקים', True)
            + toggle_row('רטט', 'בטלפונים שתומכים (לא באייפון)', True)
            + row('תצוגה', 'אוטומטי לפי השעה בגודאורי, יום או לילה', 'אוטומטי')
            + row('שפה · Language', 'נבחרה לפי הדפדפן', 'עברית')
            + toggle_row('מדידת שימוש ודיווח שגיאות', ('אנונימי ובלי עוגיות: באילו עמודים משתמשים ומה נשבר. ' if on else 'כבוי: לא נשלח דבר. ') + '<a href="#">מה נאסף</a>', on, mark=True)
            + row('איפוס השיאים במשחקים', 'השיאים נשמרים רק בטלפון הזה', '') + '</div></div>')


BOARDS = [('AN1-AnalyticsOn.dc.html', 'מדידה: דלוק (ברירת המחדל)', page('הגדרות: מדידת שימוש', an1(True), fonts=FONTS_INTL)),
          ('AN2-AnalyticsOff.dc.html', 'מדידה: כבוי', page('הגדרות: מדידה כבויה', an1(False), fonts=FONTS_INTL))]
PAGE = ('round11a', 'סבב 11: מדידה באתר')

if __name__ == '__main__':
    dst = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'design/canvas/project'
    ip = dst / 'canvas.json'
    index = json.loads(ip.read_text(encoding='utf-8'))
    if not any(p['id'] == PAGE[0] for p in index['pages']):
        index['pages'].insert(0, {'id': PAGE[0], 'name': PAGE[1]})
    for i, (name, title, html_) in enumerate(BOARDS):
        (dst / name).write_text(html_, encoding='utf-8')
        index['boards'][name] = {'x': i * 470, 'y': 0, 'w': 390, 'h': 844, 'page': PAGE[0], 'title': title}
        if name not in index['order']:
            index['order'].append(name)
    index['notes']['round11arow0'] = {'kind': 'title1', 'maxW': 860, 'page': PAGE[0], 'w': 240, 'x': 0, 'y': -300,
                                      'text': 'מתג למדידה ולשגיאות בהגדרות האתר'}
    index['launch'] = {'view': 'canvas', 'page': PAGE[0]}
    ip.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(len(BOARDS), 'boards ->', dst)
