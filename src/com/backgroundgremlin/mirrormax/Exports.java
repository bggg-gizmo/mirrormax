package com.backgroundgremlin.mirrormax;

import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.IntConsumer;

final class Exports {
  static final String PATH=Environment.DIRECTORY_DOWNLOADS+"/MirrorMax/Exports/";
  static final Uri COLLECTION=MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
  static final class Item{
    final Uri uri;final String name,type;
    Item(Uri u,String n,String t){uri=u;name=n;type=t;}
  }
  static String message(Throwable e){String s=e.getMessage();return s==null||s.trim().isEmpty()?"Android could not complete the operation.":s;}
  static Item write(Context c,Source s,Mirror m,String kind,int cap,int duration,boolean audio,Gif.Check check,IntConsumer progress)throws IOException{
    String type;
    if(kind.equals("png")&&s.kind.equals(Source.IMAGE))type="image/png";
    else if(kind.equals("gif")&&s.kind.equals(Source.GIF))type="image/gif";
    else if(kind.equals("mp4"))type="video/mp4";
    else throw new IOException("Choose an export format available for this source.");
    File tmp=File.createTempFile("mirrormax-", "."+kind,c.getCacheDir());Uri uri=null;
    try{
      if(kind.equals("png"))png(s,m,tmp,check,progress);
      else if(kind.equals("gif"))gif(s,m,tmp,check,progress);
      else Encode.write(s,tmp,m,cap,duration,audio,check,progress);
      check.run();if(tmp.length()==0)throw new IOException("No export bytes were generated.");
      String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new Date());
      String name="MirrorMax_"+stamp+"."+kind;
      ContentValues v=new ContentValues();
      v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,type);
      v.put(MediaStore.Downloads.RELATIVE_PATH,PATH);v.put(MediaStore.Downloads.IS_PENDING,1);
      ContentResolver cr=c.getContentResolver();
      uri=cr.insert(COLLECTION,v);if(uri==null)throw new IOException("Android could not create the export.");
      progress.accept(99);
      try(InputStream in=new BufferedInputStream(new FileInputStream(tmp));OutputStream o=cr.openOutputStream(uri,"w")){
        if(o==null)throw new IOException("Android could not open the export for writing.");
        copy(in,o,check);
      }
      check.run();v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);
      if(cr.update(uri,v,null,null)!=1)throw new IOException("Android did not publish the exported file.");
      Item item=new Item(uri,name,type);uri=null;return item;
    }finally{tmp.delete();if(uri!=null)try{c.getContentResolver().delete(uri,null,null);}catch(RuntimeException ignored){}}
  }
  static void copy(InputStream in,OutputStream out,Gif.Check check)throws IOException{
    byte[] buf=new byte[262144];for(int n;(n=in.read(buf))!=-1;){check.run();out.write(buf,0,n);}
  }
  static void png(Source s,Mirror m,File file,Gif.Check check,IntConsumer progress)throws IOException{
    long n=(long)s.w*s.h;
    if(n>Math.max(1,(Runtime.getRuntime().maxMemory()-32L*1024*1024)/10))
      throw new IOException("This image is too large for full-resolution PNG on this device. Try MP4 or a smaller source.");
    Bitmap original=s.bitmap(Math.max(s.w,s.h),n),out=null;
    try{
      int w=original.getWidth(),h=original.getHeight();out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
      int[] a=new int[w],b=new int[w],xs=Mirror.map(w,m.x1,m.x2),ys=Mirror.map(h,m.y1,m.y2);
      for(int y=0;y<h;y++){
        check.run();original.getPixels(a,0,w,0,ys[y],w,1);
        for(int x=0;x<w;x++)b[x]=a[xs[x]];out.setPixels(b,0,w,0,y,w,1);
        if((y&31)==0)progress.accept(y*85/h);
      }
      try(OutputStream o=new BufferedOutputStream(new FileOutputStream(file))){if(!out.compress(Bitmap.CompressFormat.PNG,100,o))throw new IOException("PNG compression failed.");}
    }finally{original.recycle();if(out!=null)out.recycle();}
  }
  static void gif(Source s,Mirror m,File f,Gif.Check check,IntConsumer progress)throws IOException{
    try(Gif.Reader in=new Gif.Reader(s.file);Gif.Writer out=new Gif.Writer(new BufferedOutputStream(new FileOutputStream(f)),s.w,s.h)){
      int[] pixel=new int[s.w*s.h],xs=Mirror.map(s.w,m.x1,m.x2),ys=Mirror.map(s.h,m.y1,m.y2);int n=0;
      for(Gif.Frame frame;(frame=in.next(check))!=null;){
        for(int y=0;y<s.h;y++){
          check.run();int row=ys[y]*s.w,at=y*s.w;
          for(int x=0;x<s.w;x++)pixel[at+x]=frame.pixels[row+xs[x]];
        }
        out.frame(pixel,frame.ms,check);progress.accept((int)(++n*96L/s.frames));
      }
      out.finish();
    }
  }
  static List<Item> list(Context c){
    List<Item> result=new ArrayList<>();
    try(Cursor r=c.getContentResolver().query(COLLECTION,new String[]{MediaStore.Downloads._ID,MediaStore.Downloads.DISPLAY_NAME,MediaStore.Downloads.MIME_TYPE},
      MediaStore.Downloads.RELATIVE_PATH+"=? AND "+MediaStore.Downloads.IS_PENDING+"=0",new String[]{PATH},MediaStore.Downloads._ID+" DESC")){
      if(r!=null)while(r.moveToNext())result.add(new Item(ContentUris.withAppendedId(COLLECTION,r.getLong(0)),r.getString(1),r.getString(2)));
    }
    return result;
  }
}
