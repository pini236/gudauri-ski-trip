#!/usr/bin/env python3
"""Completion round, part 1 (P00 to P12): what round 4 left out of the ski day, and the Android boards.
Standing, next to a lift, a long break, the end of "one descent", personal records; then the main day screens
with Android's components. Same language, same example day (day4.py), same rules. Design only.
Usage: python3 design/redesign/tools/gap_boards.py"""
import re,json
from peak_boards import *
import day4 as D
import peak_b4 as B4
from peak_b4 import EG,n0,ms,hms,main,signs,sofar,you,veil,fade,IC
CSS5="""
.H .st{font-size:12.5px;font-weight:700;color:var(--ink2);letter-spacing:.02em}
.H .kv4{display:grid;grid-template-columns:repeat(4,1fr);gap:6px}
.H .kv4 div{display:flex;flex-direction:column;gap:4px}
.H .kv4 b{font-size:27px;font-weight:300;direction:ltr;line-height:1;font-variant-numeric:tabular-nums;text-align:start;unicode-bidi:isolate}
.H .kv4 span{font-size:11.5px;color:var(--ink2);line-height:1.25}
.H .big2{font-size:64px;font-weight:300;line-height:.9;direction:ltr;font-variant-numeric:tabular-nums;letter-spacing:-.03em;unicode-bidi:isolate}
.H .lname{font-size:25px;font-weight:700;direction:ltr;unicode-bidi:isolate;line-height:1.1}
.H .why{display:flex;gap:9px;align-items:flex-start;font-size:13.5px;line-height:1.4;color:var(--ink)}
.H .why svg{flex:none;margin-top:2px}
.H .rc{display:flex;align-items:center;gap:12px;min-height:62px;padding:6px 0}
.H .rc+.rc{border-top:1px solid var(--line)}
.H .rc .v{width:104px;flex:none;display:flex;align-items:baseline;gap:4px;direction:ltr;justify-content:flex-end}
.H .rc .v b{font-size:29px;font-weight:300;line-height:1;font-variant-numeric:tabular-nums}
.H .rc .v i{font-style:normal;font-size:11.5px;color:var(--ink2);direction:rtl;unicode-bidi:isolate}
.H .rc .c{display:flex;flex-direction:column;gap:3px;min-width:0;flex:1}
.H .rc .h{font-size:14.5px;font-weight:500;line-height:1.25}
.H .rc .m{font-size:12px;color:var(--ink2);line-height:1.3;display:flex;align-items:center;gap:5px;flex-wrap:wrap}
.H .new{font-size:10.5px;font-weight:700;color:var(--onacc);background:var(--acc);border-radius:8px;padding:0 6px;height:18px;display:inline-flex;align-items:center;flex:none}
.H .xb{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;flex:none}
.H .and{position:absolute;inset:0;--surf:rgba(247,250,253,.92)}
.H .night .and{--surf:rgba(15,24,40,.92)}
.H .and .glass{background:var(--surf);-webkit-backdrop-filter:none;backdrop-filter:none;box-shadow:0 2px 8px rgba(10,25,45,.22)}
.H .and .sheet{left:0;right:0;bottom:0;border-radius:28px 28px 0 0;padding:10px 16px 34px;border-width:1px 0 0}
.H .and .hold{bottom:36px}
.H .and .gb{position:absolute;left:50%;bottom:9px;width:108px;height:4px;border-radius:2px;background:var(--ink);opacity:.5;transform:translateX(-50%)}
"""
MB.CSS=MB.CSS+CSS5
T0=D.FIN['start']
QUIET='<div style="position: absolute; inset: 0; opacity: .32">'+both(lambda m,l:P.runs_svg(VZ,m,dim=True))+'</div>'
STOP='<i style="width: 13px; height: 13px; border-radius: 3px; background: #E5352B; display: inline-block"></i>'
CHEV='<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 6 6 6-6 6"/></svg>'
XI='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"/></svg>'
LIFTI='<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 5.500 21 3"/><path d="M12 4.300V9"/><rect x="6.500" y="9" width="11" height="10" rx="2.600"/><path d="M6.500 14h11"/></svg>'
CUP='<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z"/><path d="M16 10.500h1.500a2.500 2.500 0 0 1 0 5H16"/><path d="M8 3.500v2M11.500 3.500v2"/></svg>'
CHK='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m5 12.500 4.500 4.500L19 7.500"/></svg>'
LOCK=IC['lock'].replace('width="26" height="26"','width="18" height="18"')
KIND={'gondola':'גונדולה','chair_lift':'רכבל כיסאות','drag_lift':'מעלית גרירה','platter':'מעלית גרירה','magic_carpet':'מסוע'}
def seg_at(i):return D.SEG[i]
def ridge(t,w=326,h=58,pad=4):
    """the ridge of the day so far: height against time, from the start until now (the future is not drawn)"""
    a,b=D.idx(T0,t);zlo,zhi=float(D.Z[a:b].min())-40,float(D.Z[a:b].max())+40
    X=lambda tt:pad+(tt-T0)/max(1,t-T0)*(w-2*pad-6);Y=lambda z:h-pad-(z-zlo)/(zhi-zlo)*(h-2*pad)
    def one(mode):
        st=20;ar='M%.1f %d '%(X(D.T[a]),h)+' '.join('L%.1f %.1f'%(X(D.T[i]),Y(D.Z[i])) for i in range(a,b,st))+' L%.1f %.1f L%.1f %d Z'%(X(t),Y(D.Z[b-1]),X(t),h)
        o='<path d="%s" fill="%s" stroke="none"/>'%(ar,'rgba(107,63,212,.10)' if mode=='day' else 'rgba(183,156,255,.14)');g='#40556A' if mode=='day' else '#A9B9CC'
        for sg in D.SEG:
            if sg['t0']>=t:break
            i0,i1=D.idx(sg['t0'],min(sg['t1'],t))
            if i1-i0<2:continue
            pts=[(X(D.T[i]),Y(D.Z[i])) for i in range(i0,i1,6)]+[(X(D.T[i1-1]),Y(D.Z[i1-1]))]
            if sg['type']=='descent':
                cur=None;rr=[]
                for i in range(i0,i1-6,6):
                    c=D.RAMP[mode][D.band(D.KMH[i])];p0=(X(D.T[i]),Y(D.Z[i]));p1=(X(D.T[i+6]),Y(D.Z[i+6]))
                    if cur and cur[0]==c:cur[1].append(p1)
                    else:cur=[c,[p0,p1]];rr.append(cur)
                for c,pp in rr:o+='<path d="%s" stroke="%s" stroke-width="2.4"/>'%(P.pathd(pp,.8),c)
            elif sg['type']=='lift':o+='<path d="%s" stroke="%s" stroke-width="1.3" stroke-dasharray="1 3.400"/>'%(P.pathd(pts,.8),g)
            else:o+='<path d="%s" stroke="%s" stroke-width="1" opacity=".55"/>'%(P.pathd(pts,.8),g)
        return '<svg class="%s" viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round" stroke-linejoin="round" style="direction: ltr">%s<circle cx="%.1f" cy="%.1f" r="4.500" fill="#2F7BFF" stroke="#fff" stroke-width="2"/></svg>'%('d' if mode=='day' else 'n',w,h,w,h,o,X(t),Y(D.Z[b-1]))
    return '<div style="height: %dpx; direction: ltr">%s%s</div>'%(h,one('day'),one('night'))
