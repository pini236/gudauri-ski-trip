#!/usr/bin/env python3
"""The boards of the page "ההר" in the redesign canvas (design/redesign/canvas/project/H*.dc.html).
Usage: python3 design/redesign/tools/mtn_boards.py     (after mountain.py; the pictures are canvas assets)
The run strip and the corridor mask are read from the data, so the boards never carry a hand-drawn line."""
import os,re,sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import mountain as M
OUT=os.path.join(M.ROOT,'design/redesign/canvas/project')
B={ # asset name -> url in the canvas
 'south-day':'c5ce7fdcdc9e9466709fe0fbc252b1fc','south-dusk':'9c032e3766cfb10c0ee54d61ed3501be','south-night':'fe05008d942625ba0bf0a330225aabd7',
 'south-all-day':'1bbc7959e2c885a618b597a24fe08ae2','south-all-night':'835562a62961859fe86448131c3f45b3',
 'plan-day':'b9aa390a0f258bbb21ac0a6b56f8f973','plan-night':'a1870cca47b8346e99b475507a41a686','plan-all-day':'760409f1d1b30e819999c8f5f273caa2','plan-all-night':'e7477fb42f278e774e7c1d81104ba9bc',
 'kobi-day':'a3d83607191a0bcbd4faa29327f9e466','kobi-all-day':'7a1f6c6696ae06bf8a2a1e41206cb8d5',
 'zoom-day':'21f519457f8b9abc6cfb36f3fdfb6273','zoom-night':'0425601ff37081db6e7a0598b9328b88','zoom-all-day':'87532b3f53fddd2d86e956035bf4b898','zoom-all-night':'c3fe9b2a107f9d67476f905e14a64d73',
 'zoom-run-day':'a0c3c211fddf7458ed98a67aa9a72bf0','zoom-run-night':'c2b5a01e7260a1178083de7e38fa79f4',
 'paper-day':'82987d604dd94e25535aa4eaec7d9349','paper-night':'b1214637550448918f34ecbfd1268da1','paper-all-day':'93d0bb2c06537ab8960235b8088b68b6','paper-all-night':'72c171dd13a7b73d1b48189369c868ec',
}
U=lambda n:'/_blob/'+B[n]
def pic(name,h=844,top=0,ov='all'):
    """picture + its vector layer, day and night"""
    o=''
    for m,c in(('day','d'),('night','n')):
        o+='<img class="pic %s" alt="" src="%s" style="top: %dpx; height: %dpx">'%(c,U('%s-%s'%(name,m)),top,h)
        if ov:o+='<img class="pic %s" alt="" src="%s" style="top: %dpx; height: %dpx">'%(c,U('%s-%s-%s'%(name,ov,m)),top,h)
    return o
