from pathlib import Path
import runpy
import cv2, numpy as np, gzip, struct
from PIL import Image
img=np.array(Image.open('poster-base.png').convert('RGB')).astype(np.float32)
H,W=img.shape[:2]
if not Path('charmask.png').is_file():
    raise FileNotFoundError('Historical charmask.png is not in the handoff. Supply a character mask; see art/README.md.')
char=cv2.imread('charmask.png',0)
yy,xx=np.mgrid[0:H,0:W].astype(np.float32)
def soft(m,dil,blur):
    m=cv2.dilate(m,np.ones((dil,dil),np.uint8)) if dil else m
    return cv2.GaussianBlur(m.astype(np.float32)/255,(0,0),blur)
def ss(a,b,x):
    t=np.clip((x-a)/(b-a),0,1); return t*t*(3-2*t)
F={}
# ---- breathing: everything above the shins rises, chest widens a touch
cm=soft(char,7,5)
w=1-ss(420,610,yy)              # 1 at chest/head, 0 at shins
F['breath']=(-(xx-372)*0.018*cm*np.exp(-((yy-470)/55)**2), cm*w)   # source offsets for unit amplitude (px)
# ---- hair masks (pink-white hair, not skin, not hood)
r,g,b=img[...,0],img[...,1],img[...,2]
hair=((r>165)&(b>=g-4)&(b>140)&(g>140)).astype(np.uint8)*255
def region(poly):
    m=np.zeros((H,W),np.uint8); cv2.fillPoly(m,[np.array(poly,np.int32)],255); return m
L=region([(312,326),(346,326),(348,400),(312,400)]); Ll=region([(289,236),(306,236),(308,332),(289,332)])
R=region([(376,326),(414,326),(414,398),(376,398)]); Rl=region([(432,250),(452,250),(452,336),(432,336)])
def lock(maskpoly,root,tip):
    m=cv2.bitwise_and(hair,maskpoly); m=cv2.morphologyEx(m,cv2.MORPH_CLOSE,np.ones((3,3),np.uint8))
    return soft(m,5,2.2)*ss(root,tip,yy)
hl=lock(L,326,400)+0.6*lock(Ll,250,336); hr=lock(R,326,398)+0.6*lock(Rl,262,338)
F['hairL']=(hl, -0.25*hl); F['hairR']=(hr, -0.25*hr)
# ---- ears: rotation about their base, unit = 1 rad
def ear(box,pivot,base_y):
    m=np.zeros((H,W),np.uint8); cv2.rectangle(m,box[:2],box[2:],255,-1)
    m=soft(m,9,4)*(1-ss(base_y-6,base_y+6,yy))
    vx,vy=xx-pivot[0],yy-pivot[1]
    return (vy*m, -vx*m)
F['earL']=ear((310,140,368,190),(338,190),190)
F['earR']=(lambda f:f)(ear((430,165,484,216),(456,216),214))
# ---- grass / flowers: wind wave travelling left->right (two quadrature fields)
def boxm(x0,y0,x1,y1):
    m=np.zeros((H,W),np.uint8); cv2.rectangle(m,(x0,y0),(x1,y1),255,-1); return m
greenish=((g>r*0.95)&(g>b*1.05)&(g>40))|((r>200)&(g>200)&(b>180))   # grass or white petals
excl=cv2.dilate(char,np.ones((21,21),np.uint8))
excl|=boxm(80,465,190,592)        # lantern
grass=np.zeros((H,W),np.float32)
def tuft(box,base_y,top_y,amp):
    m=greenish.astype(np.uint8)*255
    m=cv2.morphologyEx(m,cv2.MORPH_OPEN,np.ones((2,2),np.uint8))
    region_soft=cv2.GaussianBlur((boxm(*box)>0).astype(np.float32),(0,0),12)*cv2.GaussianBlur((excl==0).astype(np.float32),(0,0),5)
    return soft(m,3,2.5)*region_soft*amp*ss(base_y,top_y,yy)
grass+=tuft((0,505,315,705),705,560,1.0)      # tufts + daisies around the lantern
grass+=tuft((430,465,650,575),575,470,0.6)    # bushes behind the cat
grass+=tuft((0,790,650,922),930,800,2.2)      # blurry foreground blades
k=2*np.pi/240
F['grassC']=(grass*np.cos(k*xx),np.zeros_like(grass)); F['grassS']=(grass*np.sin(k*xx),np.zeros_like(grass))
Image.fromarray(np.clip(grass/2.2*255*1.5,0,255).astype(np.uint8)).save('chk_grass.png')
# ---- water: horizontal ripples travelling down the pond
wm=cv2.imread('watermask.png',0)
wsoft=soft(wm,0,2.5)*cv2.GaussianBlur((wm>0).astype(np.float32),(0,0),1.5)
kw=2*np.pi/9.0
F['waterC']=(wsoft*np.cos(kw*yy), 0.25*wsoft*np.sin(kw*0.7*xx))
F['waterS']=(wsoft*np.sin(kw*yy), 0.25*wsoft*np.cos(kw*0.7*xx))
core=cv2.erode(wm,np.ones((9,9),np.uint8))>0
F['glint']=(core.astype(np.float32),np.zeros_like(wsoft))   # only used as a position list
# ---- pack: union region, sparse groups
groups=[['breath','hairL','hairR','earL','earR'],['grassC','grassS'],['waterC','waterS'],['glint']]
masks=[]
for grp in groups:
    m=np.zeros((H,W),bool)
    for k_ in grp: m|=(np.abs(F[k_][0])>2e-3)|(np.abs(F[k_][1])>2e-3)
    masks.append(m)
masks[1]&=~masks[0]; masks[2]&=~(masks[0]|masks[1])
u=masks[0]|masks[1]; ys,xs=np.nonzero(u); x0,y0,x1,y1=xs.min(),ys.min(),xs.max()+1,ys.max()+1
rw,rh=x1-x0,y1-y0
print('region',x0,y0,rw,rh,'active',[int(m.sum()) for m in masks])
buf=struct.pack('>5i',x0,y0,rw,rh,len(groups))
for grp,m in zip(groups,masks):
    sub=m[y0:y1,x0:x1]; idx=np.flatnonzero(sub).astype('>i4')
    nf=0 if grp==['glint'] else len(grp)*2
    buf+=struct.pack('>2i',nf,len(idx))+idx.tobytes()
    if nf==0: continue
    for k_ in grp:
        for f in F[k_]: buf+=f[y0:y1,x0:x1].reshape(-1)[np.flatnonzero(sub)].astype('>f2').tobytes()
open('poster-warp.bin','wb').write(gzip.compress(buf,9,mtime=0))
np.savez('fields.npz',**{k_+'x':F[k_][0] for k_ in F},**{k_+'y':F[k_][1] for k_ in F})
