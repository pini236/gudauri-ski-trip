# Measures, per ski area, how many OpenStreetMap downhill runs (as OpenSkiMap splits them) carry a name, a ref and a difficulty.
# Inputs (download first, ODbL, OpenSkiData / OpenSkiMap.org, (c) OpenStreetMap contributors):
#   https://tiles.openskimap.org/geojson/ski_areas.geojson
#   https://tiles.openskimap.org/geojson/runs.geojson   (about 830 MB, one feature per line)
# Run: python3 osm_metrics.py > osm_metrics.txt   (in the folder with both files). Result of 6.10.2026 in osm_metrics.txt.
import json,collections
want={"Gudauri":"Gudauri","Didveli":"Bakuriani Didveli","Kokhta":"Bakuriani Kokhta","Tetnuldi":"Tetnuldi","Hatsvali":"Hatsvali","Goderdzi Mountain":"Goderdzi","Bansko":"Bansko","Borovets":"Borovets","Pamporovo":"Pamporovo","אתר סקי הר החרמון":"Hermon","Роза Хутор":"Rosa Khutor","Шерегеш":"Sheregesh","Эльбрус, Elbrus":"Elbrus","Домбай":"Dombay","Shymbulak":"Shymbulak","Tsaghkadzor":"Tsaghkadzor","Sölden":"Sölden","Silvretta Arena":"Ischgl","Skicircus":"Saalbach","Mayrhofen":"Mayrhofen","St. Anton":"St Anton","Livigno":"Livigno","Val di Fassa":"Val di Fassa","Campiglio Dolomiti":"Madonna di Campiglio","Val Thorens - Orelle":"Val Thorens","Tignes - Val":"Tignes-Val d'Isère","Alpe d'Huez Grand":"Alpe d'Huez","GrandValira":"Grandvalira","Jasna Low":"Jasná","Ski Center Kopaonik":"Kopaonik","Kayseri-Erciyes":"Erciyes","Cerro Catedral":"Cerro Catedral","Valle Nevado":"Valle Nevado","Газпром":"Gazprom Krasnaya Polyana"}
ids={}
for f in json.load(open('ski_areas.geojson'))['features']:
    p=f['properties']
    if p.get('type')!='skiArea':continue
    n=p.get('name') or ''
    for k,v in want.items():
        if k in n and v not in ids.values():
            ids[p['id']]=v
st=collections.defaultdict(lambda: collections.Counter())
with open('runs.geojson') as fh:
    for line in fh:
        line=line.strip().rstrip(',')
        if not line.startswith('{"type":"Feature"'):continue
        f=json.loads(line);p=f['properties']
        if 'downhill' not in (p.get('uses') or []):continue
        for sa in p.get('skiAreas') or []:
            sid=sa.get('properties',{}).get('id') if isinstance(sa,dict) else sa
            if sid in ids:
                c=st[ids[sid]];c['runs']+=1
                if p.get('name'):c['named']+=1
                if p.get('ref'):c['ref']+=1
                if p.get('name') or p.get('ref'):c['id']+=1
                if p.get('difficulty'):c['diff']+=1
print(f"{'resort':26} runs named% ref% name_or_ref% diff%")
for v in want.values():
    c=st.get(v)
    if not c: print(v,'NO DATA');continue
    r=c['runs'];print(f"{v:26} {r:4} {100*c['named']//r:4} {100*c['ref']//r:4} {100*c['id']//r:4} {100*c['diff']//r:4}")
