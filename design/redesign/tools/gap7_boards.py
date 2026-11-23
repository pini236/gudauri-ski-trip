#!/usr/bin/env python3
"""Completion round, part 7 (V00 to V16): languages and platforms. The fonts for four languages (one stack:
Heebo, then Roboto for Cyrillic, then Noto Sans Georgian), three screens in Russian, in Georgian and in
English built only from the strings that already exist in i18n/strings.json (the live day while standing,
the ski pass, the group's meetups), and six Android boards for parts 2 to 5.
The people, the group and the meetups are invented examples; the prices and the translations are real.
Design only. Usage: python3 design/redesign/tools/gap7_boards.py"""
import os,json,re
from peak_boards import *
import gap_boards as GB
import gap3_boards as G3
import gap4_boards as G4
import gap5_boards as G5
import gap6_boards as G6
from peak_b4 import EG,n0,veil,fade
from gap_boards import CHEV,XI,li,STOP
from gap3_boards import ic,rl,sw,CHL,P_BELL,P_PLUS,P_MORE
PROJ=OUT
STR=json.load(open(os.path.join(P.ROOT,'i18n/strings.json'),encoding='utf8'));STR=STR.get('strings',STR)
FONT4='<link href="https://fonts.googleapis.com/css2?family=Heebo:wght@300;400;500;700;900&family=Roboto:wght@300;400;500;700;900&family=Noto+Sans+Georgian:wght@300;400;500;700;900&display=swap" rel="stylesheet">'
STACK="'Heebo','Roboto','Noto Sans Georgian',system-ui,sans-serif"
LN=dict(he='עברית',en='English',ru='Русский',ka='ქართული')
def cat(lang,n):
    if lang=='he':return 'one' if n==1 else 'two' if n==2 else 'other'
    if lang=='ru':
        if n%10==1 and n%100!=11:return 'one'
        if n%10 in(2,3,4) and n%100 not in(12,13,14):return 'few'
        return 'many'
    return 'one' if n==1 else 'other'
def T(lang,key,**kw):
    v=STR[key][lang]
    if isinstance(v,dict):v=v.get(cat(lang,kw.get('n',0)),v['other'])
    for k,x in kw.items():v=v.replace('{%s}'%k,str(x))
    return v
PEOPLE=dict(he=('נועה','דנה','נ','גודאורי 2027'),en=('Noa','Dana','N','Gudauri 2027'),ru=('Ноа','Дана','Н','Гудаури 2027'),ka=('ნოა','დანა','ნ','გუდაური 2027'))
DUR=dict(he='שעה ו-12 דק׳',en='1 h 12 min',ru='1 ч 12 мин',ka='1 სთ 12 წთ')
def eg(lang):return '<span class="eg">%s</span>'%T(lang,'app.day_sample')
def back(lang):return CHEV if lang=='he' else CHL
CSS11="""
.H .scr[dir=ltr] .now.me{left:12px;right:64px}
.H .scr[dir=ltr] .meb{left:auto;right:12px}
.H .and .dock2{bottom:26px}
.H .and .mc{bottom:100px}
"""
WC7="""
.HW .sp4{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}
.HW .fc{background:#fff;border:1px solid #E3E7EC;border-radius:16px;padding:18px 18px 16px;display:flex;flex-direction:column;gap:10px;min-width:0}
.HW .fc .ln{font-size:13px;font-weight:700;color:#6B3FD4;letter-spacing:.02em}
.HW .fc .fn{font-size:12.5px;color:#5A6673}
.HW .fc .big{font-size:54px;font-weight:300;line-height:1}
.HW .fc .h{font-size:25px;font-weight:700;line-height:1.15}
.HW .fc .b{font-size:15px;line-height:1.5;color:#2A3440}
.HW .fc .s{font-size:12px;color:#5A6673}
.HW .fc .btn{height:48px;border-radius:24px;background:#6B3FD4;color:#fff;font-size:16px;font-weight:700;display:flex;align-items:center;justify-content:center;padding:0 16px;text-align:center}
.HW .lt{width:100%;border-collapse:collapse;font-size:13px}
.HW .lt td,.HW .lt th{padding:6px 8px;border-bottom:1px solid #E3E7EC;text-align:start;vertical-align:top}
.HW .lt th{font-size:12px;color:#5A6673;font-weight:700}
.HW .lt .n{font-variant-numeric:tabular-nums;white-space:nowrap;color:#5A6673}
.HW .lt .hot{color:#B3261E;font-weight:700}
"""
MB.CSS=MB.CSS+CSS11
def metop(lang,text):
    """the top of the mountain for any direction: the pill, and the "you" button at the end side"""
    a='<span class="av" style="background: #7A5AE0" aria-hidden="true">%s</span>'%PEOPLE[lang][2]
    return ('<div class="now glass me" style="left: 64px"><span class="dot"></span><span style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis">%s</span></div>'%text
            +'<span style="position: absolute; top: 106px; inset-inline-end: 12px">%s</span>'%eg(lang)
            +'<button class="xb glass meb" aria-label="" style="position: absolute; top: 52px; left: 12px; padding: 0">%s</button>'%a)