CSS="""
body{margin:0}
.H{--bg:#EEF3F8;--glass:rgba(255,255,255,.86);--ink:#0E2233;--ink2:#40556A;--line:rgba(14,34,51,.14);--acc:#6B3FD4;--onacc:#FFFFFF;--halo:rgba(255,255,255,.95);--good:#198549;--warn:#9A5200;--green:#198549;--blue:#1F6FD0;--red:#D1342B;--black:#15181C;--veil:rgba(92,114,148,.44);font-family:'Heebo',system-ui,sans-serif;color:var(--ink);background:var(--bg)}
.H .night{--bg:#0B1220;--glass:rgba(15,24,40,.84);--ink:#EAF1F8;--ink2:#A9B9CC;--line:rgba(234,241,248,.18);--acc:#B79CFF;--onacc:#1A0B3D;--halo:rgba(6,11,22,.95);--good:#46D08A;--warn:#F5B14C;--green:#46D08A;--blue:#62AEFF;--red:#FF7468;--black:#E6ECF2;--veil:rgba(6,11,22,.58)}
.H h1,.H h2,.H h3,.H p{margin:0}
.H .scr{position:absolute;inset:0;background:var(--bg);color:var(--ink);overflow:hidden}
.H .pic{position:absolute;left:0;width:390px;display:block}
.H .n{display:none}.H .night .n{display:block}.H .night .d{display:none}
.H button{font:inherit;color:inherit;border:0;background:none;padding:0;cursor:pointer}
.H .glass{background:var(--glass);-webkit-backdrop-filter:blur(18px) saturate(1.3);backdrop-filter:blur(18px) saturate(1.3);border:1px solid var(--line);box-shadow:0 10px 30px rgba(10,25,45,.18)}
.H .pulse{position:absolute;top:56px;left:14px;right:14px;min-height:48px;border-radius:24px;display:flex;align-items:center;gap:10px;padding:6px 16px;box-sizing:border-box;font-size:15px;font-weight:500;line-height:1.3}
.H .dot{width:9px;height:9px;border-radius:50%;background:var(--good);flex:none}
.H .sheet{position:absolute;left:8px;right:8px;bottom:8px;border-radius:28px;padding:10px 16px 16px;box-sizing:border-box;display:flex;flex-direction:column;gap:12px}
.H .grab{width:38px;height:5px;border-radius:3px;background:var(--line);margin:0 auto}
.H .lbl{position:absolute;transform:translate(-50%,-50%);font-size:11.5px;font-weight:700;white-space:nowrap;line-height:1.25;text-align:center;color:var(--ink);text-shadow:0 0 3px var(--halo),0 0 3px var(--halo),0 0 7px var(--halo);direction:ltr}
.H .lbl small{display:block;font-weight:500;font-size:10.5px;color:var(--ink);direction:rtl;unicode-bidi:isolate}
.H .tag{position:absolute;transform:translate(-50%,-50%);display:inline-flex;align-items:center;gap:5px;height:22px;padding:0 8px 0 6px;border-radius:11px;background:var(--glass);border:1px solid var(--line);font-size:11.5px;font-weight:700;white-space:nowrap;direction:ltr;color:var(--ink)}
.H .tag i{width:8px;height:8px;border-radius:50%;flex:none}
.H .bdg{display:inline-flex;align-items:center;justify-content:center;min-width:28px;height:22px;padding:0 6px;box-sizing:border-box;border-radius:6px;background:#1F6FD0;color:#fff;font-size:12.5px;font-weight:700;direction:ltr}
.H .seg{display:flex;border-radius:24px;padding:3px;gap:2px;background:var(--line)}
.H .seg button{flex:1;height:44px;border-radius:21px;font-size:14px;font-weight:500;color:var(--ink)}
.H .seg .on{background:var(--acc);color:var(--onacc);font-weight:700}
.H .find{height:48px;border-radius:24px;background:var(--line);display:flex;align-items:center;gap:10px;padding:0 16px;font-size:16px;color:var(--ink2)}
.H .chips{display:flex;gap:8px}
.H .chip{flex:1;height:44px;border-radius:22px;border:1px solid var(--line);font-size:13.5px;font-weight:500;display:flex;align-items:center;justify-content:center}
.H .fab{position:absolute;width:56px;height:56px;border-radius:50%;background:var(--acc);display:flex;align-items:center;justify-content:center;box-shadow:0 8px 22px rgba(10,25,45,.3)}
.H .fab span{width:18px;height:18px;border-radius:50%;background:var(--onacc)}
.H .num{font-size:34px;font-weight:300;line-height:1;direction:ltr;display:inline-block}
.H .cap{font-size:12.5px;color:var(--ink2)}
.H .row{display:flex;align-items:center;gap:10px;min-height:52px;border-top:1px solid var(--line)}
.H .pri{height:52px;border-radius:26px;background:var(--acc);color:var(--onacc);font-size:16px;font-weight:700;flex:1}
.H .sec{height:52px;min-width:52px;border-radius:26px;border:1px solid var(--line);font-size:15px;font-weight:500;padding:0 16px}
.H .leg{display:flex;gap:10px;font-size:11.5px;color:var(--ink2);align-items:center}
.H .leg i{display:inline-block;width:14px;height:5px;border-radius:3px;margin-inline-end:4px;vertical-align:middle}
.H .call{position:absolute;font-size:11.5px;font-weight:700;line-height:1.3;color:var(--ink);text-shadow:0 0 3px var(--halo),0 0 3px var(--halo),0 0 7px var(--halo);white-space:nowrap}
.H .call small{display:block;font-weight:500;font-size:10.5px;direction:rtl;unicode-bidi:isolate}
"""
FONT='<link href="https://fonts.googleapis.com/css2?family=Heebo:wght@300;400;500;700;900&display=swap" rel="stylesheet">'
def phone(fn,title,body,night=False,lang='he',w=390,h=844):
    d='ltr' if lang=='en' else 'rtl'
    s='''<!doctype html>
<html lang="%s">
<head>
<meta charset="utf-8">
<title>%s</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
%s
<style>%s</style>
</helmet>
<div class="H" style="width: %dpx; height: %dpx; position: relative; overflow: hidden">
<div class="{{mode}}">
<div class="scr" dir="%s">
%s
</div>
</div>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"night":{"editor":"boolean","default":%s},"$preview":{"width":%d,"height":%d}}'>
class Component extends DCLogic {
renderVals() {
return { mode: (this.props.night ?? %s) ? 'night' : 'day' };
}
}
</script>
</body>
</html>
'''%(lang,title,FONT,CSS,w,h,d,body.strip(),'true' if night else 'false',w,h,'true' if night else 'false')
    open(os.path.join(OUT,fn),'w',encoding='utf8').write(s)
