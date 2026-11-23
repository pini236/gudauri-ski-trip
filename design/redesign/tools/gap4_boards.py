#!/usr/bin/env python3
"""Completion round, part 4 (S00 to S10): on the mountain. The frame of the main screen (the village in the
dock, the map controls in one column), "where am I", the full forecast ("the mountain now"), the whole run
sheet (conditions, videos, next and previous), the compass, the first time, and the way to "my days".
The mountain, runs, lifts, heights and video titles come from site/data; the weather, the lift report and
the position are invented examples and carry the tag. Design only.
Usage: python3 design/redesign/tools/gap4_boards.py"""
import os,json
from peak_boards import *
import peak_b2 as B2
import gap3_boards as G3
from peak_b4 import EG,n0,veil,fade
from gap_boards import CHEV,XI,li
from gap2_boards import topbar,ORDER,CLOSED
from gap3_boards import ic,rl,av,CHL,P_USER,P_GEAR,P_GLOBE,P_INFO,P_SHARE
PROJ=OUT
import peak_b1 as B1m
B1peaks=B1m.peaks
from peak_b4 import you as B4you
P_LAY='<path d="M12 4 3.500 8.500 12 13l8.500-4.500z"/><path d="M3.500 13 12 17.500 20.500 13"/>'
P_LOC='<path d="M20 4 4 11l6.500 2.500L13 20z"/>'
P_HOME='<path d="M4 11.500 12 5l8 6.500"/><path d="M6.500 10v9h11v-9"/><path d="M10.500 19v-5h3v5"/>'
P_CMP='<circle cx="12" cy="12" r="8.500"/><path d="m12 6 2.300 6H9.700z" fill="currentColor"/><path d="M9.700 12 12 18l2.300-6"/>'
P_SRCH='<circle cx="10.500" cy="10.500" r="6"/><path d="m15 15 5 5"/>'
P_PLAY='<path d="M8 5.500v13l10.500-6.500z" fill="currentColor"/>'
P_DAYS='<rect x="4" y="5.500" width="16" height="14" rx="2.500"/><path d="M4 10h16M8.500 3.500v4M15.500 3.500v4"/>'
P_WIND='<path d="M3 9h10a2.500 2.500 0 1 0-2.500-2.500M3 13h15a2.500 2.500 0 1 1-2.500 2.500M3 17h7"/>'
CSS8="""
.H .dock2{position:absolute;left:10px;right:10px;bottom:12px;height:60px;border-radius:30px;display:flex;align-items:center;gap:8px;padding:0 8px;box-sizing:border-box}
.H .dock2 .vl{height:44px;border-radius:22px;border:1px solid var(--line);display:flex;align-items:center;gap:7px;padding:0 14px 0 12px;font-size:15px;font-weight:500;flex:none}
.H .dock2 .find{flex:1;min-width:0;height:44px;border-radius:22px;padding:0 14px;font-size:15px;white-space:nowrap}
.H .mc{position:absolute;left:12px;bottom:84px;display:flex;flex-direction:column;gap:8px}
.H .mc button{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;padding:0}
.H .mc button.on{background:#2F7BFF;color:#fff;border-color:transparent}
.H .ring{position:absolute;border-radius:50%;transform:translate(-50%,-50%);background:rgba(47,123,255,.14);border:1.5px solid rgba(47,123,255,.55)}
.H .hint{position:absolute;border-radius:16px;background:var(--ink);color:var(--bg);padding:10px 14px;font-size:14px;font-weight:500;line-height:1.35;box-shadow:0 8px 22px rgba(10,25,45,.3);max-width:230px}
.H .hint i{position:absolute;width:12px;height:12px;background:var(--ink);transform:rotate(45deg)}
.H .wx{display:flex;align-items:center;gap:12px;min-height:58px}
.H .wx+.wx{border-top:1px solid var(--line)}
.H .wx .t{font-size:27px;font-weight:300;direction:ltr;min-width:58px;text-align:center}
.H .wx .c{display:flex;flex-direction:column;gap:1px;min-width:0;flex:1}
.H .wx .c b{font-size:15px;font-weight:700}
.H .wx .c span{font-size:13px;color:var(--ink2)}
.H .wx .arr{width:30px;height:30px;border-radius:50%;background:var(--line);display:flex;align-items:center;justify-content:center;flex:none}
.H .dy{flex:1;border:1px solid var(--line);border-radius:14px;padding:8px 4px;display:flex;flex-direction:column;align-items:center;gap:2px;font-size:12px;color:var(--ink2);min-width:0}
.H .dy b{font-size:19px;font-weight:300;color:var(--ink);direction:ltr}
.H .rk{height:26px;border-radius:13px;padding:0 10px;font-size:12.5px;font-weight:700;display:inline-flex;align-items:center;white-space:nowrap}
.H .rk.lo{background:rgba(27,138,76,.16);color:var(--good)}.H .rk.hi{background:rgba(216,54,44,.16);color:var(--red)}.H .rk.md{background:rgba(199,119,0,.18);color:#8A5300}
.H .night .rk.md{color:#F2B95A}
.H .vd{display:flex;gap:10px;align-items:center;min-height:64px}
.H .vd+.vd{border-top:1px solid var(--line)}
.H .vd .th{width:92px;height:52px;border-radius:10px;background:linear-gradient(135deg,#2B3440,#55616F);color:#fff;display:flex;align-items:center;justify-content:center;flex:none;position:relative}
.H .vd .th small{position:absolute;bottom:3px;left:5px;font-size:10.5px;font-weight:700;direction:ltr;background:rgba(0,0,0,.55);border-radius:4px;padding:0 4px}
.H .vd .c{display:flex;flex-direction:column;gap:2px;min-width:0}
.H .vd .c b{font-size:13.5px;font-weight:500;line-height:1.3;direction:ltr;text-align:start;unicode-bidi:plaintext;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.H .vd .c span{font-size:12px;color:var(--ink2);direction:ltr;text-align:start;unicode-bidi:plaintext}
.H .pg{display:flex;gap:5px;justify-content:center}
.H .pg i{width:6px;height:6px;border-radius:50%;background:var(--line)}.H .pg i.on{background:var(--ink)}
"""
VID=[x for x in json.load(open(os.path.join(P.ROOT,'site/data/videos-seed.json'),encoding='utf8')) if x['piste']=='Tatra 2']
MB.CSS=MB.CSS+CSS8
def top(initial='נ',text='11 מתוך 14 רכבלים פתוחים · <span dir="ltr">−7°</span>',eg=True,dot='dot'):
    return G3.mount_top(initial,'<span style="white-space: nowrap">%s</span>'%text,eg).replace('class="dot"','class="%s"'%dot)
