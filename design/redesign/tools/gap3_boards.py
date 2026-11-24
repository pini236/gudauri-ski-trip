#!/usr/bin/env python3
"""Completion round, part 3 (R00 to R15): the people and the evening. The group (gate, invite link,
meetups, flights, scores, members, inviting), signing in and "you" (account, guest), the games (the cabin
evening and the shell around a game), settings, language and about. Lifts, stations and the village come
from site/data; people, the group, flights, meetups and scores are invented examples and carry the tag.
Design only. Usage: python3 design/redesign/tools/gap3_boards.py"""
from peak_boards import *
import peak_b4 as B4
import gap2_boards as G2
from peak_b4 import EG,n0,veil,fade
from gap_boards import CHEV,XI,CHK,li
from gap2_boards import IC_FLAG,topbar
CHL=CHEV.replace('m9 6 6 6-6 6','m15 6-6 6 6 6')
def ic(d,s=22,w=1.8,fill='none'):
    return '<svg width="%d" height="%d" viewBox="0 0 24 24" fill="%s" stroke="currentColor" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">%s</svg>'%(s,s,fill,w,d)
P_PLANE='<path d="M21 4 3 11l6 2.500L11.500 20l3-5.500z"/><path d="m9 13.500 5-4"/>'
P_FLAG='<path d="M6 21V4"/><path d="M6 4.500h11l-2.500 3.700 2.500 3.800H6"/>'
P_CUP='<path d="M7 4h10v5a5 5 0 0 1-10 0z"/><path d="M7 6H4v1a3 3 0 0 0 3 3M17 6h3v1a3 3 0 0 1-3 3M12 14v4M8.500 20h7"/>'
P_BELL='<path d="M6 16V11a6 6 0 0 1 12 0v5l1.500 2h-15z"/><path d="M10 20.500a2 2 0 0 0 4 0"/>'
P_USER='<circle cx="12" cy="8.500" r="3.700"/><path d="M4.500 20a7.500 7.500 0 0 1 15 0"/>'
P_GEAR='<circle cx="12" cy="12" r="3"/><path d="M12 3v2.500M12 18.500V21M3 12h2.500M18.500 12H21M5.600 5.600l1.800 1.800M16.600 16.600l1.800 1.800M5.600 18.400l1.800-1.800M16.600 7.400l1.800-1.800"/>'
P_GLOBE='<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.800 3 2.800 15 0 18M12 3c-2.800 3-2.800 15 0 18"/>'
P_INFO='<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 7.600v.200"/>'
P_SHLD='<path d="M12 3 5 6v5.500c0 4.300 2.900 7.700 7 9.500 4.100-1.800 7-5.200 7-9.500V6z"/>'
P_HELP='<circle cx="12" cy="12" r="9"/><path d="M9.500 9.500a2.500 2.500 0 1 1 3.600 2.200c-.800.500-1.100 1-1.100 1.800M12 16.800v.200"/>'
P_SND='<path d="M4 10v4h3l4 3.500v-11L7 10z"/><path d="M15 9.500a3.500 3.500 0 0 1 0 5M17.500 7a7 7 0 0 1 0 10"/>'
P_VIB='<rect x="8" y="4" width="8" height="16" rx="2"/><path d="M4.500 9v6M19.500 9v6"/>'
P_SUN='<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.600 5.600 7 7M17 17l1.400 1.400M5.600 18.400 7 17M17 7l1.400-1.400"/>'
P_CHART='<path d="M4 20V10M10 20V4M16 20v-7M21 20H3"/>'
P_UNDO='<path d="M4 8h10a5 5 0 0 1 0 10H8"/><path d="M7.500 4.500 4 8l3.500 3.500"/>'
P_MORE='<circle cx="5" cy="12" r="1.300" fill="currentColor"/><circle cx="12" cy="12" r="1.300" fill="currentColor"/><circle cx="19" cy="12" r="1.300" fill="currentColor"/>'
P_LINK='<path d="M10 14a4 4 0 0 0 5.700 0l3-3a4 4 0 0 0-5.700-5.700l-1 1"/><path d="M14 10a4 4 0 0 0-5.700 0l-3 3a4 4 0 0 0 5.700 5.700l1-1"/>'
P_SHARE='<path d="M12 15V4M8 7.500 12 4l4 3.500M5 12v7h14v-7"/>'
P_DOC='<path d="M7 3h7l4 4v14H7z"/><path d="M14 3v4h4M10 12h5M10 16h5"/>'
P_STAR='<path d="m12 3.500 2.600 5.400 5.900.800-4.300 4.100 1.100 5.800L12 16.800l-5.300 2.800 1.100-5.800-4.300-4.100 5.900-.800z"/>'
P_PLUS='<path d="M12 5v14M5 12h14"/>'
G_GOOGLE='<svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true"><path fill="#EA4335" d="M24 9.500c3.540 0 6.710 1.220 9.210 3.600l6.850-6.850C35.900 2.380 30.470 0 24 0 14.620 0 6.510 5.380 2.560 13.220l7.980 6.190C12.430 13.720 17.740 9.500 24 9.500z"/><path fill="#4285F4" d="M46.980 24.550c0-1.570-.150-3.090-.380-4.550H24v9.020h12.940c-.580 2.960-2.260 5.480-4.780 7.180l7.730 6c4.510-4.180 7.090-10.360 7.090-17.650z"/><path fill="#FBBC05" d="M10.530 28.590c-.480-1.450-.760-2.990-.760-4.590s.270-3.140.760-4.590l-7.980-6.190C.920 16.460 0 20.120 0 24c0 3.880.920 7.540 2.560 10.780l7.970-6.190z"/><path fill="#34A853" d="M24 48c6.480 0 11.930-2.130 15.890-5.810l-7.730-6c-2.150 1.450-4.920 2.300-8.160 2.300-6.260 0-11.570-4.220-13.470-9.910l-7.980 6.190C6.510 42.620 14.620 48 24 48z"/></svg>'
G_APPLE='<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M17.050 20.280c-.980.950-2.050.800-3.080.350-1.090-.460-2.090-.480-3.240 0-1.440.620-2.200.440-3.060-.350C2.790 15.250 3.510 7.590 9.050 7.310c1.350.070 2.290.740 3.080.800 1.180-.240 2.310-.930 3.570-.840 1.510.120 2.650.720 3.400 1.800-3.120 1.870-2.380 5.980.480 7.130-.570 1.500-1.310 2.990-2.540 4.090zM12.030 7.250c-.150-2.230 1.660-4.070 3.740-4.250.290 2.580-2.340 4.500-3.740 4.250z"/></svg>'
# the five games: a mark for each (not the games' own art)
GM=[('merge','איחוד כדורי שלג','מפתית ועד מלך קזבק','<circle cx="8.500" cy="13" r="4.500"/><circle cx="16.500" cy="10" r="3"/>','שיא 2,048'),
    ('descent','הירידה של החבר׳ה','הירידה על המסלולים האמיתיים','<path d="M3 7c5 1 6 5 9 7s6 2 9 4"/><path d="m8 5 2.500 5"/>','3 כוכבים ב-<bdi>Tatra 2</bdi>'),
    ('school','בית הספר לסקי','מעצירה בפיצה ועד קרווינג','<path d="M6 21V5M18 21V9"/><path d="M6 5.500h7l-1.600 2.300L13 10H6M18 9.500h-5"/>','שיעור 4 מתוך 7'),
    ('snowball','קרב כדורי שלג','מאחורי החומה, מול החבר׳ה','<path d="M3 18h18M5 18v-4h14v4"/><circle cx="15.500" cy="7" r="2.800"/><path d="M7 9.500 11 8"/>','שלב 3 מתוך 5'),
    ('fresh','שלג טרי','לגעת בשלג ולרסק אגם קפוא','<path d="M12 3v18M4.200 7.500l15.600 9M19.800 7.500l-15.600 9"/><path d="m9.500 4.500 2.500 2 2.500-2M9.500 19.500l2.500-2 2.500 2"/>','בלי ניקוד')]
