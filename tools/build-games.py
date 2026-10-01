"""Copies the approved games (decision 17 in docs/STATUS.md) into the site, as site/games/<slug>/index.html.

Each game is still made in design/ (its source, data and build script stay there). This script runs the
game's own build when it has one, adds a "back to the site" link, and writes the result into the site.
Run it after every change to a game:

    python3 tools/build-games.py
"""
import pathlib, subprocess, sys

root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'site' / 'games'

# slug, source folder, and where the back link goes: (text to find, 'after' or 'before', extra style)
GAMES = [
    ('descent', 'design/round4/game', ('<section class="menu" id="menuStart" aria-label="התחלה">', 'after', '')),
    ('school', 'design/games/school', ('<section class="menu" id="menu" aria-label="שיעורים">', 'after', '')),
    ('fresh-snow', 'design/games/powder-touch', ('<div class="row kind" id="scenes" role="group" aria-label="איפה">', 'after', 'min-height:40px')),
    ('snowball', 'design/games/snowball', ('<section class="menu" id="menuStart" aria-label="התחלה">', 'after', '')),
    ('merge', 'design/games/merge', ('<div class="row"><button id="undo"', 'inside-row', '')),
]

STYLE = ('<style>.back-site{display:inline-flex;align-items:center;gap:6px;min-height:44px;padding:0 14px;'
         'background:rgba(19,35,58,.88);color:#fff;font:700 14px "IBM Plex Sans Hebrew",system-ui,sans-serif;'
         'text-decoration:none;align-self:flex-start;flex:0 0 auto;box-sizing:border-box}'
         '.back-site:focus-visible{outline:3px solid #F4B942;outline-offset:2px}</style>')
HEAD = ('<!doctype html>\n<html lang="he" dir="rtl">\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">\n'
        '<meta name="robots" content="noindex">\n<meta name="theme-color" content="#13233A">\n'
        '<link rel="icon" href="../../favicon.svg" type="image/svg+xml">\n'
        '<style>[hidden]{display:none!important}</style>\n'
        '<script src="../../js/prefs.js"></script>\n')
LINK = '<a class="back-site" href="../../#games" style="{style}">→ לכל המשחקים</a>'


def build(slug, src, place):
    folder = root / src
    if (folder / 'build.py').exists():
        subprocess.run([sys.executable, 'build.py'], cwd=folder, check=True, stdout=subprocess.DEVNULL)
    html = (folder / 'index.html').read_text()
    find, how, style = place
    assert html.count(find) == 1, f'{slug}: the anchor for the back link was not found once'
    link = LINK.format(style=style)
    if how == 'after':
        html = html.replace(find, find + '\n  ' + link)
    else:  # the first thing inside a row of buttons
        html = html.replace(find, find.replace('<div class="row">', '<div class="row">' + link))
    # a full document head: the games were written for the artifact viewer, which adds these itself
    html = HEAD + html
    # the link's style goes right after the page's own styles, so the title stays in the first lines
    html = html.replace('</style>', '</style>\n' + STYLE, 1)
    dest = out / slug
    dest.mkdir(parents=True, exist_ok=True)
    (dest / 'index.html').write_text(html)
    print(f'{slug}: {len(html) // 1024} KB')


if __name__ == '__main__':
    for g in GAMES:
        build(*g)
