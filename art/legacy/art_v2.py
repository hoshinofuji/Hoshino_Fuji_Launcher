from pathlib import Path
import runpy
import cv2, numpy as np
from PIL import Image, ImageDraw, ImageFilter
_geometry = runpy.run_path(str(Path(__file__).with_name('arm2.py')))
P, src = _geometry['P'], _geometry['src']   # P (arm outline, mockup coords), src
srcA=np.array(src).astype(np.float32)
X0,Y0,X1,Y1=2,74,652,996
H,W=srcA.shape[:2]
S=4
def polyA(P,grow=0.0):
    m=Image.new('L',(W*S,H*S),0); ImageDraw.Draw(m).polygon([(x*S,y*S) for x,y in P],fill=255)
    if grow>0: m=m.filter(ImageFilter.MaxFilter(int(grow*S)*2+1))
    return np.array(m.resize((W,H),Image.LANCZOS)).astype(np.float32)/255
alpha=np.clip(polyA(P),0,1)
# ---- colour decontamination: edge pixels take colours from the arm interior
inner=(cv2.erode((alpha>0.99).astype(np.uint8),np.ones((5,5),np.uint8)))
col=cv2.inpaint(src_np:=np.array(src)[...,::-1].copy(),(1-inner).astype(np.uint8)*255,4,cv2.INPAINT_TELEA)[...,::-1].astype(np.float32)
edge_w=np.clip(1-cv2.GaussianBlur(inner.astype(np.float32),(0,0),1.0),0,1)[...,None]
armrgb=srcA*(1-edge_w)+col*edge_w
# ---- sprite with soft contact shadow underneath
M=10
ys,xs=np.nonzero(alpha>0.003); ax0,ay0,ax1,ay1=xs.min()-M,ys.min()-M,xs.max()+M+1,ys.max()+M+1
a=alpha[ay0:ay1,ax0:ax1]
sh=np.roll(np.roll(a,3,0),2,1); sh=cv2.GaussianBlur(sh,(0,0),3.0)*0.42
outA=a+sh*(1-a)
rgb=(armrgb[ay0:ay1,ax0:ax1]*a[...,None]+np.array([10,24,16],np.float32)*(sh*(1-a))[...,None])/np.maximum(outA[...,None],1e-4)
sprite=np.dstack([np.clip(rgb,0,255),np.clip(outA*255,0,255)]).astype(np.uint8)
Image.fromarray(sprite,'RGBA').save('arm.png')
print('arm box',ax0-X0,ay0-Y0)
# ---- base plate: remove arm (new outline ∪ old cut) and nav text
# The obsolete preview-size check was removed; source pixels come from design.png.
hole=((polyA(P,grow=3.0)>0.02)).astype(np.uint8)*255
oldpoly=[(203,366),(240,337),(268,355),(280,356),(286,378),(296,392),(314,404),(321,410),(321,466),(314,476),(298,482),(284,482),(258,460),(232,434),(204,412),(199,392)]
hole|=(polyA(oldpoly,grow=3.0)>0.02).astype(np.uint8)*255
r,g,b=srcA[...,0],srcA[...,1],srcA[...,2]
nav=np.zeros((H,W),np.uint8); nav[84:154,425:645]=255
navtxt=((srcA.max(axis=2)>95)|((g>120)&(g>r+20)))&(nav>0)
navtxt=cv2.dilate(navtxt.astype(np.uint8)*255,np.ones((5,5),np.uint8)); navtxt[146:154,405:650]=255
# background-only inpaint: treat the body strip as unknown too so no pink/green bleeds in
band=np.zeros((H,W),np.uint8); cv2.fillPoly(band,[np.array([(274,330),(470,330),(470,520),(274,520)],np.int32)],255)
bgfill=cv2.inpaint(np.array(src)[...,::-1].copy(),hole|band,15,cv2.INPAINT_TELEA)[...,::-1].astype(np.float32)
bgfill=cv2.GaussianBlur(bgfill,(0,0),2.0)
# behind the arm next to the body: the hood's shadow side
edge=np.zeros((H,W),np.uint8)
cv2.polylines(edge,[np.array([(int(x),int(y)) for x,y in P[4:12]],np.int32)],False,255,2)
d=cv2.distanceTransform(255-edge,cv2.DIST_L2,5)
shade=np.clip(1-d/16,0,1)[...,None]*0.85
bgfill=bgfill*(1-shade)+np.array([34,52,32],np.float32)*shade
hm=cv2.GaussianBlur(hole.astype(np.float32)/255,(0,0),1.2)[...,None]
fill=(srcA*(1-hm)+bgfill*hm).astype(np.uint8)
base=cv2.inpaint(fill[...,::-1].copy(),navtxt,9,cv2.INPAINT_TELEA)[...,::-1]
Image.fromarray(base[Y0:Y1,X0:X1]).save('poster-base.png')
