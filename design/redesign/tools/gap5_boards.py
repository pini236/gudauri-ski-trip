#!/usr/bin/env python3
"""Completion round, part 5 (T00 to T10): the screens that were described in words only. A new group,
"I am already in the group", waiting for an admin, the member menu, the name and dates, leaving and
deleting, deleting the account, the licences, the whole trip form, and the list of places by a station.
The people, the group, the flights and the code are invented examples; the places by the station, the
library names and the licences are real. Design only.
Usage: python3 design/redesign/tools/gap5_boards.py"""
import os,json
from peak_boards import *
import gap3_boards as G3
from peak_b4 import EG,veil,fade
from gap_boards import CHEV,XI,li
from gap2_boards import topbar
from gap3_boards import ic,rl,av,sw,CHL,GRP,MEM,P_MORE,P_DOC,P_PLUS
PROJ=OUT
P_CAL='<rect x="4" y="5.500" width="16" height="14" rx="2.500"/><path d="M4 10h16M8.500 3.500v4M15.500 3.500v4"/>'
P_CLOCK='<circle cx="12" cy="12" r="8.500"/><path d="M12 7.500V12l3 2"/>'
P_HOUR='<path d="M7 3.500h10M7 20.500h10M8 3.500c0 5 8 6 8 8.500s-8 3.500-8 8.500M16 3.500c0 5-8 6-8 8.500s8 3.500 8 8.500"/>'
P_FORK='<path d="M7 3v7a2 2 0 0 0 4 0V3M9 12v9M16 3c-2 2-2.500 5-2.500 8H16v10"/>'
P_CUP='<path d="M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM16 10.500h1.500a2.500 2.500 0 0 1 0 5H16M8 3.500v2.500M12 3.500v2.500"/>'
P_AID='<path d="M12 5v14M5 12h14"/>'
P_WC='<circle cx="8" cy="5.500" r="1.800"/><circle cx="16" cy="5.500" r="1.800"/><path d="M8 9v11M5.500 13h5M16 9l-2.500 7h5zM16 16v4"/>'
P_CHECK='<path d="m5 12.500 4.500 4.500L19 7.500"/>'
CSS9="""
.H .fl2{display:flex;flex-direction:column;gap:5px}
.H .fl2>span{font-size:12.5px;font-weight:700;color:var(--ink2)}
.H .inp.v{color:var(--ink)}
.H .inp.cur{border:2px solid var(--acc)}
.H .dp{flex:1;min-width:0;height:52px;border-radius:16px;border:1px solid var(--line);background:var(--glass);display:flex;flex-direction:column;justify-content:center;gap:0;padding:0 14px;box-sizing:border-box}
.H .dp span{font-size:11.5px;color:var(--ink2)}
.H .dp b{font-size:15.5px;font-weight:500;font-variant-numeric:tabular-nums}
.H .rad{width:22px;height:22px;border-radius:50%;border:1.5px solid var(--ink2);box-sizing:border-box;flex:none;display:inline-block}
.H .rad.on{border:7px solid var(--acc)}
.H .menu{position:absolute;border-radius:14px;padding:4px 0;min-width:216px;box-sizing:border-box;box-shadow:0 14px 34px rgba(10,25,45,.34)}
.H .menu div{min-height:46px;display:flex;align-items:center;padding:0 16px;font-size:15.5px}
.H .menu div+div{border-top:1px solid var(--line)}
.H .conf{background:var(--red);color:#fff;border-radius:14px;margin:0 -8px;padding:0 8px}
.H .night .conf{color:#1B0705}
.H .conf .h{font-weight:700}
.H .pri.delb{background:var(--red);color:#fff}.H .night .pri.delb{color:#1B0705}
.H .chip{height:36px;border-radius:18px;border:1px solid var(--line);padding:0 12px;display:inline-flex;align-items:center;gap:6px;font-size:14px;font-weight:500;white-space:nowrap;font-variant-numeric:tabular-nums}
.H .chip.add{color:var(--acc);border-style:dashed}
.H .ap{font-size:27px;font-weight:300;direction:ltr;letter-spacing:.01em;line-height:1}
.H .kd{width:32px;height:32px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;color:#fff;flex:none}
.H .pr{display:flex;align-items:center;gap:12px;min-height:50px}
.H .pr+.pr{border-top:1px solid var(--line)}
.H .pr .c{display:flex;flex-direction:column;gap:0;min-width:0;flex:1}
.H .pr .c b{font-size:15px;font-weight:500;text-align:start}
.H .pr .c span{font-size:12.5px;color:var(--ink2)}
.H .pr .dist{font-size:13.5px;color:var(--ink2);white-space:nowrap;font-variant-numeric:tabular-nums}
"""
MB.CSS=MB.CSS+CSS9
PL=json.load(open(os.path.join(P.ROOT,'site/data/places.json')))
KC=dict(food=('#E8702A',P_FORK,'אוכל'),cafe=('#8A5A33',P_CUP,'בית קפה'),aid=('#1E9E4A',P_AID,'עמדת הצלה'),wc=('#6A4FB5',P_WC,'שירותים'))
def hero_title(pre,dates=True,people=None):
    o='<div style="position: absolute; left: 20px; right: 20px; top: 118px; display: flex; flex-direction: column; align-items: center; gap: 8px; text-align: center; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span style="font-size: 15px; font-weight: 500">%s</span><span style="font-size: 44px; font-weight: 700; line-height: 1.05">%s</span>%s</div>'%(pre,GRP,'<span style="font-size: 15px; font-weight: 500">10.1 עד 15.1.2027</span>' if dates else '')
    if people is not None:
        o+='<div style="position: absolute; left: 0; right: 0; top: 262px; display: flex; flex-direction: column; align-items: center; gap: 8px">%s<span class="glass" style="height: 28px; border-radius: 14px; padding: 0 12px; display: inline-flex; align-items: center; font-size: 13px; font-weight: 500">6 חברים</span></div>'%(('<span style="display: flex; --paper: #fff">%s</span>'%''.join(av(n,'s') for n in MEM)) if people else '')
    return o
