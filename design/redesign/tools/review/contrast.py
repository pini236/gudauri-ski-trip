import sys
from PIL import Image
def lin(c):
    c/=255; return c/12.92 if c<=0.03928 else ((c+0.055)/1.055)**2.4
def L(p): return 0.2126*lin(p[0])+0.7152*lin(p[1])+0.0722*lin(p[2])
for s in sys.argv[1:]:
    f,box=s.split('@'); x0,y0,x1,y1=map(int,box.split(','))
    im=Image.open(f).convert("RGB").crop((x0,y0,x1,y1)); px=list(im.getdata())
    ls=sorted(L(p) for p in px); n=len(ls)
    med=ls[n//2]; p80=ls[int(n*.8)]; p20=ls[int(n*.2)]; mx=ls[-1]
    print(f.split('/')[-1][:28],box,'bg med %.2f p20 %.2f p80 %.2f'%(med,p20,p80),'white vs med %.2f, vs p80 %.2f'%(1.05/(med+.05),1.05/(p80+.05)), 'ink(0B1220) vs med %.2f'%((med+.05)/(0.006+.05)))