def dock(rec=True):
    return ('<div class="dock2 glass"><button class="vl">%s הכפר</button><div class="find">%s<span>לאן יורדים?</span></div>'
            '<button class="rb rec" aria-label="מקליטים"><span></span></button></div>')%(ic(P_HOME,20),ic(P_SRCH,18))
def ctl(loc=False,cmp=False):
    return ('<div class="mc">%s<button class="glass" aria-label="שכבות">%s</button><button class="glass%s" aria-label="איפה אני">%s</button></div>'
            %(('<button class="glass" aria-label="חזרה למבט הפתיחה">%s</button>'%ic(P_CMP,22,1.6)) if cmp else '',ic(P_LAY,20),' on' if loc else '',ic(P_LOC,20,1.8,'currentColor' if loc else 'none')))
def frame(view='hero',day='morning',night='night',runs='line'):
    return pic(view,day,night)+base(view,runs=runs)
def build():
    out={}
    # ---- S01: the frame of the main screen
    def b01():
        o=frame()+B1peaks()+top()+ctl()+dock()
        return o
    out['S01-Frame.dc.html']=('השלמה ד · 1 · המסך הראשי, שלם',b01())
    # ---- S02: where am I, the first time
    def b02():
        o=frame()+top()+ctl()+dock()+veil(0,844,'rgba(6,11,22,.38)')
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><p class="ttl">איפה אני על ההר</p>'
        o+='<div style="display: flex; flex-direction: column; gap: 10px">%s%s%s</div>'%tuple('<div style="display: flex; gap: 12px; align-items: flex-start"><span class="ic" style="background: var(--line); flex: none">%s</span><span style="font-size: 14.500px; line-height: 1.45; padding-top: 2px">%s</span></div>'%(ic(p),t) for p,t in(
            ('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>','<b>המיקום נשאר בטלפון.</b> הוא לא נשלח לשרת, לא נשמר ולא נמדד.'),
            (P_LOC,'פועל רק כשההר פתוח והכפתור דלוק.'),
            ('<path d="M4 18h3M9 18h3v-4H9zM14 18h3V9h-3zM19 18h1"/><path d="M4 4l16 16"/>','עובד גם בלי קליטה בהר: הטלפון מוצא את עצמו לבד.')))
        o+='<button class="pri" style="flex: none">להמשיך</button><button class="lnk">לא עכשיו</button><span class="sub" style="font-size: 12.500px; text-align: center">אחרי ״להמשיך״ הטלפון ישאל אם לאשר. אפשר לכבות בכל רגע בנגיעה בכפתור.</span></div>'
        return o
    out['S02-WhereAsk.dc.html']=('השלמה ד · 2 · איפה אני: לפני ההרשאה',b02())
    # ---- S03: where am I, on a run
    R=P.run_path(VH,'Goodaura 1');yx,yy,_,_,_,_=P.at(R,1100)
    def lbox(t,sub,act=''):
        return ('<div class="glass" style="position: absolute; left: 12px; right: 12px; top: 106px; border-radius: 18px; padding: 10px 14px; box-sizing: border-box; display: flex; align-items: center; gap: 10px; min-height: 56px">'
                '<span style="display: flex; flex-direction: column; gap: 1px; min-width: 0; flex: 1"><b style="font-size: 15px">%s</b><span class="sub" style="font-size: 13px">%s</span></span>%s</div>')%(t,sub,act)
    def b03():
        o=frame()+'<div class="ring" style="left: %.0fpx; top: %.0fpx; width: 46px; height: 46px"></div>'%(yx,yy)+B4you(yx,yy)
        o+=top()+lbox('<span style="display: inline-flex; align-items: center; gap: 7px"><i style="width: 10px; height: 10px; border-radius: 50%; background: var(--blue, #2F7BFF); display: inline-block"></i>על <bdi>Goodaura 1</bdi> · כחול</span>','דיוק של כ-8 מ׳',EG)+ctl(loc=True)+dock()
        return o
    out['S03-WhereOn.dc.html']=('השלמה ד · 3 · איפה אני: על מסלול',b03())
    # ---- S04: where am I, the honest states
    def b04():
        o=frame()+top()+ctl(loc=True)+dock()
        o+=lbox('לא בגודאורי כרגע','המיקום יופיע על המפה כשתהיו על ההר')
        st=(('המיקום לא מדויק · כ-80 מ׳','מחכים לאות טוב יותר כדי לדעת על איזה מסלול',''),('המיקום משוער · כ-2,000 מ׳','הטלפון נתן מיקום משוער, ולכן לא מצמידים למסלול','מיקום מדויק'),
            ('אין הרשאה למיקום','אפשר לאשר בהגדרות הטלפון, ואז לנסות שוב','להגדרות'),('מחפשים את המיקום','תחת שמיים פתוחים זה לוקח עד דקה',''))
        o+='<div class="sheet glass" style="gap: 0; padding-top: 14px"><span class="st" style="margin-bottom: 4px">שאר המצבים, באותה קופסה שמתחת לגלולה</span>'
        for t,s,a in st:
            o+='<div style="display: flex; align-items: center; gap: 10px; min-height: 58px; border-top: 1px solid var(--line)"><span style="display: flex; flex-direction: column; gap: 1px; min-width: 0; flex: 1"><b style="font-size: 14.500px">%s</b><span class="sub" style="font-size: 12.500px">%s</span></span>%s</div>'%(t,s,('<button class="sec" style="height: 36px; padding: 0 12px; font-size: 13px; flex: none">%s</button>'%a) if a else '')
        o+='</div>'
        return o
    out['S04-WhereStates.dc.html']=('השלמה ד · 4 · איפה אני: כשאין תשובה',b04())
    # ---- S05: the mountain now, the weather tab
    def seg2(on):
        return '<div class="seg">%s</div>'%''.join('<button class="%s">%s</button>'%('on' if i==on else '',t) for i,t in enumerate(('רכבלים','מזג אוויר')))
    def wx(name,h,t,wind,snow,deg):
        return ('<div class="wx"><span class="t">%s</span><span class="c"><b>%s <span style="font-weight: 400; font-size: 13px; color: var(--ink2)">%s מ׳</span></b>'
                '<span style="display: inline-flex; align-items: center; gap: 5px"><svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.400" stroke-linecap="round" stroke-linejoin="round" style="transform: rotate(%ddeg); flex: none"><path d="M12 19V5M6 11l6-6 6 6"/></svg>%s</span></span>'
                '<span style="display: flex; flex-direction: column; align-items: center; flex: none; min-width: 54px"><b style="font-size: 15px; font-weight: 700; direction: rtl">%s ס״מ</b><span style="font-size: 11.500px; color: var(--ink2)">שלג חדש</span></span></div>')%(t,name,h,deg,wind,snow)
    vill=P.pt(VH,*P.lift_ends(P.flift('Goodaura'))[0]);gt=P.lift_ends(P.flift('Goodaura'))[1];sz=P.lift_ends(P.flift('Sadzele'))[1]
    def b05():
        o=pic('hero','morning-wind','night')+both(lambda m,l:P.runs_svg(VH,m,dim=True))+both(lambda m,l:P.lifts_svg(VH,m,l,dim=True,cabins=False))
        for e,t in((sz,'−12°'),(gt,'−7°'),(P.lift_ends(P.flift('Goodaura'))[0],'−3°')):
            x,y,vis,_=P.pt(VH,e[0],e[1])
            if vis:o+='<div class="tag" style="left: %.0fpx; top: %.0fpx; height: 30px; border-radius: 15px; padding: 0 11px; font-size: 15px; font-weight: 700; direction: ltr">%s</div>'%(x,y-18,t)
        o+=top()
        o+='<div class="sheet glass" style="top: 296px; gap: 8px; overflow: hidden"><div class="grab"></div><div style="display: flex; align-items: center; gap: 8px; min-height: 40px"><span class="ttl">ההר עכשיו</span><span style="margin-inline-start: auto">%s</span></div>%s'%(EG,seg2(1))
        o+='<div>'+wx('<bdi>Sadzele</bdi>',n0(float(P.elev(*sz))),'−12°','רוח 46 קמ״ש, משבים 68','20',135)+wx('<bdi>Goodaura</bdi> העליונה',n0(float(P.elev(*gt))),'−7°','רוח 22 קמ״ש, משבים 35','14',135)+wx('הכפר',n0(float(P.elev(*P.lift_ends(P.flift('Goodaura'))[0]))),'−3°','רוח 9 קמ״ש, משבים 16','8',120)+'</div>'
        o+='<div style="border-top: 1px solid var(--line); padding-top: 8px; display: flex; flex-direction: column; gap: 6px"><span class="st">סגירה בגלל רוח · הערכה</span><div style="display: flex; gap: 6px; flex-wrap: wrap">'
        o+=''.join('<span class="rk %s"><bdi>%s</bdi>&nbsp;· %s</span>'%(c,n,t) for n,c,t in(('Sadzele','hi','גבוה'),('Kudebi','md','בינוני'),('Goodaura','lo','נמוך')))
        o+='</div></div><div style="display: flex; flex-direction: column; gap: 6px"><span class="st">התחזית לימי הסקי שלך</span><div style="display: flex; gap: 6px">'
        o+=''.join('<div class="dy"><span>%s</span><b>%s</b><span>%s</span></div>'%d for d in(('יום ב׳ 11.1','−8°','שלג 14 ס״מ'),('יום ג׳ 12.1','−9°','שלג 20 ס״מ'),('יום ד׳ 13.1','−5°','בלי שלג'),('יום ה׳ 14.1','−4°','בלי שלג')))
        o+='</div></div><span class="sub" style="font-size: 12px">תחזית לגובה של כל נקודה · עודכנה לפני 20 דק׳ · <bdi>Open-Meteo</bdi></span></div>'
        return o
    out['S05-MountainNow.dc.html']=('השלמה ד · 5 · ההר עכשיו: מזג אוויר',b05())
    # ---- S06: the mountain now, no forecast
    def b06():
        o=pic('hero','dawn','night')+both(lambda m,l:P.runs_svg(VH,m,dim=True))+both(lambda m,l:P.lifts_svg(VH,m,l,dim=True,cabins=False))+top(text='עוד אין מידע על הרכבלים',dot='sd q',eg=False)
        o+='<div class="sheet glass" style="gap: 8px"><div class="grab"></div><div style="display: flex; align-items: center; min-height: 40px"><span class="ttl">ההר עכשיו</span></div>%s'%seg2(1)
        o+='<div>'+''.join('<div class="wx"><span class="t" style="color: var(--ink2)">—</span><span class="c"><b>%s</b><span>אין מידע</span></span></div>'%n for n in('<bdi>Sadzele</bdi>','<bdi>Goodaura</bdi> העליונה','הכפר'))+'</div>'
        o+='<span class="sub">אין מידע. התחזית האחרונה ישנה מיומיים, או שעוד לא הגיעה. כשאין נתון לא מנחשים.</span></div>'
        return o
    out['S06-MountainNowNone.dc.html']=('השלמה ד · 6 · ההר עכשיו: אין מידע',b06())
    # ---- S07: the whole run sheet
    FH=facts(B2.SEL)
    def b07():
        def runl(mode,light):
            cz,R_=B2.chosen(VZ,mode,'s07'+mode)
            return P.details(VZ,mode,light)+P.runs_svg(VZ,mode,'ghost',skip=[B2.SEL],glow=False)+P.lifts_svg(VZ,mode,light,dim=True)+cz
        o=pic('zoom')+both(runl)+veil(0,120,'linear-gradient(180deg, var(--veil), transparent)')
        o+='<div class="glass" style="position: absolute; left: 0; right: 0; bottom: 0; top: 236px; border-radius: 28px 28px 0 0; padding: 10px 18px 18px; box-sizing: border-box; display: flex; flex-direction: column; gap: 11px; overflow: hidden"><div class="grab"></div>'
        o+='<div class="rw" style="min-height: 0">%s<div style="min-width: 0"><p class="ttl" dir="ltr" style="text-align: right">Tatra 2</p><p class="sub">כחול · קל · מראש <span dir="ltr">Goodaura</span> עד <span dir="ltr">Shino</span></p></div><button class="sec" aria-label="שיתוף" style="margin-inline-start: auto; width: 44px; height: 44px; padding: 0; flex: none">%s</button></div>'%(sign(B2.SEL,True),ic(P_SHARE,20))
        o+='<div class="nums"><div><span class="num">%s</span><span class="cap">ק״מ אורך</span></div><div><span class="num">%d</span><span class="cap">מ׳ ירידה</span></div><div><span class="num">%d°</span><span class="cap">הקטע התלול</span></div></div>'%('%.1f'%(FH['len']/1000),FH['drop'],FH['steep'])
        o+='<button class="pri" style="flex: none">לרדת בסרט</button>'
        o+='<div style="display: flex; flex-direction: column; gap: 4px"><div style="display: flex; align-items: center; gap: 8px"><span class="st">התנאים במסלול</span><span style="margin-inline-start: auto">%s</span></div>'%EG
        o+='<div style="display: flex; gap: 8px">%s%s</div></div>'%tuple('<div style="flex: 1; border: 1px solid var(--line); border-radius: 14px; padding: 8px 12px; display: flex; align-items: center; gap: 10px"><span style="font-size: 23px; font-weight: 300; direction: ltr">%s</span><span style="display: flex; flex-direction: column; gap: 0; font-size: 12.500px; color: var(--ink2)"><b style="font-size: 13px; color: var(--ink)">%s</b>%s</span></div>'%x for x in(('−7°','בראש המסלול','רוח 22 קמ״ש'),('−4°','בתחתית','רוח 11 קמ״ש')))
        o+='<div style="display: flex; flex-direction: column"><span class="st">סרטונים · %d</span>'%len(VID)
        for v in VID[:3]:
            o+='<div class="vd"><span class="th">%s<small>%s</small></span><span class="c"><b>%s</b><span>%s</span></span></div>'%(ic(P_PLAY,22),v.get('length') or '',v['title'],v.get('channel') or '')
        o+='</div><div class="pg" aria-hidden="true">%s</div><span class="sub" style="font-size: 12px; text-align: center">החלקה הצידה: המסלול הבא, בסדר של המדף</span></div>'%''.join('<i class="%s"></i>'%('on' if i==2 else '') for i in range(7))
        return o
    out['S07-RunWhole.dc.html']=('השלמה ד · 7 · מסלול נבחר: הגיליון כולו',b07())
    # ---- S08: the compass, after turning the mountain
    def b08():
        o='<img alt="" src="/_blob/3a3be622f987dccd33047e2a76037f6e" style="position: absolute; left: 0; top: 0; width: 390px; height: 844px; max-width: none">'
        o+=top()+ctl(cmp=True)+dock()
        o+='<div class="hint" style="left: 66px; bottom: 196px; max-width: 250px">המצפן מופיע רק אחרי שסובבו. נגיעה מחזירה למבט הפתיחה.<i style="left: -5px; bottom: 16px"></i></div>'
        return o
    out['S08-Compass.dc.html']=('השלמה ד · 8 · אחרי שסובבו: המצפן',b08())
    # ---- S09: the first time
    def b09():
        o=frame()+B1peaks()+top(None)+ctl()+dock()
        Rr=P.run_path(VH,'Goodaura 1');hx,hy,_,_,_,_=P.at(Rr,1100)
        o+='<div class="ring" style="left: %.0fpx; top: %.0fpx; width: 46px; height: 46px; background: rgba(255,255,255,.3); border-color: #fff"></div>'%(hx,hy)
        o+='<div class="hint" style="left: %.0fpx; top: %.0fpx">נוגעים במסלול, והוא נדלק מלמעלה עד למטה.<i style="left: 144px; top: -5px"></i></div>'%(hx-150,hy+34)
        return o
    out['S09-FirstTime.dc.html']=('השלמה ד · 9 · הפעם הראשונה: רמז אחד בכל פעם',b09())
    # ---- S10: "you", with my days first
    def b10():
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('אני',EG)
        o+='<div style="position: absolute; left: 20px; right: 20px; top: 112px; display: flex; align-items: center; gap: 14px; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)">%s<span style="display: flex; flex-direction: column; gap: 2px; min-width: 0"><span style="font-size: 27px; font-weight: 700; line-height: 1.1">נועה</span><span style="font-size: 13.500px; font-weight: 500">מחובר עם גוגל</span></span></div>'%av('נ','b')
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 196px; border: 1.500px solid var(--acc)">'+rl(ic(P_DAYS,20),'הימים שלי','3 ימי גלישה · השיאים שלך',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 270px"><span class="st" style="display: block; padding-top: 10px">הקבוצות שלי</span>'+rl(av('ג'),G3.GRP,'6 חברים · מנהל',CHL)+rl(av('מ'),'משפחה, פברואר','4 חברים',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 432px">'+rl(ic(P_GEAR,20),'הגדרות','צליל, רטט, תצוגה, מדידה',CHL)+rl(ic(P_GLOBE,20),'שפה','','עברית'+CHL)+rl(ic(P_INFO,20),'אודות','קרדיטים, פרטיות, תמיכה',CHL)+'</div>'
        o+='<div style="position: absolute; left: 20px; right: 20px; top: 624px; font-size: 13px; line-height: 1.5; text-shadow: 0 0 18px var(--halo)">״הימים שלי״ היא השורה הראשונה, לכל מי שהקליט יום אחד לפחות. בלי ימים, השורה לא מוצגת.</div>'
        return o
    out['S10-YouMyDays.dc.html']=('השלמה ד · 10 · אני: הימים שלי בראש',b10())
    return out
