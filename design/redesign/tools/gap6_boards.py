#!/usr/bin/env python3
"""Completion round, part 6 (U00 to U13): the edges. Notifications (when we ask, the reminder as it looks,
what the app says when they are off), no reception (the mountain, the forecast, the group), when something
fails or loads, and the consent to screen recording in the games (Android pilot).
The people, the group, the meetups and the numbers are invented examples; the texts are the existing strings
wherever one exists. The lock screen is a sketch, not a system screenshot. Design only.
Usage: python3 design/redesign/tools/gap6_boards.py"""
import os,json
from peak_boards import *
import gap_boards as GB
import gap3_boards as G3
import gap4_boards as G4
import gap5_boards as G5
from peak_b4 import EG,n0,veil,fade
from gap_boards import CHEV,XI,li,STOP
from gap2_boards import topbar
from gap3_boards import ic,rl,av,sw,CHL,GRP,GM,P_BELL,P_SND,P_VIB,P_SUN,P_GLOBE,P_CHART,P_UNDO,P_PLANE,P_FLAG,P_CUP
PROJ=OUT
P_BELLX='<path d="M6 16V11a6 6 0 0 1 9.500-4.900M18 11v5l1.500 2h-13"/><path d="M10 20.500a2 2 0 0 0 4 0M4 4l16 16"/>'
P_NOSIG='<path d="M5 20v-3M10 20v-6M15 20v-9M20 20V8"/><path d="M4 4l16 16"/>'
P_WAIT='<circle cx="12" cy="12" r="8.500"/><path d="M12 7.500V12l3 2"/>'
P_WARN='<path d="M12 4 3 19.500h18z"/><path d="M12 10v4.500M12 17v.200"/>'
P_REC='<rect x="3.500" y="6" width="17" height="12" rx="3"/><circle cx="12" cy="12" r="2.600" fill="currentColor"/>'
CSS10="""
.H .ofl{display:flex;align-items:flex-start;gap:9px;border-radius:14px;background:var(--line);padding:9px 12px;font-size:13px;line-height:1.4;font-weight:500}
.H .ofl svg{flex:none;margin-top:1px}
.H .ofl.bad{background:rgba(216,54,44,.14)}
.H .ofl.bad svg{color:var(--red)}
.H .errl{display:flex;align-items:flex-start;gap:8px;font-size:13.5px;line-height:1.4;font-weight:500;color:var(--red)}
.H .errl svg{flex:none;margin-top:1px}
.H .wtag{font-size:11px;font-weight:700;color:var(--ink2);border:1px dashed var(--ink2);border-radius:9px;padding:0 7px;height:20px;display:inline-flex;align-items:center;gap:4px;white-space:nowrap}
.H .skl{display:block;height:12px;border-radius:6px;background:var(--line)}
.H .skc{width:36px;height:36px;border-radius:50%;background:var(--line);flex:none;display:inline-block}
.H .lk{position:absolute;inset:0;background:linear-gradient(180deg,#0C1730,#1B2B52 55%,#3A3F6A);color:#fff}
.H .lk .card{position:absolute;left:10px;right:10px;border-radius:24px;background:rgba(20,26,40,.78);padding:14px 16px;box-sizing:border-box;display:flex;flex-direction:column;gap:4px}
.H .rchip{height:26px;border-radius:13px;padding:0 10px;display:inline-flex;align-items:center;gap:6px;font-size:12px;font-weight:700;background:rgba(229,53,43,.14);color:var(--red);white-space:nowrap}
.H .rchip i{width:8px;height:8px;border-radius:50%;background:#E5352B;display:inline-block}
"""
MB.CSS=MB.CSS+CSS10
GT=P.lift_ends(P.flift('Goodaura'))[1]
OFFP=dict(text='אין מידע עדכני · בלי קליטה',dot='sd q',eg=False)
def groupbg(flag=True,off=False):
    o=pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))
    if flag:o+=G3.flagat(VH,'Goodaura',1,'13:00')
    return o+(G4.top(**OFFP) if off else G3.mount_top('נ'))
