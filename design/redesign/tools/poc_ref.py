#!/usr/bin/env python3
"""Reference frames for the Android POC, drawn ONLY from the handoff (peak-poc.json and the pictures),
to prove the handoff is enough and to give the executor something to compare by eye.
Writes design/handoff/2026-11-20-redesign-poc/ref/ref.html ; poc_ref.js turns it into ref/*.jpg
Usage: python3 design/redesign/tools/poc_ref.py && node design/redesign/tools/poc_ref.js"""
import os,json,math
ROOT=os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT=os.path.join(ROOT,'design/handoff/2026-11-20-redesign-poc');D=json.load(open(os.path.join(OUT,'peak-poc.json'),encoding='utf8'))
os.makedirs(os.path.join(OUT,'ref'),exist_ok=True)
SL=[c for _,c in D['slope']]
def pd(a):return 'M'+' L'.join('%.1f %.1f'%(a[i],a[i+1]) for i in range(0,len(a),2))
def mode(light):return 'night' if [l for l in D['lights'] if l['name']==light][0]['night'] else 'day'

def details(V,light):
    m=mode(light);o=''
    for e in V['details'][m]:
        op=' opacity="%s"'%e['o'] if 'o' in e else ''
        if e['k']=='sh':o+='<polygon points="%s" fill="%s" opacity="%s"/>'%(' '.join(map(str,V['bshadow'][light][e['i']])),e['f'],e['o'])
        elif e['k']=='l':o+='<path d="%s" stroke="%s" stroke-width="%s"%s/>'%(pd(e['p']),e['s'],e['w'],op)
        elif e['k']=='p':o+='<polygon points="%s" fill="%s"%s%s/>'%(' '.join(map(str,e['p'])),e['f'],' stroke="%s" stroke-width="%s"'%(e['s'],e['w']) if 's' in e else '',op)
        else:o+='<circle cx="%s" cy="%s" r="%s" fill="%s"%s/>'%(e['x'],e['y'],e['r'],e['f'],op)
    return o

def runs(V,light,ghost=False,skip=None):
    m=mode(light);c=D['pal'][m];zf=V['zf_runs'];o=''
    ps=[p for p in V['pistes'] if p['key']!=skip]
    dash=lambda p:' stroke-dasharray="1 5"' if (not p['named'] or p['kind']=='ski-way') else ''
    if m=='night' and not ghost:
        for p in ps:
            if p['lit']:
                for l in p['l']:o+='<path d="%s" stroke="#FFD08A" stroke-width="16" opacity=".2"/><path d="%s" stroke="#FFE2B0" stroke-width="8" opacity=".3"/>'%(pd(l),pd(l))
    if ghost:
        for p in ps:
            for l in p['l']:o+='<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%s"%s/>'%(pd(l),c[p['color']],1.5*zf,'.55' if p['named'] else '.35',dash(p))
        return o
    for p in ps:
        if p['named']:
            for l in p['l']:o+='<path d="%s" stroke="%s" stroke-width="%.1f" opacity=".92"/>'%(pd(l),c['case'],4.6*zf)
    for p in ps:
        for l in p['l']:o+='<path d="%s" stroke="%s" stroke-width="%.1f"%s/>'%(pd(l),c[p['color']],(2.7 if p['named'] else 1.5)*zf,dash(p))
    return o

def lifts(V,light,dim=False):
    m=mode(light);c=D['pal'][m];zf=V['zf_lifts'];o='';op=' opacity=".4"' if dim else ''
    sx,sy=[v*0.11 for v in V['shadow'][light]] if m=='day' else (0,0)          # the wire runs 11 m above the ground
    for l in V['lifts']:
        if m=='day':
            for g in l['ground']:o+='<path d="%s" stroke="%s" stroke-width="%.2f" opacity=".16" transform="translate(%.1f %.1f)"/>'%(pd(g),c['sh'],0.8*zf,sx,sy)
        for w in l['wire']:o+='<path d="%s" stroke="%s" stroke-width="%.2f"%s/>'%(pd(w),c['wire'],1.0*zf,op)
        for xg,yg,x,y in l['towers']:o+='<path d="M%.1f %.1fL%.1f %.1fm%.1f 0h%.1f" stroke="%s" stroke-width="%.2f"%s/>'%(xg,yg,x,y,-1.6*zf,3.2*zf,c['wire'],1.15*zf,op)
        if not dim:
            for x,y,xg,yg in l['cabins']:
                if m=='day':o+='<ellipse cx="%.1f" cy="%.1f" rx="%.1f" ry="%.1f" fill="%s" opacity=".3"/>'%(xg+sx,yg+sy,1.5*zf,0.8*zf,c['sh'])
                o+='<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s" stroke="%s" stroke-width=".5"/>'%(x-1.3*zf,y+0.4*zf,2.6*zf,(2.6 if l['gondola'] else 1.7)*zf,0.7*zf,'#F6C445' if m=='day' else '#FFD873',c['wire'])
        for xg,yg in l['stations']:o+='<rect x="%.1f" y="%.1f" width="%.1f" height="%.1f" rx="%.1f" fill="%s" stroke="%s" stroke-width="%.2f"%s/>'%(xg-3.4*zf,yg-4.2*zf,6.8*zf,5*zf,1.4*zf,c['st'],c['wire'],1.2*zf,op)
    return o

