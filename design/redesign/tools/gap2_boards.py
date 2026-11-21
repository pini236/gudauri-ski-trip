#!/usr/bin/env python3
"""Completion round, part 2 (Q00 to Q10): what the new language had not drawn outside the ski day.
The lifts and a lift's panel, the meeting point, "your trip" and the boarding pass, the ski pass prices and
the countdown to the season. Lines, lifts, stations, prices and dates come from site/data; anything live
(lift status, weather, the trip) is an example and carries the tag. Design only.
Usage: python3 design/redesign/tools/gap2_boards.py"""
import json,datetime
from peak_boards import *
import peak_b4 as B4
import gap_boards as G
from peak_b4 import EG,n0,veil,fade
from gap_boards import CHEV,XI,LIFTI,LOCK,KIND,li
IC_FLAG='<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 21V4"/><path d="M6 4.500h11l-2.500 3.700 2.500 3.800H6" fill="currentColor"/></svg>'
CSS6="""
.H{--paper:#FFFFFF}.H .night{--paper:#16213A}
.H .lr{display:flex;align-items:center;gap:10px;min-height:48px}
.H .lr+.lr{border-top:1px solid var(--line)}
.H .lr b{font-size:15.5px;font-weight:700;direction:ltr;unicode-bidi:isolate}
.H .lr .k{font-size:12.5px;color:var(--ink2);white-space:nowrap}
.H .lr .s{margin-inline-start:auto;font-size:13.5px;font-weight:500;white-space:nowrap;display:flex;align-items:center;gap:6px}
.H .sd{width:9px;height:9px;border-radius:50%;flex:none;background:var(--good)}
.H .sd.x{background:var(--red)}.H .sd.q{background:transparent;border:1.5px solid var(--ink2);box-sizing:border-box}
.H .okc{color:var(--good)}.H .badc{color:var(--red)}
.H .tog{height:36px;padding:0 13px;border-radius:18px;border:1px solid var(--line);font-size:13px;font-weight:500;display:inline-flex;align-items:center;gap:6px;white-space:nowrap}
.H .stn{position:absolute;width:14px;height:14px;border-radius:50%;transform:translate(-50%,-50%);background:#fff;border:3px solid var(--acc);box-sizing:border-box;box-shadow:0 1px 4px rgba(0,0,0,.35)}
.H .flag{position:absolute;transform:translate(-1.500px,-100%);display:flex;align-items:flex-start;direction:ltr}
.H .flag i{width:3px;height:50px;background:#0E2233;border-radius:2px;display:block;box-shadow:0 0 0 1px rgba(255,255,255,.7)}
.H .flag b{height:26px;padding:0 16px 0 9px;background:var(--acc);color:var(--onacc);font-size:13.500px;font-weight:700;display:flex;align-items:center;clip-path:polygon(0 0,100% 0,86% 50%,100% 100%,0 100%);font-variant-numeric:tabular-nums}
.H .tc{height:44px;min-width:62px;padding:0 12px;box-sizing:border-box;border-radius:22px;border:1px solid var(--line);font-size:15px;font-weight:500;display:inline-flex;align-items:center;justify-content:center;font-variant-numeric:tabular-nums;white-space:nowrap}
.H .tc.on{background:var(--acc);color:var(--onacc);border-color:transparent;font-weight:700}
.H .passw{position:absolute;left:20px;right:20px;filter:drop-shadow(0 12px 22px rgba(10,25,45,.30))}
.H .pass{position:relative;border-radius:22px;background:var(--paper);color:var(--ink);padding:18px 20px 0;box-sizing:border-box;display:flex;flex-direction:column;gap:14px;-webkit-mask:radial-gradient(circle 10px at 0 calc(100% - 100px),#0000 97%,#000),radial-gradient(circle 10px at 100% calc(100% - 100px),#0000 97%,#000);-webkit-mask-composite:source-in;mask-composite:intersect}
.H .stub{height:100px;box-sizing:border-box;display:flex;align-items:center;gap:10px;border-top:2px dashed var(--line);margin:0 -20px;padding:0 20px}
.H .fl b.code{font-size:54px;font-weight:300;line-height:1;direction:ltr;letter-spacing:.01em}
.H .fl{display:flex;flex-direction:column;gap:2px;min-width:0}
.H .fl span{font-size:11.500px;color:var(--ink2)}
.H .fl b{font-size:15.500px;font-weight:500;font-variant-numeric:tabular-nums}
.H .cal{display:grid;grid-template-columns:repeat(7,1fr);row-gap:2px}
.H .cal span{height:42px;display:flex;align-items:center;justify-content:center;font-size:15px;font-variant-numeric:tabular-nums;position:relative}
.H .cal .wd{height:24px;font-size:11.500px;color:var(--ink2)}
.H .cal .rg{background:rgba(107,63,212,.14)}.H .night .cal .rg{background:rgba(183,156,255,.20)}
.H .cal .e{background:var(--acc);color:var(--onacc);font-weight:700;border-radius:21px}
.H .cal .sk::after{content:'';position:absolute;bottom:5px;left:50%;width:5px;height:5px;border-radius:50%;background:var(--acc);transform:translateX(-50%)}
.H .cal .off{color:var(--ink2);opacity:.45}
.H .stp{width:44px;height:44px;border-radius:50%;border:1px solid var(--line);display:flex;align-items:center;justify-content:center;font-size:23px;font-weight:300;flex:none}
.H .pc{flex:1;min-width:0;height:54px;border-radius:14px;border:1px solid var(--line);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:1px}
.H .pc b{font-size:15px;font-weight:700}.H .pc span{font-size:10.500px;color:var(--ink2);direction:ltr}
.H .pc.on{background:var(--acc);border-color:transparent;color:var(--onacc)}.H .pc.on span{color:var(--onacc)}
.H .cd{font-size:112px;font-weight:300;line-height:.86;direction:ltr;letter-spacing:-.04em;font-variant-numeric:tabular-nums}
.H .note{display:flex;gap:8px;align-items:flex-start;font-size:12.500px;line-height:1.4;color:var(--ink2)}
"""
MB.CSS=MB.CSS+CSS6
PR=json.load(open(os.path.join(P.ROOT,'site/data/prices.json')))
RES=[r for r in json.load(open(os.path.join(P.ROOT,'site/data/resorts.json')))['resorts'] if r['id']=='gudauri'][0]
PL=json.load(open(os.path.join(P.ROOT,'site/data/places.json')))
TODAY=datetime.date(2026,11,21);OPEN=datetime.date.fromisoformat(RES['season']['open']);DAYS=(OPEN-TODAY).days
WD=['יום שני','יום שלישי','יום רביעי','יום חמישי','יום שישי','שבת','יום ראשון']
dmy=lambda d:'%d.%d.%d'%(d.day,d.month,d.year)
# the example report: two lifts closed
CLOSED={'Sadzele':'רוח','Firni':None}
ORDER=['Goodaura','Shino','Soliko','Kudebi','Sadzele','Firni','Kobi','Kikilo','New Goodaura','Pirveli','Truso','Snow Park','Khada','Zuma']
LD={l['name']:l for l in P.D['lifts'] if l.get('name')}
def facts(l):
    a=[KIND.get(l['kind'],'רכבל')]
    if l.get('dur'):a.append('%s דק׳'%l['dur'])
    return ' · '.join(a)
