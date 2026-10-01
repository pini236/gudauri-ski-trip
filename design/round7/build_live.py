#!/usr/bin/env python3
"""Canvas page "באתר עכשיו": screenshots of the real site after an implementation,
back on the canvas (the "closing the loop" step in docs/FEATURES.md).
The images are uploaded to the canvas as assets; their urls are below.
Run from the repo root: python3 design/round7/build_live.py"""
import pathlib
root = pathlib.Path(__file__).resolve().parent.parent.parent
SHOTS = [
    ('L1-GamesPhone.dc.html', 'עמוד המשחקים בטלפון', '/_blob/345e208038a53bfc64b97c1d77666d9b', 390, 844),
    ('L2-GamesDesktop.dc.html', 'עמוד המשחקים במחשב', '/_blob/ab99b957bdc16f5b0e9482a24e4ab133', 1280, 800),
    ('L5-MeetPhone.dc.html', 'נקודת מפגש בלי בחירה', '/_blob/41ce55c8b5a3075a59128e4d8e91e4e4', 390, 844),
    ('L3-HomePhone.dc.html', 'דף הבית עם הכרטיס החדש', '/_blob/af57868db33584f20e781fc7ccfa4f1b', 390, 844),
    ('L4-HomePhoneNight.dc.html', 'דף הבית בלילה', '/_blob/16b9598bef2c564a43959a09b7c4514f', 390, 844),
]


def page(title, src, w, h):
    return f'''<!doctype html>
<html lang="he" dir="rtl">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<style>
body{{margin:0;background:#EEF2F5}}
</style>
</helmet>
<div style="width: {w}px; height: {h}px; overflow: hidden; background: #EEF2F5">
<img src="{src}" alt="{title}, צילום מסך של האתר" style="display: block; width: {w}px; height: auto">
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
class Component extends DCLogic {{
  renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
'''


if __name__ == '__main__':
    dst = root / 'design/canvas/project'
    for n, t, src, w, h in SHOTS:
        (dst / n).write_text(page(t, src, w, h), encoding='utf-8')
    print([s[0] for s in SHOTS])
