from pathlib import Path
import runpy
import numpy as np, cv2
from PIL import Image, ImageDraw
src=np.array(Image.open('poster-base-v3.png').convert('RGB')).astype(np.float32)
H,W=src.shape[:2]
C=np.array([332.0,227.0]); r=np.array([17.5,4.0]); d=np.array([-2.5,16.0])
def tile(c,grow=0.56):
    S=4; m=Image.new('L',(W*S,H*S),0)
    pts=[tuple((c+sx*grow*r+sy*grow*d)*S) for sx,sy in [(-1,-1),(1,-1),(1,1),(-1,1)]]
    ImageDraw.Draw(m).polygon(pts,fill=255)
    return np.array(m.resize((W,H),Image.LANCZOS)).astype(np.float32)/255
def shifted(off):
    M=np.float32([[1,0,off[0]],[0,1,off[1]]])
    return cv2.warpAffine(src,M,(W,H),flags=cv2.INTER_LINEAR,borderMode=cv2.BORDER_REPLICATE)
out=src.copy()
jobs=[  # (target centre, source centre)
 (C-r-d, C-d), (C+r-d, C-d), (C-r+d, C+d), (C+r+d, C+d),      # corners -> hair
 (C-d, C-r-d), (C+r, C+r-d), (C+d, C-r+d), (C-r, C-r-d)]      # arms of the plus -> red
for i,(tgt,srcc) in enumerate(jobs):
    m=tile(tgt,0.64 if i<4 else 0.53)[...,None]
    out=out*(1-m)+shifted(tgt-srcc)*m
# clean thin red seams left outside the plus
plus=np.zeros((H,W),np.float32)
for c in (C,C-d,C+d,C-r,C+r): plus=np.maximum(plus,tile(c,0.53))
area=np.zeros((H,W),np.uint8); cv2.fillPoly(area,[np.int32([C-1.7*r-1.7*d,C+1.7*r-1.7*d,C+1.7*r+1.7*d,C-1.7*r+1.7*d])],255)
o=out.clip(0,255).astype(np.uint8)
red=((o[...,0].astype(int)-o[...,1])>60)&(plus<0.05)&(area>0)
red=cv2.dilate(red.astype(np.uint8)*255,np.ones((3,3),np.uint8))
o=cv2.inpaint(o[...,::-1].copy(),red,3,cv2.INPAINT_TELEA)[...,::-1]
inside=cv2.erode((plus>0.5).astype(np.uint8),np.ones((3,3),np.uint8))>0
pale=inside&((o[...,0].astype(int)-o[...,1])<70)
o=cv2.inpaint(o[...,::-1].copy(),cv2.dilate(pale.astype(np.uint8)*255,np.ones((2,2),np.uint8)),2,cv2.INPAINT_TELEA)[...,::-1]
out=o.astype(np.float32)
Image.fromarray(out.clip(0,255).astype(np.uint8)).save('poster-base.png')
im=Image.fromarray(out.clip(0,255).astype(np.uint8)).crop((280,185,380,275)).resize((400,360),Image.NEAREST); im.save('chk_plus.png')
