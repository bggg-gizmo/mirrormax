import qt,slicer
from pathlib import Path
import json,traceback
root=Path(__file__).resolve().parents[1]
(root/'out').mkdir(exist_ok=True)
w=slicer.qSlicerWebWidget();w.resize(393,788);w.show()
stage=0
def js(s): w.evalJS("(function(){try{"+s+"}catch(e){return JSON.stringify({error:e.message});}})()")
def later(f,ms=440): qt.QTimer.singleShot(ms,f)
def capture(name): assert w.grab().save(str(root/'out'/name))
def done(code,raw):
 global stage
 try:
  val=json.loads(raw)
  assert not isinstance(val,dict),val
  if stage==0:
   assert val==[393,788,True,True],val
   stage=1
   js("""
    var a=id=>document.getElementById(id);
    window.record=[];window.MirrorAndroid=new Proxy({}, {get:(_,name)=>(...args)=>record.push([name,...args])});
    Studio.source({name:'Desert Dusk.png',kind:'image',tag:'PNG',w:1024,h:672,ms:0,moving:false,audio:false});
    a('all1').click();a('two').click();a('x2').value='2';a('y2').value='1';a('y2').dispatchEvent(new Event('change'));
    var i=new Image();
    i.onload=function(){
      var m=a('mount'),w=i.width,h=i.height,s=document.createElement('canvas'),d=document.createElement('canvas');
      s.width=d.width=w;s.height=d.height=h;
      var sc=s.getContext('2d'),dc=d.getContext('2d');sc.drawImage(i,0,0);
      var p=sc.getImageData(0,0,w,h),q=dc.createImageData(w,h),A=p.data,B=q.data;
      var f=(u,n,k)=>k===1?Math.min(u,n-u):k===2?Math.max(u,n-u):k===3?2*Math.min(u,n-u):u;
      for(var y=0;y<h;y++)for(var x=0;x<w;x++){
        var xx=Math.min(w-1,f(f(2*x+1,2*w,2),2*w,3)>>1),yy=Math.min(h-1,f(f(2*y+1,2*h,1),2*h,3)>>1);
        var src=(yy*w+xx)*4,dst=(y*w+x)*4;for(var c=0;c<4;c++)B[dst+c]=A[src+c];
      }
      dc.putImageData(q,0,0);d.style.cssText='width:100%;height:100%;display:block';m.append(d);document.body.dataset.media='ready';
    };
    i.src='sample.png';
    return JSON.stringify(record.filter(x=>x[0]==='mirror').slice(-1)[0]);
   """)
  elif stage==1:
   assert val==['mirror',3,3,True,2,1,False],val
   stage=2
   later(lambda:js("return JSON.stringify([document.body.dataset.media, document.querySelector('#mount').getBoundingClientRect().height>100]);"),700)
  elif stage==2:
   assert val==['ready',True],val
   capture('MirrorMax-editor.png');stage=3
   js("""
    var a=id=>document.getElementById(id);
    a('compare').click();var comp=record.filter(x=>x[0]==='mirror').slice(-1)[0].slice(-1)[0];a('compare').click();a('export').click();
    var fmt=a('content').querySelector('select'), names=()=>Array.from(a('content').querySelector('select').options).map(x=>x.value);
    var still=names();fmt.value='mp4';fmt.dispatchEvent(new Event('change'));
    var duration=!a('content').querySelectorAll('.field')[2].hidden;
    a('content').querySelector('.primary').click();var ex=record.filter(x=>x[0]==='exportMedia').slice(-1)[0];Studio.failed('Canceled',true);
    Studio.source({name:'Test.gif',tag:'GIF',kind:'gif',w:100,h:100,moving:true,ms:1000});a('export').click();var gif=names();Studio.back();
    Studio.source({name:'Test.mov',tag:'MOV',kind:'video',w:100,h:100,moving:true,audio:true,ms:1000});a('export').click();var mov=names();var sound=!a('content').querySelector('.checkbox').hidden;
    return JSON.stringify([comp,still,duration,ex,gif,mov,sound]);
   """)
  elif stage==3:
   assert val==[True,['png','mp4'],True,['exportMedia','mp4',1080,4,False],['gif','mp4'],['mp4'],True],val
   print("APK-bundled editor interaction and format checks pass.",flush=True);slicer.app.exit(0)
 except Exception:
  traceback.print_exc();slicer.app.exit(1)
w.connect('evalResult(QString,QString)',done)
w.url=qt.QUrl.fromLocalFile(str(root/'assets/studio.html'))
later(lambda:js("return JSON.stringify([innerWidth,innerHeight,!!Studio,document.getElementById('export').disabled]);"),2200)