def brief():
    sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -150px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 4 · 23.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">על ההר</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">המסך הראשי שלם, ״איפה אני״, ״ההר עכשיו״, והגיליון המלא של מסלול</p></div></div>')%(sc,U('wide-dawn-cloud'))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">מפת הניווט (עמוד ״המפה״) מצאה שמונה חורים, כולם על המסך הראשי. כאן הם סגורים, לפי ההמלצות שבלוח 3 שם. שום מסך חדש לא נוסף: הכל כפתור, לשונית או שורה במה שכבר אישרת. <b>אושר (פיני, 23.11.2026).</b> הערות שיגיעו אחר כך ישנו את הלוח, ולא מעכבות את המימוש.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוחות 1 עד 4</p><h2>המסך הראשי, ו״איפה אני״</h2><p class="p">בסרגל התחתון: ״הכפר״, ״לאן יורדים?״ וכפתור ההקלטה. מעליו, בעמודה אחת: שכבות, ״איפה אני״, ומצפן כשצריך. ״איפה אני״ מסביר פעם אחת לפני שהטלפון שואל, ואחר כך נקודה כחולה על ההר, והמסלול שלך בקופסה מתחת לגלולה.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 5 עד 7</p><h2>״ההר עכשיו״, והמסלול כולו</h2><p class="p">הגלולה פותחת גיליון אחד עם שתי לשוניות: רכבלים (כמו שאישרת) ומזג אוויר: שלושה גבהים, סיכון לסגירה, וימי הסקי שלך. הגיליון של מסלול נמשך למעלה ומראה את התנאים, הסרטונים, ושיתוף; החלקה הצידה עוברת למסלול הבא.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 8 עד 10</p><h2>מצפן, פעם ראשונה, הימים שלי</h2><p class="p">מצפן קטן שמופיע רק אחרי שסובבו את ההר. בפעם הראשונה אין מסכי הדרכה: רמז אחד על ההר, שנעלם אחרי שעשו אותו. ״הימים שלי״ היא השורה הראשונה ב״אני״.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>הסרגל התחתון למקומות ולפעולות, העמודה לשליטה במפה.</b> כפתור השכבות עבר מהסרגל לעמודה, ו״הכפר״ נכנס במקומו. נגיעה בכפר שעל ההר ממשיכה לעבוד.',
        '<b>הגלולה ליד ״אני״ קצרה יותר,</b> ולכן בשורה אחת: ״11 מתוך 14 רכבלים פתוחים · <span dir="ltr">−7°</span>״, בלי ״בפסגה״. הטמפרטורה היא של התחנה העליונה של <bdi>Goodaura</bdi>.',
        '<b>״איפה אני״ כבוי בכל פתיחה,</b> כמו היום. דלוק: הכפתור כחול, נקודה ועיגול דיוק על ההר, ומתחת לגלולה קופסה אחת: ״על <bdi>Goodaura 1</bdi> · כחול״ והדיוק. הגלולה נשארת על מצב הרכבלים. בזמן הקלטה הוא דלוק ממילא.',
        '<b>כשאין תשובה, אומרים:</b> ״לא בגודאורי כרגע״, ״המיקום לא מדויק״, ״המיקום משוער״, ״אין הרשאה״, ״מחפשים״. כולם מהמחרוזות הקיימות, בקופסה אחת מתחת לגלולה.',
        '<b>״ההר עכשיו״:</b> נגיעה בגלולה פותחת את הלשונית ״רכבלים״; נגיעה בטמפרטורה שבגלולה פותחת ישר את ״מזג אוויר״. הטמפרטורות נכתבות גם על ההר, בגובה שלהן.',
        '<b>הסיכון לסגירה הוא הערכה,</b> וכך כתוב, ורק לרכבלים שהשרת נותן להם סיכון. דיווח של <bdi>MTA</bdi> על רכבל גובר עליה.',
        '<b>ימי הסקי שלך</b> מופיעים רק כשיש טיול והיום הראשון שלו נכנס לתחזית. יום רחוק מדי: ״התחזית ליום הזה תיפתח ב-...״.',
        '<b>הגיליון של מסלול בשני גבהים:</b> נמוך (מה שאישרת בלוח K10), ומלא, במשיכה למעלה. ״סרטונים״ בגיליון הנמוך גולל אל הרשימה.',
        '<b>סרטון נפתח ביוטיוב</b> (באייפון בנגן בתוך הגיליון, כמו היום). בלי תמונה ממוזערת שממציאים: מסגרת כהה עם משך הסרטון, עד שהתמונה נטענת.',
        '<b>הקודם והבא</b> בהחלקה הצידה על הגיליון, בסדר של המדף. הנקודות מראות איפה אתה ברשימה.',
        '<b>הרמזים של הפעם הראשונה:</b> שלושה, אחד בכל פעם: ״נוגעים במסלול״, ״לאן יורדים?״, ״הכפר״. כל רמז נעלם כשעושים אותו, או בנגיעה בו, ולא חוזר.')+'</div>'
      '<div class="card" style="flex: 1"><h2>ביושר</h2>'+li(
        '<b>מה אמיתי:</b> ההר, המסלולים, הרכבלים, הגבהים של שלוש הנקודות, הנתונים של <bdi>Tatra 2</bdi>, ושמות הסרטונים, הערוצים והמשכים (מקובץ הסרטונים).',
        '<b>מה דוגמה, ומסומן:</b> מזג האוויר, הסיכון, מצב הרכבלים, ימי הסקי, המיקום והדיוק, השם והקבוצות.',
        '<b>לוח 8</b> הוא פריים של המודל החי ממזרח, בלי המסלולים והשמות שמעליו. הוא מראה רק איפה המצפן יושב.',
        '<b>ים העננים</b> עדיין לא בתחזית, ולכן לא מופיע בלשונית מזג האוויר.',
        '<b>הטקסטים</b> של ״איפה אני״, מזג האוויר והתנאים במסלול הם המחרוזות הקיימות. חדשות: ״ההר עכשיו״, ״הכפר״ בסרגל, שלושת הרמזים, השורה ״הימים שלי״ ב״אני״, ו״פועל רק כשההר פתוח והכפתור דלוק״ (במקום המשפט הישן, שהזכיר מפה וסוללה).',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי של הלוחות.')+'</div></div>')
    wide('S00-Brief.dc.html','סבב ההשלמה, חלק 4: על ההר',body,1280,1430)
X=[0,470,940,1410]
def main():
    out=build();order=list(out)
    for fn,(title,body) in out.items():phone(fn,title,body,fixed='day' if fn.startswith('S08') else None)
    brief()
    H0=1430;p=os.path.join(PROJ,'canvas.json');j=json.load(open(p,encoding='utf8'))
    rows=[(H0+380,order[0:4]),(H0+380+1510,order[4:7]),(H0+380+3020,order[7:10])]
    j['boards']['S00-Brief.dc.html']=dict(x=0,y=0,w=1280,h=H0,title='חלק 4: על ההר, ומה הוחלט',page='gap4')
    if 'S00-Brief.dc.html' not in j['order']:j['order'].append('S00-Brief.dc.html')
    for y,fns in rows:
        for i,fn in enumerate(fns):
            j['boards'][fn]=dict(x=X[i],y=y,w=390,h=844,title=out[fn][0].split(' · ',1)[1],page='gap4')
            if fn not in j['order']:j['order'].append(fn)
    if not any(pg['id']=='gap4' for pg in j['pages']):j['pages'].insert(2,dict(id='gap4',name='השלמה ד · על ההר'))
    T=dict(kind='title1',maxW=1600,page='gap4',x=0)
    j['notes'].update({'tS':dict(T,w=1000,y=-300,text='סבב ההשלמה · חלק 4 · על ההר (23.11.2026)'),'tS1':dict(T,w=620,y=H0+80,text='א · המסך הראשי, ו״איפה אני״'),
                       'tS2':dict(T,w=620,y=H0+1590,text='ב · ״ההר עכשיו״, והמסלול כולו'),'tS3':dict(T,w=760,y=H0+3100,text='ג · מצפן, פעם ראשונה, הימים שלי')})
    open(p,'w',encoding='utf8').write(json.dumps(j,ensure_ascii=False,indent=1)+'\n');print('part 4 boards written:',len(out)+1)
if __name__=='__main__':main()
