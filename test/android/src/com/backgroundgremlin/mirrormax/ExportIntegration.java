package com.backgroundgremlin.mirrormax;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import java.io.*;
import java.util.*;

public final class ExportIntegration extends Instrumentation {
  Context app;
  final StringBuilder log=new StringBuilder();
  @Override public void onCreate(Bundle b){super.onCreate(b);start();}
  @Override public void onStart(){
    Bundle result=new Bundle();
    app=getTargetContext();
    try {
      Source still=source("png"), g=source("gif"), video=source("mp4"), mov=source("mov");
      File sample=File.createTempFile("sample-", ".png", app.getCacheDir());
      try(InputStream in=app.getAssets().open("sample.png");OutputStream o=new FileOutputStream(sample)){Exports.copy(in,o,()->{});}
      Source landscape=Source.open(sample,"Desert Dusk.png",()->{});
      run(still,"png",false);run(landscape,"mp4",false);run(still,"mp4",false);
      run(g,"gif",false);run(g,"mp4",false);
      run(video,"mp4",true);run(video,"mp4",false);run(mov,"mp4",true);
      result.putString("stream","\n"+log+"ANDROID EXPORT INTEGRATION PASSED\n");
      finish(Activity.RESULT_OK,result);
    }catch(Throwable e){
      Log.e("MirrorTest","Android export failure",e);
      StringWriter text=new StringWriter();e.printStackTrace(new PrintWriter(text));result.putString("stream","\n"+log+text);
      finish(Activity.RESULT_CANCELED,result);
    }
  }
  Source source(String ext)throws Exception{
    String name="mirror-fixture."+ext;
    File f=File.createTempFile("mirror-test-", "."+ext,app.getCacheDir());
    try(InputStream in=getContext().getAssets().open(name);OutputStream out=new FileOutputStream(f)){Exports.copy(in,out,()->{});}
    return Source.open(f,name,()->{});
  }
  void run(Source s,String format,boolean audio)throws Exception{
    String caseName=s.name+" > "+format+(audio?" with audio":"");
    Log.i("MirrorTest","START "+caseName);
    Exports.Item saved=Exports.write(app,s,new Mirror(3,3,true,2,1),format,1080,2,audio,()->{},v->{if(v==99)Log.i("MirrorTest","PUBLISH "+caseName);});
    try(Cursor c=app.getContentResolver().query(saved.uri,new String[]{"_display_name","relative_path","is_pending","_size"},null,null,null)){
      if(c==null||!c.moveToFirst()||!Exports.PATH.equals(c.getString(1))||c.getInt(2)!=0||c.getLong(3)<100)throw new AssertionError("Export not committed at "+Exports.PATH);
    }
    if(Exports.list(app).stream().noneMatch(e->e.uri.equals(saved.uri)))throw new AssertionError("Export missing from app's Exports list");
    if(format.equals("mp4")){
      MediaExtractor ex=new MediaExtractor();
      try{
        ex.setDataSource(app,saved.uri,null);
        int idx=Source.track(ex,"video/");
        if(idx<0)throw new AssertionError("No video track.");
        MediaFormat m=ex.getTrackFormat(idx);
        if(!"video/avc".equals(m.getString(MediaFormat.KEY_MIME)))throw new AssertionError("Unexpected codec "+m);
        if(audio!=(Source.track(ex,"audio/")>=0))throw new AssertionError("Audio did not match request.");
        ex.selectTrack(idx);
        if(ex.getSampleTime()<0)throw new AssertionError("Video track has no samples.");
        int n=0;long last=-1;
        do{long ts=ex.getSampleTime();if(ts<=last)throw new AssertionError("Frames out of order");last=ts;n++;}while(ex.advance());
        if(n<2)throw new AssertionError("Too few frames "+n);
        log.append("MP4 ").append(m.getInteger(MediaFormat.KEY_WIDTH)).append('x').append(m.getInteger(MediaFormat.KEY_HEIGHT)).append(" frames=").append(n).append(" audio=").append(audio).append(' ');
      }finally{ex.release();}
    }
    log.append(caseName).append(" at ").append(Exports.PATH).append(saved.name).append('\n');
    Log.i("MirrorTest","PASS "+caseName+" -> "+saved.name);
  }
}
