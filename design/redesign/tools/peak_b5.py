"""Round 4, part 2: the day after it ends (L08 to L12), outside the app (L05), honest states (L13) and the brief (L00)."""
from peak_b4 import *
def stroke(mode):return '#22303F' if mode=='day' else '#EAF1F8'
def build(ctx):
    T0=D.FIN['start'];dstr=ctx['dstr'];mx=D.ST['max_at'];fall=D.FIN['falls'][0];LONG=D.DESC[9]
    back='<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></svg>'
    # ---- L09: one descent as its own film
    def b09():
        FW,FH=150,600;o='<div class="recp glass">%s<span>הירידה הארוכה של היום</span><span style="margin-inline-start: auto">%s</span></div>'%(back,EG)
        marks={}
        for mode in('day','night'):
            f,at=D.film(LONG,FW,FH,mode,pad=16,sw=6);px,py=at(LONG['max_at']['t']);sx,sy=at(LONG['stops'][0]['t0']);gx,gy=at(D.FIN['gaps'][0]['t0']+30000);x0,y0=at(LONG['t0']);x1,y1=at(LONG['t1'])
            f+='<circle cx="%.1f" cy="%.1f" r="6" fill="#fff" stroke="#E0246E" stroke-width="2.6"/>'%(px,py)
            f+='<circle cx="%.1f" cy="%.1f" r="4.4" fill="%s" stroke="%s" stroke-width="1.6"/>'%(sx,sy,'#fff' if mode=='day' else '#0B1220',stroke(mode))
            f+='<circle cx="%.1f" cy="%.1f" r="4.6" fill="#fff" stroke="#6B3FD4" stroke-width="2"/><circle cx="%.1f" cy="%.1f" r="4" fill="#6B3FD4" stroke="#fff" stroke-width="1.4"/>'%(x0,y0,x1,y1)
            marks[mode]=f;pts=dict(p=(px,py),s=(sx,sy),g=(gx,gy),a=(x0,y0),b=(x1,y1))
        L0,TP=14,104
        o+='<div style="position: absolute; left: %dpx; top: %dpx; direction: ltr"><svg class="d" viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round" stroke-linejoin="round">%s</svg><svg class="n" viewBox="0 0 %d %d" width="%d" height="%d" fill="none" stroke-linecap="round" stroke-linejoin="round">%s</svg></div>'%(L0,TP,FW,FH,FW,FH,marks['day'],FW,FH,FW,FH,marks['night'])
        NOTES=[]
        def note(k,ttl,sub):
            NOTES.append([pts[k][1],pts[k][0],ttl,sub]);return ''
        def flush():
            NOTES.sort(key=lambda q:q[0]);ys=[q[0] for q in NOTES]
            for i in range(1,len(ys)):ys[i]=max(ys[i],ys[i-1]+52)
            for i in range(len(ys)-2,-1,-1):ys[i]=min(ys[i],ys[i+1]-52)
            NX=L0+FW+26;h=''
            for (y,x,ttl,sub),yy in zip(NOTES,ys):
                h+='<div style="position: absolute; left: %dpx; top: %.0fpx; width: %dpx; transform: translateY(-50%%); display: flex; flex-direction: column; gap: 1px"><b style="font-size: 14px; line-height: 1.3">%s</b><span class="sub" style="font-size: 12.5px; line-height: 1.3">%s</span></div>'%(NX,TP+yy,390-NX-14,ttl,sub)
                h+='<svg style="position: absolute; left: 0; top: 0; overflow: visible" width="1" height="1"><path d="M%.1f %.1f L%.1f %.1f L%.1f %.1f" fill="none" stroke="var(--ink2)" stroke-width="1" opacity=".55"/></svg>'%(L0+x+9,TP+y,NX-22,TP+yy,NX-6,TP+yy)
            return h
        legs=[l for l in LONG['legs'] if l['kind']=='run' and len(l['runs'])==1];seen=[]
        _,at=D.film(LONG,FW,FH,'day',pad=16,sw=6)
        o+=note('a','<span class="tmr">%s</span> · ראש <span dir="ltr">Sadzele</span>'%D.hm(LONG['t0']),'%s מ׳ מעל הים'%n0(LONG['top_m']))
        for l in legs:
            r=l['runs'][0]
            if r in seen:continue
            seen.append(r);x,y=at((l['t0']+l['t1'])//2)
            o+='<div style="position: absolute; left: %.0fpx; top: %.0fpx; transform: translate(-100%%,-50%%); direction: ltr">%s</div>'%(L0+x-12,TP+y,sign(r))
        o+=note('p','פה טסת · <span dir="ltr">%.1f</span> קמ״ש'%LONG['max_kmh'],'ב-<span dir="ltr">Sadzele 1</span>, ב-%s'%D.hm(LONG['max_at']['t']))
        o+=note('s','עצירה אחת','%s שניות, ב-%s'%(int((LONG['stops'][0]['t1']-LONG['stops'][0]['t0'])/1000),D.hm(LONG['stops'][0]['t0'])))
        o+=note('g','דקה בלי קליטה','הקו מקווקו. לא משלימים את מה שלא נמדד.')
        o+=note('b','<span class="tmr">%s</span> · תחתית <span dir="ltr">Goodaura</span>'%D.hm(LONG['t1']),'%s מ׳ מעל הים'%n0(LONG['bottom_m']))+flush()
        o+='<div class="card glass" style="position: absolute; left: 12px; right: 12px; bottom: 12px; gap: 8px; padding: 12px 16px"><div class="nums" style="gap: 18px"><div><span class="num">%.1f</span><span class="cap">ק״מ</span></div><div><span class="num">%s</span><span class="cap">מ׳ למטה</span></div><div><span class="num">%s</span><span class="cap">דק׳ בתנועה</span></div><span class="spd" style="margin-inline-start: auto; align-self: flex-end; padding-bottom: 4px"><span>לאט</span><i style="width: 44px"></i><span>מהר</span></span></div></div>'%(LONG['dist_m']/1000,n0(LONG['drop_m']),ms(LONG['moving_s']))
        return o
    phone('L09-RunFilm.dc.html','סבב 4 · 9 · ירידה אחת כסרט',b09())
    # ---- L10: the story of the day, in the approved voice
    it=D.MOM['items'];fc=[i for i in it if i['key']=='first_color' and i['p']['color']=='black'][0];fa=[i for i in it if i['key']=='fastest'][0];stp=[i for i in it if i['key']=='steepest'][0]
    def b10():
        def card(ttl,line,w=None,extra=''):
            return '<div class="card glass" style="gap: 8px; padding: 12px 14px; box-shadow: none"><div style="display: flex; gap: 12px; align-items: center">%s<div style="display: flex; flex-direction: column; gap: 4px; min-width: 0"><b style="font-size: 16.5px; line-height: 1.25">%s</b><span class="sub">%s</span></div></div>%s</div>'%(w or '',ttl,line,extra)
        def spot(lat,lon,sc=2.3,mk='pin'):
            x,y,_,_=D.ll(VH,lat,lon)
            def f(V,mode):
                s=D.day(V,mode,w=2.2,lifts=False)
                if mk=='pin':s+='<circle cx="%.1f" cy="%.1f" r="3.4" fill="#fff" stroke="#E0246E" stroke-width="1.6"/>'%(x,y)
                else:s+='<g transform="translate(%.1f %.1f)" stroke="%s" stroke-width=".9"><circle r="4.6" fill="%s"/><path d="M0 -2.800V2.800M-2.400 -1.400 2.400 1.400M2.400 -1.400-2.400 1.400"/></g>'%(x,y,stroke(mode),'rgba(255,255,255,.92)' if mode=='day' else 'rgba(10,18,34,.9)')
                return s
            return win('hero',76,76,x,y,sc,f,day='noon',r=14)
        i=int(np.searchsorted(D.T,fc['t']));e,n_=D.Q[i]
        o='<div class="recp glass">%s<span>הסיפור של היום · <span class="tmr" style="font-weight: 500">%s</span></span><span style="margin-inline-start: auto">%s</span></div>'%(back,dstr,EG)
        o+='<div style="position: absolute; left: 14px; right: 14px; top: 108px; display: flex; flex-direction: column; gap: 9px">'
        o+='<div style="padding: 0 4px 2px"><div class="ttl" style="font-size: 27px">יום של פעם ראשונה</div><div class="sub">%d ירידות · %s מ׳ למטה · %.1f ק״מ</div></div>'%(D.ST['descents'],n0(D.ST['drop_m']),D.ST['ski_dist_m']/1000)
        la,lo=(lambda q:q)((fa['p']['at']['lat'],fa['p']['at']['lon']))
        o+=card('השחור הראשון שלך','<span dir="ltr">Sadzele 1</span>, ב-%s, ביום הגלישה 1 שלך. גם השחורים כבר על המפה שלך.'%D.hm(fc['t']),spot(la+0.0009,lo,2.0))
        o+=card('המסלול הכי תלול שלך','<span dir="ltr">Sadzele 1</span>, עם קטע של כ-%d°. המסלול הכי תלול של היום שלך.'%stp['p']['deg'],'<div class="ic" style="width: 76px; height: 76px; border-radius: 14px; background: var(--line)"><span class="num" style="font-size: 30px">%d°</span></div>'%stp['p']['deg'])
        o+=card('הרגע הכי מהיר שלך','<span dir="ltr">%.1f</span> קמ״ש ב-<span dir="ltr">Sadzele 1</span>, בירידה %d מתוך %d. עד אז הרגליים כבר היו חמות.'%(fa['p']['kmh'],fa['p']['nth'],fa['p']['of']),spot(la,lo,2.6))
        o+=card('נשיקה לשלג?','נראה שנשקת לשלג ב-<span dir="ltr">Goodaura 1</span>, ב-%s?'%D.hm(fall['t']),spot(fall['lat'],fall['lon'],2.6,'fall'),'<div class="ask"><button>כן, היה</button><button>לא נפלתי</button></div>')
        o+=card('רעיון למחר','נשאר שחור אחד שעוד לא גלשת: <span dir="ltr">Sadzele 2</span>. אחריו, כל השחורים כאן שלך.','<div class="ic" style="width: 76px; height: 76px; border-radius: 14px; background: #E4E9F0">%s</div>'%mini('Sadzele 2',34,66))
        o+='</div>'+fade(800,44)
        return o
    phone('L10-Story.dc.html','סבב 4 · 10 · הסיפור של היום',b10())
    # ---- L11: the picture to share. no name, no fall, no off-piste
    def b11():
        PW,PH=318,398;sc=PW/390*1.18
        def f(V,mode):
            px,py,_,_=D.ll(V,mx['lat'],mx['lon']);return D.day(V,mode,w=3.0)+'<circle cx="%.1f" cy="%.1f" r="4.6" fill="#fff" stroke="#E0246E" stroke-width="2.2"/>'%(px,py)
        o=pic('hero','noon','night')+veil(0,844,'var(--veil)')
        o+='<div class="recp glass">%s<span>תמונה לשיתוף</span><span style="margin-inline-start: auto">%s</span></div>'%(back,EG)
        o+='<div style="position: absolute; left: 36px; top: 112px; width: %dpx; height: %dpx; border-radius: 20px; overflow: hidden; box-shadow: 0 18px 44px rgba(6,14,30,.45)">%s'%(PW,PH,win('hero',PW,PH,196,400,sc,f,day='noon',r=0))
        o+='<div style="position: absolute; left: 0; right: 0; bottom: 0; height: 190px; background: linear-gradient(180deg, transparent, rgba(8,14,28,.86) 62%)"></div>'
        o+='<div style="position: absolute; left: 16px; right: 16px; bottom: 14px; color: #fff; display: flex; flex-direction: column; gap: 8px"><div style="font-size: 25px; font-weight: 700; line-height: 1.15">יום של פעם ראשונה</div><div style="display: flex; gap: 18px">%s</div><div style="display: flex; justify-content: space-between; font-size: 11.5px; opacity: .85; direction: ltr"><span>Gudi</span><span>Gudauri · %s</span></div></div></div>'%(''.join('<div style="display: flex; flex-direction: column; gap: 2px"><span style="font-size: 27px; font-weight: 300; line-height: 1; direction: ltr">%s</span><span style="font-size: 11px; opacity: .85">%s</span></div>'%(a,b) for a,b in((str(D.ST['descents']),'ירידות'),(n0(D.ST['drop_m']),'מ׳ למטה'),('%.1f'%(D.ST['ski_dist_m']/1000),'ק״מ'),('%.1f'%D.ST['max_kmh'],'קמ״ש, מרבית'))),dstr)
        o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div class="seg"><button class="on">תמונה</button><button>סרטון של דקה</button></div><div class="sub">בלי שם, בלי נפילות ובלי הקטע מחוץ למסלולים. מה שיוצא מהטלפון הוא רק התמונה הזו, כשבוחרים לשתף.</div><div style="display: flex; gap: 8px"><button class="pri">שיתוף</button><button class="sec">שמירה</button></div></div>'
        return o
    phone('L11-Share.dc.html','סבב 4 · 11 · תמונה לשיתוף',b11())
    # ---- L12: my days, and the trip as layers on one mountain
    parts=[D.DESC[0:3],D.DESC[3:8],D.DESC[8:12]];heads=['יום רגוע','יום של גילויים','יום של פעם ראשונה'];cols={'day':['#2F7BFF','#8A4FE0','#E0246E'],'night':['#6CB4FF','#B99CFF','#FFF2FA']}
    def b12():
        def trip(V,mode):
            return ''.join(D.track(V,d['t0'],d['t1'],mode,w=3.0,solid=cols[mode][k]) for k,ds in enumerate(parts) for d in ds)
        o='<div class="recp glass">%s<span>הימים שלי</span><span style="margin-inline-start: auto">%s</span></div>'%(back,EG)
        o+='<div style="position: absolute; left: 14px; right: 14px; top: 108px; display: flex; flex-direction: column; gap: 10px">'
        o+='<div style="position: relative">%s<div style="position: absolute; left: 0; right: 0; bottom: 0; height: 130px; border-radius: 0 0 20px 20px; background: linear-gradient(180deg, transparent, rgba(8,14,28,.84) 64%%)"></div><div style="position: absolute; left: 14px; right: 14px; bottom: 12px; color: #fff; display: flex; flex-direction: column; gap: 3px"><b style="font-size: 20px">הטיול שלך על ההר</b><span style="font-size: 13.5px; opacity: .9">3 ימי גלישה, %s מ׳ למטה, %d מסלולים.</span></div><div style="position: absolute; left: 12px; top: 12px; display: flex; gap: 6px; direction: ltr">%s</div></div>'%(win('hero',362,300,196,410,1.02,trip,day='after',r=20),n0(D.ST['drop_m']),len(D.MOM['history']['runs']),''.join('<span style="height: 22px; padding: 0 8px; border-radius: 11px; background: rgba(8,14,28,.62); color: #fff; font-size: 11px; font-weight: 700; display: inline-flex; align-items: center; gap: 5px"><i style="width: 8px; height: 8px; border-radius: 50%%; background: %s"></i>יום %d</span>'%(cols['night'][k],k+1) for k in range(3)))
        for k in(2,1,0):
            ds=parts[k]
            def one(V,mode,ds=ds):return ''.join(D.track(V,d['t0'],d['t1'],mode,w=3.4) for d in ds)
            o+='<div class="card glass" style="flex-direction: row; align-items: center; gap: 12px; padding: 10px; box-shadow: none">%s<div style="display: flex; flex-direction: column; gap: 3px; min-width: 0"><span class="sub" style="font-size: 12px">יום %d בטיול</span><b style="font-size: 16.5px">%s</b><span class="sub">%d ירידות · %s מ׳ למטה · %.1f ק״מ</span></div><span style="margin-inline-start: auto; color: var(--ink2)"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 6-6 6 6 6"/></svg></span></div>'%(win('hero',84,84,196,420,.34,one,day='noon',r=12),k+1,heads[k],len(ds),n0(sum(d['drop_m'] for d in ds)),sum(d['dist_m'] for d in ds)/1000)
        o+='<div class="sub" style="font-size: 12px; padding: 0 4px">דוגמה: היום לדוגמה מחולק כאן לשלושה ימים, כדי להראות טיול. מחיקה של יום או של הכל: בהחלקה על השורה.</div></div>'
        return o
    phone('L12-MyDays.dc.html','סבב 4 · 12 · הימים שלי והטיול כשכבות',b12())
