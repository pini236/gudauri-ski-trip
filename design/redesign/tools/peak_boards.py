#!/usr/bin/env python3
"""Round 3 boards of the redesign canvas (design/redesign/canvas/project/K*.dc.html):
the mountain as a landscape, choosing a run, a chosen run, and the app beyond the mountain.
Usage: python3 design/redesign/tools/peak_boards.py    (after peak.py; pictures are canvas assets)
Every line on a board comes from site/data through peak.py; values marked "דוגמה" are examples."""
import os,sys,math,re
import numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import peak as P, mtn_boards as MB
OUT=MB.OUT
IMG={ # picture -> id in the canvas
 'hero-morning':'5221240478d63ac82c9c61ccb7497d6e','hero-night':'b2127fcd841ba1e3a545cd20c07c5399','hero-dawn':'8b8374cb5656648b0128748e5cbbdc85',
 'hero-after':'b0e93356e66046bb33f9e56395555f3e','hero-sunset':'034c7a72df98009aa41c9dc69ef8a4ab','hero-blue':'2e236aa5a7a81bb20af34c13fc10e06d',
 'hero-morning-fresh':'82da9bb92db2d6eef5a5f74688ae5333','hero-morning-wind':'19cadbc5ca97558734a1f05bca290b15','hero-dawn-cloud':'4a9d2f3251e5dbefc990109f53c84fbe',
 'hero-noon-storm':'a07cbeb89cf89e04e31d5fd33b200627','zoom-morning':'2d2fd4382cd4f1cb0baa67da38d758c4','zoom-night':'b4aca0dc429da40ff79872b956010f62',
 'vill-morning':'aa19ad835a67b136256b0377aafe6e3f','vill-night':'dbe151f007919e249f8dec4aad390add','wide-dawn-cloud':'8dce825a4fe26a13b993798e4426902a'}
U=lambda n:'/_blob/'+IMG[n]
VH,VZ,VV=P.load('hero'),P.load('zoom'),P.load('vill')
VIEW={'hero':VH,'zoom':VZ,'vill':VV}
def pic(view,day='morning',night='night',top=0,left=0):
    V=VIEW[view];st='left: %dpx; top: %dpx; width: %dpx; height: %dpx'%(left,top,V['W'],V['H'])
    if night is None:return '<img class="pic" alt="" src="%s" style="%s">'%(U(view+'-'+day),st)
    return '<img class="pic d" alt="" src="%s" style="%s"><img class="pic n" alt="" src="%s" style="%s">'%(U(view+'-'+day),st,U(view+'-'+night),st)
def lay(day,night=None,top=0,left=0):
    """inline vector layers (strings of svg), for day and for night"""
    st='left: %dpx; top: %dpx'%(left,top)
    if night is None:return '<div class="ov" style="%s">%s</div>'%(st,day)
    return '<div class="ov d" style="%s">%s</div><div class="ov n" style="%s">%s</div>'%(st,day,st,night)
def both(fn,top=0):
    """a layer built for day and night: fn(mode, light)"""
    return lay(fn('day','morning'),fn('night','night'),top=top)
def base(view,runs='line',lifts=True,det=True,dimlifts=False,only=None,skip=None,cabins=True):
    V=VIEW[view];o=''
    if det:o+=both(lambda m,l:P.details(V,m,l))
    if runs:o+=both(lambda m,l:P.runs_svg(V,m,runs,only=only,skip=skip))
    if lifts:o+=both(lambda m,l:P.lifts_svg(V,m,l,dim=dimlifts,cabins=cabins))
    return o
def svg(V,inner,extra=''):
    return P.svg_open(V,extra)+inner+'</svg>'
def lbl(x,y,t,small=None,cls='lbl',style=''):
    return '<div class="%s" style="left: %.0fpx; top: %.0fpx;%s">%s%s</div>'%(cls,x,y,style,t,'<small>%s</small>'%small if small else '')
def tag(x,y,t,color=None,style=''):
    return '<div class="tag" style="left: %.0fpx; top: %.0fpx;%s">%s%s</div>'%(x,y,style,'<i style="background: %s"></i>'%color if color else '',t)