# ---- the three screens, for any language
s1=GB.seg_at(24);t1=s1['t0']+20000
def standing(lang):
    n,drop,dist=GB.sofar(t1);mx=max([d['max_kmh'] for d in GB.D.DESC if d['t1']<=t1] or [0])
    o=GB.mount(t1,sc=1.3,at=330)
    o+='<div class="recp glass"><span class="rdot"></span><span>%s</span><span style="margin-inline-start: auto">%s</span></div>'%(T(lang,'app.rec_now',time='<span class="tmr" style="margin: 0">%s</span>'%GB.hms(t1-GB.T0)),eg(lang))
    o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div style="display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap"><span class="ttl" style="font-size: 19px">%s</span><span class="sub">%s</span></div>'%(T(lang,'app.rec_standing'),T(lang,'app.rec_day_so_far'))
    o+='<div class="kv4"><div><b>%d</b><span>%s</span></div><div><b>%s</b><span>%s</span></div><div><b>%.1f</b><span>%s</span></div><div><b>%.1f</b><span>%s</span></div></div>'%(n,T(lang,'app.rec_runs'),n0(drop),T(lang,'app.rec_m_down'),dist/1000,T(lang,'app.rec_km_ski'),mx,T(lang,'app.rec_kmh_max'))
    o+=GB.ridge(t1)
    o+='<div style="display: flex; gap: 8px"><button class="sec" style="flex: 1">%s</button><button class="sec" style="display: flex; align-items: center; gap: 8px; white-space: nowrap">%s%s</button></div></div>'%(T(lang,'app.rec_see_day'),STOP,T(lang,'app.rec_stop'))
    return o