def lrow(name,state):
    l=LD[name]
    if state is None:s=''
    elif name in CLOSED:s='<span class="s badc"><i class="sd x"></i>סגור%s</span>'%(' · '+CLOSED[name] if CLOSED[name] else '')
    else:s='<span class="s okc"><i class="sd"></i>פתוח</span>'
    if state is None and l.get('rise'):s='<span class="s" style="color: var(--ink2); font-weight: 400">%s מ׳ למעלה</span>'%n0(float(l['rise']))
    return '<div class="lr"><b>%s</b><span class="k">%s</span>%s</div>'%(name,facts(l),s)
def stations(V,ymin=110,ymax=640):
    pts=[]
    for l in P.LIFTS:
        if not l.get('name'):continue
        for e in P.lift_ends(l):
            x,y,vis,_=P.pt(V,e[0],e[1])
            if vis and ymin<y<ymax and 8<x<V['W']-8 and all(np.hypot(x-a,y-b)>13 for a,b in pts):pts.append((x,y))
    return pts
def nowp(dot,text,extra=''):
    return '<div class="now glass"><span class="%s"></span><span>%s</span><span style="margin-inline-start: auto">%s</span></div>'%(dot,text,extra)
def topbar(title,extra=''):
    return '<div class="recp glass" style="background: transparent; border-color: transparent; box-shadow: none; -webkit-backdrop-filter: none; backdrop-filter: none; padding: 0 6px">%s<span class="ttl" style="font-size: 21px">%s</span><span style="margin-inline-start: auto">%s</span></div>'%(CHEV,title,extra)