def field(lab,val,cls='v'):
    return '<div class="fl2"><span>%s</span><div class="inp %s">%s</div></div>'%(lab,cls,val)
def dp(lab,val):
    return '<div class="dp"><span>%s</span><b>%s</b></div>'%(lab,val)
def members_sheet(extra='',top=254,scrolled=False):
    more='<button class="xb" aria-label="פעולות" style="margin-inline-end: -10px">%s</button>'%ic(P_MORE,22)
    i='<div>'
    if not scrolled:
        i+=rl(av('נועה'),'נועה <span class="m" style="display: inline">(את/ה)</span>','הלוך 10.1 · <bdi>GD 201</bdi>','<span class="role">מנהל</span>')
        i+=rl(av('איתי'),'איתי','הלוך 10.1 · <bdi>GD 201</bdi>','<span class="role">מנהל</span>'+more)
    if not scrolled:i+=rl(av('דנה'),'דנה','הלוך 10.1 · <bdi>GD 201</bdi>',more)+rl(av('יואב'),'יואב','הלוך 10.1 · <bdi>GD 201</bdi>',more)
    i+=rl(av('מאיה'),'מאיה','הלוך 11.1 · <bdi>W6 7701</bdi>',more)+rl(av('רון'),'רון','עוד בלי טיסה',more)+'</div>'
    return G3.gsheet(3,i+extra,top=top)