PRICE={1:120,2:215,3:305,4:390,5:470,6:545}
def skipass(lang):
    INFO='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true" style="flex: none; margin-top: 1px"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 7.600v.200"/></svg>'
    lari=lambda v:'<bdi>%d ₾</bdi>'%v
    o=pic('hero','morning','night')+veil(0,844,'var(--veil)')
    o+='<div class="recp glass" style="background: transparent; border-color: transparent; box-shadow: none; -webkit-backdrop-filter: none; backdrop-filter: none; padding: 0 6px">%s<span class="ttl" style="font-size: 21px">%s</span></div>'%(back(lang),T(lang,'app.skipass_title'))
    o+='<div style="position: absolute; left: 12px; right: 12px; top: 118px; display: flex; flex-direction: column; align-items: center; gap: 8px; text-align: center; text-shadow: 0 0 22px var(--halo), 0 0 6px var(--halo)"><span class="cd" style="direction: ltr">390<span style="font-size: 40px; letter-spacing: 0; margin-inline-start: 6px">₾</span></span><b style="font-size: 17px">%s</b><span style="font-size: 14px"><span>%s</span> · <b>%s</b></span></div>'%(T(lang,'app.skipass_hero',n=4,who=T(lang,'app.skipass_adult')),T(lang,'app.skipass_vs_daily',n=4,price=lari(480)),T(lang,'app.skipass_save',amount=lari(90),pct=19))
    o+='<div class="sheet glass" style="gap: 12px"><div class="grab"></div><div class="seg"><button class="on">%s</button><button>%s</button></div>'%(T(lang,'app.skipass_adult'),T(lang,'app.skipass_child',**{'from':6,'to':12}))
    o+='<div style="display: flex; align-items: center; gap: 12px"><button class="stp" aria-label="%s">−</button><div style="flex: 1; display: flex; flex-direction: column; align-items: center; gap: 1px; text-align: center"><b style="font-size: 19px">4 %s</b><span class="sub" style="font-size: 12.500px">%s · <bdi>11–14.1</bdi></span></div><button class="stp" aria-label="%s">+</button></div>'%(T(lang,'app.skipass_days_less'),T(lang,'app.skipass_days'),T(lang,'app.skipass_from_trip'),T(lang,'app.skipass_days_more'))
    o+='<div style="display: flex; gap: 5px">%s</div>'%''.join('<div class="pc%s"><b>%d</b><span>%d ₾</span></div>'%(' on' if d==4 else '',d,PRICE[d]) for d in(1,2,3,4,5,6))
    o+='<div class="note">%s<span>%s %s</span></div>'%(INFO,T(lang,'app.skipass_card',deposit=lari(5)),T(lang,'app.skipass_foreign'))
    o+='<div class="rw" style="border-top: 1px solid var(--line); min-height: 44px"><span class="sub">%s</span></div>'%T(lang,'app.skipass_night',price=lari(80))
    o+='<div class="rw" style="border-top: 1px solid var(--line); min-height: 44px"><span style="font-size: 14px; font-weight: 500">%s</span><span class="r" style="color: var(--acc); font-weight: 700">SKIPASS</span></div>'%T(lang,'app.skipass_buy')
    o+='<div class="sub" style="font-size: 12px">%s · %s</div></div>'%(T(lang,'app.skipass_checked',date='<bdi>15.11.2026</bdi>',src='MTA'),T(lang,'app.skipass_footer',season='<bdi>2026/27</bdi>'))
    return o
def meetups(lang):
    me,other,_,grp=PEOPLE[lang]
    o=pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+G3.flagat(VH,'Goodaura',1,'13:00')
    o+=metop(lang,T(lang,'status.bar_summary',open=11,total=14,ago='').split(' · ')[0])
    alt=n0(float(P.elev(*G6.GT)));bot=n0(float(P.elev(*P.lift_ends(P.flift('New Goodaura'))[0])))
    inv='<button class="sec" style="height: 44px; min-width: 44px; padding: 0 14px; display: flex; align-items: center; gap: 6px; font-size: 14px; white-space: nowrap">%s %s</button>'%(ic(P_PLUS,18),T(lang,'app.g_invite_short'))
    head='<div style="display: flex; align-items: center; gap: 10px; min-height: 48px"><div style="display: flex; flex-direction: column; gap: 1px; min-width: 0"><span class="ttl">%s</span><span class="sub">%s · <bdi>%s</bdi></span></div><span style="margin-inline-start: auto; display: flex; align-items: center; gap: 6px">%s</span></div>'%(grp,T(lang,'app.g_members',n=6),T(lang,'app.trip_range',**{'from':'10.1','to':'15.1'}),inv)
    tabs='<div class="seg">%s</div>'%''.join('<button class="%s" style="flex: 1 1 auto; white-space: nowrap; padding: 0 %dpx; font-size: %gpx">%s</button>'%('on' if i==0 else '',6 if lang=='ka' else 10,13 if lang=='ka' else 14,T(lang,k)) for i,k in enumerate(('app.g_tab_meetups','app.g_tab_flights','group.tab_scores','app.g_tab_members')))
    i='<div class="box" style="border: 1px solid var(--line); padding: 10px 14px 0; display: flex; flex-direction: column; gap: 6px"><div style="display: flex; align-items: center; gap: 8px"><span class="st">%s</span><b style="margin-inline-start: auto; color: var(--acc); font-size: 14px; white-space: nowrap">%s</b></div><div style="display: flex; align-items: flex-end; gap: 12px"><span class="num" style="font-size: 40px">13:00</span><span style="display: flex; flex-direction: column; gap: 1px; min-width: 0; padding-bottom: 2px"><b style="font-size: 17px; direction: ltr; unicode-bidi: isolate; text-align: start">Goodaura</b><span class="sub" style="font-size: 12.500px">%s</span></span></div>'%(T(lang,'app.grp_next_meetup'),T(lang,'app.g_in',time=DUR[lang]),T(lang,'app.meet_station_top',alt=alt))
    i+='<div style="display: flex; align-items: center; gap: 8px; min-height: 44px; border-top: 1px solid var(--line)">%s<span style="font-size: 14px">%s</span><span style="margin-inline-start: auto">%s</span></div></div>'%(ic(P_BELL,20),T(lang,'app.g_remind'),sw(True))
    i+='<div>'+rl('','<bdi>16:30</bdi> · <bdi>New Goodaura</bdi>',T(lang,'app.g_set_by',name=me),ic(P_BELL,20))+rl('','%s <bdi>09:30</bdi> · <bdi>Goodaura</bdi>'%T(lang,'app.grp_tomorrow'),T(lang,'app.g_set_by',name=other),ic(P_BELL,20))+'</div>'
    i+='<button class="pri" style="flex: none">%s</button>'%T(lang,'app.g_new_meetup')
    return o+'<div class="sheet glass" style="top: 292px; gap: 10px; overflow: hidden"><div class="grab"></div>%s%s%s</div>'%(head,tabs,i)+fade(790,54)
