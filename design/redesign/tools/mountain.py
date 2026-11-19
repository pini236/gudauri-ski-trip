#!/usr/bin/env python3
"""The mountain as a pre-rendered picture, from the real elevation model only (site/data).
No live 3D: a parallel (axonometric) view, so every pixel is at a known place and the
runs, lifts and the skier are drawn over it as vectors with one formula:

    sx = W/2 + S*u                       u = metres to the right of the view axis
    sy = Y0 - S*(d*sin(p) + (z-Z0)*cos(p))   d = metres ahead of the near edge, z = elevation

No vertical exaggeration. Geometry is never invented: light is styled, the ground is not.

Usage: python3 design/redesign/tools/mountain.py [view ...]
Writes design/redesign/assets/mtn-*.jpg, mtn-*.svg and prints a digest (label pixels)."""
import json,base64,struct,math,os,sys
import numpy as np
from PIL import Image
ROOT=os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT=os.path.join(ROOT,'design/redesign/assets')
D=json.load(open(os.path.join(ROOT,'site/data/runs-and-lifts.json')))
T=json.load(open(os.path.join(ROOT,'site/data/terrain.json')))
lat0,lon0=T['proj']['lat0'],T['proj']['lon0'];kx=111320*math.cos(math.radians(lat0));ky=111320
EN=lambda g:((g[1]-lon0)*kx,(g[0]-lat0)*ky)          # [lat,lon] -> (east, north) metres
dm=T['dem'];raw=base64.b64decode(dm['b64'])
Z0=np.array(struct.unpack('<%dh'%(len(raw)//2),raw),dtype=np.float32).reshape(dm['ny'],dm['nx'])
UP=4                                                  # bicubic upsample of the ~40 m model (smooth, nothing added)
ZU=np.asarray(Image.fromarray(Z0,mode='F').resize(((dm['nx']-1)*UP+1,(dm['ny']-1)*UP+1),Image.BICUBIC))
cs_x=(dm['x1']-dm['x0'])/(ZU.shape[1]-1);cs_y=(dm['y1']-dm['y0'])/(ZU.shape[0]-1)
def elev(e,n):
    """bilinear on the upsampled model; e,n arrays in metres (east, north)"""
    c=np.clip((np.asarray(e,dtype=np.float64)-dm['x0'])/cs_x,0,ZU.shape[1]-1.001);r=np.clip((-np.asarray(n,dtype=np.float64)-dm['y0'])/cs_y,0,ZU.shape[0]-1.001)
    c0=c.astype(int);r0=r.astype(int);fc=c-c0;fr=r-r0
    return (ZU[r0,c0]*(1-fc)+ZU[r0,c0+1]*fc)*(1-fr)+(ZU[r0+1,c0]*(1-fc)+ZU[r0+1,c0+1]*fc)*fr
def elev_raw(e,n):
    """bilinear on the model as shipped: the steepness numbers must match the site's"""
    sx=(dm['x1']-dm['x0'])/(dm['nx']-1);sy=(dm['y1']-dm['y0'])/(dm['ny']-1)
    c=np.clip((np.asarray(e,dtype=np.float64)-dm['x0'])/sx,0,dm['nx']-1.001);r=np.clip((-np.asarray(n,dtype=np.float64)-dm['y0'])/sy,0,dm['ny']-1.001)
    c0=c.astype(int);r0=r.astype(int);fc=c-c0;fr=r-r0
    return (Z0[r0,c0]*(1-fc)+Z0[r0,c0+1]*fc)*(1-fr)+(Z0[r0+1,c0]*(1-fc)+Z0[r0+1,c0+1]*fc)*fr
def site_slope(q,cum,step=40):
    """degrees per point, in 40 m pieces along the line (as the site colours a chosen run)"""
    ed=np.arange(0,cum[-1]+step,step);ee=np.interp(ed,cum,q[:,0]);nn=np.interp(ed,cum,q[:,1]);zz=elev_raw(ee,nn)
    deg=np.degrees(np.arctan(np.abs(np.diff(zz))/step));return deg[np.minimum((cum/step).astype(int),len(deg)-1)]
def blur(a,metres,g):
    k=max(1,int(metres/g));h,w=a.shape
    im=Image.fromarray(a.astype(np.float32),mode='F').resize((max(2,w//k),max(2,h//k)),Image.BOX).resize((w,h),Image.BICUBIC)
    return np.asarray(im)
def sstep(a,b,x):
    t=np.clip((x-a)/(b-a),0,1);return t*t*(3-2*t)

# ---------------------------------------------------------------- views
CE,CN=(44.4945-lon0)*kx,(42.4865-lat0)*ky             # centre of the Gudauri side (as in maps.py)
VIEWS={
 # name: look-toward bearing, pitch (90 = straight down), px per metre at 1x, centre, metres ahead/behind, canvas
 'south':dict(az=0,  pitch=33,S=0.128,c=(CE+110,CN+250),near=5350,far=7600,W=390,H=844,y_c=372),
 'plan': dict(az=0,  pitch=90,S=0.105,c=(CE,CN),      near=4080,far=3980,W=390,H=844,y_c=416),
 'kobi': dict(az=178,pitch=33,S=0.105,c=(CE+350,CN+4300),near=4500,far=7000,W=390,H=600,y_c=330),
 'zoom': dict(az=0,  pitch=33,S=0.25,c=(CE-486,CN-381),near=2700,far=3900,W=390,H=560,y_c=292,cont=(20,100)),      # the Tatra 2 side, closer
 'paper':dict(az=0,  pitch=33,S=0.128,c=(CE+110,CN+250),near=5350,far=7600,W=390,H=844,y_c=372,quant=50), # stacked 50 m layers
}
LIGHTS={
 # sun bearing/altitude; lit colour, shade colour, haze; rock lit/shade
 'day':  dict(az=286,alt=24,lit=(1.00,0.985,0.955),shade=(0.585,0.675,0.890),haze=(0.905,0.935,0.975),rock=(0.50,0.455,0.43),rockS=(0.30,0.32,0.42),amb=0.0),
 'dusk': dict(az=236,alt=9, lit=(1.00,0.80,0.70),  shade=(0.36,0.37,0.62),  haze=(0.80,0.70,0.80),   rock=(0.52,0.36,0.33),rockS=(0.22,0.22,0.36),amb=0.0),
 'night':dict(az=140,alt=38,lit=(0.33,0.42,0.62),  shade=(0.055,0.085,0.17),haze=(0.045,0.07,0.13),  rock=(0.12,0.15,0.24),rockS=(0.04,0.06,0.12),amb=0.0),
}
SS=2            # supersampling of the 2x output
def setup(v):
    V=dict(VIEWS[v]);az=math.radians(V['az']);V['f']=(math.sin(az),math.cos(az));V['r']=(math.cos(az),-math.sin(az))
    V['sp'],V['cp']=math.sin(math.radians(V['pitch'])),math.cos(math.radians(V['pitch']))
    V['zref']=float(elev(*V['c']))
    return V
def to_udz(V,e,n):
    e=np.asarray(e,dtype=np.float64)-V['c'][0];n=np.asarray(n,dtype=np.float64)-V['c'][1]
    return e*V['r'][0]+n*V['r'][1], e*V['f'][0]+n*V['f'][1]+V['near']
def zq(V,z):
    return np.floor(np.asarray(z)/V['quant'])*V['quant'] if V.get('quant') else z
def screen(V,u,d,z):
    """1x pixel coordinates"""
    z=zq(V,z)
    return V['W']/2+V['S']*u, V['y_c']-V['S']*((d-V['near'])*V['sp']+(z-V['zref'])*V['cp'])

def render(v,light):
    V=setup(v);L=LIGHTS[light];k=2*SS;W,H=V['W']*k,V['H']*k;S=V['S']*k
    g=1/S                                             # one cell = one output column
    PAD=40;Wo=W;W=W+2*PAD                              # margin, cropped after shading (no edge artefacts)
    nd=int((V['near']+V['far'])/g);u=(np.arange(W)-W/2+.5)*g;d=(np.arange(nd)+.5)*g
    U,Dd=np.meshgrid(u,d)
    E=V['c'][0]+U*V['r'][0]+(Dd-V['near'])*V['f'][0];N=V['c'][1]+U*V['r'][1]+(Dd-V['near'])*V['f'][1]
    z=elev(E,N).astype(np.float32)
    # ---- light (styled): normals from a slightly crisped surface; the ground itself is untouched
    zb=blur(z,48,g);zs=zb+0.75*(zb-blur(z,460,g))
    zd,zu=np.gradient(zs,g);nrm=np.sqrt(zu*zu+zd*zd+1)
    a,al=math.radians(L['az']),math.radians(L['alt']);sw=(math.sin(a)*math.cos(al),math.cos(a)*math.cos(al),math.sin(al))
    su=sw[0]*V['r'][0]+sw[1]*V['r'][1];sd=sw[0]*V['f'][0]+sw[1]*V['f'][1];sz=sw[2]
    lam=np.clip((-zu*su-zd*sd+sz)/nrm,0,1)
    # cast shadows on a coarse grid
    q=6;zc=np.asarray(Image.fromarray(z,mode='F').resize((W//q,nd//q),Image.BILINEAR));gc=g*q
    hh=math.hypot(su,sd) or 1e-6;du,dd=su/hh,sd/hh;depth=np.zeros_like(zc)
    for i in range(1,int(2600/gc)):
        ox,oy=int(round(du*i)),int(round(dd*i))
        if abs(ox)>=zc.shape[1] or abs(oy)>=zc.shape[0]:break
        sh=np.full_like(zc,-1e9)
        ys=slice(max(0,-oy),zc.shape[0]-max(0,oy));xs=slice(max(0,-ox),zc.shape[1]-max(0,ox))
        yd=slice(max(0,oy),zc.shape[0]-max(0,-oy));xd=slice(max(0,ox),zc.shape[1]-max(0,-ox))
        sh[ys,xs]=zc[yd,xd]-i*gc*math.tan(al)*(1/hh)*hh   # height of the ray above ground reference
        depth=np.maximum(depth,sh-zc)
    shadow=np.asarray(Image.fromarray(sstep(2,45,depth).astype(np.float32),mode='F').resize((W,nd),Image.BICUBIC))
    shadow=np.clip(shadow,0,1)
    direct=lam*(1-0.92*shadow)
    c1=z-blur(z,70,g);c2=z-blur(z,320,g)
    occ=1+np.clip(c1/30,-1,1)*0.07+np.clip(c2/140,-1,1)*0.09
    slope=np.degrees(np.arctan(np.hypot(*np.gradient(zb,g))))
    t=np.power(direct,0.9)[...,None]
    lit=np.array(L['lit'],dtype=np.float32);shd=np.array(L['shade'],dtype=np.float32)
    col=shd+(lit-shd)*t
    col*=(0.93+0.07*sstep(2000,3100,z))[...,None]            # a little brighter with height
    # rock where it is too steep to hold snow (same idea as the site's texture)
    rng=np.random.default_rng(7);noise=np.asarray(Image.fromarray(rng.random((nd//14+1,W//14+1)).astype(np.float32),mode='F').resize((W,nd),Image.BICUBIC))
    rk=sstep(28,39,slope+(noise-.5)*5)*0.78
    rcol=np.array(L['rockS'],dtype=np.float32)+(np.array(L['rock'],dtype=np.float32)-np.array(L['rockS'],dtype=np.float32))*t
    col=col*(1-rk[...,None])+rcol*rk[...,None]
    col*=occ[...,None]
    # contour lines every 100 m (stronger every 500): drawn, not invented
    gm=np.hypot(*np.gradient(z,g))+1e-4
    def cont(step,wpx):
        fr=np.abs(((z+step/2)%step)-step/2)/gm/g      # distance to the line, in cells
        return 1-sstep(wpx*0.5,wpx*0.5+1.2,fr)
    c_lo,c_hi=V.get('cont',(100,500))
    cl=np.maximum(cont(c_lo,1.6)*0.10,cont(c_hi,2.6)*0.2)*(1-rk)
    col=col*(1-cl[...,None])+np.array(L['shade'],dtype=np.float32)*0.55*cl[...,None]
    # aerial perspective: distance and depth of the valleys
    far=sstep(V['near']+2600,V['near']+V['far'],Dd).astype(np.float32)*(0.0 if V['pitch']>80 else 0.9)
    low=sstep(2250,1750,z)*0.22
    hz=np.array(L['haze'],dtype=np.float32)
    f=np.clip(far+low*(1-far),0,1)[...,None];col=col*(1-f)+hz*f
    # ---- projection: inverse map, per column, nearest visible cell
    if V.get('quant'):
        Q=V['quant'];zl=np.floor(z/Q)*Q;hgt=sstep(1850,3250,zl)[...,None]
        lo,hi=((0.74,0.80,0.89),(1.0,1.0,1.0)) if light!='night' else ((0.05,0.08,0.16),(0.30,0.38,0.56))
        if light=='dusk':lo,hi=(0.40,0.38,0.60),(1.0,0.84,0.76)
        pc=np.array(lo,dtype=np.float32)+(np.array(hi,dtype=np.float32)-np.array(lo,dtype=np.float32))*hgt
        pc*=(0.9+0.1*np.power(direct,0.9))[...,None]           # a breath of the same light
        foot=np.zeros_like(z);n=int(34/g)
        for i in range(1,n,2):
            w=(1-i/n)
            foot[:-i]=np.maximum(foot[:-i],(zl[i:]>zl[:-i])*w)                # a step rises behind
            foot[:,i:]=np.maximum(foot[:,i:],(zl[:,:-i]>zl[:,i:])*w*0.8)      # a step rises to the left (light from the left)
        pc*=(1-0.2*foot)[...,None]
        riser=np.zeros_like(z);riser[1:]=(zl[1:]>zl[:-1])
        edge=np.zeros_like(z);edge[:,1:]=(zl[:,1:]!=zl[:,:-1]);edge[1:]=np.maximum(edge[1:],zl[1:]<zl[:-1])
        pc=pc*(1-(0.2 if light!='night' else 0.34)*riser[...,None])*(1-0.1*edge[...,None])
        f2=np.clip(far,0,1)[...,None];col=pc*(1-f2)+hz*f2
        z=zl
    col=col[:,PAD:-PAD];z=z[:,PAD:-PAD];W=Wo
    sy=V['y_c']*k-S*((d[:,None]-V['near'])*V['sp']+(z-V['zref'])*V['cp'])
    run=np.minimum.accumulate(sy,axis=0)
    img=np.empty((H,W,3),dtype=np.float32);dep=np.full((H,W),-1,dtype=np.int32)
    rows=np.arange(H)+.5
    for j in range(W):
        rm=run[:,j]                                   # non-increasing with distance
        idx=np.searchsorted(-rm,-rows,side='left')    # first cell whose running top is above the pixel
        ok=idx<nd;ii=np.minimum(idx,nd-1)
        img[:,j]=np.where(ok[:,None],col[ii,j],hz);dep[:,j]=np.where(ok,ii,-1)
    # sky / far fade at the top
    if V['pitch']<80:
        gy=(sstep(0,0.16*H,rows)*(1-0.85*sstep(0.86*H,H,rows)))[:,None,None];top=hz*0.985
        img=img*gy+top*(1-gy)
    im=Image.fromarray((np.clip(img,0,1)*255).astype(np.uint8)).resize((V['W']*2,V['H']*2),Image.LANCZOS)
    return V,im,dep,k,g

def visible(V,dep,k,g,u,d,z):
    """is the ground point drawn (not behind a ridge)?"""
    x,y=screen(V,u,d,z);j=np.clip((x*k).astype(int),0,dep.shape[1]-1);r=np.clip((y*k).astype(int),0,dep.shape[0]-1)
    cell=dep[r,j];return (np.abs(cell-d/g)<14)&(x>-20)&(x<V['W']+20)

def dense(pts,step=12):
    o=[pts[0]]
    for a,b in zip(pts,pts[1:]):
        n=max(1,int(math.hypot(b[0]-a[0],b[1]-a[1])/step))
        for i in range(1,n+1):o.append((a[0]+(b[0]-a[0])*i/n,a[1]+(b[1]-a[1])*i/n))
    return o
def project(V,dep,k,g,pts,lift=0.0):
    q=np.array(dense(pts));u,d=to_udz(V,q[:,0],q[:,1]);z=elev(q[:,0],q[:,1])
    x,y=screen(V,u,d,z+(0 if V.get('quant') else lift));vis=visible(V,dep,k,g,u,d,z)
    return x,y,vis,z,q
def chunks(x,y,vis):
    out=[];cur=[]
    for a,b,v in zip(x,y,vis):
        if v:cur.append((a,b))
        else:
            if len(cur)>1:out.append(cur)
            cur=[]
    if len(cur)>1:out.append(cur)
    return out
def simp(c,tol=0.5):
    o=[c[0]]
    for p in c[1:-1]:
        if math.hypot(p[0]-o[-1][0],p[1]-o[-1][1])>tol*3:o.append(p)
    o.append(c[-1]);return o
pathd=lambda c:'M'+' L'.join('%.1f %.1f'%p for p in simp(c))

PISTES=[dict(key=p['key'],name=p.get('name') or p['key'],color=p['color'],named=bool(p.get('named')),kind=p.get('kind') or 'run',
             segs=[[EN(gp) for gp in s['g']] for s in p['segs'] if not s.get('area')],lit='yes' in (p.get('lit') or [])) for p in D['pistes']]
LIFTS=[dict(name=l['name'],kind=l['kind'],pts=[EN(gp) for gp in l['g']]) for l in D['lifts'] if l['name']]
SLOPE=[(15,'#3FA85F'),(25,'#F2C13D'),(30,'#F08A3C'),(91,'#DC3B33')]
PAL={'day':dict(green='#198549',blue='#1F6FD0',red='#D1342B',black='#15181C',case='#FFFFFF',wire='#1B2A3A',st='#FFFFFF'),
     'night':dict(green='#46D08A',blue='#62AEFF',red='#FF7468',black='#E6ECF2',case='#0A1120',wire='#C5D2E0',st='#0A1120')}
SEL='Tatra 2'
def overlay(V,dep,k,g,mode,variant):
    """variant: all | run (Tatra 2 chosen, by steepness) | corridor (mask for the lit corridor)"""
    c=PAL[mode];W,H=V['W'],V['H'];o=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linejoin="round" stroke-linecap="round">'%(W,H,W,H)]
    if variant=='corridor':
        p=[q for q in PISTES if q['key']==SEL][0];o.append('<filter id="b" x="-20%%" y="-20%%" width="140%%" height="140%%"><feGaussianBlur stdDeviation="%g"/></filter><g filter="url(#b)">'%(V['S']*52))
        for s in p['segs']:
            x,y,vis,z,q=project(V,dep,k,g,s)
            for ch in chunks(x,y,vis):o.append('<path d="%s" stroke="#fff" stroke-width="%g"/>'%(pathd(ch),V['S']*250))
        o.append('</g></svg>');return '\n'.join(o)
    dim=variant=='run'
    geo=[]
    for p in PISTES:
        for s in p['segs']:
            x,y,vis,z,q=project(V,dep,k,g,s);geo.append((p,chunks(x,y,vis),(x,y,vis,z,q)))
    if not dim and mode=='night':
        for p,chs,_ in geo:
            if p['lit']:
                for ch in chs:o.append('<path d="%s" stroke="#FFD08A" stroke-width="15" opacity=".22"/><path d="%s" stroke="#FFE2B0" stroke-width="8" opacity=".3"/>'%(pathd(ch),pathd(ch)))
    if not dim:
        for p,chs,_ in geo:
            if p['named'] and p['kind']=='run':
                for ch in chs:o.append('<path d="%s" stroke="%s" stroke-width="10" opacity=".16"/>'%(pathd(ch),c[p['color']]))
        for p,chs,_ in geo:
            if p['named']:
                for ch in chs:o.append('<path d="%s" stroke="%s" stroke-width="5" opacity=".95"/>'%(pathd(ch),c['case']))
    for p,chs,_ in geo:
        zf=max(1,V['S']/0.128)**0.6;w=(2.9 if p['named'] else 1.6)*zf
        da=' stroke-dasharray="1 5"' if (p['kind']=='ski-way' or not p['named']) else ''
        for ch in chs:
            if dim:o.append('<path d="%s" stroke="%s" stroke-width="%.1f" opacity=".5"%s/>'%(pathd(ch),c[p['color']],w*.62,da))
            else:o.append('<path d="%s" stroke="%s" stroke-width="%.1f"%s/>'%(pathd(ch),c[p['color']],w,da))
    for l in LIFTS:
        x,y,vis,z,q=project(V,dep,k,g,l['pts'],lift=9)
        op=' opacity=".45"' if dim else ''
        for ch in chunks(x,y,vis):o.append('<path d="%s" stroke="%s" stroke-width="1.15"%s/>'%(pathd(ch),c['wire'],op))
        n=len(x)
        for i in list(range(0,n,max(1,n//max(2,int(n*12/170)))))[1:-1]:      # towers, about every 170 m
            if vis[i]:o.append('<path d="M%.1f %.1f v3.2" stroke="%s" stroke-width="1"%s/>'%(x[i],y[i],c['wire'],op))
        for i in(0,n-1):
            if vis[i]:o.append('<rect x="%.1f" y="%.1f" width="6.4" height="6.4" rx="1.6" fill="%s" stroke="%s" stroke-width="1.3"%s/>'%(x[i]-3.2,y[i]-3.2,c['st'],c['wire'],op))
    if dim:
        p,chs,(x,y,vis,z,q)=[t for t in geo if t[0]['key']==SEL][0]
        for ch in chs:o.append('<path d="%s" stroke="%s" stroke-width="8.4"/>'%(pathd(ch),c['case']))
        # by steepness, in runs of equal class
        seg=[];dist=np.hypot(np.diff(q[:,0]),np.diff(q[:,1]))
        degs=site_slope(q,np.concatenate([[0],np.cumsum(dist)]))
        for i in range(len(x)-1):
            deg=degs[i]
            cc=[c2 for t,c2 in SLOPE if deg<t][0]
            if not(vis[i] and vis[i+1]):seg.append(None);continue
            if seg and seg[-1] and seg[-1][0]==cc:seg[-1][1].append((x[i+1],y[i+1]))
            else:seg.append([cc,[(x[i],y[i]),(x[i+1],y[i+1])]])
        for s in seg:
            if s:o.append('<path d="%s" stroke="%s" stroke-width="5.4"/>'%(pathd(s[1]),s[0]))
        # fall-line chevrons every ~260 m
        acc=0
        for i in range(1,len(x)-1):
            acc+=dist[i-1]
            if acc>260 and vis[i]:
                acc=0;ax,ay=x[i+1]-x[i-1],y[i+1]-y[i-1];h=math.hypot(ax,ay) or 1;ax/=h;ay/=h
                o.append('<path d="M%.1f %.1f L%.1f %.1f L%.1f %.1f" stroke="#fff" stroke-width="1.3"/>'%(x[i]-ax*1.6-ay*2.1,y[i]-ay*1.6+ax*2.1,x[i]+ax*1.3,y[i]+ay*1.3,x[i]-ax*1.6+ay*2.1,y[i]-ay*1.6-ax*2.1))
        o.append('<circle cx="%.1f" cy="%.1f" r="5" fill="%s" stroke="%s" stroke-width="2"/>'%(x[0],y[0],c['case'],'#6B3FD4' if mode=='day' else '#B79CFF'))
        o.append('<circle cx="%.1f" cy="%.1f" r="3.4" fill="%s"/>'%(x[-1],y[-1],'#6B3FD4' if mode=='day' else '#B79CFF'))
    o.append('</svg>');return '\n'.join(o)

def digest(V,dep,k,g,v):
    print('--',v,'labels (1x px), * = hidden behind a ridge')
    def pt(name,e,n,zz=None):
        u,d=to_udz(V,e,n);z=float(elev(e,n)) if zz is None else zz;x,y=screen(V,u,d,z);vv=bool(visible(V,dep,k,g,np.array([u]),np.array([d]),np.array([float(elev(e,n))]))[0])
        print('   %-24s %5.0f %5.0f %s %4.0f m'%(name,x,y,' ' if vv else '*',z))
    for l in LIFTS:
        ends=sorted((l['pts'][0],l['pts'][-1]),key=lambda p:float(elev(*p)))
        pt(l['name']+' bottom',*ends[0]);pt(l['name']+' top',*ends[1])
    for p in T['peaks']:pt('peak '+p['n'],p['x'],-p['y'],p['ele'])
    for p in T['env']['places']:pt('place '+str(p['n']),p['x'],-p['y'])
    for p in PISTES:
        if p['named'] and p['segs']:
            s=max(p['segs'],key=len);m=s[len(s)//2];pt('run '+p['name']+' mid',*m)

def strip(key=SEL,Wd=150,Ht=560):
    p=[q for q in PISTES if q['key']==key][0];q=np.array(dense(max(p['segs'],key=len),10));z=elev(q[:,0],q[:,1])
    if z[0]<z[-1]:q=q[::-1];z=z[::-1]
    ax=q[-1]-q[0];L=np.hypot(*ax);ax/=L;pr=np.array([ax[1],-ax[0]])       # skier's right when looking down the axis
    al=(q-q[0])@ax;la=(q-q[0])@pr
    sc=min((Ht-40)/L,(Wd-40)/(la.max()-la.min()+1));x=Wd/2-(la-(la.max()+la.min())/2)*sc;y=20+al*sc   # screen-left = skier's right when the top of the screen is the start; see README
    dist=np.concatenate([[0],np.cumsum(np.hypot(np.diff(q[:,0]),np.diff(q[:,1])))])
    segs=[];degs=site_slope(q,dist)
    for i in range(len(q)-1):
        deg=degs[i]
        cc=[c2 for t,c2 in SLOPE if deg<t][0]
        if segs and segs[-1][0]==cc:segs[-1][1].append((x[i+1],y[i+1]))
        else:segs.append([cc,[(x[i],y[i]),(x[i+1],y[i+1])],dist[i],deg])
    R=dict(segs=[(cc,pathd(pts)) for cc,pts,d0,deg in segs],ticks=[],W=Wd,H=Ht)
    print('-- strip',key,'len %.0f m, scale %.3f px/m, viewBox 0 0 %d %d'%(dist[-1],sc,Wd,Ht))
    for cc,pts,d0,deg in segs:print('   <path d="%s" stroke="%s"/>  from %.0f m'%(pathd(pts),cc,d0))
    for dz in range(int(z[0]//100*100),int(z[-1]),-100):
        i=int(np.argmin(np.abs(z-dz)));R['ticks'].append((dz,float(x[i]),float(y[i])));print('   elevation %d m at x %.0f y %.0f (%.0f m along)'%(dz,x[i],y[i],dist[i]))
    sl=list(degs)
    i=int(np.argmax(sl));print('   steepest %.0f deg at x %.0f y %.0f (%.0f m along, %d m)'%(sl[i],x[i],y[i],dist[i],z[i]))
    print('   start %.0f m, end %.0f m'%(z[0],z[-1]))
    for o in PISTES:                                                         # where other named runs touch it (within 60 m)
        if o['key']==key or not o['named']:continue
        for sg in o['segs']:
            for e in (sg[0],sg[-1]):
                dd=np.hypot(q[:,0]-e[0],q[:,1]-e[1]);i=int(np.argmin(dd))
                if dd[i]<60:print('   touches %s at x %.0f y %.0f (%.0f m along), side %s'%(o['name'],x[i],y[i],dist[i],'skier right' if ((np.array(sg[len(sg)//2])-q[i])@pr)>0 else 'skier left'))
    for l in LIFTS:
        for e in (l['pts'][0],l['pts'][-1]):
            dd=np.hypot(q[:,0]-e[0],q[:,1]-e[1]);i=int(np.argmin(dd))
            if dd[i]<200:print('   lift %s station at x %.0f y %.0f (%.0f m along, %.0f m away)'%(l['name'],x[i],y[i],dist[i],dd[i]))
    return R

if __name__=='__main__':
    os.makedirs(OUT,exist_ok=True)
    want=sys.argv[1:] or list(VIEWS)
    if 'strip' in want or not sys.argv[1:]:strip()
    want=[w for w in want if w in VIEWS]
    for v in want:
        lights=('day','dusk','night') if v=='south' else ('day','night')
        for li in lights:
            V,im,dep,k,g=render(v,li)
            im.save(os.path.join(OUT,'mtn-%s-%s.jpg'%(v,li)),quality=86)
            if li=='dusk':continue
            mode='night' if li=='night' else 'day'
            for var in ('all','run')+(('corridor',) if li=='day' else ()):
                if v=='kobi' and var!='all':continue
                if v=='paper' and var=='corridor':continue
                open(os.path.join(OUT,'mtn-%s-%s%s.svg'%(v,var,'' if var=='corridor' else '-'+mode)),'w').write(overlay(V,dep,k,g,mode,var))
            if li=='day':digest(V,dep,k,g,v)
