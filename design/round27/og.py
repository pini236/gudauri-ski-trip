#!/usr/bin/env python3
"""Round 27: a generic share image (og:image, 1200x630) for when the site opens to Google.
The world of the approved store graphic (round 9, sign no. 10, export.py feature()), built for 1200x630 with the sign
in the middle, so a square crop (WhatsApp's small preview) still shows the whole sign. No dates, no trip.
  python3 design/round27/og.py      -> design/round27/shots/og-a.png, og-b.png (+ .svg) and their square crops (-sq.png)"""
import pathlib, subprocess, sys
here = pathlib.Path(__file__).resolve().parent
root = here.parent.parent
sys.path.insert(0, str(root / 'design/round9'))
import build5 as b

FONT = (root / 'app/android/app/src/main/res/font/karantina_bold.ttf').as_uri()
W, H = 1200, 630


def sign(word='GUDAURI', s=1.1, sy=70):
    """the sign of the store graphic, centred: 420 wide at scale 1.1 -> about 460 px, x 370 to 830"""
    sx = W / 2 - 230 * s
    return ('<g transform="translate(%s %s) scale(%s)">' % (sx, sy, s)
            + '<ellipse cx="248" cy="364" rx="90" ry="10" fill="#13233A" opacity=".16"></ellipse>'
            + '<rect x="232" y="250" width="34" height="114" fill="#13233A"></rect>'
            + '<path d="M0 176 L60 90 H420 V262 H60 Z" fill="#1F5FC4"></path>'
            + '<path d="M0 176 L60 90 H420 V100 H66 L10 182 Z" fill="#fff" opacity=".18"></path>'
            + '<text x="246" y="222" text-anchor="middle" font-family="K" font-size="112" fill="#fff" letter-spacing="4">%s</text>' % word
            + '<path d="%s" fill="#fff"></path>' % b.snow_cap(24, 424, 92, 34, rnd=1.4, drips=[(.1, 24), (.36, 12), (.6, 30), (.88, 14)])
            + '</g>')


def og_pano(name='noon'):
    """Option B: the real view from the village (the home page panorama, rendered from the elevation model) with the sign."""
    src = (root / ('site/img/pano/pano-wide-%s.webp' % name)).as_uri()
    k = H / 800
    w = 2880 * k
    img = '<image href="%s" x="%s" y="0" width="%s" height="%s" preserveAspectRatio="none"></image>' % (src, (W - w) / 2, w, H)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"><defs><style>@font-face{font-family:K;src:url(%s)}</style>'
            '<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4F86CC"></stop><stop offset=".6" stop-color="#A9CBEA"></stop>'
            '<stop offset="1" stop-color="#DCE8F3"></stop></linearGradient></defs><rect width="%d" height="%d" fill="url(#sky)"></rect>%s%s</svg>'
            % (W, H, W, H, FONT, W, H, img, sign(s=1.0, sy=-20)))


def og(word='GUDAURI'):
    k = H / 500  # the store scene is 500 high; widen it to 1200 instead of stretching
    lit = ('#FFFFFF', '#CFE0F2')
    defs = ['<linearGradient id="fsky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5588CB"></stop>'
            '<stop offset=".55" stop-color="#A9CBEA"></stop><stop offset="1" stop-color="#FBE6CF"></stop></linearGradient>',
            '<linearGradient id="fgnd" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF"></stop><stop offset="1" stop-color="#D5E2F0"></stop></linearGradient>',
            '<radialGradient id="fsun" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="#FFE9A8" stop-opacity=".95"></stop><stop offset="1" stop-color="#FFE9A8" stop-opacity="0"></stop></radialGradient>',
            '<style>@font-face{font-family:K;src:url(%s)}</style>' % FONT]
    w = W / k  # scene width in store units (about 952)
    body = ['<rect width="%s" height="500" fill="url(#fsky)"></rect>' % w,
            '<circle cx="150" cy="120" r="100" fill="url(#fsun)"></circle><circle cx="150" cy="120" r="24" fill="%s"></circle>' % b.GOLD]
    far = b.ridge_from_skyline(base=290, k=24, x_from=0, x_to=w)
    body.append('<path d="%s" fill="#B1C9E1"></path>' % b.path(far + [(w, 420), (0, 420)]))
    far2 = b.ridge_from_skyline(base=312, k=16, x_from=-60, x_to=w + 70)
    body.append('<path d="%s" fill="#97B3D3"></path>' % b.path(far2 + [(w, 420), (0, 420)]))
    for i, (apex, l, r, rx, seed, steps) in enumerate([((60, 210), -100, 220, 90, 3, 6), ((330, 70), 90, 600, 360, 5, 12),
                                                       ((700, 110), 470, 930, 720, 8, 10), ((900, 190), 760, 1080, 920, 12, 9)]):
        d, sv, _ = b.peak(apex, l, r, 360, rx, seed=seed, jag=10, steps=steps, lit=lit, gid='fp%d' % i)
        defs.append(d); body.append(sv)
    body.append('<path d="M0 334 Q90 316 180 328 T360 324 T540 330 T720 322 T900 330 T%s 318 V460 H0 Z" fill="#285684"></path>' % w)
    for i in range(32):
        body.append(b.spruce(8 + i * 31, 356 + (i * 7) % 18, 48 + (i * 11) % 22, col='#1E4A78'))
    body.append('<path d="M0 430 Q200 408 400 420 T800 414 T%s 410 V500 H0 Z" fill="url(#fgnd)"></path>' % w)
    scene = '<g transform="scale(%s)">%s</g>' % (k, ''.join(body))
    sgn = sign(word)
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"><defs>%s</defs>%s%s</svg>'
            % (W, H, W, H, ''.join(defs), scene, sgn))


if __name__ == '__main__':
    out = here / 'shots'
    out.mkdir(exist_ok=True)
    for n, svg in [('og-a', og()), ('og-b', og_pano())]:
        (out / (n + '.svg')).write_text(svg, encoding='utf-8')
        subprocess.run(['node', str(root / 'design/round9/render.js'), str(out / (n + '.svg')), str(out / (n + '.png')), str(W), str(H), '0'], check=True)
        subprocess.run(['convert', str(out / (n + '.png')), '-gravity', 'center', '-crop', '630x630+0+0', '+repage', str(out / (n + '-sq.png'))], check=True)
    print('ok')
