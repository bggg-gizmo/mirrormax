package com.backgroundgremlin.mirrormax;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import java.io.*;

final class Source {
    static final String IMAGE="image",GIF="gif",VIDEO="video";
    final File file;final String name,kind,tag;final int w,h,rotation,frames;final long duration;final boolean audio;
    Source(File file,String name,String kind,String tag,int w,int h,int r,int frames,long duration,boolean audio){
        this.file=file;this.name=name;this.kind=kind;this.tag=tag;this.w=w;this.h=h;this.rotation=r;this.frames=frames;this.duration=duration;this.audio=audio;
    }
    static Source open(File f,String name,Gif.Check check)throws IOException{
        if(f.length()<6)throw new IOException("This file is empty or incomplete.");
        byte[] header=new byte[12];try(InputStream in=new FileInputStream(f)){for(int k=0;k<header.length;){int n=in.read(header,k,header.length-k);if(n<0)break;k+=n;}}
        if(header[0]=='G'&&header[1]=='I'&&header[2]=='F'){
            Gif.Info g=Gif.inspect(f,check);return new Source(f,name,GIF,"GIF",g.w,g.h,0,g.frames,g.millis*1000,false);
        }
        String lower=name.toLowerCase(java.util.Locale.ROOT);
        boolean mov=lower.endsWith(".mov")||(header[8]=='q'&&header[9]=='t'&&header[4]=='f'&&header[5]=='t');
        if(mov||lower.endsWith(".mp4")||header[4]=='f'&&header[5]=='t'&&header[6]=='y'&&header[7]=='p'){
            try{return video(f,name,mov);}catch(IOException e){if(mov||lower.endsWith(".mp4"))throw e;}
        }
        try{
            int[] d=new int[2];Bitmap b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(f),(decoder,info,s)->{
                d[0]=info.getSize().getWidth();d[1]=info.getSize().getHeight();
                decoder.setTargetSize(1,1);decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });b.recycle();
            if(d[0]<1||d[1]<1)throw new IOException("Invalid image dimensions.");
            String ext=lower.lastIndexOf('.')>=0?lower.substring(lower.lastIndexOf('.')+1):"image";
            return new Source(f,name,IMAGE,ext.length()<=5?ext.toUpperCase(java.util.Locale.ROOT):"IMAGE",d[0],d[1],0,1,0,false);
        }catch(IOException|RuntimeException e){
            if(mov||lower.endsWith(".mp4"))throw new IOException("Could not open this "+(mov?"MOV":"MP4")+" on this device.",e);
            try{return video(f,name,false);}catch(IOException ignored){throw new IOException("Choose a readable image, GIF, MP4 or MOV.",e);}
        }
    }
    private static Source video(File f,String name,boolean mov)throws IOException{
        MediaExtractor e=new MediaExtractor();MediaMetadataRetriever r=new MediaMetadataRetriever();
        try{
            e.setDataSource(f.getPath());int v=track(e,"video/");
            if(v<0)throw new IOException("No video track was found.");
            e.selectTrack(v);if(e.getSampleTime()<0)throw new IOException("No video frames were found.");
            MediaFormat m=e.getTrackFormat(v);
            int w=m.getInteger(MediaFormat.KEY_WIDTH),h=m.getInteger(MediaFormat.KEY_HEIGHT);
            String mime=m.getString(MediaFormat.KEY_MIME);
            if(mime==null||new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(MediaFormat.createVideoFormat(mime,w,h))==null)
                throw new IOException("This device cannot decode the source video codec.");
            int vw=value(m,"crop-right",w-1)-value(m,"crop-left",0)+1,vh=value(m,"crop-bottom",h-1)-value(m,"crop-top",0)+1;
            int sarw=value(m,"sar-width",1),sarh=value(m,"sar-height",1);
            if(sarw>0&&sarh>0)vw=(int)Math.max(1,Math.min(16384,Math.round(vw*(double)sarw/sarh)));
            int rot=value(m,MediaFormat.KEY_ROTATION,0);long time=m.containsKey(MediaFormat.KEY_DURATION)?m.getLong(MediaFormat.KEY_DURATION):0;
            try{r.setDataSource(f.getPath());rot=(int)parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),rot);
                if(time<=0)time=parse(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),0)*1000;
            }catch(RuntimeException ignored){}
            if(time<=0||vw<1||vh<1)throw new IOException("This video has invalid dimensions or duration.");
            rot=(rot%360+360)%360;
            return new Source(f,name,VIDEO,mov?"MOV":"MP4",rot%180==0?vw:vh,rot%180==0?vh:vw,rot,0,time,track(e,"audio/")>=0);
        }catch(RuntimeException e1){throw new IOException("This device cannot open the "+(mov?"MOV":"MP4")+" container or codec.",e1);}
        finally{e.release();try{r.release();}catch(IOException|RuntimeException ignored){}}
    }
    static int track(MediaExtractor e,String prefix){
        for(int i=0;i<e.getTrackCount();i++){String m=e.getTrackFormat(i).getString(MediaFormat.KEY_MIME);if(m!=null&&m.startsWith(prefix))return i;}return -1;
    }
    static int value(MediaFormat f,String k,int fallback){try{return f.getInteger(k);}catch(RuntimeException e){return fallback;}}
    private static long parse(String n,long fallback){try{return Long.parseLong(n);}catch(RuntimeException e){return fallback;}}
    int[] size(int edge,long pixels){
        double z=Math.min(1,Math.min((double)edge/Math.max(w,h),Math.sqrt(pixels/((double)w*h))));
        return new int[]{Math.max(1,(int)Math.round(w*z)),Math.max(1,(int)Math.round(h*z))};
    }
    Bitmap bitmap(int edge,long pixels)throws IOException{
        int[] d=size(edge,pixels);
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file),(decoder,i,s)->{
            decoder.setTargetSize(d[0],d[1]);decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
    }
}