SCR=(('Standing','עומדים: היום עד עכשיו',standing),('SkiPass','כרטיס סקי',skipass),('Meetups','הקבוצה: מפגשים',meetups))
LG=(('ru','רוסית'),('ka','גאורגית'),('en','אנגלית'))
def fix(fn,lang):
    """a board in a language that is not Hebrew: the direction, the language and the font stack"""
    p=os.path.join(OUT,fn);s=open(p,encoding='utf8').read()
    s=s.replace(MB.FONT,FONT4).replace('<html lang="he">','<html lang="%s">'%lang).replace('<html lang="en">','<html lang="%s">'%lang)
    s=s.replace('<div class="scr" dir="rtl">','<div class="scr" dir="ltr" lang="%s" style="font-family: %s">'%(lang,STACK)).replace('<div class="scr" dir="ltr">','<div class="scr" dir="ltr" lang="%s" style="font-family: %s">'%(lang,STACK))
    open(p,'w',encoding='utf8').write(s)
def inner(src):
    s=open(os.path.join(OUT,src),encoding='utf8').read()
    return re.search(r'<div class="scr" dir="rtl">\n(.*)\n</div>\n</div>\n</div>\n</x-dc>',s,re.S).group(1)
AND=lambda b:'<div class="and">'+b+'<span class="gb"></span></div>'
def android():
    out={}
    out['V11-Android-Frame.dc.html']=('השלמה ז · 11 · אנדרואיד: ההר',AND(inner('S01-Frame.dc.html')))
    out['V12-Android-MountainNow.dc.html']=('השלמה ז · 12 · אנדרואיד: ההר עכשיו',AND(inner('S05-MountainNow.dc.html')))
    out['V13-Android-LiftPanel.dc.html']=('השלמה ז · 13 · אנדרואיד: רכבל אחד',AND(inner('Q03-LiftPanel.dc.html')))
    out['V14-Android-Meetups.dc.html']=('השלמה ז · 14 · אנדרואיד: הקבוצה',AND(inner('R03-GroupMeetups.dc.html')))
    b=inner('R08-SignIn.dc.html');b2=re.sub(r'<button class="gbtn ap">.*?</button>','',b,flags=re.S);assert b!=b2
    out['V15-Android-SignIn.dc.html']=('השלמה ז · 15 · אנדרואיד: כניסה, רק גוגל',AND(b2))
    b=inner('T04-MemberMenu.dc.html');b2=re.sub(r'<div class="menu glass".*?</div></div>','',b,flags=re.S);assert b!=b2
    b2+='<div class="sheet glass" style="gap: 0; padding-top: 10px; background: var(--bg)"><div class="grab"></div><div class="rl" style="min-height: 54px"><span class="c"><span class="h" style="font-weight: 700">איתי</span></span></div><div class="rl" style="min-height: 54px">מינוי למנהל</div><div class="rl" style="min-height: 54px">מילוי בשבילו</div><div class="rl" style="min-height: 54px"><span class="danger">הוצאה מהקבוצה</span></div></div>'
    out['V16-Android-MemberMenu.dc.html']=('השלמה ז · 16 · אנדרואיד: התפריט של חבר',AND(b2))
    return out