COL={'green':'var(--green)','blue':'var(--blue)','red':'var(--red)','black':'var(--black)'}
HEX={'green':'#178A4A','blue':'#1B6FDB','red':'#D8362C','black':'#14171B'}
HE={'green':'ירוק','blue':'כחול','red':'אדום','black':'שחור'}
def sign(key,big=False):
    """a piste sign: colour plate with the official number when there is one"""
    p=P.find(key);src=[q for q in P.D['pistes'] if q['key']==p['key']][0];ref=(src.get('refs') or [''])[0]
    return '<span class="sgn%s" style="background: %s">%s</span>'%(' big' if big else '',HEX[p['color']] if p['color']!='black' else '#14171B',ref or '&nbsp;')
def facts(key):
    src=[q for q in P.D['pistes'] if q['key']==key][0];st=P.stats(key)
    return dict(name=src.get('name') or key,color=src['color'],len=src['len'],drop=st['drop'],top=st['top'],bot=st['bot'],steep=st['steep'],
                to=src.get('toLifts') or [],frm=src.get('fromLifts') or [],ref=(src.get('refs') or [''])[0])
def km(m):
    return ('%.1f ק״מ'%(m/1000)) if m>=1000 else ('%d מ׳'%(round(m/10)*10))
def lift(name):
    return [l for l in P.D['lifts'] if l.get('name')==name][0]
def glow_run(V,key,mode,w=3.4,color=None,halo=True):
    c=color or P.PAL[mode][P.find(key)['color']];o=''
    for ch in P.all_chunks(V,key):
        d=P.pathd(ch)
        if halo:o+='<path d="%s" stroke="%s" stroke-width="%.1f" opacity=".22"/>'%(d,c,w*4)
        o+='<path d="%s" stroke="%s" stroke-width="%.1f"/><path d="%s" stroke="%s" stroke-width="%.1f"/>'%(d,P.PAL[mode]['case'],w+2.4,d,c,w)
    return o
def corridor(V,key,mode,veil=None,width=250,uid='c'):
    """the rest of the mountain under a veil, the chosen run in a corridor of light"""
    R=P.run_path(V,key);d=''.join('<path d="%s" stroke="#000" stroke-width="%g"/>'%(P.pathd(ch),V['S']*width) for ch in P.chunks(R['x'],R['y'],R['vis']))
    veil=veil or ('rgba(54,78,128,.50)' if mode=='day' else 'rgba(2,6,18,.62)')
    return ('<defs><filter id="%sb" x="-30%%" y="-30%%" width="160%%" height="160%%"><feGaussianBlur stdDeviation="%g"/></filter>'
            '<mask id="%sm"><rect width="%d" height="%d" fill="#fff"/><g filter="url(#%sb)">%s</g></mask></defs><rect width="%d" height="%d" fill="%s" mask="url(#%sm)"/>')%(uid,V['S']*56,uid,V['W'],V['H'],uid,d,V['W'],V['H'],veil,uid)
def light_line(V,key,mode,w=5.2):
    R=P.run_path(V,key);case=P.PAL[mode]['case'];o=''
    for ch in P.chunks(R['x'],R['y'],R['vis']):o+='<path d="%s" stroke="%s" stroke-width="%.1f"/>'%(P.pathd(ch),case,w+3)
    o+=P.slope_paths(R,'%.1f'%w)
    acc=0
    for i in range(1,len(R['x'])-1):                    # fall-line chevrons
        acc+=R['cum'][i]-R['cum'][i-1]
        if acc>240 and R['vis'][i]:
            acc=0;x,y,ax,ay,_,_=P.at(R,R['cum'][i])
            o+='<path d="M%.1f %.1f L%.1f %.1f L%.1f %.1f" stroke="#fff" stroke-width="1.3"/>'%(x-ax*1.6-ay*2.1,y-ay*1.6+ax*2.1,x+ax*1.3,y+ay*1.3,x-ax*1.6+ay*2.1,y-ay*1.6-ax*2.1)
    return o,R