def rows(s):return [s['p'][i:i+5] for i in range(0,len(s['p']),5)]
def vis_chunks(r):
    out=[];cur=[]
    for x,y,v,_,_ in r:
        if v:cur+=[x,y]
        else:
            if len(cur)>=4:out.append(cur)
            cur=[]
    if len(cur)>=4:out.append(cur)
    return out
def at(r,m):
    i=min(max(next((k for k,q in enumerate(r) if q[3]>=m),len(r)-2),1),len(r)-2);ax,ay=r[i+1][0]-r[i-1][0],r[i+1][1]-r[i-1][1];h=math.hypot(ax,ay) or 1
    return r[i][0],r[i][1],ax/h,ay/h,r[i][4],r[i][2]
def slope_all(r,w):
    o='';cur=None
    for a,b in zip(r,r[1:]):
        if not(a[2] and b[2]):
            if cur:o+='<path d="%s" stroke="%s" stroke-width="%s"/>'%(pd(cur[1]),SL[cur[0]],w)
            cur=None;continue
        if cur and cur[0]==a[4]:cur[1]+=[b[0],b[1]]
        else:
            if cur:o+='<path d="%s" stroke="%s" stroke-width="%s"/>'%(pd(cur[1]),SL[cur[0]],w)
            cur=[a[4],[a[0],a[1],b[0],b[1]]]
    if cur:o+='<path d="%s" stroke="%s" stroke-width="%s"/>'%(pd(cur[1]),SL[cur[0]],w)
    return o

def chosen(V,vn,key,light,uid):
    m=mode(light);S=V['proj']['S'];W,H=V['W'],V['H'];sel=V['sel'][key];main=rows(sel['segs'][sel['main']]);c=D['pal'][m];o=''
    cor=''.join('<path d="%s" stroke="#000" stroke-width="%g"/>'%(pd(ch),S*250) for sg in sel['segs'] for ch in vis_chunks(rows(sg)))
    near=S>0.2;lw,cw=(5.2,8.2) if near else (3.6,6.0);gap=10.5/S          # far picture: the thinner line of the moment of choice; beads keep 10.5 pt between them
    veil='rgba(54,78,128,.50)' if m=='day' else 'rgba(2,6,18,.62)'
    o+=('<defs><filter id="%sb" filterUnits="userSpaceOnUse" x="-60" y="-60" width="600" height="1000"><feGaussianBlur stdDeviation="%g"/></filter><mask id="%sm"><rect width="%d" height="%d" fill="#fff"/><g filter="url(#%sb)">%s</g></mask></defs>'
        '<rect width="%d" height="%d" fill="%s" mask="url(#%sm)"/>')%(uid,S*56,uid,W,H,uid,cor,W,H,veil,uid)
    o+=runs(V,light,ghost=True,skip=key)+lifts(V,light,dim=True)
    if m=='day':
        for sg in sel['segs']:
            r=rows(sg)
            for ch in vis_chunks(r):o+='<path d="%s" stroke="%s" stroke-width="%s"/>'%(pd(ch),c['case'],cw)
            o+=slope_all(r,lw)
            acc=0
            for i in range(1,len(r)-1):
                acc+=r[i][3]-r[i-1][3]
                if acc>240 and r[i][2]:
                    acc=0;x,y,ax,ay,_,_=at(r,r[i][3])
                    o+='<path d="M%.1f %.1f L%.1f %.1f L%.1f %.1f" stroke="#fff" stroke-width="1.3"/>'%(x-ax*1.6-ay*2.1,y-ay*1.6+ax*2.1,x+ax*1.3,y+ay*1.3,x-ax*1.6+ay*2.1,y-ay*1.6-ax*2.1)
        for mm in range(500,int(main[-1][3]),500):
            x,y,ax,ay,_,v=at(main,mm)
            if v:o+='<circle cx="%.1f" cy="%.1f" r="3.1" fill="#fff" stroke="#0E2233" stroke-width="1.2"/><text x="%.1f" y="%.1f" text-anchor="middle" font-size="9.5" font-weight="700" fill="#fff" stroke="rgba(10,25,50,.8)" stroke-width="2.4" paint-order="stroke">%s</text>'%(x,y,x+ay*13,y-ax*13+3.4,('%.1f'%(mm/1000)).rstrip('0').rstrip('.'))
        a,b=main[0],main[-1]
        o+='<circle cx="%.1f" cy="%.1f" r="4.6" fill="#fff" stroke="#6B3FD4" stroke-width="2"/><circle cx="%.1f" cy="%.1f" r="3.6" fill="#6B3FD4" stroke="#fff" stroke-width="1.4"/>'%(a[0],a[1],b[0],b[1])
    else:
        for sg in sel['segs']:
            r=rows(sg)
            for ch in vis_chunks(r):o+='<path d="%s" stroke="#FFD08A" stroke-width="14" opacity=".2"/>'%pd(ch)
            for mm in range(0,int(r[-1][3]),int(round(gap))):
                x,y,_,_,cl,v=at(r,mm)
                if v:o+='<circle cx="%.1f" cy="%.1f" r="2.3" fill="#FFF3D6"/><circle cx="%.1f" cy="%.1f" r="4.4" fill="%s" opacity=".4"/>'%(x,y,x,y,SL[cl])
    R=D['runs'][key];top=main[0];txt=(R['ref']+' · ' if R['ref'] else '')+R['name']
    plate='<div style="position:absolute;left:%.1fpx;top:%.1fpx;transform:translate(-86%%,-100%%);display:flex;flex-direction:column;align-items:flex-end"><span style="height:24px;line-height:21px;padding:0 8px;border-radius:6px;background:%s;color:#fff;font:700 12px Heebo,sans-serif;line-height:21px;border:1.5px solid #fff;direction:ltr;white-space:nowrap">%s</span><i style="width:2px;height:12px;background:#fff;margin-inline-end:12px"></i></div>'%(top[0],top[1]-4,D['pal']['day'][R['color']],txt)
    return o,plate