def lens():
    """how long the translations run, measured on every app string that has all four languages"""
    tot=dict(he=0,en=0,ru=0,ka=0);n=0
    for k,v in STR.items():
        if not k.startswith('app.') or not isinstance(v,dict):continue
        if not all(isinstance(v.get(l),str) for l in tot):continue
        n+=1
        for l in tot:tot[l]+=len(v[l])
    return n,{l:tot[l]/tot['he'] for l in tot}
def fonts():
    n,rt=lens()
    spec=''
    for l in('he','en','ru','ka'):
        d='rtl' if l=='he' else 'ltr'
        fam=dict(he='Heebo',en='Heebo (הלטינית שלו היא Roboto)',ru='Roboto, לאותיות הקיריליות',ka='Noto Sans Georgian, לאותיות הגאורגיות')[l]
        spec+=('<div class="fc" dir="%s" lang="%s" style="font-family: %s"><span class="ln" dir="rtl">%s</span><span class="fn" dir="rtl" style="font-family: \'Heebo\'">%s</span>'
               '<span class="big">68.3</span><span class="s">%s</span><span class="h">%s</span><span class="b">%s</span><span class="btn">%s</span><span class="s">%s · <bdi>Goodaura</bdi></span></div>')%(d,l,STACK,LN[l],fam,T(l,'app.rec_kmh_max'),T(l,'app.rec_first_title'),T(l,'app.rec_first_text'),T(l,'app.g_new_meetup'),T(l,'app.meet_station_top',alt='2,705'))
    keys=('app.rec_first_title','app.g_remind','app.rec_kmh_max','app.skipass_buy','app.rec_hold','app.g_tab_members','app.skipass_card','app.rec_see_day')
    rows=''
    for k in keys:
        L={l:len(STR[k][l]) for l in('he','en','ru','ka')};mxl=max(L.values())
        rows+='<tr><td>%s</td>%s</tr>'%(STR[k]['he'].replace('{deposit}','5 ₾'),''.join('<td class="n%s">%d</td>'%(' hot' if L[l]==mxl and l!='he' else '',L[l]) for l in('he','en','ru','ka')))
    body=('<div><p class="sec">סבב ההשלמה · חלק 7 · 1</p><h1>גופנים לארבע שפות</h1></div>'
      '<p class="lead" style="max-width: 1500px">השפה החדשה כתובה ב-<bdi>Heebo</bdi>, שיש בו עברית ולטינית בלבד. ההצעה: <b>שרשרת גופנים אחת לכל השפות.</b> <bdi>Heebo</bdi> ראשון, אחריו <bdi>Roboto</bdi> לאותיות הקיריליות, ואחריו <bdi>Noto Sans Georgian</bdi>. כך המספרים, שמות המסלולים והרכבלים נראים זהים בארבע השפות, ורק האותיות של השפה מתחלפות.</p>'
      '<div class="sp4">'+spec+'</div>'
      '<div class="rowx"><div class="card" style="flex: 1"><h2>למה דווקא אלה</h2>'+li(
        '<b><bdi>Roboto</bdi> לרוסית:</b> האותיות הלטיניות של <bdi>Heebo</bdi> הן <bdi>Roboto</bdi>, ולכן הקירילית של <bdi>Roboto</bdi> היא אותה משפחה, באותם משקלים (300, 400, 500, 700). ברוב טלפוני האנדרואיד הוא גופן המערכת.',
        '<b><bdi>Noto Sans Georgian</bdi> לגאורגית:</b> כבר ארוז היום בשתי האפליקציות, באותם משקלים.',
        '<b>אנגלית נשארת ב-<bdi>Heebo</bdi>,</b> כמו בלוחות האנגלית שכבר אושרו.',
        '<b>המספרים הגדולים במשקל 300</b> תמיד מ-<bdi>Heebo</bdi>, בכל שפה.',
        '<b>רישיונות:</b> <bdi>Roboto</bdi> ברישיון <bdi>Apache 2.0</bdi>, <bdi>Noto Sans Georgian</bdi> ברישיון <bdi>OFL 1.1</bdi>. שניהם נכנסים לרשימת הרישיונות.')+'</div>'
      '<div class="card" style="flex: 1"><h2>מה מתהפך משמאל לימין, ומה לא</h2>'+li(
        '<b>מתהפך:</b> סדר השורה (סמל, טקסט, ערך), חץ החזרה, המתג, כפתור ״אני״ והגלולה, עמודת כפתורי המפה, סדר הכפתורים בגיליון, והיישור של הטקסט.',
        '<b>לא מתהפך:</b> ההר והכיוון שלו, ״לרדת בסרט״ (תמיד מלמעלה למטה), ציר הזמן והרכס של היום (תמיד משמאל לימין), מספרים, שעות, ושמות מסלולים ורכבלים.',
        '<b>שם של מקום</b> (<bdi>Goodaura</bdi>, <bdi>Tatra 2</bdi>) נשאר באותיות לטיניות בכל השפות, כמו היום.')+'</div>'
      '<div class="card" style="flex: 1.1"><h2>כמה ארוך יוצא הטקסט</h2><p class="p">נמדד על %d מחרוזות של האפליקציה שיש להן ארבע שפות: מול העברית, האנגלית ארוכה פי %.2f, הרוסית פי %.2f והגאורגית פי %.2f (במספר תווים).</p>'
      '<table class="lt"><tr><th>מחרוזת</th><th>עב</th><th>EN</th><th>RU</th><th>KA</th></tr>%s</table><p class="p" style="font-size: 12.500px; color: #5A6673">באדום: הארוכה מבין השלוש. הלוחות בעמוד הזה בנויים רק מהתרגומים הקיימים, כדי לראות איפה זה נשבר.</p></div></div>'%(n,rt['en'],rt['ru'],rt['ka'],rows))
    wide('V01-Fonts.dc.html','סבב ההשלמה, חלק 7 · 1 · גופנים לארבע שפות',body,1640,1240)
    p=os.path.join(OUT,'V01-Fonts.dc.html');s=open(p,encoding='utf8').read();open(p,'w',encoding='utf8').write(s.replace(MB.FONT,FONT4).replace('</style>\n</helmet>',WC7+'</style>\n</helmet>',1))