def mini(key,w=40,h=92):
    """the run as its own little shape: the real line turned so the start is up"""
    p=P.find(key);S=[np.array(P.dense(s,25)) for s in p['segs']];A=np.vstack(S);z=P.elev(A[:,0],A[:,1]);a0=A[int(np.argmax(z))];a1=A[int(np.argmin(z))]
    ax=a1-a0;L=np.hypot(*ax) or 1;ax/=L;pr=np.array([ax[1],-ax[0]]);al=(A-a0)@ax;la=(A-a0)@pr
    sc=min((h-8)/(al.max()-al.min()+1),(w-8)/(la.max()-la.min()+1));d=''
    for q in S:
        x=w/2-((q-a0)@pr-(la.max()+la.min())/2)*sc;y=4+((q-a0)@ax-al.min())*sc;d+=P.pathd(list(zip(x.tolist(),y.tolist())),1.2)
    return '<svg viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round" stroke-linejoin="round" style="direction: ltr"><path d="%s" stroke="%s" stroke-width="3"/><circle cx="%.1f" cy="%.1f" r="2.6" fill="#fff" stroke="%s" stroke-width="1.6"/></svg>'%(w,h,w,h,d,HEX[p['color']],w/2-(0-(la.max()+la.min())/2)*sc,4+(0-al.min())*sc,HEX[p['color']])
def shaded(pts,light):
    """is each ground point in the shadow of the mountain, for the sun of this light?"""
    L=P.LIGHTS[light];a,al=math.radians(L['az']),math.radians(L['alt']);q=np.array(pts);z0=P.elev(q[:,0],q[:,1]);sh=np.zeros(len(q),dtype=bool)
    for t in np.arange(40,7000,40):
        sh|=P.elev(q[:,0]+math.sin(a)*t,q[:,1]+math.cos(a)*t)>z0+t*math.tan(al)+4
    return sh

EXTRA="""
.H .ov{position:absolute}.H .ov svg{display:block;position:absolute;left:0;top:0}
.H .sgn{display:inline-flex;align-items:center;justify-content:center;min-width:26px;height:20px;padding:0 5px;box-sizing:border-box;border-radius:5px;color:#fff;font-size:12px;font-weight:700;direction:ltr;flex:none;border:1.5px solid rgba(255,255,255,.9)}
.H .sgn.big{min-width:40px;height:30px;font-size:17px;border-radius:7px}
.H .now{position:absolute;top:52px;left:12px;right:12px;min-height:44px;border-radius:22px;display:flex;align-items:center;gap:9px;padding:6px 14px;box-sizing:border-box;font-size:14px;font-weight:500;line-height:1.3}
.H .dock{position:absolute;left:10px;right:10px;bottom:12px;height:60px;border-radius:30px;display:flex;align-items:center;gap:8px;padding:0 8px;box-sizing:border-box}
.H .dock .find{flex:1;height:44px;background:transparent;padding:0 10px;font-size:15.5px}
.H .rb{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;flex:none;border:1px solid var(--line)}
.H .rb.rec{background:#E5352B;border:0}.H .rb.rec span{width:14px;height:14px;border-radius:50%;background:#fff}
.H .eg{font-size:10.5px;font-weight:700;color:var(--ink2);border:1px solid var(--line);border-radius:8px;padding:0 6px;height:18px;display:inline-flex;align-items:center}
.H .ttl{font-size:21px;font-weight:700;line-height:1.2}
.H .sub{font-size:13.5px;color:var(--ink2);line-height:1.4}
.H .rw{display:flex;align-items:center;gap:10px;min-height:46px}
.H .rw+.rw{border-top:1px solid var(--line)}
.H .rw b{font-weight:700;font-size:15px;direction:ltr}
.H .rw .r{margin-inline-start:auto;font-size:13px;color:var(--ink2);white-space:nowrap}
.H .nums{display:flex;gap:18px}
.H .nums div{display:flex;flex-direction:column;gap:3px}
.H .chipr{display:flex;gap:6px;overflow:hidden}
.H .ch{height:36px;padding:0 13px;border-radius:18px;border:1px solid var(--line);font-size:13px;font-weight:500;display:inline-flex;align-items:center;gap:6px;white-space:nowrap;flex:none;background:var(--glass)}
.H .ch.on{background:var(--ink);color:var(--bg);border-color:transparent;font-weight:700}
.H .pin{position:absolute;transform:translate(-50%,-100%);display:flex;flex-direction:column;align-items:center}
.H .pin b{height:24px;padding:0 9px;border-radius:12px;background:var(--ink);color:var(--bg);font-size:11.5px;font-weight:700;display:inline-flex;align-items:center;gap:5px;white-space:nowrap}
.H .pin i{width:2px;height:10px;background:var(--ink)}
.H .ring{position:absolute;border-radius:50%;transform:translate(-50%,-50%);border:2px solid #fff;box-shadow:0 0 0 2px var(--acc),0 0 18px 4px rgba(255,255,255,.6)}
.H .you{position:absolute;width:16px;height:16px;border-radius:50%;transform:translate(-50%,-50%);background:#2F7BFF;border:3px solid #fff;box-shadow:0 0 0 7px rgba(47,123,255,.25),0 2px 6px rgba(0,0,0,.35)}
.H .card{border-radius:22px;padding:14px 16px;box-sizing:border-box;display:flex;flex-direction:column;gap:10px}
.H .km{font-size:9.5px;font-weight:700;fill:#fff;paint-order:stroke;stroke:rgba(10,25,50,.8);stroke-width:2.4px}
"""
MB.CSS=MB.CSS+EXTRA
def phone(fn,title,body,night=False,lang='he',fixed=None):
    MB.phone(fn,title,body,night=night,lang=lang)
    if fixed:
        p=os.path.join(OUT,fn);s=open(p,encoding='utf8').read()
        s=s.replace('<div class="{{mode}}">','<div class="%s">'%fixed);open(p,'w',encoding='utf8').write(s)
