package com.backgroundgremlin.mirrormax;
import java.io.*;
import java.util.*;
public final class Checks {
  public static void main(String[] args)throws IOException{
    if(!Arrays.equals(Mirror.map(8,1,0),new int[]{0,1,2,3,3,2,1,0}))throw new AssertionError("left");
    if(!Arrays.equals(Mirror.map(8,2,0),new int[]{7,6,5,4,4,5,6,7}))throw new AssertionError("right");
    if(!Arrays.equals(Mirror.map(8,3,3),new int[]{2,6,6,2,2,6,6,2}))throw new AssertionError("both twice");
    for(int n=1;n<=1000;n++)for(int a=0;a<4;a++)for(int b=0;b<4;b++){
      int[] t=Mirror.map(n,a,b);
      for(int i=0;i<n;i++)if(t[i]<0||t[i]>=n||((a!=0||b!=0)&&t[i]!=t[n-1-i]))throw new AssertionError("seam");
    }
    File dir=new File(args[0]),f=new File(dir,"native.gif");
    int w=167,h=89;int[] a=new int[w*h],b=new int[a.length],c=new int[a.length];
    Random random=new Random(82);
    for(int i=0;i<a.length;i++){
      a[i]=i%11==0?0:i%3==0?0xffff0000:0xff0000ff;
      b[i]=i%7==0?0:0xffdb7493;c[i]=random.nextInt(0x1000000)|0xff000000;
    }
    try(Gif.Writer writer=new Gif.Writer(new BufferedOutputStream(new FileOutputStream(f)),w,h)){
      writer.frame(a,10,()->{});writer.frame(b,340,()->{});writer.frame(c,130,()->{});writer.finish();
    }
    Gif.Info meta=Gif.inspect(f,()->{});
    if(meta.w!=w||meta.h!=h||meta.frames!=3||meta.millis!=480)throw new AssertionError("metadata");
    try(Gif.Reader read=new Gif.Reader(f)){
      if(!Arrays.equals(read.next(()->{}).pixels,a)||!Arrays.equals(read.next(()->{}).pixels,b)||read.next(()->{}).ms!=130||read.next(()->{})!=null)throw new AssertionError("round trip");
      read.rewind();if(!Arrays.equals(read.next(()->{}).pixels,a))throw new AssertionError("loop");
    }
    try(Gif.Reader read=new Gif.Reader(new File(dir,"pillow.gif"));DataOutputStream o=new DataOutputStream(new FileOutputStream(new File(dir,"mirror.raw")))){
      o.writeInt(read.w);o.writeInt(read.h);
      for(Gif.Frame p;(p=read.next(()->{}))!=null;){o.writeInt(p.ms);for(int v:p.pixels)o.writeInt(v);}
    }
    System.out.println("Ordered mirror mapping and GIF round-trip pass");
  }
}
