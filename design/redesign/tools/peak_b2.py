"""Round 3, part 2: choosing a run (K05 to K08) and a chosen run (K09 to K12)."""
from peak_boards import *
SEL='Tatra 2'
NOW='<div class="now glass"><span class="dot"></span><span>11 מתוך 12 רכבלים פתוחים · <span dir="ltr">−7°</span> בפסגה</span><span class="eg" style="margin-inline-start: auto">דוגמה</span></div>'
def touches(V,R,lim=60):
    out=[]
    for o in P.PISTES:
        if o['key']==R['p']['key'] or not o['named']:continue
        best=None
        for sg in o['segs']:
            for e in(sg[0],sg[-1]):
                dd=np.hypot(R['q'][:,0]-e[0],R['q'][:,1]-e[1]);i=int(np.argmin(dd))
                if dd[i]<lim and (best is None or dd[i]<best[0]):best=(dd[i],i)
        if best:
            i=best[1];out.append(dict(name=o['name'],color=o['color'],m=float(R['cum'][i]),x=float(R['x'][i]),y=float(R['y'][i]),vis=bool(R['vis'][i])))
    return sorted(out,key=lambda t:t['m'])
def steepest(R):
    cls=np.array([[j for j,(t,_) in enumerate(SLOPE_) if d<t][0] for d in R['deg']]);top=cls.max();best=(0,0,0);i=0
    while i<len(cls):
        if cls[i]==top:
            j=i
            while j+1<len(cls) and cls[j+1]==top:j+=1
            if R['cum'][j]-R['cum'][i]>best[0]:best=(R['cum'][j]-R['cum'][i],i,j)
            i=j+1
        else:i+=1
    return best,top
SLOPE_=P.SLOPE
def chosen(V,mode,uid,lifts_only=('Shino',),beads=True):
    """all the layers of a chosen run on a view, one svg"""
    o=corridor(V,SEL,mode,uid=uid)
    ln,R=light_line(V,SEL,mode)
    o+=ln
    if beads:
        for m in range(500,int(R['cum'][-1]),500):
            x,y,ax,ay,_,_=P.at(R,m)
            o+='<circle cx="%.1f" cy="%.1f" r="3.1" fill="#fff" stroke="#0E2233" stroke-width="1.2"/><text class="km" x="%.1f" y="%.1f" text-anchor="middle">%s</text>'%(x,y,x+ay*13,y-ax*13+3.4,('%.1f'%(m/1000)).rstrip('0').rstrip('.'))
    x0,y0=R['x'][0],R['y'][0];xe,ye=R['x'][-1],R['y'][-1]
    o+='<circle cx="%.1f" cy="%.1f" r="4.6" fill="#fff" stroke="#6B3FD4" stroke-width="2"/><circle cx="%.1f" cy="%.1f" r="3.6" fill="#6B3FD4" stroke="#fff" stroke-width="1.4"/>'%(x0,y0,xe,ye)
    return svg(V,o,' style="direction: ltr"'),R

