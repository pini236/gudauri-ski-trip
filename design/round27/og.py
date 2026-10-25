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


PAL = {
    # day: the store graphic's palette
    'day': dict(sky=('#4F86CC', '#A9CBEA', '#F6E6D2'), sun=('#FFE9A8', '#F4B942'), far=('#B7CDE4', '#9AB6D6'),
                lit=('#FFFFFF', '#D3E3F3'), shade=('#A4BCD8', '#6685B0'), snow=('#FFFFFF', '#DCE7F3'), snow2=('#E6EEF8', '#B8CBE2'),
                track='#B9CBE0', stars=0),
    # golden hour: low sun, alpenglow on the peaks
    'gold': dict(sky=('#3E6FB3', '#E7A98A', '#FBD6A6'), sun=('#FFE2A8', '#F6A94C'), far=('#C9B3C4', '#A99AB7'),
                 lit=('#FFE9DA', '#F2C3B0'), shade=('#9C9CC0', '#6A6E9E'), snow=('#FFF4EA', '#EBD7CD'), snow2=('#F7E7DC', '#D9C3BC'),
                 track='#D7B9AE', stars=0),
    # night: moon and stars, the sign lit
    'night': dict(sky=('#0B1730', '#1E3560', '#3A5585'), sun=('#E9EEF8', '#E9EEF8'), far=('#2A426D', '#22385E'),
                  lit=('#C9D7EC', '#8FA7C9'), shade=('#4E6690', '#2E4672'), snow=('#C7D5EA', '#8EA4C6'), snow2=('#B3C4DE', '#7F96BA'),
                  track='#7E95B8', stars=1),
}


def og2(name='day', word='GUDAURI'):
    """Round 27, second pass (Pini: no little trees): native 1200x630, layered ridges, big faceted peaks and a
    snow field with two ski tracks curving to the sign, which stands in the middle."""
    P = PAL[name]
    g = lambda gid, a, b: ('<linearGradient id="%s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s"></stop>'
                           '<stop offset="1" stop-color="%s"></stop></linearGradient>' % (gid, a, b))
    defs = ['<style>@font-face{font-family:K;src:url(%s)}</style>' % FONT,
            '<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="%s"></stop><stop offset=".62" stop-color="%s"></stop>'
            '<stop offset="1" stop-color="%s"></stop></linearGradient>' % P['sky'],
            '<radialGradient id="glow" cx=".5" cy=".5" r=".5"><stop offset="0" stop-color="%s" stop-opacity=".9"></stop>'
            '<stop offset="1" stop-color="%s" stop-opacity="0"></stop></radialGradient>' % (P['sun'][0], P['sun'][0]),
            g('far', *P['far']), g('snow', *P['snow']), g('snow2', *P['snow2'])]
    body = ['<rect width="%d" height="%d" fill="url(#sky)"></rect>' % (W, H)]
    if P['stars']:
        import random
        r = random.Random(7)
        body += ['<circle cx="%d" cy="%d" r="%.1f" fill="#fff" opacity="%.2f"></circle>' % (r.uniform(0, W), r.uniform(0, 300), r.uniform(.6, 1.8), r.uniform(.4, .95)) for _ in range(90)]
        body.append('<circle cx="1010" cy="110" r="90" fill="url(#glow)" opacity=".5"></circle><circle cx="1010" cy="110" r="30" fill="%s"></circle>'
                    '<circle cx="1022" cy="102" r="27" fill="%s"></circle>' % (P['sun'][1], P['sky'][0]))
    else:
        sx = 1010 if name == 'day' else 960
        sy = 120 if name == 'day' else 255
        body.append('<circle cx="%d" cy="%d" r="140" fill="url(#glow)"></circle><circle cx="%d" cy="%d" r="32" fill="%s"></circle>' % (sx, sy, sx, sy, P['sun'][1]))
    far = b.ridge_from_skyline(base=330, k=30, x_from=-20, x_to=W + 20)
    body.append('<path d="%s" fill="url(#far)" opacity=".85"></path>' % b.path(far + [(W + 20, 470), (-20, 470)]))
    lit = P['lit']; shade = P['shade']
    for i, (apex, l, r_, rx, seed, steps) in enumerate([((110, 235), -90, 300, 140, 3, 7), ((1130, 225), 960, 1320, 1150, 12, 8),
                                                        ((600, 170), 430, 780, 615, 21, 9), ((290, 80), 60, 560, 320, 5, 12),
                                                        ((925, 95), 690, 1150, 950, 8, 11)]):
        d, sv, _ = b.peak(apex, l, r_, 440, rx, seed=seed, jag=11, steps=steps, lit=lit, shade=shade, gid='pk%d' % i)
        defs.append(d); body.append(sv)
    # a lower snowy range in front of the peaks (instead of the forest): soft, shaded, a little darker than the field
    mid = b.ridge_from_skyline(base=432, k=9, x_from=-40, x_to=W + 40)
    body.append('<path d="%s" fill="%s" opacity=".55"></path>' % (b.path(mid + [(W + 40, 520), (-40, 520)]), shade[0]))
    # the snow field: two soft rolling layers, no trees
    body.append('<path d="M0 436 C 180 414, 330 430, 470 424 S 760 408, 900 420 S 1110 434, 1200 420 V630 H0 Z" fill="url(#snow2)"></path>')
    body.append('<path d="M0 492 C 220 470, 420 500, 600 490 S 960 466, 1200 484 V630 H0 Z" fill="url(#snow)"></path>')
    body.append('<path d="M0 492 C 220 470, 420 500, 600 490 S 960 466, 1200 484" fill="none" stroke="%s" stroke-width="3" opacity=".35"></path>' % P['track'])
    # two ski tracks curving down from the left slope to the foot of the sign
    tr = 'M30 500 C 230 512, 300 548, 450 560 S 720 586, 860 640'
    body.append('<path d="%s" fill="none" stroke="%s" stroke-width="3" stroke-linecap="round"></path>' % (tr, P['track']))
    body.append('<path d="%s" fill="none" stroke="%s" stroke-width="3" stroke-linecap="round" transform="translate(9 4)"></path>' % (tr, P['track']))
    sgn = sign(word, s=1.1, sy=92)
    if name == 'night':  # a warm light on the sign at night
        sgn = '<circle cx="600" cy="270" r="260" fill="url(#glow)" opacity=".35"></circle>' + sgn
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d"><defs>%s</defs>%s%s</svg>'
            % (W, H, W, H, ''.join(defs), ''.join(body), sgn))


if __name__ == '__main__':
    out = here / 'shots'
    out.mkdir(exist_ok=True)
    for n, svg in [('og-a', og()), ('og-b', og_pano()), ('og-c-day', og2('day')), ('og-c-gold', og2('gold')), ('og-c-night', og2('night'))]:
        (out / (n + '.svg')).write_text(svg, encoding='utf-8')
        subprocess.run(['node', str(root / 'design/round9/render.js'), str(out / (n + '.svg')), str(out / (n + '.png')), str(W), str(H), '0'], check=True)
        subprocess.run(['convert', str(out / (n + '.png')), '-gravity', 'center', '-crop', '630x630+0+0', '+repage', str(out / (n + '-sq.png'))], check=True)
    print('ok')
