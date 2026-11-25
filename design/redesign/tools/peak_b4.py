"""Round 4, part 1: the ski day while it happens (L01 to L05) and the day painted on the mountain (L06, L07).
The example day: day4.py. Design only."""
from peak_boards import *
import day4 as D
EG='<span class="eg">דוגמה</span>'
CSS4="""
.H .recp{position:absolute;top:52px;left:12px;right:12px;height:44px;border-radius:22px;display:flex;align-items:center;gap:9px;padding:0 14px;box-sizing:border-box;font-size:14px;font-weight:500;white-space:nowrap}
.H .rdot{width:10px;height:10px;border-radius:50%;background:#E5352B;box-shadow:0 0 0 4px rgba(229,53,43,.22);flex:none}
.H .tmr{direction:ltr;font-variant-numeric:tabular-nums;font-weight:700;unicode-bidi:isolate}
.H .huge{font-size:132px;font-weight:300;line-height:.86;direction:ltr;font-variant-numeric:tabular-nums;letter-spacing:-.04em;width:240px;text-align:center;text-shadow:0 0 22px var(--halo),0 0 6px var(--halo)}
.H .unit{font-size:15px;font-weight:500;color:var(--ink2);text-shadow:0 0 6px var(--halo)}
.H .hold{position:absolute;left:16px;right:16px;bottom:18px;height:56px;border-radius:28px;display:flex;align-items:center;justify-content:center;gap:10px;font-size:15.5px;font-weight:700}
.H .hold i{width:15px;height:15px;border-radius:4px;background:#E5352B;display:inline-block}
.H .kv{display:grid;grid-template-columns:repeat(4,1fr);gap:6px}
.H .kv div{display:flex;flex-direction:column;gap:3px}
.H .kv b{font-size:23px;font-weight:300;direction:ltr;line-height:1;font-variant-numeric:tabular-nums;text-align:start;unicode-bidi:isolate}
.H .kv span{font-size:11.5px;color:var(--ink2)}
.H .spd{display:flex;direction:ltr;align-items:center;gap:6px;font-size:11px;color:var(--ink2);white-space:nowrap}
.H .spd i{display:block;width:70px;height:5px;border-radius:3px;background:linear-gradient(90deg,#CFC2F4,#A98BEE,#7C4FE0,#B02FC4,#E0246E)}
.H .night .spd i{background:linear-gradient(90deg,#5B46A8,#8A6BE6,#B99CFF,#F3A6F0,#FFF2FA)}
.H .win{position:relative;overflow:hidden;flex:none;background:#DCE6F2}
.H .win .in{position:absolute;left:0;top:0;transform-origin:0 0}
.H .win img{position:absolute;left:0;top:0;max-width:none;display:block}
.H .win svg{position:absolute;left:0;top:0;display:block}
.H .sgr{display:flex;align-items:center;gap:5px;flex-wrap:wrap}
.H .sgr b{font-size:15px;font-weight:700;direction:ltr}
.H .sgr em{font-style:normal;font-size:12px;color:var(--ink2)}
.H .ic{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;flex:none}
.H .l3{display:flex;gap:12px;align-items:flex-start;font-size:14.5px;line-height:1.4}
.H .l3 svg{flex:none;margin-top:1px}
.H .tl{display:flex;gap:10px;align-items:flex-start;min-height:44px;padding:7px 0}
.H .tl+.tl{border-top:1px solid var(--line)}
.H .tl .t{width:40px;flex:none;font-size:12.5px;direction:ltr;text-align:start;color:var(--ink2);font-variant-numeric:tabular-nums;padding-top:2px}
.H .tl .c{display:flex;flex-direction:column;gap:3px;min-width:0}
.H .tl .m{font-size:12.5px;color:var(--ink2);line-height:1.35}
.H .tl .h{font-size:14px;font-weight:500;line-height:1.3}
.H .bub{position:absolute;transform:translate(-50%,-100%);padding:5px 10px;border-radius:12px;background:var(--ink);color:var(--bg);font-size:11.5px;font-weight:700;white-space:nowrap}
.H .ask{display:flex;gap:8px}.H .ask button{flex:1;height:44px;border-radius:22px;border:1px solid var(--line);font-size:14px;font-weight:500}
"""
MB.CSS=MB.CSS+CSS4
n0=lambda v:format(int(round(v)),',')
ms=lambda s:'%d:%02d'%(int(s)//60,int(s)%60)
def hms(ms_):
    s=int(ms_//1000);return '%d:%02d:%02d'%(s//3600,s%3600//60,s%60) if s>=3600 else '%d:%02d'%(s//60,s%60)
def main(d):
    """the runs that name a descent: the stretches that belong to one run, in order"""
    o=[]
    for l in d['legs']:
        if l['kind']=='run' and len(l['runs'])==1 and l['runs'][0] not in o:o.append(l['runs'][0])
    return o or D.names(d)
def signs(rs,sep='›',big=False):
    return '<div class="sgr">'+(' <em>%s</em> '%sep).join('%s<b>%s</b>'%(sign(r,big),r) for r in rs)+'</div>'
def leg_at(d,t):
    for l in d['legs']:
        if l['t0']<=t<=l['t1']+999:return l
    return d['legs'][-1]
def sofar(t):
    ds=[d for d in D.DESC if d['t1']<=t];return len(ds),sum(d['drop_m'] for d in ds),sum(d['dist_m'] for d in ds)
def win(view,w,h,cx,cy,sc,fn,day='morning',night='night',r=14,style=''):
    """a window on the mountain for a phone board: fn(V,mode) -> svg inner; day and night"""
    V=VIEW[view];cx=min(max(cx,w/2/sc),V['W']-w/2/sc);cy=min(max(cy,h/2/sc),V['H']-h/2/sc)
    one=lambda cl,li,m:'<div class="%s"><img alt="" src="%s" style="width: %dpx; height: %dpx">%s</div>'%(cl,U(view+'-'+li),V['W'],V['H'],svg(V,fn(V,m)))
    return '<div class="win" style="width: %dpx; height: %dpx; border-radius: %dpx;%s"><div class="in" style="width: %dpx; height: %dpx; transform: translate(%.0fpx,%.0fpx) scale(%g)">%s%s</div></div>'%(w,h,r,style,V['W'],V['H'],w/2-cx*sc,h/2-cy*sc,sc,one('d',day,'day'),one('n',night,'night'))
def you(x,y):
    return '<div class="you" style="left: %.0fpx; top: %.0fpx"></div>'%(x,y)
def veil(top,h,a='var(--veil)'):
    return '<div style="position: absolute; left: 0; right: 0; top: %dpx; height: %dpx; background: %s"></div>'%(top,h,a)
def fade(top,h):
    return '<div style="position: absolute; left: 0; right: 0; top: %dpx; height: %dpx; background: linear-gradient(180deg, transparent, var(--bg))"></div>'%(top,h)
SEG2=lambda on:'<div class="seg" style="position: absolute; left: 16px; right: 16px; top: 106px"><button class="%s">כל היום</button><button class="%s">ירידה אחת</button></div>'%(('on','') if on==0 else ('','on'))
IC={'pocket':'<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="7" y="2.5" width="10" height="19" rx="2.4"/><path d="M4 10c0 6 3.4 9 8 9s8-3 8-9"/></svg>',
    'lock':'<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="10.5" width="14" height="10" rx="2.4"/><path d="M8.5 10.5V7.5a3.5 3.5 0 0 1 7 0v3"/></svg>',
    'pin':'<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s6.5-6 6.5-11A6.5 6.5 0 0 0 5.5 10c0 5 6.5 11 6.5 11Z"/><circle cx="12" cy="10" r="2.3"/></svg>',
    'sat':'<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M5 19a9 9 0 0 1 9-9"/><path d="M5 14a4.5 4.5 0 0 1 4.500-4.5" opacity=".55"/><circle cx="5.500" cy="18.500" r="1.4" fill="currentColor"/><path d="m14 4 6 6-3 3-6-6z"/></svg>'}

def build():
    T0=D.FIN['start']
    QUIET='<div style="position: absolute; inset: 0; opacity: .32">'+both(lambda m,l:P.runs_svg(VZ,m,dim=True))+'</div>'
    # ---- L01: the first time. one tap on the record button of the mountain
    def b01():
        o=pic('hero')+base('hero',runs='line')+veil(0,844,'var(--veil)')
        o+='<div class="sheet glass" style="gap: 14px; padding-bottom: 18px"><div class="grab"></div>'
        o+='<div><div class="ttl">מעקב גלישה</div></div>'
        o+='<div class="l3">%s<span>מזהה לבד רכבל, ירידה והפסקה, בשם המסלול.</span></div>'%IC['pocket']
        o+='<div class="l3">%s<span>הכל נשאר בטלפון. שום מיקום לא נשלח לשום מקום.</span></div>'%IC['lock']
        o+='<div class="l3">%s<span>לטלפון מספיקה ההרשאה ״בזמן השימוש באפליקציה״, עם מיקום מדויק.</span></div>'%IC['pin']
        o+='<div class="seg"><button class="on">כל היום</button><button>ירידה אחת</button></div>'
        o+='<div style="display: flex; gap: 8px"><button class="pri" style="display: flex; align-items: center; justify-content: center; gap: 10px"><span style="width: 12px; height: 12px; border-radius: 50%; background: var(--onacc)"></span>מתחילים</button><button class="sec">לא עכשיו</button></div></div>'
        return o
    phone('L01-Start.dc.html','סבב 4 · 1 · מתחילים בנגיעה אחת',b01())
    # ---- L02: before the first fix. the clock already runs, the number does not lie
    def b02():
        o=pic('hero')+veil(0,844,'var(--veil)')
        o+='<div class="recp glass"><span class="rdot"></span><span>מקליטים</span><span class="tmr">0:14</span></div>'+SEG2(0)
        o+='<div style="position: absolute; left: 0; right: 0; top: 300px; display: flex; flex-direction: column; align-items: center; gap: 6px"><div class="huge" style="font-weight: 200; font-size: 104px">—</div><div class="unit">קמ״ש</div></div>'
        o+='<div class="card glass" style="position: absolute; left: 16px; right: 16px; top: 520px; gap: 8px"><div style="display: flex; align-items: center; gap: 10px">%s<b style="font-size: 16px">מחפשים לוויינים</b><span style="margin-inline-start: auto; display: flex; gap: 4px">%s</span></div><div class="sub">הזמן כבר נספר מהנגיעה. המהירות והקו מתחילים כשיש מיקום מדויק, ולא רגע לפני.</div></div>'%(IC['sat'],''.join('<i style="width: 7px; height: %dpx; border-radius: 2px; background: var(--ink); opacity: %s; align-self: flex-end"></i>'%(h,op) for h,op in((8,'1'),(12,'1'),(16,'.25'),(20,'.25'))))
        o+='<div class="hold glass"><i></i>החזקה לסיום</div>'
        return o
    phone('L02-Searching.dc.html','סבב 4 · 2 · מחפשים מיקום',b02())
    # ---- L03: on the way down. one number, one name, one button
    d4=D.DESC[3];t=d4['t0']+100000;lg=leg_at(d4,t);i=int(np.searchsorted(D.T,t));kmh=int(round(float(D.KMH[i])))
    def b03(en=False):
        x,y,_,_=D.where(VZ,t)
        def L(mode,light):
            return svg(VZ,D.rides(VZ,mode,until=t)+D.day(VZ,mode,until=d4['t0']-1,lifts=False,w=2.6,alpha=.55)+D.track(VZ,d4['t0'],t,mode,w=4.6))
        o=pic('zoom')+QUIET+both(L)+you(x,y)+fade(430,130)+veil(560,284,'var(--bg)')
        o+='<div class="recp glass"><span class="rdot"></span><span>%s</span><span class="tmr">%s</span><span style="margin-inline-start: auto">%s</span></div>'%('Recording' if en else 'מקליטים',hms(t-T0),'<span class="eg">%s</span>'%('Example' if en else 'דוגמה'))
        o+='<div style="position: absolute; left: 0; right: 0; top: 500px; display: flex; flex-direction: column; align-items: center; gap: 8px"><div class="huge">%d</div><div class="unit">%s</div></div>'%(kmh,'km/h' if en else 'קמ״ש')
        o+='<div style="position: absolute; left: 0; right: 0; top: 672px; display: flex; justify-content: center; align-items: center; gap: 8px">%s<b style="font-size: 19px; direction: ltr">%s</b><span class="sub">%s</span></div>'%(sign(lg['runs'][0],True),' · '.join(lg['runs']),'probably' if en else 'כנראה')
        o+='<div class="hold glass"><i></i>%s</div>'%('Hold to finish' if en else 'החזקה לסיום')
        return o
    phone('L03-Descending.dc.html','סבב 4 · 3 · בירידה: מספר אחד, שם אחד',b03())
    phone('L14-Descending-EN.dc.html','Round 4 · 14 · On the way down (English)',b03(True),lang='en')
    # ---- L04: on the lift. the run you have just done
    lf=[s for s in D.LIFT if s['t0']>d4['t1']][0];tl=lf['t0']+180000;dur=float(lift(lf['name'])['dur']);left=max(1,round(dur-(tl-lf['t0'])/60000))
    n,drop,dist=sofar(tl)
    def b04():
        x,y,_,_=D.where(VZ,tl,lift=8);mx,my,_,_=D.ll(VZ,d4['max_at']['lat'],d4['max_at']['lon'])
        def L(mode,light):
            return svg(VZ,D.rides(VZ,mode,until=tl)+D.day(VZ,mode,until=d4['t0']-1,lifts=False,w=2.4,alpha=.4)+D.track(VZ,d4['t0'],d4['t1'],mode,w=4.6)+'<circle cx="%.1f" cy="%.1f" r="5" fill="#fff" stroke="#E0246E" stroke-width="2.4"/>'%(mx,my))
        o=pic('zoom')+QUIET+both(lambda m,l:P.lifts_svg(VZ,m,l,cabins=False,only=[lf['name']]))+both(L)+you(x,y)
        o+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>פה טסת · %s</b><i></i></div>'%(mx,my-6,('%.1f'%d4['max_kmh']))
        o+='<div class="recp glass"><span class="rdot"></span><span>על הרכבל <b style="direction: ltr; unicode-bidi: isolate">%s</b> · עוד כ-%d דק׳ למעלה</span><span style="margin-inline-start: auto">%s</span></div>'%(lf['name'],left,EG)
        fl,at=D.film(d4,64,150,'day',pad=8,sw=4);fn_,_=D.film(d4,64,150,'night',pad=8,sw=4)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; gap: 12px; align-items: stretch"><div style="flex: 1; display: flex; flex-direction: column; gap: 10px; min-width: 0"><div class="sub">הירידה שהרגע עשית</div>%s<div class="nums" style="gap: 16px"><div><span class="num">%s</span><span class="cap">קמ״ש, מרבית</span></div><div><span class="num">%s</span><span class="cap">דקות</span></div><div><span class="num">%s</span><span class="cap">מ׳ למטה</span></div></div></div>'%(signs(main(d4),big=True),'%.1f'%d4['max_kmh'],ms((d4['t1']-d4['t0'])/1000),n0(d4['drop_m']))
        o+='<div style="width: 64px; flex: none"><svg class="d" viewBox="0 0 64 150" width="64" height="150" fill="none" stroke-linecap="round" stroke-linejoin="round">%s</svg><svg class="n" viewBox="0 0 64 150" width="64" height="150" fill="none" stroke-linecap="round" stroke-linejoin="round">%s</svg></div></div>'%(fl,fn_)
        o+='<div class="rw" style="border-top: 1px solid var(--line); padding-top: 8px"><span class="sub">היום עד עכשיו</span><span class="r" style="color: var(--ink); font-weight: 500">%d ירידות · %s מ׳ למטה · %.1f ק״מ</span></div>'%(n,n0(drop),dist/1000)
        o+='<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">לראות את הירידה</button><button class="sec" style="display: flex; align-items: center; gap: 8px"><i style="width: 13px; height: 13px; border-radius: 3px; background: #E5352B; display: inline-block"></i>סיום</button></div></div>'
        return o
    phone('L04-OnTheLift.dc.html','סבב 4 · 4 · על הרכבל: הירידה שהרגע עשית',b04())
    # ---- L06: the end of the day. the mountain keeps the day
    mx=D.ST['max_at'];fall=D.FIN['falls'][0];date=__import__('datetime').datetime.fromtimestamp(T0/1000,D.TZ);dstr='%d.%d.%d'%(date.day,date.month,date.year)
    def b06():
        px,py,pv,_=D.ll(VH,mx['lat'],mx['lon']);fx,fy,fv,_=D.ll(VH,fall['lat'],fall['lon'])
        kd=[d for d in D.DESC if 'Kobi' in D.names(d)][0];kx,ky,_,_=D.where(VH,kd['t0'])
        def L(mode,light):
            s=D.day(VH,mode,w=3.2)
            s+='<circle cx="%.1f" cy="%.1f" r="5" fill="#fff" stroke="#E0246E" stroke-width="2.4"/>'%(px,py)
            s+='<g transform="translate(%.1f %.1f)" stroke="%s" stroke-width="1.5"><circle r="7.5" fill="%s" stroke-width="1.2"/><path d="M0 -4.500V4.500M-3.900 -2.250 3.900 2.250M3.900 -2.250-3.900 2.250"/></g>'%(fx,fy,'#22303F' if mode=='day' else '#EAF1F8','rgba(255,255,255,.92)' if mode=='day' else 'rgba(10,18,34,.9)')
            return svg(VH,s)
        o=pic('hero','noon','night')+both(L)
        o+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>פה טסת · %.1f</b><i></i></div>'%(px,py-6,D.ST['max_kmh'])
        o+=tag(96,ky-4,'ועוד ירידה בצד של Kobi ›',style=' direction: rtl')
        o+='<div class="recp glass"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></svg><span>היום שלך · <span class="tmr" style="font-weight: 500">%s</span> · <span class="tmr" style="font-weight: 500">%s–%s</span></span><span style="margin-inline-start: auto">%s</span></div>'%(dstr,D.hm(T0),D.hm(D.FIN['end']),EG)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: flex-end; gap: 10px"><div class="ttl" style="font-size: 25px; white-space: nowrap">יום של פעם ראשונה</div></div>'
        o+='<div class="nums" style="gap: 22px"><div><span class="num">%d</span><span class="cap">ירידות</span></div><div><span class="num">%s</span><span class="cap">מ׳ למטה</span></div><div><span class="num">%.1f</span><span class="cap">ק״מ גלישה</span></div><span class="spd" style="margin-inline-start: auto; align-self: flex-end; padding-bottom: 4px"><span>לאט</span><i style="width: 48px"></i><span>מהר</span></span></div>'%(D.ST['descents'],n0(D.ST['drop_m']),D.ST['ski_dist_m']/1000)
        o+='<div style="display: flex; gap: 8px"><button class="pri" style="display: flex; align-items: center; justify-content: center; gap: 9px"><svg width="16" height="16" viewBox="0 0 16 16"><path d="M4 2.500v11l9-5.500z" fill="currentColor"/></svg>היום בדקה</button><button class="sec">הסיפור</button><button class="sec">שיתוף</button></div></div>'
        return o
    phone('L06-DayPainted.dc.html','סבב 4 · 6 · סוף היום: ההר זוכר את היום שלך',b06())
    # ---- L07: the ridge of the day and the timeline
    def b07():
        W,Hh=358,150;tm=mx['t']
        def rib(mode):
            r,X,Y=D.ribbon(W,Hh,mode);ax=''
            for hh in(10,11,12):
                tt=T0+int((hh-9.5)*3600000);ax+='<path d="M%.1f 4V%d" stroke="%s" stroke-width="1" stroke-dasharray="2 4"/>'%(X(tt),Hh,'rgba(14,34,51,.22)' if mode=='day' else 'rgba(234,241,248,.25)')
            i=int(np.searchsorted(D.T,tm));cx,cy=X(tm),Y(D.Z[i])
            return '<svg viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round" stroke-linejoin="round" style="display: block; direction: ltr">%s%s<path d="M%.1f 0V%d" stroke="%s" stroke-width="1.6"/><circle cx="%.1f" cy="%.1f" r="5" fill="#fff" stroke="#E0246E" stroke-width="2.4"/></svg>'%(W,Hh,W,Hh,ax,r,cx,Hh,'#0E2233' if mode=='day' else '#EAF1F8',cx,cy),X
        rd,X=rib('day');rn,_=rib('night')
        o='<div class="recp glass" style="top: 52px"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></svg><span>הרכס של היום · <span class="tmr" style="font-weight: 500">%s</span></span><span style="margin-inline-start: auto">%s</span></div>'%(dstr,EG)
        o+='<div style="position: absolute; left: 16px; top: 136px; width: %dpx"><div class="d">%s</div><div class="n">%s</div>'%(W,rd,rn)
        o+='<div style="display: flex; justify-content: space-between; direction: ltr; font-size: 11px; color: var(--ink2); padding: 4px 0 0">%s</div></div>'%''.join('<span style="position: absolute; left: %.0fpx; transform: translateX(-50%%)">%d:00</span>'%(X(T0+int((hh-9.5)*3600000)),hh) for hh in(10,11,12))
        o+='<div class="bub" style="left: %.0fpx; top: 132px">%s · %.1f קמ״ש</div>'%(min(max(16+X(tm),70),320),D.hm(tm),D.ST['max_kmh'])
        o+='<div class="spd" style="position: absolute; left: 16px; top: 312px"><span>לאט</span><i></i><span>מהר</span><span style="margin-inline-start: 10px; direction: rtl">· · · רכבל</span></div>'
        lb=[q for q in D.SEG if q['type']=='stop' and q.get('kind')=='long'][0]
        o+='<div style="position: absolute; left: %.0fpx; top: 152px; transform: translateX(-50%%); font-size: 11px; color: var(--ink2); white-space: nowrap">הפסקה · %d דק׳</div>'%(16+(X(lb['t0'])+X(lb['t1']))/2,round((lb['t1']-lb['t0'])/60000))
        rows='';a=[k for k,s in enumerate(D.SEG) if s['type']=='lift' and s['name']=='Sadzele'][0]
        for s in D.SEG[a:a+12]:
            if s['type']=='lift':
                rows+='<div class="tl"><span class="t">%s</span><div class="c"><span class="h">רכבל <b style="direction: ltr; unicode-bidi: isolate">%s</b>, מלמטה עד למעלה</span><span class="m">%s דק׳ · %s מ׳ למעלה</span></div></div>'%(D.hm(s['t0']),s['name'],ms((s['t1']-s['t0'])/1000),n0(s['rise_m']))
            elif s['type']=='descent':
                ex=''
                if s is D.DESC[9]:ex='<span class="m">פה טסת: %.1f קמ״ש ב-<b style="direction: ltr; unicode-bidi: isolate">Sadzele 1</b> · דקה בלי קליטה ב-%s, מסומנת בקו מקווקו</span>'%(s['max_kmh'],D.hm(D.FIN['gaps'][0]['t0']))
                rows+='<div class="tl"><span class="t">%s</span><div class="c">%s<span class="m">%.1f ק״מ · %s מ׳ למטה · מרבית %.0f קמ״ש%s</span>%s</div></div>'%(D.hm(s['t0']),signs(main(s)),s['dist_m']/1000,n0(s['drop_m']),s['max_kmh'],' · עצירה אחת' if len(s['stops'])==1 else '',ex)
            elif s['type']=='walk' and s['dist_m']>100:
                rows+='<div class="tl" style="min-height: 0"><span class="t">%s</span><div class="c"><span class="m">הליכה, %s מ׳</span></div></div>'%(D.hm(s['t0']),n0(round(s['dist_m']/10)*10))
            elif s['type']=='stop' and s.get('kind')=='queue' and s['t1']-s['t0']>=80000:
                rows+='<div class="tl" style="min-height: 0"><span class="t">%s</span><div class="c"><span class="m">תור, %d דק׳</span></div></div>'%(D.hm(s['t0']),round((s['t1']-s['t0'])/60000))
        o+='<div style="position: absolute; left: 16px; right: 16px; top: 340px; bottom: 0; overflow: hidden"><div class="k" style="font-size: 12px; font-weight: 700; color: var(--ink2); padding-bottom: 2px">צהריים</div>%s</div>'%rows
        o+=fade(790,54)
        return o
    phone('L07-DayRidge.dc.html','סבב 4 · 7 · הרכס של היום וציר הזמן',b07())
    return dict(d4=d4,kmh=kmh,dstr=dstr)
