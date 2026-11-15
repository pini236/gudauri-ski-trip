#!/usr/bin/env python3
"""Maps for the redesign canvas, from the real data only (site/data).
Usage: python3 design/redesign/tools/maps.py   (writes design/redesign/assets/, prints a digest)
North is up, one scale for every direction; styles differ, geometry does not."""
import json,base64,struct,math,io,os
from PIL import Image,ImageEnhance,ImageOps
ROOT=os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT=os.path.join(ROOT,'design/redesign/assets')
D=json.load(open(os.path.join(ROOT,'site/data/runs-and-lifts.json')))
T=json.load(open(os.path.join(ROOT,'site/data/terrain.json')))
lat0,lon0=T['proj']['lat0'],T['proj']['lon0'];kx=111320*math.cos(math.radians(lat0));ky=111320
P=lambda g:((g[1]-lon0)*kx,-(g[0]-lat0)*ky)
d=T['dem'];raw=base64.b64decode(d['b64']);H=struct.unpack('<%dh'%(len(raw)//2),raw)
sx=(d['x1']-d['x0'])/(d['nx']-1);sy=(d['y1']-d['y0'])/(d['ny']-1)
def elev(x,y):
    c=max(0,min(d['nx']-1.0001,(x-d['x0'])/sx));r=max(0,min(d['ny']-1.0001,(y-d['y0'])/sy))
    c0,r0=int(c),int(r);fc,fr=c-c0,r-r0;i=r0*d['nx']+c0
    return (H[i]*(1-fc)+H[i+1]*fc)*(1-fr)+(H[i+d['nx']]*(1-fc)+H[i+d['nx']+1]*fc)*fr
# window: 390x844 px, the Gudauri side; scale px per metre
W,Hh=390,844;S=0.105
CX,CY=( (44.4945-lon0)*kx, -(42.4865-lat0)*ky )   # centre of the window, metres
OY=416                                             # px row of the centre
px=lambda m:(round(W/2+(m[0]-CX)*S,1),round(OY+(m[1]-CY)*S,1))
def path(pts):return 'M'+' L'.join('%g %g'%px(p) for p in pts)
def dense(pts,step=40):
    o=[pts[0]]
    for a,b in zip(pts,pts[1:]):
        n=max(1,int(math.hypot(b[0]-a[0],b[1]-a[1])/step))
        for k in range(1,n+1):o.append((a[0]+(b[0]-a[0])*k/n,a[1]+(b[1]-a[1])*k/n))
    return o
pistes=[]
for p in D['pistes']:
    segs=[[P(g) for g in s['g']] for s in p['segs'] if not s.get('area')]
    pistes.append(dict(key=p['key'],name=p.get('name') or p['key'],color=p['color'],named=p.get('named'),kind=p.get('kind','run'),len=p['len'],segs=segs))
lifts=[dict(name=l['name'],kind=l['kind'],len=l['len'],dur=l.get('dur'),occ=l.get('occ'),pts=[P(g) for g in l['g']],status=l.get('status')) for l in D['lifts']]
SL=[(15,'#3FA85F'),(25,'#F2C13D'),(30,'#F08A3C'),(91,'#DC3B33')]
def slope_runs(pts):
    q=dense(pts,40);out=[]
    for a,b in zip(q,q[1:]):
        dd=math.hypot(b[0]-a[0],b[1]-a[1]) or 1;deg=math.degrees(math.atan(abs(elev(*a)-elev(*b))/dd))
        c=[c for t,c in SL if deg<t][0]
        if out and out[-1][0]==c:out[-1][1].append(b)
        else:out.append([c,[a,b]])
    return out
STY={ # per direction: day/night palettes for the map only
 'a':dict(w=3.2,case=5.6,lift=1.4,st=2.6,cap='round',
   day=dict(green='#198549',blue='#1F6FD0',red='#D1342B',black='#15181C',case='#FFFFFF',lift='#0E2233',dim='#9FB0C0',trk='#6B3FD4'),
   night=dict(green='#46D08A',blue='#62AEFF',red='#FF7468',black='#DCE4EC',case='#0B1220',lift='#C5D2E0',dim='#3A4A5E',trk='#B79CFF')),
 'b':dict(w=2.2,case=0,lift=1,st=2.2,cap='round',
   day=dict(green='#198549',blue='#1F6FD0',red='#D1342B',black='#111111',case='none',lift='#111111',dim='#C9CBCF',trk='#0A7E8C'),
   night=dict(green='#46D08A',blue='#62AEFF',red='#FF7468',black='#F2F2F0',case='none',lift='#F2F2F0',dim='#3A3D42',trk='#5CC8D6')),
 'c':dict(w=4.2,case=0,lift=1.6,st=3.2,cap='butt',
   day=dict(green='#127A40',blue='#1559B8',red='#C4261D',black='#000000',case='none',lift='#000000',dim='#C4C4C4',trk='#000000'),
   night=dict(green='#3EE08C',blue='#5CB0FF',red='#FF6B5E',black='#FFFFFF',case='none',lift='#FFFFFF',dim='#3C3C3C',trk='#C8F31D')),
 'd':dict(w=4.6,case=7,lift=2.4,st=4.2,cap='round',
   day=dict(green='#198549',blue='#1F6FD0',red='#D1342B',black='#14181D',case='#FFFFFF',lift='#14181D',dim='#C8CDD3',trk='#14181D'),
   night=dict(green='#46D08A',blue='#62AEFF',red='#FF7468',black='#E9EDF1',case='#101418',lift='#E9EDF1',dim='#3B434C',trk='#FFCC00')),
}
DAY=['Goodaura','Tatra 2','Shino','Goodaura 1','Soliko','Soliko 1','Kudebi','Kudebi 1','Kudebi 2','Khada','Kikilo 1','Kikilo','Goodaura 2']  # sample day: real lines, example order
def svg(dirn,mode,variant):
    st=STY[dirn];c=st[mode];o=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linejoin="round" stroke-linecap="%s">'%(W,Hh,W,Hh,st['cap'])]
    sel='Tatra 2';dim=variant!='all'
    def col(p):return c['dim'] if dim else c[p['color']]
    if st['case'] and not dim:
        for p in pistes:
            if p['named']:
                for s in p['segs']:o.append('<path d="%s" stroke="%s" stroke-width="%g"/>'%(path(s),c['case'],st['case']))
    for p in pistes:
        for s in p['segs']:
            w=st['w']*(1 if p['named'] else .55);da=' stroke-dasharray="%g %g"'%(w*1.6,w*1.6) if (p['kind']=='ski-way' or not p['named']) else ''
            o.append('<path d="%s" stroke="%s" stroke-width="%g"%s%s/>'%(path(s),col(p),w*(.7 if dim else 1),da,' opacity=".6"' if not p['named'] else ''))
    for l in lifts:
        if not l['name']:continue
        lc=c['dim'] if (dim and not (variant=='day' and l['name'] in DAY)) else c['lift']
        o.append('<path d="%s" stroke="%s" stroke-width="%g"%s/>'%(path(l['pts']),lc,st['lift'],' stroke-dasharray="1 4"' if dirn=='b' else ''))
        for e in (l['pts'][0],l['pts'][-1]):
            x,y=px(e);o.append('<circle cx="%g" cy="%g" r="%g" fill="%s" stroke="%s" stroke-width="%g"/>'%(x,y,st['st'],c['case'] if c['case']!='none' else ('#FFFFFF' if mode=='day' else '#000000'),lc,st['lift']))
    if variant=='run':
        p=[q for q in pistes if q['key']==sel][0]
        for s in p['segs']:
            if st['case']:o.append('<path d="%s" stroke="%s" stroke-width="%g"/>'%(path(s),c['case'],st['case']+4))
            for cc,pts in slope_runs(s):o.append('<path d="%s" stroke="%s" stroke-width="%g"/>'%(path(pts),cc,st['w']+2.4))
            x,y=px(s[0]);o.append('<circle cx="%g" cy="%g" r="5" fill="%s" stroke="%s" stroke-width="2"/>'%(x,y,c['trk'],'#FFFFFF' if mode=='day' else '#000000'))
    if variant=='day':
        for p in pistes:
            if p['key'] in DAY:
                for s in p['segs']:
                    if st['case']:o.append('<path d="%s" stroke="%s" stroke-width="%g"/>'%(path(s),c['case'],st['case']+2))
                    o.append('<path d="%s" stroke="%s" stroke-width="%g"/>'%(path(s),c['trk'],st['w']+1.2))
    o.append('</svg>');return '\n'.join(o)
os.makedirs(OUT,exist_ok=True)
for dn in STY:
    for mode in('day','night'):
        for v in('all','run','day'):open(os.path.join(OUT,'map-%s-%s-%s.svg'%(dn,v,mode)),'w').write(svg(dn,mode,v))
# relief (the site's own hillshade), cropped to the same window
hs=T['hill']['src'];im=Image.open(io.BytesIO(base64.b64decode(hs.split(',')[1]))).convert('L')
mx0=CX-W/2/S;my0=CY-OY/S;mx1=mx0+W/S;my1=my0+Hh/S
fx=lambda x:(x-d['x0'])/(d['x1']-d['x0'])*im.width;fy=lambda y:(y-d['y0'])/(d['y1']-d['y0'])*im.height
cr=im.crop((fx(mx0),fy(my0),fx(mx1),fy(my1))).resize((W*2,Hh*2),Image.LANCZOS)
cr=ImageOps.autocontrast(cr,cutoff=1)
ImageOps.colorize(cr,'#B9C8D8','#FFFFFF',mid='#E6EDF4').save(os.path.join(OUT,'relief-day.jpg'),quality=82)
ImageOps.colorize(cr,'#05080F','#2A3B55',mid='#111B2C').save(os.path.join(OUT,'relief-night.jpg'),quality=82)
# elevation ladder for direction d: x = schematic column, y = true elevation
order=['Pirveli','Zuma','Soliko','Kudebi','Khada','Kikilo','New Goodaura','Goodaura','Shino','Snow Park','Sadzele','Firni']
def ladder(mode):
    c=STY['d'][mode];LW,LH=358,300;lo,hi=1900,3300;yy=lambda e:round(LH-14-(e-lo)/(hi-lo)*(LH-28),1)
    o=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round">'%(LW,LH,LW,LH)]
    for e in range(2000,3300,250):o.append('<path d="M0 %g H%d" stroke="%s" stroke-width="1" stroke-dasharray="2 5"/>'%(yy(e),LW,c['dim']))
    for i,n in enumerate(order):
        l=[q for q in lifts if q['name']==n][0];e0,e1=sorted((elev(*l['pts'][0]),elev(*l['pts'][-1])));x=18+i*(LW-36)/(len(order)-1)
        o.append('<path d="M%g %g V%g" stroke="%s" stroke-width="5"/>'%(x,yy(e0),yy(e1),c['lift']))
        for e in(e0,e1):o.append('<circle cx="%g" cy="%g" r="5.5" fill="%s" stroke="%s" stroke-width="2.4"/>'%(x,yy(e),'#FFFFFF' if mode=='day' else '#101418',c['lift']))
    o.append('</svg>');return '\n'.join(o)
for mode in('day','night'):open(os.path.join(OUT,'ladder-d-%s.svg'%mode),'w').write(ladder(mode))
# ---- digest ----
print('LIFTS name kind len dur bottom top  px(bottom) px(top)')
for l in lifts:
    if l['name']:
        e=[round(elev(*q)) for q in (l['pts'][0],l['pts'][-1])];print(' ',l['name'],l['kind'],l['len'],l['dur'],min(e),max(e),px(l['pts'][0]),px(l['pts'][-1]))
print('LADDER columns x:',[(n,round(18+i*(358-36)/(len(order)-1))) for i,n in enumerate(order)],' y(e)=286-(e-1900)/1400*272')
print('RUNS key colour len top bottom drop steepest')
for p in pistes:
    if p['named'] and p['segs']:
        q=dense(max(p['segs'],key=len),40);es=[elev(*a) for a in q]
        stp=max(math.degrees(math.atan(abs(es[i]-es[i+3])/120)) for i in range(max(1,len(es)-3))) if len(es)>3 else 0
        print(' ',p['name'],p['color'],p['len'],round(max(es)),round(min(es)),round(max(es)-min(es)),round(stp),'px',px(q[len(q)//2]))
p=[q for q in pistes if q['key']=='Tatra 2'][0];q=dense(p['segs'][0],40);es=[elev(*a) for a in q]
if es[0]<es[-1]:es=es[::-1]
PW,PH=326,96;lo,hi=min(es),max(es)
print('TATRA2 profile (viewBox 0 0 326 96, top at left): M'+' L'.join('%g %g'%(round(i/(len(es)-1)*PW,1),round(8+(hi-e)/(hi-lo)*(PH-16),1)) for i,e in enumerate(es)))
print('PEAKS px',[(k['n'],k['ele'],px((k['x'],k['y']))) for k in T['peaks']])
print('PLACES',[(k,) for k in T['env']['places']][:8])
