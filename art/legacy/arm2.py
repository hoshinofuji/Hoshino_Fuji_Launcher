from pathlib import Path
import runpy
import numpy as np, cv2
from PIL import Image, ImageDraw, ImageFilter
src=Image.open(str(Path(__file__).resolve().parents[1] / 'source/design.png')).convert('RGB')
# outline in mockup coords (clockwise from fist top-left)
P=[(206.0, 365.0), (242.0, 340.5), (257.0, 344.5), (266.5, 359.0), (277.5, 359.25), (288.75, 378.5), (297.5, 391.5), (310.0, 399.5), (318.75, 408.25), (321.5, 420.0), (320.5, 455.0), (313.0, 465.0), (297.5, 470.5), (280.0, 472.5), (262.5, 466.5), (240.0, 442.5), (221.25, 415.5), (211.0, 399.5), (215.0, 389.0)]
import sys
S=4
def mask(P,shrink=0):
    m=Image.new('L',(src.width*S,src.height*S),0); ImageDraw.Draw(m).polygon([(x*S,y*S) for x,y in P],fill=255)
    if shrink: m=m.filter(ImageFilter.MinFilter(shrink*2+1))
    return m.resize(src.size,Image.LANCZOS)
if __name__ == '__main__' and len(sys.argv) > 1 and sys.argv[1]=='show':
    c=src.crop((190,325,335,485)).resize((580,640),Image.NEAREST); d=ImageDraw.Draw(c)
    d.line([((x-190)*4,(y-325)*4) for x,y in P+[P[0]]],fill=(255,0,255),width=2)
    c.save('chk_poly.png')