def nextbox(row,soon='בעוד שעה ו-12 דק׳'):
    i='<div class="box" style="border: 1px solid var(--line); padding: 10px 14px 0; display: flex; flex-direction: column; gap: 6px"><div style="display: flex; align-items: center; gap: 8px"><span class="st">המפגש הבא</span><b style="margin-inline-start: auto; color: var(--acc); font-size: 14px; white-space: nowrap">%s</b></div><div style="display: flex; align-items: flex-end; gap: 12px"><span class="num" style="font-size: 40px">13:00</span><span style="display: flex; flex-direction: column; gap: 1px; min-width: 0; padding-bottom: 2px"><b style="font-size: 17px; direction: ltr; unicode-bidi: isolate; text-align: start">Goodaura</b><span class="sub" style="font-size: 12.500px">התחנה העליונה · %s מ׳</span></span></div>'%(soon,n0(float(P.elev(*GT))))
    return i+'<div style="display: flex; align-items: center; gap: 8px; min-height: 44px; border-top: 1px solid var(--line)">%s</div></div>'%row
REM_ON='%s<span style="font-size: 14px">תזכורת רבע שעה לפני</span><span style="margin-inline-start: auto">%s</span>'%(ic(P_BELL,20),sw(True))
def seg2(on):
    return '<div class="seg">%s</div>'%''.join('<button class="%s">%s</button>'%('on' if i==on else '',t) for i,t in enumerate(('רכבלים','מזג אוויר')))
def wx(name,h,t,wind,snow,deg):
    return ('<div class="wx"><span class="t">%s</span><span class="c"><b>%s <span style="font-weight: 400; font-size: 13px; color: var(--ink2)">%s מ׳</span></b>'
            '<span style="display: inline-flex; align-items: center; gap: 5px"><svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.400" stroke-linecap="round" stroke-linejoin="round" style="transform: rotate(%ddeg); flex: none"><path d="M12 19V5M6 11l6-6 6 6"/></svg>%s</span></span>'
            '<span style="display: flex; flex-direction: column; align-items: center; flex: none; min-width: 54px"><b style="font-size: 15px; font-weight: 700; direction: rtl">%s ס״מ</b><span style="font-size: 11.500px; color: var(--ink2)">שלג חדש</span></span></div>')%(t,name,h,deg,wind,snow)
