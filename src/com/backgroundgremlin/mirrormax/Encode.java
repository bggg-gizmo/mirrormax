package com.backgroundgremlin.mirrormax;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.Surface;
import java.io.*;
import java.nio.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

final class Encode {
  static final String AVC=MediaFormat.MIMETYPE_VIDEO_AVC;
  static final String AAC=MediaFormat.MIMETYPE_AUDIO_AAC;
  static final long WAIT=20000;
  static final class Profile{
    final String name;final int w,h,rate,bitrate;
    Profile(String n,int w,int h,int fps,int b){name=n;this.w=w;this.h=h;rate=fps;bitrate=b;}
  }
  static int align(double x,int a,int min){return Math.max((min+a-1)/a*a,(int)Math.round(x/a)*a);}
  static Profile profile(Source s,int cap,int fps)throws IOException{
    int[] d=s.size(cap,(long)cap*cap);
    MediaCodecInfo[] list=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
    for(double scale:new double[]{1,.875,.75,.625,.5,.375,.25}){
      for(MediaCodecInfo i:list){
        if(!i.isEncoder())continue;
        try{
          MediaCodecInfo.CodecCapabilities c=i.getCapabilitiesForType(AVC);
          boolean surf=false;for(int v:c.colorFormats)surf|=v==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;if(!surf)continue;
          MediaCodecInfo.VideoCapabilities v=c.getVideoCapabilities();
          int w=align(d[0]*scale,v.getWidthAlignment(),v.getSupportedWidths().getLower());
          int h=align(d[1]*scale,v.getHeightAlignment(),v.getSupportedHeights().getLower());
          if(v.areSizeAndRateSupported(w,h,fps))
            return new Profile(i.getName(),w,h,fps,v.getBitrateRange().clamp((int)Math.max(300000,(long)w*h*fps/5)));
        }catch(RuntimeException ignored){}
      }
    }
    throw new IOException("No compatible H.264 encoder is available.");
  }
  static void write(Source s,File file,Mirror rule,int cap,int seconds,boolean audio,Gif.Check check,IntConsumer progress)throws IOException{
    MediaExtractor sound=null;File aac=null;long audioOffset=0;
    try{
      int fps=s.kind.equals(Source.IMAGE)?24:30;
      if(s.kind.equals(Source.VIDEO)){
        MediaExtractor in=new MediaExtractor();
        try{in.setDataSource(s.file.getPath());
          int idx=Source.track(in,"video/");
          if(idx>=0)fps=Math.min(60,Math.max(24,Source.value(in.getTrackFormat(idx),MediaFormat.KEY_FRAME_RATE,30)));
        }finally{in.release();}
      }
      if(s.audio&&audio){
        sound=new MediaExtractor();sound.setDataSource(s.file.getPath());int a=Source.track(sound,"audio/");
        if(a<0){sound.release();sound=null;}
        else{
          sound.selectTrack(a);long start=sound.getSampleTime();
          if(sound.getSampleTrackIndex()<0){sound.release();sound=null;}
          else if(!AAC.equals(sound.getTrackFormat(a).getString(MediaFormat.KEY_MIME))){
            sound.release();sound=null;aac=File.createTempFile("audio-", ".m4a",file.getParentFile());
            Audio.convert(s.file,aac,check);
            sound=new MediaExtractor();sound.setDataSource(aac.getPath());
            a=Source.track(sound,"audio/");if(a<0)throw new IOException("No AAC audio was encoded.");
            sound.selectTrack(a);audioOffset=Math.max(0,start)-Math.max(0,sound.getSampleTime());
          }
        }
      }
      long duration=s.kind.equals(Source.IMAGE)?seconds*1000000L:s.duration;
      Profile p=profile(s,cap,fps);progress.accept(0);
      try(Writer out=new Writer(file,p,sound)){
        long first=0;
        if(s.kind.equals(Source.VIDEO))first=movie(s,rule,out,check,progress);
        else frames(s,rule,out,duration,p.rate,check,progress);
        out.finish(duration,first-audioOffset,check);
      }
      progress.accept(97);
    }finally{if(sound!=null)sound.release();if(aac!=null)aac.delete();}
  }
  static void frames(Source s,Mirror m,Writer out,long duration,int fps,Gif.Check check,IntConsumer progress)throws IOException{
    try(Gl.Draw d=new Gl.Draw(false)){
      if(s.kind.equals(Source.GIF)){
        try(Gif.Reader gif=new Gif.Reader(s.file)){
          int[] z=s.size(Math.min(2048,d.limit()),4000000);
          Bitmap b=Bitmap.createBitmap(z[0],z[1],Bitmap.Config.ARGB_8888);
          int[] p=z[0]==s.w&&z[1]==s.h?null:new int[z[0]*z[1]];
          try{
            long t=0;
            for(Gif.Frame f;(f=gif.next(check))!=null;){
              if(p!=null)Preview.resize(f.pixels,s.w,s.h,p,z[0],z[1]);
              b.setPixels(p==null?f.pixels:p,0,z[0],0,0,z[0],z[1]);
              d.upload(b);out.present(d,z[0],z[1],m,null,0,t,check);t+=f.ms*1000L;
              progress.accept((int)Math.min(96,t*96/duration));
            }
          }finally{b.recycle();}
        }
      }else{
        Bitmap b=s.bitmap(Math.min(4096,d.limit()),10000000);
        try{
          d.upload(b);
          for(long i=0,t=0;t<duration;i++,t=i*1000000/fps){
            out.present(d,b.getWidth(),b.getHeight(),m,null,0,t,check);
            progress.accept((int)Math.min(96,t*96/duration));
          }
        }finally{b.recycle();}
      }
    }
  }
  static long movie(Source s,Mirror m,Writer out,Gif.Check check,IntConsumer progress)throws IOException{
    HandlerThread h=new HandlerThread("MirrorMax frames");h.start();
    MediaExtractor in=new MediaExtractor();MediaCodec decoder=null;
    try(Gl.Draw d=new Gl.Draw(true)){
      Semaphore signal=new Semaphore(0);
      SurfaceTexture texture=new SurfaceTexture(d.tex);Surface surface=new Surface(texture);
      try{
        texture.setOnFrameAvailableListener(t->signal.release(),new Handler(h.getLooper()));
        in.setDataSource(s.file.getPath());int index=Source.track(in,"video/");
        if(index<0)throw new IOException("Video track is missing.");in.selectTrack(index);
        MediaFormat fmt=in.getTrackFormat(index);
        fmt.setInteger(MediaFormat.KEY_ROTATION,0);
        String mime=fmt.getString(MediaFormat.KEY_MIME);
        decoder=MediaCodec.createDecoderByType(mime);
        if(Build.VERSION.SDK_INT>=31)fmt.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        try{decoder.configure(fmt,surface,null,0);}
        catch(IllegalArgumentException|IllegalStateException e){
          decoder.release();decoder=null;fmt.removeKey(MediaFormat.KEY_COLOR_TRANSFER_REQUEST);
          decoder=MediaCodec.createDecoderByType(mime);decoder.configure(fmt,surface,null,0);
        }
        decoder.start();MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        float[] matrix=new float[16];boolean inEnd=false,outEnd=false;
        long first=Long.MIN_VALUE,last=-1,idle=SystemClock.elapsedRealtime();
        while(!outEnd){
          check.run();boolean step=false;
          if(!inEnd){
            int i=decoder.dequeueInputBuffer(0);
            if(i>=0){
              ByteBuffer bytes=decoder.getInputBuffer(i);if(bytes==null)throw new IOException("No decoder buffer.");
              bytes.clear();int n=in.readSampleData(bytes,0);
              if(n<0){decoder.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inEnd=true;}
              else {decoder.queueInputBuffer(i,0,n,in.getSampleTime(),0);in.advance();}
              step=true;
            }
          }
          int i=decoder.dequeueOutputBuffer(info,8000);
          if(i>=0){
            outEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            boolean show=info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0;
            decoder.releaseOutputBuffer(i,show);
            if(show){
              long until=SystemClock.elapsedRealtime()+WAIT;
              try{while(!signal.tryAcquire(55,TimeUnit.MILLISECONDS)){
                check.run();if(SystemClock.elapsedRealtime()>until)throw new IOException("Video frame timed out.");
              }}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Export canceled.");}
              texture.updateTexImage();texture.getTransformMatrix(matrix);
              if(first==Long.MIN_VALUE)first=info.presentationTimeUs;
              long t=info.presentationTimeUs-first;
              if(t>last){out.present(d,s.w,s.h,m,matrix,s.rotation,t,check);last=t;progress.accept((int)Math.min(96,96*t/s.duration));}
            }
            step=true;
          }else if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)step=true;
          out.drain(false,check);
          if(step)idle=SystemClock.elapsedRealtime();
          if(SystemClock.elapsedRealtime()-idle>WAIT)throw new IOException("The video decoder stopped responding.");
        }
        if(last<0)throw new IOException("No video frames were decoded.");
        return first;
      }finally{if(decoder!=null)try{decoder.release();}catch(RuntimeException ignored){}surface.release();texture.release();}
    }finally{in.release();h.quitSafely();}
  }
  static final class Writer implements AutoCloseable{
    final Profile p; final MediaExtractor audio;
    MediaCodec codec;Surface surface;Gl.Window gl;MediaMuxer mux;
    boolean started,stopped;int v=-1,a=-1;
    long last=-1;final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    Writer(File f,Profile p,MediaExtractor audio)throws IOException{
      this.p=p;this.audio=audio;
      try{
        MediaFormat fmt=MediaFormat.createVideoFormat(AVC,p.w,p.h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE,p.rate);fmt.setInteger(MediaFormat.KEY_BIT_RATE,p.bitrate);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);fmt.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
        fmt.setInteger(MediaFormat.KEY_COLOR_STANDARD,MediaFormat.COLOR_STANDARD_BT709);
        fmt.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        fmt.setInteger(MediaFormat.KEY_COLOR_RANGE,MediaFormat.COLOR_RANGE_LIMITED);
        codec=MediaCodec.createByCodecName(p.name);codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface=codec.createInputSurface();codec.start();gl=new Gl.Window(surface,true);
        mux=new MediaMuxer(f.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      }catch(IOException|RuntimeException e){close();throw e;}
    }
    void present(Gl.Draw d,int w,int h,Mirror m,float[] tr,int rot,long pts,Gif.Check check)throws IOException{
      check.run();drain(false,check);d.apply(p.w,p.h,w,h,m,tr,rot,false);gl.frame(pts);drain(false,check);
    }
    void drain(boolean end,Gif.Check check)throws IOException{
      long idle=SystemClock.elapsedRealtime();
      for(;;){
        check.run();int id=codec.dequeueOutputBuffer(info,end?8000:0);
        if(id==MediaCodec.INFO_TRY_AGAIN_LATER){
          if(!end)return;
          if(SystemClock.elapsedRealtime()-idle>WAIT)throw new IOException("The H.264 encoder stopped responding.");
        }else if(id==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
          if(started)throw new IOException("The MP4 track format changed while writing.");
          v=mux.addTrack(codec.getOutputFormat());
          if(audio!=null&&audio.getSampleTrackIndex()>=0)a=mux.addTrack(audio.getTrackFormat(audio.getSampleTrackIndex()));
          mux.start();started=true;
        }else if(id>=0){
          boolean eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
          try{if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
            ByteBuffer b=codec.getOutputBuffer(id);
            if(b==null||!started||info.presentationTimeUs<=last)throw new IOException("Invalid H.264 output frame.");
            b.position(info.offset);b.limit(info.offset+info.size);
            mux.writeSampleData(v,b,info);last=info.presentationTimeUs;
          }}finally{codec.releaseOutputBuffer(id,false);}
          idle=SystemClock.elapsedRealtime();if(eos)return;
        }
      }
    }
    void finish(long duration,long origin,Gif.Check check)throws IOException{
      codec.signalEndOfInputStream();drain(true,check);
      if(last<0)throw new IOException("No MP4 frames were encoded.");
      long end=Math.max(duration,last+1000);
      info.set(0,0,end,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      mux.writeSampleData(v,ByteBuffer.allocateDirect(0),info);
      if(a>=0){
        ByteBuffer b=ByteBuffer.allocateDirect(262144);long latest=-1;
        while(audio.getSampleTrackIndex()>=0){
          check.run();long pts=audio.getSampleTime()-origin;
          if(pts>=end)break;
          if(pts>=0&&pts>latest){
            long n=audio.getSampleSize();if(n>4000000)throw new IOException("An audio packet is too large.");
            if(n>b.capacity())b=ByteBuffer.allocateDirect((int)n);
            b.clear();int len=audio.readSampleData(b,0);if(len<0)break;b.position(0);b.limit(len);
            info.set(0,len,pts,MediaCodec.BUFFER_FLAG_KEY_FRAME);mux.writeSampleData(a,b,info);latest=pts;
          }
          audio.advance();
        }
      }
      mux.stop();stopped=true;
    }
    public void close(){
      if(gl!=null)gl.close();if(surface!=null)surface.release();
      if(codec!=null)try{codec.release();}catch(RuntimeException ignored){}
      if(mux!=null){if(started&&!stopped)try{mux.stop();}catch(RuntimeException ignored){}try{mux.release();}catch(RuntimeException ignored){}}
    }
  }
}
