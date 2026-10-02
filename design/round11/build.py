#!/usr/bin/env python3
"""Round 11 design screens: a visible language picker on the site (decision 18: canvas before code).

The site already picks its language from the browser (site/js/i18n.js, decision 39). These screens show where a
person can change it by hand. Three places, one canvas page "סבב 11: בורר שפה":
  LP1  bottom of the home page, Hebrew: four small signs, each language written in its own script.
  LP2  the same at the bottom of the English home page (left to right, LT1).
  LP3  the about and settings page: a row "שפה" like "תצוגה", opening a sheet with the four languages.
  LP4  the top bar: a short code next to the gear (עב / EN), opening the same list.
Reuses the pieces of round 10. Run from the repo root: python3 design/round11/build.py [canvas folder]."""
import json, pathlib, runpy, sys

root = pathlib.Path(__file__).resolve().parent.parent.parent
R = runpy.run_path(str(root / 'design/round10/build.py'))
C, DISP, page, FONTS_INTL, icon = R['C'], R['DISP'], R['page'], R['FONTS_INTL'], R['icon']
BODY_HE = "font-family: 'IBM Plex Sans Hebrew', 'Segoe UI', system-ui, sans-serif;"
BODY_EN = "font-family: 'IBM Plex Sans', 'Segoe UI', system-ui, sans-serif;"

# each language in its own script and its own title font (round 10, FT1)
LANGS = [('he', 'עברית', DISP, 'rtl'),
         ('en', 'English', DISP, 'ltr'),
         ('ru', 'Русский', "font-family: Oswald, 'Arial Narrow', sans-serif; font-weight: 600;", 'ltr'),
         ('ka', 'ქართული', "font-family: 'Noto Sans Georgian', sans-serif; font-weight: 800; font-stretch: 62.5%;", 'ltr')]


def lang_signs(cur, ltr=False):
    """Four small trail signs in a row. The current one is filled; the arrow points forward in the page's direction."""
    out = []
    for code, name, font, d in LANGS:
        on = code == cur
        clip = ('polygon(0 0, calc(100% - 12px) 0, 100% 50%, calc(100% - 12px) 100%, 0 100%)' if ltr
                else 'polygon(0 50%, 12px 0, 100% 0, 100% 100%, 12px 100%)')
        pad = '0 20px 0 10px' if ltr else '0 10px 0 20px'
        bg, fg = (C['ink'], '#fff') if on else (C['paper'], C['ink'])
        out.append(f'<a href="#" lang="{code}" dir="{d}" aria-current="{"true" if on else "false"}" '
                   f'style="display: inline-flex; align-items: center; min-height: 44px; padding: {pad}; background: {bg}; color: {fg}; '
                   f'clip-path: {clip}; text-decoration: none; {font} font-size: 24px; line-height: 1; '
                   f'{"" if on else "box-shadow: inset 0 0 0 1.5px " + C["rule"] + ";"}">{name}</a>')
    return f'<nav aria-label="{"Language" if ltr else "שפה"}" style="display: flex; flex-wrap: wrap; gap: 8px">{"".join(out)}</nav>'


def home_bottom(ltr=False):
    he = not ltr
    t = (lambda h, e: h if he else e)
    tally = ''.join(f'<li style="display: flex; flex-direction: column; gap: 2px; border-top: 6px solid {c}; padding-top: 6px">'
                    f'<b style="{DISP} font-size: 38px; line-height: .9">{n}</b><span style="font-size: 12.5px; color: {C["muted"]}">{w}</span></li>'
                    for n, w, c in [(5, t('ירוק', 'Green'), C['green']), (16, t('כחול', 'Blue'), C['blue']),
                                    (4, t('אדום', 'Red'), C['red']), (2, t('שחור', 'Black'), C['ink'])])
    crew = ''.join(f'<span style="display: inline-flex; align-items: center; min-height: 36px; padding: 0 12px; background: {C["paper"]}; border-top: 3px solid {C["ink"]}; font-size: 14px" dir="rtl">{n}</span>'
                   for n in R['CREW'])
    hint = t('האתר בחר שפה לפי הדפדפן. אפשר לשנות כאן, והבחירה נשמרת בטלפון.',
             'The site chose a language from your browser. You can change it here; the choice stays on this phone.')
    return (f'<div style="padding: 24px 20px 30px; display: flex; flex-direction: column; gap: 28px">'
            f'<section><h2 style="margin: 0 0 10px; font-size: 13px; font-weight: 400; color: {C["muted"]}">{t("מסלולים במפה הרשמית", "Runs on the official map")}</h2>'
            f'<ul style="list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px">{tally}</ul></section>'
            f'<section><h2 style="margin: 0 0 10px; font-size: 13px; font-weight: 400; color: {C["muted"]}">{t("החבר׳ה · 6", "The crew · 6")}</h2>'
            f'<div style="display: flex; flex-wrap: wrap; gap: 8px">{crew}</div></section>'
            f'<section style="padding: 16px; background: {C["paper2"]}; border: 2px dashed {C["gold"]}">'
            f'<h2 style="margin: 0 0 4px; {DISP} font-size: 28px; line-height: 1">{t("שפה", "Language")}</h2>'
            f'<p style="margin: 0 0 12px; font-size: 13px; color: {C["muted"]}">{hint}</p>{lang_signs("he" if he else "en", ltr)}</section>'
            f'<a href="#" style="font-size: 14px; font-weight: 700; color: {C["muted"]}">{t("אודות, הגדרות וקרדיטים", "About, settings and credits")}</a></div>')


