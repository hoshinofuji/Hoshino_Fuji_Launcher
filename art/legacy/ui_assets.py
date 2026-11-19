from pathlib import Path
import runpy
import numpy as np, cv2
from PIL import Image, ImageDraw
src=np.array(Image.open(str(Path(__file__).resolve().parents[1] / 'source/design.png')).convert('RGB')).astype(int)
def cutout(box,thr,soft=18,hull=False):
    x0,y0,x1,y1=box; c=src[y0:y1,x0:x1]
    bg=np.median(np.concatenate([c[0],c[-1],c[:,0],c[:,-1]]),axis=0)
    d=np.sqrt(((c-bg)**2).sum(-1))
    a=np.clip((d-thr)/soft,0,1)
    m=(a>0.5).astype(np.uint8)
    n,lab,st,_=cv2.connectedComponentsWithStats(m)
    if n>1:
        k=1+np.argmax(st[1:,cv2.CC_STAT_AREA]); keep=cv2.dilate((lab==k).astype(np.uint8),np.ones((5,5),np.uint8))
        a=a*keep
    if hull:
        cs,_=cv2.findContours((a>0.5).astype(np.uint8),cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        hm=np.zeros(a.shape,np.uint8); cv2.fillPoly(hm,[cv2.convexHull(np.vstack(cs))],1)
        hm=cv2.erode(hm,np.ones((2,2),np.uint8))
        a=np.maximum(a,hm.astype(float)); a=cv2.GaussianBlur(a,(3,3),0.6)
    else:
        mm=cv2.morphologyEx((a>0.4).astype(np.uint8),cv2.MORPH_CLOSE,np.ones((3,3),np.uint8))
        a=np.maximum(a*mm,cv2.GaussianBlur(mm.astype(float),(3,3),0.6)*0.0+a*0)+0
        a=cv2.GaussianBlur(mm.astype(float),(3,3),0.7)
    # un-premultiply-ish: pull colour away from bg on edges
    col=np.where(a[...,None]>0.05,(c-bg*(1-a[...,None]))/np.maximum(a[...,None],0.05),c)
    out=np.dstack([np.clip(col,0,255),a*255]).astype(np.uint8)
    return Image.fromarray(out,'RGBA')
cutout((34,8,92,68),40,hull=True).save('brand-block.png')
cutout((748,272,820,346),60).save('instance-glyph.png')
def deco(w,h,cells,color,name):
    S=2; im=Image.new('RGBA',(w*S,h*S),(0,0,0,0)); d=ImageDraw.Draw(im)
    for (cx,cy,a) in cells:
        d.rectangle((cx*S,cy*S,(cx+19)*S-1,(cy+19)*S-1),fill=color+(a,))
    im.save(name)
# card top-right (anchored right-top): pattern from mockup
deco(100,64,[(24,4,12),(62,4,16),(43,23,14),(81,23,18),(81,4,8)],(200,255,210),'card-tr.png')
deco(84,84,[(4,4,10),(23,23,14),(4,42,12),(42,61,12),(4,61,8)],(200,255,210),'card-bl.png')
deco(64,70,[(23,2,26),(4,21,22),(4,40,30),(23,40,18)],(90,170,20),'btn-tl.png')
deco(90,62,[(4,23,22),(42,4,26),(61,23,20),(23,42,24),(61,42,28)],(90,170,20),'btn-br.png')