WCSS="""
.HW .sec{font-size:13px;font-weight:700;letter-spacing:.04em;color:#6B3FD4}
.HW h1{font-size:40px;font-weight:900;line-height:1.15}
.HW h2{font-size:23px;font-weight:700;line-height:1.25}
.HW h3{font-size:16.5px;font-weight:700;line-height:1.3}
.HW .lead{font-size:18px;line-height:1.55;max-width:900px}
.HW .cap{font-size:13.5px;line-height:1.45;color:#3C4753}
.HW .cap b{color:#10161D}
.HW .rowx{display:flex;gap:16px;align-items:flex-start}
.HW .fr{display:flex;flex-direction:column;gap:8px;flex:none}
.HW .th{background:#DCE6F2}
.HW .th .in{position:absolute;left:0;top:0;transform-origin:0 0}
.HW .th .in img{position:absolute;display:block;max-width:none}
.HW .th .in .ov{position:absolute;left:0;top:0}
.HW .th .in .ov svg{position:absolute;left:0;top:0;display:block}
.HW .tg{display:inline-flex;align-items:center;height:22px;padding:0 9px;border-radius:11px;font-size:12px;font-weight:700;white-space:nowrap}
.HW .tg.ok{background:#DDF2E5;color:#0F5C31}.HW .tg.new{background:#E7E0FA;color:#3D2A9E}.HW .tg.ask{background:#FBEBCF;color:#7A4A00}
.HW .grid{display:grid;gap:16px}
.HW .tm{font-size:22px;font-weight:300;direction:ltr;display:inline-block}
"""
def wide(fn,title,body,w,h):
    MB.wide(fn,title,body,w,h);p=os.path.join(OUT,fn);s=open(p,encoding='utf8').read()
    s=s.replace('</style>\n</helmet>',WCSS+'</style>\n</helmet>',1);open(p,'w',encoding='utf8').write(s)
def crop(view,light,w,h,cx,cy,scale,layers=''):
    """a window on a picture: centre (1x pixels of the view) and scale"""
    V=VIEW[view];cx=min(max(cx,w/2/scale),V['W']-w/2/scale);cy=min(max(cy,h/2/scale),V['H']-h/2/scale)
    return ('<div class="th" style="width: %dpx; height: %dpx"><div class="in" style="width: %dpx; height: %dpx; transform: translate(%.0fpx,%.0fpx) scale(%g)">'
            '<img alt="" src="%s" style="left: 0; top: 0; width: %dpx; height: %dpx">%s</div></div>')%(w,h,V['W'],V['H'],w/2-cx*scale,h/2-cy*scale,scale,U(view+'-'+light),V['W'],V['H'],layers)
def wl(s):
    return '<div class="ov">%s</div>'%s

if __name__=='__main__':
    import peak_b1,peak_b2,peak_b3
    for m in(peak_b1,peak_b2,peak_b3):m.build()
    print('boards written to',OUT)