def build():
    FH=facts(SEL)
    # ---- K05: the loupe
    R=P.run_path(VH,SEL);fx,fy,_,_,_,_=P.at(R,1300);i=int(np.searchsorted(R['cum'],1300));G=R['q'][i];zx,zy,_,_=P.pt(VZ,*G)
    D=196;lx=min(max(fx,D/2+10),390-D/2-10);ly=fy-158
    near=[]
    for o in P.PISTES:
        if not o['named'] or o['key']==SEL:continue
        best=None
        for sg in o['segs']:
            x,y,vis,z,q=P.project(VZ,sg);dd=np.hypot(x-zx,y-zy);j=int(np.argmin(dd))
            if vis[j] and (best is None or dd[j]<best[0]):best=(float(dd[j]),float(x[j]),float(y[j]))
        if best and best[0]<82 and math.hypot(best[1]-zx-34,best[2]-zy+4)>46:near.append((best,o))
    near=sorted(near,key=lambda t:t[0][0])[:3]
    def lens(mode,light):
        s=P.runs_svg(VZ,mode)+svg(VZ,glow_run(VZ,SEL,mode,w=4.4))+P.lifts_svg(VZ,mode,light)
        return s
    inner=''
    for m,cl,li in(('day','d','morning'),('night','n','night')):
        inner+='<div class="%s"><img alt="" src="%s" style="position: absolute; left: 0; top: 0; width: 390px; height: 560px; max-width: none">%s</div>'%(cl,U('zoom-'+li),''.join('<div class="ov" style="left: 0; top: 0">%s</div>'%x for x in(P.runs_svg(VZ,m),svg(VZ,glow_run(VZ,SEL,m,w=4.4)),P.lifts_svg(VZ,m,li))))
    tags=''.join(tag(b[1],b[2],o['name'],HEX[o['color']]) for b,o in near)+tag(zx+34,zy-4,'Tatra 2',HEX['blue'],' box-shadow: 0 0 0 2px var(--acc)')
    b=pic('hero')+base('hero')
    b+='<div style="position: absolute; left: %.0fpx; top: %.0fpx; width: 2px; height: %.0fpx; background: rgba(255,255,255,.8)"></div>'%(fx-1,ly+D/2,fy-ly-D/2-22)
    b+='<div style="position: absolute; left: %.0fpx; top: %.0fpx; width: 46px; height: 46px; border-radius: 50%%; background: rgba(255,255,255,.35); border: 2px solid rgba(255,255,255,.95); box-shadow: 0 2px 10px rgba(0,0,0,.3)"></div>'%(fx-23,fy-23)
    b+=('<div style="position: absolute; left: %.0fpx; top: %.0fpx; width: %dpx; height: %dpx; border-radius: 50%%; overflow: hidden; border: 3px solid #fff; box-shadow: 0 14px 34px rgba(5,15,40,.45), 0 0 0 1px rgba(14,34,51,.25); background: #9db6d8">'
        '<div style="position: absolute; left: %.0fpx; top: %.0fpx; width: 390px; height: 560px">%s%s</div></div>')%(lx-D/2,ly-D/2,D,D,D/2-zx,D/2-zy,inner,tags)
    b+=NOW+('<div class="sheet glass" style="gap: 8px"><div class="rw" style="min-height: 0">%s<span class="ttl" dir="ltr">Tatra 2</span><span class="r">כחול · %s</span></div>'
            '<p class="sub">נגיעה ארוכה פותחת זכוכית מגדלת מעל האצבע. מחליקים, והמסלול הקרוב נדלק. מרימים, והוא נבחר. נגיעה קצרה על קו בוחרת מיד, והרשימה והחיפוש נשארים.</p></div>')%(sign(SEL,True),km(FH['len']))
    phone('K05-Loupe.dc.html','בוחרים: זכוכית מגדלת',b)

    # ---- K06: from the station
    fan=[p['key'] for p in P.D['pistes'] if p.get('named') and 'Goodaura' in (p.get('fromLifts') or [])]
    tx,ty,_,tz=P.pt(VH,*P.lift_ends(P.flift('Goodaura'))[1])
    def fanl(mode,light):
        o='<rect width="390" height="844" fill="%s"/>'%('rgba(40,62,110,.30)' if mode=='day' else 'rgba(2,6,18,.40)')
        return P.details(VH,mode,light)+svg(VH,o)+P.runs_svg(VH,mode,'ghost',skip=fan,glow=False)+P.lifts_svg(VH,mode,light,dim=True)+P.lifts_svg(VH,mode,light,only=['Goodaura'])+svg(VH,''.join(glow_run(VH,k,mode,w=3.2) for k in fan))
    T0=-120;b=pic('hero',top=T0)+both(fanl,top=T0)
    for k in fan:
        Rk=P.run_path(VH,k);x,y,_,_,_,_=P.at(Rk,Rk['cum'][-1]*{'Tatra 1':0.3,'Tatra 2':0.62}.get(k,0.5))
        if k!='Baby':b+=tag(x,y+T0,Rk['p']['name'],HEX[Rk['p']['color']])
    b+='<div class="ring" style="left: %.0fpx; top: %.0fpx; width: 26px; height: 26px"></div>'%(tx,ty+T0)
    rows=''
    for k in sorted(fan,key=lambda k:-facts(k)['len']):
        f=facts(k);back=' · חוזרים ב-<span dir="ltr">%s</span>'%f['to'][0] if f['to'] else ''
        rows+='<div class="rw" style="min-height: 40px">%s<b>%s</b><span class="r">%s%s</span></div>'%(sign(k),f['name'],km(f['len']) if k!='Baby' else km(f['len'])+' · אזור מתחילים',back if k!='Baby' else '')
    b+=NOW+('<div class="sheet glass" style="gap: 6px"><div class="grab"></div><div><p class="ttl">מראש <span dir="ltr">Goodaura</span></p><p class="sub"><span dir="ltr">%s</span> מ׳ · %d מסלולים יורדים מכאן</p></div>%s'
            '<p class="sub" style="font-size: 12px">נוגעים בתחנה, ומה שיורד ממנה נדלק. כש"איפה אני" דלוק, זה נפתח לבד כשיורדים מהרכבל.</p></div>')%('{:,}'.format(int(round(tz))),len(fan),rows)
    phone('K06-FromStation.dc.html','בוחרים: מהתחנה',b)

    # ---- K07: the shelf
    shelf=['Kudebi 3','Kudebi 1','Kudebi 2'];cur='Kudebi 1'
    def shl(mode,light):
        o='<rect width="390" height="844" fill="%s"/>'%('rgba(40,62,110,.26)' if mode=='day' else 'rgba(2,6,18,.36)')
        return P.details(VH,mode,light)+svg(VH,o)+P.runs_svg(VH,mode,'ghost',skip=[cur],glow=False)+P.lifts_svg(VH,mode,light,dim=True)+P.lifts_svg(VH,mode,light,only=['Kudebi'])+svg(VH,glow_run(VH,cur,mode,w=3.6))
    T0=-120;b=pic('hero',top=T0)+both(shl,top=T0)
    Rk=P.run_path(VH,cur);x,y,_,_,_,_=P.at(Rk,Rk['cum'][-1]*0.5);b+=tag(x-44,y+T0,'Kudebi 1',HEX['red'])
    cards=''
    for n,k in enumerate(shelf):
        f=facts(k);left={0:330,1:79,2:-172}[n];on=k==cur
        back='חוזרים ב-<span dir="ltr">%s</span> · %s דק׳'%(f['to'][0],str(lift(f['to'][0])['dur'])) if f['to'] else 'ממשיכים במסלול אחר'
        cards+=('<div class="card glass" style="position: absolute; left: %dpx; bottom: 22px; width: 232px; height: 156px; flex-direction: row; gap: 12px; align-items: stretch;%s">'
                '<div style="flex: none; display: flex; align-items: center">%s</div><div style="display: flex; flex-direction: column; gap: 6px; min-width: 0; flex: 1">'
                '<div style="display: flex; align-items: center; gap: 8px">%s<b style="font-size: 17px; direction: ltr">%s</b></div><p class="sub">%s · %s</p>'
                '<div class="nums" style="gap: 14px"><div><span class="num" style="font-size: 24px">%s</span><span class="cap">ק״מ</span></div><div><span class="num" style="font-size: 24px">%d</span><span class="cap">מ׳ ירידה</span></div></div>'
                '<p class="sub" style="font-size: 12px">%s</p></div></div>')%(left,'' if on else ' opacity: .72;',mini(k),sign(k),f['name'],HE[f['color']],'בינוני' if f['color']=='red' else 'קל','%.1f'%(f['len']/1000),f['drop'],back)
    b+=NOW+'<div class="chipr" style="position: absolute; right: 12px; left: 12px; bottom: 192px"><span class="ch on">לפי רכבל</span><span class="ch">מהקל לקשה</span><span class="ch">הארוכים</span><span class="ch">בשמש עכשיו</span></div>'
    b+='<div class="call" style="right: 16px; bottom: 236px; font-size: 13px">Kudebi · 3 מסלולים</div>'
    b+=cards
    phone('K07-Shelf.dc.html','בוחרים: מדף המסלולים',b)

    # ---- K08: lenses (in the sun now)
    o='';ns=nsh=0
    for p in P.PISTES:
        if not p['named']:continue
        tot=0;sun_=0
        for s in p['segs']:
            x,y,vis,z,q=P.project(VH,s);sh=shaded(q,'dawn');tot+=len(sh);sun_+=int((~sh).sum())
            for flag,col,w,op in((False,'#FFC93A',3.0,1),(True,'#B9CCFA',1.8,.95)):
                m=vis&(sh==flag)
                for ch in P.chunks(x,y,m):
                    d=P.pathd(ch)
                    if not flag:o+='<path d="%s" stroke="#FFE9A0" stroke-width="9" opacity=".35"/><path d="%s" stroke="#fff" stroke-width="5"/>'%(d,d)
                    o+='<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%s"/>'%(d,col,w,op)
        if p['kind']=='run':
            if sun_*2>=tot:ns+=1
            else:nsh+=1
    b=pic('hero','dawn',None)+lay(P.details(VH,'day','dawn')+P.lifts_svg(VH,'day','dawn',dim=True)+svg(VH,o))
    b+=NOW+'<div class="chipr" style="position: absolute; right: 12px; left: 12px; top: 106px"><span class="ch on">בשמש עכשיו</span><span class="ch">פתוח</span><span class="ch">קל ← קשה</span><span class="ch">שלג חדש</span></div>'
    b+=('<div class="sheet glass" style="gap: 8px"><div class="rw" style="min-height: 0"><span class="num">%d</span><div><p style="font-weight: 700; font-size: 16px">מסלולים בשמש עכשיו</p><p class="sub">ו-%d עוד בצל · 09:00</p></div></div>'
        '<div class="leg"><span><i style="background: #FFC93A"></i>בשמש</span><span><i style="background: #B9CCFA"></i>בצל ההר</span></div>'
        '<p class="sub" style="font-size: 12.5px">עדשה צובעת את ההר לפי שאלה אחת. הצל מחושב ממודל הגובה ומיקום השמש, לא מתחזית; ענן לא נלקח בחשבון.</p></div>')%(ns,nsh)
    phone('K08-Lenses.dc.html','בוחרים: עדשות',b,fixed='day')

    # ---- K09: the moment of choosing, frame by frame
    Rh=P.run_path(VH,SEL);tx_,ty_,_,_,_,_=P.at(Rh,1300)
    f1=wl(P.runs_svg(VH,'day'))+wl(P.lifts_svg(VH,'day','morning'))+wl(svg(VH,'<circle cx="%.0f" cy="%.0f" r="26" stroke="#fff" stroke-width="2" opacity=".9"/><circle cx="%.0f" cy="%.0f" r="14" fill="#fff" opacity=".55"/>'%(tx_,ty_,tx_,ty_)))
    part=dict(Rh);part['vis']=Rh['vis']&(Rh['cum']<Rh['cum'][-1]*0.56);hx,hy,_,_,_,_=P.at(Rh,Rh['cum'][-1]*0.56)
    f2=wl(svg(VH,'<rect width="390" height="844" fill="rgba(54,78,128,.42)"/>'))+wl(P.runs_svg(VH,'day','ghost',skip=[SEL]))+wl(svg(VH,''.join('<path d="%s" stroke="#fff" stroke-width="16" opacity=".35"/><path d="%s" stroke="#fff" stroke-width="6"/>'%(P.pathd(ch),P.pathd(ch)) for ch in P.chunks(part['x'],part['y'],part['vis']))+P.slope_paths(part,'3.6')+'<circle cx="%.1f" cy="%.1f" r="13" fill="#fff" opacity=".5"/><circle cx="%.1f" cy="%.1f" r="5" fill="#fff"/>'%(hx,hy,hx,hy)))
    cz,Rz=chosen(VZ,'day','s3');f3=wl(P.runs_svg(VZ,'day','ghost',skip=[SEL]))+wl(P.lifts_svg(VZ,'day','morning',dim=True))+wl(cz)
    plate='<div style="position: absolute; left: %.0fpx; top: %.0fpx; transform: translate(-86%%,-100%%); display: flex; flex-direction: column; align-items: flex-end"><span style="height: 24px; padding: 0 8px; border-radius: 6px; background: #1B6FDB; color: #fff; font-size: 12px; font-weight: 700; display: inline-flex; align-items: center; gap: 5px; border: 1.5px solid #fff; direction: ltr">7A · Tatra 2</span><i style="width: 2px; height: 12px; background: #fff; margin-inline-end: 12px"></i></div>'%(Rz['x'][0],Rz['y'][0]-4)
    sh4='<div style="position: absolute; left: 6px; right: 6px; bottom: 6px; border-radius: 16px; background: rgba(255,255,255,.92); padding: 10px 12px; display: flex; flex-direction: column; gap: 6px"><div style="display: flex; gap: 8px; align-items: center"><span style="min-width: 28px; height: 20px; border-radius: 5px; background: #1B6FDB; color: #fff; font-size: 11.5px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center">7A</span><b dir="ltr" style="font-size: 15px">Tatra 2</b></div><div style="display: flex; gap: 14px; font-size: 12px; color: #40556A"><span><b style="font-size: 17px; font-weight: 300; color: #0E2233">%s</b> ק״מ</span><span><b style="font-size: 17px; font-weight: 300; color: #0E2233">%d</b> מ׳ ירידה</span><span><b style="font-size: 17px; font-weight: 300; color: #0E2233" dir="ltr">%d°</b> תלול</span></div></div>'%('%.1f'%(FH['len']/1000),FH['drop'],FH['steep'])
    F=[(crop('hero','morning',234,506,195,422,0.6,f1),'1 · נגיעה','0 מ״ש','עיגול קטן יוצא מהאצבע. רטט קל אחד.'),
       (crop('hero','morning',234,506,195,422,0.6,f2),'2 · האור יורד','0 עד 600 מ״ש','שאר ההר נכנס לצל. אור יורד במסלול מלמעלה למטה, בקצב של גולש, וצובע אותו לפי השיפוע.'),
       (crop('zoom','morning',234,506,195,280,0.905,f3+plate),'3 · מתקרבים','300 עד 900 מ״ש','התמונה מתחלפת בדעיכה לתמונה הקרובה של אותו צד. שלט המסלול ננעץ בראש. רטט שני.'),
       (crop('zoom','morning',234,506,195,280,0.905,f3+plate)[:-6]+sh4+'</div>','4 · הפרטים עולים','900 מ״ש','הגיליון עולה עם שלושה מספרים. מכאן: "לרדת בסרט", סרטונים, שיתוף.')]
    fr=''.join('<div class="fr" style="width: 234px">%s<h3>%s</h3><span class="tm" style="font-size: 15px; font-weight: 500; direction: rtl">%s</span><p class="cap">%s</p></div>'%f for f in F)
    wide('K09-Moment.dc.html','רגע הבחירה',('<div><p class="sec">מסלול נבחר · 1 מתוך 4</p><h1>רגע הבחירה, פריים אחרי פריים</h1></div>'
      '<p class="lead">בחירת מסלול היא הרגע שחוזר הכי הרבה באפליקציה, ולכן הוא צריך להרגיש כמו משהו שקורה בהר ולא כמו פתיחת חלון. שנייה אחת, ארבעה שלבים.</p>'
      '<div class="rowx" style="gap: 54px; justify-content: center">%s</div><div class="card"><h3>הכללים של המעבר</h3><ul style="display: grid; grid-template-columns: 1fr 1fr; gap: 7px 40px"><li>אותו מעבר מכל דרך בחירה: נגיעה, זכוכית מגדלת, מדף, תחנה, חיפוש או קישור.</li><li>האור יורד מלמעלה למטה, כי כך גולשים. אף פעם לא מלמטה למעלה.</li><li>בלי צליל. שני רטטים קצרים בלבד, ורק אם הרטט דולק בהגדרות.</li><li>בהפחתת תנועה: דעיכה אחת של 200 מ״ש, בלי אור שיורד ובלי התקרבות.</li><li>ביטול: נגיעה בשטח ריק מחזירה את כל ההר באותה דעיכה.</li><li>לא חוסם: אפשר לבחור מסלול אחר באמצע המעבר.</li></ul></div>')%fr,1280,1080)

    # ---- K10: the chosen run and what is on it
    def runl(mode,light):
        cz,R=chosen(VZ,mode,'k10'+mode)
        return P.details(VZ,mode,light)+P.runs_svg(VZ,mode,'ghost',skip=[SEL],glow=False)+P.lifts_svg(VZ,mode,light,dim=True)+P.lifts_svg(VZ,mode,light,only=['Shino'])+cz
    Rz=P.run_path(VZ,SEL);(ln_,i0,i1),top=steepest(Rz);sx,sy=float(Rz['x'][(i0+i1)//2]),float(Rz['y'][(i0+i1)//2])
    tch=[t for t in touches(VZ,Rz) if t['vis']]
    print('K10 touches',[(t['name'],round(t['m']),round(t['x']),round(t['y'])) for t in tch],'steep',round(ln_),round(Rz['cum'][i0]),round(sx),round(sy),'top',round(Rz['x'][0]),round(Rz['y'][0]),'end',round(Rz['x'][-1]),round(Rz['y'][-1]))
    b=pic('zoom')+both(runl)
    b+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>%s<span dir="ltr">Tatra 2</span></b><i></i></div>'%(Rz['x'][0],Rz['y'][0]-5,sign(SEL))
    b+='<div class="call" style="left: %.0fpx; top: %.0fpx">הכי תלול כאן<small>%s · לאורך %d מ׳</small></div>'%(sx+14,sy-14,['עד 15°','15° עד 25°','25° עד 30°','מעל 30°'][top],round(ln_/10)*10)
    seen=[]
    for t in tch:
        if any(abs(t['y']-s)<26 for s in seen):continue
        seen.append(t['y']);b+=tag(t['x']+(54 if t['x']<120 else -52),t['y']+(0 if t['m']>100 else 14),'↔ '+t['name'],HEX[t['color']])
    lx_,ly_,_,_=P.pt(VZ,*P.lift_ends(P.flift('Shino'))[0])
    b+=tag(lx_+62,ly_+20,'<span dir="ltr">Shino ↑</span>&nbsp;7.8 דק׳',None,' direction: rtl')
    px_,py_,_,_,_,_=P.at(Rz,900);b+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b style="background: var(--acc); color: var(--onacc)">פה טסת · 61 קמ״ש</b><i style="background: var(--acc)"></i></div>'%(px_,py_-4)
    b+=('<div class="glass" style="position: absolute; left: 0; right: 0; bottom: 0; height: 296px; border-radius: 28px 28px 0 0; padding: 10px 18px 18px; box-sizing: border-box; display: flex; flex-direction: column; gap: 12px">'
        '<div class="grab"></div><div class="rw" style="min-height: 0">%s<div><p class="ttl" dir="ltr" style="text-align: right">Tatra 2</p><p class="sub">כחול · קל · מראש <span dir="ltr">Goodaura</span> עד <span dir="ltr">Shino</span></p></div></div>'
        '<div class="nums"><div><span class="num">%s</span><span class="cap">ק״מ אורך</span></div><div><span class="num">%d</span><span class="cap">מ׳ ירידה</span></div><div><span class="num">%d°</span><span class="cap">הקטע התלול</span></div></div>'
        '<div class="leg"><span><i style="background: #3FA85F"></i>עד 15°</span><span><i style="background: #F2C13D"></i>15° עד 25°</span><span><i style="background: #F08A3C"></i>25° עד 30°</span><span><i style="background: #DC3B33"></i>מעל 30°</span></div>'
        '<div style="display: flex; gap: 10px; margin-top: auto"><button class="pri">לרדת בסרט</button><button class="sec">סרטונים</button><button class="sec" aria-label="שיתוף" style="padding: 0"><svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M10 13V3M6 6.5 10 3l4 3.5M4 11v5h12v-5"/></svg></button></div></div>')%(sign(SEL,True),'%.1f'%(FH['len']/1000),FH['drop'],FH['steep'])
    phone('K10-Run.dc.html','מסלול נבחר: מה יש עליו',b)

    # ---- K11: three ways to mark it
    cx,cy,_,_,_,_=P.at(Rz,1250)
    a_,_=chosen(VZ,'day','m1');A_=wl(P.runs_svg(VZ,'day','ghost',skip=[SEL]))+wl(a_)
    rib='';pol=''
    for ch in P.chunks(Rz['x'],Rz['y'],Rz['vis']):rib+='<path d="%s" stroke="#fff" stroke-width="13" opacity=".62"/><path d="%s" stroke="#1B6FDB" stroke-width="1.2" stroke-dasharray="1 4" opacity=".8"/>'%(P.pathd(ch),P.pathd(ch))
    for m in range(40,int(Rz['cum'][-1]),70):
        x,y,ax,ay,_,_=P.at(Rz,m)
        for s in(-1,1):pol+='<circle cx="%.1f" cy="%.1f" r="1.5" fill="#1B6FDB" stroke="#fff" stroke-width=".5"/>'%(x-ay*7.4*s,y+ax*7.4*s)
    B_=wl(P.runs_svg(VZ,'day','ghost',skip=[SEL]))+wl(svg(VZ,rib+pol))
    bd=''
    for ch in P.chunks(Rz['x'],Rz['y'],Rz['vis']):bd+='<path d="%s" stroke="#FFD08A" stroke-width="14" opacity=".2"/>'%P.pathd(ch)
    for m in range(0,int(Rz['cum'][-1]),42):
        x,y,ax,ay,_,dg=P.at(Rz,m);cc=[c2 for t,c2 in P.SLOPE if dg<t][0]
        bd+='<circle cx="%.1f" cy="%.1f" r="2.3" fill="#FFF3D6"/><circle cx="%.1f" cy="%.1f" r="4.4" fill="%s" opacity=".4"/>'%(x,y,x,y,cc)
    C_=wl(svg(VZ,corridor(VZ,SEL,'night',uid='m3')))+wl(P.runs_svg(VZ,'night','ghost',skip=[SEL],glow=False))+wl(svg(VZ,bd))
    M=[(crop('zoom','morning',372,330,cx,cy,1.5,A_),'א · קו אור','המסלול במסדרון מואר, והקו צבוע לפי השיפוע. חיצים קטנים בכיוון הירידה, וחרוז כל חצי קילומטר.','ok','ההמלצה ליום'),
       (crop('zoom','morning',372,330,cx,cy,1.5,B_),'ב · סרט מגוהץ','רצועת שלג חלקה, ועמודי סימון קטנים בצבע המסלול משני הצדדים, כמו בהר עצמו. יפה ושקט, אבל הצבע חלש והשיפוע לא נראה.','ask','חלופה'),
       (crop('zoom','night',372,330,cx,cy,1.5,C_),'ג · חרוזי אור','בלילה הקו הופך לשרשרת של אורות, כמו פנסים לאורך מסלול מואר. צבע השיפוע נשאר כהילה סביב כל אור.','ok','ההמלצה ללילה')]
    cells=''.join('<div class="fr" style="width: 372px">%s<div class="rowx" style="align-items: center; gap: 10px"><h3>%s</h3><span class="tg %s">%s</span></div><p class="cap">%s</p></div>'%(c,n,cls,tg,t) for c,n,t,cls,tg in M)
    wide('K11-Marking.dc.html','איך מסמנים מסלול נבחר',('<div><p class="sec">מסלול נבחר · 3 מתוך 4</p><h1>שלוש דרכים לסמן מסלול</h1></div>'
      '<p class="lead">אותו קטע של Tatra 2, שלושה סימונים. בכל השלושה שאר המסלולים נשארים כקו דק בצבע שלהם, כדי שתמיד יהיה ברור מה מתחבר למה.</p>'
      '<div class="rowx" style="gap: 22px">%s</div>'
      '<div class="card"><h3>מה תמיד נכון, בכל סימון</h3><ul><li>צבע המסלול הוא הצבע הרשמי. צבעי השיפוע (ירוק, צהוב, כתום, אדום) מופיעים רק על מסלול נבחר, עם מקרא.</li><li>קטע בלי קו בנתונים לא מושלם בסימון. הקו נעצר, כמו היום.</li><li>קטע שמוסתר מאחורי רכס לא מצויר על הרכס. במבט מלמעלה רואים את כולו.</li></ul></div>')%cells,1280,820)

    # ---- K12: skiing it before skiing it
    M0=1240
    def ridel(mode,light):
        cz,R=chosen(VZ,mode,'k12'+mode,beads=False)
        return P.runs_svg(VZ,mode,'ghost',skip=[SEL],glow=False)+P.lifts_svg(VZ,mode,light,dim=True)+cz
    ux,uy,_,_,uz,ud=P.at(Rz,M0);nxt=[t for t in touches(VZ,Rz) if t['m']>M0+60]
    b=pic('zoom')+both(ridel)+'<div class="you" style="left: %.0fpx; top: %.0fpx"></div>'%(ux,uy)
    rail='';Lr=Rz['cum'][-1];Hh=400;y_=0;i=0;deg=Rz['deg']
    while i<len(deg)-1:
        cc=[c2 for t,c2 in P.SLOPE if deg[i]<t][0];j=i
        while j+1<len(deg)-1 and [c2 for t,c2 in P.SLOPE if deg[j+1]<t][0]==cc:j+=1
        rail+='<rect x="9" y="%.1f" width="8" height="%.1f" fill="%s"/>'%(Rz['cum'][i]/Lr*Hh,max(1,(Rz['cum'][j+1]-Rz['cum'][i])/Lr*Hh+.5),cc);i=j+1
    ty=M0/Lr*Hh
    b+=('<div class="glass" style="position: absolute; left: 10px; top: 64px; width: 30px; height: %dpx; border-radius: 15px; padding: 12px 0; box-sizing: content-box"><svg viewBox="0 0 26 %d" width="30" height="%d" style="display: block; direction: ltr">'
        '<rect x="9" y="0" width="8" height="%d" rx="4" fill="rgba(120,140,170,.3)"/><g clip-path="inset(0 round 4px)">%s</g><circle cx="13" cy="%.1f" r="9" fill="#fff" stroke="#2F7BFF" stroke-width="3"/></svg></div>')%(Hh,Hh,Hh,Hh,rail,ty)
    nx='בעוד %d מ׳: מצטרף <span dir="ltr">%s</span>'%(round((nxt[0]['m']-M0)/10)*10,nxt[0]['name']) if nxt else 'עד הסוף בלי הצטלבויות'
    b+=('<div class="glass" style="position: absolute; left: 0; right: 0; bottom: 0; height: 296px; border-radius: 28px 28px 0 0; padding: 10px 18px 18px; box-sizing: border-box; display: flex; flex-direction: column; gap: 12px">'
        '<div class="grab"></div><div class="rw" style="min-height: 0">%s<p class="ttl" dir="ltr">Tatra 2</p><span class="r">%s מתוך %s</span></div>'
        '<div class="nums"><div><span class="num">%s</span><span class="cap">מ׳ גובה</span></div><div><span class="num">%d°</span><span class="cap">השיפוע כאן</span></div><div><span class="num">%s</span><span class="cap">ק״מ עד הסוף</span></div></div>'
        '<p class="sub" style="font-size: 14px; color: var(--ink)">%s</p>'
        '<div style="display: flex; gap: 10px; margin-top: auto"><button class="pri">▶ ממשיכים לרדת</button><button class="sec" dir="ltr">×8</button></div>'
        '<p class="sub" style="font-size: 12px">גוררים את הנקודה על הפס או על ההר. רטט קצר בכל 100 מ׳ ירידה, חזק יותר בקטע תלול.</p></div>')%(sign(SEL,True),km(M0),km(Lr),'{:,}'.format(int(round(uz))),round(ud),'%.1f'%((Lr-M0)/1000),nx)
    phone('K12-Ride.dc.html','מסלול נבחר: לרדת בסרט',b)