def lp(ltr):
    body = BODY_EN if ltr else BODY_HE
    d = 'ltr' if ltr else 'rtl'
    tag = ('Bottom of the home page' if ltr else 'תחתית דף הבית')
    return (f'<div lang="{"en" if ltr else "he"}" dir="{d}" style="width: 390px; height: 844px; box-sizing: border-box; position: relative; overflow: hidden; '
            f'background: {C["snow"]}; color: {C["ink"]}; {body}">'
            f'<div style="padding: 10px 20px; background: {C["ink"]}; color: #fff; font-size: 12px; font-weight: 700">{tag} ↓</div>'
            f'{home_bottom(ltr)}</div>')


def settings_row(label, sub, val):
    return (f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 60px; padding: 10px 0; border-bottom: 1px solid {C["rule"]}">'
            f'<span style="display: flex; flex-direction: column; gap: 2px"><b style="font-size: 16px">{label}</b><small style="font-size: 13px; color: {C["muted"]}">{sub}</small></span>'
            f'<span style="font-weight: 700; color: {C["blue"]}">{val}</span></div>')


def lang_list(cur):
    rows = []
    for code, name, font, d in LANGS:
        on = code == cur
        rows.append(f'<label lang="{code}" dir="{d}" style="display: flex; align-items: center; justify-content: space-between; min-height: 56px; padding: 0 14px; '
                    f'border: 2px solid {C["ink"] if on else C["rule"]}; background: {C["paper"]}; cursor: pointer">'
                    f'<span style="{font} font-size: 28px; line-height: 1">{name}</span>'
                    f'<input type="radio" name="lang" {"checked" if on else ""} style="width: 22px; height: 22px; accent-color: {C["ink"]}"></label>')
    return f'<div role="radiogroup" style="display: flex; flex-direction: column; gap: 8px">{"".join(rows)}</div>'


def lp3():
    under = (f'<div style="padding: 12px 16px 6px; display: flex; justify-content: space-between; align-items: center">'
             f'<h1 style="margin: 0; {DISP} font-size: 44px; line-height: 1">אודות והגדרות</h1>'
             f'<a href="#" style="font-weight: 700; color: {C["blue"]}; text-decoration: none">בית</a></div>'
             f'<div style="padding: 0 20px"><h2 style="{DISP} font-size: 30px; margin: 14px 0 4px">הגדרות</h2>'
             + settings_row('צליל', 'בכרטיס הטיסה ובמשחקים', 'פועל')
             + settings_row('רטט', 'בטלפונים שתומכים (לא באייפון)', 'פועל')
             + settings_row('תצוגה', 'אוטומטי לפי השעה בגודאורי, יום או לילה', 'אוטומטי')
             + f'<div style="outline: 2px dashed {C["gold"]}; outline-offset: 2px">' + settings_row('שפה · Language', 'נבחרה לפי הדפדפן', 'עברית') + '</div>'
             + settings_row('איפוס השיאים במשחקים', 'השיאים נשמרים רק בטלפון הזה', '') + '</div>')
    sheet = R['sheet'](under, f'<h2 style="margin: 0 0 4px; {DISP} font-size: 32px">שפה</h2>'
                       f'<p style="margin: 0 0 14px; font-size: 13.5px; color: {C["muted"]}">כל שפה כתובה בכתב שלה, כדי שכל אחד ימצא את שלו. הבחירה נשמרת בטלפון הזה.</p>'
                       + lang_list('he') +
                       f'<a href="#" style="margin-top: 14px; font-size: 14px; color: {C["blue"]}">לפי הדפדפן (אוטומטי)</a>', top=440)
    return f'<div lang="he" dir="rtl" style="width: 390px; height: 844px; position: relative; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {BODY_HE}">{sheet}</div>'


