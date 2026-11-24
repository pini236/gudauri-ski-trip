import sys
from PIL import Image
d=sys.argv[1]; out=sys.argv[2]; names=sys.argv[3:]
ims=[]
for n in names:
    im=Image.open(f"{d}/{n}").convert("RGB"); h=1000; w=int(im.width*h/im.height); ims.append(im.resize((w,h)))
W=sum(i.width for i in ims)+10*(len(ims)-1)
o=Image.new("RGB",(W,1000),(40,40,40)); x=0
for i in ims: o.paste(i,(x,0)); x+=i.width+10
o.save(out,quality=85)
