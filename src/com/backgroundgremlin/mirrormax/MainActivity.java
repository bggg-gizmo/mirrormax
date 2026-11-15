package com.backgroundgremlin.mirrormax;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
  static final String HOST="studio.mirrormax.local";
  static final int PICK=28;
  final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(r,"MirrorMax files"));
  final AtomicBoolean cancel=new AtomicBoolean();
  final Set<String> exports=new HashSet<>();
  FrameLayout root;WebView web;TextureView texture;Preview preview;Source media;
  boolean alive=true,page,active,playing=true,muted,original,busy,exporting,second;
  int x1,y1,x2,y2;Uri pending;
  String savedSource,savedName;
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    if(saved!=null){
      x1=saved.getInt("x1");y1=saved.getInt("y1");x2=saved.getInt("x2");y2=saved.getInt("y2");
      playing=saved.getBoolean("play",true);muted=saved.getBoolean("mute");second=saved.getBoolean("second");
      savedSource=saved.getString("file");savedName=saved.getString("name");
    }else pending=incoming(getIntent());
    root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(16,17,25));
    web=new WebView(this);web.setBackgroundColor(Color.rgb(16,17,25));
    web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowContentAccess(false);settings.setAllowFileAccess(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    settings.setTextZoom(Math.max(100,Math.min(155,Math.round(getResources().getConfiguration().fontScale*100))));
    web.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
      @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){
        Uri u=r.getUrl();
        if(!"https".equals(u.getScheme())||!HOST.equals(u.getAuthority())||!"GET".equals(r.getMethod()))return blocked();
        String p=u.getPath(),type=null;
        if("/studio.html".equals(p))type="text/html";
        if("/studio.css".equals(p))type="text/css";
        if("/studio.js".equals(p))type="application/javascript";
        if("/ornament.svg".equals(p))type="image/svg+xml";
        if(type==null)return blocked();
        try{return new WebResourceResponse(type,"UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),getAssets().open(p.substring(1)));}
        catch(IOException e){return blocked();}
      }
      @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){
        root.removeView(v);v.destroy();web=null;alive=false;
        Toast.makeText(MainActivity.this,"The Android editor stopped. Reopen MirrorMax to continue.",Toast.LENGTH_LONG).show();finish();return true;
      }
    });
    root.addView(web,new FrameLayout.LayoutParams(-1,-1));
    preview=new Preview(s->main(()->js("notice",s)));
    texture=new TextureView(this);texture.setOpaque(true);texture.setAlpha(0);texture.setClickable(false);
    texture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);texture.setSurfaceTextureListener(this);
    root.addView(texture,new FrameLayout.LayoutParams(1,1));web.addJavascriptInterface(new Bridge(),"MirrorAndroid");
    setContentView(root);web.loadUrl("https://"+HOST+"/studio.html");
    final String keep=savedSource;
    io.execute(()->{File[] a=new File(getCacheDir(),"media").listFiles();if(a!=null)for(File f:a)if(!f.getName().equals(keep)&&System.currentTimeMillis()-f.lastModified()>86400000L)f.delete();});
  }
  static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
  static Uri incoming(Intent i){
    if(i==null||!Intent.ACTION_SEND.equals(i.getAction()))return null;
    try{
      Uri u=Build.VERSION.SDK_INT>=33?i.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class):i.getParcelableExtra(Intent.EXTRA_STREAM);
      return u!=null&&"content".equals(u.getScheme())?u:null;
    }catch(RuntimeException e){return null;}
  }
  @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);Uri u=incoming(i);if(u!=null){if(page)load(u,false,false);else pending=u;}}
  @Override protected void onResume(){super.onResume();active=true;play();}
  @Override protected void onPause(){active=false;play();super.onPause();}
  @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(web!=null)web.getSettings().setTextZoom(Math.max(100,Math.min(155,Math.round(c.fontScale*100))));js("layout");}
  @Override public void onBackPressed(){
    if(!page||web==null){super.onBackPressed();return;}
    web.evaluateJavascript("window.Studio && window.Studio.back()",s->{if(!"true".equals(s))super.onBackPressed();});
  }
  @Override protected void onSaveInstanceState(Bundle b){
    super.onSaveInstanceState(b);
    b.putInt("x1",x1);b.putInt("x2",x2);b.putInt("y1",y1);b.putInt("y2",y2);b.putBoolean("second",second);b.putBoolean("play",playing);b.putBoolean("mute",muted);
    if(media!=null){b.putString("file",media.file.getName());b.putString("name",media.name);}
  }
  @Override protected void onDestroy(){
    alive=false;cancel.set(true);io.shutdownNow();
    if(texture!=null&&texture.isAvailable())texture.setSurfaceTextureListener(null);
    if(preview!=null){preview.close();preview=null;}
    if(web!=null){web.removeJavascriptInterface("MirrorAndroid");root.removeView(web);web.destroy();web=null;}
    super.onDestroy();
  }
  void main(Runnable r){runOnUiThread(()->{if(alive)r.run();});}
  void play(){if(preview!=null)preview.play(playing,muted,active);}
  void js(String name,Object... args){
    if(!page||web==null||!alive)return;
    JSONArray a=new JSONArray();for(Object x:args)a.put(x==null?JSONObject.NULL:x);
    web.evaluateJavascript("window.Studio && window.Studio."+name+".apply(null,"+a+")",null);
  }
  static JSONObject json(Object... items){
    JSONObject x=new JSONObject();try{for(int j=0;j<items.length;j+=2)x.put((String)items[j],items[j+1]);}
    catch(JSONException e){throw new IllegalArgumentException(e);}return x;
  }
  Mirror rule(){return new Mirror(x1,y1,second,x2,y2);}
  void pick(){
    if(busy)return;
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
    i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*","video/quicktime","application/octet-stream"});
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try{startActivityForResult(i,PICK);}catch(ActivityNotFoundException e){js("notice","Android has no file picker.");}
  }
  @Override protected void onActivityResult(int request,int code,Intent i){super.onActivityResult(request,code,i);if(request==PICK&&code==RESULT_OK&&i!=null&&i.getData()!=null)load(i.getData(),false,false);}
  void check()throws IOException{if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Operation canceled.");}
  String filename(Uri u){
    try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getString(0);
    }catch(RuntimeException ignored){}return "Imported media";
  }
  File folder()throws IOException{
    File dir=new File(getCacheDir(),"media");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Could not open the local media folder.");
    return dir;
  }
  void load(Uri uri,boolean demo,boolean restore){
    if(busy){js("notice","Finish the current operation before opening another file.");return;}
    if(!demo&&!restore&&(uri==null||!"content".equals(uri.getScheme())))return;
    final String cached=savedSource, title=savedName;
    busy=true;cancel.set(false);js("loading",true);
    io.execute(()->{
      File src=null;
      try{
        String n;
        if(restore){
          if(cached==null||!cached.matches("source-[0-9]+[.]media"))throw new IOException("The previous source isn't available.");
          src=new File(folder(),cached);n=title;
        }else{
          src=File.createTempFile("source-", ".media",folder());
          n=demo?"Desert Dusk.png":filename(uri);
          try(InputStream in=demo?getAssets().open("sample.png"):getContentResolver().openInputStream(uri);
              OutputStream o=new BufferedOutputStream(new FileOutputStream(src))){
            if(in==null)throw new IOException("Could not open the selected media.");Exports.copy(in,o,this::check);
          }
        }
        Source s=Source.open(src,n,this::check);
        runOnUiThread(()->{
          if(!alive){if(!restore)s.file.delete();return;}
          busy=false;js("loading",false);
          Source old=media;media=s;original=false;if(!restore)playing=true;
          preview.source(s);preview.update(rule());play();
          if(old!=null&&!old.file.equals(s.file))old.file.delete();
          js("source",json("name",s.name,"kind",s.kind,"tag",s.tag,"w",s.w,"h",s.h,"audio",s.audio,
            "moving",s.kind.equals(Source.VIDEO)||s.kind.equals(Source.GIF)&&s.frames>1,"ms",s.duration/1000,
            "play",playing,"mute",muted,"x1",x1,"y1",y1,"x2",x2,"y2",y2,"second",second));
        });
      }catch(IOException|RuntimeException|OutOfMemoryError e){if(src!=null&&!restore)src.delete();failed(e);}
    });
  }
  void failed(Throwable e){
    String s=cancel.get()?"Operation canceled.":e instanceof OutOfMemoryError?"This media needs more memory than the device can provide.":Exports.message(e);
    android.util.Log.w("MirrorMax",s,e);
    main(()->{boolean wasExport=exporting;busy=false;exporting=false;
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      js("failed",s,wasExport);
    });
  }
  JSONObject item(Exports.Item e){return json("name",e.name,"uri",e.uri.toString(),"type",e.type);}
  void export(String fmt,int edge,int seconds,boolean audio){
    if(busy||media==null||!Arrays.asList("png","gif","mp4").contains(fmt))return;
    final int cap=edge==720||edge==2160?edge:1080,len=Math.max(2,Math.min(10,seconds));
    final Source s=media;final Mirror m=rule();
    busy=true;exporting=true;cancel.set(false);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    io.execute(()->{
      long[] last={0};int[] seen={-1};
      try{
        Exports.Item e=Exports.write(this,s,m,fmt,cap,len,audio,this::check,n->{
          long now=SystemClock.elapsedRealtime();if(n<=seen[0]||(n<99&&now-last[0]<90))return;
          seen[0]=n;last[0]=now;main(()->{if(exporting&&!cancel.get())js("progress",n);});
        });
        main(()->{busy=false;exporting=false;exports.add(e.uri.toString());
          getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);js("saved",item(e));
        });
      }catch(IOException|RuntimeException|OutOfMemoryError e){failed(e);}
    });
  }
  void list(){
    if(exporting)return;
    io.execute(()->{try{List<Exports.Item> r=Exports.list(this);main(()->{
      JSONArray a=new JSONArray();for(Exports.Item e:r){exports.add(e.uri.toString());a.put(item(e));}js("files",a);
    });}catch(RuntimeException e){main(()->js("notice","Android could not list the exports folder."));}});
  }
  void fileAction(String id,boolean share){
    if(!exports.contains(id))return;Uri u=Uri.parse(id);
    try{
      String type=getContentResolver().getType(u);
      if(type==null){js("notice","This file was moved or deleted.");return;}
      Intent i=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW);
      if(share){i.setType(type);i.putExtra(Intent.EXTRA_STREAM,u);}else i.setDataAndType(u,type);
      i.setClipData(ClipData.newRawUri("MirrorMax export",u));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(i,share?"Share reflection":"Open reflection"));
    }catch(RuntimeException e){js("notice","Android could not open this export.");}
  }
  void viewport(float x,float y,float w,float h,float fullW,float fullH){
    if(web==null||!Float.isFinite(x+y+w+h+fullW+fullH)||fullW<=0||fullH<=0)return;
    if(w<1||h<1){texture.setAlpha(0);return;}
    float sx=web.getWidth()/fullW,sy=web.getHeight()/fullH;
    int l=Math.round(x*sx),t=Math.round(y*sy),r=Math.round((x+w)*sx),b=Math.round((y+h)*sy);
    if(l<0||t<0||r>root.getWidth()+1||b>root.getHeight()+1||r<=l||b<=t)return;
    FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)texture.getLayoutParams();
    if(p.leftMargin!=l||p.topMargin!=t||p.width!=r-l||p.height!=b-t){
      p.leftMargin=l;p.topMargin=t;p.width=r-l;p.height=b-t;texture.setLayoutParams(p);
    }
    texture.setAlpha(1);
  }
  @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){if(preview!=null)preview.target(t,w,h);}
  @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){if(preview!=null)preview.target(t,w,h);}
  @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){if(preview!=null){preview.detach(t);return false;}return true;}
  @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
  public final class Bridge{
    @JavascriptInterface public void ready(){main(()->{
      if(page)return;page=true;if(pending!=null){Uri u=pending;pending=null;load(u,false,false);}
      else if(savedSource!=null&&savedName!=null)load(null,false,true);
    });}
    @JavascriptInterface public void pick(){main(MainActivity.this::pick);}
    @JavascriptInterface public void sample(){main(()->load(null,true,false));}
    @JavascriptInterface public void viewport(float x,float y,float w,float h,float cw,float ch){main(()->MainActivity.this.viewport(x,y,w,h,cw,ch));}
    @JavascriptInterface public void mirror(int a,int b,boolean use,int c,int d,boolean compare){main(()->{
      x1=Mirror.mode(a);y1=Mirror.mode(b);x2=Mirror.mode(c);y2=Mirror.mode(d);second=use;original=compare;preview.update(original?Mirror.NONE:rule());
    });}
    @JavascriptInterface public void playback(boolean p,boolean m){main(()->{playing=p;muted=m;play();});}
    @JavascriptInterface public void exportMedia(String fmt,int edge,int len,boolean sound){main(()->export(fmt,edge,len,sound));}
    @JavascriptInterface public void files(){main(MainActivity.this::list);}
    @JavascriptInterface public void fileAction(String id,boolean share){main(()->MainActivity.this.fileAction(id,share));}
    @JavascriptInterface public void cancel(){main(()->{if(exporting)MainActivity.this.cancel.set(true);});}
  }
}
