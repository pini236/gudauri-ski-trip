#!/usr/bin/env python3
"""New design language: lays out canvas/project/canvas.json for all boards (the boards themselves are written by hand).
Pages: open (Main + the five identity sheets), parts (core and domain sheets, one row each, a column per language),
phone (a row per language, the same screen in each column), desk (desktop home per language), today (the site now).
Board sizes come from each board's $preview. Run from the repo root: python3 design/language/index.py"""
import json, re, pathlib, datetime
P = pathlib.Path(__file__).resolve().parent / 'canvas/project'
idx_path = P / 'canvas.json'
old = json.loads(idx_path.read_text(encoding='utf-8'))
L = json.loads((P.parent.parent / 'languages.json').read_text(encoding='utf-8'))['langs']
def size(f):
    pv = json.loads(re.search(r"data-props='([^']*)'", (P / f).read_text(encoding='utf-8')).group(1))['$preview']
    return pv['width'], pv['height']
def name(code): return f'{code} · {L[code]["he"]}'
boards, order, notes = {}, [], {}
def put(f, x, y, page, title):
    w, h = size(f); boards[f] = {'x': x, 'y': y, 'w': w, 'h': h, 'page': page, 'title': title}; order.append(f); return w, h
GAP, ROW, NOTE = 80, 120, 300
codes = sorted(L)
# open
put('Main.dc.html', 0, 0, 'open', 'איך לקרוא ומה לבחור')
x = 840
for c in codes:
    w, _ = put(f'{c}0-Identity.dc.html', x, 0, 'open', f'{name(c)}: זהות'); x += w + GAP
notes['openrow'] = {'x': 840, 'y': -NOTE, 'text': 'חמש שפות עיצוביות', 'kind': 'title1', 'maxW': x - 840 - GAP, 'page': 'open'}
# parts
y = 0
for key, label, num in (('core', 'רכיבים: הבסיס', 1), ('domain', 'רכיבים: של גודאורי', 2)):
    x, hmax = 0, 0
    for c in codes:
        f = next(P.glob(f'{c}{num}-*.dc.html')).name
        w, h = put(f, x, y, 'parts', f'{name(c)}: {label}'); x += w + GAP; hmax = max(hmax, h)
    notes[f'parts{num}'] = {'x': 0, 'y': y - NOTE, 'text': label, 'kind': 'title1', 'maxW': x - GAP, 'page': 'parts'}
    y += hmax + ROW + NOTE
# phone
SCREENS = [('3', 'בית'), ('4', 'בית בלילה'), ('5', 'מפה'), ('6', 'Tatra 2'), ('7', 'נקודת מפגש'), ('8', 'משחקים'), ('9', 'קבוצה'), ('10', 'הגדרות')]
y = 0
for c in codes:
    x = 0
    for n, t in SCREENS:
        f = next(P.glob(f'{c}{n}-*.dc.html')).name
        w, h = put(f, x, y, 'phone', f'{c}: {t}'); x += w + GAP
    notes[f'phone{c}'] = {'x': 0, 'y': y - NOTE, 'text': name(c), 'kind': 'title1', 'maxW': x - GAP, 'page': 'phone'}
    y += 844 + ROW + NOTE
# desk
y = 0
for c in codes:
    put(f'{c}11-Desktop.dc.html', 0, y, 'desk', f'{name(c)}: מחשב')
    notes[f'desk{c}'] = {'x': 0, 'y': y - NOTE, 'text': name(c), 'kind': 'title1', 'maxW': 1280, 'page': 'desk'}
    y += 800 + ROW + NOTE
# today: keep as it was
for f, b in old['boards'].items():
    if b.get('page') == 'today': boards[f] = b; order.append(f)
for k, v in old['notes'].items():
    if v.get('page') == 'today': notes[k] = v
idx = {**old, 'boards': boards, 'order': order, 'notes': notes, 'launch': {'view': 'canvas', 'page': 'open'},
       'pages': [{'id': 'open', 'name': 'פתיחה: חמש שפות'}, {'id': 'parts', 'name': 'רכיבים'}, {'id': 'phone', 'name': 'מסכים בטלפון'},
                 {'id': 'desk', 'name': 'מחשב'}, {'id': 'today', 'name': 'היום באתר'}]}
idx_path.write_text(json.dumps(idx, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(len(boards), 'boards')
