#!/usr/bin/env python3
"""Completion round, part 8 (W00 to W07): large text (three screens at 130 percent), what the screen reader
says on the mountain, an Android tablet (the sheet becomes a side panel), the opening screen, and the store
screenshots in the new language. Built from the boards that already exist (S01, P01, R03, K10, L06, S05),
so nothing here is a new screen. The tablet board and the store board are sketches. Design only.
Usage: python3 design/redesign/tools/gap8_boards.py"""
import os,json,re,base64,io
from peak_boards import *
import gap_boards as GB
import gap3_boards as G3
import gap4_boards as G4
import gap5_boards as G5
import gap6_boards as G6
import gap7_boards as G7
from peak_b4 import veil
from gap_boards import li
from gap3_boards import ic
PROJ=OUT
inner=G7.inner
CSS12="""
.H .bt{position:absolute;left:0;top:0;width:300px;height:649px;transform:scale(1.3);transform-origin:0 0;overflow:hidden}
.H .bt .now{left:58px!important;right:10px!important;top:40px}
.H .bt .now span{white-space:normal!important}
.H .bt .meb{top:40px!important;left:8px!important}
.H .bt .kv4{grid-template-columns:repeat(2,1fr);row-gap:12px}
.H .bt .col2{flex-direction:column!important}
.H .bt .recp{top:40px}
.H .bt .dock2 .find span{display:none}
.H .bt .dock2 .find{flex:none;width:44px;padding:0;display:flex;align-items:center;justify-content:center}
.H .bt .dock2{justify-content:space-between}
.H .mk{position:absolute;width:26px;height:26px;border-radius:50%;background:#C2185B;color:#fff;font-size:14px;font-weight:700;display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 3px rgba(255,255,255,.9)}
.H .sr{display:flex;gap:12px;align-items:flex-start;padding:10px 0;border-bottom:1px solid var(--line)}
.H .sr .mk{position:static;flex:none;box-shadow:none;margin-top:2px}
.H .sr b{font-size:16px;line-height:1.35;display:block}
.H .sr span{font-size:13.5px;color:var(--ink2);line-height:1.4}
.H .sr span.mk{color:#fff;font-size:14px;line-height:1}
.H .shot{width:320px;height:692px;border-radius:30px;position:relative;overflow:hidden;flex:none;background:linear-gradient(180deg,#CFE0F6 0%,#EAF1FA 46%,#F7FAFD 100%)}
.H .night .shot{background:linear-gradient(180deg,#101B33 0%,#16233F 50%,#0B1220 100%)}
.H .shot .shc{position:absolute;left:20px;right:20px;top:34px;font-size:25px;font-weight:900;line-height:1.18;text-align:center;color:var(--ink)}
.H .shot .dev{position:absolute;left:19px;top:128px;width:282px;height:610px;border-radius:30px;overflow:hidden;box-shadow:0 18px 44px rgba(10,25,45,.34),0 0 0 1px var(--line)}
.H .shot .dev .in{position:absolute;left:0;top:0;width:390px;height:844px;transform:scale(.7231);transform-origin:0 0;overflow:hidden;background:var(--bg)}
"""
MB.CSS=MB.CSS+CSS12
def bt(src,more=''):
    return '<div class="bt">%s%s</div>'%(inner(src),more)
def b01():
    return bt('S01-Frame.dc.html')
def b02():
    s=inner('P01-Standing.dc.html')
    a='<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">';assert a in s
    return '<div class="bt">%s</div>'%s.replace(a,'<div class="col2" style="display: flex; gap: 8px"><button class="sec" style="flex: none">')
def b03():
    return bt('R03-GroupMeetups.dc.html')
SR=((1,'מצב ההר: 11 מתוך 14 רכבלים פתוחים, מינוס 7 מעלות','כפתור. פותח את ״ההר עכשיו״. הצבע של הנקודה לא נקרא; המילים אומרות הכל.',(340,102)),
    (2,'אני, נועה','כפתור. לאורח: ״אני, אורח״.',(22,102)),
    (3,'ההר. 28 מסלולים ו-14 רכבלים','תמונה אחת, עם פעולות: ״המסלול הבא״, ״המסלול הקודם״, ״חזרה למבט הפתיחה״. מסלול נקרא: ״Tatra 2, כחול, קל, 2.7 קילומטר״.',(182,330)),
    (4,'שכבות · איפה אני','שני כפתורים. ״איפה אני, כבוי״ או ״דלוק״.',(58,640)),
    (5,'הכפר · לאן יורדים? · מקליטים','״לאן יורדים?״ הוא הדרך לבחור מסלול בלי לראות את ההר: רשימה של כל המסלולים.',(182,738)))
