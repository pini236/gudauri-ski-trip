"""Round 3, part 3: beyond the mountain (K13 to K17) and the brief (K00)."""
from peak_boards import *
def tracks(V,mode,keys,lifts):
    o=''
    for k in keys:
        R=P.run_path(V,k)
        for ch in P.chunks(R['x'],R['y'],R['vis']):
            d=P.pathd(ch);o+='<path d="%s" stroke="#FFB85C" stroke-width="11" opacity=".16"/><path d="%s" stroke="#FFD9A0" stroke-width="5" opacity=".35"/><path d="%s" stroke="#FFF1D8" stroke-width="1.8"/>'%(d,d,d)
    for n in lifts:
        x,y,vis,z,q=P.project(V,P.flift(n)['pts'],lift=11)
        for ch in P.chunks(x,y,vis):o+='<path d="%s" stroke="#9FB6E8" stroke-width="1.1" stroke-dasharray="2 4" opacity=".8"/>'%P.pathd(ch)
    return svg(V,o)
def build():
    # ---- K13: the skier's day, moment by moment
    J=[('לפני הטיול','חלון להר','ההר של עכשיו, באור ובמזג האוויר האמיתיים, נפתח גם מהבית. הספירה לפתיחת העונה וכרטיס הסקי (סבבים 39 ו-40) יושבים על התמונה ולא בלוח נפרד.','ok','נתונים קיימים'),
       ('בוקר','בוקר על ההר','מסך פתיחה אחד: התמונה של הבוקר, הטמפרטורה בפסגה, כמה שלג ירד וכמה רכבלים פתוחים. נגיעה אחת, ואתה בהר.','new','לוח K14'),
       ('על הרכבל','כרטיס הרכבל','הרגע שבו כולם מוציאים טלפון. הירידה האחרונה בשורה אחת, כמה זמן נשאר למעלה, ומה יורד מהתחנה שאליה עולים. גם במסך הנעילה.','new','לוח K15'),
       ('בירידה','שקט','שום דבר לא קופץ ולא רוטט בזמן גלישה. עצרת? מספר אחד גדול: כמה נשאר עד הסוף. כפתורים לכפפות, 56 נקודות לפחות.','ask','מחזיר "מצב כפפות" (בוטל, החלטה 11)'),
       ('נפגשים','דגל בהר','נקודת המפגש היא דגל שנעוץ בתמונה, והשיתוף לוואטסאפ הוא התמונה עצמה עם הדגל והשעה. מי שמקבל רואה איפה, בלי לקרוא מפה.','ok','על נקודת המפגש הקיימת'),
       ('ערב','ציור היום','כל מה שגלשת היום נדלק על ההר הלילי כשבילי אור, עם "פה טסת" ושלושה מספרים. פוסטר אחד לשיתוף.','new','לוח K16'),
       ('אחרי','ההר שלי','בסוף הטיול: המסלולים שעשית נשארים דולקים, ומה שלא עשית נשאר עמום. סיבה לחזור.','ask','מחזיר "דרכון המסלולים" (בוטל, החלטה 11)'),
       ('בכל רגע','הכפר','כל מה שאינו מסלול ויש לו מקום (קופה, אוכל, חניה, נקודת המפגש) חי בכפר, שנכנסים אליו בנגיעה. בלי תפריט.','new','לוח K17')]
    cells=''.join('<div class="card" style="gap: 8px"><p class="sec" style="color: #56616D">%s</p><h2>%s</h2><p class="p" style="font-size: 14.5px">%s</p><span class="tg %s" style="align-self: flex-start; margin-top: auto">%s</span></div>'%j for j in J)
    wide('K13-Day.dc.html','היום של הגולש',('<div><p class="sec">מחוץ להר · 1 מתוך 5</p><h1>היום של הגולש, רגע אחרי רגע</h1></div>'
      '<p class="lead">ההר הוא הבית של האפליקציה, אבל רוב היום הגולש לא מסתכל במפה. לכל רגע ביום יש דבר אחד שהוא צריך, והאפליקציה מגישה אותו בלי שיחפש.</p>'
      '<div class="grid" style="grid-template-columns: repeat(4, 1fr); grid-auto-rows: 1fr">%s</div>'
      '<div class="card"><h3>שלושה עקרונות לכל המסכים</h3><ul><li><b>התמונה היא התשובה.</b> מזג אוויר, שעה, מה פתוח ואיפה הייתי: קודם רואים על ההר, ורק אחר כך קוראים מספר.</li><li><b>הרגע הנכון.</b> מידע מגיע כשיש יד פנויה: בבוקר, על הרכבל, בעצירה ובערב. אף פעם לא באמצע ירידה.</li><li><b>מקום, לא תפריט.</b> לדברים יש מקום בהר או בכפר, ומגיעים אליהם בנגיעה במקום עצמו. החיפוש והרשימה נשארים למי שמעדיף.</li></ul></div>')%cells,1280,1040)

    # ---- K14: morning
    b=pic('hero','dawn-cloud',None)
    b+=('<div style="position: absolute; top: 58px; right: 22px; left: 22px; color: #fff; text-shadow: 0 1px 12px rgba(20,30,80,.55)"><p style="font-size: 15px; font-weight: 500">בוקר טוב · יום ג׳ 12.1 · 08:40</p>'
        '<p style="font-size: 92px; font-weight: 300; line-height: 1; direction: ltr; text-align: right; margin-top: 6px">−9°</p><p style="font-size: 17px; font-weight: 500; margin-top: 2px">בפסגה · <span dir="ltr">−3°</span> בכפר · רוח חלשה</p></div>'
        '<span class="eg" style="position: absolute; top: 62px; left: 20px; color: #fff; border-color: rgba(255,255,255,.7)">דוגמה</span>')
    tiles=[('20','ס״מ שלג חדש','בלילה'),('11/12','רכבלים פתוחים','Sadzele ממתין'),('09:40','שמש בבסיס','עד אז צל')]
    b+='<div style="position: absolute; left: 12px; right: 12px; bottom: 84px; display: flex; gap: 8px">'+''.join('<div class="card glass" style="flex: 1; padding: 12px 12px; gap: 2px; border-radius: 18px"><span class="num" style="font-size: 28px">%s</span><span style="font-size: 13px; font-weight: 700">%s</span><span class="cap" style="font-size: 12px">%s</span></div>'%t for t in tiles)+'</div>'
    b+='<div style="position: absolute; left: 12px; right: 12px; bottom: 16px; display: flex; gap: 10px"><button class="pri" style="box-shadow: 0 8px 22px rgba(10,25,45,.3)">אל ההר</button><button class="sec glass" style="display: flex; align-items: center; gap: 8px"><span style="width: 12px; height: 12px; border-radius: 50%; background: #E5352B"></span>מקליטים</button></div>'
    phone('K14-Morning.dc.html','בוקר על ההר',b,fixed='day')

    # ---- K15: on the lift
    fan=[p['key'] for p in P.D['pistes'] if p.get('named') and 'Goodaura' in (p.get('fromLifts') or []) and p['key']!='Baby']
    def liftl(mode,light):
        o='<rect width="390" height="844" fill="%s"/>'%('rgba(40,62,110,.30)' if mode=='day' else 'rgba(2,6,18,.40)')
        x,y,vis,z,q=P.project(VH,P.flift('Goodaura')['pts'],lift=11);hl=''.join('<path d="%s" stroke="#fff" stroke-width="5" opacity=".9"/><path d="%s" stroke="#2F7BFF" stroke-width="2.6"/>'%(P.pathd(ch),P.pathd(ch)) for ch in P.chunks(x,y,vis))
        return P.details(VH,mode,light)+svg(VH,o)+P.runs_svg(VH,mode,'ghost',skip=fan,glow=False)+P.lifts_svg(VH,mode,light,dim=True)+svg(VH,''.join(glow_run(VH,k,mode,w=2.4) for k in fan)+hl)
    x,y,vis,z,q=P.project(VH,P.flift('Goodaura')['pts'],lift=11)
    if z[0]>z[-1]:x,y=x[::-1],y[::-1]
    T0=-120;i=int(len(x)*0.62);b=pic('hero',top=T0)+both(liftl,top=T0)+'<div class="you" style="left: %.0fpx; top: %.0fpx"></div>'%(x[i],y[i]+T0)
    for k in fan:
        Rk=P.run_path(VH,k);tx,ty,_,_,_,_=P.at(Rk,Rk['cum'][-1]*{'Tatra 1':0.3,'Tatra 2':0.62}.get(k,0.5));b+=tag(tx,ty+T0,Rk['p']['name'],HEX[Rk['p']['color']])
    b+=('<div class="now glass" style="flex-direction: column; align-items: stretch; gap: 8px; border-radius: 24px; padding: 12px 16px"><div style="display: flex; align-items: baseline; gap: 8px"><span style="font-size: 17px; font-weight: 700">על <span dir="ltr">Goodaura</span></span><span style="margin-inline-start: auto; font-size: 15px">עוד <b dir="ltr">2:30</b> דק׳ למעלה</span></div>'
        '<div style="height: 6px; border-radius: 3px; background: var(--line); overflow: hidden"><div style="width: 62%; height: 100%; background: #2F7BFF"></div></div></div>')
    b+=('<div class="sheet glass" style="gap: 8px"><div class="grab"></div><div class="rw" style="min-height: 0"><span class="cap" style="font-weight: 700">הירידה האחרונה</span><span class="eg" style="margin-inline-start: auto">דוגמה</span></div>'
        '<div class="rw" style="min-height: 40px">%s<b>Soliko 1</b><span class="r"><span dir="ltr">4:12</span> דק׳ · 52 קמ״ש מרבית</span></div>'
        '<p class="cap" style="font-weight: 700; margin-top: 4px">מלמעלה יורדים</p><div class="chipr">%s</div>'
        '<p class="sub" style="font-size: 12px">נפתח לבד כשההקלטה מזהה רכבל, וגם במסך הנעילה. בלי הקלטה: רק "מלמעלה יורדים", לפי המיקום.</p></div>')%(sign('Soliko 1'),''.join('<span class="ch">%s<span dir="ltr">%s</span></span>'%(sign(k),facts(k)['name']) for k in fan))
    phone('K15-OnTheLift.dc.html','על הרכבל',b)

    # ---- K16: the day drawn on the mountain
    day=['Soliko 1','Tatra 2','Tatra 1','Goodaura 1','Kudebi 1','Kudebi 2','Kikilo 1'];lf=['Goodaura','Soliko','Kudebi','Kikilo']
    b=pic('hero','night',None)+lay(P.details(VH,'night','night')+P.runs_svg(VH,'night','ghost',skip=day,glow=False)+tracks(VH,'night',day,lf))
    Rt=P.run_path(VH,'Tatra 2');px,py,_,_,_,_=P.at(Rt,900)
    b+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b style="background: #FFD9A0; color: #2A1600">פה טסת · 61 קמ״ש</b><i style="background: #FFD9A0"></i></div>'%(px,py-4)
    b+=('<div style="position: absolute; top: 58px; right: 22px; left: 22px; color: #fff"><p style="font-size: 30px; font-weight: 700; line-height: 1.15">היום שלך על ההר</p><p style="font-size: 14.5px; margin-top: 4px; color: #C9D6EA">יום סקי 3 מתוך 4 · 13.1.2027</p></div>'
        '<span class="eg" style="position: absolute; top: 66px; left: 20px">יום לדוגמה</span>')
    b+=('<div class="sheet glass" style="gap: 12px"><div class="nums" style="justify-content: space-between"><div><span class="num">11</span><span class="cap">ירידות</span></div><div><span class="num">21.4</span><span class="cap">ק״מ גלישה</span></div><div><span class="num">5,230</span><span class="cap">מ׳ ירידה</span></div></div>'
        '<div style="display: flex; gap: 10px"><button class="pri">שיתוף כפוסטר</button><button class="sec">הסיפור של היום</button></div></div>')
    phone('K16-DayDrawn.dc.html','ערב: ציור היום',b,night=True,fixed='night')

    # ---- K17: the village
    b=pic('vill')+base('vill')
    gx,gy,_,gz=P.pt(VV,*P.lift_ends(P.flift('Goodaura'))[0]);sx_,sy_,_,_=P.pt(VV,*P.lift_ends(P.flift('Soliko'))[0]);zx,zy,_,_=P.pt(VV,*P.lift_ends(P.flift('Zuma'))[0])
    nb={b_['n']:b_ for b_ in P.BLD['b'] if b_.get('n')}
    def bp(n):
        p=nb[n]['p'];e=sum(q[0] for q in p)/len(p);s=sum(q[1] for q in p)/len(p);return P.pt(VV,e,-s)
    tk=bp('Gondola Tickets');pal=bp('Gudauri Palace');ar=P.BLD['areas'][0]['p'];pk=P.pt(VV,sum(q[0] for q in ar)/len(ar),-sum(q[1] for q in ar)/len(ar))
    print('K17',[(n,round(bp(n)[0]),round(bp(n)[1])) for n in('Gondola Tickets','Gudauri Palace','Alpic','Atrium','Twins 1','Ski Pass Office')],'park',round(pk[0]),round(pk[1]),'G',round(gx),round(gy))
    b+=tag(gx+30,gy-58,'Goodaura ↑',None)+tag(sx_-28,sy_+30,'Soliko ↑',None)+tag(zx-8,zy+24,'Zuma',None)
    b+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>קופת כרטיסים</b><i></i></div>'%(tk[0]-6,tk[1]-12)
    al=bp('Alpic');b+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>אוכל ושתייה · 15</b><i></i></div>'%(al[0]+8,al[1]-10)
    b+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b>חניה</b><i></i></div>'%(pk[0],pk[1]-4)
    b+='<div class="pin" style="left: %.0fpx; top: %.0fpx"><b style="background: var(--acc); color: var(--onacc)">נפגשים · 16:30</b><i style="background: var(--acc)"></i></div>'%(sx_+2,sy_-12)
    b+='<div class="tag" style="left: 62px; top: 76px; height: 32px; border-radius: 16px; padding: 0 12px; direction: rtl; font-size: 13px">↑ חזרה להר</div>'
    b+=('<div class="glass" style="position: absolute; left: 0; right: 0; bottom: 0; height: 296px; border-radius: 28px 28px 0 0; padding: 10px 18px 14px; box-sizing: border-box; display: flex; flex-direction: column; gap: 4px">'
        '<div class="grab"></div><div style="margin: 6px 0 4px"><p class="ttl">הכפר</p><p class="sub"><span dir="ltr">New Gudauri</span> · <span dir="ltr">%s</span> מ׳ · 41 בניינים ממופים</p></div>'
        '<div class="rw"><span style="font-weight: 700">כרטיס סקי</span><span class="r">4 ימים · 390 ₾</span></div>'
        '<div class="rw"><span style="font-weight: 700">אוכל ושתייה</span><span class="r">15 מקומות עד 250 מ׳ מהבסיס</span></div>'
        '<div class="rw"><span style="font-weight: 700">החבר׳ה</span><span class="r">נפגשים 16:30, תחתית <span dir="ltr">Soliko</span> · דוגמה</span></div>'
        '<div class="rw"><span style="font-weight: 700">ערב בבקתה</span><span class="r">5 משחקים</span></div></div>')%('{:,}'.format(int(round(gz/10)*10)))
    phone('K17-Village.dc.html','הכפר',b)

    # ---- K00: the brief
    def li(*a):return '<ul>'+''.join('<li>%s</li>'%x for x in a)+'</ul>'
    Q=[('ההר החדש: שמיים, רכסים רחוקים, אור לפי השעה האמיתית ופרטים קטנים. זה הכיוון?','כן'),
       ('מזג האוויר על התמונה: שלג חדש, רוח ברכס ויום לבן עכשיו; ים עננים רק אחרי שהארכיטקט בודק אם יש נתון.','כן'),
       ('איך בוחרים: זכוכית מגדלת ו"מהתחנה" כדרכים הראשיות, והמדף במקום הרשימה של היום.','כן לשלושתם'),
       ('העדשה "בשמש עכשיו" מחזירה את מפת השמש שבוטלה בהחלטה 11. כאן היא כמעט בחינם, כי הצל כבר מחושב.','להחזיר'),
       ('סימון מסלול נבחר: "קו אור" ביום ו"חרוזי אור" בלילה.','כן'),
       ('מחוץ להר: במה להעמיק בסבב הבא? בוקר על ההר, כרטיס הרכבל, ציור היום, הכפר.','בוקר וציור היום קודם'),
       ('"ההר שלי" (מה שגלשת נשאר דולק) ו"שקט בירידה" עם כפתורי כפפות מחזירים שני דברים שבוטלו בהחלטה 11.','רק אם אתה רוצה')]
    qs=''.join('<div class="q"><span class="qn">%d</span><span>%s <b>(ההמלצה: %s)</b></span></div>'%(i+1,q,a) for i,(q,a) in enumerate(Q))
    ban=('<div class="th" style="width: 1160px; height: 400px; border-radius: 18px"><img alt="" src="%s" style="width: 1160px; height: 629px; top: -30px">'
         '<div style="position: absolute; right: 36px; top: 30px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.6)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב 3 · 19.11.2026</p><p style="font-size: 54px; font-weight: 900; line-height: 1.1; margin-top: 4px">ההר, הבחירה והיום</p></div></div>')%U('wide-dawn-cloud')
    body=(ban+
      '<p class="lead" style="max-width: 1100px">ביקשת הר יפה ומרשים יותר, עם פרטים קטנים, ורעיונות חדשים לכל החוויה: איך בוחרים מסלול, מה קורה כשבוחרים, ומה יש מחוץ לעמוד ההר. הכל כאן, בשלושה חלקים. התמונה למעלה היא ההר בזריחה מעל ים עננים, מצוירת מאותם נתונים שבריפו.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">חלק א · ההר</p><h2>נוף, לא מפה</h2>'+li('<b>K01</b> ההר החדש, ביום ובלילה','<b>K02</b> שש שעות של אור אמיתי','<b>K03</b> מזג האוויר על התמונה','<b>K04</b> הפרטים הקטנים, ומה אמיתי בכל אחד')+'</div>'
      '<div class="card" style="flex: 1"><p class="sec">חלק ב · המסלול</p><h2>בוחרים, ורואים</h2>'+li('<b>K05</b> זכוכית מגדלת מעל האצבע','<b>K06</b> מהתחנה: מה יורד מכאן','<b>K07</b> מדף המסלולים','<b>K08</b> עדשות: בשמש עכשיו','<b>K09</b> רגע הבחירה, פריים אחרי פריים','<b>K10</b> מה יש על מסלול נבחר','<b>K11</b> שלוש דרכים לסמן','<b>K12</b> לרדת בסרט')+'</div>'
      '<div class="card" style="flex: 1"><p class="sec">חלק ג · מחוץ להר</p><h2>היום כולו</h2>'+li('<b>K13</b> היום של הגולש, רגע אחרי רגע','<b>K14</b> בוקר על ההר','<b>K15</b> על הרכבל','<b>K16</b> ערב: ציור היום','<b>K17</b> הכפר')+'</div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.15"><h2>מה השתנה בהר מאז סבב 2</h2>'+li('<b>יש שמיים.</b> המבט "מתיישר" ככל שמתרחקים, ולכן הרכסים האמיתיים שמעבר ל-Kobi עומדים מול שמיים. עדיין נוסחה אחת לכל נקודה, עם שתי טבלאות קטנות.','<b>השמש במקומה.</b> שש שעות ביום, לפי מיקום השמש מעל גודאורי. הצל בתמונה הוא הצל בהר.','<b>חומר.</b> אדוות רוח בשלג, ברק מול השמש, סלע עם פסים בכיוון הנפילה, ומסלולים מגוהצים שלחוצים בשלג.','<b>דברים קטנים ואמיתיים.</b> 41 בנייני New Gudauri עם צל וחלונות, כבישים, נהרות, עמודים וקרונות.','<b>מזג אוויר.</b> שלג חדש, רוח ברכס, ים עננים ויום לבן.')+'</div>'
      '<div class="card" style="flex: 1"><h2>מה לא השתנה</h2>'+li('אין מודל תלת-ממד חי. הכל תמונות מוכנות, ושכבות חיות מעליהן.','אין הגזמת גובה, ואין קו שאינו בנתונים.','כל דבר מצויר מסומן בלוח K04: מהנתונים, ציור מעל נתונים, או סמל.','מה שאין לו מקור לא מצויר: אין עצים, אין בתים מומצאים, אין הרים מעבר לקצה המודל.','שלושת המבטים של סבב 2 (מהעמק, מלמעלה, מצד Kobi) נשארים. כאן צויר מחדש רק "מהעמק".')+'</div></div>'
      '<div class="card"><h2>שאלות אליך</h2>'+qs+'</div>'
      '<div class="card"><h2>ביושר</h2>'+li('מקרוב ההר עדיין רך: המודל בתאים של כ-40 מ׳, והכלי לא ממציא פרטים. בתמונת הכפר הבניינים נושאים את המראה, לא הקרקע.','מרקם השלג, העננים, השמיים והכוכבים הם ציור. עמודי הרכבל ורוחב המסלול המגוהץ הם סמלים.','התמונות מחושבות ל-12 בינואר. לסוף העונה צריך סט נוסף.','כל מספר שמסומן "דוגמה" מומצא לצורך ההדמיה (מזג אוויר, מצב רכבלים, יום גלישה). אורכים, גבהים, שמות וחיבורים מהנתונים.','בסבב 2 נכתב ל-Tatra 2 "28°". זה היה מספר ישן, מלפני תיקון הנתונים. לפי הכלל של האתר על הנתונים של היום הקטע התלול הוא 20°, וכך כתוב כאן; המספר והצבעים על הקו מאותו חישוב.','בדקתי את הלוחות ברינדור מקומי, לא בתוך הקנבס. גודל התמונות הוא של הטיוטה (כ-230KB לתמונה).','אם יאושר: סבב ליטוש, ואז סקירת ארכיטקט (תמונות כנכס חדש, נוסחת ההטלה כחוזה משותף, שדה לבסיס הענן) לפני כל קוד.')+'</div>')
    wide('K00-Brief.dc.html','סבב 3: ההר, הבחירה והיום',body,1280,2010)