def daynums(t):
    n,drop,dist=sofar(t);mx=max([d['max_kmh'] for d in D.DESC if d['t1']<=t] or [0])
    return '<div class="kv4"><div><b>%d</b><span>ירידות</span></div><div><b>%s</b><span>מ׳ למטה</span></div><div><b>%.1f</b><span>ק״מ גלישה</span></div><div><b>%s</b><span>קמ״ש, מרבית</span></div></div>'%(n,n0(drop),dist/1000,('%.1f'%mx) if mx else '—')
def recp(t,label='מקליטים',extra=EG):
    return '<div class="recp glass"><span class="rdot"></span><span>%s</span><span class="tmr">%s</span><span style="margin-inline-start: auto">%s</span></div>'%(label,hms(t-T0),extra)
def mount(t,lift=None,lf=2,sc=1.7,at=300):
    """the mountain behind a live screen: a window on the whole mountain with the day so far, the last descent
    brighter, and the dot where the skier stands, placed above the sheet"""
    V=VH;w,h=390,844;x,y,_,_=D.where(V,t,lift=lf);last=[d for d in D.DESC if d['t1']<=t]
    cx=min(max(x,w/2/sc),V['W']-w/2/sc);cy=min(max(y+(h/2-at)/sc,h/2/sc),V['H']-h/2/sc)
    def L(V,mode):
        s='<g opacity=".32">'+P.runs_svg(V,mode,dim=True)+'</g>'
        if lift:s+=P.lifts_svg(V,mode,'morning' if mode=='day' else 'night',cabins=False,only=[lift])
        s+=D.rides(V,mode,until=t,w=.9)
        if last:s+=D.day(V,mode,until=last[-1]['t0']-1,lifts=False,w=1.5,alpha=.45)+D.track(V,last[-1]['t0'],last[-1]['t1'],mode,w=2.3)
        return s
    return B4.win('hero',w,h,cx,cy,sc,L,r=0,style=' position: absolute; left: 0; top: 0')+you(w/2+(x-cx)*sc,h/2+(y-cy)*sc)
