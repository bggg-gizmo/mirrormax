from PIL import Image, ImageDraw, ImageFilter
import math, random
from pathlib import Path
W,H=1024,672
stops=[(0,(22,17,49)),(.38,(60,40,92)),(.74,(175,84,115)),(1,(247,151,114))]
r=random.Random(67)
im=Image.new('RGB',(W,H))
p=im.load()
for y in range(H):
    t=y/(H-1)
    for j in range(1,len(stops)):
        if t <= stops[j][0]:
            u=(t-stops[j-1][0])/(stops[j][0]-stops[j-1][0])
            base=[(1-u)*a+u*b for a,b in zip(stops[j-1][1],stops[j][1])]
            break
    for x in range(W):
        q=math.exp(-((x-585)/365)**2-((y-382)/186)**2)
        grain=r.choice([-2,-1,0,0,0,1,2])
        p[x,y]=tuple(max(0,min(255,round(c+d*q+grain))) for c,d in zip(base,(22,12,7)))
d=ImageDraw.Draw(im)
for _ in range(138):
    x,y=r.randrange(W),r.randrange(315)
    if r.random()>1-y/630:continue
    rad=r.choice([0,0,0,1,1,2])
    d.ellipse((x-rad,y-rad,x+rad,y+rad),fill=r.choice([(113,93,145),(150,125,166),(177,152,184),(220,183,190)]))
for x,y in [(137,147),(290,87),(813,128),(431,192),(930,79)]:
    d.line((x-5,y,x+5,y),fill=(209,172,187),width=1)
    d.line((x,y-5,x,y+5),fill=(209,172,187),width=1)
glow=Image.new('RGBA',im.size)
g=ImageDraw.Draw(glow)
g.ellipse((493,225,700,432),fill=(255,154,125,115))
im=Image.alpha_composite(im.convert('RGBA'),glow.filter(ImageFilter.GaussianBlur(34)))
d=ImageDraw.Draw(im)
for yy in range(-83,84):
    xx=int(math.sqrt(83**2-yy**2))
    c=tuple(round(a+(b-a)*(yy+83)/166) for a,b in zip((255,215,152),(248,107,113)))
    d.line((598-xx,327+yy,598+xx,327+yy),fill=c,width=1)
for yy in [353,368,380,391]:
    xx=int(math.sqrt(83**2-(yy-327)**2))
    d.line((598-xx,yy,598+xx,yy),fill=(202,102,123),width=2)
def ridge(points, color, noise=0):
    path=[]
    for (x,y),(xx,yy) in zip(points,points[1:]):
        for a in range(x,xx,4):
            sy=y+(yy-y)*(a-x)/(xx-x)
            path.append((a,round(sy+r.uniform(-noise,noise))))
    path.append(points[-1])
    d.polygon(path+[(W,H),(0,H)], fill=color)
ridge([(0,419),(50,399),(110,410),(170,379),(220,412),(275,398),(325,425),(383,404),(450,427),(510,390),(585,416),(625,402),(704,430),(752,404),(829,414),(925,363),(1024,409)],(76,56,98),2)
ridge([(0,464),(116,419),(182,457),(243,407),(298,442),(358,412),(399,461),(460,448),(562,493),(644,451),(683,469),(803,426),(862,449),(942,413),(1024,442)],(57,45,81),4)
ridge([(0,515),(55,478),(123,490),(201,466),(233,516),(306,501),(397,476),(438,516),(530,514),(639,531),(688,489),(739,514),(830,471),(891,504),(999,454),(1024,473)],(41,34,65),4)
ridge([(0,579),(87,551),(166,560),(225,533),(295,566),(404,556),(470,577),(545,559),(613,569),(717,547),(777,569),(857,555),(941,533),(1024,549)],(27,26,52),3)
ridge([(0,639),(98,606),(136,610),(220,590),(311,622),(391,614),(450,641),(511,630),(600,640),(693,627),(779,616),(880,592),(958,608),(1024,592)],(19,21,39),2)
for loc,height in [(77,94),(122,53),(851,52),(906,110),(947,73)]:
    base=655 if loc<300 else 638
    d.rounded_rectangle((loc,base-height,loc+7,base+4),radius=4,fill=(15,18,33))
    if height>60:
        d.line((loc-8,base-height+22,loc-8,base-height+42,loc+3,base-height+45),fill=(15,18,33),width=5)
        d.line((loc+15,base-height+34,loc+15,base-height+50,loc+4,base-height+55),fill=(15,18,33),width=5)
im.convert('RGB').save(Path(__file__).resolve().parent.parent/'assets'/'sample.png',optimize=True)
