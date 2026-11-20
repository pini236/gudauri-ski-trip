#!/usr/bin/env python3
"""The redesign POC on Android (decisions 83, 84): the round 3 mountain as the app's first screen.

Writes, into the test build only (app/android/app/src/preview/assets/redesign/):
  * peak-<hour>.webp   the hero picture for each hour of the day, 3x (1170 x 2532), with the small real things
                       (rivers, roads, the 41 buildings, the village lights) baked in by peak.details(), composited in
                       Chromium so they look exactly as on the canvas.
  * peak.json          the live layer: every run and lift already projected with peak.py's formula onto the 390 x 844
                       view, split where a ridge hides it, the chosen-run line (top to bottom, distance and slope per
                       point, by the site's rule), the facts on the run's sign, and per hour the sun's offset for the
                       lifts' shadows.

The projection stays in peak.py (one formula, the architect's condition for phase A); the app draws what it gets.
Usage: python3 design/redesign/tools/peak_android.py [hour ...]   (Chromium through node's playwright)"""
import os,sys,json,math,io,base64,subprocess,tempfile
import numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
os.environ.setdefault('PEAK_CACHE',os.path.join(tempfile.gettempdir(),'peak-android'))
import peak as P
from PIL import Image
ROOT=P.ROOT
OUT=os.path.join(ROOT,'app/android/app/src/preview/assets/redesign')
# the hours the app blends between (Gudauri local time); blue and night take the night palette
HOURS=[('dawn',9.0),('morning',10.6),('noon',13.0),('after',15.9),('sunset',17.35),('blue',18.3),('night',22.0)]
NIGHT={'blue','night'}
SCALE=3
D=json.load(open(os.path.join(ROOT,'site/data/runs-and-lifts.json')))
SRC={p['key']:p for p in D['pistes']}

def r1(a):return [round(float(v),1) for v in a]
def flat(ch):
    o=[]
    for x,y in ch:o+= [round(float(x),1),round(float(y),1)]
    return o
def thin(ch,gap=1.6):
    o=[ch[0]]
    for q in ch[1:-1]:
        if math.hypot(q[0]-o[-1][0],q[1]-o[-1][1])>gap:o.append(q)
    o.append(ch[-1]);return o

def vectors(V):
    pis=[]
    for p in P.PISTES:
        src=SRC[p['key']];chs=[]
        for s in p['segs']:
            x,y,vis,z,q=P.project(V,s);chs+=[flat(thin(c)) for c in P.chunks(x,y,vis)]
        e=dict(key=p['key'],name=p['name'],color=p['color'],kind=p['kind'],named=p['named'],lit=p['lit'],
               ref=(src.get('refs') or [''])[0],chunks=chs)
        if p['named'] and p['segs']:
            R=P.run_path(V,p['key']);st=P.stats(p['key'])
            e.update(len=src.get('len'),drop=st['drop'],steep=st['steep'],top=st['top'],bot=st['bot'],
                     frm=src.get('fromLifts') or [],to=src.get('toLifts') or [],
                     path=dict(x=r1(R['x']),y=r1(R['y']),v=[int(b) for b in R['vis']],m=[round(float(c)) for c in R['cum']],
                               d=[round(float(c),1) for c in R['deg']],z=[round(float(c)) for c in R['z']]))
        pis.append(e)
    lifts=[]
    for l in P.LIFTS:
        x,y,vis,z,q=P.project(V,l['pts'],lift=11);xg,yg,_,_,_=P.project(V,l['pts'])
        ends=[]
        for i in(0,len(x)-1):ends.append([round(float(xg[i]),1),round(float(yg[i]),1),int(vis[i])])
        lifts.append(dict(name=l['name'],kind=l['kind'],x=r1(x),y=r1(y),gx=r1(xg),gy=r1(yg),v=[int(b) for b in vis],ends=ends,
                          gondola=l['kind'] in('gondola','cable_car','mixed_lift')))
    return pis,lifts

def compose(jobs):
    """jobs: (picture png path, details svg, out png path): the picture with the details over it, 3x, in Chromium"""
    tmp=tempfile.mkdtemp();js=os.path.join(tmp,'shot.js');lst=[]
    for i,(pic,svg,outp) in enumerate(jobs):
        h=os.path.join(tmp,'p%d.html'%i)
        open(h,'w').write('<!doctype html><html><body style="margin:0;width:390px;height:844px;overflow:hidden">'
            '<img src="file://%s" style="position:absolute;left:0;top:0;width:390px;height:844px">'
            '<div style="position:absolute;left:0;top:0">%s</div></body></html>'%(pic,svg))
        lst.append([h,outp])
    open(js,'w').write("""const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();
const p=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:%d});
for(const [h,o] of %s){await p.goto('file://'+h);await p.waitForTimeout(150);await p.screenshot({path:o});}
await b.close();})();"""%(SCALE,json.dumps(lst)))
    subprocess.run(['node',js],check=True,cwd='/tmp')

def main(sel):
    os.makedirs(OUT,exist_ok=True)
    meta=os.path.join(OUT,'peak.json')
    old=json.load(open(meta)) if os.path.exists(meta) else {}
    hours={h['key']:h for h in old.get('hours',[])}
    V=None;jobs=[];tmp=tempfile.mkdtemp()
    for key,h in HOURS:
        if sel and key not in sel:continue
        V,im=P.render('hero',key,out=SCALE);pic=os.path.join(tmp,key+'.png');im.save(pic)
        mode='night' if key in NIGHT else 'day'
        jobs.append((pic,P.details(V,mode,key),os.path.join(tmp,key+'-c.png')))
        dx,dy=P.sun_shift(V,key,1)
        hours[key]=dict(key=key,h=h,night=key in NIGHT,dx=round(dx,3),dy=round(dy,3),file='peak-%s.webp'%key)
        print('rendered',key,flush=True)
    compose(jobs)
    for pic,_,c in jobs:
        key=os.path.basename(c)[:-6];Image.open(c).convert('RGB').save(os.path.join(OUT,'peak-%s.webp'%key),'WEBP',quality=80,method=6)
        print(key,os.path.getsize(os.path.join(OUT,'peak-%s.webp'%key))//1024,'KB')
    if V is None:V=P.load('hero')
    pis,lifts=vectors(V)
    data=dict(v=1,w=V['W'],h=V['H'],source='design/redesign/tools/peak.py, view hero (12.1.2027)',
              hours=[hours[k] for k,_ in HOURS if k in hours],pistes=pis,lifts=lifts,
              peaks=[dict(name='Sadzele',ele=3307,x=352,y=186),dict(name='Bidara',ele=3174,x=100,y=197),dict(name='Kudebi',ele=3006,x=352,y=322)],
              places=[dict(name='New Gudauri',x=166,y=684)],
              slope=[[t,c] for t,c in P.SLOPE])
    json.dump(data,open(meta,'w'),ensure_ascii=False,separators=(',',':'))
    print('peak.json',os.path.getsize(meta)//1024,'KB')

if __name__=='__main__':main(sys.argv[1:])