AND=lambda b:'<div class="and">'+b+'<span class="gb"></span></div>'
def build():
    out={}
    # ---- U01: before the phone asks about notifications (after the first meetup is saved)
    def b01():
        o=groupbg()+veil(0,844,'rgba(6,11,22,.38)')
        o+='<div class="sheet glass" style="gap: 10px; align-items: center; text-align: center"><div class="grab"></div><span class="ic" style="width: 52px; height: 52px; background: var(--line); margin-top: 4px">%s</span>'%ic(P_BELL,26)
        o+='<span style="font-size: 21px; font-weight: 700; line-height: 1.2">להזכיר לך רבע שעה לפני?</span><span class="sub" style="max-width: 300px">הטלפון מזכיר בעצמו, גם בלי קליטה על ההר. אפשר לכבות לכל מפגש בנפרד.</span>'
        o+='<div class="box" style="align-self: stretch; border: 1px solid var(--line); padding: 10px 14px; display: flex; align-items: center; gap: 12px; text-align: start"><span class="num" style="font-size: 30px">13:00</span><span style="display: flex; flex-direction: column; gap: 1px; min-width: 0"><b style="font-size: 15px; direction: ltr; unicode-bidi: isolate; text-align: start">Goodaura</b><span class="sub" style="font-size: 12.500px">התחנה העליונה · נשמר בקבוצה</span></span><span style="margin-inline-start: auto">%s</span></div>'%EG
        o+='<button class="pri" style="flex: none; align-self: stretch">להדליק תזכורות</button><button class="lnk" style="color: var(--ink)">לא עכשיו</button>'
        o+='<span class="sub" style="font-size: 12.500px">אחרי ״להדליק תזכורות״ הטלפון ישאל אם לאשר התראות.</span></div>'
        return o
    out['U01-NotifAsk.dc.html']=('השלמה ו · 1 · התראות: לפני שהטלפון שואל',b01())
    # ---- U02: the reminder itself, on the lock screen
    def b02():
        o='<div class="lk"><div style="position: absolute; left: 0; right: 0; top: 96px; text-align: center; font-size: 15px; font-weight: 500">יום ג׳, 12 בינואר</div><div style="position: absolute; left: 0; right: 0; top: 122px; text-align: center; font-size: 84px; font-weight: 300; line-height: 1; direction: ltr">12:45</div>'
        o+='<div class="card" style="top: 262px"><div style="display: flex; align-items: center; gap: 8px; font-size: 12.500px; opacity: .8"><span style="width: 20px; height: 20px; border-radius: 6px; background: #2F7BFF; display: inline-flex; align-items: center; justify-content: center">%s</span><span>Gudi</span><span style="margin-inline-start: auto">עכשיו</span></div>'%ic(P_FLAG,13,2.2)
        o+='<b style="font-size: 16px; margin-top: 4px">בעוד רבע שעה: <bdi>Goodaura</bdi>, התחנה העליונה</b><span style="font-size: 14.500px; opacity: .92">%s · נפגשים ב-<bdi>13:00</bdi></span></div>'%GRP
        o+='<div style="position: absolute; left: 24px; right: 24px; bottom: 44px; text-align: center; font-size: 12.500px; opacity: .72; line-height: 1.45">סכמה של מסך הנעילה, לא צילום. נגיעה בהתראה פותחת את ההר עם הדגל של המפגש.</div>'
        o+='<span style="position: absolute; top: 60px; right: 20px">%s</span></div>'%EG.replace('class="eg"','class="eg" style="color: #fff; border-color: rgba(255,255,255,.4)"')
        return o
    out['U02-Reminder.dc.html']=('השלמה ו · 2 · התזכורת: רבע שעה לפני',b02())
    # ---- U03: the notifications are off in the phone. the group says so, once, where it matters
    def b03():
        row='<span style="color: var(--ink2); display: flex">%s</span><span style="display: flex; flex-direction: column; gap: 0; min-width: 0"><span style="font-size: 14px; font-weight: 500">ההתראות כבויות בטלפון</span><span class="sub" style="font-size: 12px">התזכורת לא תגיע</span></span><button class="lnk" style="margin-inline-start: auto; min-width: 44px">להגדרות</button>'%ic(P_BELLX,20)
        i=nextbox(row)
        dim='<span style="color: var(--ink2); display: flex; opacity: .6">%s</span>'%ic(P_BELLX,20)
        i+='<div>'+rl('','<bdi>16:30</bdi> · <bdi>New Goodaura</bdi>','התחנה התחתונה · קבע/ה: נועה',dim)+rl('','מחר <bdi>09:30</bdi> · <bdi>Goodaura</bdi>','התחנה התחתונה · קבע/ה: דנה',dim)+'</div>'
        i+='<button class="pri" style="flex: none">מפגש חדש</button>'
        return groupbg()+G3.gsheet(0,i)
    out['U03-NotifOff.dc.html']=('השלמה ו · 3 · ההתראות כבויות',b03())
    # ---- U04: Android, recording while the notifications are off: there is no stop button outside the app
    s1=GB.seg_at(24);t1=s1['t0']+20000
    def b04():
        o=GB.mount(t1,sc=1.3,at=300)+GB.recp(t1)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: baseline; gap: 8px"><span class="ttl" style="font-size: 19px">עומדים</span><span class="sub">היום עד עכשיו</span></div>'
        o+=GB.daynums(t1)
        o+='<div class="ofl">%s<span>ההתראות כבויות, ולכן אין כפתור עצירה מחוץ לאפליקציה. עוצרים כאן, ב״סיום״.</span></div>'%ic(P_BELLX,18)
        o+='<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">לראות את היום</button><button class="sec" style="display: flex; align-items: center; gap: 8px">%sסיום</button></div></div>'%STOP
        return AND(o)
    out['U04-RecNoNotif.dc.html']=('השלמה ו · 4 · אנדרואיד: מקליטים בלי התראות',b04())
    # ---- U05: no reception. the mountain is whole, only the pill says so
    def b05():
        return G4.frame('hero','morning','night')+G4.top(**OFFP)+G4.ctl()+G4.dock()
    out['U05-OfflineMountain.dc.html']=('השלמה ו · 5 · בלי קליטה: ההר',b05())
    # ---- U06: the forecast is old. it says how old, and stays
    def b06():
        o=pic('hero','morning','night')+both(lambda m,l:P.runs_svg(VH,m,dim=True))+both(lambda m,l:P.lifts_svg(VH,m,l,dim=True,cabins=False))+G4.top(**OFFP)
        o+='<div class="sheet glass" style="top: 330px; gap: 8px; overflow: hidden"><div class="grab"></div><div style="display: flex; align-items: center; gap: 8px; min-height: 40px"><span class="ttl">ההר עכשיו</span><span style="margin-inline-start: auto">%s</span></div>%s'%(EG,seg2(1))
        o+='<div class="ofl">%s<span>עודכנה לפני 3 שעות, כנראה בלי קליטה</span></div>'%ic(P_WAIT,18)
        sz=P.lift_ends(P.flift('Sadzele'))[1];v0=P.lift_ends(P.flift('Goodaura'))[0]
        o+='<div>'+wx('<bdi>Sadzele</bdi>',n0(float(P.elev(*sz))),'−12°','רוח 46 קמ״ש, משבים 68','20',135)+wx('<bdi>Goodaura</bdi> העליונה',n0(float(P.elev(*GT))),'−7°','רוח 22 קמ״ש, משבים 35','14',135)+wx('הכפר',n0(float(P.elev(*v0))),'−3°','רוח 9 קמ״ש, משבים 16','8',120)+'</div>'
        o+='<div style="display: flex; flex-direction: column; gap: 6px"><span class="st">התחזית לימי הסקי שלך</span><div style="display: flex; gap: 6px">'
        o+=''.join('<div class="dy"><span>%s</span><b>%s</b><span>%s</span></div>'%d for d in(('יום ב׳ 11.1','−8°','שלג 14 ס״מ'),('יום ג׳ 12.1','−9°','שלג 20 ס״מ'),('יום ד׳ 13.1','−5°','בלי שלג'),('יום ה׳ 14.1','−4°','בלי שלג')))
        o+='</div></div><span class="sub" style="font-size: 12px">תחזית לגובה של כל נקודה · <bdi>Open-Meteo</bdi></span></div>'
        return o
    out['U06-ForecastStale.dc.html']=('השלמה ו · 6 · בלי קליטה: תחזית ישנה',b06())
    # ---- U07: the group without reception. what is saved in the phone, and a change that waits
    def b07():
        i='<div class="ofl">%s<span>בלי קליטה. מוצג מה שנשמר בטלפון ב-<bdi>10:42</bdi></span></div>'%ic(P_NOSIG,18)
        i+=nextbox(REM_ON)
        i+='<div>'+rl('','<bdi>16:30</bdi> · <bdi>New Goodaura</bdi>','התחנה התחתונה · קבע/ה: נועה','<span class="wtag">%s מחכה לקליטה</span>'%ic(P_WAIT,12,2.2))+rl('','מחר <bdi>09:30</bdi> · <bdi>Goodaura</bdi>','התחנה התחתונה · קבע/ה: דנה',ic(P_BELL,20))+'</div>'
        i+='<span class="sub" style="font-size: 12.500px">מה ששינית כאן יישלח לקבוצה כשתחזור הקליטה</span>'
        i+='<button class="pri" style="flex: none">מפגש חדש</button>'
        return groupbg(off=True)+G3.gsheet(0,i,top=262)
    out['U07-GroupOffline.dc.html']=('השלמה ו · 7 · בלי קליטה: הקבוצה',b07())
    # ---- U08: the group was never saved in this phone, and there is no reception
    def b08():
        i='<div style="display: flex; flex-direction: column; align-items: center; gap: 10px; text-align: center; padding: 26px 8px 30px"><span class="ic" style="width: 52px; height: 52px; background: var(--line)">%s</span><span style="font-size: 17px; font-weight: 700">בלי קליטה</span><span class="sub" style="max-width: 290px">הקבוצה עוד לא נשמרה בטלפון. היא תופיע כשתחזור הקליטה.</span><button class="sec" style="flex: none; height: 44px; padding: 0 22px; margin-top: 4px">לנסות שוב</button></div>'%ic(P_NOSIG,26)
        return groupbg(False,True)+G3.gsheet(0,i,top=420,inv=False)
    out['U08-GroupNeverSaved.dc.html']=('השלמה ו · 8 · בלי קליטה: קבוצה שעוד לא נשמרה',b08())
    # ---- U09: the reception is back, and the server refused a change that waited
    def b09():
        i='<div class="ofl bad">%s<span style="flex: 1">שינוי שחיכה לקליטה לא נשמר בקבוצה. זה כבר לא קיים. אולי נמחק בינתיים.</span><button class="xb" aria-label="סגירה" style="margin: -10px -10px -10px 0; flex: none">%s</button></div>'%(ic(P_WARN,18),XI)
        i+=nextbox(REM_ON)
        i+='<div>'+rl('','מחר <bdi>09:30</bdi> · <bdi>Goodaura</bdi>','התחנה התחתונה · קבע/ה: דנה',ic(P_BELL,20))+'</div>'
        i+='<button class="pri" style="flex: none">מפגש חדש</button>'
        return groupbg()+G3.gsheet(0,i,top=300)
    out['U09-ChangeRefused.dc.html']=('השלמה ו · 9 · שינוי שלא נשמר בקבוצה',b09())
    # ---- U10: an action failed. the message sits next to the button, and nothing typed is lost
    def b10():
        o=pic('vill')+base('vill')+'<div class="tag" style="left: 62px; top: 76px; height: 32px; border-radius: 16px; padding: 0 12px; direction: rtl; font-size: 13px">↑ חזרה להר</div>'
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: center"><div><p class="ttl">החבר׳ה</p><p class="sub">קבוצה אחת לכל הטיול</p></div><span style="margin-inline-start: auto">%s</span></div>'%EG
        o+='<div style="border-top: 1px solid var(--line); padding-top: 12px; display: flex; flex-direction: column; gap: 10px"><span class="st">יש לי קוד הזמנה</span>%s'%G3.codebox('SNW4K2')
        o+='<div class="errl" role="alert">%s<span>אין חיבור לשרת. בודקים קליטה ומנסים שוב.</span></div></div>'%ic(P_WARN,18)
        o+='<div style="display: flex; gap: 8px"><button class="pri">הצטרפות</button><button class="sec">יצירת קבוצה</button></div>'
        o+='<span class="sub" style="font-size: 12.500px">כל השאר עובד בלי חשבון: המפה, הטיול שלך, המשחקים והשיאים.</span></div>'
        return o
    out['U10-ActionFailed.dc.html']=('השלמה ו · 10 · פעולה שנכשלה',b10())
    # ---- U11: loading. rows in the shape of what is coming, never a spinner over the mountain
    def b11():
        sk=lambda w1,w2:'<div class="rl"><span class="skc"></span><span class="c" style="gap: 8px"><span class="skl" style="width: %d%%"></span><span class="skl" style="width: %d%%; height: 10px; opacity: .7"></span></span></div>'%(w1,w2)
        i='<div class="box" style="border: 1px solid var(--line); padding: 14px; display: flex; flex-direction: column; gap: 12px"><span class="skl" style="width: 34%"></span><span class="skl" style="width: 58%; height: 30px; border-radius: 10px"></span><span class="skl" style="width: 46%; height: 10px; opacity: .7"></span></div>'
        i+='<div>'+sk(52,34)+sk(44,40)+sk(58,28)+'</div><span class="sub" style="text-align: center" role="status">טוען…</span>'
        return pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+G3.mount_top('נ')+G3.gsheet(0,i,top=330,inv=False)
    out['U11-Loading.dc.html']=('השלמה ו · 11 · טעינה',b11())
    # ---- U12: Android, settings: the consent to screen recording in the games. off until switched on
    def b12():
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('הגדרות')
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 112px">'+rl(ic(P_CHART,20),'מדידת שימוש ודיווח שגיאות','בלי שם ובלי עוגיות. עוזר לנו לתקן תקלות.',sw(True))+rl('','מה נאסף','',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 252px; padding: 4px 16px 14px">'+rl(ic(P_REC,20),'אני מסכים להקלטת שימוש','כבוי. המסך אינו מוקלט.',sw(False))
        o+='<span class="sub" style="display: block; border-top: 1px solid var(--line); padding-top: 10px; font-size: 13px; line-height: 1.5">ההקלטה כבויה כברירת מחדל. בהסכמתך הנפרדת, דגימות של מסכי המשחקים והנגיעות נשלחות לסנטרי כדי להבין שימוש ותקלות. מופעלת הסתרה אוטומטית של רכיבי ממשק, אך גרפיקה, ניקוד וטקסט מצויר במשחק עשויים להופיע בהקלטה. המפה, המיקום, דף הבית, הטיול, החשבון והקבוצות אינם מוקלטים. אפשר לכבות בכל רגע. אין הקלטה שנשמרת לשליחה מאוחרת כשאין רשת.</span></div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 590px">'+rl(ic(P_UNDO,20),'איפוס השיאים במשחקים')+'</div>'
        return AND(o)
    out['U12-ReplayConsent.dc.html']=('השלמה ו · 12 · אנדרואיד: הסכמה להקלטת שימוש',b12())
    # ---- U13: Android, the cabin while the recording is on: it says so, and one touch leads to the switch
    def b13():
        o=pic('vill','morning','night')+base('vill')+'<div class="tag" style="left: 62px; top: 76px; height: 32px; border-radius: 16px; padding: 0 12px; direction: rtl; font-size: 13px">↑ חזרה להר</div>'
        o+='<div class="sheet glass" style="gap: 6px"><div class="grab"></div><div style="display: flex; align-items: center; gap: 10px; min-height: 48px"><div><p class="ttl">ערב בבקתה</p><p class="sub">5 משחקים · בלי קליטה</p></div><span style="margin-inline-start: auto; display: flex; align-items: center; gap: 6px"><button class="rchip" aria-label="ההקלטה פעילה. נגיעה פותחת את ההגדרות" style="min-height: 44px; background: none; padding: 0"><span class="rchip"><i></i>מוקלט</span></button>%s</span></div><div>'%EG
        for k,(key,name,tag_,g,best) in enumerate(GM):
            o+='<div class="rl" style="min-height: 64px"><span class="ic" style="background: var(--line)">%s</span><span class="c"><span class="h" style="font-weight: 700">%s</span><span class="m">%s</span></span><span class="e" style="flex-direction: column; align-items: flex-end; gap: 1px"><span style="font-size: 12.500px; font-weight: 500; color: %s">%s</span></span></div>'%(ic(g,24),name,tag_,'var(--ink2)' if key=='fresh' else 'var(--ink)',best)
        o+='</div><span class="sub" style="font-size: 12.500px; margin-top: 4px">פעיל במסכי המשחקים בלבד. תוכן המשחק עשוי להופיע בהקלטה.</span></div>'
        return AND(o)
    out['U13-ReplayOn.dc.html']=('השלמה ו · 13 · אנדרואיד: ההקלטה פעילה',b13())
    return out