def build():
    out={}
    # ---- P01: standing. the day so far
    s1=seg_at(24);t1=s1['t0']+20000
    def b01():
        o=mount(t1,sc=1.3,at=330)+recp(t1)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: baseline; gap: 8px"><span class="ttl" style="font-size: 19px">עומדים</span><span class="sub">היום עד עכשיו</span></div>'
        o+=daynums(t1)+ridge(t1)
        o+='<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">לראות את היום</button><button class="sec" style="display: flex; align-items: center; gap: 8px">%sסיום</button></div></div>'%STOP
        return o
    out['P01-Standing.dc.html']=('השלמה א · 1 · עומדים: היום עד עכשיו',b01())
    # ---- P02: standing next to a lift. what is known, and no more
    s2=seg_at(20);t2=s2['t0']+60000;lf=lift(D.SEG[21]['name'])
    def b02():
        o=mount(t2,lift=lf['name'])+recp(t2)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div>'
        o+='<div style="display: flex; align-items: center; gap: 12px"><span class="ic" style="background: var(--line)">%s</span><div style="display: flex; flex-direction: column; gap: 2px; min-width: 0"><span class="sub">ליד הרכבל</span><span class="lname">%s</span></div><div style="margin-inline-start: auto; display: flex; flex-direction: column; align-items: flex-end; gap: 2px"><span class="tmr" style="font-size: 23px; font-weight: 300">%s</span><span class="cap">עומדים כאן</span></div></div>'%(LIFTI,lf['name'],ms((t2-s2['t0'])/1000))
        o+='<div class="rw" style="border-top: 1px solid var(--line); min-height: 40px"><span class="sub">%s</span><span class="r" style="color: var(--ink); font-weight: 500">%s דק׳ למעלה · %s מ׳ למעלה</span></div>'%(KIND.get(lf['kind'],'רכבל'),lf['dur'],n0(float(lf['rise'])))
        o+='<div class="rw" style="min-height: 40px"><span class="sub">מצב הרכבל</span><span class="r">אין דיווח</span></div>'
        n,drop,dist=sofar(t2)
        o+='<div class="rw" style="min-height: 40px"><span class="sub">היום עד עכשיו</span><span class="r" style="color: var(--ink); font-weight: 500">%d ירידות · %s מ׳ למטה · %.1f ק״מ</span></div>'%(n,n0(drop),dist/1000)
        o+='<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">לראות את היום</button><button class="sec" style="display: flex; align-items: center; gap: 8px">%sסיום</button></div></div>'%STOP
        return o
    out['P02-NextToALift.dc.html']=('השלמה א · 2 · ליד הרכבל: מה שיודעים, ולא יותר',b02())
    # ---- P03: a long break. the screen rests too
    s3=seg_at(28);t3=s3['t0']+12*60000
    def b03():
        o=mount(t3,sc=1.7,at=470)+veil(0,844,'var(--veil)')+recp(t3)
        o+='<div style="position: absolute; left: 0; right: 0; top: 190px; display: flex; flex-direction: column; align-items: center; gap: 10px"><span class="ic glass" style="width: 56px; height: 56px">%s</span><div class="ttl" style="font-size: 25px; text-shadow: 0 0 10px var(--halo)">הפסקה</div><div class="big2" style="text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)">%d<span style="font-size: 17px; font-weight: 500; letter-spacing: 0; margin-inline-start: 6px">דק׳</span></div></div>'%(CUP,round((t3-s3['t0'])/60000))
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><span class="st">היום עד עכשיו</span>'+daynums(t3)+ridge(t3)
        o+='<div class="sub">ההקלטה ממשיכה. כשתזוזו, המסך חוזר לבד.</div>'
        o+='<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">לראות את היום</button><button class="sec" style="display: flex; align-items: center; gap: 8px">%sסיום</button></div></div>'%STOP
        return o
    out['P03-Break.dc.html']=('השלמה א · 3 · הפסקה: גם המסך נח',b03())
    # ---- P04: "one descent" ended by itself. the card
    d4=D.DESC[3];nxt=[s for s in D.LIFT if s['t0']>d4['t1']][0]
    def b04(reason=None):
        mx,my,_,_=D.ll(VZ,d4['max_at']['lat'],d4['max_at']['lon'])
        def L(mode,light):
            return svg(VZ,D.track(VZ,d4['t0'],d4['t1'],mode,w=4.6)+'<circle cx="%.1f" cy="%.1f" r="5" fill="#fff" stroke="#E0246E" stroke-width="2.4"/>'%(mx,my))
        o=pic('zoom')+QUIET+both(L)
        o+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>פה טסת · %.1f</b><i></i></div>'%(mx,my-6,d4['max_kmh'])
        o+='<div class="recp glass"><span style="color: var(--good); display: flex">%s</span><span>ירידה אחת · נשמרה</span><span style="margin-inline-start: auto; display: flex; align-items: center; gap: 6px">%s<button class="xb" aria-label="סגירה" style="margin-inline-end: -12px">%s</button></span></div>'%(CHK,EG,XI)
        fl,_=D.film(d4,64,150,'day',pad=8,sw=4);fn_,_=D.film(d4,64,150,'night',pad=8,sw=4)
        dur=(d4['t1']-d4['t0'])/1000;avg=d4['dist_m']/dur*3.6
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; gap: 12px; align-items: stretch"><div style="flex: 1; display: flex; flex-direction: column; gap: 10px; min-width: 0">%s'%signs(main(d4),big=True)
        o+='<div style="display: flex; align-items: baseline; gap: 8px"><span class="big2">%.1f</span><span class="cap">קמ״ש, מרבית<br>הערכה</span></div>'%d4['max_kmh']
        o+='<div class="nums" style="gap: 16px"><div><span class="num" style="font-size: 25px">%s</span><span class="cap">דקות</span></div><div><span class="num" style="font-size: 25px">%s</span><span class="cap">מ׳ למטה</span></div><div><span class="num" style="font-size: 25px">%.1f</span><span class="cap">ק״מ</span></div><div><span class="num" style="font-size: 25px">%.0f</span><span class="cap">קמ״ש, ממוצע</span></div></div></div>'%(ms(dur),n0(d4['drop_m']),d4['dist_m']/1000,avg)
        o+='<div style="width: 64px; flex: none"><svg class="d" viewBox="0 0 64 150" width="64" height="150" fill="none" stroke-linecap="round" stroke-linejoin="round">%s</svg><svg class="n" viewBox="0 0 64 150" width="64" height="150" fill="none" stroke-linecap="round" stroke-linejoin="round">%s</svg></div></div>'%(fl,fn_)
        o+='<div class="why" style="border-top: 1px solid var(--line); padding-top: 10px">%s<span>%s</span></div>'%(LIFTI.replace('width="26" height="26"','width="20" height="20"'),reason or 'המדידה נעצרה לבד כשעלית לרכבל <bdi>%s</bdi>.'%nxt['name'])
        o+='<div style="display: flex; gap: 8px"><button class="pri" style="display: flex; align-items: center; justify-content: center; gap: 10px"><span style="width: 12px; height: 12px; border-radius: 50%; background: var(--onacc)"></span>עוד ירידה</button><button class="sec">הסרט</button><button class="sec">שיתוף</button></div></div>'
        return o
    out['P04-OneDescentEnd.dc.html']=('השלמה א · 4 · ״ירידה אחת״ נגמרה לבד',b04())
    # ---- P05: personal records. only against yourself
    BEST=D.MOM['history']['best'];mxa=D.ST['max_at'];ld=max(D.DESC,key=lambda d:d['dist_m']);date=__import__('datetime').datetime.fromtimestamp(T0/1000,D.TZ);dshort='%d.%d'%(date.day,date.month)
    fast=[x for x in D.MOM['items'] if x['key']=='fastest'][0]['p'];stp=[x for x in D.MOM['items'] if x['key']=='steepest'][0]['p']
    def b05():
        px,py,_,_=D.ll(VH,mxa['lat'],mxa['lon'])
        def L(V,mode):
            return D.day(V,mode,w=2.2,alpha=.5,lifts=False)+D.track(V,ld['t0'],ld['t1'],mode,w=3.6)+'<circle cx="%.1f" cy="%.1f" r="5" fill="#fff" stroke="#E0246E" stroke-width="2.4"/>'%(px,py)
        V=VH;w,h,sc=358,214,1.0;cx,cy=px+10,py+62
        cx=min(max(cx,w/2/sc),V['W']-w/2/sc);cy=min(max(cy,h/2/sc),V['H']-h/2/sc)
        o='<div class="recp glass" style="background: transparent; border-color: transparent; box-shadow: none; -webkit-backdrop-filter: none; backdrop-filter: none; padding: 0 6px">%s<span class="ttl" style="font-size: 21px">השיאים שלך</span><span style="margin-inline-start: auto">%s</span></div>'%(CHEV,EG)
        o+='<div style="position: absolute; left: 16px; top: 106px">'+B4.win('hero',w,h,cx,cy,sc,L,day='noon',r=22)
        o+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>פה טסת · %.1f</b><i></i></div></div>'%(w/2+(px-cx)*sc,h/2+(py-6-cy)*sc,BEST['max_kmh'])
        def row(v,u,h_,m,new=True):
            return '<div class="rc"><div class="c"><span class="h" style="display: flex; align-items: center; gap: 7px">%s%s</span><span class="m">%s</span></div><div class="v"><i>%s</i><b>%s</b></div></div>'%(h_,'<span class="new">חדש</span>' if new else '',m,u,v)
        rows=row('%.1f'%BEST['max_kmh'],'קמ״ש','שיא מהירות אישי','%s<bdi>%s</bdi> · %s · הערכה'%(sign(fast['runs'][0]),fast['runs'][0],dshort))
        rows+=row('%.1f'%(BEST['longest_m']/1000),'ק״מ','הירידה הכי ארוכה שלך','מ-<bdi>%s</bdi> עד <bdi>%s</bdi> · %s'%(main(ld)[0],main(ld)[-1],dshort))
        rows+=row(n0(BEST['drop_m']),'מ׳','היום הכי גדול שלך','מטרים למטה ביום אחד · %s'%dshort)
        rows+=row('%d'%BEST['descents'],'','הכי הרבה ירידות ביום',dshort)
        rows+=row('%.1f'%(BEST['ski_dist_m']/1000),'ק״מ','הכי הרבה קילומטרים','על השלג ביום אחד · %s'%dshort)
        rows+=row('%d°'%BEST['steep_deg'],'','המסלול הכי תלול שלך','%s<bdi>%s</bdi> · הקטע התלול שלו'%(sign(stp['run']),stp['run']),new=False)
        o+='<div style="position: absolute; left: 16px; right: 16px; top: 328px">%s</div>'%rows
        o+='<div style="position: absolute; left: 16px; right: 16px; bottom: 18px; display: flex; align-items: center; gap: 8px; color: var(--ink2); font-size: 12.5px">%s<span>רק מול עצמך. נשאר בטלפון, ולא נשלח לאף אחד.</span></div>'%LOCK
        return o
    out['P05-Records.dc.html']=('השלמה א · 5 · השיאים שלך',b05())
    # ---- P06: no records yet
    def b06():
        o='<div class="recp glass" style="background: transparent; border-color: transparent; box-shadow: none; -webkit-backdrop-filter: none; backdrop-filter: none; padding: 0 6px">%s<span class="ttl" style="font-size: 21px">השיאים שלך</span></div>'%CHEV
        o+='<div style="position: absolute; left: 16px; top: 106px; opacity: .55">'+B4.win('hero',358,214,195,330,1.0,lambda V,m:'',day='noon',r=22)+'</div>'
        o+='<div style="position: absolute; left: 32px; right: 32px; top: 372px; display: flex; flex-direction: column; align-items: center; gap: 10px; text-align: center"><div class="ttl">עוד אין שיאים</div><div class="sub" style="font-size: 14.500px">אחרי היום הראשון שמקליטים הם מופיעים כאן, עם המקום על ההר.</div></div>'
        def ghost(h_):
            return '<div class="rc" style="opacity: .5"><div class="c"><span class="h">%s</span></div><div class="v"><b>—</b></div></div>'%h_
        o+='<div style="position: absolute; left: 16px; right: 16px; top: 500px">%s</div>'%''.join(ghost(x) for x in('שיא מהירות אישי','הירידה הכי ארוכה שלך','היום הכי גדול שלך'))
        o+='<div style="position: absolute; left: 16px; right: 16px; bottom: 18px; display: flex; flex-direction: column; gap: 10px"><div class="sub" style="text-align: center">היום לדוגמה לא נספר בשיאים.</div><button class="pri" style="display: flex; align-items: center; justify-content: center; gap: 10px; flex: none"><span style="width: 12px; height: 12px; border-radius: 50%; background: var(--onacc)"></span>מקליטים</button></div>'
        return o
    out['P06-RecordsEmpty.dc.html']=('השלמה א · 6 · עוד אין שיאים',b06())
    for fn,(title,body) in out.items():phone(fn,title,body)
    # ---- P07 to P11: the same screens with Android's components
    def android(src,fn,title):
        s=open(os.path.join(OUT,src),encoding='utf8').read();m=re.search(r'<div class="scr" dir="rtl">\n(.*)\n</div>\n</div>\n</div>\n</x-dc>',s,re.S)
        phone(fn,title,'<div class="and">'+m.group(1)+'<span class="gb"></span></div>')
    android('L01-Start.dc.html','P07-Android-Start.dc.html','השלמה א · 7 · אנדרואיד: מתחילים')
    android('L03-Descending.dc.html','P08-Android-Descending.dc.html','השלמה א · 8 · אנדרואיד: בירידה')
    android('L04-OnTheLift.dc.html','P09-Android-OnTheLift.dc.html','השלמה א · 9 · אנדרואיד: על הרכבל')
    android('L06-DayPainted.dc.html','P10-Android-DayPainted.dc.html','השלמה א · 10 · אנדרואיד: סוף היום')
    android('P04-OneDescentEnd.dc.html','P11-Android-OneDescentEnd.dc.html','השלמה א · 11 · אנדרואיד: ״ירידה אחת״ נגמרה')
    android('P05-Records.dc.html','P12-Android-Records.dc.html','השלמה א · 12 · אנדרואיד: השיאים שלך')
    brief()
    print('completion part 1 boards written to',OUT)