def build():
    out={}
    # ---- Q01: the lifts, with a fresh report (example)
    opn=[n for n in ORDER if n not in CLOSED]
    def b01():
        o=pic('hero')+both(lambda m,l:P.runs_svg(VH,m,dim=True))+both(lambda m,l:P.lifts_svg(VH,m,l,only=opn))+both(lambda m,l:P.lifts_svg(VH,m,l,dim=True,cabins=False,only=list(CLOSED)))
        for n in CLOSED:
            e=P.lift_ends(P.flift(n))[1];x,y,vis,_=P.pt(VH,e[0],e[1])
            if vis:o+=tag(x,y-16,'%s · סגור'%n,color='var(--red)')
        o+=nowp('dot','%d מתוך %d רכבלים פתוחים · עודכן לפני 6 דק׳'%(len(opn),len(ORDER)),EG)
        o+='<div class="sheet glass" style="top: 384px; gap: 6px; overflow: hidden"><div class="grab"></div><div style="display: flex; align-items: center; gap: 10px; min-height: 44px"><span class="ttl">רכבלים</span><span class="num" style="font-size: 23px">%d/%d</span><button class="tog" style="margin-inline-start: auto">רק מה שפתוח בשבילי</button></div>'%(len(opn),len(ORDER))
        o+='<div>%s</div></div>'%''.join(lrow(n,True) for n in ORDER[:8])+fade(790,54)
        return o
    out['Q01-Lifts.dc.html']=('השלמה ב · 1 · הרכבלים, עם דיווח',b01())
    # ---- Q02: the lifts, no report (the true state today, before the season)
    def b02():
        o=pic('hero')+both(lambda m,l:P.runs_svg(VH,m,dim=True))+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False))
        o+='<div class="now glass"><span class="sd q"></span><span>עוד אין מידע על הרכבלים</span></div>'
        o+='<div class="sheet glass" style="top: 384px; gap: 6px; overflow: hidden"><div class="grab"></div><div style="display: flex; align-items: center; gap: 10px; min-height: 44px"><span class="ttl">רכבלים</span><span class="num" style="font-size: 23px">%d</span><span class="sub" style="margin-inline-start: auto">אין דיווח</span></div>'%len(ORDER)
        o+='<div class="lr" style="min-height: 52px"><span style="display: flex; flex-direction: column; gap: 1px"><span style="font-size: 14.500px; font-weight: 500">פתיחה מתוכננת %s</span><span class="k">לפי MTA · יכולה להשתנות עם השלג</span></span><span class="s"><span class="num" style="font-size: 25px">%d</span><span class="k">ימים לפתיחה</span></span></div>'%(dmy(OPEN),DAYS)
        o+='<div style="border-top: 1px solid var(--line)">%s</div></div>'%''.join(lrow(n,None) for n in ORDER[:7])+fade(790,54)
        return o
    out['Q02-LiftsNoReport.dc.html']=('השלמה ב · 2 · הרכבלים, בלי דיווח',b02())
    # ---- Q03: a lift's panel
    LF='Goodaura';lf=LD[LF];pl=P.flift(LF);bot,top=P.lift_ends(pl)
    down=[p for p in P.D['pistes'] if p.get('named') and LF in (p.get('fromLifts') or [])]
    PK={q['id']:q['kind'] for q in PL['places']}
    def near(end):
        c={}
        for st in PL['stations']:
            if any(e['lift']==LF and e['end']==end for e in st['ends']):
                for q in st['places']:c[PK[q['p']]]=c.get(PK[q['p']],0)+1
        return c
    KN=dict(food='אוכל',cafe='בית קפה',aid='עמדת הצלה',wc='שירותים');PN={q['id']:q for q in PL['places']}
    def nearrow(lab,end):
        ids=[]
        for st in PL['stations']:
            if any(e['lift']==LF and e['end']==end for e in st['ends']):ids=[q['p'] for q in sorted(st['places'],key=lambda q:q['d'])]
        if not ids:t='<span class="sub">אין מידע על מקומות כאן</span>'
        else:
            t=' · '.join('<bdi>%s</bdi>'%PN[i]['name'] if PN[i]['name'] else KN[PN[i]['kind']] for i in ids[:3])
            if len(ids)>3:t+=' · <b style="color: var(--acc)">כל ה-%d</b>'%len(ids)
        return '<div style="display: flex; flex-direction: column; gap: 2px; padding: 7px 0"><span class="sub" style="font-size: 12.500px">%s</span><span style="font-size: 14px; font-weight: 500">%s</span></div>'%(lab,t)
    chip=lambda p:'<span style="display: inline-flex; align-items: center; gap: 5px; font-size: 13.500px; font-weight: 500; white-space: nowrap"><i style="width: 11px; height: 11px; border-radius: 3px; background: %s; display: inline-block"></i><bdi>%s</bdi></span>'%(HEX[p['color']] if p['color']!='black' else '#14171B',p.get('name') or p['key'])
    def b03():
        V=VH;xb,yb,_,_=P.pt(V,bot[0],bot[1]);xt,yt,_,_=P.pt(V,top[0],top[1]);w,h,sc=390,844,1.45;cx=(xb+xt)/2;cy=(yb+yt)/2+(h/2-250)/sc
        cx=min(max(cx,w/2/sc),V['W']-w/2/sc);cy=min(max(cy,h/2/sc),V['H']-h/2/sc)
        def L(V,mode):
            s='<g opacity=".32">'+P.runs_svg(V,mode,dim=True)+'</g>'
            for p in down:s+=glow_run(V,p['key'],mode,w=1.9,halo=False)
            return s+P.lifts_svg(V,mode,'morning' if mode=='day' else 'night',only=[LF])
        o=B4.win('hero',w,h,cx,cy,sc,L,r=0,style=' position: absolute; left: 0; top: 0')
        o+=nowp('dot','%d מתוך %d רכבלים פתוחים · עודכן לפני 6 דק׳'%(len(opn),len(ORDER)),EG)
        o+='<div class="sheet glass" style="gap: 10px"><div class="grab"></div>'
        o+='<div style="display: flex; align-items: center; gap: 12px"><span class="ic" style="background: var(--line)">%s</span><div style="display: flex; flex-direction: column; gap: 2px; min-width: 0"><span class="lname">%s</span><span class="sub">%s · נבנה ב-%s</span></div><span class="s okc" style="margin-inline-start: auto; display: flex; align-items: center; gap: 6px; font-size: 14.500px; font-weight: 700"><i class="sd"></i>פתוח</span></div>'%(LIFTI,LF,KIND[lf['kind']],lf['year'])
        o+='<div class="nums" style="gap: 20px"><div><span class="num">%s</span><span class="cap">דק׳ למעלה</span></div><div><span class="num">%s</span><span class="cap">מ׳ למעלה</span></div><div><span class="num">%.1f</span><span class="cap">ק״מ</span></div></div>'%(lf['dur'],n0(float(lf['rise'])),lf['len']/1000)
        o+='<div class="rw" style="border-top: 1px solid var(--line); min-height: 40px"><span class="sub">תחנות</span><span class="r" style="color: var(--ink); font-weight: 500">%s מ׳ למטה, %s מ׳ למעלה</span></div>'%(n0(float(P.elev(*bot))),n0(float(P.elev(*top))))
        o+='<div style="display: flex; flex-direction: column; gap: 6px; padding: 4px 0 6px"><span class="sub">מסלולים מהתחנה העליונה</span><span style="display: flex; flex-wrap: wrap; gap: 6px 14px">%s</span></div>'%''.join(chip(p) for p in down)
        o+='<div style="border-top: 1px solid var(--line)">'+nearrow('ליד התחנה התחתונה','bottom')+nearrow('ליד התחנה העליונה','top')+'</div><span class="sub" style="font-size: 12px; margin-top: -4px">כדאי לבדוק במקום</span>'
        o+='<div style="display: flex; gap: 8px"><button class="pri">נפגשים כאן</button><button class="sec">המסלולים</button></div></div>'
        return o
    out['Q03-LiftPanel.dc.html']=('השלמה ב · 3 · רכבל אחד',b03())
    # ---- Q04: the meeting point. where?
    ST=stations(VH);gt=P.lift_ends(P.flift('Goodaura'))[1];fx,fy,_,_=P.pt(VH,gt[0],gt[1]);ftop=float(P.elev(*gt))
    def meetbase(dimst=False):
        o=pic('hero')+both(lambda m,l:P.runs_svg(VH,m,dim=True))+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))
        return o+''.join('<span class="stn" style="left: %.0fpx; top: %.0fpx%s"></span>'%(x,y,'; opacity: .55' if dimst else '') for x,y in ST)
    def b04():
        o=meetbase()+'<div class="now glass"><span style="display: flex">%s</span><span>איפה נפגשים? נוגעים בתחנה</span><button class="xb" aria-label="סגירה" style="margin-inline-start: auto; margin-inline-end: -10px">%s</button></div>'%(IC_FLAG,XI)
        o+='<div class="sheet glass" style="gap: 10px"><div class="grab"></div><span class="st">או בלחיצה אחת</span>'
        for t,n,w in(('09:30','רכבל הבוקר','התחנה התחתונה של <bdi>Goodaura</bdi>'),('13:00','צהריים','התחנה העליונה של <bdi>Goodaura</bdi>'),('16:30','סוף יום','התחנה התחתונה של <bdi>New Goodaura</bdi>')):
            o+='<div class="lr" style="min-height: 52px"><span class="tc" style="min-width: 66px; direction: ltr">%s</span><span style="display: flex; flex-direction: column; gap: 1px"><b style="direction: rtl">%s</b><span class="k">%s</span></span><span class="s" style="color: var(--ink2)">%s</span></div>'%(t,n,w,CHEV.replace('m9 6 6 6-6 6','m15 6-6 6 6 6'))
        return o+'</div>'
    out['Q04-MeetWhere.dc.html']=('השלמה ב · 4 · נפגשים: איפה?',b04())
    # ---- Q05: the flag is planted
    def b05():
        o=meetbase(True)+'<span class="stn" style="left: %.0fpx; top: %.0fpx; width: 18px; height: 18px; border-color: #0E2233"></span><div class="flag" style="left: %.0fpx; top: %.0fpx"><i></i><b>13:00</b></div>'%(fx,fy,fx,fy)
        o+='<div class="now glass"><span style="display: flex">%s</span><span>עוד 42 דקות למפגש</span><span style="margin-inline-start: auto; display: flex; align-items: center; gap: 6px">%s<button class="xb" aria-label="ניקוי הבחירה" style="margin-inline-end: -10px">%s</button></span></div>'%(IC_FLAG,EG,XI)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: flex-end; gap: 10px"><div style="display: flex; flex-direction: column; gap: 2px; min-width: 0"><span class="lname">Goodaura</span><span class="sub">התחנה העליונה · %s מ׳</span></div><span class="num" style="margin-inline-start: auto; font-size: 44px">13:00</span></div>'%n0(ftop)
        o+='<div style="display: flex; gap: 6px; overflow: hidden">%s</div>'%''.join('<button class="tc%s" style="flex: none">%s</button>'%(' on' if i==1 else '',t) for i,t in enumerate(('ב׳ 11.1','ג׳ 12.1','ד׳ 13.1','ה׳ 14.1')))
        o+='<div style="display: flex; gap: 6px; overflow: hidden">%s</div>'%''.join('<button class="tc%s" style="flex: none; direction: %s">%s</button>'%(' on' if t=='13:00' else '','rtl' if t=='אחרת' else 'ltr',t) for t in('09:30','12:00','13:00','16:30','אחרת'))
        o+='<div style="display: flex; gap: 8px"><button class="pri">שליחה בוואטסאפ</button><button class="sec">תמונה</button><button class="sec">קישור</button></div>'
        o+='<button class="sec" style="height: 44px">שמירה בקבוצה</button></div>'
        return o
    out['Q05-MeetSet.dc.html']=('השלמה ב · 5 · נפגשים: הדגל נעוץ',b05())
    # ---- Q06: the picture that is shared
    def b06():
        w,h,sc=326,408,1.0;cx=min(max(fx-10,w/2/sc),VH['W']-w/2/sc);cy=min(max(fy+70,h/2/sc),VH['H']-h/2/sc)
        def L(V,mode):
            return '<g opacity=".5">'+P.runs_svg(V,mode,dim=True)+'</g>'+P.lifts_svg(V,mode,'morning' if mode=='day' else 'night',cabins=False,only=['Goodaura','Shino'])
        px,py=w/2+(fx-cx)*sc,h/2+(fy-cy)*sc
        o=topbar('תמונה לשיתוף',EG)
        o+='<div style="position: absolute; left: 32px; top: 118px; width: %dpx; height: %dpx; border-radius: 18px; overflow: hidden; box-shadow: 0 14px 34px rgba(10,25,45,.28)">%s'%(w,h,B4.win('hero',w,h,cx,cy,sc,L,r=0))
        o+='<span class="stn" style="left: %.0fpx; top: %.0fpx; width: 18px; height: 18px; border-color: #0E2233"></span><div class="flag" style="left: %.0fpx; top: %.0fpx"><i></i><b>13:00</b></div>'%(px,py,px,py)
        o+='<div style="position: absolute; left: 0; right: 0; bottom: 0; padding: 60px 18px 16px; background: linear-gradient(180deg, transparent, rgba(8,16,32,.82)); color: #fff; display: flex; flex-direction: column; gap: 2px"><span style="font-size: 31px; font-weight: 700; line-height: 1.1">נפגשים ב-13:00</span><span style="font-size: 15px; font-weight: 500"><bdi>Goodaura</bdi>, התחנה העליונה · %s מ׳</span><span style="font-size: 12.500px; opacity: .85; display: flex; justify-content: space-between"><span>יום שלישי, 12.1</span><span dir="ltr">Gudi · Gudauri</span></span></div></div>'%n0(ftop)
        o+='<div class="sub" style="position: absolute; left: 32px; right: 32px; top: 544px; text-align: center">התמונה היא ההודעה: המקום על ההר, השעה והיום. מי שמקבל לא צריך לקרוא מפה.</div>'
        o+='<div style="position: absolute; left: 16px; right: 16px; bottom: 18px; display: flex; gap: 8px"><button class="pri">שיתוף</button><button class="sec">שמירה בטלפון</button></div>'
        return o
    out['Q06-MeetShare.dc.html']=('השלמה ב · 6 · נפגשים: התמונה שמשתפים',b06())
    # ---- Q07: your trip. the boarding pass
    FLY=datetime.date(2027,1,10);dfly=(FLY-TODAY).days
    PLANE='<svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" style="transform: scaleX(-1)"><path d="M21 15.500v-2l-8-5V3.800a1.500 1.500 0 0 0-3 0V8.500l-8 5v2l8-2.500v5l-2 1.500V21l3.500-1 3.500 1v-1.500L13 18v-5z" transform="rotate(90 12 12)"/></svg>'
    def passcard(ret=False,blank=False):
        a,b=('TBS','TLV') if ret else ('TLV','TBS');ca,cb=('טביליסי','תל אביב') if ret else ('תל אביב','טביליסי')
        o='<div class="pass"><div style="display: flex; align-items: center; gap: 8px"><span class="st">הטיול שלך</span><span class="tog" style="height: 24px; padding: 0 9px; font-size: 11.500px; font-weight: 700">%s</span><span style="margin-inline-start: auto">%s</span></div>'%('חזור' if ret else 'הלוך','' if blank else EG)
        o+='<div style="display: flex; align-items: center; justify-content: space-between"><div class="fl"><b class="code">%s</b><span>%s</span></div><span style="color: var(--acc)">%s</span><div class="fl" style="align-items: flex-end"><b class="code">%s</b><span>%s</span></div></div>'%(a,ca,PLANE,b if not blank else '<span style="opacity: .28; font-size: inherit; color: inherit">TBS</span>',cb if not blank else 'לאן נוחתים?')
        if blank:
            return o+'<div class="stub" style="justify-content: space-between"><span class="ttl" style="font-size: 25px">מתי טסים?</span><span class="sub" style="font-size: 12.500px">בלי הרשמה. נשמר רק בטלפון.</span></div></div>'
        f=lambda k,v:'<div class="fl"><span>%s</span><b>%s</b></div>'%(k,v)
        o+='<div style="display: grid; grid-template-columns: 1.5fr 1fr 1fr; gap: 12px 10px">%s%s%s%s%s%s</div>'%(f('תאריך','יום א׳ · 10.1.2027'),f('המראה','<bdi>15:30</bdi>'),f('נחיתה','<bdi>20:05</bdi>'),f('נוסע','אורח'),f('טיסה','<bdi>GD 201</bdi>'),f('כבודה','תיק יד'))
        o+='<div class="stub"><span class="cd" style="font-size: 66px">%d</span><span style="display: flex; flex-direction: column; gap: 2px"><b style="font-size: 16px">ימים לטיסה</b><span class="sub" style="font-size: 12.500px">4 ימי סקי · 11 עד 14.1</span></span></div></div>'%dfly
        return o
    def b07():
        o=pic('hero','sunset','night')+veil(0,844,'var(--veil)')+topbar('',''+'<button class="sec" style="height: 44px; min-width: 44px; background: var(--glass)">עריכה</button>')
        o+='<div class="passw" style="top: 126px; left: 34px; right: 34px; opacity: .78"><div class="pass" style="height: 150px; -webkit-mask: none; mask: none"><div style="display: flex; align-items: center; gap: 8px"><span class="st">הטיול שלך</span><span class="tog" style="height: 24px; padding: 0 9px; font-size: 11.500px; font-weight: 700">חזור · 15.1</span></div></div></div>'
        o+='<div class="passw" style="top: 176px">%s</div>'%passcard()
        o+='<div style="position: absolute; left: 20px; right: 20px; top: 640px; display: flex; gap: 8px"><button class="sec glass" style="flex: 1">טיסת החזור</button><button class="sec glass" style="flex: 1">כרטיס סקי · 390 ₾</button></div>'
        return o
    out['Q07-TripPass.dc.html']=('השלמה ב · 7 · הטיול שלך: הכרטיס',b07())
    # ---- Q08: no trip yet. the pass is the form
    def b08():
        o=pic('hero','sunset','night')+veil(0,844,'var(--veil)')+topbar('','')
        o+='<div class="passw" style="top: 112px">%s</div>'%passcard(blank=True)
        cells=''.join('<span class="wd">%s</span>'%d for d in 'אבגדהוש')
        cells+=''.join('<span class="off">%d</span>'%d for d in(27,28,29,30,31))
        for d in range(1,32):
            cl='e' if d in(10,15) else ('rg sk' if 11<=d<=14 else '')
            cells+='<span class="%s">%d</span>'%(cl,d)
        o+='<div class="sheet glass" style="gap: 8px"><div class="grab"></div><div style="display: flex; align-items: center; gap: 8px"><span class="ttl">ינואר 2027</span><span style="margin-inline-start: auto; display: flex; gap: 4px"><button class="xb" aria-label="החודש הקודם">%s</button><button class="xb" aria-label="החודש הבא">%s</button></span></div>'%(CHEV,CHEV.replace('m9 6 6 6-6 6','m15 6-6 6 6 6'))
        o+='<div class="cal">%s</div>'%cells
        o+='<div style="display: flex; align-items: center; gap: 8px; font-size: 13.500px"><i style="width: 6px; height: 6px; border-radius: 50%; background: var(--acc); display: inline-block"></i><span>4 ימי סקי, 11 עד 14.1</span><span class="sub" style="margin-inline-start: auto">10.1 עד 15.1</span></div>'
        o+='<div style="display: flex; gap: 8px"><button class="pri">שמירה</button><button class="sec">בלי חזור</button></div></div>'
        return o
    out['Q08-TripEmpty.dc.html']=('השלמה ב · 8 · מתי טסים? הכרטיס הוא הטופס',b08())
    # ---- Q09: the ski pass
    ps={p['days']:p for p in PR['passes']};sel=4;day1=ps[1]['adult'];chk=datetime.date.fromisoformat(PR['checked'])
    def b09():
        INFO='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true" style="flex: none; margin-top: 1px"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 7.600v.200"/></svg>'
        o=pic('hero','morning','night')+veil(0,844,'var(--veil)')+topbar('כרטיס סקי')
        o+='<div style="position: absolute; left: 0; right: 0; top: 118px; display: flex; flex-direction: column; align-items: center; gap: 8px; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span class="cd">%d<span style="font-size: 40px; letter-spacing: 0; margin-inline-start: 6px">₾</span></span><b style="font-size: 17px">כרטיס %d ימים · מבוגר</b><span style="font-size: 14px"><span>יומי × %d: <bdi>%d ₾</bdi></span> · <b>חוסכים %d ₾ (%d%%)</b></span></div>'%(ps[sel]['adult'],sel,sel,day1*sel,day1*sel-ps[sel]['adult'],round((1-ps[sel]['adult']/(day1*sel))*100))
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div class="seg"><button class="on">מבוגר</button><button>ילד %d עד %d</button></div>'%tuple(PR['child'])
        o+='<div style="display: flex; align-items: center; gap: 12px"><button class="stp" aria-label="פחות יום סקי">−</button><div style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 1px"><b style="font-size: 19px">%d ימי סקי</b><span class="sub" style="font-size: 12.500px">לפי ״הטיול שלך״ · 11 עד 14.1</span></div><button class="stp" aria-label="עוד יום סקי">+</button></div>'%sel
        o+='<div style="display: flex; gap: 5px">%s</div>'%''.join('<div class="pc%s"><b>%d</b><span>%d ₾</span></div>'%(' on' if d==sel else '',d,ps[d]['adult']) for d in(1,2,3,4,5,6))
        o+='<div class="note">%s<span>כרטיס פיזי חובה, פיקדון <bdi>%d ₾</bdi> שמוחזר בקופה. QR לא מתקבל. מחיר לזר. לתושבי גאורגיה מחיר אחר.</span></div>'%(INFO,PR['card_deposit'])
        o+='<div class="rw" style="border-top: 1px solid var(--line); min-height: 44px"><span class="sub">סקי לילה: <bdi>%d ₾</bdi> לערב, כרטיס נפרד</span></div>'%PR['night']['adult']
        o+='<div class="rw" style="border-top: 1px solid var(--line); min-height: 44px"><span style="font-size: 14px; font-weight: 500">איפה קונים: בקופות האתרים</span><span class="r" style="color: var(--acc); font-weight: 700">SKIPASS</span></div>'
        o+='<div class="sub" style="font-size: 12px">נבדק %s, MTA · עונת %s · בלארי, כולל מע״מ</div></div>'%(dmy(chk),PR['season'])
        return o
    out['Q09-SkiPass.dc.html']=('השלמה ב · 9 · כרטיס סקי',b09())
    # ---- Q10: the countdown to the season, on the mountain
    def b10():
        o=pic('hero','dawn','night')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))
        o+='<div class="now glass"><span class="sd q"></span><span>עוד אין מידע על הרכבלים</span></div>'
        o+='<div style="position: absolute; left: 0; right: 0; top: 150px; display: flex; flex-direction: column; align-items: center; gap: 6px; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span class="cd">%d</span><b style="font-size: 19px">ימים לפתיחה</b></div>'%DAYS
        o+='<div class="sheet glass" style="gap: 4px"><div class="grab"></div>'
        o+='<div class="lr" style="min-height: 56px"><span style="display: flex; flex-direction: column; gap: 1px"><span style="font-size: 15.500px; font-weight: 700">פתיחה מתוכננת %s</span><span class="k">%s · לפי MTA · יכולה להשתנות עם השלג</span></span></div>'%(dmy(OPEN),WD[OPEN.weekday()])
        o+='<div class="lr" style="min-height: 52px"><span style="display: flex; flex-direction: column; gap: 1px"><span style="font-size: 14.500px; font-weight: 500">הטיול שלך</span><span class="k">10.1 עד 15.1 · 4 ימי סקי</span></span><span class="s"><span class="num" style="font-size: 25px">%d</span><span class="k">ימים לטיסה</span>%s</span></div>'%(dfly,EG)
        o+='<div class="lr" style="min-height: 52px"><span style="display: flex; flex-direction: column; gap: 1px"><span style="font-size: 14.500px; font-weight: 500">כרטיס סקי</span><span class="k">%d ימי סקי · מבוגר</span></span><span class="s"><span class="num" style="font-size: 25px">%d ₾</span>%s</span></div>'%(sel,ps[sel]['adult'],CHEV.replace('m9 6 6 6-6 6','m15 6-6 6 6 6'))
        o+='<div class="dock" style="position: static; height: 56px; margin-top: 8px; padding: 0; gap: 8px; background: none; border: 0; box-shadow: none"><div class="find" style="flex: 1">לאן?</div><button class="rb rec" aria-label="מקליטים"><span></span></button></div></div>'
        return o
    out['Q10-SeasonCountdown.dc.html']=('השלמה ב · 10 · לפני העונה: הספירה על ההר',b10())
    for fn,(title,body) in out.items():phone(fn,title,body)
    brief(len(opn))
    print('completion part 2 boards written to',OUT)
