#!/usr/bin/env python3
"""The record button, three alternatives (Pini via the manager, 25.11.2026): can the shape and the motion say what it does,
without a word or with a better one. Same canvas page as v56_boards ("הערות על גרסה 56"), under the boards already there.
  A  ski tracks that end in a dot (the skier), no words
  B  a small light line of the day on a ridge, ending in a red dot, no words
  C  the word until the first tracking ends, then the tracks alone (the recommendation)
Design only. Usage: python3 design/redesign/tools/rd_boards.py"""
import os, json
import v56_boards as V
from v56_boards import mtn, top, ctl, ic, P_HOME, P_SRCH, veil, wb, q, li, PROJ, PAGE
import peak_boards as PB
import gap4_boards as G4
import live_boards as LB

WORD = 'הקלטת גלישה'   # the text in main today (app.rec_sign)
TIME = '1:24'

# ---- the button, without a scope, so it works in the phones (.H) and in the wide board (.HW)
BCSS = """
@keyframes tkd{0%{stroke-dashoffset:1;opacity:1}28%{stroke-dashoffset:0;opacity:1}78%{stroke-dashoffset:0;opacity:1}100%{stroke-dashoffset:0;opacity:0}}
@keyframes tkp{0%,100%{transform:translateY(0)}28%{transform:translateY(1.4px)}}
@keyframes rdh{0%{box-shadow:0 0 0 0 rgba(229,53,43,.55)}100%{box-shadow:0 0 0 12px rgba(229,53,43,0)}}
@keyframes lnd{0%{stroke-dashoffset:1}45%{stroke-dashoffset:0}100%{stroke-dashoffset:0}}
@keyframes lng{0%,100%{opacity:.75}50%{opacity:1}}
.ra{width:48px;height:48px;border-radius:24px;background:#E5352B;color:#fff;display:flex;align-items:center;justify-content:center;gap:7px;border:0;flex:none;padding:0;box-sizing:border-box;font-family:inherit}
.ra svg{flex:none;overflow:visible}
.ra .tk{stroke-dasharray:1;stroke-dashoffset:0}
.ra.idle .tk{animation:tkd 6s ease-out infinite}
.ra.idle .sk{animation:tkp 6s ease-out infinite}
.ra.live{width:auto;padding:0 15px 0 11px;animation:rdh 1.8s ease-out infinite}
.ra.live .tk{animation:tkd 2.4s linear infinite}
.ra.live .sk{animation:tkp 2.4s linear infinite}
.ra.word{width:auto;padding:0 16px 0 10px;font-size:15px;font-weight:700;white-space:nowrap}
.ra .t{font-size:15px;font-weight:500;font-variant-numeric:tabular-nums;direction:ltr}
.rb2{height:48px;width:64px;border-radius:24px;background:#10161D;display:flex;align-items:center;justify-content:center;gap:7px;border:0;flex:none;padding:0;box-sizing:border-box;color:#fff;font-family:inherit}
.rb2.n{background:#2A3858}
.rb2 .ln{stroke-dasharray:1;stroke-dashoffset:0}
.rb2.idle .ln{animation:lnd 5s ease-out infinite}
.rb2 .gl{animation:lng 2s ease-in-out infinite}
.rb2.live{width:auto;padding:0 14px 0 8px}
.rb2 .t{font-size:15px;font-weight:500;font-variant-numeric:tabular-nums;direction:ltr}
.still .tk,.still .sk,.still .ln,.still .gl{animation:none!important}
.still.ra,.still.rb2{animation:none!important}
"""
G4.MB.CSS = G4.MB.CSS + BCSS.replace('\n.', '\n.H .').replace(',.', ',.H .')
LB.CSS = LB.CSS + BCSS + """
.HW .stage{display:flex;align-items:center;justify-content:center;gap:22px;height:150px;border-radius:12px}
.HW .stage.d{background:linear-gradient(#DDE8F3,#F4F7FA)}
.HW .stage.n{background:linear-gradient(#0B1424,#16213A)}
.HW .stage .z{zoom:1.9}
.HW .frames{display:flex;gap:7px;align-items:center;justify-content:center;height:84px;border-radius:12px;background:#EEF2F6}
.HW .frames .z{zoom:1.05}
.HW .opt{flex:1;min-width:0}
.HW .opt h2 .k{display:inline-flex;width:34px;height:34px;border-radius:17px;background:#10161D;color:#fff;align-items:center;justify-content:center;font-size:18px;margin-inline-end:8px}
.HW .opt.rec{border:3px solid #6B3FD4}
.HW .pc{font-size:14.5px;line-height:1.5}
.HW .pc b{display:inline-block;min-width:58px}
"""


TRK = 'M19 2.4C9.6 3.4 5.6 6.4 10.2 9.6C14.8 12.6 15.6 15.8 7.6 18.6'


