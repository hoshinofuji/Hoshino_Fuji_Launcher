from pathlib import Path
import runpy
import numpy as np, cv2
from PIL import Image, ImageDraw, ImageFilter
_geometry = runpy.run_path(str(Path(__file__).with_name('arm2.py')))
P, src = _geometry['P'], _geometry['src']   # P, src (mockup)
srcA=np.array(src).astype(np.float32)
X0,Y0,X1,Y1=2,74,652,996
H,W=srcA.shape[:2]; S=4
def polyA(P):
    m=Image.new('L',(W*S,H*S),0); ImageDraw.Draw(m).polygon([(x*S,y*S) for x,y in P],fill=255)
    return np.array(m.resize((W,H),Image.LANCZOS)).astype(np.float32)/255
alpha=np.clip(polyA(P),0,1)
inner=cv2.erode((alpha>0.99).astype(np.uint8),np.ones((5,5),np.uint8))
col=cv2.inpaint(np.array(src)[...,::-1].copy(),(1-inner).astype(np.uint8)*255,4,cv2.INPAINT_TELEA)[...,::-1].astype(np.float32)
ew=np.clip(1-cv2.GaussianBlur(inner.astype(np.float32),(0,0),1.0),0,1)[...,None]
armrgb=srcA*(1-ew)+col*ew
yy,xx=np.mgrid[0:H,0:W].astype(np.float32)
# elbow (mockup coords): on the arm axis between fist and shoulder
fist=np.array([238.,365.]); shoulder=np.array([305.,462.])
axis=(shoulder-fist)/np.linalg.norm(shoulder-fist)
PIV=np.array([303.0,458.0])
R=30.0
side=(xx-PIV[0])*axis[0]+(yy-PIV[1])*axis[1]          # <0 : fist side
dist=np.hypot(xx-PIV[0],yy-PIV[1])
fore_sel=np.clip(0.5-side,0,1)                          # soft cut line
disk=np.clip((R-dist)/4.0+0.5,0,1)
fore=alpha.copy()
fore_sel=np.ones_like(alpha)
upper=alpha*np.clip(side+0.5,0,1)
print('pivot mockup',PIV,'poster',PIV-[X0,Y0])
# forearm sprite with soft shadow
M=10
ys,xs=np.nonzero(fore>0.003); ax0,ay0,ax1,ay1=xs.min()-M,ys.min()-M,xs.max()+M+1,ys.max()+M+1
a=fore[ay0:ay1,ax0:ax1]
a_sh=(alpha*fore_sel)[ay0:ay1,ax0:ax1]
sh=np.roll(np.roll(a_sh,3,0),2,1); sh=cv2.GaussianBlur(sh,(0,0),3.0)*0.38
sh=sh*np.clip((dist[ay0:ay1,ax0:ax1]-R)/10,0,1)
outA=a+sh*(1-a)
rgb=(armrgb[ay0:ay1,ax0:ax1]*a[...,None]+np.array([10,24,16],np.float32)*(sh*(1-a))[...,None])/np.maximum(outA[...,None],1e-4)
Image.fromarray(np.dstack([np.clip(rgb,0,255),np.clip(outA*255,0,255)]).astype(np.uint8),'RGBA').save('arm.png')
print('arm box poster',ax0-X0,ay0-Y0)
# restore the upper arm into the base plate
base=np.array(Image.open('poster-base-plus.png').convert('RGB')).astype(np.float32)
up=(alpha*disk)[Y0:Y1,X0:X1][...,None]
base=base*(1-up)+armrgb[Y0:Y1,X0:X1]*up
Image.fromarray(base.clip(0,255).astype(np.uint8)).save('poster-base.png')
cv2.imwrite('upperarm.png',((upper[Y0:Y1,X0:X1]>0.4)*255).astype(np.uint8))