def wide(fn,title,body,w,h):
    s='''<!doctype html>
<html lang="he">
<head>
<meta charset="utf-8">
<title>%s</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
%s
<style>
body{margin:0}
.HW{font-family:'Heebo',system-ui,sans-serif;color:#10161D;background:#F6F7F9}
.HW h1,.HW h2,.HW h3,.HW p,.HW ul{margin:0}
.HW ul{padding-inline-start:18px;display:flex;flex-direction:column;gap:7px}
.HW li{font-size:15px;line-height:1.5}
.HW .k{font-size:13px;font-weight:700;letter-spacing:.04em;color:#56616D}
.HW .card{background:#fff;border:1px solid #D9DEE4;border-radius:14px;padding:22px;display:flex;flex-direction:column;gap:10px}
.HW td,.HW th{padding:10px 12px;border-bottom:1px solid #D9DEE4;text-align:start;vertical-align:top;font-size:14.5px;line-height:1.45}
.HW th{font-size:13px;color:#56616D;font-weight:700}
.HW .q{display:flex;gap:12px;align-items:baseline;font-size:15.5px;line-height:1.5}
.HW .qn{font-weight:900;font-size:20px;min-width:24px}
.HW .th{position:relative;overflow:hidden;border-radius:12px;border:1px solid #D9DEE4;flex:none}
.HW .th img{position:absolute;left:0;top:0;width:100%%;height:100%%;display:block}
.HW .p{font-size:15px;line-height:1.55}
.HW a{color:#3D2A9E}
</style>
</helmet>
<div class="HW" dir="rtl" style="width: %dpx; height: %dpx; box-sizing: border-box; padding: 52px 60px; display: flex; flex-direction: column; gap: 26px">
%s
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":%d,"height":%d}}'>
class Component extends DCLogic {
renderVals() {
return {};
}
}
</script>
</body>
</html>
'''%(title,FONT,w,h,body.strip(),w,h)
    open(os.path.join(OUT,fn),'w',encoding='utf8').write(s)

L=lambda x,y,t,sm='':'<span class="lbl" style="left: %dpx; top: %dpx">%s%s</span>'%(x,y,t,'<small>%s</small>'%sm if sm else '')
T=lambda x,y,c,t:'<span class="tag" style="left: %dpx; top: %dpx"><i style="background: var(--%s)"></i>%s</span>'%(x,y,c,t)
SEARCH='<svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="9" cy="9" r="6"/><path d="M14 14l4 4"/></svg>'
def seg(on,labels=('מהעמק','מלמעלה','Kobi')):
    return '<div class="seg" role="group" aria-label="%s">'%('המבט' if labels[0]=='מהעמק' else 'View')+''.join('<button%s>%s</button>'%(' class="on" aria-pressed="true"' if i==on else '',l) for i,l in enumerate(labels))+'</div>'
SHEET0=lambda on:'''<div class="sheet glass"><div class="grab"></div>%s
<div style="display: flex; gap: 8px"><div class="find" style="flex: 1">%s<span>לאן?</span></div><button class="sec" aria-label="שכבות: רכבלים, מזג אוויר, מפגש" style="width: 48px; min-width: 48px; height: 48px; padding: 0; display: flex; align-items: center; justify-content: center"><svg width="22" height="22" viewBox="0 0 22 22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" aria-hidden="true"><path d="M11 3l8 4-8 4-8-4z"/><path d="M3 11l8 4 8-4"/><path d="M3 15l8 4 8-4"/></svg></button></div></div>'''%(seg(on),SEARCH)
FAB='<button class="fab" aria-label="מקליטים" style="right: 16px; bottom: %dpx"><span></span></button>'

