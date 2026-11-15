from pathlib import Path
from PIL import Image,ImageSequence
import json, subprocess, numpy as np
d=Path('bridge/device-exports')
files=sorted(d.glob('MirrorMax_*'))
assert len(files)==8, len(files)
names=['png','sample-mp4','still-mp4','gif','gif-mp4','video-audio','video-silent','mov-audio']
def run(*a):return subprocess.check_output(a)
def fold(x,n,m):
    if m==1:return np.minimum(x,n-x)
    if m==2:return np.maximum(x,n-x)
    if m==3:return 2*np.minimum(x,n-x)
    return x
def expected(a):
    h,w=a.shape[:2]
    x=np.minimum(w-1,fold(fold(2*np.arange(w)+1,2*w,2),2*w,3)//2)
    y=np.minimum(h-1,fold(fold(2*np.arange(h)+1,2*h,1),2*h,3)//2)
    return a[y[:,None],x[None,:]]
def video(file):
    j=json.loads(run('ffprobe','-v','error','-show_streams','-show_format','-of','json',str(file)))
    vs=next(x for x in j['streams'] if x['codec_type']=='video')
    w,h=vs['width'],vs['height']
    audio=any(x['codec_type']=='audio' for x in j['streams'])
    raw=run('ffmpeg','-v','error','-threads','1','-i',str(file),'-frames:v','1','-pix_fmt','rgb24','-f','rawvideo','-threads','1','-')
    return np.frombuffer(raw,np.uint8).reshape((h,w,3)),float(j['format']['duration']),audio,vs['codec_name']
static=np.asarray(Image.open('assets/mirror-fixture.png').convert('RGB'))
for case,f in zip(names,files):
    if case=='png':
        p=np.asarray(Image.open(f).convert('RGB'));assert np.array_equal(p,expected(static))
        print(case,f.name,'pixel-exact')
    elif case=='gif':
        with Image.open(f) as a,Image.open('assets/mirror-fixture.gif') as b:
            assert a.info['loop']==0 and a.n_frames==b.n_frames
            for x,y in zip(ImageSequence.Iterator(a),ImageSequence.Iterator(b)):
                assert x.info['duration']==y.info['duration']
                assert np.array_equal(np.asarray(x.convert('RGB')),expected(np.asarray(y.convert('RGB'))))
        print(case,f.name,'8 exact frames, timings and endless loop')
    else:
        p,dur,audio,codec=video(f)
        assert codec=='h264'
        assert audio==(case in ('video-audio','mov-audio'))
        target=2 if case in ('sample-mp4','still-mp4') else 1.01 if case=='gif-mp4' else 1
        assert abs(dur-target)<.13,(case,dur,target)
        if case!='sample-mp4':
            src=video(Path('assets/mirror-fixture.mp4'))[0] if case in ('video-audio','video-silent','mov-audio') else static
            m=expected(src);assert p.shape==m.shape
            delta=np.abs(p.astype(float)-m)
            assert delta.mean()<12,(case,delta.mean())
            assert abs(p[:,:,:].astype(float)-p[:,::-1,:]).mean()<4,(case,'horizontal seam')
            assert abs(p[:,:,:].astype(float)-p[::-1,:,:]).mean()<4,(case,'vertical seam')
        print(case,f.name,p.shape,str(dur)+'s','audio='+str(audio))
print('All Android-exported files valid and expected two-pass reflection confirmed.')
