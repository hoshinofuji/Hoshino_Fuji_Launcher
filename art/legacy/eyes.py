from pathlib import Path
import runpy
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
src=Image.open(str(Path(__file__).resolve().parents[1] / 'source/design.png')).convert('RGB')
X0,Y0=2,74
S=4  # supersample
W,H=src.size
eyes=[dict(q=[(318,331),(356,336),(354,379),(319,375)],skin=[(366,362),(370,372),(337,327)], line=[(320,360),(328,365),(338,367),(348,366),(355,362)]),
      dict(q=[(385,347),(426,356),(423,394),(383,391)],skin=[(370,372),(374,380),(405,345)], line=[(384,375),(393,381),(404,383),(414,382),(424,378)])]
ov=Image.new('RGBA',(W*S,H*S),(0,0,0,0)); d=ImageDraw.Draw(ov)
arr=np.array(src).astype(int)
for e in eyes:
    lo=np.mean([arr[y,x] for x,y in e['skin'][:2]],axis=0); hi=np.array(arr[e['skin'][2][1],e['skin'][2][0]])
    ys=[p[1] for p in e['q']]; y0,y1=min(ys),max(ys)
    m=Image.new('L',(W*S,H*S),0); ImageDraw.Draw(m).polygon([(x*S,y*S) for x,y in e['q']],fill=255)
    m=m.filter(ImageFilter.GaussianBlur(S*0.8))
    grad=Image.new('RGBA',(W*S,H*S))
    g=np.zeros((H*S,W*S,4),np.uint8)
    for yy in range((y0-2)*S,(y1+3)*S):
        t=min(max((yy/S-y0)/(y1-y0),0),1)
        # lid: slightly darker/pinker at top, skin at bottom
        c=lo*(0.94+0.06*t)
        g[yy,:,:3]=np.clip(c,0,255); g[yy,:,3]=255
    grad=Image.fromarray(g,'RGBA'); grad.putalpha(m)
    ov=Image.alpha_composite(ov,grad); d=ImageDraw.Draw(ov)
    pts=[(x*S,y*S) for x,y in e['line']]
    d.line(pts,fill=(58,38,44,255),width=int(3.2*S),joint='curve')
    for p in (pts[0],pts[-1]):
        d.ellipse((p[0]-1.6*S,p[1]-1.6*S,p[0]+1.6*S,p[1]+1.6*S),fill=(58,38,44,255))
    # small lash flick at outer corner
    ox,oy=e['line'][-1] if e is eyes[1] else e['line'][0]
    dx=1 if e is eyes[1] else -1
    d.line([(ox*S,oy*S),((ox+dx*3)*S,(oy-3)*S)],fill=(58,38,44,255),width=int(2.2*S))
    # blush hint under line
ov=ov.resize((W,H),Image.LANCZOS)
box=(312,322,432,402)
crop=ov.crop(box); crop.save('eyes-closed.png')
open('meta.txt','a').write(f'{box[0]-X0} {box[1]-Y0}\n')
prev=src.convert('RGBA'); prev.alpha_composite(ov)
prev.crop((290,310,450,410)).resize((640,400),Image.LANCZOS).save('chk_eyes.png')
