#!/usr/bin/env python3
"""The canvas board of the Kobi question (page "live", board M07): what happens when a run behind the mountain is
chosen. Pini asked (21.11.2026): in today's app, choosing a run on the Kobi side turns the camera; how is it in the new
design? The frames are from the design tool of the live model (live_ref.js, with sel=Kobi), scaled to 585 by 1266, in
design/redesign/assets/kobi, and uploaded to the canvas as assets; the urls are below. The numbers (how much of a run
the opening camera sees) are a line-of-sight count on the elevation model, see visible() below.
  python3 design/redesign/tools/kobi_boards.py             write the board and update canvas.json
  python3 design/redesign/tools/kobi_boards.py --check D    also write a plain html copy with local images into D
  python3 design/redesign/tools/kobi_boards.py --numbers    print how much of every named run each camera sees
The frames (FROM_DISK=1 node design/redesign/tools/live_ref.js <dir> ...), all with light=noon unless said:
  open     (the opening camera)                              stay    sel=Kobi
  o60      sel=Kobi tx=26 ty=2350 tz=811 dist=6600 yaw=60 pitch=20
  o120     sel=Kobi tx=-17 ty=2350 tz=-745 dist=6300 yaw=120 pitch=20
  facesel  sel=Kobi tx=-60 ty=2350 tz=-2300 dist=6000 yaw=180 pitch=17      (the Kobi face; face = the same without sel)
  up       sel=Kobi tx=-60 ty=2740 tz=-1166 dist=3900 yaw=181 pitch=22      (looking up the slope, as today's app)
  facemorning, faceafter: the Kobi face with light=morning and light=after
"""
import base64, json, math, os, re, struct, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from live_boards import board, wide, PROJ, ROOT, H00

A = 'design/redesign/assets/kobi/'
IMG = {
    'open': ('/_blob/7caf45333c42165218d6527ad709d8c3', A + 'kobi-open.jpg'),
    'stay': ('/_blob/b377f0c4f333b3b4379842531b568499', A + 'kobi-stay.jpg'),
    'o60': ('/_blob/1d38b39bc8971896433f6e5654b4eb39', A + 'kobi-o60.jpg'),
    'o120': ('/_blob/f7a3856842f64808f7e9410bbf015c59', A + 'kobi-o120.jpg'),
    'facesel': ('/_blob/7d6e3867fae55d40d49b0ae353852283', A + 'kobi-facesel.jpg'),
    'up': ('/_blob/03c6b0097355c8d2832756f51e0ecc95', A + 'kobi-up.jpg'),
    'facemorning': ('/_blob/34a1ca7b7d27d3f3b2b0a68f8b9620e1', A + 'kobi-facemorning.jpg'),
    'face': ('/_blob/b1e40ddb38d665403fa2a81853239186', A + 'kobi-face.jpg'),
    'faceafter': ('/_blob/0d4fb6d4c74ca7216b33a10f630a53f8', A + 'kobi-faceafter.jpg'),
}
OPEN = dict(tx=69, ty=2350, tz=2366, dist=7100, yaw=0, pitch=16)
FACE = dict(tx=-60, ty=2350, tz=-2300, dist=6000, yaw=180, pitch=17)


def shot(k, w, cap, sub='', mark=''):
    h = round(w * 1266 / 585)
    return ('<div class="fr" style="width: %dpx"><div class="th" style="width: %dpx; height: %dpx%s"><img alt="" src="%s"></div><p class="cap"><b>%s</b>%s</p></div>'
            % (w, w, h, mark, IMG[k][0], cap, ('<br>' + sub) if sub else ''))


HK = 2040


