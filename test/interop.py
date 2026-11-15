from PIL import Image,ImageDraw,ImageSequence
from pathlib import Path
import struct,sys
base=Path(__file__).parent/'fixtures'
if sys.argv[1]=='make':
    a=[]
    for i in range(4):
        im=Image.new('RGBA',(71,49),(0,0,0,0))
        ImageDraw.Draw(im).rectangle((i*9,6+i*5,i*9+19,24+i*5), fill=(90+i*30,90,211,255))
        a.append(im)
    a[0].save(base/'pillow.gif',save_all=True,append_images=a[1:],duration=[70,90,150,230],disposal=[1,2,3,2],loop=0,transparency=0)
else:
    raw=(base/'mirror.raw').read_bytes();w,h=struct.unpack_from('>II',raw);p=8
    with Image.open(base/'pillow.gif') as im:
        for f in ImageSequence.Iterator(im):
            assert struct.unpack_from('>I',raw,p)[0]==f.info['duration'];p+=4
            expected=tuple(a<<24|r<<16|g<<8|b if a else 0 for r,g,b,a in f.convert('RGBA').get_flattened_data())
            assert struct.unpack_from(f'>{w*h}I',raw,p)==expected;p+=4*w*h
        assert p==len(raw)
    with Image.open(base/'native.gif') as im:
        assert im.n_frames==3 and im.info['loop']==0
        assert [f.info['duration'] for f in ImageSequence.Iterator(im)]==[10,340,130]
        im.seek(0);assert im.convert('RGBA').getpixel((0,0))[3]==0
    print('Independent GIF encoder and decoder interoperability passes')
