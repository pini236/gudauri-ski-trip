# Measures OpenStreetMap coverage (via OpenSkiMap, ODbL) for Sella Ronda (four valleys of Dolomiti Superski, without Seiser Alm) and Les 3 Vallees.
# Run: python3 osm_two_areas.py ski_areas.geojson runs.geojson  (OpenSkiMap export, 7.10.2026). Result in sella-3vallees.md.
import json,sys,math,collections
sa=json.load(open(sys.argv[1]))['features']; runs=sys.argv[2]
byname={}
for f in sa:
    p=f['properties']
    if p.get('type')=='skiArea': byname.setdefault(p.get('name'),p['id'])
want={'Dolomiti Superski':'DS','Seiser Alm - Mont de Sëuc - Alpe di Siusi, Seiser Alm':'SEIS','Alta Badia':'AB','Arabba':'AR','Belvedere - Col Rodella - Passo Pordoi':'BEL','Val di Fassa':'VF',
 'Les Trois Vallées':'3V','Val Thorens':'VT','Val Thorens - Orelle':'VTO','Courchevel':'CO','Méribel':'ME','Orelle':'OR'}
ids={}
for n,k in want.items():
    m=[i for nn,i in byname.items() if nn and nn.startswith(n.split(',')[0])]
    if m: ids[m[0]]=k
print('ids',{v:k[:10] for k,v in ids.items()},file=sys.stderr)
def km(g):
    c=g['coordinates']; t=g['type']; L=0
    lines=[c] if t=='LineString' else (c if t=='MultiLineString' else [])
    for ln in lines:
        for a,b in zip(ln,ln[1:]):
            dx=(b[0]-a[0])*111.32*math.cos(math.radians(a[1])); dy=(b[1]-a[1])*110.57; L+=math.hypot(dx,dy)
    return L
def cen(g):
    c=g['coordinates']
    pts=c if g['type']=='LineString' else [q for l in c for q in (l if isinstance(l[0][0],(int,float)) else [x for y in l for x in y])]
    return sum(q[1] for q in pts)/len(pts), sum(q[0] for q in pts)/len(pts)
def region(tags,lat,lon):
    out=set()
    if 'DS' in tags and 'SEIS' not in tags and 46.44<lat<46.62 and 11.66<lon<11.97: out.add('SELLA4')
    if 'DS' in tags and 'SEIS' not in tags and 46.53<lat<46.62 and 11.66<lon<11.80: out.add('GARDENA')
    for k in ('AB','AR','BEL','VF','3V','VT','CO','ME'):
        if k in tags: out.add(k)
    return out
st=collections.defaultdict(collections.Counter)
with open(runs) as fh:
    for line in fh:
        line=line.strip().rstrip(',')
        if not line.startswith('{"type":"Feature"'):continue
        f=json.loads(line);p=f['properties']
        if 'downhill' not in (p.get('uses') or []):continue
        tags={ids[s.get('properties',{}).get('id') if isinstance(s,dict) else s] for s in (p.get('skiAreas') or []) if (s.get('properties',{}).get('id') if isinstance(s,dict) else s) in ids}
        if not tags or f['geometry']['type'] not in ('LineString','MultiLineString'): continue
        lat,lon=cen(f['geometry'])
        for r in region(tags,lat,lon):
            c=st[r];L=km(f['geometry']);c['runs']+=1;c['km']+=L
            if p.get('name'):c['named']+=1;c['kmn']+=L
            if p.get('ref'):c['ref']+=1
            if p.get('name') or p.get('ref'):c['id']+=1;c['kmid']+=L
            if p.get('difficulty'):c['diff']+=1
            if p.get('grooming')=='backcountry' or p.get('difficulty')=='freeride':c['route']+=1
print(f"{'area':8} runs   km  named% ref% id% id_km% diff% routes")
for r in ['SELLA4','GARDENA','AB','AR','BEL','VF','3V','VT','CO','ME']:
    c=st.get(r)
    if not c: print(r,'none');continue
    n=c['runs'];print(f"{r:8} {n:4} {c['km']:6.1f} {100*c['named']//n:5} {100*c['ref']//n:4} {100*c['id']//n:4} {100*c['kmid']//c['km']:5} {100*c['diff']//n:5} {c['route']:4}")