def m07():
    rec = '; border: 3px solid #6B3FD4'
    opts = (shot('stay', 250, 'א · להישאר', 'המצלמה לא זזה. מהמסלול רואים 10 אחוז, ליד הרכס. הגיליון נפתח על מסלול שלא רואים.')
            + shot('up', 250, 'ב · לסובב כמו היום', 'המצלמה מתייצבת מול המדרון של המסלול, מקרוב. זווית אחרת לכל מסלול; ב-Kobi זה חצי סיבוב בבת אחת.')
            + shot('facesel', 250, 'ג · להר יש שני צדדים (ההמלצה)', 'ההר מסתובב לצד של Kobi, למבט רחב כמו הפתיחה, ומשם מתקרבים כרגיל.', rec))
    facts = ('<div class="card" style="flex: 1"><p class="sec">מה מוסתר, במספרים</p><h2>מסלול אחד מאחורי ההר</h2><ul>'
             '<li><b>Kobi:</b> כחול, 2.9 ק״מ. יורד צפונה מהרכס (2,950 מ׳) עד 2,513 מ׳, בצד שממבט הפתיחה לא רואים.</li>'
             '<li><b>ממבט הפתיחה רואים 10 אחוז ממנו.</b> גם רבע סיבוב לא עוזר: 15 אחוז ממזרח, 2 ממערב.</li>'
             '<li><b>זה המסלול היחיד עם שם בצד הזה,</b> ואיתו שני רכבלים: Kobi ו-Truso.</li>'
             '<li><b>כל שאר 27 המסלולים</b> נראים מהפתיחה, 85 עד 100 אחוז מכל אחד.</li></ul>'
             '<p class="cap">האחוזים: ספירה של קו ראייה מהמצלמה לכל נקודה במסלול, על מודל הגובה.</p></div>')
    steps = (shot('open', 178, '0°', 'הפתיחה') + shot('o60', 178, '60°', 'ההר מסתובב, דרך המזרח') + shot('o120', 178, '120°', 'המסלול מתגלה')
             + shot('facesel', 178, '180°', 'הצד של Kobi'))
    rule = ('<div class="card" style="flex: 1"><p class="sec">ההמלצה: ג</p><h2>הכיוון משתנה רק כדי להראות מה שמוסתר</h2><ol>'
            '<li><b>רואים לפחות חצי מהמסלול:</b> הכיוון נשאר, ורק מתקרבים. כך ב-27 מתוך 28 המסלולים.</li>'
            '<li><b>רואים פחות מחצי:</b> ההר מסתובב לצד השני בתנועה אחת שרואים, בכשנייה, ואז האור יורד במסלול ומתקרבים. היום זה Kobi ושני הרכבלים שלו.</li>'
            '<li><b>בצד של Kobi</b> מופיע השלט הקטן "צד Kobi" (מחרוזת שכבר קיימת). נגיעה בו מחזירה לחזית. סגירת הגיליון לא מסובבת חזרה לבד.</li>'
            '<li><b>מי שסובב את ההר בעצמו:</b> לא נוגעים בכיוון שלו, כל עוד המסלול נראה.</li>'
            '<li><b>הפחתת תנועה:</b> מעבר רך בין שני המבטים, בלי סיבוב.</li></ol></div>')
    hours = (shot('facemorning', 150, '10:36', 'בוקר: רובו בצל') + shot('face', 150, '13:00', 'צהריים') + shot('faceafter', 150, '15:54', 'אחר הצהריים'))
    why = ('<div class="card" style="flex: 1"><h2>למה לא א ולא ב</h2><ul>'
           '<li><b>א, להישאר:</b> גיליון עם מספרים על מסלול שלא רואים. מי שרוצה לראות אותו צריך לסובב לבד, בלי לדעת לאן.</li>'
           '<li><b>ב, כמו היום:</b> טוב למסלול אחד, מבלבל ברצף. במעבר ממסלול למסלול ההר זז כל פעם לזווית אחרת, וחצי סיבוב בבת אחת מאבד את הכיוון. בחזית זה גם מיותר, כי משם רואים הכל.</li></ul></div>')
    hon = ('<div class="card" style="flex: 1"><h2>ביושר</h2><ul>'
           '<li><b>הפריימים מכלי העיצוב בדפדפן,</b> לא מהטלפון. הרכבלים לא מצוירים בכלי, והמסלול הנבחר הוא קו פשוט ולא קו האור הסופי.</li>'
           '<li><b>הצד של Kobi פונה צפונה.</b> בינואר הוא בצל חלק גדול מהיום, וכך הוא מצויר (שלושת הפריימים הקטנים).</li>'
           '<li><b>בגרסת הבדיקה של היום (שלב א, התמונה):</b> Kobi נשאר במבט הרחוק, ורואים רק את ראשו. לא מציע לצייר בשבילו סט תמונות שני: המודל החי פותר את זה.</li>'
           '<li><b>הזמנים והסף של "חצי"</b> הם ההגדרה שלי, ועוד לא נבדקו על טלפון.</li></ul></div>')
    qs = ('<div class="card"><h2>שאלות אליך</h2>'
          '<div class="q"><span class="qn">1</span><span><b>להר יש שני צדדים,</b> והמצלמה מסתובבת רק כדי להראות מסלול מוסתר. <b>(ההמלצה: כן, ג)</b></span></div>'
          '<div class="q"><span class="qn">2</span><span><b>אחרי שסוגרים את הגיליון של Kobi:</b> נשארים בצד של Kobi, עם השלט שמחזיר לחזית, או חוזרים לחזית לבד? <b>(ההמלצה: נשארים)</b></span></div></div>')
    inner = ('<p class="sec">שאלה שלך · 21.11.2026</p><h1>מסלול שמאחורי ההר: מה קורה כשבוחרים את Kobi</h1>'
             '<p class="lead">באפליקציה של היום, בחירת מסלול מעמידה את המצלמה מול המדרון שלו, ולכן ב-Kobi המצלמה קופצת לצד השני של ההר. '
             'שאלת איך זה נראה בעיצוב החדש. כאן שלוש אפשרויות במודל החי, באותו אור של צהריים, וההמלצה שלי.</p>'
             '<div class="rowx">%s%s</div>'
             '<h2 style="margin-top: 6px">ג, צעד אחר צעד: סיבוב אחד שרואים</h2><div class="rowx">%s%s</div>'
             '<h2 style="margin-top: 6px">הצד של Kobi לאורך היום</h2><div class="rowx" style="align-items: stretch">%s%s%s</div>%s'
             % (opts, facts, steps, rule, hours, why, hon, qs))
    return board('מסלול שמאחורי ההר: Kobi', 1280, HK, wide(1280, HK, inner))