def li(*a):
    return '<ul style="margin: 0; padding-inline-start: 20px; display: flex; flex-direction: column; gap: 7px; font-size: 15px; line-height: 1.5">'+''.join('<li>%s</li>'%x for x in a)+'</ul>'
def brief():
    VW=P.load('wide');sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -190px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"><div style="position: absolute; left: 0; top: 0">%s</div></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 1 · 21.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">מה שהיה חסר ביום הגלישה</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">עמידה ותור, ״ירידה אחת״, השיאים, ואנדרואיד</p></div></div>')%(sc,U('wide-dawn-cloud'),svg(VW,D.day(VW,'night',w=3.4)))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">סבב 4 צייר את יום הגלישה, ואושר. נשארו בו ארבעה חורים: מה רואים כשעומדים, איך נגמרת ״ירידה אחת״, איפה השיאים, ואיך כל זה נראה באנדרואיד. הלוחות כאן סוגרים אותם באותה שפה ובאותם כללים, ואיתם מסירה שאפשר לבנות ממנה: <span dir="ltr">design/handoff/2026-11-21-redesign-day-gaps/</span>.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוחות 1 עד 3</p><h2>כשעומדים</h2><p class="p">בירידה: מספר אחד. על הרכבל: הירידה שהרגע עשית. וכשעומדים: היום עד עכשיו. ליד רכבל המסך אומר ליד איזה רכבל, כמה זמן עומדים, ומה שבנתונים עליו. הוא לא אומר ״תור״, כי את זה יודעים רק אחרי שעולים.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוח 4</p><h2>״ירידה אחת״ נגמרת לבד</h2><p class="p">המדידה נעצרת כשעולים לרכבל, כשעומדים ליד תחנה, או אחרי דקה וחצי בלי תנועה. הכרטיס אומר למה היא נעצרה, מראה את הירידה כסרט קטן, ומציע עוד ירידה בנגיעה.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 5 ו-6</p><h2>השיאים שלך</h2><p class="p">שישה שיאים, כולם מול עצמך בלבד, ועל ההר המקום שבו נמדדה המהירות המרבית והירידה הכי ארוכה. בלי טבלה, בלי דירוג ובלי אנשים אחרים.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>עמידה קצרה באמצע ירידה</b> (עד 20 שניות) לא מחליפה מסך: המספר יורד לאפס והשם נשאר. רק אחרי 20 שניות עולה ״עומדים״.',
        '<b>״ליד הרכבל״</b> מופיע עד 80 מ׳ מתחנת עלייה (אותו מרחק שהמנתח משתמש בו לתור). השעון ״עומדים כאן״ סופר מרגע העמידה.',
        '<b>״חיכית 3 דק׳״</b> מופיע רק על מסך הרכבל, אחרי שעלית: שם זה כבר ידוע. מעל דקה בלבד.',
        '<b>״הפסקה״</b> מחליפה את ״עומדים״ אחרי 5 דקות. היא מסך רגוע: המספר היחיד הוא כמה זמן. ב-55 דקות מופיעה האזהרה שכבר אושרה בסבב 4.',
        '<b>מצב הרכבל</b> מוצג רק כשיש דיווח עדכני. אחרת ״אין דיווח״, כמו בכל האפליקציה.',
        '<b>״סיום״ מחוץ לירידה</b> הוא כפתור רגיל שמחזיקים שנייה, כמו ״החזקה לסיום״. עם קורא מסך: נגיעה ואישור.',
        '<b>שיא בלי היום שלו:</b> אם מוחקים יום, השיא נשאר, בלי תאריך ובלי מקום על ההר.',
        '<b>״חדש״</b> מסומן על שיא שנקבע ביום האחרון שהוקלט, עד היום הבא.',
        '<b>היום לדוגמה</b> לא נספר בשיאים, כמו שכבר נקבע.')+'</div>'
      '<div class="card" style="flex: 1"><h2>אנדרואיד: לוחות 7 עד 12</h2><p class="p" style="margin-bottom: 10px">אותו מבנה, אותם טקסטים ואותם צבעים. רק רכיבי המערכת שונים:</p>'+li(
        '<b>הגיליון צמוד לתחתית</b>, ברוחב מלא, עם פינות עליונות של 28. באייפון הוא צף, עם מרווח של 8 מכל צד.',
        '<b>בלי טשטוש מאחור:</b> משטח אטום ב-92 אחוז, עם צל קצר. באייפון זכוכית מטושטשת.',
        '<b>מחוות החזרה</b> של המערכת סוגרת את השכבה העליונה: גיליון, כרטיס, ואז המסך.',
        '<b>״החזקה לסיום״</b> יושב מעל פס המחוות, 36 מהתחתית.',
        '<b>מחוץ לאפליקציה</b> לא השתנה: ההתראה הקבועה עם ״סיום״, כמו בלוח 5 של סבב 4.')+
      '<p class="cap" style="margin-top: 12px">הלוחות נוצרו מאותם מסכים של האייפון, עם החלפת הרכיבים בלבד. פס המחוות מצויר כדי להראות את המרווח; אין פס מצב מצויר.</p></div></div>'
      '<div class="card"><h2>ביושר</h2>'+li(
        '<b>היום לדוגמה</b> הוא היום הסינתטי של כלי הבדיקה, כמו בסבב 4. הקווים, הרכבלים והקרקע אמיתיים; השעות והמהירויות מומצאות. כל לוח מסומן ״דוגמה״.',
        '<b>״ירידה אחת״ בלוח 4</b> מוצגת על ירידה מתוך היום לדוגמה (<bdi>Tatra 2</bdi>), לא על הקלטה של מצב ״ירידה אחת״.',
        '<b>השיאים בלוח 5</b> הם של יום אחד, ולכן כולם ״חדש״ ובאותו תאריך.',
        '<b>״אין דיווח״ על הרכבל</b> הוא המצב האמיתי היום: מצב הרכבלים מתחיל להגיע רק בעונה.',
        '<b>הנוסחים החדשים</b> הם הצעה, ברשימה במסירה, לסשן התוכן בארבע השפות.',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי של הלוחות, לא בתוך הקנבס.')+'</div>')
    wide('P00-Brief.dc.html','סבב ההשלמה, חלק 1: יום הגלישה ואנדרואיד',body,1280,1460)
if __name__=='__main__':
    build()