def tracks(prog=None, bg='#E5352B'):
    """ski tracks: one curve drawn wide in white and again thin in the button colour, so it reads as two parallel lines;
    they end at the skier (a dot). prog: a frame of the motion (0..1), None = animated"""
    off = '' if prog is None else ' style="stroke-dashoffset: %.2f; animation: none"' % (1 - prog)
    sk = '' if prog is None else ' style="transform: translateY(%.1fpx); animation: none"' % (1.4 * min(prog, 1))
    return ('<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke-linecap="round" aria-hidden="true">'
            '<path class="tk" pathLength="1" d="%s" stroke="#fff" stroke-width="5"%s/>'
            '<path class="tk" pathLength="1" d="%s" stroke="%s" stroke-width="2"%s/>'
            '<circle class="sk" cx="6.9" cy="18.8" r="3.4" fill="#fff"%s/></svg>' % (TRK, off, TRK, bg, off, sk))


def ra(state='idle', word=WORD, t=TIME, prog=None, still=False):
    s = ' still' if still or prog is not None else ''
    if state == 'idle':
        return '<button class="ra idle%s" aria-label="%s">%s</button>' % (s, word, tracks(prog))
    if state == 'live':
        return '<button class="ra live%s" aria-label="%s, %s">%s<span class="t">%s</span></button>' % (s, word, t, tracks(prog), t)
    return '<button class="ra word%s" aria-label="%s">%s<span>%s</span></button>' % (s, word, tracks(prog), word)


RAMP = {'day': ['#CFC2F4', '#A98BEE', '#7C4FE0', '#B02FC4', '#E0246E'], 'night': ['#8A6BE6', '#B99CFF', '#F3A6F0', '#FFF2FA', '#FFF2FA']}


def lightline(mode='day', prog=None, long=False):
    """a ridge in thin white, and the light line of the day zig-zagging down it to a red dot"""
    c = RAMP[mode]
    pts = 'M5 7C12 8 4 13 12 15S16 19 21 20.5' if not long else 'M4 6C11 7 3 11 10 13S5 17 13 18S17 20 22 21'
    end = '21 20.5' if not long else '22 21'
    ex, ey = end.split()
    off = '' if prog is None else ' style="stroke-dashoffset: %.2f; animation: none"' % (1 - prog)
    gid = 'g%s%s' % (mode, 'L' if long else '')
    return ('<svg width="30" height="24" viewBox="0 0 26 24" fill="none" aria-hidden="true">'
            '<defs><linearGradient id="%s" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="%s"/><stop offset=".5" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient></defs>'
            '<path d="M1 12 6.5 3.5 10 8 14 2 19 9 25 6" stroke="#fff" stroke-opacity=".38" stroke-width="1.1" stroke-linejoin="round"/>'
            '<path class="ln" pathLength="1" d="%s" stroke="url(#%s)" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"%s/>'
            '<circle class="gl" cx="%s" cy="%s" r="3.3" fill="#E5352B" stroke="#fff" stroke-width="1.2"/></svg>'
            % (gid, c[0], c[2], c[4], pts, gid, off, ex, ey))


def rb2(state='idle', mode='day', t=TIME, prog=None, still=False):
    s = ' still' if still or prog is not None else ''
    n = ' n' if mode == 'night' else ''
    if state == 'live':
        return '<button class="rb2 live%s%s" aria-label="%s, %s">%s<span class="t">%s</span></button>' % (n, s, WORD, t, lightline(mode, prog, True), t)
    return '<button class="rb2 idle%s%s" aria-label="%s">%s</button>' % (n, s, WORD, lightline(mode, prog))


def dock(btn):
    return ('<div class="dock2 glass"><button class="vl">%s הכפר</button><div class="find">%s<span>לאן יורדים?</span></div>%s</div>'
            % (ic(P_HOME, 20), ic(P_SRCH, 18), btn))


def phones():
    out = {}
    out['RD1.dc.html'] = ('כפתור ההקלטה · א, עקבות, ביום', mtn() + top() + ctl() + dock(ra('idle')), 'day')
    out['RD2.dc.html'] = ('כפתור ההקלטה · א, בזמן הקלטה, בלילה', mtn('night') + top() + ctl() + dock(ra('live')), 'night')
    out['RD3.dc.html'] = ('כפתור ההקלטה · ב, קו האור, ביום', mtn() + top() + ctl() + dock(rb2('idle', 'day')), 'day')
    out['RD4.dc.html'] = ('כפתור ההקלטה · ב, בזמן הקלטה, בלילה', mtn('night') + top() + ctl() + dock(rb2('live', 'night')), 'night')
    out['RD5.dc.html'] = ('כפתור ההקלטה · ג, בפעם הראשונה, ביום', mtn() + top() + ctl() + dock(ra('word')), 'day')
    out['RD6.dc.html'] = ('כפתור ההקלטה · ג, אחרי הקלטה ראשונה, בלילה', mtn('night') + top() + ctl() + dock(ra('idle')), 'night')
    return out


