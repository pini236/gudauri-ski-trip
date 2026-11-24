import sys
from PIL import Image
out=sys.argv[1]; specs=sys.argv[2:]
ims=[]
for s in specs:
    f,box=s.split('@'); x0,y0,x1,y1=map(int,box.split(','))
    im=Image.open(f).convert("RGB").crop((x0,y0,x1,y1)); h=900; ims.append(im.resize((int(im.width*h/im.height),h)))
W=sum(i.width for i in ims)+10*(len(ims)-1)
o=Image.new("RGB",(W,900),(40,40,40)); x=0
for i in ims: o.paste(i,(x,0)); x+=i.width+10
o.save(out,quality=88)
