#!/usr/bin/env python3
"""Round 4: the ski day on the painted mountain. Data for the boards.
The example day is the synthetic full day of the reference analyser (tools/fixtures/dayrec-logs/full_day.jsonl:
real lifts, runs and ground; invented times and speeds) with its expected analysis (dayrec-cases.json) and
moments (dayrec-moments.json). Every board that shows it carries the tag "דוגמה"."""
import os,sys,json,math,datetime
import numpy as np
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import peak as P
from mountain import ROOT,EN,elev
TZ=datetime.timezone(datetime.timedelta(hours=4))
_c=json.load(open(os.path.join(ROOT,'tools/fixtures/dayrec-cases.json')))
CASE=[x for x in _c['cases'] if x['name']=='full_day'][0];FIN=CASE['expected']['final'];SEG=FIN['segments'];ST=FIN['stats']
MOM=[x for x in json.load(open(os.path.join(ROOT,'tools/fixtures/dayrec-moments.json')))['cases'] if x['name']=='full_day'][0]['expected']
def _load():
    T=[];LL=[];S=[]
    for ln in open(os.path.join(ROOT,'tools/fixtures/dayrec-logs/full_day.jsonl')):
        r=json.loads(ln)
        if 'la' in r:T.append(r['t']);LL.append((r['la'],r['lo']));S.append(r['s'] or 0)
    T=np.array(T,dtype=np.int64);q=np.array([EN(g) for g in LL]);S=np.array(S)*3.6
    k=np.ones(5)/5;S=np.convolve(np.pad(S,2,mode='edge'),k,mode='valid')          # 5 s mean of the Doppler speed, for drawing only
    for w in(7,):                                                                   # light smoothing of the position, for drawing only
        kk=np.ones(w)/w;q=np.stack([np.convolve(np.pad(q[:,i],w//2,mode='edge'),kk,mode='valid') for i in(0,1)],1)
    return T,q,S,elev(q[:,0],q[:,1])
T,Q,KMH,Z=_load()
hm=lambda t:datetime.datetime.fromtimestamp(t/1000,TZ).strftime('%H:%M')
hour=lambda t:(lambda d:d.hour+d.minute/60+d.second/3600)(datetime.datetime.fromtimestamp(t/1000,TZ))
def idx(t0,t1):
    return np.searchsorted(T,t0),np.searchsorted(T,t1,side='right')
DESC=[s for s in SEG if s['type']=='descent'];LIFT=[s for s in SEG if s['type']=='lift']
def names(d):
    """the runs of a descent in order, without repeats (shared stretches keep both names)"""
    o=[]
    for l in d['legs']:
        n=' · '.join(l['runs']) if l['kind']=='run' else None
        for r in(l['runs'] if l['kind']=='run' else []):
            if r not in o:o.append(r)
    return o
# speed as light: the faster, the hotter. Its own scale, away from the piste colours and the steepness colours.
SP=[20,35,50,62]
RAMP={'day':['#CFC2F4','#A98BEE','#7C4FE0','#B02FC4','#E0246E'],'night':['#5B46A8','#8A6BE6','#B99CFF','#F3A6F0','#FFF2FA']}
def band(k):
    return sum(k>=x for x in SP)
def track(V,t0,t1,mode,w=3.0,case=True,alpha=1.0,solid=None):
    """the skied line between two moments on a view, coloured by speed (or one colour)"""
    a,b=idx(t0,t1);q=Q[a:b]
    if len(q)<2:return ''
    u,d=P.to_ud(V,q[:,0],q[:,1]);z=Z[a:b];x,y=P.screen(V,u,d,z+2);vis=P.visible(V,u,d,z);gap=np.diff(T[a:b])>10000
    runs=[];cur=None
    for i in range(len(q)-1):
        if not(vis[i] and vis[i+1]) or gap[i]:cur=None;continue
        c=solid or RAMP[mode][band(KMH[a+i])]
        if cur and cur[0]==c:cur[1].append((x[i+1],y[i+1]))
        else:
            cur=[c,[(x[i],y[i]),(x[i+1],y[i+1])]];runs.append(cur)
    o=''
    if case:
        cs='#FFFFFF' if mode=='day' else '#050A16'
        for c,pts in runs:o+='<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%.2f"/>'%(P.pathd(pts,1.6),cs,w+2.2,alpha*.9)
    for c,pts in runs:o+='<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%.2f"/>'%(P.pathd(pts,1.6),c,w,alpha)
    return o
def rides(V,mode,until=None,w=1.4):
    o='';c='#22303F' if mode=='day' else '#C9D5E2'
    for s in LIFT:
        if until and s['t0']>until:break
        a,b=idx(s['t0'],min(s['t1'],until or s['t1']));q=Q[a:b]
        if len(q)<2:continue
        e0,e1=q[0],q[-1];x0,y0,_,_=P.pt(V,*e0,lift=8);x1,y1,_,_=P.pt(V,*e1,lift=8)
        o+='<path d="M%.1f %.1f L%.1f %.1f" stroke="%s" stroke-width="%.1f" stroke-dasharray="1 4" opacity=".8"/>'%(x0,y0,x1,y1,c,w)
    return o
def day(V,mode,until=None,w=3.0,lifts=True,solid=None,alpha=1.0):
    o=rides(V,mode,until) if lifts else ''
    for d in DESC:
        if until and d['t0']>until:break
        o+=track(V,d['t0'],min(d['t1'],until or d['t1']),mode,w=w,solid=solid,alpha=alpha)
    return o
def where(V,t,lift=2):
    i=int(np.clip(np.searchsorted(T,t),0,len(T)-1));x,y,vis,z=P.pt(V,Q[i,0],Q[i,1],lift=lift);return x,y,vis,i
def ll(V,lat,lon,lift=2):
    e,n=EN((lat,lon));return P.pt(V,e,n,lift=lift)
def ribbon(w,h,mode,pad=6,until=None,sw=2.2):
    """the day as a ridge: height against time. lifts are the calm climbs, descents burn by speed."""
    t0,t1=FIN['start'],FIN['end'];zlo,zhi=float(Z.min())-40,float(Z.max())+40
    X=lambda t:pad+(t-t0)/(t1-t0)*(w-2*pad);Y=lambda z:h-pad-(z-zlo)/(zhi-zlo)*(h-2*pad)
    st=30;tt=T[::st];zz=Z[::st];o='';ar='M%.1f %.1f '%(X(tt[0]),h)+' '.join('L%.1f %.1f'%(X(a),Y(b)) for a,b in zip(tt,zz))+' L%.1f %.1f Z'%(X(tt[-1]),h)
    o+='<path d="%s" fill="%s" stroke="none"/>'%(ar,'rgba(107,63,212,.10)' if mode=='day' else 'rgba(183,156,255,.14)')
    for s in SEG:
        if until and s['t0']>until:break
        a,b=idx(s['t0'],min(s['t1'],until or s['t1']));
        if b-a<2:continue
        pts=[(X(T[i]),Y(Z[i])) for i in range(a,b,6)]+[(X(T[b-1]),Y(Z[b-1]))]
        if s['type']=='descent':
            cur=None;rr=[]
            for i in range(a,b-6,6):
                c=RAMP[mode][band(KMH[i])];p0=(X(T[i]),Y(Z[i]));p1=(X(T[i+6]),Y(Z[i+6]))
                if cur and cur[0]==c:cur[1].append(p1)
                else:cur=[c,[p0,p1]];rr.append(cur)
            for c,pp in rr:o+='<path d="%s" stroke="%s" stroke-width="%.1f"/>'%(P.pathd(pp,.8),c,sw+.6)
        elif s['type']=='lift':
            o+='<path d="%s" stroke="%s" stroke-width="%.1f" stroke-dasharray="1 3.4"/>'%(P.pathd(pts,.8),'#40556A' if mode=='day' else '#A9B9CC',sw-.6)
        else:
            o+='<path d="%s" stroke="%s" stroke-width="%.1f" opacity=".55"/>'%(P.pathd(pts,.8),'#40556A' if mode=='day' else '#A9B9CC',sw-.9)
    return o,X,Y
def film(d,w,h,mode,pad=14,sw=5.0):
    """one descent as its own film: the real line turned so the start is up, burning by speed"""
    a,b=idx(d['t0'],d['t1']);q=Q[a:b];a0,a1=q[0],q[-1];ax=a1-a0;L=np.hypot(*ax) or 1;ax=ax/L;pr=np.array([ax[1],-ax[0]])
    al=(q-a0)@ax;la=(q-a0)@pr;sc=min((h-2*pad)/(al.max()-al.min()+1),(w-2*pad)/(la.max()-la.min()+1))
    x=w/2-(la-(la.max()+la.min())/2)*sc;y=pad+(al-al.min())*sc;gap=np.diff(T[a:b])>10000;o='';cs='#FFFFFF' if mode=='day' else '#050A16'
    rr=[];cur=None
    for i in range(len(q)-1):
        if gap[i]:
            o+='<path d="M%.1f %.1f L%.1f %.1f" stroke="%s" stroke-width="1.6" stroke-dasharray="2 5"/>'%(x[i],y[i],x[i+1],y[i+1],'#40556A' if mode=='day' else '#A9B9CC');cur=None;continue
        c=RAMP[mode][band(KMH[a+i])]
        if cur and cur[0]==c:cur[1].append((x[i+1],y[i+1]))
        else:cur=[c,[(x[i],y[i]),(x[i+1],y[i+1])]];rr.append(cur)
    for c,pp in rr:o+='<path d="%s" stroke="%s" stroke-width="%.1f"/>'%(P.pathd(pp,1.2),cs,sw+2.6)
    for c,pp in rr:o+='<path d="%s" stroke="%s" stroke-width="%.1f"/>'%(P.pathd(pp,1.2),c,sw)
    at=lambda t:(lambda i:(float(x[i]),float(y[i])))(int(np.clip(np.searchsorted(T[a:b],t),0,b-a-1)))
    return o,at
if __name__=='__main__':
    print(len(T),hm(FIN['start']),hm(FIN['end']),ST,[names(d) for d in DESC][:3],float(KMH.max()))
