#!/usr/bin/env python3
"""Round 3: the mountain as a landscape. Same idea as mountain.py (a picture drawn in advance
from the real elevation model, with live vectors over it), with a painter's care:

  * a "bent" parallel view: the pitch eases from 33 degrees over the resort to a few degrees
    far away, so the real ridges beyond stand up against a sky. Still one formula per point:
        sx = W/2 + S*u
        sy = Y0 - S*( F(d) + (z-Z0)*C(d) )      F, C = two small tables of the distance d
  * the sun where it really is over Gudauri on a January day (so shade on the picture is shade
    on the mountain), cast shadows, sheen, rock with fall-line streaks, wind-rippled snow,
    groomed runs pressed into the snow, valley mist, a sea of cloud, spindrift on the ridges.
  * small things from real data only: the 41 mapped buildings of New Gudauri, roads, rivers,
    lifts. No trees (no forest data here), no invented houses. Snow texture, mist, cloud and
    sky are painting, not measurement; the ground is never changed.

Usage: python3 design/redesign/tools/peak.py [view[:light[:weather]] ...]"""
import json,math,os,sys,datetime,pickle
import numpy as np
from PIL import Image,ImageDraw,ImageFilter
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from mountain import ROOT,OUT,D,T,dm,EN,elev,site_slope,blur,sstep,CE,CN,PISTES,LIFTS,dense,chunks,pathd,SLOPE
CACHE=os.environ.get('PEAK_CACHE','/tmp/peak-cache');os.makedirs(CACHE,exist_ok=True)
BLD=json.load(open(os.path.join(ROOT,'site/data/buildings.json')))
LAT,LON,TZ,DAY=42.51,44.495,4.0,datetime.date(2027,1,12)
def sun(h):
    """altitude, bearing of the sun over Gudauri at local hour h (12.1.2027)"""
    n=DAY.timetuple().tm_yday;dec=math.radians(-23.44*math.cos(math.radians(360/365*(n+10))))
    b=math.radians(360/364*(n-81));eot=9.87*math.sin(2*b)-7.53*math.cos(b)-1.5*math.sin(b)
    ha=math.radians(15*(h+(LON-TZ*15)/15+eot/60-12));phi=math.radians(LAT)
    alt=math.asin(math.sin(phi)*math.sin(dec)+math.cos(phi)*math.cos(dec)*math.cos(ha))
    az=math.atan2(math.sin(ha),math.cos(ha)*math.sin(phi)-math.tan(dec)*math.cos(phi))
    return math.degrees(alt),(math.degrees(az)+180)%360

NORTH=-dm['y0']                                        # the model ends 7.8 km north of the origin
VIEWS={
 # p: (pitch near, pitch far, where the easing starts, where it ends) in metres ahead of the near edge
 'hero': dict(az=0,S=0.128,c=(CE+110,CN+250),near=5350,far=NORTH-(CN+250)-60,W=390,H=844,y_c=452,p=(33,5.5,6500,10800)),
 'zoom': dict(az=0,S=0.25, c=(CE-486,CN-381),near=2700,far=3900,W=390,H=560,y_c=292,p=(33,33,1,2)),
 'vill': dict(az=0,S=0.62, c=(-430,-4470),   near=1050,far=1500,W=390,H=560,y_c=300,p=(33,33,1,2),haze=0.15),
 'wide': dict(az=0,S=0.150,c=(CE+110,CN+250),near=5350,far=NORTH-(CN+250)-60,W=1180,H=640,y_c=372,p=(33,5.5,6000,10800)),
}
def L_(h,lit,shade,rock,rockS,zen,hor,band=None,direct=1.0,stars=0,moon=None):
    alt,az=moon if moon else sun(h)
    return dict(h=h,alt=alt,az=az,lit=lit,shade=shade,rock=rock,rockS=rockS,zen=zen,hor=hor,band=band,direct=direct,stars=stars)