# ------------------------------------------------------------ H01 the mountain
south_lbl=L(272,138,'Sadzele','3,237 מ׳')+L(332,256,'Kudebi','2,985 מ׳')+L(205,326,'Goodaura','2,705 מ׳')+L(96,150,'Bidara','3,174 מ׳')+L(190,585,'Gudauri','2,160 מ׳')+L(50,660,'Pirveli','1,993 מ׳')+L(206,128,'Kobi ↑')
phone('H01-Mountain.dc.html','ההר: כל ההר, מהעמק',pic('south')+south_lbl+'''
<div class="pulse glass"><span class="dot"></span><span>10 מתוך 12 רכבלים פתוחים · <bdi>-9°</bdi> למעלה</span></div>
'''+FAB%182+SHEET0(0))
# ------------------------------------------------------------ H02 dusk
phone('H02-Dusk.dc.html','ההר: אור לפי השעה (שקיעה)','<img class="pic" alt="" src="%s" style="top: 0; height: 844px"><img class="pic" alt="" src="%s" style="top: 0; height: 844px">'%(U('south-dusk'),U('south-all-day'))+south_lbl+'''
<div class="pulse glass"><span class="dot" style="background: var(--warn)"></span><span>16:40 · הרכבלים נסגרים בעוד 20 דק׳</span></div>
<div class="sheet glass"><div class="grab"></div>
<div class="seg" role="group" aria-label="האור"><button>בוקר</button><button>יום</button><button class="on" aria-pressed="true">שקיעה</button><button>לילה</button></div>
<p class="cap">האור לפי השעה בגודאורי. ארבע תמונות מוכנות מראש, מתחלפות בדעיכה.</p></div>''')
# ------------------------------------------------------------ H03 nearer: names appear
Z=44
phone('H03-Near.dc.html','ההר: מתקרבים, השמות מופיעים',pic('zoom',560,Z)+
 T(86,285+Z,'blue','Tatra 2 · 7A')+T(150,400+Z,'blue','Soliko 1 · 2A')+T(232,352+Z,'blue','Sportuli 1 · 2C')+T(290,424+Z,'blue','Goodaura 1')+T(46,196+Z,'blue','Tatra 1')+T(196,300+Z,'blue','Soliko 2 · 2B')+
 L(318,80+Z,'Goodaura · Shino','2,705 מ׳')+L(60,508+Z,'Shino','2,172 מ׳')+'''
<div class="pulse glass"><span class="dot"></span><span>הצד המערבי של גודאורי</span></div>
<div class="sheet glass" style="gap: 0"><div class="grab" style="margin-bottom: 10px"></div>
<div class="row" style="border-top: 0"><span class="bdg">7A</span><b style="flex: 1" dir="ltr">Tatra 2</b><span class="cap">2.7 ק״מ · 536 מ׳</span></div>
<div class="row"><span class="bdg">2A</span><b style="flex: 1" dir="ltr">Soliko 1</b><span class="cap">2.8 ק״מ · 541 מ׳</span></div>
<div class="row"><span class="bdg">2C</span><b style="flex: 1" dir="ltr">Sportuli 1</b><span class="cap">1.9 ק״מ · 418 מ׳</span></div>
<div class="row"><span class="bdg" style="background: none; border: 1.5px solid #1F6FD0"></span><b style="flex: 1" dir="ltr">Goodaura 1</b><span class="cap">2.7 ק״מ · 547 מ׳</span></div>
</div>''')
# ------------------------------------------------------------ H04 a chosen run: the corridor is lit
cor=open(os.path.join(M.OUT,'mtn-zoom-corridor.svg'),encoding='utf8').read()
cor_paths=''.join(re.findall(r'<path [^>]+/>',cor));blur=re.search(r'stdDeviation="([\d.]+)"',cor).group(1)
def lit(uid):
    o=''
    for m,c in(('day','d'),('night','n')):
        o+='''<img class="pic %s" alt="" src="%s" style="top: %dpx; height: 560px">
<div class="%s" style="position: absolute; left: 0; top: %dpx; width: 390px; height: 560px; background: var(--veil)"></div>
<svg class="pic %s" viewBox="0 0 390 560" style="top: %dpx; height: 560px" aria-hidden="true"><defs><filter id="%s-f-%s" x="-20%%" y="-20%%" width="140%%" height="140%%"><feGaussianBlur stdDeviation="%s"/></filter><mask id="%s-m-%s" maskUnits="userSpaceOnUse" x="0" y="0" width="390" height="560"><g filter="url(#%s-f-%s)" fill="none" stroke-linecap="round" stroke-linejoin="round">%s</g></mask></defs><image href="%s" width="390" height="560" mask="url(#%s-m-%s)"/></svg>
<img class="pic %s" alt="" src="%s" style="top: %dpx; height: 560px">'''%(c,U('zoom-'+m),Z,c,Z,c,Z,uid,m,blur,uid,m,uid,m,cor_paths,U('zoom-'+m),uid,m,c,U('zoom-run-'+m),Z)
    return o
LEG='<div class="leg"><span><i style="background: #3FA85F"></i>%s 15°</span><span><i style="background: #F2C13D"></i>15°–25°</span><span><i style="background: #F08A3C"></i>25°–30°</span><span><i style="background: #DC3B33"></i>30°+</span></div>'
def run_board(fn,title,en,night):
    t=(lambda he,e:e if en else he)
    body=lit('h7' if en else 'h4')+'''
<span class="call" style="left: 150px; top: %dpx; direction: ltr">Goodaura · Shino<small>2,707 %s</small></span>
<span class="call" style="left: 118px; top: %dpx">%s</span>
<span class="call" style="left: 96px; top: %dpx; direction: ltr">Shino<small>2,172 %s</small></span>
<div class="pulse glass"><button aria-label="%s" style="width: 44px; height: 44px; margin-inline-start: -10px; display: flex; align-items: center; justify-content: center"><svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="%s"/></svg></button><span>%s</span></div>
<div class="sheet glass"><div class="grab"></div>
<div style="display: flex; align-items: center; gap: 10px"><span class="bdg">7A</span><h2 style="font-size: 26px; font-weight: 700; flex: 1" dir="ltr">Tatra 2</h2><span class="cap">%s</span></div>
<div style="display: flex; justify-content: space-between"><div><span class="num">2.7</span><p class="cap">%s</p></div><div><span class="num">536</span><p class="cap">%s</p></div><div><span class="num">28°</span><p class="cap">%s</p></div></div>
%s
<div style="display: flex; gap: 8px"><button class="pri">%s</button><button class="sec">%s</button></div></div>'''%(
 80+Z,t('מ׳','m'),236+Z,t('הקטע התלול','Steepest part'),486+Z,t('מ׳','m'),t('חזרה לכל ההר','Back to the whole mountain'),
 'M12 4l-6 6 6 6' if en else 'M8 4l6 6-6 6',t('כל ההר','The whole mountain'),
 t('כחול','Blue'),t('ק״מ','km'),t('מ׳ ירידה','m drop'),t('הקטע התלול','steepest'),
 LEG%t('עד','to'),t('סרט הירידה','The run strip'),t('שיתוף','Share'))
    phone(fn,title,body,night=night,lang='en' if en else 'he')
