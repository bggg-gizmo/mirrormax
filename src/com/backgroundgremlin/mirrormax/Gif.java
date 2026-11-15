package com.backgroundgremlin.mirrormax;

import java.io.*;
import java.util.*;

/** GIF89a compositing and stream encoding, with one frame held at a time. */
final class Gif {
    interface Check {void run() throws IOException;}
    static final class Info{final int w,h,frames;final long millis;Info(int w,int h,int f,long ms){this.w=w;this.h=h;frames=f;millis=ms;}}
    static final class Frame{final int[] pixels;final int ms;Frame(int[] p,int t){pixels=p;ms=t;}}
    static final class Reader implements Closeable{
        final RandomAccessFile in;final int w,h,bg;final int[] canvas,global;final long start;
        int disposal,l,t,r,b,clear;int[] backup;boolean first=true;
        Reader(File f)throws IOException{
            in=new RandomAccessFile(f,"r");
            try{
                byte[] ident=new byte[6];in.readFully(ident);
                String h0=new String(ident,java.nio.charset.StandardCharsets.US_ASCII);
                if(!h0.equals("GIF89a")&&!h0.equals("GIF87a"))throw new IOException("Invalid GIF header.");
                w=read16();h=read16();valid(w,h);
                int f0=in.readUnsignedByte();bg=in.readUnsignedByte();in.readUnsignedByte();
                global=(f0&128)==0?null:palette(2<<(f0&7));
                start=in.getFilePointer();canvas=new int[w*h];
            }catch(IOException|RuntimeException|OutOfMemoryError e){in.close();throw e;}
        }
        int read16()throws IOException{return in.readUnsignedByte()+(in.readUnsignedByte()<<8);}
        int[] palette(int n)throws IOException{int[] a=new int[n];for(int i=0;i<n;i++)a[i]=0xff000000|(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();return a;}
        void skip(int n)throws IOException{long p=in.getFilePointer()+n;if(p>in.length())throw new EOFException("Incomplete GIF.");in.seek(p);}
        void skipBlocks()throws IOException{for(int k;(k=in.readUnsignedByte())!=0;)skip(k);}
        void rewind()throws IOException{in.seek(start);first=true;backup=null;disposal=0;Arrays.fill(canvas,0);}
        Frame next(Check check)throws IOException{return next(check,true);}
        Frame next(Check check,boolean render)throws IOException{
            int delay=0,method=0,trans=-1;
            for(;;){
                check.run();
                int block=in.readUnsignedByte();
                if(block==0x3b)return null;
                if(block==0)continue;
                if(block==0x21){
                    int label=in.readUnsignedByte();
                    if(label==0xf9){
                        if(in.readUnsignedByte()!=4)throw new IOException("Invalid GIF control block.");
                        int f=in.readUnsignedByte();method=(f>>2)&7;delay=read16()*10;
                        int c=in.readUnsignedByte();trans=(f&1)==0?-1:c;
                        if(in.readUnsignedByte()!=0)throw new IOException("Invalid GIF control block ending.");
                    }else {skipBlocks();if(label==1){delay=0;method=0;trans=-1;}}
                    continue;
                }
                if(block!=0x2c)throw new IOException("Invalid GIF frame header.");
                if(render){
                    if(disposal==2&&l<r)for(int y=t;y<b;y++)Arrays.fill(canvas,y*w+l,y*w+r,clear);
                    if(disposal==3&&backup!=null)System.arraycopy(backup,0,canvas,0,canvas.length);
                }
                int x0=read16(),y0=read16(),fw=read16(),fh=read16();valid(fw,fh);
                int f=in.readUnsignedByte();int colors=2<<(f&7);
                if(!render){if((f&128)!=0)skip(colors*3);in.readUnsignedByte();skipBlocks();return new Frame(null,delay==0?100:delay);}
                int[] pal=(f&128)==0?global:palette(colors);
                if(pal==null)throw new IOException("Missing GIF palette.");
                clear=trans<0&&global!=null&&bg<global.length?global[bg]:0;
                if(first){if(clear!=0)Arrays.fill(canvas,clear);first=false;}
                disposal=method;backup=method==3?canvas.clone():null;l=x0;t=y0;r=Math.min(w,x0+fw);b=Math.min(h,y0+fh);
                byte[] data=decode(fw*fh,check);
                int off=0;
                if((f&64)==0){for(int y=0;y<fh;y++,off+=fw)paint(data,off,y+y0,pal,trans);}
                else {int[] u={0,4,2,1},s={8,8,4,2};for(int j=0;j<4;j++)for(int y=u[j];y<fh;y+=s[j],off+=fw)paint(data,off,y+y0,pal,trans);}
                return new Frame(canvas,delay==0?100:delay);
            }
        }
        void paint(byte[] data,int off,int y,int[] pal,int trans){
            if(y>=h||l>=r)return;int d=y*w+l;
            for(int x=0;x<r-l;x++){int v=data[off+x]&255;if(v!=trans&&v<pal.length)canvas[d+x]=pal[v];}
        }
        byte[] decode(int count,Check check)throws IOException{
            int minimum=in.readUnsignedByte();if(minimum<2||minimum>8)throw new IOException("Invalid GIF dictionary.");
            int reset=1<<minimum,end=reset+1,next=end+1,bits=minimum+1,old=-1,firstCode=0;
            short[] parents=new short[4096];byte[] values=new byte[4096],stack=new byte[4097],pixels=new byte[count],buf=new byte[255];
            int k=0,y=0,n=0,word=0,filled=0;boolean blocksEnded=false;
            while(k<count){
                while(filled<bits){
                    if(y==n){check.run();n=in.readUnsignedByte();y=0;if(n==0){blocksEnded=true;break;}in.readFully(buf,0,n);}
                    word|=(buf[y++]&255)<<filled;filled+=8;
                }
                if(blocksEnded)break;
                int code=word&((1<<bits)-1);word>>>=bits;filled-=bits;
                if(code==reset){next=end+1;bits=minimum+1;old=-1;continue;}
                if(code==end)break;
                if(old<0){if(code>=reset)throw new IOException("Invalid first GIF code.");pixels[k++]=(byte)code;firstCode=code;old=code;continue;}
                int original=code,p=0;
                if(code==next){stack[p++]=(byte)firstCode;code=old;}
                else if(code>next)throw new IOException("Invalid GIF code.");
                while(code>=end+1){if(p>=4096||code>=next)throw new IOException("Invalid GIF chain.");stack[p++]=values[code];code=parents[code]&65535;}
                if(code>=reset)throw new IOException("Invalid GIF color.");
                firstCode=code;stack[p++]=(byte)code;
                if(next<4096){parents[next]=(short)old;values[next++]=(byte)code;if(next==(1<<bits)&&bits<12)bits++;}
                old=original;
                while(p>0&&k<count)pixels[k++]=stack[--p];
            }
            if(!blocksEnded)skipBlocks();
            if(k!=count)throw new IOException("Incomplete GIF frame.");
            return pixels;
        }
        @Override public void close()throws IOException{in.close();}
    }
    static void valid(int w,int h)throws IOException{if(w<=0||h<=0||(long)w*h>16_000_000)throw new IOException("GIF canvas must be at most 16 megapixels.");}
    static Info inspect(File f,Check check)throws IOException{
        try(Reader r=new Reader(f)){long time=0;int n=0;
            for(Frame s;(s=r.next(check,false))!=null;){time+=s.ms;n++;}
            if(n==0)throw new IOException("This GIF has no frames.");
            r.rewind();r.next(check);
            return new Info(r.w,r.h,n,time);
        }
    }
    static final class Writer implements Closeable{
        final OutputStream out;final int w,h;boolean ended;
        Writer(OutputStream o,int w,int h)throws IOException{
            out=o;this.w=w;this.h=h;valid(w,h);
            out.write(new byte[]{'G','I','F','8','9','a'});u16(w);u16(h);
            out.write(new byte[]{(byte)0xf0,0,0,0,0,0,0,0,0});
            out.write(new byte[]{0x21,(byte)0xff,11,'N','E','T','S','C','A','P','E','2','.','0',3,1,0,0,0});
        }
        void u16(int n)throws IOException{out.write(n);out.write(n>>8);}
        void frame(int[] pixels,int duration,Check check)throws IOException{
            Palette pal=new Palette(pixels,check);
            out.write(new byte[]{0x21,(byte)0xf9,4,9});u16(Math.max(1,Math.min(65535,(duration+5)/10)));
            out.write(new byte[]{0,0,0x2c,0,0,0,0});u16(w);u16(h);out.write(0x87);
            for(int v:pal.colors){out.write(v>>16);out.write(v>>8);out.write(v);}
            out.write(8);
            Bits stream=new Bits(out);
            int[] keys=new int[8192],vals=new int[8192],age=new int[8192];
            int run=pal.index(pixels[0]),next=258,width=9,epoch=1;
            stream.write(256,width);
            for(int i=1;i<pixels.length;i++){
                if((i&65535)==0)check.run();
                int c=pal.index(pixels[i]),key=(run<<8)|c,j=(key*0x9e3779b9)>>>19;
                while(age[j]==epoch&&keys[j]!=key)j=(j+1)&8191;
                if(age[j]==epoch){run=vals[j];continue;}
                stream.write(run,width);
                if(next==4096){stream.write(256,width);next=258;width=9;epoch++;}
                else {if(next==(1<<width))width++;age[j]=epoch;keys[j]=key;vals[j]=next++;}
                run=c;
            }
            stream.write(run,width);if(next==(1<<width)&&width<12)width++;
            stream.write(257,width);stream.finish();
        }
        void finish()throws IOException{if(!ended){out.write(0x3b);out.flush();ended=true;}}
        public void close()throws IOException{out.close();}
    }
    static final class Bits{
        final OutputStream o;final byte[] buf=new byte[255];int n,bits,word;
        Bits(OutputStream o){this.o=o;}
        void write(int v,int size)throws IOException{word|=v<<bits;bits+=size;while(bits>=8){put(word);word>>>=8;bits-=8;}}
        void put(int v)throws IOException{buf[n++]=(byte)v;if(n==255)flush();}
        void flush()throws IOException{if(n>0){o.write(n);o.write(buf,0,n);n=0;}}
        void finish()throws IOException{if(bits>0)put(word);flush();o.write(0);}
    }
    static int bucket(int c){return ((c>>>9)&0x7c00)|((c>>>6)&0x3e0)|((c>>>3)&31);}
    static final class Palette{
        final int[] colors=new int[256],map=new int[32768];Map<Integer,Integer> exact=new HashMap<>();
        Palette(int[] pixels,Check check)throws IOException{
            int[] count=new int[32768],r=new int[32768],g=new int[32768],b=new int[32768];
            for(int i=0;i<pixels.length;i++){
                if((i&65535)==0)check.run();
                int c=pixels[i];if((c>>>24)<128)continue;c|=0xff000000;
                int q=bucket(c);count[q]++;r[q]+=(c>>>16)&255;g[q]+=(c>>>8)&255;b[q]+=c&255;
                if(exact.size()<256&&!exact.containsKey(c)){int s=exact.size()+1;exact.put(c,s);if(s<256)colors[s]=c;}
            }
            if(exact.size()<=255)return;
            exact=null;
            Integer[] bins=new Integer[32768];int n=0;for(int q=0;q<32768;q++)if(count[q]>0)bins[n++]=q;
            PriorityQueue<Box> heap=new PriorityQueue<>((x,y)->Integer.compare(y.priority,x.priority));heap.add(new Box(bins,count,0,n));
            while(heap.size()<255&&heap.peek().priority>0){
                Box a=heap.remove();
                Arrays.sort(bins,a.lo,a.hi,Comparator.comparingInt(q->(q>>a.axis)&31));
                int j=a.lo,t=0;do{t+=count[bins[j++]];}while(j<a.hi-1&&t<a.weight/2);
                heap.add(new Box(bins,count,a.lo,j));heap.add(new Box(bins,count,j,a.hi));
            }
            int s=1;
            for(Box a:heap){
                long R=0,G=0,B=0;
                for(int i=a.lo;i<a.hi;i++){int q=bins[i];R+=r[q];G+=g[q];B+=b[q];map[q]=s;}
                colors[s++]=(int)(R/a.weight)<<16|(int)(G/a.weight)<<8|(int)(B/a.weight);
            }
        }
        int index(int c){return (c>>>24)<128?0:exact==null?map[bucket(c)]:exact.get(c|0xff000000);}
    }
    static final class Box{
        final int lo,hi,axis,weight,priority;
        Box(Integer[] a,int[] counts,int l,int h){
            lo=l;hi=h;int rl=31,gl=31,bl=31,rh=0,gh=0,bh=0,sum=0;
            for(int j=l;j<h;j++){int k=a[j],r=k>>10,g=(k>>5)&31,b=k&31;rl=Math.min(rl,r);rh=Math.max(rh,r);gl=Math.min(gl,g);gh=Math.max(gh,g);bl=Math.min(bl,b);bh=Math.max(bh,b);sum+=counts[k];}
            int r=rh-rl,g=gh-gl,b=bh-bl;axis=g>=r&&g>=b?5:r>=b?10:0;weight=sum;priority=h-l<2?0:Math.max(r,Math.max(g,b))*(int)Math.sqrt(sum);
        }
    }
}
