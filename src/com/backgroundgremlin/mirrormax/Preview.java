package com.backgroundgremlin.mirrormax;

import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.util.function.Consumer;

final class Preview {
  final HandlerThread thread=new HandlerThread("MirrorMax preview");
  final Handler work;final Consumer<String> error;
  SurfaceTexture target,video;Surface surface,videoSurface;
  Gl.Window gl;Gl.Draw draw;Source source;MediaPlayer player;Gif.Reader gif;Bitmap bitmap;
  int[] scaled;int w,h;boolean playing=true,foreground,muted,ready,frame;
  long next,remaining;Mirror mirror=Mirror.NONE;final float[] matrix=new float[16];final Runnable tick=this::step;
  Preview(Consumer<String> error){this.error=error;thread.start();work=new Handler(thread.getLooper());}
  void target(SurfaceTexture t,int w,int h){
    work.post(()->safe(()->{
      if(target!=t){clear();if(target!=null)target.release();target=t;this.w=0;this.h=0;}
      if(w<2||h<2)return;
      boolean resized=this.w!=w||this.h!=h;
      this.w=w;this.h=h;
      if(resized)t.setDefaultBufferSize(w,h);
      if(gl==null)open();else render();
    }));
  }
  void detach(SurfaceTexture t){
    if(!work.post(()->{if(t==target){clear();target=null;}t.release();}))t.release();
  }
  void source(Source s){work.post(()->safe(()->{clear();source=s;open();}));}
  void update(Mirror m){work.post(()->safe(()->{mirror=m;render();}));}
  void play(boolean b,boolean mute,boolean active){work.post(()->safe(()->{
    boolean previous=playing&&foreground;playing=b;muted=mute;foreground=active;
    boolean current=b&&active;
    if(player!=null&&ready){player.setVolume(mute?0:1,mute?0:1);if(previous!=current){if(current)player.start();else player.pause();}}
    if(gif!=null&&source.frames>1&&previous!=current){
      work.removeCallbacks(tick);
      if(current){next=SystemClock.uptimeMillis()+Math.max(1,remaining);work.postAtTime(tick,next);}
      else remaining=Math.max(1,next-SystemClock.uptimeMillis());
    }
  }));}
  void open()throws IOException{
    if(source==null||target==null||w<2||h<2)return;
    target.setDefaultBufferSize(w,h);
    surface=new Surface(target);gl=new Gl.Window(surface,false);
    boolean external=source.kind.equals(Source.VIDEO);
    draw=new Gl.Draw(external);
    if(external){
      video=new SurfaceTexture(draw.tex);
      video.setOnFrameAvailableListener(v->{if(v==video)safe(()->{if(gl.current()){v.updateTexImage();v.getTransformMatrix(matrix);frame=true;render();}});},work);
      videoSurface=new Surface(video);
      MediaPlayer p=new MediaPlayer();player=p;p.setSurface(videoSurface);
      p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
      try(FileInputStream f=new FileInputStream(source.file)){p.setDataSource(f.getFD());}
      p.setLooping(true);
      p.setOnPreparedListener(x->{if(x==player)safe(()->{ready=true;x.setVolume(muted?0:1,muted?0:1);if(playing&&foreground)x.start();else x.seekTo(0,MediaPlayer.SEEK_CLOSEST_SYNC);});});
      p.setOnErrorListener((x,what,extra)->{if(x==player){error.accept("Android could not preview the video codec ("+what+", "+extra+").");clear();}return true;});
      p.prepareAsync();return;
    }
    if(source.kind.equals(Source.GIF)){
      gif=new Gif.Reader(source.file);int[] d=source.size(Math.min(1536,draw.limit()),1800000);
      bitmap=Bitmap.createBitmap(d[0],d[1],Bitmap.Config.ARGB_8888);
      Gif.Frame f=gif.next(()->{});if(f==null)throw new IOException("No GIF frames.");
      upload(f);remaining=f.ms;render();
      if(playing&&foreground&&source.frames>1){next=SystemClock.uptimeMillis()+f.ms;work.postAtTime(tick,next);}
    }else{
      bitmap=source.bitmap(Math.min(2048,draw.limit()),3000000);draw.upload(bitmap);frame=true;render();
    }
  }
  void upload(Gif.Frame f){
    int w0=bitmap.getWidth(),h0=bitmap.getHeight();
    int[] p=f.pixels;
    if(w0!=source.w||h0!=source.h){if(scaled==null)scaled=new int[w0*h0];resize(p,source.w,source.h,scaled,w0,h0);p=scaled;}
    bitmap.setPixels(p,0,w0,0,0,w0,h0);draw.upload(bitmap);frame=true;
  }
  static void resize(int[] p,int sw,int sh,int[] out,int w,int h){
    for(int y=0;y<h;y++){int s=(int)((2L*y+1)*sh/(2L*h))*sw;for(int x=0;x<w;x++)out[y*w+x]=p[s+(int)((2L*x+1)*sw/(2L*w))];}
  }
  void step(){safe(()->{
    if(gif==null||!playing||!foreground||!gl.current())return;
    Gif.Frame f=gif.next(()->{});
    if(f==null){gif.rewind();f=gif.next(()->{});}
    if(f==null)throw new IOException("Unable to loop this GIF.");
    upload(f);render();next=Math.max(SystemClock.uptimeMillis()+1,next+f.ms);work.postAtTime(tick,next);
  });}
  void render(){
    if(!frame||gl==null||w<1||h<1||!gl.current())return;
    boolean v=video!=null;
    int[] actual=gl.dimensions();
    int width=actual[0]>1?actual[0]:w,height=actual[1]>1?actual[1]:h;
    draw.apply(width,height,v?source.w:draw.width,v?source.h:draw.height,mirror,v?matrix:null,0,true);gl.frame(-1);
    // The consumer can update the native-buffer geometry on the first swap.
    int[] post=gl.dimensions();
    if(post[0]>1&&post[1]>1&&(post[0]!=width||post[1]!=height)){
      draw.apply(post[0],post[1],v?source.w:draw.width,v?source.h:draw.height,mirror,v?matrix:null,0,true);gl.frame(-1);
    }
  }
  void clear(){
    work.removeCallbacks(tick);
    if(player!=null){player.release();player=null;}
    if(videoSurface!=null){videoSurface.release();videoSurface=null;}
    if(video!=null){video.setOnFrameAvailableListener(null);video.release();video=null;}
    if(gif!=null){try{gif.close();}catch(IOException ignored){}gif=null;}
    if(bitmap!=null){bitmap.recycle();bitmap=null;}
    if(draw!=null){if(gl!=null&&gl.current())draw.close();draw=null;}
    if(gl!=null){gl.close();gl=null;}
    if(surface!=null){surface.release();surface=null;}
    scaled=null;frame=false;ready=false;
  }
  void safe(Gif.Check r){try{r.run();}catch(IOException|RuntimeException|OutOfMemoryError e){error.accept(e instanceof OutOfMemoryError?"Not enough memory to preview this file.":Exports.message(e));clear();}}
  void close(){work.post(()->{clear();if(target!=null){target.release();target=null;}thread.quitSafely();});}
}
