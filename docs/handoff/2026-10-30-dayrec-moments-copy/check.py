import json,re,sys
d=json.load(open(sys.argv[1]))
CAT={'he':{'one','two','other'},'en':{'one','other'},'ru':{'one','few','many','other'},'ka':{'one','other'}}
BAD={'he':['אתה ','בטוח?','בטיחות','הצלה','חירום','מהר יותר','מסוכן'],'en':['faster','safe','rescue'],'ru':['быстрее','безопас'],'ka':[]}
err=0
def ph(v): return set(re.findall(r'\{(\w+)\}',v if isinstance(v,str) else ' '.join(v.values())))
def chk(name,e,lists):
    global err
    if lists:
        n={len(e[L]) for L in CAT}
        if len(n)!=1: print('variant count',name,{L:len(e[L]) for L in CAT}); err+=1; return
        rows=list(zip(*[e[L] for L in CAT]))
    else: rows=[tuple(e[L] for L in CAT)]
    for i,row in enumerate(rows):
        sets=[ph(v) for v in row]
        if len({frozenset(x) for x in sets})!=1: print('placeholders',name,i,sets); err+=1
        for L,v in zip(CAT,row):
            if isinstance(v,dict) and set(v)!=CAT[L]: print('plural cats',name,L,set(v)); err+=1
            txt=v if isinstance(v,str) else ' '.join(v.values())
            for b in BAD[L]:
                if b in txt: print('word',name,L,b); err+=1
for k,e in d['shared'].items(): chk(k,e,False)
for t,ks in d['tones'].items():
    for k,e in ks.items(): chk(t+':'+k,e,True)
print('errors',err)
