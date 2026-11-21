#!/usr/bin/env python3
"""Handoff for the live model of the mountain (stage B of the redesign): what the 3D engine needs so that the real
terrain looks like the painted mountain of round 3, at any hour and from any angle.
Writes design/handoff/2026-11-20-redesign-live/ :
  tex/relief.webp   world map of the snow surface: R,G = slope east and south, B = occlusion   (hour independent)
  tex/cover.webp    world map of what covers the ground: R = rock potential, G = groomed run, B = rock tone
  tex/tile.webp     repeating fine relief: R,G = slope of ripples and grain, B = grain
  tex/horizon.webp  the real far horizon around Gudauri (from the same open elevation source): R,G = slope, B = distance
  tex/cirrus.webp   high cloud, painted (no data): one channel
  peak-live.json   lights by the sun's height, haze, shader constants, the opening camera, the village lights, checks
Usage: PEAK_CACHE=<dir> python3 design/redesign/tools/live_export.py [--far]     (--far fetches the wide elevation tiles)
Nothing invented: the ground is the shipped elevation model; the far horizon is real terrain; clouds are paint."""
import os,sys,json,math,io,urllib.request
import numpy as np
from PIL import Image,ImageDraw
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import peak as P
from mountain import ROOT,dm,elev,blur,sstep,PISTES,kx,ky,lat0,lon0,Z0
OUT=os.path.join(ROOT,'design/handoff/2026-11-20-redesign-live');os.makedirs(os.path.join(OUT,'tex'),exist_ok=True)
N=int(os.environ.get('LIVE_N','1024'))
X0,X1,W0,W1=dm['x0'],dm['x1'],dm['y0'],dm['y1']          # world: x east, z = -north; row 0 of the model is its north edge
GX,GZ=(X1-X0)/N,(W1-W0)/N;G=(GX+GZ)/2
rng=lambda s:np.random.default_rng(s)
def vnoise(shape,cell,seed):return P.vnoise(shape,max(1,int(cell)),seed)
def tnoise(n,cx,cz,seed):
    """tileable value noise, n x n, with cx by cz cells"""
    r=rng(seed).random((cz,cx)).astype(np.float32);r=np.pad(r,2,mode='wrap')
    big=np.asarray(Image.fromarray(r,mode='F').resize((int(round(n*(cx+4)/cx)),int(round(n*(cz+4)/cz))),Image.BICUBIC))
    ox,oz=int(round(2*n/cx)),int(round(2*n/cz));return big[oz:oz+n,ox:ox+n]
def q8(a,dither=True,seed=0):
    a=np.clip(a,0,1)*255
    if dither:a=a+rng(90+seed).random(a.shape).astype(np.float32)-.5
    return np.clip(np.round(a),0,255).astype(np.uint8)