def lp4():
    gear = icon('gear', 22)
    bar = (f'<div style="display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 14px; background: {C["paper"]}; border-bottom: 1px solid {C["rule"]}">'
           f'<b style="{DISP} font-size: 30px; line-height: 1">גודאורי 2027</b>'
           f'<span style="display: flex; align-items: center; gap: 6px">'
           f'<span style="outline: 2px dashed {C["gold"]}; outline-offset: 2px; display: inline-flex; align-items: center; justify-content: center; min-width: 44px; height: 44px; '
           f'border: 1.5px solid {C["ink"]}; font-weight: 700; font-size: 15px">עב ▾</span>'
           f'<span style="display: inline-flex; align-items: center; justify-content: center; width: 44px; height: 44px; font-size: 22px">{gear}</span></span></div>')
    drop = (f'<div style="position: absolute; top: 66px; left: 14px; width: 220px; background: {C["paper"]}; box-shadow: 0 10px 26px rgba(19,35,58,.25); padding: 8px; display: flex; flex-direction: column; gap: 6px">'
            + lang_list('he').replace('min-height: 56px', 'min-height: 48px').replace('font-size: 28px', 'font-size: 24px') + '</div>')
    rest = (f'<div style="padding: 24px 20px; color: {C["muted"]}; font-size: 14px">הקוד הקצר תמיד בסרגל: עב, EN, RU, KA. '
            f'בטלפון הסרגל כבר צפוף (שם, ספירה לאחור, שעון, יום ולילה, גלגל שיניים), ולכן זה המקום הכי גלוי, וגם הכי עמוס.</div>')
    return f'<div lang="he" dir="rtl" style="width: 390px; height: 844px; position: relative; overflow: hidden; background: {C["snow"]}; color: {C["ink"]}; {BODY_HE}">{bar}{rest}{drop}</div>'


BOARDS = [
    ('LP1-HomeBottom.dc.html', 'בורר בתחתית דף הבית', page('בורר שפה: תחתית דף הבית', lp(False), fonts=FONTS_INTL)),
    ('LP2-HomeBottomEn.dc.html', 'אותו בורר באנגלית', page('Language picker: bottom of the home page', lp(True), fonts=FONTS_INTL, lang='en', d='ltr')),
    ('LP3-Settings.dc.html', 'שורה בהגדרות, עם רשימה', page('בורר שפה: בהגדרות', lp3(), fonts=FONTS_INTL)),
    ('LP4-TopBar.dc.html', 'קוד קצר בסרגל העליון', page('בורר שפה: בסרגל העליון', lp4(), fonts=FONTS_INTL)),
]
PAGE = ('round11', 'סבב 11: בורר שפה')

if __name__ == '__main__':
    dst = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'design/canvas/project'
    ip = dst / 'canvas.json'
    index = json.loads(ip.read_text(encoding='utf-8'))
    if not any(p['id'] == PAGE[0] for p in index['pages']):
        index['pages'].insert(0, {'id': PAGE[0], 'name': PAGE[1]})
    x = 0
    for name, title, html_ in BOARDS:
        (dst / name).write_text(html_, encoding='utf-8')
        index['boards'][name] = {'x': x, 'y': 0, 'w': 390, 'h': 844, 'page': PAGE[0], 'title': title}
        if name not in index['order']:
            index['order'].append(name)
        x += 470
    index['notes']['round11row0'] = {'kind': 'title1', 'maxW': 1800, 'page': PAGE[0], 'w': 240, 'x': 0, 'y': -300,
                                     'text': 'בורר שפה גלוי: איפה? ההמלצה: LP1 ו-LP3 ביחד (בתחתית דף הבית ובהגדרות). LP4 הכי גלוי אבל מעמיס את הסרגל'}
    index['launch'] = {'view': 'canvas', 'page': PAGE[0]}
    ip.write_text(json.dumps(index, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(len(BOARDS), 'boards ->', dst)