run_board('H04-Run.dc.html','ההר: מסלול נבחר, המסדרון מואר',False,False)
run_board('H07-RunEn.dc.html','The mountain: a chosen run (EN, night)',True,True)
# ------------------------------------------------------------ H05 the run strip
R=M.strip()
SX,SY=214,150                      # where the 150 x 560 strip sits on the screen
rib=''.join('<path d="%s" stroke="var(--halo)" stroke-width="17"/>'%d for c,d in R['segs'])+''.join('<path d="%s" stroke="%s" stroke-width="11"/>'%(d,c) for c,d in R['segs'])
ticks=''.join('<path d="M%.0f %.0f H131" stroke="var(--ink2)" stroke-width="1" stroke-dasharray="1 4"/><text x="162" y="%.0f" font-size="10.5" fill="var(--ink2)" text-anchor="end" font-family="Heebo">%s</text>'%(x+11,y,y+3.5,'{:,}'.format(e)) for e,x,y in R['ticks'])
def mom(y,h,sm='',acc=False):
    return '<div style="position: absolute; right: 184px; left: 16px; top: %dpx; transform: translateY(-50%%); text-align: start"><p style="font-size: 14.5px; font-weight: 700; line-height: 1.3%s">%s</p>%s</div>'%(SY+y,'; color: var(--acc)' if acc else '',h,'<p class="cap" style="line-height: 1.35">%s</p>'%sm if sm else '')
phone('H05-Strip.dc.html','ההר: סרט הירידה','''
<div style="position: absolute; top: 56px; left: 14px; right: 14px; height: 48px; display: flex; align-items: center; gap: 10px"><button aria-label="חזרה" style="width: 44px; height: 44px; display: flex; align-items: center; justify-content: center"><svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8 4l6 6-6 6"/></svg></button><span class="bdg">7A</span><h2 style="font-size: 22px; font-weight: 700; flex: 1" dir="ltr">Tatra 2</h2><span class="cap">2.7 ק״מ · 536 מ׳</span></div>
<p class="cap" style="position: absolute; top: 110px; left: 16px; right: 16px">הקו האמיתי, מסובב כך שההתחלה למעלה. כמו שרואים אותו מהעמק.</p>
<svg viewBox="0 0 150 560" width="150" height="560" style="position: absolute; left: %dpx; top: %dpx; overflow: visible; direction: ltr" fill="none" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
%s
<path d="M39 402 l26 6" stroke="var(--blue)" stroke-width="4" stroke-dasharray="1 7"/>
<path d="M120 500 l-24 -8" stroke="var(--blue)" stroke-width="5"/>
%s
<rect x="114" y="13" width="14" height="14" rx="3.5" fill="var(--halo)" stroke="var(--ink)" stroke-width="2"/>
<rect x="114" y="533" width="14" height="14" rx="3.5" fill="var(--halo)" stroke="var(--ink)" stroke-width="2"/>
<path d="M-196 226 H18" stroke="var(--acc)" stroke-width="1.5" stroke-dasharray="2 5"/>
<circle cx="31" cy="226" r="10" fill="var(--acc)" stroke="var(--halo)" stroke-width="3"/>
</svg>
%s%s%s%s%s%s
<div class="sheet glass" style="flex-direction: row; align-items: center; gap: 8px; padding: 10px"><div style="flex: 1; padding-inline-start: 8px"><p style="font-size: 14.5px; font-weight: 700">1,090 מ׳ מההתחלה</p><p class="cap">גובה 2,538 מ׳ · 20°</p></div><button class="sec">על ההר</button><button class="pri" style="flex: none; padding: 0 18px">מודדים ירידה</button></div>
'''%(SX,SY,rib,ticks,
 mom(20,'<bdi>Goodaura</bdi>, למעלה','2,707 מ׳'),mom(118,'960 מ׳ מתונים','עד 15°'),mom(226,'הקטע התלול','360 מ׳ · 15° עד 25°',True),
 mom(402,'<bdi>Shino</bdi> יוצא שמאלה','דרך מקשרת'),mom(492,'<bdi>Tatra 1</bdi> מצטרף מימין'),mom(540,'<bdi>Shino</bdi>, למטה','2,172 מ׳ · 7.8 דק׳ בחזרה למעלה')))
# ------------------------------------------------------------ H06 from above, where am I
phone('H06-Above.dc.html','ההר: מלמעלה, איפה אני',pic('plan')+L(270,140,'Sadzele','3,237 מ׳')+L(350,134,'▲ 3,307')+L(130,92,'Bidara','3,174 מ׳')+L(214,336,'Goodaura','2,705 מ׳')+L(196,612,'Gudauri','2,160 מ׳')+'''
<span style="position: absolute; left: 98px; top: 449px; width: 64px; height: 64px; margin: -32px; border-radius: 50%; background: var(--acc); opacity: .2"></span>
<span style="position: absolute; left: 98px; top: 449px; width: 18px; height: 18px; margin: -9px; box-sizing: border-box; border-radius: 50%; background: var(--acc); border: 3px solid var(--halo)"></span>
<div class="pulse glass"><span class="dot" style="background: var(--acc)"></span><span>אתה ב-<bdi>Tatra 2</bdi>, כנראה · 2,422 מ׳</span></div>
'''+FAB%182+SHEET0(1))
# ------------------------------------------------------------ H08 the other material
phone('H08-Model.dc.html','ההר: מראה חלופי, דגם שכבות',pic('paper')+L(272,138,'Sadzele','3,237 מ׳')+L(332,256,'Kudebi','2,985 מ׳')+L(205,326,'Goodaura','2,705 מ׳')+L(190,585,'Gudauri','2,160 מ׳')+'''
<div class="pulse glass"><span class="dot"></span><span>דגם שכבות · כל מדרגה 50 מ׳</span></div>
<div class="sheet glass"><div class="grab"></div>
<div class="seg" role="group" aria-label="המראה"><button>מצויר</button><button class="on" aria-pressed="true">דגם שכבות</button></div>
<p class="cap">אותו הר, אותו מבט, אותם קווים. הגובה נקרא במדרגות, בלי אור וצל.</p></div>''')