def brief(found):
    sc=1160/1180
    ban=('<div class="th" style="width: 1160px; height: 300px; border-radius: 18px"><div style="position: absolute; left: 0; top: -120px; width: 1180px; height: 640px; transform: scale(%g); transform-origin: 0 0"><img alt="" src="%s" style="width: 1180px; height: 640px; max-width: none"></div>'
         '<div style="position: absolute; right: 36px; top: 28px; color: #fff; text-shadow: 0 2px 16px rgba(20,30,80,.7)"><p style="font-size: 14px; font-weight: 700; letter-spacing: .04em">סבב ההשלמה · חלק 7 · 23.11.2026</p><p style="font-size: 46px; font-weight: 900; line-height: 1.1; margin-top: 4px">שפות ופלטפורמות</p><p style="font-size: 19px; font-weight: 500; margin-top: 6px">רוסית, גאורגית ואנגלית בשפה החדשה, ואנדרואיד לחלקים 2 עד 5</p></div></div>')%(sc,U('wide-dawn-cloud'))
    body=(ban+
      '<p class="lead" style="max-width: 1120px">עד עכשיו השפה החדשה צוירה כמעט רק בעברית ובאייפון. כאן שלושה מסכים בכל אחת משלוש השפות האחרות, <b>בנויים רק מהתרגומים שכבר קיימים בקובץ השפות,</b> כדי לראות איך זה באמת יוצא; הצעה לגופנים; ושישה מסכים של אנדרואיד.</p>'
      '<div class="rowx"><div class="card" style="flex: 1"><p class="sec">לוח 1</p><h2>גופנים</h2><p class="p">שרשרת אחת: <bdi>Heebo</bdi>, <bdi>Roboto</bdi> לקירילית, <bdi>Noto Sans Georgian</bdi> לגאורגית. המספרים והשמות זהים בכל השפות.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 2 עד 10</p><h2>רוסית, גאורגית, אנגלית</h2><p class="p">שלושה מסכים בכל שפה: עומדים ביום הגלישה, כרטיס סקי, והמפגשים של הקבוצה. משמאל לימין.</p></div>'
      '<div class="card" style="flex: 1"><p class="sec">לוחות 11 עד 16</p><h2>אנדרואיד</h2><p class="p">ההר, ״ההר עכשיו״, רכבל אחד, הקבוצה, כניסה (רק גוגל), והתפריט של חבר כגיליון תחתון.</p></div></div>'
      '<div class="rowx"><div class="card" style="flex: 1.1"><h2>מה החלטתי, כדי שלא יצטרכו לשאול</h2>'+li(
        '<b>גופן לפי האות, לא לפי השפה.</b> שרשרת אחת בכל האפליקציה. מספר, שעה ושם של מסלול נראים אותו דבר בעברית וברוסית.',
        '<b>טקסט שלא נכנס יורד לשורה שנייה,</b> ולא מתקצר בשלוש נקודות. חוץ מהלשוניות ומהגלולה, ששם שורה אחת היא חובה: שם הטקסט מתקצר.',
        '<b>כפתור לא מקטין את הגופן שלו.</b> כפתור ראשי גדל לגובה (עד שתי שורות). שני כפתורים זה ליד זה שלא נכנסים, יורדים זה מתחת לזה.',
        '<b>ארבע התוויות מתחת למספרים</b> (ירידות, מ׳ למטה...) עוברות לשתי שורות כשצריך, והמספרים נשארים על קו אחד.',
        '<b>כיוון:</b> ברוסית, בגאורגית ובאנגלית כל המסך משמאל לימין. כפתור ״אני״ עובר לימין, חץ החזרה מתהפך. ההר לא מתהפך.',
        '<b>אנדרואיד:</b> כמו שנקבע בחלק 1. הזכוכית הופכת למשטח אטום, גיליון ברוחב מלא עם פינות עליונות 28, פס המחוות למטה, ותפריט של המערכת הוא גיליון תחתון. בכניסה רק גוגל.')+'</div>'
      '<div class="card" style="flex: 1"><h2>מה מצאתי בדרך</h2>'+li(*found)+'<h2 style="margin-top: 14px">ביושר</h2>'+li(
        '<b>התרגומים אמיתיים,</b> מקובץ השפות, כמו שהם. לא תיקנתי ולא תרגמתי בעצמי. רוסית וגאורגית בלי בדיקת דובר (החלטה 39).',
        '<b>רק שלושה מסכים בכל שפה.</b> נבחרו כי כל הטקסט שלהם כבר מתורגם. שאר המסכים לפי אותם כללים.',
        '<b>משך הזמן</b> (״1 ч 12 мин״) ושמות האנשים בדוגמה נכתבו על ידי, לא מהקובץ.',
        '<b>לוחות האנדרואיד</b> נגזרו מלוחות האייפון באותה המרה של חלק 1. הם סכמה, לא צילום.',
        '<b>לא נבדק בטלפון.</b> נבדק בעין ברינדור מקומי, ביום ובלילה.')+'</div></div>')
    wide('V00-Brief.dc.html','סבב ההשלמה, חלק 7: שפות ופלטפורמות',body,1280,1380)
