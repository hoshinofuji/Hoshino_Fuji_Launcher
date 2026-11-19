from pathlib import Path
import runpy
import numpy as np, cv2, json
from PIL import Image, ImageDraw, ImageFilter
base=np.array(Image.open('poster-base-v2.png').convert('RGB'))
H,W=base.shape[:2]
meta={}
# ---------------- 1. remove signature watermark (only its light line pixels)
x0,y0,x1,y1=18,700,305,895
roi=base[y0:y1,x0:x1].astype(np.int32)
gray=roi.mean(-1)
bg=cv2.medianBlur(roi.astype(np.uint8),21).mean(-1)
lines=((gray-bg)>22).astype(np.uint8)*255
lines=cv2.dilate(lines,np.ones((5,5),np.uint8))
m=np.zeros((H,W),np.uint8); m[y0:y1,x0:x1]=lines
base=cv2.inpaint(base[...,::-1].copy(),m,6,cv2.INPAINT_TELEA)[...,::-1]
# gentle blur of the repaired area so no faint traces remain
blur=cv2.GaussianBlur(base,(0,0),2.2)
mm=cv2.GaussianBlur(cv2.dilate(m,np.ones((7,7),np.uint8)).astype(np.float32)/255,(0,0),3)[...,None]
base=(base*(1-mm)+blur*mm).astype(np.uint8)
# ---------------- 2. cat cut-out
gc=np.full((H,W),cv2.GC_BGD,np.uint8)
gc[512:675,482:645]=cv2.GC_PR_BGD
cv2.rectangle(gc,(505,540),(585,660),cv2.GC_PR_FGD,-1)
cv2.rectangle(gc,(520,600),(570,655),cv2.GC_FGD,-1)
cv2.rectangle(gc,(510,545),(580,585),cv2.GC_FGD,-1)
cv2.rectangle(gc,(603,580),(620,625),cv2.GC_PR_FGD,-1)
bgd=np.zeros((1,65)); fgd=np.zeros((1,65))
cv2.grabCut(base[...,::-1].copy(),gc,None,bgd,fgd,8,cv2.GC_INIT_WITH_MASK)
cat=np.where((gc==1)|(gc==3),255,0).astype(np.uint8)
n,lab,st,_=cv2.connectedComponentsWithStats(cat); k=1+np.argmax(st[1:,4]); cat=np.where(lab==k,255,0).astype(np.uint8)
cat=cv2.morphologyEx(cat,cv2.MORPH_CLOSE,np.ones((5,5),np.uint8))
# fill holes
ff=cat.copy(); h_,w_=ff.shape; mask=np.zeros((h_+2,w_+2),np.uint8); cv2.floodFill(ff,mask,(0,0),255); cat=cat|(255-ff)
cv2.imwrite('catmask.png',cat)
alpha=cv2.GaussianBlur(cat.astype(np.float32)/255,(0,0),0.7)
tail=np.zeros((H,W),np.float32); tail[:, 593:]=1; tail[640:,:]=0
tail=cv2.GaussianBlur(tail,(0,0),1.0)
inner=cv2.erode(cat,np.ones((5,5),np.uint8))
col=cv2.inpaint(base[...,::-1].copy(),255-inner,4,cv2.INPAINT_TELEA)[...,::-1].astype(np.float32)
ew=np.clip(1-cv2.GaussianBlur((inner>0).astype(np.float32),(0,0),1.0),0,1)[...,None]
rgb=base*(1-ew)+col*ew
def sprite(a,name,pad=4):
    ys,xs=np.nonzero(a>0.01); bx0,by0,bx1,by1=xs.min()-pad,ys.min()-pad,xs.max()+pad+1,ys.max()+pad+1
    out=np.dstack([rgb[by0:by1,bx0:bx1],a[by0:by1,bx0:bx1]*255]).clip(0,255).astype(np.uint8)
    Image.fromarray(out,'RGBA').save(name); return [int(bx0),int(by0)]
meta['catBody']=sprite(alpha*(1-tail),'cat-body.png')
meta['catTail']=sprite(alpha*tail,'cat-tail.png')
# cat closed eyes, in body-sprite coordinates
bx,by=meta['catBody']; bw,bh=Image.open('cat-body.png').size
S=4; ov=Image.new('RGBA',(bw*S,bh*S),(0,0,0,0)); d=ImageDraw.Draw(ov)
fur=tuple(int(v) for v in base[570,530])+(255,)
for ex0,ey0,ex1,ey1 in [(513,547,528,563),(535,550,552,565)]:
    d.rectangle(((ex0-bx)*S,(ey0-by)*S,(ex1-bx)*S,(ey1-by)*S),fill=fur)
    cx=(ex0+ex1)/2; cy=(ey0+ey1)/2+1
    d.arc(((cx-6-bx)*S,(cy-6-by)*S,(cx+6-bx)*S,(cy+4-by)*S),200,340,fill=(70,52,60,255),width=int(1.8*S))
ov=ov.filter(ImageFilter.GaussianBlur(S*0.4)).resize((bw,bh),Image.LANCZOS)
ov.save('cat-blink.png')
# plate: remove the cat
hole=cv2.dilate(cat,np.ones((15,15),np.uint8))
plate=base.copy()
sx0,sx1=459,474                      # strip of bushes/grass between her leg and the cat
L=sx1-sx0
ys,xs=np.nonzero(hole)
for y,x in zip(ys,xs):
    k=(x-sx0)%(2*L); k=k if k<L else 2*L-1-k
    plate[y,x]=base[y,sx0+k]
plate=cv2.GaussianBlur(cv2.medianBlur(plate,5),(0,0),1.6)
hm=cv2.GaussianBlur(hole.astype(np.float32)/255,(0,0),2.5)[...,None]
plate=(base*(1-hm)+plate*hm).astype(np.uint8)
base=plate
Image.fromarray(base).save('poster-base.png')
# ---------------- 3. water mask
img=base.astype(int); r,g,b=img[...,0],img[...,1],img[...,2]
wm=((b>g+4)|((r>g+8)&(b>g-6)&(r>90)))
wm[:680]=False; wm[:, :280]=False; wm[860:]=False
wm=cv2.morphologyEx(wm.astype(np.uint8)*255,cv2.MORPH_OPEN,np.ones((3,3),np.uint8))
wm=cv2.morphologyEx(wm,cv2.MORPH_CLOSE,np.ones((15,15),np.uint8))
cv2.imwrite('watermask.png',wm)
json.dump(meta,open('cat-meta.json','w')); print(meta)
