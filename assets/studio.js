"use strict";
(()=>{
  const $=id=>document.getElementById(id);
  const ui={media:null,playing:true,muted:false,compare:false,busy:false,sheet:null,returnFocus:null};
  const native=(key,...args)=>{if(window.MirrorAndroid&&typeof window.MirrorAndroid[key]==="function")window.MirrorAndroid[key](...args);};
  const el=(type,c,t)=>{const q=document.createElement(type);if(c)q.className=c;if(t!=null)q.textContent=t;return q;};
  function ico(name){
    const svg=document.createElementNS("http://www.w3.org/2000/svg","svg");
    const mark=document.createElementNS(svg.namespaceURI,"use");
    mark.setAttribute("href","#"+name);svg.append(mark);svg.setAttribute("aria-hidden","true");return svg;
  }
  function empty(q){while(q.firstChild)q.removeChild(q.firstChild);}
  function makeButton(label,c,fn,icon){
    const b=el("button",c);if(icon)b.append(ico(icon));if(label)b.append(el("span",null,label));b.onclick=fn;return b;
  }
  function select(entries){
    const n=el("select");
    for(const [k,v] of entries){const o=el("option",null,v);o.value=k;n.append(o);}return n;
  }
  function field(label,select){
    const l=el("label","field",label),s=el("span","select");s.append(select,ico("chev"));l.append(s);return l;
  }
  for(const p of [1,2]){
    const x=select([[0,"Off"],[1,"From left"],[2,"From right"],[3,"Both sides"]]);
    const y=select([[0,"Off"],[1,"From top"],[2,"From bottom"],[3,"Both sides"]]);
    x.id="x"+p;y.id="y"+p;x.setAttribute("aria-label","Pass "+p+" horizontal");y.setAttribute("aria-label","Pass "+p+" vertical");
    x.onchange=rule;y.onchange=rule;
    const a=makeButton("Mirror all","all",()=>{
      const n=a.getAttribute("aria-pressed")==="true"?"0":"3";x.value=n;y.value=n;rule();
    });
    a.id="all"+p;a.setAttribute("aria-label","Mirror all axes, pass "+p);a.setAttribute("aria-pressed","false");
    $("pass"+p).append(field("Horizontal",x),field("Vertical",y),a);
  }
  function layout(){
    const v=$("viewport"),s=ui.media,m=$("mount");
    if(!s||ui.sheet||!$("dropdown").hidden){native("viewport",0,0,0,0,innerWidth,innerHeight);return;}
    const r=v.getBoundingClientRect();
    if(r.width<1||r.height<1){native("viewport",0,0,0,0,innerWidth,innerHeight);return;}
    const fit=Math.min(r.width/s.w,r.height/s.h),w=s.w*fit,h=s.h*fit;
    m.style.width=w+"px";m.style.height=h+"px";
    const b=m.getBoundingClientRect();native("viewport",b.left,b.top,b.width,b.height,innerWidth,innerHeight);
  }
  function repaint(){requestAnimationFrame(()=>requestAnimationFrame(layout));}
  function rule(){
    for(const p of [1,2])$("all"+p).setAttribute("aria-pressed",String($("x"+p).value==="3"&&$("y"+p).value==="3"));
    $("pass2").hidden=!$("two").checked;
    native("mirror",+$("x1").value,+$("y1").value,$("two").checked,+$("x2").value,+$("y2").value,ui.compare);
    repaint();
  }
  function controls(){
    const m=ui.media;
    $("open").disabled=ui.busy;$("export").disabled=ui.busy||!m;$("sample").disabled=ui.busy;
    $("compare").disabled=!m;$("compare").setAttribute("aria-pressed",String(ui.compare));
    $("compare").setAttribute("aria-label",ui.compare?"Show reflection":"Show original");
    $("play").hidden=!m||!m.moving;$("mute").hidden=!m||!m.audio;
    empty($("play"));$("play").append(ico(ui.playing?"pauseIcon":"playIcon"));
    $("play").setAttribute("aria-label",ui.playing?"Pause playback":"Resume playback");
    empty($("mute"));$("mute").append(ico(ui.muted?"muteIcon":"sound"));
    $("mute").setAttribute("aria-label",ui.muted?"Unmute preview":"Mute preview");
  }
  function source(m){
    ui.media=m;ui.compare=false;
    if(m){
      ui.playing=m.play!==false;ui.muted=!!m.mute;
      for(const name of ["x1","y1","x2","y2"])if(m[name]!=null)$(name).value=String(m[name]);
      if(m.second!=null)$("two").checked=!!m.second;
    }
    $("badge").textContent=m?m.tag:"NO SOURCE";
    $("filename").textContent=m?m.name:"No media selected";
    const seconds=m?Math.round(m.ms/1000):0;
    $("details").textContent=m?m.w+" × "+m.h+(m.moving?"  ·  "+Math.floor(seconds/60)+":"+String(seconds%60).padStart(2,"0")+"  ·  Loop":"  ·  Still image"):"Your original stays untouched";
    $("intro").hidden=!!m;$("mount").hidden=!m;controls();rule();
  }
  function notice(msg){
    clearTimeout(notice.timer);$("notice").textContent=msg;$("notice").hidden=false;
    notice.timer=setTimeout(()=>$("notice").hidden=true,6100);
  }
  function menu(show){
    $("dropdown").hidden=!show;$("shade").hidden=!show&&!ui.sheet;
    $("menu").setAttribute("aria-expanded",String(show));
    if(show)$("sample").focus();else if(!ui.sheet)$("menu").focus();repaint();
  }
  function sheet(type,title,kicker,fn){
    if(!ui.sheet)ui.returnFocus=$("dropdown").hidden?document.activeElement:$("menu");
    ui.sheet=type;$("dropdown").hidden=true;$("menu").setAttribute("aria-expanded","false");
    $("sheetTitle").textContent=title;$("sheetKicker").textContent=kicker;$("close").hidden=type==="progress";
    $("sheet").hidden=false;$("shade").hidden=false;
    const c=$("content");empty(c);fn(c);$("sheet").focus();repaint();
  }
  function close(force){
    if(ui.sheet==="progress"&&force!==true)return true;
    if(ui.sheet){
      ui.sheet=null;$("sheet").hidden=true;$("shade").hidden=true;
      if(ui.returnFocus&&!ui.returnFocus.disabled&&ui.returnFocus.offsetWidth)ui.returnFocus.focus();
      repaint();return true;
    }
    if(!$("dropdown").hidden){menu(false);return true;}return false;
  }
  const path=()=>el("p","path","Downloads / MirrorMax / Exports");
  function exportSheet(){
    if(!ui.media||ui.busy)return;
    sheet("export","Export reflection","EXPORT",c=>{
      const s=ui.media,body=el("p","sheetLead");
      const choices=s.kind==="image"?[["png","PNG image"],["mp4","MP4 video"]]:s.kind==="gif"?[["gif","Animated GIF"],["mp4","MP4 video"]]:[["mp4","MP4 video"]];
      const format=select(choices),edge=select([[1080,"Up to 1080p"],[720,"Up to 720p"],[2160,"Up to 4K"]]),duration=select([[4,"4 seconds"],[2,"2 seconds"],[6,"6 seconds"],[10,"10 seconds"]]);
      const grid=el("div","grid"),res=field("Long edge",edge),len=field("Still duration",duration);
      grid.append(field("Format",format),res,len);
      const tick=el("label","checkbox"),input=el("input");input.type="checkbox";input.checked=s.audio;tick.append(input,el("span",null,"Include source audio"));
      function update(){
        const video=format.value==="mp4";
        res.hidden=!video;len.hidden=!video||s.kind!=="image";tick.hidden=!video||!s.audio;
        grid.classList.toggle("one",!video);
        body.textContent=format.value==="png"?"PNG keeps the image dimensions and transparency.":format.value==="gif"?"GIF keeps the frame timing and loops continuously.":"MP4 keeps the source aspect ratio.";
      }
      format.onchange=update;update();
      const actions=el("div","actions");
      actions.append(makeButton("Cancel","action ghost",()=>close(false)),makeButton("Save file","action primary",()=>{
        ui.busy=true;controls();progress(0);native("exportMedia",format.value,+edge.value,+duration.value,input.checked);
      },"download"));
      c.append(body,grid,tick,path(),actions);
    });
  }
  function progress(n){
    if(ui.sheet!=="progress")sheet("progress","Saving reflection","EXPORT IN PROGRESS",c=>{
      const p=el("p","progressLabel");p.id="exportStatus";
      const bar=el("progress");bar.id="exportProgress";bar.max=100;bar.setAttribute("aria-label","Export progress");
      const buttons=el("div","actions");const cancel=makeButton("Cancel export","action ghost",()=>{cancel.disabled=true;p.textContent="Canceling…";native("cancel");});
      buttons.append(cancel);c.append(p,bar,buttons);
    });
    $("exportProgress").value=n;
    $("exportStatus").textContent=n>=99?"Publishing to Exports…":n===0?"Preparing export…":"Rendering reflection · "+n+"%";
  }
  function saved(item){
    ui.busy=false;controls();
    sheet("saved","Reflection saved","EXPORT COMPLETE",c=>{
      const buttons=el("div","actions");
      buttons.append(makeButton("Share","action ghost",()=>native("fileAction",item.uri,true),"share"),makeButton("Open file","action primary",()=>native("fileAction",item.uri,false)));
      c.append(el("p","sheetLead",item.name),path(),buttons);
    });
  }
  function files(items){
    sheet("files","Your exports","MIRRORMAX FILES",c=>{
      c.append(path());
      if(!items.length){c.append(el("p","progressLabel","No exports yet. Open media and save your first reflection."));return;}
      const list=el("ul","list");
      for(const p of items){
        const li=el("li");
        const share=makeButton(null,"icon",()=>native("fileAction",p.uri,true),"share");share.setAttribute("aria-label","Share "+p.name);
        li.append(makeButton(p.name,"fileOpen",()=>native("fileAction",p.uri,false),"imageIcon"),share);list.append(li);
      }
      c.append(list);
    });
  }
  $("two").onchange=rule;
  $("reset").onclick=()=>{
    for(const k of ["x1","y1","x2","y2"])$(k).value="0";
    $("two").checked=false;ui.compare=false;rule();controls();
  };
  $("compare").onclick=()=>{ui.compare=!ui.compare;rule();controls();};
  $("play").onclick=()=>{ui.playing=!ui.playing;native("playback",ui.playing,ui.muted);controls();};
  $("mute").onclick=()=>{ui.muted=!ui.muted;native("playback",ui.playing,ui.muted);controls();};
  $("open").onclick=()=>native("pick");$("export").onclick=exportSheet;
  $("menu").onclick=()=>menu($("dropdown").hidden);$("shade").onclick=()=>close(false);$("close").onclick=()=>close(false);
  $("sample").onclick=()=>{menu(false);native("sample");};$("files").onclick=()=>{menu(false);native("files");};
  $("guide").onclick=()=>sheet("guide","Mirror controls","STUDIO GUIDE",c=>{
    const terms=el("dl","guide");
    for(const [name,help] of [
      ["From left / right","Keep the selected half and reflect it into the other half."],
      ["From top / bottom","Keep the selected half and reflect it vertically."],
      ["Both sides","Fit the full source into each half, reflecting the far half."],
      ["Mirror all","Use both axes to make four reflected quadrants."],
      ["Second pass","Apply an independent reflection to the first result."]
    ])terms.append(el("dt",null,name),el("dd",null,help));
    c.append(terms);
  });
  $("about").onclick=()=>sheet("about","MirrorMax","REFLECTION STUDIO",c=>{
    c.append(el("p","sheetLead","A local studio for images, GIFs and video."),
      el("p","path","Created by the Background Gremlin Group Creating Unique Tools for Unique Individuals."),
      el("p","progressLabel","Version 1.0.1"));
  });
  window.addEventListener("resize",repaint);
  if(window.ResizeObserver)new ResizeObserver(repaint).observe($("viewport"));
  document.addEventListener("keydown",e=>{
    if(e.key==="Escape"&&close(false)){e.preventDefault();return;}
    if(e.key!=="Tab"||!ui.sheet)return;
    const a=Array.from($("sheet").querySelectorAll("button,input,select")).filter(x=>x.offsetWidth&&!x.disabled);
    if(!a.length){e.preventDefault();return;}
    if(e.shiftKey&&(document.activeElement===a[0]||document.activeElement===$("sheet"))){e.preventDefault();a[a.length-1].focus();}
    else if(!e.shiftKey&&document.activeElement===a[a.length-1]){e.preventDefault();a[0].focus();}
  });
  window.Studio=Object.freeze({source,notice,layout,progress,saved,files,back:()=>close(false),
    loading(on){ui.busy=on;controls();if(on)notice("Opening media…");else $("notice").hidden=true;},
    failed(msg,render){ui.busy=false;controls();if(render)close(true);notice(msg);}
  });
  controls();native("ready");
})();
