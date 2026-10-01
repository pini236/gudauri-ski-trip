#!/usr/bin/env python3
"""Exports the chosen icon (round 5, no. 10 "the sign in the mountains", Pini 1.10.2026, without the run) as files:
  design/round9/export/store-icon-512.png       Google Play icon (512x512, full bleed, Play rounds the corners)
  design/round9/export/feature-1024x500.png     Google Play feature graphic (text rendered to pixels)
  design/round9/export/masks.png                the launcher icon under the common masks, to check the crop
  app/android/app/src/main/res/drawable-nodpi/  the adaptive launcher icon layers (432x432 = 108dp at xxxhdpi):
      ic_launcher_bg.png, ic_launcher_fg.png, ic_launcher_mono.png
The SVG sources are written next to them. Rendering uses the repo's Playwright and the preinstalled Chromium
(render.js). Run from the repo root: python3 design/round9/export.py"""
import pathlib, subprocess, sys, json
here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(here))
import build5 as b

OUT = here / 'export'
RES = root / 'app/android/app/src/main/res/drawable-nodpi'
FONT = (root / 'app/android/app/src/main/res/font/karantina_bold.ttf').as_uri()

# The launcher icon: 108dp canvas (432 px). Launchers show at least the centre 72dp (288 px), and keep the
# 66dp circle safe. The whole 512 scene is scaled to 0.6 so the peaks and the sign both fit inside that window.
S, OX, OY = 0.6, 62, 46


def wrap(w, h, body, defs=''):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"><defs>%s</defs>%s</svg>'
            % (w, h, w, h, defs, body))


def layers():
    defs, scene, sign = b.h1_parts()
    g = '<g transform="translate(%s %s) scale(%s)">%%s</g>' % (OX, OY, S)
    # the background continues past the scene: sky above, snow below (only seen while a launcher animates)
    ext = ('<rect width="432" height="432" fill="#5588CB"></rect>'
           '<rect y="%s" width="432" height="%s" fill="#D5E2F0"></rect>' % (OY + 500 * S, 432))
    bg = wrap(432, 432, ext + g % ''.join(scene), ''.join(defs))
    fg = wrap(432, 432, g % ''.join(sign), ''.join(defs))
    # monochrome (themed icons, Android 13+): the peaks above the forest line and the sign, one colour, arrow cut out
    peaks = [s.replace('></path>', ' stroke="#000" stroke-width="3" stroke-linejoin="round"></path>').replace('fill="url(#h1aL)"', 'fill="#000"').replace('fill="url(#h1aS)"', 'fill="#000"')
              .replace('fill="url(#h1bL)"', 'fill="#000"').replace('fill="url(#h1bS)"', 'fill="#000"')
              .replace('fill="url(#h1cL)"', 'fill="#000"').replace('fill="url(#h1cS)"', 'fill="#000"')
             for s in scene if 'url(#h1a' in s or 'url(#h1b' in s or 'url(#h1c' in s]
    mono_defs = ('<clipPath id="above"><rect width="512" height="300"></rect></clipPath>'
                 '<mask id="arrow"><rect width="512" height="512" fill="#fff"></rect>'
                 '<path d="M176 398 L222 366 V382 H356 V414 H222 V430 Z" fill="#000"></path></mask>')
    sign_mono = ''.join(x.replace('fill="#fff"', 'fill="#000"').replace('fill="%s"' % b.BLUE, 'fill="#000"').replace('fill="%s"' % b.INK, 'fill="#000"')
                        for x in sign if 'ellipse' not in x and 'opacity=".18"' not in x and 'M176 398' not in x)
    mono = wrap(432, 432, g % ('<g clip-path="url(#above)">%s</g><g mask="url(#arrow)">%s</g>' % (''.join(peaks), sign_mono)), mono_defs)
    return bg, fg, mono


def masks(bg, fg):
    """The launcher icon (bg + fg) under a circle, a squircle and a rounded square, at 72dp shown as 192 px."""
    inner = lambda s: s[s.index('>') + 1:s.rindex('</svg>')]
    cells = ''
    shapes = ['<circle cx="216" cy="216" r="144"></circle>',
              '<path d="M216 72 C 336 72, 360 96, 360 216 C 360 336, 336 360, 216 360 C 96 360, 72 336, 72 216 C 72 96, 96 72, 216 72 Z"></path>',
              '<rect x="72" y="72" width="288" height="288" rx="58"></rect>']
    for i, sh in enumerate(shapes):
        cells += ('<g transform="translate(%d 24) scale(.6667) translate(-72 -72)"><clipPath id="m%d">%s</clipPath>'
                  '<g clip-path="url(#m%d)">%s%s</g></g>' % (24 + i * 216, i, sh, i, inner(bg), inner(fg)))
    return wrap(672, 240, '<rect width="672" height="240" fill="#EEF2F5"></rect>' + cells)


