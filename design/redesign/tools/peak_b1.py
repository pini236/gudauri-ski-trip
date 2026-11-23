"""Round 3, part 1: the mountain itself (K01 to K04)."""
from peak_boards import *
def peaks():
    return (lbl(352,186,'Sadzele','3,307 מ׳')+lbl(100,197,'Bidara','3,174 מ׳')+lbl(352,322,'Kudebi','3,006 מ׳')+lbl(166,684,'New Gudauri'))
def build():
    # ---- K01: the mountain, day and night
    b=pic('hero')+base('hero')+peaks()
    b+='''<div class="now glass"><span class="dot"></span><span>11 מתוך 12 רכבלים פתוחים · <span dir="ltr">−7°</span> בפסגה</span><span class="eg" style="margin-inline-start: auto">דוגמה</span></div>
<div class="tag" style="left: 88px; top: 118px; height: 26px; border-radius: 13px; direction: rtl"><span class="d" style="display: inline">10:35 · האור של עכשיו</span></div>
<div class="dock glass"><div class="find"><svg width="18" height="18" viewBox="0 0 18 18" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="8" cy="8" r="5.5"/><path d="M12.2 12.2 16 16"/></svg><span>לאן יורדים?</span></div>
<button class="rb" aria-label="שכבות"><svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M10 3 2.5 7 10 11l7.5-4z"/><path d="M2.5 11 10 15l7.5-4"/></svg></button>
<button class="rb rec" aria-label="מקליטים"><span></span></button></div>'''
    b=b.replace('<span class="d" style="display: inline">10:35 · האור של עכשיו</span>','<span class="d">10:35 · האור של עכשיו</span><span class="n">22:00 · אור ירח</span>')
    phone('K01-Mountain.dc.html','ההר, כמו נוף',b)

    # ---- K02: the light of the day, the sun where it really is
    L=[('dawn','09:00','זריחה','השמש 4° מעל האופק. רק הפסגות ורודות, העמק עוד בצל.'),('morning','10:35','בוקר','אור חם מדרום-מזרח, צללים ארוכים וכחולים.'),
       ('after','15:55','אחר הצהריים','השמש עברה לדרום-מערב. מה שהיה בצל בבוקר מואר עכשיו.'),('sunset','17:20','שקיעה','אור כתום אחרון על הרכסים, ופס ורוד באופק הצפוני.'),
       ('blue','18:20','השעה הכחולה','אין שמש ישירה. הכפר מדליק אורות.'),('night','22:00','לילה','אור ירח, כוכבים, חלונות הכפר והמסלול המואר.')]
    fr=''
    for k,t,n,c in L:
        ov=wl(P.details(VH,'night','night')) if k in('blue','night') else ''
        fr+='<div class="fr" style="width: 180px">%s<div><span class="tm">%s</span> <b style="font-size: 15px">%s</b></div><p class="cap">%s</p></div>'%(crop('hero',k,180,390,195,422,180/390,ov),t,n,c)
    wide('K02-Light.dc.html','האור של היום',('<div><p class="sec">ההר · 2 מתוך 4</p><h1>אותו הר, שש שעות</h1></div>'
      '<p class="lead">השמש מצוירת במקום שבו היא באמת נמצאת מעל גודאורי באמצע ינואר, והצל מחושב ממודל הגובה. לכן הצל בתמונה הוא הצל בהר: מי שמחפש מסלול בשמש רואה אותו בלי שכבה נוספת.</p>'
      '<div class="rowx" style="gap: 18px">%s</div>'
      '<div class="rowx"><div class="card" style="flex: 1"><h3>איך זה עובד באפליקציה</h3><ul><li>שש תמונות מוכנות למבט, כ-1.3MB יחד. האפליקציה בוחרת לפי השעה בגודאורי ומחליפה בדעיכה איטית.</li><li>בלילה נדלקים מעל התמונה חלונות הכפר והמסלול המואר (Pirveli, היחיד שמסומן מואר בנתונים).</li><li>מתג היום והלילה הקיים נשאר: אוטומטי, יום, לילה.</li></ul></div>'
      '<div class="card" style="flex: 1"><h3>ביושר</h3><ul><li>התמונות מחושבות ל-12 בינואר. בסוף העונה השמש גבוהה יותר, ולכן צריך סט שני למרץ.</li><li>השמיים, העננים הדקים והכוכבים הם ציור. הירח לא במקומו האמיתי.</li><li>הפס הוורוד בזריחה ובשקיעה קיים באמת בצד שמול השמש, אבל הגוון נבחר בעין.</li></ul></div></div>')%fr,1280,1010)

    # ---- K03: the weather on the mountain
    Wx=[('morning-fresh','שלג חדש','20 ס״מ בלילה: פחות סלע חשוף, ונצנוץ עדין שנע על המדרונות המוארים.','snow24 מהתחזית','ok','קיים בנתונים'),
        ('morning-wind','רוח ברכס','שלג שעף מהרכסים הגבוהים, בכיוון הרוח. כשהרוח חזקה, הרכבלים הגבוהים מסומנים "בסיכון".','רוח ב-700hPa ומשבים','ok','קיים בנתונים'),
        ('dawn-cloud','ים עננים','העמק מתחת לענן והפסגות מעליו. הבוקר הכי יפה שיש בהר.','גובה בסיס הענן','ask','צריך שדה חדש בתחזית'),
        ('noon-storm','יום לבן','אור שטוח, בלי צללים, והמסלולים המגוהצים בולטים יותר. שלג יורד כשכבה נעה.','קוד מזג אוויר וראות','ok','קיים בנתונים')]
    fr=''
    for k,n,c,src,cls,tg in Wx:
        fr+='<div class="fr" style="width: 272px">%s<h3>%s</h3><p class="cap">%s</p><p class="cap"><b>מאיפה:</b> %s</p><span class="tg %s" style="align-self: flex-start">%s</span></div>'%(crop('hero',k,272,440,195,360,272/390),n,c,src,cls,tg)
    wide('K03-Weather.dc.html','מזג האוויר על ההר',('<div><p class="sec">ההר · 3 מתוך 4</p><h1>ההר נראה כמו היום שבחוץ</h1></div>'
      '<p class="lead">במקום סמל של ענן ליד מספר, התמונה עצמה משתנה. פותחים את האפליקציה בבוקר ורואים מה מחכה למעלה עוד לפני שקוראים מילה. המספרים נשארים, בשורה אחת בראש המסך.</p>'
      '<div class="rowx" style="gap: 18px">%s</div>'
      '<div class="card"><h3>הכלל: לא מנחשים</h3><p class="p">כל מצב נדלק רק מנתון של התחזית שכבר מגיעה מהשרת (<span dir="ltr">/api/weather</span>). תחזית ישנה או חסרה: התמונה הרגילה של השעה, והשורה "אין מידע". דיווח חי של MTA על רכבל גובר על כל הערכה. ים העננים דורש שדה שעוד לא בחוזה (גובה בסיס הענן), ולכן הוא מסומן כשאלה לארכיטקט ולא כהבטחה.</p></div>')%fr,1280,1000)

    # ---- K04: the small things
    Vv,Vz=VV,VZ
    dv=wl(P.details(Vv,'day','morning'))+wl(P.runs_svg(Vv,'day'))+wl(P.lifts_svg(Vv,'day','morning'))
    dn=wl(P.details(Vv,'night','night'))+wl(P.runs_svg(Vv,'night'))+wl(P.lifts_svg(Vv,'night','night'))
    dz=wl(P.runs_svg(Vz,'day'))+wl(P.lifts_svg(Vz,'day','morning'))
    C=[(crop('vill','morning',372,250,255,275,1.18,dv),'הכפר','41 הבניינים הממופים של New Gudauri, כל אחד בצורה ובגובה שבנתונים, עם גג מושלג וצל לפי השמש. בלי בתים מומצאים.','ok','מהנתונים'),
       (crop('vill','night',372,250,255,275,1.18,dn),'הכפר בלילה','חלונות מוארים על הבניינים האמיתיים, ונקודות אור בתוך שטח הכפר שבמפה.','new','ציור מעל נתונים'),
       (crop('zoom','morning',372,250,255,330,1.25,dz),'רכבלים','כבל, עמודים וקרונות קטנים עם צל על השלג. כשהרכבל פתוח הקרונות נעים; כשסגור הם עומדים והכבל אפור.','ask','העמודים סמליים: מיקומם לא בנתונים'),
       (crop('zoom','morning',372,250,200,330,1.5),'מסלול מגוהץ','כל מסלול לחוץ מעט בשלג כרצועה חלקה, כמו אחרי חתול שלג, והשוליים שלו תופסים אור. רואים את הרשת גם לפני שמציירים עליה צבע.','ask','הרוחב סמלי (28 מ׳)'),
       (crop('hero','morning',372,250,100,330,1.9),'סלע ושלג','סלע רק במקום שתלול מדי לשלג, עם פסים בכיוון הנפילה. השלג מקבל אדוות רוח עדינות ונצנוץ מול השמש.','new','ציור: המרקם לא נמדד'),
       (crop('hero','sunset',372,250,195,150,1.25),'רכסים רחוקים','הרכסים שמעבר ל-Kobi הם מהמודל האמיתי, עומדים מול השמיים ונמוגים באובך. מעבר לקצה המודל אין הרים מצוירים.','ok','מהנתונים')]
    cells=''.join('<div class="fr" style="width: 372px">%s<div class="rowx" style="align-items: center; gap: 10px"><h3>%s</h3><span class="tg %s">%s</span></div><p class="cap">%s</p></div>'%(c,n,cls,tg,t) for c,n,t,cls,tg in C)
    wide('K04-Details.dc.html','הפרטים הקטנים',('<div><p class="sec">ההר · 4 מתוך 4</p><h1>הפרטים הקטנים</h1></div>'
      '<p class="lead">מה שהופך תמונה למקום. כל פרט מסומן: מהנתונים, ציור מעל נתונים, או סמל. מה שאין לו מקור לא מצויר: אין עצים (אין לנו נתוני יער), אין בתים מחוץ ל-41 הממופים, ואין הרים מעבר לקצה המודל.</p>'
      '<div class="grid" style="grid-template-columns: repeat(3, 372px); gap: 26px 22px">%s</div>')%cells,1280,1000)