CSS7="""
.H .av{width:36px;height:36px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;flex:none;color:#fff;font-size:15px;font-weight:700;box-sizing:border-box}
.H .av.s{width:28px;height:28px;font-size:12.5px;border:2px solid var(--paper);margin-inline-start:-8px}
.H .av.s:first-child{margin-inline-start:0}
.H .av.b{width:64px;height:64px;font-size:27px}
.H .av.g{background:var(--line);color:var(--ink)}
.H .sw{width:52px;height:32px;border-radius:16px;background:var(--line);position:relative;flex:none;display:inline-block}
.H .sw::after{content:'';position:absolute;top:3px;inset-inline-start:3px;width:26px;height:26px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.3)}
.H .sw.on{background:var(--acc)}.H .sw.on::after{inset-inline-start:23px}
.H .cbx{display:flex;gap:6px;direction:ltr;justify-content:center}
.H .cbx b{width:46px;height:56px;border-radius:14px;border:1px solid var(--line);display:flex;align-items:center;justify-content:center;font-size:27px;font-weight:500;box-sizing:border-box;background:var(--glass)}
.H .cbx b.cur{border:2px solid var(--acc)}
.H .cbx.big b{width:50px;height:64px;font-size:33px;font-weight:300}
.H .mp{border-radius:18px;background:var(--paper);color:var(--ink);padding:12px 14px 0;box-sizing:border-box;display:flex;flex-direction:column;gap:8px;box-shadow:0 6px 16px rgba(10,25,45,.18);border:1px solid var(--line)}
.H .mp .cd2{font-size:30px;font-weight:300;line-height:1;direction:ltr}
.H .mp .stb{min-height:48px;display:flex;align-items:center;gap:8px;border-top:2px dashed var(--line);margin:0 -14px;padding:0 14px}
.H .inp{height:52px;border-radius:16px;border:1px solid var(--line);background:var(--glass);display:flex;align-items:center;padding:0 16px;font-size:16px;color:var(--ink2);box-sizing:border-box}
.H .lnk{min-height:44px;display:inline-flex;align-items:center;justify-content:center;font-size:14.5px;font-weight:700;color:var(--acc)}
.H .rl{display:flex;align-items:center;gap:12px;min-height:54px}
.H .rl+.rl{border-top:1px solid var(--line)}
.H .rl .c{display:flex;flex-direction:column;gap:1px;min-width:0;flex:1}
.H .rl .h{font-size:15.5px;font-weight:500;line-height:1.25}
.H .rl .m{font-size:12.5px;color:var(--ink2);line-height:1.3}
.H .rl .e{margin-inline-start:auto;display:flex;align-items:center;gap:6px;font-size:13.5px;color:var(--ink2);white-space:nowrap}
.H .role{font-size:11px;font-weight:700;border:1px solid var(--line);border-radius:9px;height:20px;padding:0 7px;display:inline-flex;align-items:center;color:var(--ink2)}
.H .gbtn{height:52px;border-radius:26px;display:flex;align-items:center;justify-content:center;gap:10px;font-size:16px;font-weight:500;box-sizing:border-box}
.H .gbtn.gg{background:#fff;color:#1F1F1F;border:1px solid #747775}
.H .gbtn.ap{background:#000;color:#fff}.H .night .gbtn.ap{background:#fff;color:#000}
.H .box{border-radius:22px;padding:4px 16px;box-sizing:border-box}
.H .me{background:rgba(107,63,212,.10);border-radius:14px;margin:0 -8px;padding:0 8px}.H .night .me{background:rgba(183,156,255,.16)}
.H .danger{color:var(--red);font-weight:700}
.H .stars{display:flex;gap:6px;direction:ltr;color:var(--acc)}
.H .lg b{font-size:19px;font-weight:500}
.H .sheet>*{flex-shrink:0}
"""
MB.CSS=MB.CSS+CSS7
AVC={'נ':'#7A5AE0','א':'#1F7A7A','ד':'#B34A33','י':'#3B6FB8','מ':'#8A6420','ר':'#5C7A3A','ע':'#6A5A8C'}
def av(name,cls=''):
    return '<span class="av %s" style="background: %s" aria-hidden="true">%s</span>'%(cls,AVC.get(name[0],'#5A6A7A'),name[0])
