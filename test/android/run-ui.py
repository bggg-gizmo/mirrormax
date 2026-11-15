from pathlib import Path
import os, re, subprocess, time, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
folder=root/'bridge'
folder.mkdir(exist_ok=True)
adb=os.environ.get('MIRRORMAX_ADB','adb')
def cmd(*a,timeout=65):
    r=subprocess.run([adb,*a],check=True,capture_output=True,timeout=timeout)
    return r.stdout
def shell(*a,timeout=65):return cmd('shell',*a,timeout=timeout).decode('utf-8','replace')
def screen(name):
    p=folder/(name+'.png');p.write_bytes(cmd('exec-out','screencap','-p'));return p
def xml(label='tree'):
    shell('uiautomator','dump','/sdcard/window.xml',timeout=26)
    data=cmd('exec-out','cat','/sdcard/window.xml',timeout=12)
    (folder/(label+'.xml')).write_bytes(data)
    return list(ET.fromstring(data).iter('node'))
def coords(n):
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
    return ((b[0]+b[2])//2,(b[1]+b[3])//2) if len(b)==4 and b[0]<b[2] and b[1]<b[3] else None
def find(text, attempts=5, label=None):
    for i in range(attempts):
        try:
            ns=xml(label or 'tree')
            for n in ns:
                if any(text.casefold()==n.get(k,'').casefold() for k in ('text','content-desc','resource-id')) and coords(n):
                    return coords(n)
        except (ET.ParseError, subprocess.SubprocessError):pass
        time.sleep(.75)
    raise RuntimeError('Cannot find '+text)
def tap(x,y):
    shell('input','tap',str(x),str(y));time.sleep(.6)
def click(s,attempts=5):
    print('CLICK',s,flush=True);tap(*find(s,attempts=attempts))
def back():shell('input','keyevent','KEYCODE_BACK');time.sleep(.5)
def base():
    installed=shell('pm','path','com.backgroundgremlin.mirrormax')
    if not installed.strip().startswith('package:'): raise RuntimeError('Install MirrorMax first.')
    shell('am','start','-W','-n','com.backgroundgremlin.mirrormax/.MainActivity')
    screen('android-home')
def export_menu(fmt):
    click('Export');screen('android-export-sheet')
    if fmt=='mp4':
        try: default=find('MP4 video',attempts=1)
        except RuntimeError: default=None
        if default is None:
            for name in ('PNG image','Animated GIF'):
                try: click(name,attempts=1);break
                except RuntimeError: continue
            click('MP4 video')
    click('Save file')
    find('Reflection saved',attempts=16,label='saved')
    screen('android-saved-'+fmt)
    back()
def saved():
    return [x.strip() for x in shell('ls','-1','/sdcard/Download/MirrorMax/Exports/').splitlines() if x.strip()]
def pull_new(previous,stem):
    new=set(saved())-set(previous)
    if len(new)!=1:raise RuntimeError('Expected one export, got '+str(new))
    name=new.pop()
    destination=folder/(stem+Path(name).suffix)
    cmd('pull','/sdcard/Download/MirrorMax/Exports/'+name,str(destination))
    print('PUBLISHED',name,destination.stat().st_size,flush=True)
def run():
    base()
    click('Studio menu');click('Try a sample')
    find('Desert Dusk.png',label='sample')
    click('Mirror all axes, pass 1');click('Enable')
    click('Mirror all axes, pass 2')
    screen('android-editor')
    previous=[]
    try:previous=saved()
    except subprocess.CalledProcessError:pass
    export_menu('mp4');pull_new(previous,'sample-mp4')
    previous=saved();export_menu('png');pull_new(previous,'sample-png')
    click('Studio menu');click('Exports')
    find('Your exports');screen('android-exports')
if __name__=='__main__':
    try:run()
    except BaseException:
        try:screen('android-failure')
        except Exception:pass
        raise