X=[0,470,940,1410,1880,2350]
FOUND=(
 '<b>ברוסית, מילה מתחת למספר צריכה צורות רבים.</b> ״4 дней катания״ שגוי (צריך ״4 дня катания״), וכך גם ״спусков״ מתחת ל-2. שתי המחרוזות האלה כתובות בצורה אחת. לסשן התוכן; המפתחות במסירה.',
 '<b>בגאורגית ארבע הלשוניות של הקבוצה לא נכנסות</b> ברוחב שווה ובגודל 14. לכן: לשונית ברוחב הטקסט שלה, ובגאורגית גודל 13.',
 '<b>״км/ч, максимум״ ו-״კმ/სთ, მაქსიმუმი״</b> יורדות לשתי שורות מתחת למספר. זה בסדר לפי הכלל.',
 '<b>בגלולה שבראש ההר</b> הסיכום ברוסית ובגאורגית נכנס לבדו. יחד עם הטמפרטורה לא בדקתי.',
 '<b>שם הקבוצה, מספר החברים והתאריכים</b> נכנסים בשורה אחת בכל השפות, ליד כפתור ההזמנה.')
def main(found=FOUND):
    out={};k=2
    for lang,lname in LG:
        for key,sname,fn_ in SCR:
            out['V%02d-%s-%s.dc.html'%(k,lang.upper(),key)]=('השלמה ז · %d · %s: %s'%(k,lname,sname),fn_(lang),lang);k+=1
    for fn,(title,body,lang) in out.items():
        phone(fn,title,body,lang='en');fix(fn,lang)
    an=android()
    for fn,(title,body) in an.items():phone(fn,title,body)
    fonts();brief(found)
    H0=1380;p=os.path.join(PROJ,'canvas.json');j=json.load(open(p,encoding='utf8'))
    def put(fn,x,y,w,h,title):
        j['boards'][fn]=dict(x=x,y=y,w=w,h=h,title=title,page='gap7')
        if fn not in j['order']:j['order'].append(fn)
    put('V00-Brief.dc.html',0,0,1280,H0,'חלק 7: שפות ופלטפורמות')
    put('V01-Fonts.dc.html',0,H0+380,1640,1240,'גופנים לארבע שפות')
    order=list(out);y=H0+380+1240+420
    for r in range(3):
        for i,fn in enumerate(order[r*3:r*3+3]):put(fn,X[i],y+r*1510,390,844,out[fn][0].split(' · ',2)[2])
    y2=y+3*1510
    for i,fn in enumerate(an):put(fn,X[i],y2,390,844,an[fn][0].split(' · ',2)[2])
    if not any(pg['id']=='gap7' for pg in j['pages']):j['pages'].insert(2,dict(id='gap7',name='השלמה ז · שפות ופלטפורמות'))
    T_=dict(kind='title1',maxW=1600,page='gap7',x=0)
    j['notes'].update({'tV':dict(T_,w=1100,y=-300,text='סבב ההשלמה · חלק 7 · שפות ופלטפורמות (23.11.2026)'),'tV0':dict(T_,w=620,y=H0+80,text='א · גופנים'),
                       'tV1':dict(T_,w=620,y=y-300,text='ב · רוסית'),'tV2':dict(T_,w=620,y=y+1510-300,text='ג · גאורגית'),'tV3':dict(T_,w=620,y=y+3020-300,text='ד · אנגלית'),'tV4':dict(T_,w=900,y=y2-300,text='ה · אנדרואיד, חלקים 2 עד 5')})
    open(p,'w',encoding='utf8').write(json.dumps(j,ensure_ascii=False,indent=1)+'\n');print('part 7 boards written:',len(out)+len(an)+2)
if __name__=='__main__':main()
