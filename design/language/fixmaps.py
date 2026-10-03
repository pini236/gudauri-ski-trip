#!/usr/bin/env python3
"""New design language: two fixes on the pasted map snippets in the boards (<L>5-Map, <L>6-Run, <L>7-Meet).
1. direction: ltr on the map's <svg>: inside an RTL page, SVG text is laid out right to left, so "⇡ Goodaura"
   and "▲ Sadzele 3307" were reordered and text-anchor="start" labels jumped to the other side of their line.
2. On a zoomed crop (the meet board), line widths, station dots and label sizes are in map units, so they grew
   with the zoom; they are scaled back by (crop width / full map width), so they look as on the full map.
Only attributes of the snippet's own <svg> change; the geometry is untouched.
Usage, from the repo root: python3 design/language/fixmaps.py A B C ..."""
import re, sys, pathlib
P = pathlib.Path(__file__).resolve().parent / 'canvas/project'
FULL = 4933
for L in sys.argv[1:]:
    for f in sorted(P.glob(f'{L}[567]-*.dc.html')):
        s = f.read_text(encoding='utf-8'); n0 = s
        def fix(m):
            svg = m.group(0)
            head = svg[:svg.index('>') + 1]
            vb = re.search(r'viewBox="([-\d.]+) ([-\d.]+) ([\d.]+) ([\d.]+)"', head)
            if 'direction' not in head:
                if 'style="' in head: head2 = head.replace('style="', 'style="direction: ltr; ', 1)
                else: head2 = head.replace('<svg ', '<svg style="direction: ltr" ', 1)
                svg = head2 + svg[len(head):]
            if vb and 'Tatra 2 על המפה' not in head:
                k = float(vb.group(3)) / FULL
                if k < 0.95 and 'data-scaled' not in head:
                    sc = lambda a: (lambda mm: f'{a}="{round(float(mm.group(1)) * k, 1)}"')
                    body = svg[svg.index('>') + 1:]
                    for a in ('stroke-width', 'font-size', 'r'):
                        body = re.sub(rf'(?<![\w-]){a}="([\d.]+)"', sc(a), body)
                    body = re.sub(r'stroke-dasharray="([\d.]+) ([\d.]+)"', lambda mm: f'stroke-dasharray="{round(float(mm.group(1))*k,1)} {round(float(mm.group(2))*k,1)}"', body)
                    svg = svg[:svg.index('>')] + ' data-scaled="1">' + body
            return svg
        s = re.sub(r'<svg [^>]*aria-label="(?:מפת המסלולים והרכבלים של גודאורי|Tatra 2 על המפה, צבוע לפי השיפוע)"[^>]*>.*?</svg>', fix, s, flags=re.S)
        if s != n0: f.write_text(s, encoding='utf-8'); print('fixed', f.name)
        else: print('no snippet found', f.name)