def build():
    out={}
    # ---- T01: a new group
    def b01():
        o=pic('hero','sunset','night')+veil(0,844,'var(--veil)')+topbar('קבוצה חדשה',EG)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div>'
        o+=field('שם הקבוצה',GRP,'v cur')
        o+='<div style="display: flex; gap: 8px">%s%s</div>'%(dp('מתאריך','10.1.2027'),dp('עד תאריך','15.1.2027'))
        o+='<span class="sub" style="font-size: 12.500px; margin-top: -6px">התאריכים מ״הטיול שלך״. אפשר לשנות.</span>'
        o+=field('איך קוראים לך בקבוצה?','נועה')
        o+='<div style="display: flex; align-items: center; gap: 10px; min-height: 44px"><span style="font-size: 15px; font-weight: 500">להציג את הטיסה שלי בקבוצה</span><span style="margin-inline-start: auto">%s</span></div>'%sw(True)
        o+='<button class="pri" style="flex: none">יצירת הקבוצה</button></div>'
        return o
    out['T01-NewGroup.dc.html']=('השלמה ה · 1 · קבוצה חדשה',b01())
    # ---- T02: I am already in the group
    def b02():
        o=pic('hero','sunset','night')+veil(0,844,'var(--veil)')+topbar('',EG)+hero_title('חוזרים ל',dates=False)
        o+='<div class="sheet glass" style="gap: 8px"><div class="grab"></div><span style="font-size: 17px; font-weight: 700">אני כבר בקבוצה</span><span class="sub">בוחרים את השם שלך ב%s. מנהל מאשר, ואת/ה חוזר/ת עם כל מה שהיה לך: הטיסה, המפגשים והשיאים.</span><div>'%GRP
        for n in MEM:o+=rl(av(n),n,'','<span class="rad%s" aria-hidden="true"></span>'%(' on' if n=='דנה' else ''))
        o+='</div><button class="pri" style="flex: none">שליחת בקשה למנהל</button></div>'
        return o
    out['T02-AlreadyIn.dc.html']=('השלמה ה · 2 · אני כבר בקבוצה',b02())
    # ---- T03: waiting for an admin
    def b03():
        o=pic('hero','sunset','night')+veil(0,844,'var(--veil)')+topbar('',EG)+hero_title('מצטרפים ל',people=False)
        o+='<div class="sheet glass" style="gap: 10px; align-items: center; text-align: center; padding-top: 12px"><div class="grab"></div><span class="ic" style="width: 52px; height: 52px; background: var(--line); margin-top: 6px">%s</span>'%ic(P_HOUR,26)
        o+='<span style="font-size: 19px; font-weight: 700">ממתין לאישור המנהל</span><span class="sub" style="max-width: 290px">הבקשה נשלחה למנהל. כשיאשר, הקבוצה תופיע כאן.</span><button class="lnk">ביטול הבקשה</button></div>'
        return o
    out['T03-Pending.dc.html']=('השלמה ה · 3 · ממתין לאישור מנהל',b03())
    # ---- T04: the member menu (an admin looks)
    def b04():
        o=pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+G3.mount_top('נ')+members_sheet()
        o+=veil(0,844,'rgba(6,11,22,.30)')
        o+='<div class="menu glass" style="left: 22px; top: 556px; background: var(--bg)"><div>מינוי למנהל</div><div>מילוי בשבילו</div><div class="danger">הוצאה מהקבוצה</div></div>'
        return o
    out['T04-MemberMenu.dc.html']=('השלמה ה · 4 · חברים: התפריט של חבר',b04())
    # ---- T05: the name and the dates
    def b05():
        o=pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+G3.mount_top('נ')+veil(0,844,'rgba(6,11,22,.38)')
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: center; min-height: 40px"><span class="ttl">שם ותאריכים</span><span style="margin-inline-start: auto">%s</span></div>'%EG
        o+=field('שם הקבוצה',GRP,'v cur')+'<div style="display: flex; gap: 8px">%s%s</div>'%(dp('מתאריך','10.1.2027'),dp('עד תאריך','15.1.2027'))
        o+='<div style="display: flex; gap: 8px"><button class="pri">שמירה</button><button class="sec">ביטול</button></div></div>'
        return o
    out['T05-NameDates.dc.html']=('השלמה ה · 5 · מנהל: שם ותאריכים',b05())
    # ---- T06: leaving and deleting, the second touch
    def b06():
        ex='<div style="border-top: 1px solid var(--line); margin-top: 4px">'+rl('','שם ותאריכים','',CHL)+rl('','הגדרות ההזמנה','',CHL)+rl('','<span class="danger">עזיבת הקבוצה</span>')
        ex+='<div class="rl conf"><span class="c"><span class="h">הקבוצה תימחק לכולם. לחיצה נוספת מוחקת.</span></span></div></div>'
        o=pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+G3.mount_top('נ')+members_sheet(ex,top=286,scrolled=True)
        return o
    out['T06-LeaveDelete.dc.html']=('השלמה ה · 6 · מנהל: עזיבה ומחיקה, הנגיעה השנייה',b06())
    # ---- T07: deleting the account
    def b07():
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('מחיקת חשבון',EG)
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 116px; padding: 14px 16px 6px"><span class="st">מה נמחק</span>'
        for t in('החשבון, השם ודרכי הכניסה','הטיול שלך והשיאים שבשרת','החברות בקבוצות והטיסות שהזנת. מפגשים שיצרת נשארים בקבוצה, בלי השם שלך.'):
            o+='<div style="display: flex; gap: 12px; align-items: flex-start; padding: 10px 0; border-top: 1px solid var(--line); margin-top: 8px"><span style="color: var(--red); flex: none; padding-top: 2px">%s</span><span style="font-size: 15px; line-height: 1.4">%s</span></div>'%(XI,t)
        o+='<span class="sub" style="display: block; border-top: 1px solid var(--line); padding: 10px 0 8px">הכל נמחק מיד, בלי תקופת המתנה. מה ששמור בטלפון נשאר, עד שמוחקים את האפליקציה. בקבוצה שרק את/ה מנהל/ת בה, המנהלות עוברת לחבר אחר.</span></div>'
        o+='<div class="sheet glass" style="gap: 10px; text-align: center"><span class="danger" style="font-size: 14.500px">בטוח? אי אפשר לבטל. לחיצה נוספת מוחקת.</span><button class="pri delb" style="flex: none">מחיקת החשבון שלי</button><button class="lnk" style="color: var(--ink)">ביטול</button></div>'
        return o
    out['T07-DeleteAccount.dc.html']=('השלמה ה · 7 · מחיקת חשבון: האישור',b07())
    # ---- T08: the licences
    def b08():
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('רישיונות','')
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 112px">'+rl('','צליל הקריעה בכרטיס','<bdi>everythingsounds</bdi> ב-<bdi>Freesound</bdi> · <bdi>CC BY 4.0</bdi>',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 186px"><span class="st" style="display: block; padding-top: 10px">גופנים</span>'+rl('','<bdi>Heebo</bdi>','<bdi>SIL Open Font License 1.1</bdi>',CHL)+rl('','<bdi>Noto Sans Georgian</bdi>','<bdi>SIL Open Font License 1.1</bdi>',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 340px"><span class="st" style="display: block; padding-top: 10px">ספריות</span>'
        for n,l in(('Jetpack Compose, AndroidX','Apache License 2.0'),('Kotlin, kotlinx','Apache License 2.0'),('OkHttp','Apache License 2.0'),('PostHog Android','MIT'),('Sentry Android','MIT')):o+=rl('','<bdi>%s</bdi>'%n,'<bdi>%s</bdi>'%l,CHL)
        o+=rl('','תנאי ה-<bdi>SDK</bdi> של אנדרואיד','<bdi>Sign in with Google, Install Referrer</bdi>',CHL.replace('m15 6-6 6 6 6','M9 7h8v8M17 7 7 17'))+'</div>'
        return o+fade(790,54)
    out['T08-Licences.dc.html']=('השלמה ה · 8 · רישיונות',b08())
    # ---- T09: the whole trip form
    def b09():
        chip=lambda p,t,add=False:'<span class="chip%s">%s%s</span>'%(' add' if add else '',ic(p,16) if p else '',t)
        leg=lambda a,b,auto='':'<div style="display: flex; align-items: baseline; gap: 10px"><span style="direction: ltr; display: inline-flex; align-items: baseline; gap: 10px"><span class="ap">%s</span><span style="color: var(--ink2)" aria-hidden="true">→</span><span class="ap">%s</span></span>%s</div>'%(a,b,('<span class="sub" style="font-size: 12.500px; margin-inline-start: auto">%s</span>'%auto) if auto else '')
        bx='<div class="glass box" style="padding: 12px 16px 14px; display: flex; flex-direction: column; gap: 10px"><div style="display: flex; align-items: center"><span class="st">%s</span><span class="sub" style="font-size: 12.500px; margin-inline-start: auto">%s</span></div>%s<div style="display: flex; gap: 6px; flex-wrap: wrap">%s</div></div>'
        o=pic('hero','sunset','night')+veil(0,844,'var(--veil)')+topbar('הטיול שלך',EG)
        o+='<div style="position: absolute; left: 12px; right: 12px; top: 112px; display: flex; flex-direction: column; gap: 12px">'
        o+=bx%('הלוך','חובה רק התאריך',leg('TLV','TBS'),chip(P_CAL,'10.1.2027')+chip(P_CLOCK,'15:30')+chip(P_CLOCK,'20:05')+chip('','<bdi>GD 201</bdi>'))
        o+=bx%('חזור','אפשר גם בלי',leg('TBS','TLV','אוטומטית'),chip(P_CAL,'15.1.2027')+chip('','+ שעה',True)+chip('','+ מספר',True))
        o+='<div class="glass box">'+rl(ic(P_CAL,20),'11.1 עד 14.1 · 4 ימים','ימי סקי מלאים, מחושב מהטיסות','<button class="lnk" style="min-height: 44px">שינוי</button>')+'</div>'
        o+='<span style="padding: 0 8px; font-size: 12.500px; font-weight: 500; text-shadow: 0 0 18px var(--halo)">בלי הרשמה. נשמר רק בטלפון.</span></div>'
        o+='<div class="sheet glass" style="gap: 6px"><button class="pri" style="flex: none">שמירה</button><button class="lnk danger">מחיקת הטיול</button></div>'
        return o
    out['T09-TripForm.dc.html']=('השלמה ה · 9 · הטיול שלך: הטופס המלא',b09())
    # ---- T10: every place by a station
    st=[s for s in PL['stations'] if s['id']=='goodaura-bottom'][0];PN={q['id']:q for q in PL['places']}
    rows=sorted(st['places'],key=lambda q:q['d'])
    def b10():
        o=pic('hero')+both(lambda m,l:P.runs_svg(VH,m,dim=True))+both(lambda m,l:P.lifts_svg(VH,m,l,dim=True,cabins=False))+G3.mount_top('נ')+veil(0,844,'rgba(6,11,22,.38)')
        o+='<div class="sheet glass" style="top: 150px; gap: 8px; overflow: hidden"><div class="grab"></div><div style="display: flex; flex-direction: column; gap: 1px"><span class="ttl">ליד התחנה התחתונה</span><span class="sub"><bdi>Goodaura</bdi> · %d מקומות, עד 250 מ׳</span></div><div>'%len(rows)
        for q in rows[:11]:
            p=PN[q['p']];c,pth,kn=KC[p['kind']]
            o+='<div class="pr"><span class="kd" style="background: %s" aria-hidden="true">%s</span><span class="c"><b>%s</b><span>%s%s</span></span><span class="dist">%d מ׳</span></div>'%(c,ic(pth,18,1.8),('<bdi>%s</bdi>'%p['name']) if p.get('name') else kn,kn if p.get('name') else 'בלי שם במפה',' · עונתי' if p.get('seasonal') else '',q['d'])
        o+='</div></div>'+fade(770,74)+'<div style="position: absolute; left: 0; right: 0; bottom: 18px; text-align: center; font-size: 12.500px; color: var(--ink2)">כדאי לבדוק במקום</div>'
        return o
    out['T10-Places.dc.html']=('השלמה ה · 10 · כל המקומות ליד תחנה',b10())
    return out