def save(name,chans,**kw):
    im=Image.fromarray(np.dstack(chans) if len(chans)>1 else chans[0]);p=os.path.join(OUT,'tex',name)
    im.save(p,**(dict(quality=kw.get('q',92),method=6) if name.endswith('.webp') else dict(optimize=True)))
    print(name,im.size,os.path.getsize(p)//1024,'KB');return im

def world():
    e=X0+(np.arange(N)+.5)*GX;w=W0+(np.arange(N)+.5)*GZ;E,Wz=np.meshgrid(e.astype(np.float32),w.astype(np.float32))
    z=elev(E,-Wz).astype(np.float32);sh=z.shape
    # groomed runs, 28 m wide (a symbol: the real width is not in the data)
    im=Image.new('L',(N,N),0);dr=ImageDraw.Draw(im)
    for p in PISTES:
        if p['named'] and p['kind']=='run':
            for s in p['segs']:
                q=np.array(s);xy=list(zip(((q[:,0]-X0)/GX).tolist(),((-q[:,1]-W0)/GZ).tolist()))
                if len(xy)>1:dr.line(xy,fill=255,width=max(1,int(round(28/G))),joint='curve')
    groom=np.clip(blur(np.asarray(im,dtype=np.float32)/255,10,G)*1.25,0,1)
    zb=blur(z,44,G);zs=zb+0.9*(zb-blur(z,420,G))
    gz_,gx_=np.gradient(zb,GZ,GX);slope=np.degrees(np.arctan(np.hypot(gx_,gz_)))
    amp=(1-0.85*groom)*(1-sstep(30,40,slope))*0.35
    zs=zs+(vnoise(sh,64/G,11)-.5)*2.6*0.6*amp-groom*0.8          # the big drifts; a groomed run sits a little lower
    dz,dx=np.gradient(zs,GZ,GX)
    c1=z-blur(z,60,G);c2=z-blur(z,300,G);occ=1+np.clip(c1/26,-1,1)*0.08+np.clip(c2/130,-1,1)*0.10
    save('relief.webp',[q8(.5+dx/3.2,seed=1),q8(.5+dz/3.2,seed=2),q8((occ-.7)/.6,seed=3)])
    # rock: too steep to hold snow, streaked along the fall line
    gn=np.hypot(gx_,gz_)+1e-5;fu,fd=gx_/gn,gz_/gn
    base=vnoise(sh,1,31) if G>5 else vnoise(sh,5/G,31);rr,cc=np.mgrid[0:N,0:N].astype(np.float32);streak=np.zeros(sh,dtype=np.float32)
    st=max(1.0,3*1.95/G)
    for i in range(-5,6):
        streak+=base[np.clip((rr+fd*i*st).astype(np.int32),0,N-1),np.clip((cc+fu*i*st).astype(np.int32),0,N-1)]
    streak/=11;del rr,cc,base
    pot=slope+(streak-.5)*17+(vnoise(sh,90/G,32)-.5)*8+(vnoise(sh,22/G,34)-.5)*5
    strata=0.82+0.36*vnoise(sh,34/G,33)*np.clip(occ,0.8,1.15);tone=strata*(0.72+0.56*streak)
    save('cover.webp',[q8((pot-18)/30,seed=4),q8(groom,False),q8(tone/2,seed=5)])
    return z,slope,groom,occ,pot,tone,dx,dz

def tile(n=512,period=640.0):
    g=period/n
    h=(tnoise(n,27,27,12)-.5)*0.9*0.35+(tnoise(n,80,80,13)-.5)*0.26+(tnoise(n,14,91,3)-.5)*0.42+(tnoise(n,9,46,4)-.5)*0.5   # 24 m, 8 m, and wind ripples: 46 x 7 m, 70 x 14 m
    h=h*0.35
    dx=(np.roll(h,-1,1)-np.roll(h,1,1))/(2*g);dz=(np.roll(h,-1,0)-np.roll(h,1,0))/(2*g)
    # rock grain as fine streaks along z (north-south); read the tile with x and z swapped for streaks along x
    streak=np.zeros((n,n),dtype=np.float32);base=tnoise(n,256,256,31)
    for i in range(-9,10):streak+=np.roll(base,i,0)
    streak=streak/19;streak=np.clip(.5+(streak-streak.mean())/(4*streak.std()),0,1)
    save('tile.webp',[q8(.5+dx*2,seed=6),q8(.5+dz*2,seed=7),q8(streak,False)],q=95)
    return dict(px=n,period_m=period,slope_range=0.25)

# ---------------------------------------------------------------- the far horizon, from real terrain
CACHE=os.environ.get('PEAK_CACHE','/tmp/peak-cache');FAR=os.path.join(CACHE,'far.npy');FZ=10
def fetch_far(half=62000.0,cell=120.0):
    """a wide elevation grid around Gudauri from the open terrain tiles (the source of the shipped model): metres, north up"""
    n=2**FZ;tx=lambda lon:(lon+180)/360*n;ty=lambda lat:(1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*n
    la0,la1=lat0-half/ky-.05,lat0+half/ky+.05;lo0,lo1=lon0-half/kx-.08,lon0+half/kx+.08
    xa,xb=int(tx(lo0)),int(tx(lo1));ya,yb=int(ty(la1)),int(ty(la0))
    mos=np.zeros(((yb-ya+1)*256,(xb-xa+1)*256),dtype=np.float32)
    for y in range(ya,yb+1):
        for x in range(xa,xb+1):
            f=os.path.join(CACHE,'t%d_%d_%d.png'%(FZ,x,y))
            if not os.path.exists(f):
                urllib.request.urlretrieve('https://s3.amazonaws.com/elevation-tiles-prod/terrarium/%d/%d/%d.png'%(FZ,x,y),f)
            a=np.asarray(Image.open(f).convert('RGB'),dtype=np.float32)
            mos[(y-ya)*256:(y-ya+1)*256,(x-xa)*256:(x-xa+1)*256]=a[...,0]*256+a[...,1]+a[...,2]/256-32768
    m=int(2*half/cell)+1;e=np.linspace(-half,half,m);nn=np.linspace(half,-half,m);E,Nn=np.meshgrid(e,nn)
    lat=lat0+Nn/ky;lon=lon0+E/(111320*np.cos(np.radians(lat)))                     # true longitudes this far out
    px=(lon+180)/360*n*256-xa*256;py=(1-np.arcsinh(np.tan(np.radians(lat)))/math.pi)/2*n*256-ya*256
    c0=np.clip(px.astype(int),0,mos.shape[1]-2);r0=np.clip(py.astype(int),0,mos.shape[0]-2);fc=px-c0;fr=py-r0
    z=(mos[r0,c0]*(1-fc)+mos[r0,c0+1]*fc)*(1-fr)+(mos[r0+1,c0]*(1-fc)+mos[r0+1,c0+1]*fc)*fr
    np.save(FAR,z.astype(np.float32));print('far model',z.shape,'max',z.max(),'at',np.unravel_index(z.argmax(),z.shape))
def horizon(eye,w=4096,h=512,lo=-5.0,hi=6.0,half=62000.0,cell=120.0,ss=2):
    """panorama of the terrain outside the shipped model, seen from eye=(east,north,height): per pixel the slope of the
    ground there (east, south) and how far it is. Column 0 looks north, going clockwise; row 0 is `hi` degrees up."""
    z=np.load(FAR);m=z.shape[0]
    # lone spikes in the open data (voids): a cell far above all its neighbours is not a mountain
    nb=np.stack([np.roll(np.roll(z,i,0),j,1) for i in (-1,0,1) for j in (-1,0,1) if i or j]);z=np.where(z-nb.max(0)>60,np.median(nb,0),z)
    zsm=blur(z,2.2*cell,cell);dz,dx=np.gradient(zsm,cell,cell)                    # rows go south
    W,Hh=w*ss,h*ss;az=(np.arange(W)+.5)/W*2*math.pi;sa,ca=np.sin(az),np.cos(az)
    ds=[];d=1500.0
    while d<half*0.98:ds.append(d);d+=max(40.0,d*0.004)
    ds=np.array(ds,dtype=np.float32);ang=np.empty((len(ds),W),dtype=np.float32);gxs=np.empty_like(ang);gzs=np.empty_like(ang)
    for i,d in enumerate(ds):
        e=eye[0]+sa*d;n_=eye[1]+ca*d
        c=np.clip((e+half)/cell,0,m-1.001);r=np.clip((half-n_)/cell,0,m-1.001);c0=c.astype(int);r0=r.astype(int);fc=c-c0;fr=r-r0
        bil=lambda a:(a[r0,c0]*(1-fc)+a[r0,c0+1]*fc)*(1-fr)+(a[r0+1,c0]*(1-fc)+a[r0+1,c0+1]*fc)*fr
        inside=(e>X0)&(e<X1)&(-n_>W0)&(-n_<W1)                                       # the live mesh draws that part itself
        zz=bil(z)-d*d/(2*6371000.0*1.15)                                             # curvature, with standard refraction
        ang[i]=np.where(inside,-90,np.degrees(np.arctan2(zz-eye[2],d)));gxs[i]=bil(dx);gzs[i]=bil(dz)
    top=np.maximum.accumulate(ang,axis=0);rows=hi-(np.arange(Hh)+.5)/Hh*(hi-lo)
    R=np.full((Hh,W),.5,dtype=np.float32);Gc=np.full((Hh,W),.5,dtype=np.float32);B=np.ones((Hh,W),dtype=np.float32)
    for j in range(W):
        t=top[:,j];idx=np.searchsorted(t,rows,side='left');ok=idx<len(ds);ii=np.minimum(idx,len(ds)-1);i0=np.maximum(ii-1,0)
        f=np.clip((rows-t[i0])/np.maximum(t[ii]-t[i0],1e-4),0,1);f=np.where(t[ii]-t[i0]>0.25,1.0,f)      # a new ridge: no blend across the gap
        dd=ds[i0]+(ds[ii]-ds[i0])*f
        R[:,j]=np.where(ok,.5+gxs[ii,j]/3.2,.5);Gc[:,j]=np.where(ok,.5+gzs[ii,j]/3.2,.5);B[:,j]=np.where(ok,dd/half*0.98,1.0)
    dn=lambda a:np.asarray(Image.fromarray(a,mode='F').resize((w,h),Image.BOX))
    save('horizon.webp',[q8(dn(R),False),q8(dn(Gc),False),q8(dn(B),False)],q=95)
    return dict(w=w,h=h,lo_deg=lo,hi_deg=hi,eye=[round(float(v),1) for v in eye],far_m=half,note='col 0 = north, clockwise; row 0 = hi_deg above the horizontal; B = distance / far_m * 0.98, 1 = sky')
def cirrus(w=2048,h=256):
    """painted high cloud over the whole sky ring (tileable in azimuth): long soft streaks, thinning to nothing"""
    st=np.pad(rng(52).random((9,10)).astype(np.float32),((2,2),(2,2)),mode='wrap')
    st=np.asarray(Image.fromarray(st,mode='F').resize((int(w*14/10),int(h*13/9)),Image.BICUBIC))[int(h*2/9):int(h*2/9)+h,int(w*2/10):int(w*2/10)+w]
    a=st*(0.55+0.9*tnoise(w,24,24,51)[:h])*(0.6+0.8*tnoise(w,7,7,53)[:h])
    m=sstep(0.22,0.70,a);save('cirrus.webp',[q8(m,False)],q=95)
    return dict(w=w,h=h,lo_deg=0.0,hi_deg=24.0,note='col 0 = north, clockwise; row 0 = hi_deg; repeat in azimuth, clamp in height')

CAM=dict(tx=round(P.CE+110,1),ty=2350.0,tz=round(-(P.CN+250),1),dist=7100.0,yaw_deg=0.0,pitch_deg=16.0,fov_deg=50.0)   # OrbitCamera terms
def main():
    pr,dd=math.radians(CAM['pitch_deg']),CAM['dist']
    eye=(CAM['tx'],-(CAM['tz']+math.cos(pr)*dd),CAM['ty']+math.sin(pr)*dd)                # east, north, height of the opening eye
    if '--far' in sys.argv or not os.path.exists(FAR):fetch_far()
    world();t=tile();hz=horizon(eye);ci=cirrus()
    L=[]
    for k in ['dawn','morning','noon','after','sunset','blue','night']:
        l=P.LIGHTS[k];r3=lambda c:[round(float(v),3) for v in c]
        L.append(dict(name=k,hour=l['h'],alt=round(l['alt'],1),az=round(l['az'],1),side='am' if l['h']<13 else 'pm',lit=r3(l['lit']),shade=r3(l['shade']),rock=r3(l['rock']),rockS=r3(l['rockS']),
                      zen=r3(l['zen']),hor=r3(l['hor']),band=r3(l['band']) if l['band'] else None,direct=l['direct'],stars=l['stars']))
    # checks: what a texel reads once it is on the GPU (catches a flipped or swapped load); all five are lossy WebP, so +-12
    chk=[]
    for name in ['relief.webp','cover.webp','tile.webp','horizon.webp']:
        a=np.asarray(Image.open(os.path.join(OUT,'tex',name)).convert('RGB'));hh,ww=a.shape[:2]
        for u,v in [(0.25,0.25),(0.5,0.5),(0.62,0.71)]:chk.append(dict(tex=name,u=u,v=v,rgb=[int(c) for c in a[int(v*hh),int(u*ww)]]))
    vill=[]
    for b in P.BLD['b']:
        q=np.array(b['p']);x,z=float(q[:,0].mean()),float(q[:,1].mean());vill.append([round(x,1),round(float(elev(x,-z))+(b.get('h') or 9)*0.6,1),round(z,1)])
    o=dict(schema=0,note='designer handoff for the live model, not the shared contract',
           world=dict(x0=X0,x1=X1,z0=W0,z1=W1,note='x east, y up (metres, no exaggeration), z = -north: as MapScene. uv = ((x-x0)/(x1-x0), (z-z0)/(z1-z0)), v=0 at the north edge'),
           tex=dict(relief=dict(file='relief.webp',px=N,decode='slope_east=(R-0.5)*3.2; slope_south=(G-0.5)*3.2; occlusion=0.7+0.6*B; normal=normalize(-slope_east,1,-slope_south)'),
                    cover=dict(file='cover.webp',px=N,decode='rock_potential_deg=18+30*R; groomed=G; rock_tone=2*B'),
                    tile=dict(t,file='tile.webp',decode='slope_east=(R-0.5)*0.5; slope_south=(G-0.5)*0.5; uv=xz/period_m, repeat. B = rock streaks along z, read at xz/160 (and at zx/160 for streaks along x)'),
                    horizon=dict(hz,file='horizon.webp'),cirrus=dict(ci,file='cirrus.webp')),
           camera=CAM,
           k=dict(ripple=1.6,haze=dict(start_x_dist=1.0,length_x_dist=0.43,max=0.72,valley=0.26,valley_from=2300,valley_to=1750),
                  far_air=dict(at_9km=0.55,more=0.30,length_m=26000),sky_top_deg=11,band=dict(centre_deg=4.5,width_deg=3.2,strength=0.75),
                  glow=dict(full_below_alt=4,none_above_alt=7,from_m=2350,to_m=3000,low=0.35),shadow=dict(strength=0.94,blur='3x3 (1 2 1)')),
           lights=L,village=vill,check=chk)
    json.dump(o,open(os.path.join(OUT,'peak-live.json'),'w'),ensure_ascii=False,separators=(',',':'))
    print('json',os.path.getsize(os.path.join(OUT,'peak-live.json'))//1024,'KB')
if __name__=='__main__':main()
