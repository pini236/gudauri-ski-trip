#!/usr/bin/env python3
"""Handoff for the Android POC of the redesign (decision 85): the mountain as the opening screen.
Writes design/handoff/2026-11-20-redesign-poc/ :
  img/<view>-<light>.webp   the pictures, 3 px per point (hero 1170x2532, zoom 1170x1680)
  peak-poc.json             everything the screen draws over them, already projected (points of the picture)
Usage: PEAK_CACHE=<dir> python3 design/redesign/tools/poc_export.py [--img]
Nothing here is the shared contract (site/data/peak.json comes later, through the architect)."""
import os,sys,re,json,math
import numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import peak as P
from mountain import elev_raw,kx,ky,lat0,lon0
OUT=os.path.join(P.ROOT,'design/handoff/2026-11-20-redesign-poc');os.makedirs(os.path.join(OUT,'img'),exist_ok=True)
LIGHTS=['dawn','morning','noon','after','sunset','blue','night'];NIGHT=('blue','night')
R1=lambda a:[round(float(v),1) for v in a]
def flat(ch):return [round(float(v),1) for p in ch for v in p]
def thin(ch,gap=1.6):
    o=[ch[0]]
    for q in ch[1:-1]:
        if math.hypot(q[0]-o[-1][0],q[1]-o[-1][1])>gap:o.append(q)
    o.append(ch[-1]);return o