def brief(nopen):
    VW=P.load('wide');sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -150px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 2 · 21.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">סביב ההר</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">רכבלים, נקודת מפגש, הטיול והכרטיס, כרטיס הסקי והספירה לפתיחה</p></div></div>')%(sc,U('wide-dawn-cloud'))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">חמישה דברים שכבר קיימים באפליקציה, ועוד לא צוירו בשפה החדשה. כולם יושבים על ההר ולא בעמוד נפרד: הרכבלים הם שכבה על התמונה, נקודת המפגש היא דגל שנעוץ בה, והספירה לפתיחה היא המסך הראשון לפני העונה. המסירה: <span dir="ltr">design/handoff/2026-11-21-redesign-around/</span>.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוחות 1 עד 3</p><h2>רכבלים</h2><p class="p">הגלולה שבראש ההר נפתחת לרשימה. עם דיווח: פתוח או סגור, והסיבה. בלי דיווח: רק מה שבנתונים, ולא ״סגור״. רכבל אחד: כמה זמן, כמה גובה, מה יורד ממנו, ומה יש לאכול ליד התחנות.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 4 עד 6</p><h2>נקודת מפגש</h2><p class="p">נוגעים בתחנה, והדגל נעוץ בה עם השעה. בוחרים יום ושעה, ושולחים תמונה: המקום על ההר, השעה והיום. רק תחנות רכבל, כי רק להן יש מקום בנתונים.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 7 עד 10</p><h2>הטיול, הכרטיס והעונה</h2><p class="p">כרטיס העלייה למטוס נשאר הדבר היחיד מנייר באפליקציה. בלי טיול, הכרטיס שואל ״מתי טסים?״ ולוח שנה אחד עונה. כרטיס הסקי מחשב לפי ימי הסקי. לפני העונה, הספירה היא המסך.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>בלי דיווח אין ״סגור״:</b> הרשימה מראה סוג, זמן נסיעה וגובה, ושורה אחת ״אין דיווח״. לא חוזרים על זה בכל שורה.',
        '<b>רכבל סגור על ההר:</b> קו דהוי בלי קרונות, ותגית ״סגור״ בתחנה העליונה. פתוח: קו מלא עם קרונות.',
        '<b>״רק מה שפתוח בשבילי״</b> נשאר כמו היום: מסלול פתוח רק אם רכבל שמגיע לראשו פתוח.',
        '<b>נקודת מפגש:</b> שלוש הנקודות הקבועות נשארות כהצעה בלחיצה אחת. ״איך מגיעים״ לא חוזר (ירד בסבב 20).',
        '<b>הדגל</b> בצבע המבטא, עם השעה. הוא מופיע גם על ההר הרגיל, לכל מי שבקבוצה, עד שעה אחרי המפגש.',
        '<b>הכרטיס בלי הטיה:</b> נייר לבן, שני חריצים, קו ניקוב וספח. התלישה, ההחלפה לחזור, הצליל והרטט נשארים כמו היום.',
        '<b>הספירה בספח:</b> ימים לטיסה לפני הטיול, ״יום סקי 2 מתוך 4״ בזמן הטיול, וכלום אחריו. כמו היום.',
        '<b>כרטיס הסקי:</b> אותו חישוב שאושר בסבב 40. בלי קנייה דרכנו, בלי שקלים, עם מקור ותאריך.',
        '<b>לפני העונה:</b> המספר הגדול הוא ימים לפתיחה. ביום הפתיחה ואחריו, לפי הכללים של סבב 39.')+'</div>'
      '<div class="card" style="flex: 1"><h2>איפה כל דבר נמצא</h2>'+li(
        '<b>הגלולה שבראש ההר</b> (״%d מתוך 14 רכבלים פתוחים״): נגיעה פותחת את הרכבלים.'%nopen,
        '<b>נגיעה ברכבל על ההר</b>, או בשורה ברשימה: רכבל אחד.',
        '<b>״נפגשים כאן״</b> ברכבל, ״נפגשים״ בכפר (לוח K17) ובקבוצה: נקודת המפגש.',
        '<b>לפני העונה</b> ההר נפתח עם הספירה, ומתחתיה הטיול וכרטיס הסקי.',
        '<b>בעונה</b> אותן שורות (הטיול, כרטיס הסקי) נמצאות בגיליון של הכפר.',
        '<b>אנדרואיד:</b> לפי הכללים של חלק 1, בלי לוחות נפרדים: גיליון צמוד, משטח אטום, מחוות החזרה.')+'</div></div>'
      '<div class="card"><h2>ביושר</h2>'+li(
        '<b>מה אמיתי:</b> הרכבלים, התחנות, הגבהים, זמני הנסיעה, המסלולים שיורדים מכל רכבל, מספר מקומות האוכל, המחירים (MTA, נבדק 15.11.2026) ותאריך הפתיחה (20.12.2026), כולם מהנתונים שבריפו.',
        '<b>מה דוגמה, ומסומן:</b> מצב הרכבלים (היום אין דיווח בכלל), ״עודכן לפני 6 דק׳״, המפגש, והטיול. הטיסה בכרטיס מומצאת.',
        '<b>הספירה</b> מחושבת מ-21.11.2026: 29 ימים לפתיחה, 50 לטיסה לדוגמה.',
        '<b>״נבנה ב-2011״</b> ברכבל הוא שדה מהנתונים; לא לכל רכבל יש אותו.',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי של הלוחות, לא בתוך הקנבס.')+'</div>')
    wide('Q00-Brief.dc.html','סבב ההשלמה, חלק 2: סביב ההר',body,1280,1500)
if __name__=='__main__':
    build()
