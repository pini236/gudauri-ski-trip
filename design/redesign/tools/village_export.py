#!/usr/bin/env python3
"""Handoff for the village, the shelf and the lenses (round 3: K17, K06, K07, K08).
Writes design/handoff/2026-11-24-redesign-village-shelf-lenses/ :
  img/vill-<light>.webp   the village picture in the seven light keys, 3 px per point (1170x1680)
  village.json            what the screen draws over it, already projected (points of the picture), the pins and the tags,
                          the pose of the live model that matches the picture, and the order of the shelf (to check a comparator)
Usage: PEAK_CACHE=<dir> python3 design/redesign/tools/village_export.py [--img]
Same format as peak-poc.json (poc_export.py). Not the shared contract."""
import os,sys,json,math
import numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import peak as P, poc_export as X
OUT=os.path.join(P.ROOT,'design/handoff/2026-11-24-redesign-village-shelf-lenses');os.makedirs(os.path.join(OUT,'img'),exist_ok=True)
R1=X.R1
def images():
    tot=0
    for l in X.LIGHTS:
        V,im=P.render('vill',l,out=3);P.save(V,'vill');f=os.path.join(OUT,'img','vill-%s.webp'%l);im.save(f,quality=84,method=6);tot+=os.path.getsize(f);print(l,im.size,os.path.getsize(f)//1024)
    print('pictures: %d KB'%(tot//1024))
def main():
    if '--img' in sys.argv:images()
    V,o=X.view('vill');o.pop('sel',None)
    pt=lambda e,n:R1(P.pt(V,e,n)[:2])
    nb={b['n']:b for b in P.BLD['b'] if b.get('n')}
    def bc(n):
        p=nb[n]['p'];return sum(q[0] for q in p)/len(p),-sum(q[1] for q in p)/len(p)
    # the stations inside the picture: the lower end of every lift that starts here, and the upper end of Pirveli
    st=[]
    for nm,end,up,dx,dy in(('Goodaura',0,True,30,-58),('New Goodaura',0,True,0,0),('Soliko',0,True,-28,30),('Zuma',0,False,-8,24),('Zuma',1,False,0,0),('Pirveli',1,False,0,0)):
        e,n=P.lift_ends(P.flift(nm))[end];x,y=pt(e,n)
        st.append(dict(lift=nm,end='bottom' if end==0 else 'top',x=x,y=y,z=round(float(P.elev(e,n))),inside=bool(0<=x<=V['W'] and 0<=y<=V['H']),
                       tag=dict(dx=dx,dy=dy,arrow=up) if (dx or dy) else None))
    o['stations']=st
    ar=P.BLD['areas'][0]['p'];pk=(sum(q[0] for q in ar)/len(ar),-sum(q[1] for q in ar)/len(ar))
    # the places of the base station (site/data/places.json, goodaura-bottom): food and cafes only
    pj=json.load(open(os.path.join(P.ROOT,'site/data/places.json'),encoding='utf8'));pl={p['id']:p for p in pj['places']}
    base=[s for s in pj['stations'] if s['id']=='goodaura-bottom'][0];food=[pl[x['p']] for x in base['places'] if pl[x['p']]['kind'] in('food','cafe')]
    en=[P.EN(p['ll']) for p in food];fc=(sum(e for e,_ in en)/len(en),sum(n for _,n in en)/len(en))
    def pin(kind,e,n,dx=0,dy=0,**kw):
        x,y=pt(e,n);return dict(kind=kind,x=round(x+dx,1),y=round(y+dy,1),**kw)
    o['pins']=[pin('tickets',*bc('Gondola Tickets'),dx=-6,dy=-12,src='buildings.json: Gondola Tickets'),
               pin('tickets2',*bc('Ski Pass Office'),src='buildings.json: Ski Pass Office (not on the board: one pin is enough)'),
               pin('food',*fc,count=len(food),src='places.json: goodaura-bottom, kinds food and cafe, centre of the %d places'%len(food)),
               pin('food_board',*bc('Alpic'),dx=8,dy=-10,src='where the board K17 put it (by the building Alpic)'),
               pin('parking',*pk,dy=-4,src='buildings.json: areas[0], parking'),
               pin('meet_soliko',*P.lift_ends(P.flift('Soliko'))[0],dx=2,dy=-12,src='the flag of a meetup at the bottom of Soliko (the example of the board)')]
    o['labels']=[dict(t=b['n'],x=pt(*bc(b['n']))[0],y=pt(*bc(b['n']))[1]) for b in P.BLD['b'] if b.get('n')]
    # the pose of the live model that lands on the picture (OrbitCamera, the target on the ground); fitted on nine points, worst 13 pt
    o['live_pose']=dict(tx=-420,ty=2109.7,tz=4780,dist=1150,yaw_deg=0,pitch_deg=36,fov_deg=50,note='fitted to this picture on nine points (stations and buildings): worst 13 points of 390')
    # ---- the shelf: the order of the cards in every sorting, to check a comparator
    D=P.D;L={l['name']:l for l in D['lifts'] if l.get('name')};rk={'green':0,'blue':1,'red':2,'black':3}
    lz={n:round(float(P.elev(*P.lift_ends(P.flift(n))[0]))) for n in L}
    import re
    nat=lambda s:[int(t) if t.isdigit() else t.lower() for t in re.split(r'(\d+)',s)]
    runs=[]
    for p in D['pistes']:
        if not p.get('named'):continue
        s=P.stats(p['key']);fl=p.get('fromLifts') or [];tl=p.get('toLifts') or []
        runs.append(dict(key=p['key'],name=p.get('name') or p['key'],color=p['color'],kind=p.get('kind') or 'run',ref=(p.get('refs') or [''])[0],len=p['len'],drop=s['drop'],steep=s['steep'],
                         group=fl[0] if fl else None,back=tl[0] if tl else None,back_min=(L[tl[0]].get('dur') if tl else None)))
    groups=sorted({r['group'] for r in runs if r['group']},key=lambda g:(lz[g],g))
    by_lift=[]
    for g in groups+[None]:
        by_lift.append(dict(lift=g,bottom_m=lz.get(g),runs=[r['key'] for r in sorted([r for r in runs if r['group']==g],key=lambda r:(rk[r['color']],nat(r['name'])))]))
    o2=dict(runs=runs,by_lift=by_lift,easy_to_hard=[r['key'] for r in sorted(runs,key=lambda r:(rk[r['color']],r['steep'],nat(r['name'])))],
            longest=[r['key'] for r in sorted(runs,key=lambda r:(-r['len'],nat(r['name'])))])
    doc=dict(schema=0,note='Handoff for the village, the shelf and the lenses (24.11.2026). Units of the village: points of the picture; the picture is 3 px per point. Same format as peak-poc.json. Not the shared contract.',
             lights=X.LIGHTS,village=o,shelf=o2)
    f=os.path.join(OUT,'village.json');json.dump(doc,open(f,'w',encoding='utf8'),ensure_ascii=False,separators=(',',':'))
    print('village.json %d KB'%(os.path.getsize(f)//1024),'pistes',len(o['pistes']),'lifts',len(o['lifts']),'details day',len(o['details']['day']),'night',len(o['details']['night']))
    for s in st:print(s)
    for p in o['pins']:print(p)
    for g in by_lift:print(g)
    print('easy',o2['easy_to_hard']);print('long',o2['longest'][:8])
if __name__=='__main__':main()