def opt(k, h2, stage_d, stage_n, frames, pros, cons, rec=False):
    return ('<div class="card opt%s"><h2><span class="k">%s</span>%s</h2>'
            '<div class="stage d">%s</div><div class="stage n">%s</div><div class="frames">%s</div>'
            '<p class="pc"><b>מה עובד</b>%s</p><p class="pc"><b>מה חסר</b>%s</p></div>'
            % (' rec' if rec else '', k, h2, stage_d, stage_n, frames, pros, cons))


def z(x, cls='z'):
    return '<div class="%s">%s</div>' % (cls, x)


HRD = 1250


def rd00():
    fa = ''.join(z(ra('idle', prog=p), 'z') for p in (0.05, 0.4, 0.8, 1.0))
    fb = ''.join(z(rb2('idle', prog=p), 'z') for p in (0.1, 0.45, 0.8, 1.0))
    fc = z(ra('word', still=True)) + '<span style="font-size: 22px; color: #8A93A0">←</span>' + z(ra('idle', still=True))
    a = opt('א', 'עקבות סקי שנגמרות בנקודה', z(ra('idle')) + z(ra('live')), z(ra('idle')) + z(ra('live')), fa,
            'נקודה אדומה היא "הקלטה" בכל טלפון, והעקבות אומרות "סקי". העקבות נמשכות לנקודה פעם בכמה שניות, ובזמן הקלטה בלי הפסקה.',
            'מי שלא מכיר יבין "משהו של גלישה", אבל לא בטוח שיבין "מודד את היום שלי".')
    b = opt('ב', 'קו האור של היום, בקטן', z(rb2('idle', 'day')) + z(rb2('live', 'day')), z(rb2('idle', 'night')) + z(rb2('live', 'night')), fb,
            'אותו קו אור שמצייר את היום על ההר. מי שכבר הקליט יום, מזהה אותו מיד.',
            'בפעם הראשונה הוא נראה כמו גרף או מפה, לא כמו כפתור שמתחיל משהו. גם קטן מדי כדי לקרוא בכפפות.')
    c = opt('ג', 'המילה, עד שההקלטה הראשונה נגמרת', z(ra('word')), z(ra('word')), fc,
            'בפעם הראשונה אין ספק מה הכפתור עושה. אחרי שהקלטת פעם אחת, נשאר רק הסמל של א, ושום מילה לא תופסת מקום בסרגל.',
            'שני מצבים לכפתור, ולכן עוד מעבר לבנות ולבדוק.', rec=True)
    rec = ('<div class="card"><h2>ההמלצה: ג</h2>' + li(
        'באפליקציות הסקי שבדקנו (Slopes, Strava, Carv, Skitude, My Epic) הכפתור עם מילה, ו-Slopes שינו את מסך ההתחלה כי משתמשים חדשים לא הבינו איך מתחילים. לא מצאנו כפתור שברור בלי שום מילה.',
        'לכן הסמל לבד (א) טוב רק למי שכבר השתמש. ג נותן את המילה בדיוק כשצריך אותה, ואחר כך את הסמל.',
        'בג אין בועה ואין גיליון הסבר לפני ההקלטה הראשונה: המילה מספיקה.',
        'קורא מסך תמיד אומר "%s", גם כשרואים רק את הסמל.' % WORD,
        'בהפחתת תנועה: העקבות מצוירות בלי תנועה, והנקודה לא פועמת.')
        + q(1, '<b>איזו חלופה:</b> א, ב או ג? <b>(ההמלצה: ג)</b>') + '</div>')
    inner = ('<p class="sec">הערות על גרסה 56 · כפתור ההקלטה</p><h1>כפתור ההקלטה: הצורה אומרת מה הוא עושה</h1>'
             '<div class="rowx" style="align-items: stretch">%s%s%s</div>%s' % (a, b, c, rec))
    wb('RD00-RecButton.dc.html', 'כפתור ההקלטה: שלוש חלופות', 1280, HRD, inner)


def main():
    ph = phones()
    for fn, (title, body, fx) in ph.items():
        PB.phone(fn, title, body, night=fx == 'night', fixed=fx, lang='he')
    rd00()
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    for fn in list(j['boards']):
        if fn.startswith('RD'): del j['boards'][fn]
    y = max(v['y'] + v['h'] for v in j['boards'].values() if v.get('page') == PAGE) + 260
    V.PLACE.clear()
    V.PLACE.append(('RD00-RecButton.dc.html', 0, y, 1280, HRD, 'כפתור ההקלטה: שלוש חלופות'))
    y += HRD + 140
    for i, (fn, t) in enumerate([('RD1.dc.html', 'א · עקבות, ביום'), ('RD2.dc.html', 'א · בזמן הקלטה, בלילה'),
                                 ('RD3.dc.html', 'ב · קו האור, ביום'), ('RD4.dc.html', 'ב · בזמן הקלטה, בלילה'),
                                 ('RD5.dc.html', 'ג · בפעם הראשונה, ביום (ההמלצה)'), ('RD6.dc.html', 'ג · אחרי הקלטה ראשונה, בלילה')]):
        V.PLACE.append((fn, 470 * i, y, 390, 844, t))
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n')
    V.place_all(); print('ok', len(ph))


if __name__ == '__main__':
    main()