def brief():
    sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -120px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 6 · 23.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">הקצוות</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">התראות, בלי קליטה, כשמשהו נכשל, והסכמה להקלטה</p></div></div>')%(sc,U('wide-dawn-cloud'))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">מה שקורה כשהדברים לא הולכים חלק: הטלפון שואל על התראות, אין קליטה על ההר, השרת לא עונה. הכלל אחד: <b>ההר תמיד שם, ושום מסך לא נחסם.</b> כל נתון אומר בעצמו בן כמה הוא, והודעה מופיעה ליד מה שנגעת בו. כמעט כל הנוסחים קיימים כבר באפליקציה.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוחות 1 עד 4</p><h2>התראות</h2><p class="p">ההסבר שלנו לפני שהטלפון שואל, התזכורת כמו שהיא נראית על מסך הנעילה, מה כתוב כשההתראות כבויות, ובאנדרואיד: הקלטה בלי כפתור עצירה מחוץ לאפליקציה.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 5 עד 8</p><h2>בלי קליטה</h2><p class="p">ההר שלם ורק הגלולה אומרת. התחזית הישנה נשארת, עם הגיל שלה. הקבוצה מוצגת ממה שנשמר בטלפון, ושינוי מחכה. וקבוצה שעוד לא נשמרה.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 9 עד 11</p><h2>נכשל, וטוען</h2><p class="p">שינוי שהשרת סירב לו, פעולה שנכשלה (ההודעה ליד הכפתור), וטעינה: שורות בצורת התוכן, בלי גלגל מסתובב מעל ההר.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 12, 13</p><h2>הסכמה להקלטה</h2><p class="p">באנדרואיד בלבד (פיילוט): המתג בהגדרות עם הנוסח שאושר, כבוי עד שמדליקים. ו<b>הצעה חדשה</b>: תג ״מוקלט״ בבקתה ובמשחקים כשהיא פעילה.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>שואלים על התראות רק ברגע שזה נותן משהו:</b> כשמפגש ראשון שעוד לפנינו נשמר בקבוצה. באנדרואיד גם בהקלטה הראשונה, מיד אחרי ״מתחילים״, בלי גיליון נוסף שלנו (הגיליון של הפעם הראשונה כבר מסביר). אף פעם לא בפתיחה הראשונה. כמו באפליקציה של היום.',
        '<b>קודם ההסבר שלנו, ורק אחריו השאלה של הטלפון.</b> ״לא עכשיו״ לא מציג את השאלה של הטלפון, ולכן אפשר להציע שוב במפגש הבא. אחרי שתי הצעות לא מציעים יותר.',
        '<b>כשההתראות כבויות, זה כתוב רק איפה שזה משנה:</b> בשורת התזכורת של המפגש (עם ״להגדרות״), ובאנדרואיד במסך ההקלטה. בלי חלון קופץ ובלי נזיפה.',
        '<b>התזכורת:</b> רבע שעה לפני, מהטלפון עצמו, גם בלי קליטה. הנוסח כמו היום. נגיעה פותחת את ההר עם הדגל.',
        '<b>בלי קליטה אין פס אזהרה, אין חלון ואין מסך שגיאה.</b> הגלולה אומרת ״אין מידע עדכני · בלי קליטה״. ההר, המסלולים, ״איפה אני״, ההקלטה, הטיול, כרטיס הסקי והמשחקים עובדים כרגיל.',
        '<b>נתון ישן נשאר, עם הגיל שלו.</b> מצב הרכבלים עד חצי שעה, התחזית עד יומיים. אחרי זה ״אין מידע״, ולא מספר ישן.',
        '<b>הקבוצה בלי קליטה:</b> מה שנשמר בטלפון, עם השעה. מה שמשנים מחכה, מסומן ״מחכה לקליטה״, ונשלח לבד. אם השרת סירב: שורה אחת בראש הקבוצה, עם הסיבה, עד שסוגרים.',
        '<b>פעולה שנכשלה:</b> ההודעה מופיעה ליד הכפתור שנלחץ, באותו גיליון. מה שהוקלד נשאר, והכפתור מוכן שוב. בלי הודעה שקופצת ונעלמת.',
        '<b>טעינה:</b> ההר לא מחכה לשרת אף פעם (התמונה ארוזה באפליקציה). גיליון שמחכה לשרת מראה שורות בצורת התוכן. אם אין תשובה: ההודעה של פעולה שנכשלה, עם ״לנסות שוב״.',
        '<b>טלפון שלא מצייר את המודל החי</b> מקבל את ההר המצויר (שלב א), בלי הודעה. המצפן לא מוצג, כי אין מה לסובב.',
        '<b>ההסכמה להקלטה נשארת מתג בהגדרות,</b> מתחת למדידה, כבויה. לא שואלים עליה בחלון, ולא בזמן משחק.')+'</div>'
      '<div class="card" style="flex: 1"><h2>ביושר</h2>'+li(
        '<b>מה קיים היום ורק צויר בשפה החדשה:</b> מתי שואלים על התראות, נוסח התזכורת, ״ההתראות כבויות... עוצרים כאן״, שורות ״בלי קליטה״ בקבוצה, ״שינוי שחיכה לקליטה לא נשמר״, הודעות השגיאה, ונוסח ההסכמה להקלטה.',
        '<b>מה חדש, ומחכה להערה שלך:</b> ההסבר לפני השאלה של הטלפון (לוח 1); ״בלי קליטה״ בגלולה (לוח 5); התג ״מחכה לקליטה״ (לוח 7); שורות הטעינה (לוח 11); והתג ״מוקלט״ (לוח 13).',
        '<b>התג ״מוקלט״ הוא הצעה שלי, לא משהו שקיים.</b> ההמלצה: כן. מי שהסכים להקלטה צריך לראות מתי היא פעילה, ולהגיע למתג בנגיעה אחת.',
        '<b>לוח 2 הוא סכמה</b> של מסך הנעילה, לא צילום. החלון של הטלפון ששואל על התראות לא מצויר: הוא של המערכת.',
        '<b>ההקלטה קיימת רק באנדרואיד.</b> באייפון ובאתר אין הקלטת מסך, ולכן לוחות 12 ו-13 באנדרואיד בלבד.',
        '<b>מה דוגמה:</b> האנשים, הקבוצה, המפגשים, השעות והמספרים.',
        '<b>12 מחרוזות קצרות חדשות,</b> ברשימה במסירה. כל השאר קיים.',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי של הלוחות, ביום ובלילה.')+'</div></div>')
    wide('U00-Brief.dc.html','סבב ההשלמה, חלק 6: הקצוות',body,1280,1480)
X=[0,470,940,1410]
def main():
    out=build();order=list(out)
    for fn,(title,body) in out.items():phone(fn,title,body,fixed='night' if fn.startswith('U02') else None)
    brief()
    H0=1480;p=os.path.join(PROJ,'canvas.json');j=json.load(open(p,encoding='utf8'))
    rows=[(H0+380,order[0:4]),(H0+380+1510,order[4:8]),(H0+380+3020,order[8:11]),(H0+380+4530,order[11:13])]
    j['boards']['U00-Brief.dc.html']=dict(x=0,y=0,w=1280,h=H0,title='חלק 6: הקצוות',page='gap6')
    if 'U00-Brief.dc.html' not in j['order']:j['order'].append('U00-Brief.dc.html')
    for y,fns in rows:
        for i,fn in enumerate(fns):
            j['boards'][fn]=dict(x=X[i],y=y,w=390,h=844,title=out[fn][0].split(' · ',1)[1],page='gap6')
            if fn not in j['order']:j['order'].append(fn)
    if not any(pg['id']=='gap6' for pg in j['pages']):j['pages'].insert(2,dict(id='gap6',name='השלמה ו · הקצוות'))
    T=dict(kind='title1',maxW=1600,page='gap6',x=0)
    j['notes'].update({'tU':dict(T,w=1100,y=-300,text='סבב ההשלמה · חלק 6 · הקצוות (23.11.2026)'),'tU1':dict(T,w=620,y=H0+80,text='א · התראות'),
                       'tU2':dict(T,w=620,y=H0+1590,text='ב · בלי קליטה'),'tU3':dict(T,w=820,y=H0+3100,text='ג · נכשל, וטוען'),'tU4':dict(T,w=1100,y=H0+4610,text='ד · הסכמה להקלטת שימוש (אנדרואיד בלבד)')})
    open(p,'w',encoding='utf8').write(json.dumps(j,ensure_ascii=False,indent=1)+'\n');print('part 6 boards written:',len(out)+1)
if __name__=='__main__':main()