def feature():
    """1024x500: the same world as the icon, built wide (the real skyline across, faceted peaks, forest, snow),
    with the sign on the right carrying the place name."""
    font = '<style>@font-face{font-family:K;src:url(%s)}</style>' % FONT
    lit = ('#FFFFFF', '#CFE0F2')
    defs = ['<linearGradient id="fsky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5588CB"></stop>'
            '<stop offset=".55" stop-color="#A9CBEA"></stop><stop offset="1" stop-color="#FBE6CF"></stop></linearGradient>',
            '<linearGradient id="fgnd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="1" stop-color="#D5E2F0"></stop></linearGradient>',
            '<radialGradient id="fsun" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFE9A8" stop-opacity=".95"></stop><stop offset="1" stop-color="#FFE9A8" stop-opacity="0"></stop></radialGradient>', font]
    body = ['<rect width="1024" height="500" fill="url(#fsky)"></rect>',
            '<circle cx="470" cy="150" r="110" fill="url(#fsun)"></circle><circle cx="470" cy="150" r="26" fill="%s"></circle>' % b.GOLD]
    far = b.ridge_from_skyline(base=290, k=24, x_from=0, x_to=1024)
    body.append('<path d="%s" fill="#B1C9E1"></path>' % b.path(far + [(1024, 420), (0, 420)]))
    far2 = b.ridge_from_skyline(base=312, k=16, x_from=-60, x_to=1100)
    body.append('<path d="%s" fill="#97B3D3"></path>' % b.path(far2 + [(1024, 420), (0, 420)]))
    for i, (apex, l, r, rx, seed, steps) in enumerate([((70, 200), -100, 220, 100, 3, 6), ((560, 120), 380, 760, 580, 8, 10),
                                                       ((860, 170), 700, 1100, 880, 12, 9), ((250, 50), -20, 520, 290, 5, 12)]):
        d, sv, _ = b.peak(apex, l, r, 360, rx, seed=seed, jag=10, steps=steps, lit=lit, gid='fp%d' % i)
        defs.append(d); body.append(sv)
    body.append('<path d="M0 334 Q90 316 180 328 T360 324 T540 330 T720 322 T900 330 T1024 318 V460 H0 Z" fill="#285684"></path>')
    for i in range(34):
        x = 8 + i * 31
        body.append(b.spruce(x, 356 + (i * 7) % 18, 48 + (i * 11) % 22, col='#1E4A78'))
    body.append('<path d="M0 430 Q200 408 400 420 T800 414 T1024 410 V500 H0 Z" fill="url(#fgnd)"></path>')
    sx, sy = 560, 118
    body.append('<g transform="translate(%d %d) scale(.95)">' % (sx, sy)
                + '<ellipse cx="248" cy="364" rx="90" ry="10" fill="#13233A" opacity=".16"></ellipse>'
                + '<rect x="232" y="250" width="34" height="114" fill="#13233A"></rect>'
                + '<path d="M0 176 L60 90 H420 V262 H60 Z" fill="#1F5FC4"></path>'
                + '<path d="M0 176 L60 90 H420 V100 H66 L10 182 Z" fill="#fff" opacity=".18"></path>'
                + '<text x="246" y="222" text-anchor="middle" font-family="K" font-size="112" fill="#fff" letter-spacing="4">GUDAURI</text>'
                + '<path d="%s" fill="#fff"></path>' % b.snow_cap(24, 424, 92, 34, rnd=1.4, drips=[(.1, 24), (.36, 12), (.6, 30), (.88, 14)])
                + '</g>')
    return wrap(1024, 500, ''.join(body), ''.join(defs))


def render(svg_path, png_path, w, h, transparent=False):
    subprocess.run(['node', str(here / 'render.js'), str(svg_path), str(png_path), str(w), str(h), '1' if transparent else '0'], check=True)


if __name__ == '__main__':
    OUT.mkdir(exist_ok=True)
    RES.mkdir(parents=True, exist_ok=True)
    (OUT / 'store-icon.svg').write_text(b.h1(), encoding='utf-8')
    bg, fg, mono = layers()
    for name, svg in [('launcher-bg', bg), ('launcher-fg', fg), ('launcher-mono', mono), ('masks', masks(bg, fg)), ('feature', feature())]:
        (OUT / (name + '.svg')).write_text(svg, encoding='utf-8')
    render(OUT / 'store-icon.svg', OUT / 'store-icon-512.png', 512, 512)
    render(OUT / 'feature.svg', OUT / 'feature-1024x500.png', 1024, 500)
    render(OUT / 'masks.svg', OUT / 'masks.png', 672, 240)
    render(OUT / 'launcher-bg.svg', RES / 'ic_launcher_bg.png', 432, 432)
    render(OUT / 'launcher-fg.svg', RES / 'ic_launcher_fg.png', 432, 432, transparent=True)
    render(OUT / 'launcher-mono.svg', RES / 'ic_launcher_mono.png', 432, 432, transparent=True)
    print('ok')
