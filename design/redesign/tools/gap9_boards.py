#!/usr/bin/env python3
"""Two day-recording windows that were still in the old look and can open over the mountain:
X01 "the day was cut" (CutDayDialog: opening after a recorded day was cut; continue, or close the day)
and X02 "no permission" (BlockedSheet: no location permission, approximate location, location off;
one button to the settings). Same sheets as part 1 (P01 to P06) and "the mountain now"; existing strings only.
Design only. Usage: python3 design/redesign/tools/gap9_boards.py"""
import os,json
from peak_boards import *
import gap3_boards as G3
import gap4_boards as G4
from peak_b4 import veil
from gap3_boards import ic
PROJ=OUT
P_CUT='<circle cx="12" cy="12" r="8.500"/><path d="M10 9v6M14 9v6"/>'
P_LOCK='<rect x="5" y="10.500" width="14" height="10" rx="2.500"/><path d="M8.500 10.500V8a3.500 3.500 0 0 1 7 0v2.500"/>'
S=json.load(open(os.path.join(P.ROOT,'i18n/strings.json'),encoding='utf8'))['strings']
def t(k):return S[k]['he']
BG=lambda:pic('hero')+both(lambda m,l:P.lifts_svg(VH,m,l,cabins=False,dim=True))+G4.top()+veil(0,844,'rgba(6,11,22,.38)')
def head(path,title,body):
    return ('<span class="ic" style="width: 52px; height: 52px; background: var(--line); margin-top: 4px">%s</span>'
            '<span style="font-size: 21px; font-weight: 700; line-height: 1.2">%s</span><span class="sub" style="max-width: 300px">%s</span>')%(ic(path,26),title,body)
def build():
    out={}
    # X01: not dismissable: no grab handle, no tap outside. two answers, both visible
    o=BG()+'<div class="sheet glass" style="gap: 10px; align-items: center; text-align: center; padding-top: 22px">'
    o+=head(P_CUT,t('app.rec_cut_title'),t('app.rec_cut_text'))
    o+='<button class="pri" style="flex: none; align-self: stretch; margin-top: 6px">%s</button><button class="sec" style="flex: none; align-self: stretch">%s</button></div>'%(t('app.rec_cut_go'),t('app.rec_cut_close'))
    out['X01-CutDay.dc.html']=('יום הגלישה · ״היום נקטע״',o)
    # X02: dismissable (grab, swipe down, tap outside). the line under the title changes with the reason (note under the boards)
    o=BG()+'<div class="sheet glass" style="gap: 10px; align-items: center; text-align: center"><div class="grab"></div>'
    o+=head(P_LOCK,t('app.rec_perm_title'),t('app.rec_err_perm'))
    o+='<button class="pri" style="flex: none; align-self: stretch; margin-top: 6px">%s</button>'%t('app.rec_settings')
    o+='</div>'
    out['X02-Blocked.dc.html']=('יום הגלישה · ״בלי הרשאה״',o)
    return out
def main():
    out=build()
    for fn,(title,body) in out.items():phone(fn,title,body)
    p=os.path.join(PROJ,'canvas.json');j=json.load(open(p,encoding='utf8'))
    for i,fn in enumerate(out):
        j['boards'][fn]=dict(x=[0,470][i],y=0,w=390,h=844,title=out[fn][0].split(' · ',1)[1],page='gap9')
        if fn not in j['order']:j['order'].append(fn)
    if not any(pg['id']=='gap9' for pg in j['pages']):j['pages'].insert(2,dict(id='gap9',name='יום הגלישה · שני חלונות'))
    j['notes']['tX']=dict(kind='title1',maxW=1600,page='gap9',x=0,w=900,y=-200,text='יום הגלישה · ״היום נקטע״ ו״בלי הרשאה״ (24.11.2026)')
    j['notes']['tX2']=dict(kind='title1',maxW=900,page='gap9',x=470,w=420,y=900,text='באותו מקום, לפי הסיבה: מיקום משוער: %s · מיקום כבוי: %s'%(t('app.rec_err_precise'),t('app.rec_err_off')))
    open(p,'w',encoding='utf8').write(json.dumps(j,ensure_ascii=False,indent=1)+'\n');print('boards written:',len(out))
if __name__=='__main__':main()