def b04():
    ph='<div style="position: absolute; top: 28px; inset-inline-start: 40px; width: 390px; height: 844px; border-radius: 34px; overflow: hidden; box-shadow: 0 0 0 1px var(--line), 0 14px 40px rgba(10,25,45,.25)"><div style="position: absolute; inset: 0">%s%s</div></div>'%(inner('S01-Frame.dc.html'),''.join('<span class="mk" style="left: %dpx; top: %dpx">%d</span>'%(x,y,n) for n,_,_,(x,y) in SR))
    tx='<div style="position: absolute; top: 34px; inset-inline-start: 480px; inset-inline-end: 44px; display: flex; flex-direction: column; gap: 4px"><span class="st">קורא המסך · הסדר ומה נאמר</span><span class="ttl" style="font-size: 26px">ההר, בלי לראות אותו</span>'
    tx+=''.join('<div class="sr"><span class="mk">%d</span><div><b>״%s״</b><span>%s</span></div></div>'%(n,a,b) for n,a,b,_ in SR)
    tx+='<div style="display: flex; flex-direction: column; gap: 7px; padding-top: 12px; font-size: 14.5px; line-height: 1.45">%s</div></div>'%''.join('<span>%s</span>'%t for t in(
        '<b>הסדר:</b> מלמעלה למטה, ומההתחלה לסוף. גיליון שנפתח מקבל את המיקוד, נקרא בכותרת שלו, וכל מה שמתחתיו מוסתר מקורא המסך.',
        '<b>מספר ותווית נקראים יחד:</b> ״68.3 קילומטר לשעה, מהירות מרבית״, ולא שני פריטים.',
        '<b>צבע אף פעם לא לבדו:</b> דרגת קושי נאמרת במילים, ורכבל סגור נאמר ״סגור״.',
        '<b>בזמן הקלטה</b> קורא המסך מכריז רק על שינוי מצב (״על הרכבל Goodaura״, ״ירידה 4 נגמרה״), ולא על כל שנייה.',
        '<b>ציורים</b> (הרכס של היום, ״לרדת בסרט״) מוסתרים, ומה שהם אומרים נקרא כמשפט אחד.',
        '<b>הפחתת תנועה:</b> ההר לא מסתובב ולא מתקרב בתנועה; המבט מתחלף מיד. האור של הבחירה מופיע בבת אחת.'))
    return '<div style="position: absolute; inset: 0; background: var(--bg)"></div>'+ph+tx
def b05():
    s=inner('R03-GroupMeetups.dc.html');i=s.index('<div class="sheet glass"');sh=s[i:]
    j=sh.index('>');sh='<div class="sheet glass" style="left: 12px; right: auto; width: 400px; top: 108px; bottom: 12px; gap: 10px; overflow: hidden">'+sh[j+1:]
    sh=re.sub(r'<div style="position: absolute; left: 0; right: 0; top: 790px.*$','',sh,flags=re.S) if 'top: 790px' in sh else sh
    o='<img alt="" src="%s" style="position: absolute; left: -166px; top: 0; width: 1512px; height: 820px; max-width: none">'%U('wide-dawn-cloud')
    o+='<div class="now glass" style="left: auto; right: 64px; width: 360px"><span class="dot"></span><span style="white-space: nowrap">11 מתוך 14 רכבלים פתוחים · <span dir="ltr">−7°</span></span><span style="margin-inline-start: auto"><span class="eg">דוגמה</span></span></div>'
    o+='<button class="xb glass" style="position: absolute; top: 52px; right: 12px; padding: 0"><span class="av" style="background: #7A5AE0" aria-hidden="true">נ</span></button>'
    o+=G4.dock().replace('class="dock2 glass"','class="dock2 glass" style="left: auto; right: 12px; width: 420px"')
    o+=G4.ctl().replace('class="mc"','class="mc" style="left: auto; right: 12px"')
    return o+sh
def icon_uri():
    from PIL import Image
    im=Image.open(os.path.join(P.ROOT,'design/round9/export/store-icon-512.png')).convert('RGB').resize((192,192),Image.LANCZOS)
    b=io.BytesIO();im.save(b,'JPEG',quality=88);return 'data:image/jpeg;base64,'+base64.b64encode(b.getvalue()).decode()
def b06():
    o='<div class="d" style="position: absolute; inset: 0; background: linear-gradient(180deg,#BFD6F2 0%,#E6EFF9 60%,#F7FAFD 100%)"></div><div class="n" style="position: absolute; inset: 0; background: linear-gradient(180deg,#0B1220 0%,#14203A 100%)"></div>'
    o+='<img alt="הסמל של האפליקציה" src="%s" style="position: absolute; left: 141px; top: 368px; width: 108px; height: 108px; border-radius: 26px; box-shadow: 0 10px 28px rgba(10,25,45,.3)">'%icon_uri()
    return o