# ------------------------------------------------------------ wide boards
def th(w,h,*names):
    return '<div class="th" style="width: %dpx; height: %dpx">%s</div>'%(w,h,''.join('<img alt="" src="%s">'%U(n) for n in names))
wide('H00-Brief.dc.html','ההר: מה רואים, ואיך','''
<div style="display: flex; flex-direction: column; gap: 12px">
<p class="k">GUDI · עיצוב מחדש · סבב "ההר" · 19.11.2026</p>
<h1 style="font-size: 52px; font-weight: 900; line-height: 1.1">ההר מצויר מראש. המידע חי מעליו.</h1>
<p style="font-size: 19px; line-height: 1.55; max-width: 1000px; color: #2B343E">הבקשה: איך ההר נראה ואיך רואים עליו מסלול, מאפס, ובלי לבנות עכשיו מודל תלת-ממד מלא. ההצעה: להפסיק לחשב את ההר בטלפון 60 פעם בשנייה, ולצייר אותו פעם אחת, לאט וביופי, מאותם נתוני גובה. כל התמונות בעמוד הזה נוצרו כך, מהקבצים שבריפו. שום קוד באפליקציות לא שונה.</p>
</div>
<div style="display: flex; gap: 20px">
%s
<div style="display: flex; flex-direction: column; gap: 20px; flex: 1">
<div class="card"><p class="k">מה לא עובד היום</p><ul>
<li>מודל הגובה גס (תא של כ-40 מ׳). תלת-ממד חי מציג אותו כמו שהוא, ולכן ההר רך וחלק. טלפון חזק יותר לא משפר את זה.</li>
<li>מצלמה חופשית מעבירה את העבודה למשתמש: צריך לסובב ולהטות עד שרואים את המסלול.</li>
<li>מסלול הוא קו דק על משטח. לא רואים מדרון, ולא רואים איפה הוא ביחס להר.</li>
<li>מבט על ותלת-ממד הם שני מסכים שנראים ומתנהגים אחרת.</li></ul></div>
<div class="card"><p class="k">מה למדתי מהמחקר</p><ul>
<li><b>מפות הסקי המצוירות</b> (James Niehues): מבט אחד שמראה את כל ההר. הכוח באור ובקומפוזיציה; המחיר הוא עיוות ידני של ההר.</li>
<li><b>הסגנון השוויצרי</b> (Eduard Imhof): אור חם, צל כחול-סגול, ואובך שמרחיק את הרקע.</li>
<li><b>Plan oblique</b> (Patterson ו-Jenny, 2007): הר עם גוף אבל בלי נקודת מגוז, ולכן הצורות והמרחקים נשמרים.</li>
<li><b>תבליט מחושב מראש</b> (Blender, לפי Daniel Huffman): איכות של איור ממודל גובה רגיל, כי מחשבים פעם אחת.</li>
<li><b>אפליקציות הסקי</b> (Slopes, Slopie, Ski Resort 3D ואחרות): לפי התיאור שלהן בחנות, תלת-ממד כללי לאלפי אתרים. לא התקנתי ולא בדקתי אותן.</li></ul>
<p style="font-size: 12.5px; color: #56616D">המקורות עם קישורים: research/mountain-rendering.md בריפו.</p></div>
<div class="card" style="border: 2px solid #10161D"><p class="k">מה לא משתנה</p><p class="p">בלי הגזמת גובה. בלי קו שאינו בנתונים. האור מעוצב, הקרקע לא: ההטלה והמדידות מהמודל כמו שהוא. צבעי המסלולים וספי השיפוע (15°, 25°, 30°) כמו היום. מה שמוסתר מאחורי רכס לא מצויר, ורואים אותו במבט אחר.</p></div>
</div>
</div>
<div style="display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px">
<div class="card"><p class="k">1 · תמונה, לא מודל</p><p class="p">ההר מצויר מראש ממודל הגובה האמיתי: אור נמוך מהצד, צל כחול, סלע במקום שתלול מדי לשלג, קווי גובה ואובך במרחק. ארבע שעות אור: בוקר, יום, שקיעה, לילה.</p></div>
<div class="card"><p class="k">2 · מבט מקביל, בלי פרספקטיבה</p><p class="p">כל פיקסל בתמונה הוא מקום ידוע. נוסחה אחת מציבה עליה מסלול, רכבל, אותך וחברים, ויודעת מה מוסתר מאחורי רכס. לכן הקווים חיים: מצב רכבלים, מסלול נבחר, המיקום שלך.</p></div>
<div class="card"><p class="k">3 · שלושה מבטים, לא מצלמה חופשית</p><p class="p">מהעמק (כל צד גודאורי), מלמעלה (למיקום מדויק), ומצד Kobi. מתקרבים בצביטה, והשמות מופיעים לפי המרחק. אין זווית גרועה, כי כל מבט נבחר מראש.</p></div>
</div>
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px">
<div class="card"><p class="k">הלוחות בעמוד</p><ul>
<li>כל ההר מהעמק, ביום ובלילה · אור של שקיעה</li>
<li>מתקרבים: שמות המסלולים והמספרים מופיעים</li>
<li>מסלול נבחר: ההר נשאר, המסדרון מואר, והקו צבוע לפי השיפוע</li>
<li>"סרט הירידה": המסלול כסרט אחד מלמעלה למטה</li>
<li>מלמעלה, עם "איפה אני" · מראה חלופי: דגם שכבות</li>
<li>שלושת המבטים וכללי התוויות · איך בונים, ומה זה עולה</li></ul></div>
<div class="card"><p class="k">שאלות לפיני</p>
<div class="q"><span class="qn">1</span><span>הכיוון: הר מצויר מראש עם שלושה מבטים, במקום תלת-ממד חי עם מצלמה חופשית? (ההמלצה: כן)</span></div>
<div class="q"><span class="qn">2</span><span>המראה: "מצויר" (אור וצל) או "דגם שכבות"? (מצויר; הדגם אפשרי כמצב נוסף)</span></div>
<div class="q"><span class="qn">3</span><span>"סרט הירידה" נפתח מפאנל המסלול, ומחליף שם את פרופיל הגובה? (כן)</span></div>
<div class="q"><span class="qn">4</span><span>הטיסה במורד המסלול הופכת לנקודה שנעה על התמונה ועל הסרט, בלי מצלמה שטסה? (כן)</span></div>
<div class="q"><span class="qn">5</span><span>האור לפי השעה בגודאורי, בארבע תמונות? (כן)</span></div>
</div>
</div>
<p style="font-size: 13.5px; line-height: 1.6; color: #56616D">במסכים: ההר, הקווים, השמות, הגבהים, האורכים והמספרים הרשמיים מהקבצים. מצב הרכבלים, הטמפרטורה והמיקום הם דוגמה. "28°" הוא המספר שהאתר מציג היום; צבעי השיפוע כאן מדגימה של 40 מ׳, ובמימוש יהיה חישוב אחד לשניהם. הצד שממנו מסלול מצטרף ב"סרט הירידה" מחושב מהנתונים, וייבדק מול השטח.</p>
'''%th(390,844,'south-day','south-all-day'),1280,1800)