def images():
    tot=0
    for v in('hero','zoom'):
        for l in LIGHTS:
            V,im=P.render(v,l,out=3);P.save(V,v);f=os.path.join(OUT,'img','%s-%s.webp'%(v,l));im.save(f,quality=84,method=6);tot+=os.path.getsize(f);print(v,l,im.size,os.path.getsize(f)//1024)
    print('pictures: %d KB'%(tot//1024))

# ---- the static small things (rivers, roads, buildings, village lights) as a display list, parsed from peak.details
AT=lambda s,k,d=None:(re.search(r'\b%s="([^"]*)"'%k,s) or [None,d])[1]
def dlist(svg):
    o=[]
    for m in re.finditer(r'<(path|polygon|circle)\b([^>]*)/>',svg):
        t,a=m.group(1),m.group(2);op=AT(a,'opacity');e={}
        if t=='path':
            nums=[float(x) for x in re.findall(r'-?\d+\.?\d*',AT(a,'d'))];e=dict(k='l',p=R1(nums),s=AT(a,'stroke'),w=float(AT(a,'stroke-width')))
        elif t=='polygon':
            nums=[float(x) for x in re.findall(r'-?\d+\.?\d*',AT(a,'points'))];e=dict(k='p',p=R1(nums),f=AT(a,'fill'))
            if AT(a,'stroke'):e.update(s=AT(a,'stroke'),w=float(AT(a,'stroke-width')))
        else:e=dict(k='c',x=float(AT(a,'cx')),y=float(AT(a,'cy')),r=float(AT(a,'r')),f=AT(a,'fill'))
        if op:e['o']=float(op)
        o.append(e)
    return o

def view(v):
    V=P.load(v);S=V['S'];W,H=V['W'],V['H'];o=dict(W=W,H=H,px=3,images={l:'img/%s-%s.webp'%(v,l) for l in LIGHTS})
    o['proj']=dict(S=S,c=R1(V['c']),near=V['near'],y_c=V['y_c'],zref=round(V['zref'],2),step=10,F=[round(float(x),2) for x in V['F']],C=[round(float(x),4) for x in V['C']])
    o['zf_runs']=round(max(1,S/0.128)**0.62,3);o['zf_lifts']=round(max(1,S/0.128)**0.7,3)
    o['shadow']={l:R1(np.array(P.sun_shift(V,l,1))*100) for l in LIGHTS if l not in NIGHT}      # screen offset of the shadow of a point 100 m up
    # the small things: one list for the day and one for the night; only the shadows of the buildings move with the hour
    dl={l:dlist(P.details(V,'night' if l in NIGHT else 'day',l)) for l in LIGHTS};day=[l for l in LIGHTS if l not in NIGHT];base=[];sh={l:[] for l in day}
    for i,e in enumerate(dl[day[0]]):
        if all(dl[l][i]==e for l in day):base.append(e)
        else:
            base.append(dict(k='sh',i=len(sh[day[0]]),f=e['f'],o=e.get('o',1)))
            for l in day:sh[l].append(dl[l][i]['p'])
    assert dl['blue']==dl['night']
    o['details']=dict(day=base,night=dl['night']);o['bshadow']=sh
    # runs, as the live layer draws them
    ps=[]
    for p in P.PISTES:
        ln=[]
        for s in p['segs']:
            x,y,vis,z,q=P.project(V,s);ln+=[flat(thin(ch)) for ch in P.chunks(x,y,vis)]
        if ln:ps.append(dict(key=p['key'],name=p['name'],color=p['color'],named=p['named'],kind=p['kind'],lit=p['lit'],l=ln))
    o['pistes']=ps
    ls=[]
    for l in P.LIFTS:
        hgt=11;x,y,vis,z,q=P.project(V,l['pts'],lift=hgt);xg,yg,_,_,_=P.project(V,l['pts']);n=len(x);step=14
        if not vis.any():continue
        ls.append(dict(name=l['name'],gondola=l['kind'] in('gondola','cable_car','mixed_lift'),
            wire=[flat(thin(ch)) for ch in P.chunks(x,y,vis)],ground=[flat(thin(ch)) for ch in P.chunks(xg,yg,vis)],
            towers=[R1((xg[i],yg[i],x[i],y[i])) for i in list(range(0,n,step))[1:] if vis[i] and i<n-3],
            cabins=[R1((x[i],y[i],xg[i],yg[i])) for i in range(step//2,n-2,max(3,step//2+1)) if vis[i]],
            stations=[R1((xg[i],yg[i])) for i in(0,n-1) if vis[i]]))
    o['lifts']=ls
    # a chosen run: its mapped pieces joined where they share a node (2 m), top to bottom, 10 m steps: x, y, visible, metres along, slope class (0..3, the site's rule)
    # pieces that do not touch stay apart: a hole in the map is not bridged
    sel={}
    for p in P.PISTES:
        if not p['named']:continue
        pc=[]
        for s in p['segs']:
            q=np.array(s,dtype=float)
            if elev_raw(*q[0])<elev_raw(*q[-1]):q=q[::-1]
            pc.append(q)
        pc.sort(key=lambda q:-float(elev_raw(*q[0])));used=set();segs=[]
        for a in range(len(pc)):
            if a in used:continue
            used.add(a);q=pc[a]
            while True:
                nx=[b for b in range(len(pc)) if b not in used and math.hypot(*(pc[b][0]-q[-1]))<2]
                if not nx:break
                used.add(nx[0]);q=np.concatenate([q,pc[nx[0]][1:]])
            x,y,vis,z,qq=P.project(V,q.tolist(),step=10)
            cum=np.concatenate([[0],np.cumsum(np.hypot(np.diff(qq[:,0]),np.diff(qq[:,1])))]);deg=P.site_slope(qq,cum)
            cls=[[j for j,(t,_) in enumerate(P.SLOPE) if d<t][0] for d in deg]
            segs.append(dict(n=len(x),len=round(float(cum[-1])),p=[round(float(a_),1) if k<2 else int(a_) for row in zip(x,y,vis,np.round(cum),cls) for k,a_ in enumerate(row)]))
        if not segs:continue
        main=max(range(len(segs)),key=lambda k:segs[k]['len'])
        allp=np.concatenate([np.array(s_['p']).reshape(-1,5) for s_ in segs]);inside=(allp[:,2]>0)&(allp[:,0]>8)&(allp[:,0]<W-8)&(allp[:,1]>8)&(allp[:,1]<H-8)
        sel[p['key']]=dict(segs=segs,main=main,seen=round(float(inside.mean()),2))
    o['sel']=sel
    return V,o

def main():
    if '--img' in sys.argv:images()
    VH,hero=view('hero');VZ,zoom=view('zoom')
    hero['labels']=[dict(t='Sadzele',sub=3307,x=352,y=186),dict(t='Bidara',sub=3174,x=100,y=197),dict(t='Kudebi',sub=3006,x=352,y=322),dict(t='New Gudauri',x=166,y=684)]
    # the near picture is the far one, scaled about a point: zoom = hero*k + t (exact for every run point inside the near picture; above y=50 of the near picture the far one bends, so cross-fade)
    A=[];B=[]
    for p in P.PISTES:
        for sg in p['segs']:
            xh,yh,_,_,_=P.project(VH,sg);xz,yz,_,_,_=P.project(VZ,sg);m=(xz>0)&(xz<VZ['W'])&(yz>0)&(yz<VZ['H'])
            A+=list(zip(xh[m],yh[m]));B+=list(zip(xz[m],yz[m]))
    A,B=np.array(A),np.array(B);k=VZ['S']/VH['S'];t=(B-A*k).mean(0);err=float(np.abs(A*k+t-B).max())
    zoom['from_hero']=dict(k=round(k,5),tx=round(float(t[0]),1),ty=round(float(t[1]),1));print('zoom = hero*%.4f + (%.1f, %.1f), worst %.2f pt on %d run points'%(k,t[0],t[1],err,len(A)))
    runs={}
    for p in P.D['pistes']:
        if not p.get('named'):continue
        st=P.stats(p['key']);runs[p['key']]=dict(name=p.get('name') or p['key'],color=p['color'],ref=(p.get('refs') or [''])[0],len=p['len'],drop=st['drop'],top=st['top'],bot=st['bot'],steep=st['steep'],
                                                 near=bool(zoom['sel'].get(p['key'],{}).get('seen',0)>=0.85))
    lights=[dict(name=l,hour=P.LIGHTS[l]['h'],alt=round(P.sun(P.LIGHTS[l]['h'])[0],1),az=round(P.sun(P.LIGHTS[l]['h'])[1],1),night=l in NIGHT) for l in LIGHTS]
    # points to check the formula by eye and by number: lift stations (lat, lon, elevation of the model as shipped)
    chk=[]
    for nm in('Goodaura','Pirveli','Soliko','Kudebi','Sadzele','Shino','Kobi 1'):
        l=[q for q in P.D['lifts'] if q.get('name')==nm]
        if not l:continue
        for tag,g in(('bottom',l[0]['g'][0]),('top',l[0]['g'][-1])):
            e,n=P.EN(g);z=float(elev_raw(e,n));row=dict(what='%s, %s end of the line'%(nm,'first' if tag=='bottom' else 'last'),lat=g[0],lon=g[1],z=round(z,1))
            for vn,V in(('hero',VH),('zoom',VZ)):
                u,d=P.to_ud(V,e,n);x,y=P.screen(V,u,d,z);vis=bool(P.visible(V,np.array([u]),np.array([d]),np.array([float(P.elev(e,n))]))[0])
                row[vn]=dict(x=round(float(x),1),y=round(float(y),1),seen=bool(vis and 0<=x<=V['W'] and 0<=y<=V['H']))
            chk.append(row)
    doc=dict(schema=0,note='POC handoff (decision 85). Units: points of the picture; the picture is 3 px per point. Not the shared contract.',
             sun_date='2027-01-12',tz_hours=4,geo=dict(lat0=lat0,lon0=lon0,kx=round(kx,3),ky=ky),lights=lights,
             slope=[[t,c] for t,c in P.SLOPE],pal=P.PAL,views=dict(hero=hero,zoom=zoom),runs=runs,check=chk)
    f=os.path.join(OUT,'peak-poc.json');json.dump(doc,open(f,'w',encoding='utf8'),ensure_ascii=False,separators=(',',':'))
    print('peak-poc.json %d KB'%(os.path.getsize(f)//1024),'pistes',len(hero['pistes']),'lifts',len(hero['lifts']),'selectable',len(runs),'near',sum(r['near'] for r in runs.values()))
    for r in chk:print(r['what'],r['z'],r['hero'],r['zoom'])
if __name__=='__main__':main()
