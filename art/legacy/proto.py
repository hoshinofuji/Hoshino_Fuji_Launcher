from pathlib import Path
import runpy
import cv2, numpy as np, math
from PIL import Image
base=np.array(Image.open('poster-base.png').convert('RGB'))
H,W=base.shape[:2]; Fz=np.load('fields.npz')
arm=Image.open('arm.png'); eyes=Image.open('eyes-closed.png')
yy,xx=np.mgrid[0:H,0:W].astype(np.float32)
def warp(co):
    mx=xx.copy(); my=yy.copy()
    for k,a in co.items(): mx+=a*Fz[k+'x']; my+=a*Fz[k+'y']
    return cv2.remap(base,mx,my,cv2.INTER_LINEAR,borderMode=cv2.BORDER_REPLICATE)
def compose(co,lift,armang=0,blink=0):
    im=Image.fromarray(warp(co)).convert('RGBA')
    R=290;c=Image.new('RGBA',(2*R,2*R)); c.alpha_composite(arm,(R+192-298,R+254-388)); c=c.rotate(-armang,Image.BICUBIC,center=(R,R))
    im.alpha_composite(c,(298-R,round(388-lift)-R))
    return im
import sys
mode=sys.argv[1]
if mode=='ears':
    fr=[compose({'earL':th,'earR':-th},0) for th in (-0.15,0,0.15)]
    w=Image.new('RGB',(3*240,150))
    for i,f in enumerate(fr): w.paste(f.crop((290,120,510,260)).resize((240,153)).convert('RGB'),(i*240,0))
    w.save('chk_ears.png')
if mode=='gif':
    sm=lambda x:(lambda y:y*y*(3-2*y))(max(0,min(1,x)))
    def twitch(t):
        if t<0 or t>0.5: return 0
        if t<0.09: return sm(t/0.09)
        if t<0.2: return 1-1.35*sm((t-0.09)/0.11)
        return -0.35*(1-sm((t-0.2)/0.3))
    def bc(t):
        if t<0 or t>0.23: return 0
        if t<0.07: return sm(t/0.07)
        if t<0.13: return 1
        return 1-sm((t-0.13)/0.10)
    frames=[]; fps=20; T=7.6
    for i in range(int(fps*T)):
        t=i/fps
        b=0.5-0.5*math.cos(2*math.pi*t/3.8); A=2.0*b
        hL=1.5*math.sin(2*math.pi*t/3.1)+0.5*math.sin(2*math.pi*t/1.7+1.1)
        hR=1.5*math.sin(2*math.pi*t/3.1+0.9)+0.5*math.sin(2*math.pi*t/1.9+2.0)
        eL=-0.12*twitch(t-1.4)-0.12*twitch(t-5.3)-0.006*math.sin(t*1.7)
        eR=0.12*twitch(t-3.2)+0.12*twitch(t-5.35)+0.006*math.sin(t*1.9)
        tw=t-4.0; env=sm(min(1,tw/0.25))*sm(min(1,(2.1-tw)/0.35)) if 0<=tw<=2.1 else 0
        idle=1.4*math.sin(t*1.25)+0.5*math.sin(t*2.9)
        ang=idle*(1-env)+env*(-3.5+7.5*math.sin(tw*math.pi*2*1.55))
        gu=0.75+0.35*math.sin(t*0.55)+0.15*math.sin(t*1.9+0.7); ph=2*math.pi*t/2.6
        im=compose({'breath':A,'hairL':hL,'hairR':hR,'earL':eL,'earR':eR,'grassC':gu*math.cos(ph),'grassS':gu*math.sin(ph)},A,ang)
        bl=max(bc(t-0.6),bc(t-2.9),bc(t-3.18))
        if bl>0:
            e=eyes.copy(); e.putalpha(e.getchannel('A').point(lambda v:int(v*bl))); im.alpha_composite(e,(310,round(248-A)))
        frames.append(im.crop((0,100,650,922)).convert('RGB'))
    frames[0].save('proto_full.gif',save_all=True,append_images=frames[1:],duration=50,loop=0)
    # extremes check
    a=compose({'breath':2.0,'hairL':2,'hairR':2},2.0); b_=compose({'breath':0,'hairL':-2,'hairR':-2},0)
    w=Image.new('RGB',(820,620)); w.paste(a.crop((150,100,560,720)).convert('RGB'),(0,0)); w.paste(b_.crop((150,100,560,720)).convert('RGB'),(410,0)); w.save('chk_extremes.png')
if mode=='grass':
    frames=[]
    for i in range(50):
        t=i/20; w=2*math.pi*t/2.6; A=1.0+0.35*math.sin(t*0.7)
        im=Image.fromarray(warp({'grassC':A*math.cos(w),'grassS':A*math.sin(w)}))
        frames.append(im.crop((0,460,650,922)).convert('RGB'))
    frames[0].save('grass.gif',save_all=True,append_images=frames[1:],duration=50,loop=0)
    w_=Image.new('RGB',(1300,462)); w_.paste(frames[0],(0,0)); w_.paste(frames[13],(650,0)); w_.save('chk_grassframes.png')
if mode=='water':
    a=Image.fromarray(warp({'waterC':1.4,'waterS':0})).crop((280,660,650,880)); b=Image.fromarray(warp({'waterC':0,'waterS':1.4})).crop((280,660,650,880))
    w=Image.new('RGB',(740,220)); w.paste(a,(0,0)); w.paste(b,(370,0)); w.resize((1110,330)).save('chk_waterwarp.png')