def frame(name,vn,light,key=None):
    V=D['views'][vn];W,H=V['W'],V['H'];m=mode(light);plate=''
    if key:
        ch,plate=chosen(V,vn,key,light,name.replace('-',''));inner=details(V,light)+ch
    else:inner=details(V,light)+runs(V,light)+lifts(V,light)
    lb=''
    if not key:
        ink,halo=('#0E2233','rgba(255,255,255,.95)') if m=='day' else ('#EAF1F8','rgba(6,11,22,.95)')
        for l in V.get('labels',[]):
            lb+='<div style="position:absolute;left:%dpx;top:%dpx;transform:translate(-50%%,-50%%);text-align:center;font:700 11.5px Heebo,sans-serif;color:%s;text-shadow:0 0 3px %s,0 0 3px %s,0 0 7px %s;direction:ltr;white-space:nowrap">%s%s</div>'%(l['x'],l['y'],ink,halo,halo,halo,l['t'],'<br><span style="font-weight:500;font-size:10.5px">%s מ׳</span>'%format(l['sub'],',') if 'sub' in l else '')
    return ('<div class="f" id="%s" style="width:%dpx;height:%dpx"><img src="../%s" width="%d" height="%d">'
            '<svg viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linejoin="round" stroke-linecap="round">%s</svg>%s%s</div>')%(name,W,H,V['images'][light],W,H,W,H,W,H,inner,lb,plate)

FR=[('hero-day','hero','morning',None),('hero-night','hero','night',None),('zoom-day-tatra2','zoom','morning','Tatra 2'),('zoom-night-tatra2','zoom','night','Tatra 2'),
    ('hero-day-kudebi1','hero','morning','Kudebi 1'),('hero-night-kudebi1','hero','night','Kudebi 1')]
html='<!doctype html><meta charset="utf8"><link href="https://fonts.googleapis.com/css2?family=Heebo:wght@500;700&display=swap" rel="stylesheet"><style>body{margin:0;background:#222;display:flex;flex-wrap:wrap;gap:12px}.f{position:relative;overflow:hidden}.f>*{position:absolute;left:0;top:0}.f>div{left:auto;top:auto}</style>'+''.join(frame(*f) for f in FR)
open(os.path.join(OUT,'ref','ref.html'),'w',encoding='utf8').write(html);print('ref.html',len(html)//1024,'KB',[f[0] for f in FR])