LIGHTS={
 'dawn':   L_(9.0, (1.00,0.66,0.56),(0.33,0.40,0.66),(0.60,0.36,0.32),(0.20,0.22,0.38),(0.30,0.44,0.72),(0.93,0.74,0.76),band=(0.98,0.70,0.72)),
 'morning':L_(10.6,(1.00,0.975,0.93),(0.44,0.57,0.88),(0.52,0.46,0.42),(0.25,0.28,0.42),(0.16,0.40,0.78),(0.80,0.89,0.98)),
 'noon':   L_(13.0,(1.00,0.99,0.97),(0.52,0.64,0.90),(0.50,0.46,0.44),(0.27,0.30,0.42),(0.14,0.38,0.80),(0.80,0.90,0.99)),
 'after':  L_(15.9,(1.00,0.93,0.82),(0.42,0.52,0.84),(0.56,0.44,0.38),(0.23,0.25,0.40),(0.17,0.36,0.72),(0.88,0.86,0.90)),
 'sunset': L_(17.35,(1.00,0.58,0.42),(0.30,0.33,0.60),(0.60,0.32,0.26),(0.17,0.18,0.34),(0.22,0.30,0.62),(0.90,0.62,0.68),band=(0.99,0.60,0.62)),
 'blue':   L_(18.3,(0.50,0.58,0.82),(0.26,0.33,0.60),(0.24,0.26,0.42),(0.12,0.14,0.28),(0.08,0.14,0.36),(0.40,0.44,0.70),direct=0.0,stars=0.25),
 'night':  L_(22.0,(0.44,0.54,0.76),(0.045,0.075,0.165),(0.13,0.16,0.26),(0.03,0.05,0.11),(0.012,0.025,0.075),(0.07,0.11,0.22),stars=1,moon=(34,138)),
 'flat':   L_(13.0,(0.86,0.88,0.92),(0.66,0.70,0.80),(0.44,0.44,0.47),(0.34,0.35,0.42),(0.72,0.75,0.80),(0.84,0.86,0.90),direct=0.25),
}
SS=2
def vnoise(shape,cell,seed):
    h,w=shape;r=np.random.default_rng(seed).random((h//cell+3,w//cell+3)).astype(np.float32)
    return np.asarray(Image.fromarray(r,mode='F').resize((w,h),Image.BICUBIC))
def gblur(a,r):
    """gaussian blur of a 0..1 float field"""
    return np.asarray(Image.fromarray((np.clip(a,0,1)*255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)),dtype=np.float32)/255
def setup(v):
    V=dict(VIEWS[v]);az=math.radians(V['az']);V['f']=(math.sin(az),math.cos(az));V['r']=(math.cos(az),-math.sin(az))
    V['zref']=float(elev(*V['c']));p0,p1,da,db=V['p']
    dt=np.arange(0,V['near']+V['far']+20,10.0);pp=np.radians(p0+(p1-p0)*sstep(da,db,dt))
    F=np.concatenate([[0],np.cumsum(np.sin(pp[:-1])*10)]);F-=np.interp(V['near'],dt,F)
    V['dt'],V['F'],V['C'],V['name']=dt,F,np.cos(pp),v
    return V
def to_ud(V,e,n):
    e=np.asarray(e,dtype=np.float64)-V['c'][0];n=np.asarray(n,dtype=np.float64)-V['c'][1]
    return e*V['r'][0]+n*V['r'][1], e*V['f'][0]+n*V['f'][1]+V['near']
def screen(V,u,d,z):
    return V['W']/2+V['S']*np.asarray(u), V['y_c']-V['S']*(np.interp(d,V['dt'],V['F'])+(np.asarray(z)-V['zref'])*np.interp(d,V['dt'],V['C']))

def grid_lines(V,g,W,nd,PAD,lines,width_m):
    """a soft mask on the (distance, across) grid for a set of polylines (east,north)"""
    im=Image.new('L',(W,nd),0);dr=ImageDraw.Draw(im)
    for pts,wm in lines:
        q=np.array(pts);u,d=to_ud(V,q[:,0],q[:,1]);xy=list(zip((u/g+W/2).tolist(),(d/g).tolist()))
        if len(xy)>1:dr.line(xy,fill=255,width=max(1,int((wm or width_m)/g)),joint='curve')
    return np.asarray(im,dtype=np.float32)/255

def render(v,light='morning',wx=None,ss=SS):
    """wx: None | 'fresh' | 'wind' | 'cloud' | 'storm'"""
    V=setup(v);L=LIGHTS['flat' if wx=='storm' else light];k=2*ss;Wo,H=V['W']*k,V['H']*k;S=V['S']*k;g=1/S
    PAD=40;W=Wo+2*PAD;nd=int((V['near']+V['far'])/g);u=(np.arange(W)-W/2+.5)*g;d=(np.arange(nd)+.5)*g
    U,Dd=np.meshgrid(u.astype(np.float32),d.astype(np.float32))
    E=V['c'][0]+U*V['r'][0]+(Dd-V['near'])*V['f'][0];N=V['c'][1]+U*V['r'][1]+(Dd-V['near'])*V['f'][1]
    z=elev(E,N).astype(np.float32);del E,N
    sh=(nd,W)
    # groomed runs: a mask on the ground, 28 m wide (a symbol; the real width is not in the data)
    groom=grid_lines(V,g,W,nd,PAD,[(s,None) for p in PISTES if p['named'] and p['kind']=='run' for s in p['segs']],28)
    groom=np.clip(blur(groom,10,g)*1.25,0,1)
    # sea of cloud: a soft white surface in the valleys (weather, drawn; lifted geometry only for the cloud itself)
    cloud=None
    if wx=='cloud':
        top=2130+90*(vnoise(sh,int(420/g),5)-.5)+40*(vnoise(sh,int(130/g),6)-.5)+14*(vnoise(sh,int(45/g),7)-.5)
        cloud=sstep(-6,22,top-z).astype(np.float32);zg=z;z=np.maximum(z,top.astype(np.float32));del top
    # ---- light: normals from a slightly crisped surface plus painted snow relief (ripples); ground untouched
    zb=blur(z,44,g);zs=zb+0.9*(zb-blur(z,420,g))
    slope=np.degrees(np.arctan(np.hypot(*np.gradient(zb,g))))
    amp=(1-0.85*groom)*(1-sstep(30,40,slope))*(0.35 if cloud is None else 1)
    rip=np.asarray(Image.fromarray(np.random.default_rng(3).random((nd//int(7/g)+3,W//int(46/g)+3)).astype(np.float32),mode='F').resize((W,nd),Image.BICUBIC))
    bump=(vnoise(sh,int(64/g),11)-.5)*2.6+(vnoise(sh,int(24/g),12)-.5)*0.9+(vnoise(sh,max(2,int(8/g)),13)-.5)*0.26+(rip-.5)*0.42
    if cloud is not None:bump=bump*(1-cloud)+cloud*((vnoise(sh,int(150/g),21)-.5)*46+(vnoise(sh,int(50/g),22)-.5)*14)
    zs=zs+bump*amp-groom*0.8 if cloud is None else zs+bump*np.maximum(amp,cloud)-groom*0.8*(1-cloud);del bump,rip     # a groomed run sits a little lower: its edges catch the light
    zd,zu=np.gradient(zs,g);nrm=np.sqrt(zu*zu+zd*zd+1);del zs
    a,al=math.radians(L['az']),math.radians(max(L['alt'],1.2));sw=(math.sin(a)*math.cos(al),math.cos(a)*math.cos(al),math.sin(al))
    su=sw[0]*V['r'][0]+sw[1]*V['r'][1];sd=sw[0]*V['f'][0]+sw[1]*V['f'][1];sz=sw[2]
    lam=np.clip((-zu*su-zd*sd+sz)/nrm,0,1)
    # sheen: the glaze of low sun on snow, toward the viewer (view direction for a 33 degree pitch)
    vu,vd,vz=0.0,-math.cos(math.radians(33)),math.sin(math.radians(33));hx,hy,hz_=su+vu,sd+vd,sz+vz;hn=math.sqrt(hx*hx+hy*hy+hz_*hz_)
    spec=np.power(np.clip((-zu*hx-zd*hy+hz_)/(nrm*hn),0,1),38)
    upf=1/nrm;del zu,zd
    # cast shadows (coarse grid, long reach for a low sun)
    q=4;zc=np.asarray(Image.fromarray(z,mode='F').resize((W//q,nd//q),Image.BILINEAR));gc=g*q
    hh=math.hypot(su,sd) or 1e-6;du,dd=su/hh,sd/hh;depth=np.zeros_like(zc);tan=math.tan(al)/math.cos(al)*math.cos(al)
    reach=min(12000,2200/max(math.tan(al),0.05))
    for i in range(1,int(reach/gc)):
        ox,oy=int(round(du*i)),int(round(dd*i))
        if abs(ox)>=zc.shape[1] or abs(oy)>=zc.shape[0]:break
        ys=slice(max(0,-oy),zc.shape[0]-max(0,oy));xs=slice(max(0,-ox),zc.shape[1]-max(0,ox))
        yd=slice(max(0,oy),zc.shape[0]-max(0,-oy));xd=slice(max(0,ox),zc.shape[1]-max(0,-ox))
        np.maximum(depth[ys,xs],zc[yd,xd]-i*gc*math.tan(al)/hh*hh-zc[ys,xs],out=depth[ys,xs])
    shadow=np.clip(np.asarray(Image.fromarray(sstep(1.5,26,depth).astype(np.float32),mode='F').resize((W,nd),Image.BICUBIC)),0,1)
    direct=lam*(1-0.94*shadow)*L['direct']+(1-L['direct'])*0.55*upf
    c1=z-blur(z,60,g);c2=z-blur(z,300,g)
    occ=1+np.clip(c1/26,-1,1)*0.08+np.clip(c2/130,-1,1)*0.10;del c1,c2
    t=np.power(direct,0.85)[...,None]
    lit=np.array(L['lit'],dtype=np.float32);shd=np.array(L['shade'],dtype=np.float32)
    # alpenglow: at a very low sun only the heights catch it
    if L['alt']<5 and L['direct']>0:t=t*(0.35+0.65*sstep(2350,3000,z))[...,None]
    col=shd*(0.86+0.14*upf[...,None]*1.0)+(lit-shd*0.86)*t
    col+=(spec*(1-shadow)*L['direct']*0.20)[...,None]*lit
    col*=(0.94+0.06*sstep(2000,3100,z))[...,None]
    col=col*(1+0.05*groom[...,None]*(1-0.5*t))                                  # the satin of a groomed run
    # rock: too steep to hold snow; streaked along the fall line, less of it after fresh snow
    gd,gu=np.gradient(zb,g);gn=np.hypot(gd,gu)+1e-5;fu,fd=gu/gn,gd/gn
    base=vnoise(sh,max(2,int(5/g)),31);rr,cc=np.mgrid[0:nd,0:W].astype(np.float32);streak=np.zeros(sh,dtype=np.float32)
    for sI in range(-5,6):
        streak+=base[np.clip((rr+fd*sI*3).astype(np.int32),0,nd-1),np.clip((cc+fu*sI*3).astype(np.int32),0,W-1)]
    streak/=11;del rr,cc,base,gd,gu,gn,fu,fd
    lim=(33,43) if wx=='fresh' else (28.5,38)
    rk=sstep(lim[0],lim[1],slope+(streak-.5)*17+(vnoise(sh,int(90/g),32)-.5)*8+(vnoise(sh,int(22/g),34)-.5)*5)*(0.9 if wx!='fresh' else 0.6)
    if cloud is not None:rk=rk*(1-cloud)
    strata=0.82+0.36*vnoise(sh,int(34/g),33)*np.clip(occ,0.8,1.15)
    rcol=(np.array(L['rockS'],dtype=np.float32)+(np.array(L['rock'],dtype=np.float32)-np.array(L['rockS'],dtype=np.float32))*t)*strata[...,None]*(0.72+0.56*streak[...,None])
    col=col*(1-rk[...,None])+rcol*rk[...,None];del rcol,strata,streak
    col*=occ[...,None]
    if cloud is not None:
        ccol=(shd*0.5+0.5)+(np.array([1,1,1],dtype=np.float32)-(shd*0.5+0.5))*np.power(direct,0.7)[...,None]
        col=col*(1-cloud[...,None])+ccol*cloud[...,None];del ccol
    # aerial perspective: far ridges go to the colour of the horizon, in steps of mist; valleys hold a thin haze
    hor=np.array(L['hor'],dtype=np.float32)
    p0,p1,da,db=V['p'];flat=p0==p1
    if flat:far=sstep(V['near']+2600,V['near']+V['far'],Dd)*0.55*V.get('haze',1)
    else:far=1-np.exp(-np.clip(Dd-(da-600),0,None)/3900.0)
    low=sstep(2300,1750,z)*(0.26 if wx!='storm' else 0.5)*(0 if cloud is not None else 1)*V.get('haze',1)
    wisp=vnoise(sh,int(260/g),41)
    f=np.clip(far+low*(0.6+0.8*wisp)*(1-far)+(0.22 if wx=='storm' else 0),0,0.97)[...,None];col=col*(1-f)+hor*f;del far,low,wisp
    # ---- projection: inverse map per column, nearest visible cell
    col=col[:,PAD:-PAD];zz=z[:,PAD:-PAD];W=Wo
    Fd=np.interp(d,V['dt'],V['F']).astype(np.float32)[:,None];Cd=np.interp(d,V['dt'],V['C']).astype(np.float32)[:,None]
    sy=V['y_c']*k-S*(Fd+(zz-V['zref'])*Cd);run=np.minimum.accumulate(sy,axis=0)
    img=np.empty((H,W,3),dtype=np.float32);dep=np.full((H,W),-1,dtype=np.int32);rows=np.arange(H)+.5
    for j in range(W):
        idx=np.searchsorted(-run[:,j],-rows,side='left');ok=idx<nd;ii=np.minimum(idx,nd-1)
        img[:,j]=col[ii,j];dep[:,j]=np.where(ok,ii,-1)
    skym=dep<0
    # ---- sky (painting): zenith to horizon, a band of earth-shadow pink at dawn and dusk, stars at night
    yy=(rows/H)[:,None];hl=float(np.clip(np.mean(np.argmax(~skym,axis=0))/H,0.05,0.5)) if not flat else 0.2
    tt=np.clip(yy/hl,0,1)**1.25;sky=np.array(L['zen'],dtype=np.float32)*(1-tt[...,None])+hor*tt[...,None]
    sky=np.broadcast_to(sky,(H,W,3)).copy()
    if L['band']:
        b=np.exp(-((yy-hl*0.72)/(hl*0.20))**2)[...,None]*0.75;sky=sky*(1-b)+np.array(L['band'],dtype=np.float32)*b
    cir=vnoise((H,W),int(H*0.035),51)*np.asarray(Image.fromarray(np.random.default_rng(52).random((H//int(H*0.008)+3,W//int(H*0.16)+3)).astype(np.float32),mode='F').resize((W,H),Image.BICUBIC))*(0.6+0.8*vnoise((H,W),int(H*0.11),53))
    cirm=sstep(0.26,0.70,cir)*(0.34 if L['stars']<1 else 0.08)*(1.4 if wx=='storm' else 1)*sstep(0,0.3,yy/hl)
    sky=sky*(1-cirm[...,None])+(hor*0.4+0.6*lit if L['stars']<1 else hor*1.6)*cirm[...,None]
    if L['stars']:
        r=np.random.default_rng(9);n=int(W*H*hl*0.0016)
        sx_=r.integers(0,W,n);sy_=(r.random(n)**1.3*hl*H).astype(int);br=(r.random(n)**3)*L['stars']
        st=np.zeros((H,W),dtype=np.float32);np.maximum.at(st,(sy_,sx_),br)
        st=gblur(st,k*0.32)*5.5
        sky+=np.clip(st,0,1)[...,None]*np.array([0.95,0.97,1.0],dtype=np.float32)*(1-cirm[...,None])
    if not flat:img[skym]=sky[skym]
    # spindrift: snow blown off the high ridges (weather: wind). From the skyline pixels, smeared downwind
    if wx=='wind' and not flat:
        zi=np.zeros((H,W),dtype=np.float32)
        for j in range(W):zi[:,j]=zz[np.clip(dep[:,j],0,nd-1),j]
        edge=np.zeros((H,W),dtype=np.float32);jump=(dep[:-1]-dep[1:]>int(260/g))|(dep[:-1]<0)
        edge[1:]=(jump&(dep[1:]>=0))*sstep(2650,3000,zi[1:])
        pl=np.zeros_like(edge);n=int(k*46)
        nz=vnoise((H,W),int(k*5),61)
        for i in range(n):
            w=(1-i/n)**1.6;sh_=np.roll(edge,(-(i//5),i),axis=(0,1));sh_[:,:i]=0;pl=np.maximum(pl,sh_*w)
        pl=gblur(pl,k*1.6)*(0.55+0.9*nz)
        pl=np.clip(pl*2.6,0,0.9)[...,None];img=img*(1-pl)+(lit*0.5+0.5)*pl
    if wx=='storm':
        img=img*0.86+np.array([0.80,0.82,0.86],dtype=np.float32)*0.14
    # near edge: the valley falls into mist under the sheet
    gy=(1-0.9*sstep(0.90*H,H,rows))[:,None,None];img=img*gy+hor*(1-gy)
    # grade: a gentle filmic curve, a breath of grain against banding
    img=np.clip(img,0,1);img=img*img*(3-2*img)*0.55+img*0.45
    pim=Image.fromarray((img*255).astype(np.uint8));usm=np.asarray(pim.filter(ImageFilter.UnsharpMask(radius=k*1.1,percent=60,threshold=0)),dtype=np.float32)/255;img=usm
    img+=(np.random.default_rng(1).random((H,W,1)).astype(np.float32)-.5)*0.012
    im=Image.fromarray((np.clip(img,0,1)*255).astype(np.uint8)).resize((V['W']*2,V['H']*2),Image.LANCZOS)
    V['g'],V['k']=g,k;V['dep']=dep[::2,::2].copy();V['kd']=k//2
    return V,im
def save(V,v):
    pickle.dump(V,open(os.path.join(CACHE,v+'.pkl'),'wb'))
def load(v):
    p=os.path.join(CACHE,v+'.pkl')
    if not os.path.exists(p):
        V,im=render(v);save(V,v)
    return pickle.load(open(p,'rb'))

# ---------------------------------------------------------------- live vectors over the picture
def pathd(c,gap=2.3):
    o=[c[0]]
    for q in c[1:-1]:
        if math.hypot(q[0]-o[-1][0],q[1]-o[-1][1])>gap:o.append(q)
    o.append(c[-1]);return 'M'+' '.join('%.1f %.1f'%q for q in o).replace('.0 ',' ')
def visible(V,u,d,z):
    x,y=screen(V,u,d,z);dep=V['dep'];kd=V['kd']
    j=np.clip((x*kd).astype(int),0,dep.shape[1]-1);r=np.clip((y*kd).astype(int),0,dep.shape[0]-1)
    return (np.abs(dep[r,j]-np.asarray(d)/V['g'])<16)&(x>-20)&(x<V['W']+20)&(y>-20)&(y<V['H']+20)
def project(V,pts,lift=0.0,step=12):
    q=np.array(dense(pts,step));u,d=to_ud(V,q[:,0],q[:,1]);z=elev(q[:,0],q[:,1])
    x,y=screen(V,u,d,z+lift);return x,y,visible(V,u,d,z),z,q
def pt(V,e,n,lift=0.0):
    u,d=to_ud(V,e,n);z=float(elev(e,n));x,y=screen(V,u,d,z+lift)
    return float(x),float(y),bool(visible(V,np.array([u]),np.array([d]),np.array([z]))[0]),z
def find(name):
    for p in PISTES:
        if p['key']==name or p['name']==name:return p
    for l in LIFTS:
        if l['name']==name:return l
def flift(name):
    return [l for l in LIFTS if l['name']==name][0]
def lift_ends(l):
    return sorted((l['pts'][0],l['pts'][-1]),key=lambda p:float(elev(*p)))      # bottom, top
PAL={'day':dict(green='#178A4A',blue='#1B6FDB',red='#D8362C',black='#14171B',case='#FFFFFF',wire='#22303F',st='#FFFFFF',sh='#1B2E55'),
     'night':dict(green='#4FD893',blue='#6CB4FF',red='#FF776A',black='#EEF2F6',case='#081020',wire='#C9D5E2',st='#0A1120',sh='#000510')}
def svg_open(V,extra=''):
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linejoin="round" stroke-linecap="round"%s>'%(V['W'],V['H'],V['W'],V['H'],extra)
def sun_shift(V,light,h):
    """screen offset of the shadow of a point h metres above the ground"""
    L=LIGHTS[light];a,al=math.radians(L['az']),math.radians(max(L['alt'],6));ln=h/math.tan(al)
    e,n=-math.sin(a)*ln,-math.cos(a)*ln;u=e*V['r'][0]+n*V['r'][1];dd=e*V['f'][0]+n*V['f'][1]
    return V['S']*u,-V['S']*dd*math.sin(math.radians(V['p'][0]))
def details(V,mode,light):
    """the small real things: rivers, roads, the mapped buildings (with their shadows), lit windows at night"""
    c=PAL[mode];o=[svg_open(V)];S=V['S'];env=T['env']
    for r in env['rivers']:
        pts=[(r[i],-r[i+1]) for i in range(0,len(r),2)];x,y,vis,z,q=project(V,pts)
        for ch in chunks(x,y,vis):o.append('<path d="%s" stroke="%s" stroke-width="%.2f" opacity=".55"/>'%(pathd(ch),'#3E5F8E' if mode=='day' else '#0A1830',max(0.9,S*9)))
    for cls,r in env['roads']:
        pts=[(r[i],-r[i+1]) for i in range(0,len(r),2)];x,y,vis,z,q=project(V,pts)
        w=max(0.7,S*(9 if cls==0 else 5.5))
        for ch in chunks(x,y,vis):
            o.append('<path d="%s" stroke="%s" stroke-width="%.2f" opacity="%s"/>'%(pathd(ch),'#FFFFFF' if mode=='day' else '#1B2740',w+1.1,'.5' if mode=='day' else '.6'))
            o.append('<path d="%s" stroke="%s" stroke-width="%.2f" opacity="%s"/>'%(pathd(ch),'#56617A' if mode=='day' else '#3A4868',w,'.62' if cls==0 else '.4'))
    if mode=='night':                                   # the lights of the real villages (areas from the map), drawn as scattered warm points
        rng=np.random.default_rng(4)
        for poly in env['village']:
            P=np.array([(poly[i],-poly[i+1]) for i in range(0,len(poly),2)]);mn,mx=P.min(0),P.max(0);area=(mx-mn).prod()
            for _ in range(int(min(46,area/5200))):
                e,n=mn+rng.random(2)*(mx-mn);x,y,v,z=pt(V,e,n,2)
                if v:o.append('<circle cx="%.1f" cy="%.1f" r="%.2f" fill="#FFC56E" opacity="%.2f"/>'%(x,y,max(0.5,S*5),0.5+0.5*rng.random()))
    dx,dy=sun_shift(V,light,1)
    bl=[]
    for b in BLD['b']:
        P=[(p[0],-p[1]) for p in b['p']];cx=sum(p[0] for p in P)/len(P);cy=sum(p[1] for p in P)/len(P)
        u,d=to_ud(V,cx,cy);bl.append((float(d),b,P))
    for d,b,P in sorted(bl,key=lambda t:-t[0]):
        base=[pt(V,*p) for p in P];h=b.get('h') or 9
        if not any(v for _,_,v,_ in base):continue
        zc=max(z for *_,z in base);up=V['S']*h*math.cos(math.radians(V['p'][0]))
        g_=[(x,y) for x,y,_,_ in base];r_=[(x,y-up) for x,y in g_]
        poly=lambda pp:' '.join('%.1f,%.1f'%p for p in pp)
        if mode=='day':o.append('<polygon points="%s" fill="%s" opacity=".34"/>'%(poly(g_+[(x+dx*h,y+dy*h) for x,y in reversed(g_)]),c['sh']))
        n=len(g_)
        for i in range(n if S>0.2 else 0):              # walls facing the viewer (only when close)
            a,b2=g_[i],g_[(i+1)%n]
            o.append('<polygon points="%s" fill="%s"/>'%(poly([a,b2,(b2[0],b2[1]-up),(a[0],a[1]-up)]),('#8E6B52' if (b2[0]-a[0])*(1 if dx<0 else -1)>0 else '#6E5446') if mode=='day' else '#1C2238'))
        o.append('<polygon points="%s" fill="%s" stroke="%s" stroke-width="%.2f"/>'%(poly(r_),'#FDFEFF' if mode=='day' else '#34456B','#9FB2CE' if mode=='day' else '#0B1226',max(0.25,S*0.6)))
        if mode=='night' and S<=0.2:o.append('<circle cx="%.1f" cy="%.1f" r=".7" fill="#FFCB78"/>'%(sum(p[0] for p in r_)/n,sum(p[1] for p in r_)/n))
        if mode=='night' and S>0.2:
            rng=np.random.default_rng(sum(map(ord,b['id'])))
            for i in range(n):
                a,b2=g_[i],g_[(i+1)%n];ln=math.hypot(b2[0]-a[0],b2[1]-a[1])
                for j in range(int(ln/max(1.2,S*5))):
                    for fl in range(1,max(2,int(h/3.2))):
                        if rng.random()<0.42:
                            tq=(j+.5)/max(1,int(ln/max(1.2,S*5)));o.append('<circle cx="%.1f" cy="%.1f" r="%.2f" fill="#FFCB78"/>'%(a[0]+(b2[0]-a[0])*tq,a[1]+(b2[1]-a[1])*tq-up*fl/max(2,int(h/3.2)),max(0.35,S*1.1)))
    o.append('</svg>');return '\n'.join(o)
def lifts_svg(V,mode,light,dim=False,cabins=True,only=None):
    c=PAL[mode];o=[svg_open(V)];S=V['S'];zf=max(1,S/0.128)**0.7;dx,dy=sun_shift(V,light,1)
    op=' opacity=".4"' if dim else ''
    for l in LIFTS:
        if only and l['name'] not in only:continue
        hgt=11;x,y,vis,z,q=project(V,l['pts'],lift=hgt);xg,yg,_,_,_=project(V,l['pts'])
        n=len(x);step=max(1,int(round(170/12)))
        if mode=='day':
            for ch in chunks(xg+dx*hgt,yg+dy*hgt,vis):o.append('<path d="%s" stroke="%s" stroke-width="%.2f" opacity=".16"/>'%(pathd(ch),c['sh'],0.8*zf))
        for ch in chunks(x,y,vis):o.append('<path d="%s" stroke="%s" stroke-width="%.2f"%s/>'%(pathd(ch),c['wire'],1.0*zf,op))
        tw=''.join('M%.1f %.1fL%.1f %.1fm%.1f 0h%.1f'%(xg[i],yg[i],x[i],y[i],-1.6*zf,3.2*zf) for i in list(range(0,n,step))[1:] if vis[i] and i<n-3)
        if tw:o.append('<path d="%s" stroke="%s" stroke-width="%.2f"%s/>'%(tw,c['wire'],1.15*zf,op))
        if cabins and not dim:
            gond=l['kind'] in('gondola','cable_car','mixed_lift')
            for i in range(step//2,n-2,max(3,step//2+1)):
                if vis[i]:
                    if mode=='day':o.append('<ellipse cx="%.1f" cy="%.1f" rx="%.1f" ry="%.1f" fill="%s" opacity=".3"/>'%(xg[i]+dx*hgt,yg[i]+dy*hgt,1.5*zf,0.8*zf,c['sh']))
                    o.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s" stroke="%s" stroke-width=".5"/>'%(x[i]-1.3*zf,y[i]+0.4*zf,2.6*zf,(2.6 if gond else 1.7)*zf,0.7*zf,'#F6C445' if mode=='day' else '#FFD873',c['wire']))
        for i in(0,n-1):
            if vis[i]:o.append('<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s" stroke="%s" stroke-width="%.2f"%s/>'%(xg[i]-3.4*zf,yg[i]-4.2*zf,6.8*zf,5*zf,1.4*zf,c['st'],c['wire'],1.2*zf,op))
    o.append('</svg>');return '\n'.join(o)
def runs_svg(V,mode,style='line',dim=False,only=None,skip=None,glow=True):
    """style: line (cased colour line) | pole (thin edge of colour on the groomed band) | ghost (faint, for a chosen run)"""
    c=PAL[mode];o=[svg_open(V)];zf=max(1,V['S']/0.128)**0.62
    geo=[]
    for p in PISTES:
        if only and p['key'] not in only:continue
        if skip and p['key'] in skip:continue
        for s in p['segs']:
            x,y,vis,z,q=project(V,s);geo.append((p,chunks(x,y,vis)))
    if mode=='night' and glow:
        for p,chs in geo:
            if p['lit']:
                for ch in chs:o.append('<path d="%s" stroke="#FFD08A" stroke-width="16" opacity=".2"/><path d="%s" stroke="#FFE2B0" stroke-width="8" opacity=".3"/>'%(pathd(ch),pathd(ch)))
    if style=='ghost':
        for p,chs in geo:
            for ch in chs:o.append('<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%s"%s/>'%(pathd(ch),c[p['color']],1.5*zf,'.55' if p['named'] else '.35',' stroke-dasharray="1 5"' if not p['named'] or p['kind']=='ski-way' else ''))
    else:
        for p,chs in geo:
            if p['named']:
                for ch in chs:o.append('<path d="%s" stroke="%s" stroke-width="%.1f" opacity=".92"/>'%(pathd(ch),c['case'],4.6*zf))
        for p,chs in geo:
            w=(2.7 if p['named'] else 1.5)*zf;da=' stroke-dasharray="1 5"' if (p['kind']=='ski-way' or not p['named']) else ''
            for ch in chs:o.append('<path d="%s" stroke="%s" stroke-width="%.1f"%s/>'%(pathd(ch),c[p['color']],w,da))
    o.append('</svg>');return '\n'.join(o)
def run_path(V,key,which='long'):
    """the chosen run, top to bottom: screen points, visibility, distance along, elevation, slope (degrees)"""
    p=find(key);s=max(p['segs'],key=len);x,y,vis,z,q=project(V,s,step=10)
    if z[0]<z[-1]:x,y,vis,z,q=x[::-1],y[::-1],vis[::-1],z[::-1],q[::-1]
    cum=np.concatenate([[0],np.cumsum(np.hypot(np.diff(q[:,0]),np.diff(q[:,1])))]);deg=site_slope(q,cum)
    return dict(x=x,y=y,vis=vis,z=z,q=q,cum=cum,deg=deg,p=p)
def stats(key):
    """top, bottom, drop (m) and the steepest ~100 m stretch (degrees), by the site's own rule (R.stats in site/js/relief.js)"""
    from mountain import elev_raw
    p=find(key);top=-1e9;bot=1e9;mg=0
    for L in p['segs']:
        L=np.array(L);acc=np.concatenate([[0],np.cumsum(np.hypot(np.diff(L[:,0]),np.diff(L[:,1])))]);h=elev_raw(L[:,0],L[:,1])
        top=max(top,float(h.max()));bot=min(bot,float(h.min()));j=0
        for i in range(len(L)):
            while j<len(L)-1 and acc[j]-acc[i]<100:j+=1
            dl=acc[j]-acc[i]
            if dl>=80:mg=max(mg,abs(h[j]-h[i])/dl)
    return dict(top=round(top),bot=round(bot),drop=round(top-bot),steep=round(math.degrees(math.atan(mg))))
def all_chunks(V,key):
    """every mapped piece of a run, as visible screen chunks"""
    o=[]
    for s in find(key)['segs']:
        x,y,vis,z,q=project(V,s);o+=chunks(x,y,vis)
    return o
def slope_paths(R,width,extra=''):
    o=[];seg=[]
    for i in range(len(R['x'])-1):
        cc=[c2 for t,c2 in SLOPE if R['deg'][i]<t][0]
        if not(R['vis'][i] and R['vis'][i+1]):seg.append(None);continue
        if seg and seg[-1] and seg[-1][0]==cc:seg[-1][1].append((R['x'][i+1],R['y'][i+1]))
        else:seg.append([cc,[(R['x'][i],R['y'][i]),(R['x'][i+1],R['y'][i+1])]])
    for s in seg:
        if s:o.append('<path d="%s" stroke="%s" stroke-width="%s"%s/>'%(pathd(s[1]),s[0],width,extra))
    return ''.join(o)
def at(R,m):
    """screen point and direction at m metres along the run"""
    i=int(np.clip(np.searchsorted(R['cum'],m),1,len(R['x'])-2));ax,ay=R['x'][i+1]-R['x'][i-1],R['y'][i+1]-R['y'][i-1];h=math.hypot(ax,ay) or 1
    return float(R['x'][i]),float(R['y'][i]),ax/h,ay/h,float(R['z'][i]),float(R['deg'][i])
def whole(R):
    return pathd(list(zip(R['x'].tolist(),R['y'].tolist())))

if __name__=='__main__':
    os.makedirs(OUT,exist_ok=True)
    for a in sys.argv[1:]:
        pa=a.split(':')+['',''];v,li,wx=pa[0],pa[1] or 'morning',pa[2] or None
        V,im=render(v,li,wx);name='pk-%s-%s%s'%(v,li,'-'+wx if wx else '')
        im.save(os.path.join(OUT,name+'.jpg'),quality=88,subsampling=0)
        if wx!='cloud':save(V,v)
        print(name,im.size)
