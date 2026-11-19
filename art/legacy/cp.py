from pathlib import Path
import runpy
import struct,sys
def parse(data):
    off=10; n=struct.unpack('>H',data[8:10])[0]; cp=[None]*n; i=1
    while i<n:
        t=data[off]
        if t==1:
            l=struct.unpack('>H',data[off+1:off+3])[0]; cp[i]=(1,data[off+3:off+3+l],off); off+=3+l
        elif t in(3,4): cp[i]=(t,data[off+1:off+5],off); off+=5
        elif t in(5,6): cp[i]=(t,data[off+1:off+9],off); off+=9; i+=1
        elif t in(7,8,16,19,20): cp[i]=(t,data[off+1:off+3],off); off+=3
        elif t in(9,10,11,12,17,18): cp[i]=(t,data[off+1:off+5],off); off+=5
        elif t==15: cp[i]=(t,data[off+1:off+4],off); off+=4
        else: raise Exception(t)
        i+=1
    return cp,off
def mutf8(b):
    return b.decode('utf-8','replace')
if __name__=='__main__':
    d=open(sys.argv[1],'rb').read(); cp,_=parse(d)
    for i,e in enumerate(cp):
        if e and e[0]==8:
            idx=struct.unpack('>H',e[1])[0]; print(i, repr(mutf8(cp[idx][1])))