SHOTS=(('S01-Frame.dc.html','כל ההר,<br>באור של עכשיו'),('K10-Run.dc.html','כל מסלול:<br>כמה תלול, כמה ארוך'),('L06-DayPainted.dc.html','היום שלך,<br>מצויר על ההר'),
       ('S05-MountainNow.dc.html','מזג אוויר<br>בשלושה גבהים'),('R03-GroupMeetups.dc.html','נפגשים<br>על ההר'))
def b07():
    o='<div style="position: absolute; inset: 0; background: var(--bg)"></div><div style="position: absolute; left: 40px; top: 40px; display: flex; gap: 20px; direction: rtl">'
    for src,cap in SHOTS:
        o+='<div class="shot"><div class="shc">%s</div><div class="dev"><div class="in">%s</div></div></div>'%(cap,inner(src))
    return o+'</div>'
def brief():
    sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -120px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 8 · 23.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">לכל אחד, ובכל מסך</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">טקסט גדול, קורא מסך, טאבלט, מסך הפתיחה וצילומי החנות</p></div></div>')%(sc,U('wide-dawn-cloud'))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">החלק האחרון לפני המשחקים. אין כאן מסך חדש: אלה אותם מסכים, כשהטקסט גדול, כשלא רואים את המסך, כשהמסך רחב, וכשמציגים אותם בחנות.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוחות 1 עד 3</p><h2>טקסט גדול</h2><p class="p">ההר, עומדים ביום הגלישה, והמפגשים של הקבוצה, כשהטקסט בטלפון ב-130 אחוז.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוח 4</p><h2>קורא מסך</h2><p class="p">מה נקרא על ההר, באיזה סדר, ואיך בוחרים מסלול בלי לראות.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוח 5</p><h2>טאבלט</h2><p class="p">באנדרואיד בלבד: ההר על כל המסך, והגיליון הוא לוח צד.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 6, 7</p><h2>פתיחה וחנות</h2><p class="p">מסך הפתיחה, וחמישה צילומים לחנות עם כותרת לכל אחד.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>הטקסט גדל עם הגדרת הטלפון, עד 200 אחוז.</b> המספרים הענקיים (המהירות, המחיר, השעה של המפגש) גדלים עד 130 אחוז בלבד, כי הם כבר גדולים.',
        '<b>כשאין מקום, הפריסה משתנה, והטקסט לא נחתך:</b> ארבעת המספרים עוברים לשתי שורות של שניים; שני כפתורים יורדים זה מתחת לזה; הגלולה גדלה לשתי שורות; ב״לאן יורדים?״ נשאר רק הסמל. גיליון שלא נכנס נגלל.',
        '<b>יעדי מגע</b> לא קטנים אף פעם מתחת ל-44, וגדלים עם הטקסט.',
        '<b>ההר הוא פריט אחד לקורא המסך,</b> עם פעולות למעבר בין מסלולים. ״לאן יורדים?״ הוא הדרך המלאה לבחור מסלול בלי לראות.',
        '<b>טלפון: לאורך בלבד,</b> בשתי הפלטפורמות. באייפון זה כבר כך. <b>באנדרואיד זה שינוי:</b> היום האפליקציה מסתובבת. <b>אושר (פיני, 23.11.2026).</b>',
        '<b>טאבלט (אנדרואיד, מ-600 dp):</b> ההר על כל המסך בכל כיוון, והגיליון הוא לוח צד ברוחב 400, בצד הסוף. באייפון אין גרסה לאייפד.',
        '<b>מסך הפתיחה:</b> הסמל שאושר, על צבע השמיים של השפה החדשה, ביום ובלילה. אחריו ההר עולה בדעיכה של 300 מ״ש. בלי לוגו מונפש ובלי המתנה.',
        '<b>הסמל לא משתנה.</b> הוא אושר (סבב 9) והוא כבר בשתי החנויות.',
        '<b>צילומי החנות:</b> חמישה, לפי הסדר שבלוח, כותרת של שתי שורות מעל כל אחד. מתארים רק מה שבגרסה; לכן הרביעי הוא מזג האוויר ולא מצב הרכבלים, שעוד אין לו נתון.')+'</div>'
      '<div class="card" style="flex: 1"><h2>ביושר</h2>'+li(
        '<b>הטקסט הגדול הוא הדמיה:</b> בלוחות הכל מוגדל פי 1.3, גם הסמלים והרווחים. בטלפון רק הטקסט גדל. הלחץ על הפריסה דומה, לא זהה.',
        '<b>200 אחוז לא צויר.</b> הכללים אותם כללים, וצריך לבדוק באמולטור (חלק `bigtext` כבר קיים).',
        '<b>מה שקורא המסך אומר</b> הוא הצעה לנוסח, לסשן התוכן. לא נבדק עם TalkBack או VoiceOver.',
        '<b>לוח הטאבלט הוא סכמה:</b> התמונה הרחבה היא ציור אחד בשעת זריחה, בלי קווי המסלולים, כי אין עדיין מבט רחב עם הטלה. ביום בלבד.',
        '<b>נעילת הסיבוב באנדרואיד</b> חלה רק על טלפונים. מאנדרואיד 16, במסך של 600 dp ומעלה המערכת מתעלמת מנעילה כזו; לכן הטאבלט חייב לעבוד בשני הכיוונים. לארכיטקט.',
        '<b>צילומי החנות בלוח</b> הם הלוחות עצמם, עם ״דוגמה״. הצילומים האמיתיים יצולמו מהאפליקציה, כשהיא בנויה. הגדלים: לפי כל חנות.',
        '<b>הכותרות של צילומי החנות</b> אושרו בעברית (פיני, 23.11.2026). שאר השפות: סשן התוכן ואחראי החנויות.',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי.')+'</div></div>')
    wide('W00-Brief.dc.html','סבב ההשלמה, חלק 8: לכל אחד, ובכל מסך',body,1280,1420)
X=[0,470,940,1410]
def main():
    ph={'W01-BigText-Mountain.dc.html':('השלמה ח · 1 · טקסט גדול: ההר',b01()),'W02-BigText-Standing.dc.html':('השלמה ח · 2 · טקסט גדול: עומדים',b02()),
        'W03-BigText-Meetups.dc.html':('השלמה ח · 3 · טקסט גדול: מפגשים',b03()),'W06-Splash.dc.html':('השלמה ח · 6 · מסך הפתיחה',b06())}
    for fn,(t,b) in ph.items():phone(fn,t,b)
    MB.phone('W04-ScreenReader.dc.html','השלמה ח · 4 · קורא המסך על ההר',b04(),w=1180,h=900)
    def phone_fixed(fn):
        p=os.path.join(OUT,fn);t=open(p,encoding='utf8').read().replace('<div class="{{mode}}">','<div class="day">');open(p,'w',encoding='utf8').write(t)
    MB.phone('W05-Tablet.dc.html','השלמה ח · 5 · טאבלט (אנדרואיד)',b05(),w=1180,h=820);phone_fixed('W05-Tablet.dc.html')
    MB.phone('W07-StoreShots.dc.html','השלמה ח · 7 · צילומי החנות',b07(),w=1760,h=780)
    brief()
    H0=1420;p=os.path.join(PROJ,'canvas.json');j=json.load(open(p,encoding='utf8'))
    def put(fn,x,y,w,h,title):
        j['boards'][fn]=dict(x=x,y=y,w=w,h=h,title=title,page='gap8')
        if fn not in j['order']:j['order'].append(fn)
    put('W00-Brief.dc.html',0,0,1280,H0,'חלק 8: לכל אחד, ובכל מסך')
    y=H0+380
    for i,fn in enumerate(('W01-BigText-Mountain.dc.html','W02-BigText-Standing.dc.html','W03-BigText-Meetups.dc.html')):put(fn,X[i],y,390,844,ph[fn][0].split(' · ',2)[2])
    y2=y+1510;put('W04-ScreenReader.dc.html',0,y2,1180,900,'קורא המסך על ההר')
    y3=y2+900+420;put('W05-Tablet.dc.html',0,y3,1180,820,'טאבלט (אנדרואיד)')
    y4=y3+820+420;put('W06-Splash.dc.html',0,y4,390,844,'מסך הפתיחה');put('W07-StoreShots.dc.html',470,y4,1760,780,'צילומי החנות')
    if not any(pg['id']=='gap8' for pg in j['pages']):j['pages'].insert(2,dict(id='gap8',name='השלמה ח · לכל אחד, ובכל מסך'))
    T_=dict(kind='title1',maxW=1600,page='gap8',x=0)
    j['notes'].update({'tW':dict(T_,w=1200,y=-300,text='סבב ההשלמה · חלק 8 · לכל אחד, ובכל מסך (23.11.2026)'),'tW1':dict(T_,w=900,y=y-300,text='א · טקסט גדול (130 אחוז)'),
                       'tW2':dict(T_,w=620,y=y2-300,text='ב · קורא המסך'),'tW3':dict(T_,w=620,y=y3-300,text='ג · טאבלט'),'tW4':dict(T_,w=900,y=y4-300,text='ד · מסך הפתיחה וצילומי החנות')})
    open(p,'w',encoding='utf8').write(json.dumps(j,ensure_ascii=False,indent=1)+'\n');print('part 8 boards written: 8')
if __name__=='__main__':main()
