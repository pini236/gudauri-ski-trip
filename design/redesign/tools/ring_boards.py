#!/usr/bin/env python3
"""Y02 (25.11.2026), for Pini's note on 0.1.884 via the manager: "the mountain looks better, but it looks odd among all
the mountains, because they are plain, without special details. I'd want it to look more beautiful and breathtaking."
The board shows how the mountains around the live model get the same quality as the mountain itself, before and after,
from the same far view, by day, at sunset and at night; with the budget, a lighter alternative, and the recommendation.
The frames are from the design tool of the live model (live_ref.js, ring=now / ring=2 added for this board), in
design/redesign/assets/ring2/frames (585 by 1266), uploaded to the canvas as assets; the urls are below.
  python3 design/redesign/tools/ring_boards.py      write the board and update canvas.json
The frames (FROM_DISK=1 node design/redesign/tools/live_ref.js <dir> name=query ...), F = dist=10000&far=1:
  now-*        light=<morning|sunset|night>&F&ring=now      0.1.884: the ring of 700 m cells, no shadow, no relief
  res-m        light=morning&F&ring=2&rtex=0&rsh=0          step 1: 240 m cells near the mountain (24 km), 700 m past
  tex-m        light=morning&F&ring=2                       step 2: + the 60 m relief, the model's rock, cast shadows
  after-*      light=..&F&ring=2&air=2                      step 3: + the haze of the heights (the recommendation)
  lean-m       light=morning&F&ring=2&air=2&r2=ring2lean    the lighter alternative: 300 m to 22 km, 1400 m past, 90 m relief
  open-now / open-after   light=morning&ring=now / ring=2&air=2     the opening view
The data: PEAK_CACHE=/tmp/peak-cache python3 design/redesign/tools/ring2_export.py (and with RING_DST=ring2lean
RING_IN=22000 RING_CELL_IN=300 RING_CELL_OUT=1400 RING_TEX=90 for the lighter one)."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from live_boards import board, wide, PROJ

IMG = {'now-m': '2f9b815c6ef72dc5843e7a4049844c2c', 'after-m': '5fd1ac623bb99e0d78b630588d40ab27', 'res-m': 'b2277f729d62e1056e2914c6ddaf06b6',
       'tex-m': '1d1824d3a3b7729edbe98d768d76e6ea', 'lean-m': '06318424bd7ff889df0917cd1ae9ff59', 'now-s': '8282b8e09f4946901a4efe372fad802f',
       'after-s': 'fab62ec9037c1b1378f6cd0f0a8bf779', 'now-n': 'b212d745bd9d55508a692a1f72b6ee3f', 'after-n': 'e5eeca41aef75b62c89078a34b134e1a',
       'open-now': '85428fce83e920e82d423aec8917d6a8', 'open-after': 'b1349fbf6be7705080c7da412ed6a248'}
U = lambda k: '/_blob/' + IMG[k]
REC = '; border: 3px solid #6B3FD4'


def shot(k, w, cap, sub='', mark=''):
    h = round(w * 844 / 390)
    return ('<div class="fr" style="width: %dpx"><div class="th" style="width: %dpx; height: %dpx%s"><img alt="" src="%s"></div><p class="cap"><b>%s</b>%s</p></div>'
            % (w, w, h, mark, U(k), cap, ('<br>' + sub) if sub else ''))


def li(*xs): return '<ul>' + ''.join('<li>%s</li>' % x for x in xs) + '</ul>'


def table(rows, head=None, widths=None):
    td = 'padding: 7px 10px; vertical-align: top; text-align: right'
    h = ''
    if head: h = '<tr>' + ''.join('<th style="%s; font-weight: 700; border-bottom: 2px solid #10161D">%s</th>' % (td, c) for c in head) + '</tr>'
    return ('<table style="border-collapse: collapse; width: 100%; font-size: 14.5px; line-height: 1.45">' + h
            + ''.join('<tr style="border-top: 1px solid #D9DEE4">' + ''.join('<td style="%s%s">%s</td>' % (td, ('; width: %s' % widths[i]) if widths and widths[i] else '', c) for i, c in enumerate(r)) + '</tr>' for r in rows)
            + '</table>')


HY2 = 4640


def y02():
    hero = (shot('now-m', 370, 'היום (0.1.884), בוקר', 'מתרחקים עד הסוף (10 ק״מ). מסביב: גושים חלקים באובך.')
            + shot('after-m', 370, 'אחרי (ההמלצה)', 'אותו מבט. קזבק, הרכסים והסלע, באותו אור ובאותו שלג של ההר.', REC))
    why = ('<div class="card" style="flex: 1"><p class="sec">למה זה נראה "פשוט"</p><h2>שלושה דברים, לא אחד</h2>' + li(
        '<b>הרשת גסה פי 17 מההר:</b> תאים של 700 מ׳ מול 40 מ׳. רכס שלם נכנס לתא אחד, ולכן נשארות רק גבעות עגולות, וקזבק נמוך ב-130 מ׳ (4,853 במקום 4,981 בנתונים).',
        '<b>אין עליהם פרטים:</b> ההר מקבל מפת תבליט מצוירת (שיפועים, שקעים, סלע לאורך קו הנפילה). הטבעת מקבלת רק את הנורמלים של הרשת הגסה, ולכן גם הסלע נראה ככתמים.',
        '<b>האובך בולע אותם:</b> האובך של ההר מתחיל במרחק המצלמה ונגמר בערך בפעמיים ממנו. כל מה שמעבר להר מקבל כ-70 אחוז מצבע האופק, בלי קשר לגובה. לכן הכל נראה אותו דבר.',
        '<b>ומה שעובד, ונשאר:</b> אותה שמש, אותו שלג בשני צבעים, אותו אריח של אדוות, ואין קצה.') + '</div>')
    steps = (shot('now-m', 270, 'היום', 'תאים של 700 מ׳, בלי צל ובלי תבליט.')
             + shot('res-m', 270, '1 · רשת עדינה ליד ההר', '240 מ׳ עד 24 ק״מ, 700 מ׳ אחר כך. הצורות נכונות, אבל הסלע כתמים.')
             + shot('tex-m', 270, '2 · תבליט, סלע וצל', 'מפת תבליט ב-60 מ׳, הסלע של ההר, וצל שהרכסים מטילים.')
             + shot('after-m', 270, '3 · אוויר של גובה', 'הפסגות הגבוהות מעל האובך, העמקים בתוכו. זה מה שמסדר אותם בשכבות.', REC))
    did = ('<div class="card"><p class="sec">מה ביקשת לבדוק, ומה יצא</p>' + table([
        ('רזולוציה ליד ההר', '<b>כן.</b> טבעת פנימית ב-240 מ׳ עד 24 ק״מ לכל צד (קזבק בתוכה, 21 ק״מ צפונה), וטבעת חיצונית ב-700 מ׳ עד 42 ק״מ, כמו היום. על רכס, הנקודה לוקחת את הדגימה הגבוהה בתא שלה, כדי שקו הרקיע לא יתקצץ.'),
        ('אותו אריח של שלג וסלע', '<b>כן.</b> אותו אריח, אותן אדוות. הסלע עם הגרעין של ההר, וגם ממנו נקרא בקנה מידה גדול (900 עד 5,200 מ׳), כדי שהגרעין ייראה גם מ-25 ק״מ ולא יתמזג לכתם אפור.'),
        ('צל ואור לפי השעה', '<b>כן.</b> צל שכל רכס מטיל, לכל נקודה ברשת, מחושב יחד עם הצל של ההר (אותו מפתח של 10 דקות, מחוץ לציור). בשקיעה הפסגות מקבלות את האור הוורוד, והעמקים בצל.'),
        ('אובך שמדרג את המרחק', '<b>כן, אבל אחר משחשבתי.</b> אובך רגיל לפי מרחק לא מספיק: מה שרחוק נמחק. מה שעובד הוא אוויר שתלוי בגובה: מעל 2,800 מ׳ האוויר דליל (35 אחוז מהצפיפות ב-4,600 מ׳). כך הקווקז נשאר חד, והעמקים שביניהם נמסים, וזה מה שיוצר שכבות.'),
        ('פסגות אמיתיות', '<b>כן, כשהן בנתונים.</b> קזבק (4,981 מ׳ בנתונים הפתוחים, מהדגימה הגבוהה בתא) ושאר הקווקז הגבוה. שם על קזבק: רעיון קיים, לא בלוח הזה.'),
        ('רכסים חדים על השמיים', '<b>בחלקו.</b> 240 מ׳ ושמירת הדגימה הגבוהה נותנים קו רקיע עם שיניים, וה-MSAA שכבר יש באפליקציה מחליק אותו. מעבר ל-24 ק״מ (700 מ׳) הרכסים עדיין רכים, אבל שם הם באוויר ממילא.'),
        ('החלקות ("המודל זז חלק")', '<b>נשמרת, בתנאי תקציב.</b> הכל סטטי: הרשת נבנית פעם אחת, הצל מחושב מחוץ לציור רק כשהאור משתנה, ובציור יש קריאה אחת נוספת של מרקם לכל פיקסל. הסיכון היחיד הוא מספר המשולשים, ולכן יש גם חלופה קלה.'),
    ], widths=['21%', None]) + '</div>')
    hours = (shot('now-s', 270, 'היום, שקיעה') + shot('after-s', 270, 'אחרי, שקיעה', 'אור ורוד על הקווקז, צל בעמקים.')
             + shot('now-n', 270, 'היום, לילה') + shot('after-n', 270, 'אחרי, לילה', 'הרכסים באור הירח, והעמקים כהים.'))
    opens = (shot('open-now', 270, 'מבט הפתיחה, היום') + shot('open-after', 270, 'מבט הפתיחה, אחרי', 'גם מהפתיחה רואים את ההרים מאחורי Sadzele. ההר עצמו כמעט לא משתנה.')
             + shot('lean-m', 270, 'החלופה הקלה', '300 מ׳ עד 22 ק״מ, 1,400 מ׳ אחר כך, תבליט ב-90 מ׳. הקרוב כמעט זהה; הרחוק רך יותר.'))
    bud = ('<div class="card" style="flex: 1"><p class="sec">תקציב</p><h2>כמה זה עולה, מול מה שאושר</h2>' + table([
        ('משולשים בטבעת', '19.5 אלף', '<b>92 אלף</b>', '44 אלף', '40 אלף'),
        ('קובץ הגבהים (דחוס)', '29 ק״ב', '<b>כ-103 ק״ב</b>', 'כ-49 ק״ב', '64 ק״ב'),
        ('מפת התבליט (WebP)', 'אין', '<b>156 ק״ב</b>', '74 ק״ב', 'לא הוגדר'),
        ('זיכרון גרפי למפה', 'אין', 'כ-3.4 מ״ב', 'כ-1.3 מ״ב', 'בתוך 32 מ״ב'),
        ('נקודות לצל (מחוץ לציור)', 'אין', 'כ-55 אלף', 'כ-25 אלף', 'לא הוגדר'),
    ], head=['', 'היום', 'ההמלצה', 'החלופה הקלה', 'מה שאושר'], widths=['31%', '15%', '19%', '19%', '16%'])
        + '<p class="cap" style="margin-top: 8px">ההר עצמו כ-288 אלף משולשים, כך שההמלצה מוסיפה כשליש. אפשר כנראה לחסוך בלי לאבד: רשת שמתעבה בהדרגה עם המרחק במקום שתי טבעות. זה לארכיטקט ולמבצע. מפת התבליט: 800 על 800 פיקסלים בהמלצה, 489 על 489 בחלופה.</p></div>')
    rec = ('<div class="card" style="flex: 1"><p class="sec">ההמלצה</p><h2>שלושת הצעדים יחד</h2><ol>'
           '<li><b>שתי טבעות:</b> 240 מ׳ עד 24 ק״מ, ו-700 מ׳ עד 42 ק״מ. על רכסים, הדגימה הגבוהה בתא.</li>'
           '<li><b>מפת תבליט ב-60 מ׳</b> מעל הטבעת הפנימית, מאותם אריחי גובה פתוחים (זום 11), עם הסלע של ההר וצל מוטל.</li>'
           '<li><b>אוויר של גובה,</b> אחד להר ולטבעת: עד פעם וחצי ממרחק המצלמה בדיוק כמו היום, ואחר כך אובך איטי (25 ק״מ) שדליל מעל 2,800 מ׳.</li>'
           '<li><b>אם הארכיטקט לא מאשר את התקציב:</b> החלופה הקלה. היא קרובה מאוד מכל מבט שנבדק, ובתוך 64 ק״ב.</li></ol>'
           '<p class="cap">המסירה למבצע: <span dir="ltr">design/handoff/2026-11-25-redesign-ring-detail</span></p></div>')
    hon = ('<div class="card"><h2>ביושר</h2>' + li(
        '<b>הפריימים מכלי העיצוב בדפדפן,</b> עם אותו עולם ואותה מצלמה של המנוע, לא מהטלפון. את הצילום שלך לא ראיתי: "המבט הרחוק" כאן הוא המרחק המרבי (10 ק״מ) מכיוון הפתיחה.',
        '<b>כל הגבהים אמיתיים,</b> מאותם אריחי גובה פתוחים של ההר. השלג מצויר כמו על ההר: לא ידוע איפה בעמקים שמסביב אין שלג, ואין יער.',
        '<b>האוויר של הגובה משנה מעט גם את ההר:</b> החלק הרחוק שלו (מעבר לפעם וחצי ממרחק המצלמה) קצת פחות מעורפל. במבט הפתיחה כמעט לא רואים הבדל.',
        '<b>התפר בחזית:</b> כשמסתכלים ישר על הקצה הדרומי של ההר, נראה קו דק בין ההר לטבעת. הוא קיים גם היום. לא פתרתי אותו בכלי; המבצע יבדוק בטלפון.',
        '<b>לא נמדד בטלפון:</b> קצב הפריימים, הזיכרון וזמן חישוב הצל.') + '</div>')
    qs = ('<div class="card"><h2>שאלות אליך</h2>'
          '<div class="q"><span class="qn">1</span><span><b>להביא את ההרים שמסביב לאיכות של ההר:</b> רשת עדינה, תבליט, צל ואוויר של גובה. <b>(ההמלצה: כן, שלושת הצעדים יחד)</b></span></div>'
          '<div class="q"><span class="qn">2</span><span><b>אם התקציב גדול מדי לארכיטקט:</b> החלופה הקלה, או לחכות לרשת שמתעבה עם המרחק. <b>(ההמלצה: החלופה הקלה, כדי לא לחכות)</b></span></div></div>')
    inner = ('<p class="sec">הערה שלך על 0.1.884 · 25.11.2026</p><h1>ההרים מסביב, באותה איכות של ההר</h1>'
             '<p class="lead">"ההר נראה יותר טוב, אבל זה נראה מוזר בין כל ההרים, כי הם פשוטים בלי פרטים מיוחדים. הייתי רוצה שזה ייראה יותר יפה ומרהיב." '
             'זה לפני ואחרי מאותו מבט רחוק, ביום, בשקיעה ובלילה, עם מה שזה עולה. ההמלצה: שלושה צעדים יחד.</p>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>'
             '<h2 style="margin-top: 4px">צעד אחר צעד, מאותו מבט</h2><div class="rowx">%s</div>%s'
             '<h2 style="margin-top: 4px">בשקיעה ובלילה</h2><div class="rowx">%s</div>'
             '<h2 style="margin-top: 4px">מבט הפתיחה, והחלופה הקלה</h2><div class="rowx">%s</div>'
             '<div class="rowx" style="align-items: stretch">%s%s</div>%s%s'
             % (hero, why, steps, did, hours, opens, bud, rec, hon, qs))
    return board('ההרים מסביב', 1280, HY2, wide(1280, HY2, inner))


def main():
    open(os.path.join(PROJ, 'Y02-Ring.dc.html'), 'w', encoding='utf8').write(y02())
    p = os.path.join(PROJ, 'canvas.json'); j = json.load(open(p, encoding='utf8'))
    j['boards']['Y02-Ring.dc.html'] = dict(x=1480, y=0, w=1280, h=HY2, title='ההרים מסביב, באותה איכות של ההר', page='edge')
    if 'Y02-Ring.dc.html' not in j['order']: j['order'].append('Y02-Ring.dc.html')
    open(p, 'w', encoding='utf8').write(json.dumps(j, ensure_ascii=False, indent=1) + '\n'); print('Y02 written')


if __name__ == '__main__': main()