wide('H09-Views.dc.html','ההר: שלושה מבטים, שלוש רמות קירוב','''
<div style="display: flex; flex-direction: column; gap: 8px"><p class="k">המבטים והקירוב</p><h1 style="font-size: 40px; font-weight: 900; line-height: 1.15">שלושה מבטים קבועים, שלוש רמות קירוב</h1></div>
<div style="display: flex; gap: 22px; align-items: flex-start">
<div style="display: flex; flex-direction: column; gap: 10px; width: 234px">%s<h3 style="font-size: 19px">מהעמק</h3><p class="p">ברירת המחדל. מסתכלים צפונה, 33° מעל האופק. כל צד גודאורי, מ-Pirveli ועד Sadzele, במסך אחד לאורך.</p></div>
<div style="display: flex; flex-direction: column; gap: 10px; width: 234px">%s<h3 style="font-size: 19px">מלמעלה</h3><p class="p">צפון למעלה, קנה מידה אחיד. למיקום מדויק, ל"איפה אני" ולמדידת מרחק. אותו אור, אותם צבעים.</p></div>
<div style="display: flex; flex-direction: column; gap: 10px; width: 234px">%s<h3 style="font-size: 19px">מצד Kobi</h3><p class="p">מהצפון. Kobi, Truso והמעבר. מה שמוסתר במבט מהעמק נמצא כאן.</p></div>
<div style="display: flex; flex-direction: column; gap: 14px; flex: 1">
<div class="card"><p class="k">הקירוב קובע מה כתוב</p><table style="border-collapse: collapse; width: 100%%">
<tr><th style="width: 86px">רמה</th><th>מה רואים</th></tr>
<tr><td><b>כל ההר</b></td><td>מסלולים כחוטים צבעוניים. רק תחנות, פסגות וגבהים. בלי שמות מסלולים.</td></tr>
<tr><td><b>אזור</b></td><td>שמות המסלולים והמספרים הרשמיים כתגיות. קווי גובה כל 20 מ׳. רשימת המסלולים שבמסך.</td></tr>
<tr><td><b>מסלול</b></td><td>שאר ההר מתעמעם והמסדרון מואר. הקו צבוע לפי השיפוע, עם חיצים במורד. התחלה, הקטע התלול, סוף.</td></tr></table></div>
<div class="card"><p class="k">האור: ארבע תמונות לכל מבט</p><div style="display: flex; gap: 10px">%s%s%s</div><p class="p">יום, שקיעה, לילה (ובוקר, באותה דרך). בלילה המסלול המואר זוהר: היום רק Pirveli מסומן בנתונים כמואר.</p></div>
<div class="card"><p class="k">תנועה</p><p class="p">בין מבטים: דעיכה של 300 אלפיות שנייה, והקווים עוברים למקומם החדש. בחירת מסלול: ההר מתעמעם והמסדרון נדלק מלמעלה למטה. בהפחתת תנועה: החלפה מיידית.</p></div>
</div></div>
'''%(th(234,506,'south-day','south-all-day'),th(234,506,'plan-day','plan-all-day'),th(234,360,'kobi-day','kobi-all-day'),th(116,251,'south-day'),th(116,251,'south-dusk'),th(116,251,'south-night')),1280,1030)