def brief():
    sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -120px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 5 · 23.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">מה שתואר רק במילים</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">עשרה מסכים שכבר הוחלטו בחלקים 2 ו-3, ועכשיו גם מצוירים</p></div></div>')%(sc,U('wide-dawn-cloud'))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">בחלקים הקודמים עשרה מסכים נכתבו במסירה בלי לוח: ״אותם רכיבים״. כאן הם מצוירים, כדי שתראה אותם לפני שנבנים. אין כאן החלטה חדשה על ההתנהגות: הכל כמו באפליקציה של היום, בשפה החדשה. <b>אושר (פיני, 23.11.2026).</b> הערות שיגיעו אחר כך ישנו את הלוח, ולא מעכבות את המימוש.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוחות 1 עד 3</p><h2>נכנסים לקבוצה</h2><p class="p">קבוצה חדשה (שם, תאריכים מהטיול שלך, השם שלך, והאם להציג את הטיסה). ״אני כבר בקבוצה״: בוחרים את השם, ומנהל מאשר. ו״ממתין לאישור המנהל״, עם ביטול.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 4 עד 6</p><h2>מנהלים קבוצה</h2><p class="p">התפריט של חבר (מינוי, מילוי טיסה בשבילו, הוצאה), שינוי השם והתאריכים, ועזיבה ומחיקה בשתי נגיעות. הגדרות ההזמנה כבר מצוירות בלוח ״מזמינים חברים״.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 7 עד 10</p><h2>חשבון, רישיונות, טיול, מקומות</h2><p class="p">האישור של מחיקת חשבון, רשימת הרישיונות, הטופס המלא של ״הטיול שלך״ (מאחורי ״עריכה״), והרשימה המלאה של המקומות ליד תחנה.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>קבוצה חדשה נפתחת על שם הקבוצה,</b> עם המקלדת. התאריכים מתמלאים מ״הטיול שלך״ כשיש, ואפשר לשנות. אורח שנוגע ב״יצירת קבוצה״ עובר קודם בכניסה, וחוזר לכאן.',
        '<b>״אני כבר בקבוצה״ הוא רשימה עם בחירה אחת.</b> בוחרים שם, ואז ״שליחת בקשה למנהל״. לשם שיש לו חשבון: ״לשם הזה יש חשבון״ וכפתור כניסה, במקום הבקשה.',
        '<b>״ממתין לאישור״ נשאר על המסך</b> גם אחרי שסוגרים ופותחים. כשהמנהל מאשר, הקבוצה נפתחת לבד. בכפר, השורה ״החבר׳ה״ אומרת ״ממתין לאישור המנהל״.',
        '<b>התפריט של חבר הוא תפריט של המערכת:</b> באייפון תפריט ליד שלוש הנקודות, באנדרואיד גיליון תחתון. הפעולה האדומה בסוף.',
        '<b>״מילוי בשבילו״</b> פותח את הטיסות שכבר בקבוצה (הכרטיסים הקטנים של לשונית הטיסות), ו״טיסה אחרת״ שפותחת את הטופס של לוח 9 בשביל החבר.',
        '<b>פעולה אדומה בשתי נגיעות:</b> השורה הופכת לאדומה מלאה עם ״לחיצה נוספת...״ לארבע שניות, ואז חוזרת. בלי חלון אישור.',
        '<b>מחיקת חשבון היא מסך משלו,</b> כי היא הדבר היחיד שאי אפשר לבטל. הכפתור האדום למטה, ו״ביטול״ מתחתיו.',
        '<b>הרישיונות:</b> הקרדיט לצליל הקריעה ראשון (חובה ברישיון שלו). נגיעה בשורה פותחת את הטקסט המלא באותו מסך.',
        '<b>הטופס המלא של הטיול:</b> שני כרטיסים, הלוך וחזור. כל פרט הוא גלולה שנוגעים בה; מה שחסר הוא גלולה מקווקוות עם ״+״. מסלול החזור הפוך להלוך, אוטומטית.',
        '<b>המקומות:</b> לפי המרחק מהתחנה, עם צבע וסמל לכל סוג, ו״עונתי״ כשזה ידוע. בלי שעות, טלפון או הבטחה: ״כדאי לבדוק במקום״.')+'</div>'
      '<div class="card" style="flex: 1"><h2>ביושר</h2>'+li(
        '<b>מה אמיתי:</b> ההר; 13 המקומות ליד התחנה התחתונה של <bdi>Goodaura</bdi>, עם המרחקים (מהמפה הפתוחה); שמות הספריות והרישיונות; הנוסחים, כמעט כולם מהמחרוזות הקיימות.',
        '<b>מה דוגמה, ומסומן:</b> האנשים, הקבוצה, התאריכים, הטיסות והשמות.',
        '<b>ברשימת הרישיונות</b> מופיע <bdi>Heebo</bdi>, הגופן של השפה החדשה. הרשימה האמיתית נבנית ממה שכל אפליקציה אורזת בפועל.',
        '<b>לא צויר כאן:</b> לוח השנה והשעון (לוח ״מתי טסים?״ ורכיבי המערכת), בחירת שדה תעופה, והטקסט המלא של רישיון.',
        '<b>תשע מחרוזות קצרות חדשות</b> (למשל ״חוזרים ל״, ״בלי שם במפה״), ברשימה במסירה. כל השאר קיים.',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי של הלוחות.')+'</div></div>')
    wide('T00-Brief.dc.html','סבב ההשלמה, חלק 5: מה שתואר רק במילים',body,1280,1400)
X=[0,470,940,1410]
def main():
    out=build();order=list(out)
    for fn,(title,body) in out.items():phone(fn,title,body)
    brief()
    H0=1400;p=os.path.join(PROJ,'canvas.json');j=json.load(open(p,encoding='utf8'))
    rows=[(H0+380,order[0:3]),(H0+380+1510,order[3:6]),(H0+380+3020,order[6:10])]
    j['boards']['T00-Brief.dc.html']=dict(x=0,y=0,w=1280,h=H0,title='חלק 5: מה שתואר רק במילים',page='gap5')
    if 'T00-Brief.dc.html' not in j['order']:j['order'].append('T00-Brief.dc.html')
    for y,fns in rows:
        for i,fn in enumerate(fns):
            j['boards'][fn]=dict(x=X[i],y=y,w=390,h=844,title=out[fn][0].split(' · ',1)[1],page='gap5')
            if fn not in j['order']:j['order'].append(fn)
    if not any(pg['id']=='gap5' for pg in j['pages']):j['pages'].insert(2,dict(id='gap5',name='השלמה ה · מה שתואר במילים'))
    T=dict(kind='title1',maxW=1600,page='gap5',x=0)
    j['notes'].update({'tT':dict(T,w=1100,y=-300,text='סבב ההשלמה · חלק 5 · מה שתואר רק במילים (23.11.2026)'),'tT1':dict(T,w=620,y=H0+80,text='א · נכנסים לקבוצה'),
                       'tT2':dict(T,w=620,y=H0+1590,text='ב · מנהלים קבוצה'),'tT3':dict(T,w=820,y=H0+3100,text='ג · חשבון, רישיונות, טיול, מקומות')})
    open(p,'w',encoding='utf8').write(json.dumps(j,ensure_ascii=False,indent=1)+'\n');print('part 5 boards written:',len(out)+1)
if __name__=='__main__':main()