GRP='גודאורי 2027';ME='נועה';MEM=['נועה','איתי','דנה','יואב','מאיה','רון']
sw=lambda on:'<span class="sw%s" role="switch"></span>'%(' on' if on else '')
def rl(icon,h,m='',e='',cls=''):
    return '<div class="rl %s">%s<span class="c"><span class="h">%s</span>%s</span>%s</div>'%(cls,('<span class="ic" style="width: 36px; height: 36px; background: var(--line)">%s</span>'%icon) if icon else '',h,('<span class="m">%s</span>'%m) if m else '',('<span class="e">%s</span>'%e) if e else '')
def mount_top(initial=None,text='11 מתוך 14 רכבלים פתוחים',eg=True):
    """the top of the mountain: the pill, and the new "you" button at its end"""
    a=av(initial) if initial else '<span class="av g">%s</span>'%ic(P_USER,20)
    return ('<div class="now glass" style="left: 64px"><span class="dot"></span><span>%s</span><span style="margin-inline-start: auto">%s</span></div>'%(text,EG if eg else '')+
            '<button class="xb glass" aria-label="אני: חשבון והגדרות" style="position: absolute; top: 52px; left: 12px; padding: 0">%s</button>'%a)
def flagat(V,lift,end,t):
    e=P.lift_ends(P.flift(lift))[end];x,y,_,_=P.pt(V,e[0],e[1])
    return '<span class="stn" style="left: %.0fpx; top: %.0fpx; width: 16px; height: 16px; border-color: #0E2233"></span><div class="flag" style="left: %.0fpx; top: %.0fpx"><i></i><b>%s</b></div>'%(x,y,x,y,t)
def ghead(extra=''):
    return '<div style="display: flex; align-items: center; gap: 10px; min-height: 48px"><div style="display: flex; flex-direction: column; gap: 1px; min-width: 0"><span class="ttl">%s</span><span class="sub">6 חברים · 10.1 עד 15.1</span></div><span style="margin-inline-start: auto; display: flex; align-items: center; gap: 6px">%s%s</span></div>'%(GRP,EG,extra)
def tabs(on):
    return '<div class="seg">%s</div>'%''.join('<button class="%s">%s</button>'%('on' if i==on else '',t) for i,t in enumerate(('מפגשים','טיסות','שיאים','חברים')))
INV='<button class="sec" style="height: 44px; min-width: 44px; padding: 0 14px; display: flex; align-items: center; gap: 6px; font-size: 14px">%s הזמנה</button>'%ic(P_PLUS,18)
def gsheet(tab,inner,top=292,inv=True):
    return '<div class="sheet glass" style="top: %dpx; gap: 10px; overflow: hidden"><div class="grab"></div>%s%s%s</div>'%(top,ghead(INV if inv else ''),tabs(tab),inner)+fade(790,54)
def codebox(code,big=False,cur=None):
    return '<div class="cbx%s">%s</div>'%(' big' if big else '',''.join('<b class="%s">%s</b>'%('cur' if i==cur else '',c if c!=' ' else '') for i,c in enumerate(code)))