wide('H10-Build.dc.html','ההר: איך בונים, ומה זה עולה','''
<div style="display: flex; flex-direction: column; gap: 8px"><p class="k">היתכנות</p><h1 style="font-size: 40px; font-weight: 900; line-height: 1.15">איך בונים את זה, בלי מודל תלת-ממד</h1></div>
<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px">
<div class="card"><p class="k">1 · פעם אחת, אצלנו</p><p class="p">כלי קטן (כבר כתוב: tools/mountain.py) מצייר את ההר ממודל הגובה שבריפו. שניות ספורות לתמונה, בלי ספרייה חיצונית.</p></div>
<div class="card"><p class="k">2 · נכס באפליקציה</p><p class="p">תמונה של 780 על 1,688, כ-130KB. שלושה מבטים בארבע שעות אור: בערך 1.5MB, פחות ממנוע התלת-ממד של האתר.</p></div>
<div class="card"><p class="k">3 · בטלפון: ציור וקטורי</p><p class="p">הקווים מצוירים מעל התמונה בנוסחה אחת, עם מודל הגובה שכבר נשלח. בלי GPU, בלי מנוע, אותו דבר באתר, באנדרואיד ובאייפון.</p></div>
<div class="card"><p class="k">4 · אתר סקי חדש</p><p class="p">מריצים את הכלי על המודל שלו. אין עבודת ציור ידנית לכל הר.</p></div>
</div>
<table style="border-collapse: collapse; width: 100%%; background: #fff; border: 1px solid #D9DEE4; border-radius: 14px">
<tr><th style="width: 190px"></th><th>היום: תלת-ממד חי</th><th>ההצעה: הר מצויר מראש</th></tr>
<tr><td><b>איך ההר נראה</b></td><td>מה שהטלפון מספיק לחשב בפריים</td><td>מה שרוצים: אור, צל, אובך, בלי מגבלת זמן חישוב</td></tr>
<tr><td><b>ביצועים וסוללה</b></td><td>תלוי בטלפון. עוד לא נמדד בטלפון בינוני</td><td>תמונה וקווים. קל לכל טלפון</td></tr>
<tr><td><b>שלוש פלטפורמות</b></td><td>שלושה מנועים (three.js, OpenGL, Metal) שצריך ליישר</td><td>אותן תמונות ואותה נוסחה, עם ערכי בדיקה משותפים</td></tr>
<tr><td><b>תנועה חופשית</b></td><td>סיבוב והטיה לכל זווית</td><td>שלושה מבטים וקירוב. אין סיבוב חופשי</td></tr>
<tr><td><b>טיסה במורד המסלול</b></td><td>מצלמה שטסה לאורך הקו</td><td>נקודה שנעה על התמונה ועל סרט הירידה</td></tr>
<tr><td><b>בלי קליטה</b></td><td>עובד</td><td>עובד: הכל ארוז</td></tr>
</table>
<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px">
<div class="card"><p class="k">מה צריך לדעת (ביושר)</p><ul>
<li>מקרוב ההר רך. מודל של 40 מ׳ לא מכיל פרטים קטנים, והכלי לא ממציא אותם. קווי גובה של 20 מ׳ ותגיות נותנים את החדות.</li>
<li>מדרון שפונה הרחק מהמבט ותלול מ-33° מוסתר. בצד הגלישה של גודאורי כמעט אין כאלה; צד Kobi מקבל מבט משלו.</li>
<li>רוחב אמיתי של מסלול ידוע רק למעטים בנתונים. לכן קו ומסדרון רך, ולא רצועה ברוחב מומצא.</li>
<li>התמונות כאן הן טיוטה ראשונה של הכלי. יש עוד לאן לשפר: מרקם שלג, סלע, כפר ואורות.</li></ul></div>
<div class="card"><p class="k">אם מאשרים: הסדר</p><ul>
<li>סבב שני בקנבס: ליטוש המראה, בוקר, הכפר, מצב רכבלים ויום הגלישה על ההר המצויר.</li>
<li>סקירת ארכיטקט: נכס חדש, נוסחת ההטלה כחוזה משותף, וערכי בדיקה לשלוש הפלטפורמות.</li>
<li>ניסוי באתר מאחורי דגל, ואחריו אנדרואיד ואייפון.</li>
<li>במקביל: לבדוק אם יש מודל גובה פתוח ומדויק יותר לגודאורי. אותו כלי, תמונה חדה יותר.</li></ul></div>
</div>
''',1280,1000)
print('boards written')