Y = H00 + 2600 + 844 + 380
BOARDS = [('M07-Kobi.dc.html', m07, 0, Y, 1280, HK, '7 · מסלול שמאחורי ההר: Kobi')]
NOTES = {'tM4': dict(kind='title1', maxW=1600, page='live', w=520, x=0, y=Y - 300, text='ד · מסלול שמאחורי ההר (שאלה של פיני)')}


def numbers():
    T = json.load(open(os.path.join(ROOT, 'site/data/terrain.json'))); D = json.load(open(os.path.join(ROOT, 'site/data/runs-and-lifts.json')))
    dm = T['dem']; nx, ny = dm['nx'], dm['ny']; Z = struct.unpack('<%dh' % (nx * ny), base64.b64decode(dm['b64']))
    x0, x1, z0, z1 = dm['x0'], dm['x1'], dm['y0'], dm['y1']; sx = (x1 - x0) / (nx - 1); sz = (z1 - z0) / (ny - 1)

    def elev(x, z):
        c = min(max((x - x0) / sx, 0), nx - 1.001); r = min(max((z - z0) / sz, 0), ny - 1.001); c0 = int(c); r0 = int(r); fc = c - c0; fr = r - r0
        a = lambda c, r: Z[r * nx + c]
        return (a(c0, r0) * (1 - fc) + a(c0 + 1, r0) * fc) * (1 - fr) + (a(c0, r0 + 1) * (1 - fc) + a(c0 + 1, r0 + 1) * fc) * fr
    kx = 111320 * math.cos(math.radians(T['proj']['lat0']))

    def eye(tx, ty, tz, dist, yaw, pitch):                      # OrbitCamera, as live_ref_main.js
        y = math.radians(yaw); p = math.radians(pitch)
        e = [tx + math.sin(y) * math.cos(p) * dist, ty + math.sin(p) * dist, tz + math.cos(y) * math.cos(p) * dist]
        e[1] = max(e[1], elev(e[0], e[2]) + 40); return e

    def visible(e, x, z):                                        # is the ground between the eye and a point 6 m above the run?
        y = elev(x, z) + 6; dx, dy, dz = x - e[0], y - e[1], z - e[2]; L = math.sqrt(dx * dx + dy * dy + dz * dz); n = int(L / 30)
        for i in range(1, n):
            t = i / n; px, pz = e[0] + dx * t, e[2] + dz * t
            if px < x0 or px > x1 or pz < z0 or pz > z1: continue
            if elev(px, pz) > e[1] + dy * t + 2 and (1 - t) * L > 60: return False
        return True
    cams = [('open', OPEN), ('east', dict(OPEN, yaw=90)), ('west', dict(OPEN, yaw=270)), ('kobi', FACE)]
    eyes = [(n, eye(**c)) for n, c in cams]
    print('%-16s' % 'run', ' '.join('%5s' % n for n, _ in cams))
    for p in D['pistes']:
        if not p.get('named'): continue
        P = []
        for s in p['segs']:
            if s.get('area'): continue
            g = [((v[1] - T['proj']['lon0']) * kx, -(v[0] - T['proj']['lat0']) * 111320) for v in s['g']]
            for i in range(len(g) - 1):
                n = max(1, math.ceil(math.hypot(g[i + 1][0] - g[i][0], g[i + 1][1] - g[i][1]) / 25))
                P += [(g[i][0] + (g[i + 1][0] - g[i][0]) * j / n, g[i][1] + (g[i + 1][1] - g[i][1]) * j / n) for j in range(n)]
        if P: print('%-16s' % p['key'], ' '.join('%4d%%' % round(100 * sum(visible(e, x, z) for x, z in P) / len(P)) for _, e in eyes))


def main():
    if '--numbers' in sys.argv: return numbers()
    chk = sys.argv[sys.argv.index('--check') + 1] if '--check' in sys.argv else None
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf-8'))
    for name, fn, x, y, w, h, title in BOARDS:
        s = fn(); open(os.path.join(PROJ, name), 'w', encoding='utf-8').write(s)
        j['boards'][name] = dict(x=x, y=y, w=w, h=h, title=title, page='live')
        if name not in j['order']: j['order'].append(name)
        if chk:
            os.makedirs(chk, exist_ok=True); t = s
            for k, (u, f) in IMG.items(): t = t.replace(u, 'file://' + os.path.join(ROOT, f))
            t = re.sub(r'</?x-dc>|</?helmet>|<script src="./support.js"></script>', '', t)
            open(os.path.join(chk, name.replace('.dc.html', '.html')), 'w', encoding='utf-8').write(t)
    j['notes'].update(NOTES)
    open(p, 'w', encoding='utf-8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    print('wrote', len(BOARDS), 'board')


if __name__ == '__main__': main()
