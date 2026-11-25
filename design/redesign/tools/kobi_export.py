#!/usr/bin/env python3
"""Handoff: the Kobi side as a picture (Pini, 25.11.2026: "I want the Kobi side in the picture view too").
Same painter and the same seven lights as the front (peak.py, poc_export.py); the view looks south from
the north, on the target of the live Kobi view (live handoff, section 10).
Writes design/handoff/2026-11-25-redesign-kobi-picture/ :
  img/kobi-<light>.webp   seven pictures, 1.5 px per point (585x1266), at most 350 KB together; downloaded from the site, not packed
  peak-kobi.json          one view in the shape of views.hero of peak-poc.json, plus the projection (for peak.json)
                          and check points (for tools/fixtures/peak-projection.json)
Usage: PEAK_CACHE=<dir> python3 design/redesign/tools/kobi_export.py [--img]"""
import os,sys,json
import numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import peak as P
import poc_export as X
from mountain import elev_raw
OUT=os.path.join(P.ROOT,'design/handoff/2026-11-25-redesign-kobi-picture');os.makedirs(os.path.join(OUT,'img'),exist_ok=True)
PX,Q=1.5,74                          # 585x1266: the architect caps the set at 350 KB (25.11.2026)

def images():
    tot=0
    for l in X.LIGHTS:
        V,im=P.render('kobi',l,out=2);P.save(V,'kobi');im=im.resize((round(390*PX),round(844*PX)),P.Image.LANCZOS);f=os.path.join(OUT,'img','kobi-%s.webp'%l);im.save(f,quality=Q,method=6)
        tot+=os.path.getsize(f);print(l,im.size,os.path.getsize(f)//1024)
    print('pictures: %d KB'%(tot//1024))

def main():
    if '--img' in sys.argv:images()
    V,o=X.view('kobi');o['px']=PX;o['images']={l:'img/kobi-%s.webp'%l for l in X.LIGHTS}
    V0=P.VIEWS['kobi']
    # the projection for the shared contract (architect, 20.11.2026, condition 3): one entry of site/data/peak.json
    import hashlib;h=hashlib.sha256()
    for l in X.LIGHTS:h.update(open(os.path.join(OUT,'img','kobi-%s.webp'%l),'rb').read())
    rev=h.hexdigest()[:10]                # the set and its tables go together (architect, 25.11.2026: site/img/peak/kobi/<rev>/)
    o['contract']=dict(id='kobi',packaged=False,rev=rev,path='img/peak/kobi/%s/'%rev,images={l:'kobi-%s.webp'%l for l in X.LIGHTS},az=V0['az'],S=V0['S'],c=X.R1(V0['c']),near=V0['near'],W=V0['W'],H=V0['H'],y_c=V0['y_c'],zref=round(V['zref'],2),step=10,
                       F=[round(float(x),2) for x in V['F']],C=[round(float(x),4) for x in V['C']])
    o['labels']=[]                       # names of the Kobi side come from the data at run time (Kobi Pass, the village of Kobi); none baked here
    chk=[]
    for nm in('Kobi','Truso'):
        l=[q for q in P.D['lifts'] if q.get('name')==nm][0]
        for tag,g in(('first',l['g'][0]),('last',l['g'][-1])):
            e,n=P.EN(g);z=float(elev_raw(e,n));u,d=P.to_ud(V,e,n);x,y=P.screen(V,u,d,z)
            vis=bool(P.visible(V,np.array([u]),np.array([d]),np.array([float(P.elev(e,n))]))[0])
            chk.append(dict(what='%s, %s end of the line'%(nm,tag),lat=g[0],lon=g[1],z=round(z,1),kobi=dict(x=round(float(x),1),y=round(float(y),1),seen=bool(vis and 0<=x<=V['W'] and 0<=y<=V['H']))))
    for nm in('Sadzele','Goodaura'):      # front side: behind the ridge, or squeezed at its top
        l=[q for q in P.D['lifts'] if q.get('name')==nm][0];g=l['g'][-1];e,n=P.EN(g);z=float(elev_raw(e,n));u,d=P.to_ud(V,e,n);x,y=P.screen(V,u,d,z)
        vis=bool(P.visible(V,np.array([u]),np.array([d]),np.array([float(P.elev(e,n))]))[0])
        chk.append(dict(what='%s, last end of the line (front side)'%nm,lat=g[0],lon=g[1],z=round(z,1),kobi=dict(x=round(float(x),1),y=round(float(y),1),seen=bool(vis and 0<=x<=V['W'] and 0<=y<=V['H']))))
    seen={k:v['seen'] for k,v in o['sel'].items()}
    doc=dict(schema=0,note='Kobi side handoff (25.11.2026). Same units and shape as views.hero of peak-poc.json; the picture is 1.5 px per point. contract is the entry for site/data/peak.json.',
             sun_date='2027-01-12',tz_hours=4,view=o,check=chk,seen=seen)
    f=os.path.join(OUT,'peak-kobi.json');json.dump(doc,open(f,'w',encoding='utf8'),ensure_ascii=False,separators=(',',':'))
    print('peak-kobi.json %d KB'%(os.path.getsize(f)//1024));print('seen:',sorted(seen.items(),key=lambda t:-t[1])[:6]);print(chk)

if __name__=='__main__':main()
