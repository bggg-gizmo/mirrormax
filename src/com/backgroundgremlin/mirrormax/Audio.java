package com.backgroundgremlin.mirrormax;
import android.media.*;
import android.os.SystemClock;
import java.io.*;
import java.nio.*;

/** Decode source sound to PCM and encode AAC for the MP4 container. */
final class Audio {
  static void convert(File source,File dest,Gif.Check check)throws IOException {
    MediaCodec d=null,e=null;MediaMuxer mux=null;MediaExtractor x=new MediaExtractor();
    boolean muxed=false,stopped=false;
    try{
      x.setDataSource(source.getPath());int track=Source.track(x,"audio/");
      if(track<0)throw new IOException("The audio track is unavailable.");
      x.selectTrack(track);
      MediaFormat f=x.getTrackFormat(track);d=MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME));
      f.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
      d.configure(f,null,null,0);d.start();
      mux=new MediaMuxer(dest.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
      boolean inEnd=false,decEnd=false,encEnd=false,fin=false;
      int held=-1,rate=0,channels=0,outCh=0,encoding=2,bytes=2,outTrack=-1;
      long base=0,used=0,next=0,last=-1,idle=SystemClock.elapsedRealtime();
      ByteBuffer pcm=null;MediaCodec.BufferInfo di=new MediaCodec.BufferInfo(),ei=new MediaCodec.BufferInfo();
      while(!fin) {
        check.run();boolean changed=false;
        if(!inEnd){
          int id=d.dequeueInputBuffer(0);
          if(id>=0){
            ByteBuffer b=d.getInputBuffer(id);if(b==null)throw new IOException("No audio decoder buffer.");
            b.clear();int n=x.readSampleData(b,0);
            if(n<0){d.queueInputBuffer(id,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inEnd=true;}
            else {d.queueInputBuffer(id,0,n,x.getSampleTime(),0);x.advance();}
            changed=true;
          }
        }
        if(held<0&&!decEnd){
          int id=d.dequeueOutputBuffer(di,2500);
          if(id==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            MediaFormat df=d.getOutputFormat();
            int r=df.getInteger(MediaFormat.KEY_SAMPLE_RATE),n=df.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if(e!=null&&(r!=rate||n!=channels))throw new IOException("The audio layout changed during export.");
            rate=r;channels=n;outCh=Math.min(2,n);
            encoding=Source.value(df,MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT||encoding==AudioFormat.ENCODING_PCM_32BIT?4:encoding==AudioFormat.ENCODING_PCM_24BIT_PACKED?3:encoding==AudioFormat.ENCODING_PCM_8BIT?1:2;
            if(e==null){
              if(rate<1||channels<1||channels>32)throw new IOException("Unsupported audio layout.");
              MediaFormat ef=MediaFormat.createAudioFormat(Encode.AAC,rate,outCh);
              ef.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);ef.setInteger(MediaFormat.KEY_BIT_RATE,outCh==1?88000:160000);
              e=MediaCodec.createEncoderByType(Encode.AAC);e.configure(ef,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);e.start();
            }
            changed=true;
          }else if(id>=0){
            decEnd=(di.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            if(di.size>0&&(di.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
              if(e==null)throw new IOException("Unknown decoded audio format.");
              pcm=d.getOutputBuffer(id);
              if(pcm==null)throw new IOException("No decoded audio data.");
              pcm.order(ByteOrder.LITTLE_ENDIAN);pcm.position(di.offset);pcm.limit(di.offset+di.size);
              held=id;base=Math.max(next,di.presentationTimeUs);used=0;
            }else d.releaseOutputBuffer(id,false);
            changed=true;
          }
        }
        if(e!=null){
          if(!encEnd&&(held>=0||decEnd)){
            int id=e.dequeueInputBuffer(0);
            if(id>=0){
              ByteBuffer b=e.getInputBuffer(id);if(b==null)throw new IOException("No AAC buffer.");
              b.clear();b.order(ByteOrder.LITTLE_ENDIAN);
              if(held>=0){
                int n=Math.min(pcm.remaining()/(channels*bytes),b.remaining()/(outCh*2));
                if(n<1)throw new IOException("Incomplete PCM frame.");
                for(int j=0;j<n;j++){
                  int left=sample(pcm,encoding);
                  if(channels==1){b.putShort((short)left);continue;}
                  int right=sample(pcm,encoding),extra=0;
                  for(int k=2;k<channels;k++)extra+=sample(pcm,encoding);
                  if(channels>2){extra/=channels-2;left=(left*3+extra)/4;right=(right*3+extra)/4;}
                  b.putShort((short)left);b.putShort((short)right);
                }
                e.queueInputBuffer(id,0,n*outCh*2,base+used*1000000/rate,0);
                used+=n;next=base+used*1000000/rate;
                if(pcm.remaining()<channels*bytes){d.releaseOutputBuffer(held,false);held=-1;pcm=null;}
              }else {e.queueInputBuffer(id,0,0,next,MediaCodec.BUFFER_FLAG_END_OF_STREAM);encEnd=true;}
              changed=true;
            }
          }
          int id=e.dequeueOutputBuffer(ei,0);
          if(id==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            if(muxed)throw new IOException("The audio format changed.");
            outTrack=mux.addTrack(e.getOutputFormat());mux.start();muxed=true;changed=true;
          }else if(id>=0){
            fin=(ei.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
            try{if(ei.size>0&&(ei.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
              ByteBuffer b=e.getOutputBuffer(id);if(b==null||!muxed)throw new IOException("No encoded audio data.");
              b.position(ei.offset);b.limit(ei.offset+ei.size);
              ei.presentationTimeUs=Math.max(last+1,ei.presentationTimeUs);last=ei.presentationTimeUs;
              mux.writeSampleData(outTrack,b,ei);
            }}finally{e.releaseOutputBuffer(id,false);}
            changed=true;
          }
        }else if(decEnd)throw new IOException("No audio samples.");
        if(changed)idle=SystemClock.elapsedRealtime();
        else if(SystemClock.elapsedRealtime()-idle>Encode.WAIT)throw new IOException("The audio converter stopped responding.");
        else SystemClock.sleep(2);
      }
      if(last<0)throw new IOException("No AAC samples were produced.");
      mux.stop();stopped=true;
    }finally{
      x.release();if(d!=null)try{d.release();}catch(RuntimeException ignored){}if(e!=null)try{e.release();}catch(RuntimeException ignored){}
      if(mux!=null){if(muxed&&!stopped)try{mux.stop();}catch(RuntimeException ignored){}try{mux.release();}catch(RuntimeException ignored){}}
    }
  }
  static int sample(ByteBuffer b,int n){
    if(n==AudioFormat.ENCODING_PCM_FLOAT)return (int)(Math.max(-1f,Math.min(b.getFloat(),1f))*32767f);
    if(n==AudioFormat.ENCODING_PCM_32BIT)return b.getInt()>>16;
    if(n==AudioFormat.ENCODING_PCM_24BIT_PACKED){b.get();return (b.get()&255)|(b.get()<<8);}
    if(n==AudioFormat.ENCODING_PCM_8BIT)return ((b.get()&255)-128)*256;
    return b.getShort();
  }
}