def build():
    out={}
    # ---- R01: the gate. not in a group yet
    def b01():
        o=pic('vill')+base('vill')+'<div class="tag" style="left: 62px; top: 76px; height: 32px; border-radius: 16px; padding: 0 12px; direction: rtl; font-size: 13px">↑ חזרה להר</div>'
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div><p class="ttl">החבר׳ה</p><p class="sub">קבוצה אחת לכל הטיול</p></div>'
        o+='<div style="display: flex; gap: 8px">%s</div>'%''.join('<div style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; font-size: 13px; font-weight: 500; line-height: 1.3"><span class="ic" style="background: var(--line)">%s</span>%s</div>'%(ic(p),t) for p,t in((P_PLANE,'הטיסות של כולם'),(P_FLAG,'נפגשים על ההר'),(P_CUP,'שיאים במשחקים')))
        o+='<div style="border-top: 1px solid var(--line); padding-top: 12px; display: flex; flex-direction: column; gap: 10px"><span class="st">יש לי קוד הזמנה</span>%s</div>'%codebox('SNW   ',cur=3)
        o+='<div style="display: flex; gap: 8px"><button class="pri" style="opacity: .45">הצטרפות</button><button class="sec">יצירת קבוצה</button></div>'
        o+='<span class="sub" style="font-size: 12.500px">כל השאר עובד בלי חשבון: המפה, הטיול שלך, המשחקים והשיאים.</span></div>'
        return o
    out['R01-GroupGate.dc.html']=('השלמה ג · 1 · החבר׳ה: עוד בלי קבוצה',b01())
    # ---- R02: an invite link was opened
    def b02():
        o=pic('hero','sunset','night')+veil(0,844,'var(--veil)')+topbar('',EG)
        o+='<div style="position: absolute; left: 20px; right: 20px; top: 128px; display: flex; flex-direction: column; align-items: center; gap: 8px; text-align: center; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span style="font-size: 15px; font-weight: 500">מצטרפים ל</span><span style="font-size: 44px; font-weight: 700; line-height: 1.05">%s</span><span style="font-size: 15px; font-weight: 500">10.1 עד 15.1.2027</span></div>'%GRP
        o+='<div style="position: absolute; left: 0; right: 0; top: 290px; display: flex; flex-direction: column; align-items: center; gap: 8px"><span style="display: flex; --paper: #fff">%s</span><span class="glass" style="height: 28px; border-radius: 14px; padding: 0 12px; display: inline-flex; align-items: center; font-size: 13px; font-weight: 500">6 חברים</span></div>'%''.join(av(n,'s') for n in MEM)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><span style="font-size: 17px; font-weight: 700">איך קוראים לך בקבוצה?</span><div class="inp">השם שהחבר׳ה מכירים</div>'
        o+='<button class="pri" style="flex: none; opacity: .45">הצטרפות</button><button class="lnk">אני כבר בקבוצה</button>'
        o+='<span class="sub" style="font-size: 12.500px; text-align: center">בלי הרשמה. את המקום אפשר לשמור אחר כך, בנגיעה אחת.</span></div>'
        return o
    out['R02-JoinLink.dc.html']=('השלמה ג · 2 · הזמנה נפתחה',b02())
    # ---- R03: the group, meetups (during the trip)
    def b03():
        gt=P.lift_ends(P.flift('Goodaura'))[1];xt,yt,_,_=P.pt(VH,gt[0],gt[1]);w,h,sc=390,844,1.45
        cx=min(max(xt,w/2/sc),VH['W']-w/2/sc);cy=min(max(yt+(h/2-215)/sc,h/2/sc),VH['H']-h/2/sc);px,py=w/2+(xt-cx)*sc,h/2+(yt-cy)*sc
        L=lambda V,mode:'<g opacity=".4">'+P.runs_svg(V,mode,dim=True)+'</g>'+P.lifts_svg(V,mode,'morning' if mode=='day' else 'night',cabins=False,dim=True)
        o=B4.win('hero',w,h,cx,cy,sc,L,r=0,style=' position: absolute; left: 0; top: 0')
        o+='<span class="stn" style="left: %.0fpx; top: %.0fpx; width: 18px; height: 18px; border-color: #0E2233"></span><div class="flag" style="left: %.0fpx; top: %.0fpx"><i></i><b>13:00</b></div>'%(px,py,px,py)+mount_top('נ')
        i='<div class="box" style="border: 1px solid var(--line); padding: 10px 14px 0; display: flex; flex-direction: column; gap: 6px"><div style="display: flex; align-items: center; gap: 8px"><span class="st">המפגש הבא</span><b style="margin-inline-start: auto; color: var(--acc); font-size: 14px; white-space: nowrap">בעוד שעה ו-12 דק׳</b></div><div style="display: flex; align-items: flex-end; gap: 12px"><span class="num" style="font-size: 40px">13:00</span><span style="display: flex; flex-direction: column; gap: 1px; min-width: 0; padding-bottom: 2px"><b style="font-size: 17px; direction: ltr; unicode-bidi: isolate; text-align: start">Goodaura</b><span class="sub" style="font-size: 12.500px">התחנה העליונה · %s מ׳</span></span></div>'%n0(float(P.elev(*gt)))
        i+='<div style="display: flex; align-items: center; gap: 8px; min-height: 44px; border-top: 1px solid var(--line)">%s<span style="font-size: 14px">תזכורת רבע שעה לפני</span><span style="margin-inline-start: auto">%s</span></div></div>'%(ic(P_BELL,20),sw(True))
        i+='<div>'+rl('','<bdi>16:30</bdi> · <bdi>New Goodaura</bdi>','התחנה התחתונה · קבע/ה: נועה',ic(P_BELL,20))+rl('','מחר <bdi>09:30</bdi> · <bdi>Goodaura</bdi>','התחנה התחתונה · קבע/ה: דנה',ic(P_BELL,20))+'</div>'
        i+='<button class="pri" style="flex: none">מפגש חדש</button>'
        return o+gsheet(0,i)
    out['R03-GroupMeetups.dc.html']=('השלמה ג · 3 · הקבוצה: מפגשים',b03())
    # ---- R04: the group, flights
    PL2='<span style="color: var(--acc); display: flex; transform: scaleX(-1)">%s</span>'%ic(P_PLANE,20)
    def mpass(a,b,ta,tb,no,who,foot):
        return ('<div class="mp"><div style="display: flex; align-items: center; justify-content: space-between; gap: 10px"><span style="display: flex; flex-direction: column"><span class="cd2">%s</span><span class="sub" style="font-size: 12px"><bdi>%s</bdi></span></span>%s<span style="display: flex; flex-direction: column; align-items: flex-end"><span class="cd2">%s</span><span class="sub" style="font-size: 12px"><bdi>%s</bdi></span></span><span style="display: flex; flex-direction: column; align-items: flex-end; gap: 2px; margin-inline-start: 6px"><span class="sub" style="font-size: 11.500px">טיסה</span><b style="font-size: 14px"><bdi>%s</bdi></b></span></div>'
                '<div class="stb"><span style="display: flex">%s</span>%s</div></div>')%(a,ta,PL2,b,tb,no,''.join(av(n,'s') for n in who),foot)
    def b04():
        o=pic('hero','sunset','night')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+mount_top('נ')
        i='<div style="display: flex; align-items: center; gap: 6px"><span class="ch on">הלוך</span><span class="ch">חזור</span><span class="sub" style="margin-inline-start: auto; font-size: 12.500px">יום א׳ · 10.1</span></div>'
        i+=mpass('TLV','TBS','15:30','20:05','GD 201',['נועה','איתי','דנה','יואב'],'<span style="font-size: 13.500px; font-weight: 500">4 על הטיסה</span><span style="margin-inline-start: auto; display: flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 700; color: var(--good)">%s את/ה</span>'%CHK)
        i+=mpass('TLV','KUT','06:10','10:25','GD 415',['מאיה'],'<span style="font-size: 13.500px; font-weight: 500">מאיה</span><button class="lnk" style="margin-inline-start: auto; font-size: 13.500px">אני על אותה טיסה</button>')
        i+=rl(av('רון'),'רון','עוד בלי טיסה','<button class="lnk" style="font-size: 13.500px">מילוי בשבילו</button>')
        return o+gsheet(1,i,top=254)
    out['R04-GroupFlights.dc.html']=('השלמה ג · 4 · הקבוצה: טיסות',b04())
    # ---- R05: the group, scores
    def b05():
        o=pic('hero','blue','night')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+mount_top('נ')
        i='<div class="chipr">%s</div>'%''.join('<span class="ch%s">%s</span>'%(' on' if k==0 else '',t) for k,t in enumerate(('איחוד כדורי שלג','הירידה של החבר׳ה','בית הספר לסקי','קרב כדורי שלג')))
        rows=[('דנה','4,096'),('נועה','2,048'),('איתי','1,024'),('יואב','512')]
        i+='<div>%s</div>'%''.join('<div class="rl%s"><span class="num" style="font-size: 23px; width: 22px; text-align: center">%d</span>%s<span class="c"><span class="h">%s%s</span></span><span class="e"><span class="num" style="font-size: 25px; color: var(--ink)">%s</span></span></div>'%(' me' if n==ME else '',k+1,av(n),n,' <span class="m" style="display: inline">(את/ה)</span>' if n==ME else '',s) for k,(n,s) in enumerate(rows))
        i+='<span class="sub" style="font-size: 12.500px">השיא הכי טוב של כל אחד, מכל משחק. נשלח לקבוצה כשיש קליטה.</span><button class="sec" style="flex: none">לשחק עכשיו</button>'
        return o+gsheet(2,i)
    out['R05-GroupScores.dc.html']=('השלמה ג · 5 · הקבוצה: שיאים',b05())
    # ---- R06: the group, members (what an admin sees)
    def b06():
        o=pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+mount_top('נ')
        i='<div class="box" style="border: 1px solid var(--line); padding: 10px 14px 12px; display: flex; flex-direction: column; gap: 8px"><span class="st">בקשות · רק מנהלים רואים</span><div style="display: flex; align-items: center; gap: 10px">%s<span style="display: flex; flex-direction: column; gap: 1px"><span style="font-size: 15px; font-weight: 500">עומר מבקש/ת להצטרף</span><span class="sub" style="font-size: 12.500px">אחרי אישור נכנסים לקבוצה.</span></span></div><div style="display: flex; gap: 8px"><button class="pri" style="height: 44px; font-size: 15px">אישור</button><button class="sec" style="height: 44px; flex: 1">דחייה</button></div></div>'%av('עומר')
        more='<button class="xb" aria-label="פעולות" style="margin-inline-end: -10px">%s</button>'%ic(P_MORE,22)
        i+='<div>'+rl(av('נועה'),'נועה <span class="m" style="display: inline">(את/ה)</span>','הלוך 10.1 · <bdi>GD 201</bdi>','<span class="role">מנהל</span>')
        i+=rl(av('איתי'),'איתי','הלוך 10.1 · <bdi>GD 201</bdi>','<span class="role">מנהל</span>'+more)+rl(av('דנה'),'דנה','הלוך 10.1 · <bdi>GD 201</bdi>',more)+rl(av('רון'),'רון','עוד בלי טיסה',more)+'</div>'
        return o+gsheet(3,i,top=254)
    out['R06-GroupMembers.dc.html']=('השלמה ג · 6 · הקבוצה: חברים וניהול',b06())
    # ---- R07: inviting
    def b07():
        o=pic('hero')+veil(0,844,'var(--veil)')+topbar('הזמנת חברים',EG)
        o+='<div style="position: absolute; left: 0; right: 0; top: 122px; display: flex; flex-direction: column; align-items: center; gap: 14px; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span style="font-size: 15px; font-weight: 500">לקבוצה <b>%s</b></span><div style="text-shadow: none">%s</div><span style="font-size: 13px; font-weight: 500; max-width: 300px; text-align: center; line-height: 1.4">הקישור פותח את האפליקציה, או את האתר למי שאין. מי שהתקין בדרך מקליד את הקוד.</span></div>'%(GRP,codebox('SNWKBT',big=True))
        o+='<div class="sheet glass" style="gap: 10px"><div class="grab"></div><div style="display: flex; gap: 8px"><button class="pri">וואטסאפ</button><button class="sec" aria-label="העתקת הקישור" style="padding: 0; display: flex; align-items: center; justify-content: center">%s</button><button class="sec" aria-label="שיתוף" style="padding: 0; display: flex; align-items: center; justify-content: center">%s</button></div>'%(ic(P_LINK),ic(P_SHARE))
        o+='<span class="st" style="margin-top: 4px">הגדרות ההזמנה · מנהלים</span><div>'
        o+=rl('','מנהל מאשר כל מצטרף','כבוי: כל מי שיש לו את הקישור נכנס',sw(False))+rl('','תוקף','עד סוף הטיול, 16.1.2027')
        o+='<div class="rl"><span class="c"><span class="h danger">ביטול הקישור והחלפה בחדש</span><span class="m">הקישור והקוד הישנים מפסיקים לעבוד מיד. מי שכבר בקבוצה נשאר.</span></span></div></div></div>'
        return o
    out['R07-Invite.dc.html']=('השלמה ג · 7 · מזמינים חברים',b07())
    # ---- R08: signing in
    def b08():
        o=pic('hero','dawn','night')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+mount_top(None,'עוד אין מידע על הרכבלים',eg=False).replace('class="dot"','class="sd q"')
        o+=veil(0,844,'rgba(6,11,22,.38)')  # a sheet dims the whole screen, the top row included: no edge under the pill
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div><p class="ttl">כניסה</p><p class="sub">יצירת קבוצה דורשת התחברות. כל השאר עובד בלי.</p></div>'
        o+='<div style="display: flex; gap: 8px">%s</div>'%''.join('<div style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; font-size: 13px; font-weight: 500"><span class="ic" style="background: var(--line)">%s</span>%s</div>'%(ic(p),t) for p,t in((P_USER,'רק השם'),('<path d="M4 7h16v11H4z"/><path d="m4 7 8 6.500L20 7M4 4l16 17"/>','בלי מיילים'),('<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3M4 4l16 17"/>','בלי סיסמה')))
        o+='<button class="gbtn ap">%s המשך עם אפל</button><button class="gbtn gg">%s המשך עם גוגל</button>'%(G_APPLE,G_GOOGLE)
        o+='<button class="lnk">לא עכשיו</button><span class="sub" style="font-size: 12.500px; text-align: center">מה שכבר שמור בטלפון הזה עובר לחשבון, ומופיע גם באתר.</span></div>'
        return o
    out['R08-SignIn.dc.html']=('השלמה ג · 8 · כניסה',b08())
    # ---- R09: you, signed in
    def b09():
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('אני',EG)
        o+='<div style="position: absolute; left: 20px; right: 20px; top: 112px; display: flex; align-items: center; gap: 14px; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)">%s<span style="display: flex; flex-direction: column; gap: 2px; min-width: 0"><span style="font-size: 27px; font-weight: 700; line-height: 1.1">נועה</span><span style="font-size: 13.500px; font-weight: 500">מחובר עם גוגל</span></span><button class="sec glass" style="margin-inline-start: auto; height: 44px; text-shadow: none">שינוי</button></div>'%av('נועה','b')
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 196px"><div class="rl" style="min-height: 40px"><span class="st">הקבוצות שלי</span></div>'
        o+=rl(av('ג'),GRP,'6 חברים · מנהל',CHL)+rl(av('מ'),'משפחה, פברואר','4 חברים',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 372px">'
        o+=rl(ic(P_GEAR,20),'הגדרות','צליל, רטט, תצוגה, מדידה',CHL)+rl(ic(P_GLOBE,20),'שפה','','עברית'+CHL)+rl(ic(P_INFO,20),'אודות','קרדיטים, פרטיות, תמיכה',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 556px">'+rl('','דרכי כניסה','גוגל מחובר · אפל לא מחובר',CHL)+rl('','יציאה מהחשבון')+'<div class="rl"><span class="c"><span class="h danger">מחיקת החשבון</span></span></div></div>'
        o+='<div class="sub" style="position: absolute; left: 24px; right: 24px; top: 742px; font-size: 12.500px; text-align: center; text-shadow: 0 0 6px var(--halo)">הטיול שלך והקבוצות מסונכרנים בין האתר לאפליקציה.</div>'
        return o
    out['R09-YouSignedIn.dc.html']=('השלמה ג · 9 · אני: עם חשבון',b09())
    # ---- R10: you, a guest (in a group, with no account)
    def b10():
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('אני',EG)
        o+='<div style="position: absolute; left: 20px; right: 20px; top: 112px; display: flex; align-items: center; gap: 14px; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span class="av b g glass" style="text-shadow: none">%s</span><span style="display: flex; flex-direction: column; gap: 2px; min-width: 0"><span style="font-size: 27px; font-weight: 700; line-height: 1.1">דנה</span><span style="font-size: 13.500px; font-weight: 500">אורח בקבוצה, בלי חשבון</span></span></div>'%ic(P_USER,30)
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 196px; padding: 14px 16px; display: flex; flex-direction: column; gap: 10px"><span style="font-size: 17px; font-weight: 700">שומרים את המקום?</span><span class="sub">נכנסת בלי הרשמה, וזה מספיק. רק מי שמחליף טלפון או מוחק את האפליקציה צריך לבקש מהמנהל לחזור. חשבון שומר את המקום שלך.</span><button class="gbtn ap">%s שמירה עם אפל</button><button class="gbtn gg">%s שמירה עם גוגל</button></div>'%(G_APPLE,G_GOOGLE)
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 478px">'+rl(av('ג'),GRP,'6 חברים',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 552px">'
        o+=rl(ic(P_GEAR,20),'הגדרות','צליל, רטט, תצוגה, מדידה',CHL)+rl(ic(P_GLOBE,20),'שפה','','עברית'+CHL)+rl(ic(P_INFO,20),'אודות','קרדיטים, פרטיות, תמיכה',CHL)+'</div>'
        return o
    out['R10-YouGuest.dc.html']=('השלמה ג · 10 · אני: אורח',b10())
    # ---- R11: the cabin evening. the games
    def b11():
        o=pic('vill','morning','night')+base('vill')+'<div class="tag" style="left: 62px; top: 76px; height: 32px; border-radius: 16px; padding: 0 12px; direction: rtl; font-size: 13px">↑ חזרה להר</div>'
        o+='<div class="sheet glass" style="gap: 6px"><div class="grab"></div><div style="display: flex; align-items: center; gap: 10px; min-height: 48px"><div><p class="ttl">ערב בבקתה</p><p class="sub">5 משחקים · בלי קליטה</p></div><span style="margin-inline-start: auto">%s</span></div><div>'%EG
        for k,(key,name,tag_,g,best) in enumerate(GM):
            o+='<div class="rl" style="min-height: 64px"><span class="ic" style="background: var(--line)">%s</span><span class="c"><span class="h" style="font-weight: 700">%s</span><span class="m">%s</span></span><span class="e" style="flex-direction: column; align-items: flex-end; gap: 1px"><span style="font-size: 12.500px; font-weight: 500; color: %s">%s</span></span></div>'%(ic(g,24),name,tag_,'var(--ink2)' if key=='fresh' else 'var(--ink)',best)
        o+='</div><button class="sec" style="flex: none; height: 44px; margin-top: 6px">הטבלה של %s</button></div>'%GRP
        return o
    out['R11-CabinGames.dc.html']=('השלמה ג · 11 · ערב בבקתה: המשחקים',b11())
    # ---- R12: the shell around a game: how it ends
    def b12():
        st=lambda f:ic(P_STAR,34,1.6,'currentColor' if f else 'none')
        o=pic('hero','night','night')+veil(0,844,'rgba(6,11,22,.62)')
        o+='<button class="xb glass" aria-label="יציאה מהמשחק" style="position: absolute; top: 52px; right: 12px">%s</button><button class="xb glass" aria-label="צליל" style="position: absolute; top: 52px; left: 12px">%s</button>'%(XI,ic(P_SND,20))
        o+='<div class="sub" style="position: absolute; left: 0; right: 0; top: 118px; text-align: center; color: #EAF1F8; font-size: 12.500px">מאחור: המשחק עצמו, כמו היום</div>'
        o+='<div class="glass" style="position: absolute; left: 16px; right: 16px; top: 190px; border-radius: 28px; padding: 22px 20px 18px; box-sizing: border-box; display: flex; flex-direction: column; align-items: center; gap: 12px">'
        o+='<span class="st">הירידה של החבר׳ה · <bdi>Tatra 2</bdi></span><div class="stars">%s%s%s</div><span class="cd" style="font-size: 84px">1:42</span><span style="display: flex; align-items: center; gap: 8px"><span class="new">שיא חדש</span><span class="sub">הקודם <bdi>1:51</bdi></span></span>'%(st(1),st(1),st(0))
        o+='<div style="align-self: stretch; border-top: 1px solid var(--line)">'+rl(av('ג'),'מקום 2 ב%s'%GRP,'דנה לפניך: <bdi>1:38</bdi>',EG)+'</div>'
        o+='<div style="align-self: stretch; display: flex; gap: 8px"><button class="pri">עוד פעם</button><button class="sec">המסלול הבא</button></div><button class="lnk">חזרה לבקתה</button></div>'
        return o
    out['R12-GameEnd.dc.html']=('השלמה ג · 12 · סוף משחק',b12())
    # ---- R13: settings
    def b13():
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('הגדרות')
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 112px">'+rl(ic(P_SND,20),'צליל','בכרטיס הטיסה ובמשחקים',sw(True))+rl(ic(P_VIB,20),'רטט','',sw(True))+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 238px; padding: 12px 16px 14px; display: flex; flex-direction: column; gap: 10px"><div style="display: flex; align-items: center; gap: 12px"><span class="ic" style="width: 36px; height: 36px; background: var(--line)">%s</span><span class="c" style="display: flex; flex-direction: column"><span style="font-size: 15.500px; font-weight: 500">תצוגה</span><span class="sub" style="font-size: 12.500px">אוטומטי: לפי השעה בגודאורי</span></span></div><div class="seg"><button class="on">אוטומטי</button><button>יום</button><button>לילה</button></div></div>'%ic(P_SUN,20)
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 392px">'+rl(ic(P_GLOBE,20),'שפה','לפי הטלפון','עברית'+CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 466px">'+rl(ic(P_CHART,20),'מדידת שימוש ודיווח שגיאות','בלי שם ובלי עוגיות. עוזר לנו לתקן תקלות.',sw(True))+rl('','מה נאסף','',CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 606px">'+rl(ic(P_UNDO,20),'איפוס השיאים במשחקים')+'</div>'
        return o
    out['R13-Settings.dc.html']=('השלמה ג · 13 · הגדרות',b13())
    # ---- R14: language
    def b14():
        o=b13()+veil(0,844,'rgba(6,11,22,.38)')
        o+='<div class="sheet glass lg" style="gap: 4px"><div class="grab"></div><p class="ttl" style="margin: 6px 0 2px">שפה</p><div>'
        for name,sub,on in(('עברית','',True),('English','',False),('Русский','',False),('ქართული','',False)):
            o+='<div class="rl" style="min-height: 56px"><b style="direction: ltr; unicode-bidi: isolate">%s</b><span class="e" style="color: var(--acc)">%s</span></div>'%(name,CHK if on else '')
        o+='<div class="rl" style="min-height: 56px"><span class="c"><span class="h">לפי הטלפון (אוטומטי)</span><span class="m">טלפון בעברית: עברית. כל טלפון אחר: אנגלית.</span></span></div></div></div>'
        return o
    out['R14-Language.dc.html']=('השלמה ג · 14 · שפה',b14())
    # ---- R15: about
    def b15():
        o=pic('hero','dawn','night')+veil(0,844,'var(--veil)')+topbar('אודות',EG)
        o+='<div style="position: absolute; left: 20px; right: 20px; top: 112px; display: flex; flex-direction: column; gap: 4px; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span style="font-size: 44px; font-weight: 700; line-height: 1; direction: ltr; text-align: right; unicode-bidi: isolate">Gudi</span><span style="font-size: 14px; font-weight: 500">גרסה <bdi>1.2</bdi> · אתר ואפליקציה לא רשמיים, בלי קשר ל-<bdi>MTA</bdi>.</span></div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 196px">'+rl(ic(P_USER,20),'פיני זולברג','מי בנה · יחד עם <bdi>Claude</bdi>','<bdi>GitHub</bdi>'+CHL)+'</div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 270px"><div class="rl" style="min-height: 40px"><span class="st">מאיפה הנתונים</span></div>'
        o+=rl('','מסלולים, רכבלים וכבישים','התורמים של <bdi>OpenStreetMap</bdi>, ברישיון <bdi>ODbL</bdi>',CHL)+rl('','גבהים ותבליט','<bdi>AWS Terrain Tiles</bdi>, בעיקר <bdi>SRTM</bdi>')+rl('','תחזית מזג האוויר','<bdi>Open-Meteo</bdi>, ברישיון <bdi>CC BY 4.0</bdi>',CHL)
        o+='<div class="rl"><span class="c"><span class="m">המפה הרשמית של <bdi>MTA</bdi> שימשה רק לזיהוי שמות וצבעים של מסלולים, ולא הועתקה.</span></span></div></div>'
        o+='<div class="glass box" style="position: absolute; left: 12px; right: 12px; top: 556px">'+rl(ic(P_SHLD,20),'מדיניות הפרטיות','',CHL)+rl(ic(P_HELP,20),'תמיכה','',CHL)+rl(ic(P_DOC,20),'רישיונות קוד פתוח','גופנים, ספריות וצלילים',CHL)+'</div>'
        return o
    out['R15-About.dc.html']=('השלמה ג · 15 · אודות',b15())
    for fn,(title,body) in out.items():phone(fn,title,body)
    brief()
    print('completion part 3 boards written to',OUT)
def brief():
    sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -150px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 3 · 23.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">החבר׳ה, הבקתה ואני</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">קבוצה והזמנה, כניסה וחשבון, המשחקים, הגדרות, שפה ואודות</p></div></div>')%(sc,U('wide-dawn-cloud'))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">החלק האחרון של סבב ההשלמה: האנשים והערב. גם כאן שום דבר לא עובר ל״עמוד״ משלו בלי סיבה: הקבוצה היא גיליון מעל ההר, עם הדגלים של המפגשים נעוצים בו; המשחקים הם הערב בכפר; והחשבון, ההגדרות והאודות נמצאים מאחורי כפתור אחד חדש, ״אני״. המסירה: <span dir="ltr">design/handoff/2026-11-23-redesign-people/</span>.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוחות 1 עד 7</p><h2>החבר׳ה</h2><p class="p">בלי קבוצה: שלושה סמלים, קוד של שש אותיות ו״יצירת קבוצה״. עם קבוצה: ארבע לשוניות, מפגשים, טיסות, שיאים וחברים. המפגש הבא תמיד למעלה, והדגל שלו על ההר. טיסה היא כרטיס נייר קטן, עם מי שעליה.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 8 עד 10</p><h2>כניסה ו״אני״</h2><p class="p">כפתור עגול אחד בראש ההר, ליד הגלולה. לאורח הוא סמל של אדם, ולמי שנכנס האות הראשונה של השם. מאחוריו: השם, הקבוצות, ההגדרות, השפה והאודות. אורח בקבוצה רואה קודם ״שומרים את המקום?״.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 11 עד 15</p><h2>הבקתה וההגדרות</h2><p class="p">״ערב בבקתה״ בכפר: חמישה משחקים ברשימה אחת, עם השיא שלך. המשחקים עצמם לא מצוירים מחדש; רק המעטפת: יציאה, צליל, ומסך סיום אחד לכולם. הגדרות, שפה ואודות: שורות, מתגים, ובלי הסברים ארוכים.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>כפתור ״אני״ חדש על ההר:</b> עיגול 44 בקצה הגלולה שבראש המסך; הגלולה מתקצרת ב-52. זו הדרך היחידה לחשבון, להגדרות, לשפה ולאודות.',
        '<b>הקבוצה והמשחקים בכפר:</b> השורות ״החבר׳ה״ ו״ערב בבקתה״ בגיליון של הכפר (לוח K17). הקבוצה גם מ״אני״, מדגל על ההר ומקישור הזמנה.',
        '<b>לשונית הפתיחה לפי הזמן:</b> לפני הטיול ״טיסות״; מיום הטיסה ועד סוף הטיול ״מפגשים״; אחריו ״שיאים״.',
        '<b>טיסה בקבוצה היא כרטיס נייר קטן.</b> הנייר נשאר רק לטיסות: הכרטיס שלך, והכרטיסים של החבר׳ה. החשבון כבר לא ״סקי־פס״.',
        '<b>בלי אישור מנהל, אין שמות בהזמנה:</b> כשהקבוצה דורשת אישור, מסך ההזמנה מראה רק שם, תאריכים ומספר חברים (כמו היום בשרת).',
        '<b>אפל לפני גוגל באייפון,</b> ובאנדרואיד רק גוגל, כמו היום. הכפתורים בצבעים הרשמיים של כל אחת, לא בסגול שלנו.',
        '<b>מחיקה והוצאה בשתי נגיעות,</b> כמו היום: הנגיעה הראשונה הופכת את השורה לאדומה עם ״לחיצה נוספת...״ לארבע שניות.',
        '<b>המשחקים לא מצוירים מחדש.</b> חמשת המשחקים נשארים כמו שהם. חדש רק מה שמסביב: הרשימה, שני הכפתורים בראש, ומסך הסיום.',
        '<b>שפה, אושר (פיני, 23.11.2026):</b> שורה ב״אני״ ובהגדרות, שפותחת גיליון, כל שפה בכתב שלה. תג השפה יורד מהמסך הראשי, כי ״אני״ במרחק נגיעה אחת מכל מסך. זה מחליף את החלטה 47 באפליקציות בשפה החדשה.')+'</div>'
      '<div class="card" style="flex: 1"><h2>איך מגיעים</h2>'+li(
        '<b>״אני״:</b> העיגול בראש ההר. מכל מסך של ההר, בנגיעה אחת.',
        '<b>״החבר׳ה״:</b> בכפר; ב״אני״ (הקבוצות שלי); נגיעה בדגל על ההר; קישור הזמנה או קוד.',
        '<b>״כניסה״:</b> ״יצירת קבוצה״ לאורח; ״אני״ לאורח; ״שמירה עם...״ לאורח בקבוצה.',
        '<b>״ערב בבקתה״:</b> בכפר; ״לשחק עכשיו״ בשיאים של הקבוצה.',
        '<b>״הזמנה״:</b> הכפתור בראש גיליון הקבוצה. לכל חבר, לא רק למנהל; ההגדרות שבתחתית רק למנהלים.',
        '<b>אנדרואיד:</b> לפי הכללים של חלק 1, בלי לוחות נפרדים: גיליון צמוד, משטח אטום, מחוות החזרה.')+'</div></div>'
      '<div class="card"><h2>ביושר</h2>'+li(
        '<b>מה אמיתי:</b> ההר, הכפר, הרכבלים, התחנות והגבהים; שמות המשחקים והתיאורים שלהם; מקורות הנתונים והרישיונות; ארבע השפות.',
        '<b>מה דוגמה, ומסומן:</b> האנשים (שמות מומצאים), הקבוצה, הטיסות, המפגשים, השיאים, הקוד, ומספר הגרסה.',
        '<b>סמלי המשחקים</b> הם סימנים פשוטים שציירתי, לא האמנות של המשחקים. אפשר להחליף אותם בתמונות הממוזערות שכבר באתר.',
        '<b>מסך הסיום</b> מצויר על ההר בלילה, כי את המשחק עצמו אני לא מצייר מחדש. במשחק אמיתי מאחוריו נמצא המשחק, מעומעם.',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי של הלוחות, לא בתוך הקנבס.')+'</div>')
    wide('R00-Brief.dc.html','סבב ההשלמה, חלק 3: החבר׳ה, הבקתה ואני',body,1280,1570)
if __name__=='__main__':
    build()
