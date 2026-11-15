package com.backgroundgremlin.mirrormax;
import android.graphics.Bitmap;
import android.opengl.*;
import android.view.Surface;
import java.nio.*;
import static android.opengl.EGL14.*;
import static android.opengl.GLES20.*;

final class Gl {
  static final class Window implements AutoCloseable{
    static EGLDisplay shared=EGL_NO_DISPLAY;static int refs;
    static synchronized EGLDisplay get(){
      if(refs==0){shared=eglGetDisplay(EGL_DEFAULT_DISPLAY);if(shared==EGL_NO_DISPLAY||!eglInitialize(shared,new int[1],0,new int[1],0))fail("OpenGL display");}
      refs++;return shared;
    }
    static synchronized void put(){if(--refs==0){eglTerminate(shared);shared=EGL_NO_DISPLAY;}}
    EGLDisplay d=EGL_NO_DISPLAY; EGLContext c=EGL_NO_CONTEXT;EGLSurface s=EGL_NO_SURFACE;
    Window(Surface surface,boolean encoder){
      try{
        d=get();eglBindAPI(EGL_OPENGL_ES_API);
        int[] config={EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_WINDOW_BIT,0x3142,1,EGL_NONE};
        if(!encoder)config[12]=EGL_NONE;
        EGLConfig[] a=new EGLConfig[1];int[] n=new int[1];
        if(!eglChooseConfig(d,config,0,a,0,1,n,0)||n[0]==0)fail("OpenGL configuration");
        c=eglCreateContext(d,a[0],EGL_NO_CONTEXT,new int[]{EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE},0);
        s=eglCreateWindowSurface(d,a[0],surface,new int[]{EGL_NONE},0);
        if(c==EGL_NO_CONTEXT||s==EGL_NO_SURFACE||!current())fail("OpenGL window");
      }catch(RuntimeException e){close();throw e;}
    }
    boolean current(){return d!=EGL_NO_DISPLAY&&eglMakeCurrent(d,s,s,c);}
    private final int[] query=new int[2];
    int[] dimensions(){eglQuerySurface(d,s,EGL_WIDTH,query,0);eglQuerySurface(d,s,EGL_HEIGHT,query,1);return query.clone();}
    void frame(long pts){if(pts>=0&&!EGLExt.eglPresentationTimeANDROID(d,s,pts*1000))fail("Video timestamp");if(!eglSwapBuffers(d,s))fail("Frame rendering");}
    static void fail(String s){throw new IllegalStateException(s+" failed (EGL 0x"+Integer.toHexString(eglGetError())+").");}
    public void close(){
      if(d==EGL_NO_DISPLAY)return;
      eglMakeCurrent(d,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
      if(s!=EGL_NO_SURFACE)eglDestroySurface(d,s);
      if(c!=EGL_NO_CONTEXT)eglDestroyContext(d,c);
      eglReleaseThread();put();d=EGL_NO_DISPLAY;
    }
  }
  static final class Draw implements AutoCloseable{
    private static final String VERT="attribute vec2 point;varying vec2 uv;void main(){gl_Position=vec4(point,0.,1.);uv=vec2(point.x+1.,1.-point.y)*.5;}";
    final int program,tex,target,point,src,dst,rule,matrix,rotate,checker;
    final FloatBuffer vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    final float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    int width,height;
    Draw(boolean external){
      String frag=(external?"#extension GL_OES_EGL_image_external : require\n":"")+
        "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"+
        (external?"uniform samplerExternalOES video;":"uniform sampler2D video;")+
        "varying vec2 uv;uniform vec2 src,dst;uniform ivec4 rule;uniform mat4 matrix;uniform int rotate,checker;"+Mirror.GLSL+
        "void main(){vec2 n=2.*dst;vec2 i=2.*floor(uv*dst)+1.;vec2 t=vec2(f(f(i.x,n.x,rule.z),n.x,rule.x),f(f(i.y,n.y,rule.w),n.y,rule.y));"+
        "vec2 p=(clamp(floor(t*src/n+0.00004),vec2(0.),src-1.)+.5)/src;"+
        (external?"if(rotate==90)p=vec2(p.y,1.-p.x);else if(rotate==180)p=1.-p;else if(rotate==270)p=vec2(1.-p.y,p.x);p.y=1.-p.y;":"")+
        "vec4 c=texture2D(video,(matrix*vec4(p,0.,1.)).xy);"+
        (external?"gl_FragColor=vec4(c.rgb,1.);":"float s=mod(floor(gl_FragCoord.x/12.)+floor(gl_FragCoord.y/12.),2.);vec3 b=checker==1?mix(vec3(.077,.077,.105),vec3(.115,.115,.148),s):vec3(0.);gl_FragColor=vec4(c.rgb+b*(1.-c.a),1.);")+"}";
      program=glCreateProgram();
      int a=shader(GL_VERTEX_SHADER,VERT),b=shader(GL_FRAGMENT_SHADER,frag);
      glAttachShader(program,a);glAttachShader(program,b);glLinkProgram(program);glDeleteShader(a);glDeleteShader(b);
      int[] n=new int[1];glGetProgramiv(program,GL_LINK_STATUS,n,0);
      if(n[0]==0)throw new IllegalStateException("Mirror shader link failed. "+glGetProgramInfoLog(program));
      target=external?GLES11Ext.GL_TEXTURE_EXTERNAL_OES:GL_TEXTURE_2D;
      glGenTextures(1,n,0);tex=n[0];bind();
      glTexParameteri(target,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(target,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
      glTexParameteri(target,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(target,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
      point=glGetAttribLocation(program,"point");src=u("src");dst=u("dst");rule=u("rule");matrix=u("matrix");rotate=u("rotate");checker=u("checker");
      glUseProgram(program);glUniform1i(u("video"),0);
      vertices.put(new float[]{-1,-1,1,-1,-1,1,1,1});vertices.position(0);
    }
    private int u(String s){return glGetUniformLocation(program,s);}
    static int shader(int type,String source){
      int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);int[] n=new int[1];glGetShaderiv(s,GL_COMPILE_STATUS,n,0);
      if(n[0]==0){String message=glGetShaderInfoLog(s);glDeleteShader(s);throw new IllegalStateException("Mirror shader compile failed. "+message);}
      return s;
    }
    int limit(){int[] n=new int[1];glGetIntegerv(GL_MAX_TEXTURE_SIZE,n,0);return n[0];}
    private void bind(){glActiveTexture(GL_TEXTURE0);glBindTexture(target,tex);}
    void upload(Bitmap bitmap){
      bind();
      if(width==bitmap.getWidth()&&height==bitmap.getHeight())GLUtils.texSubImage2D(target,0,0,0,bitmap);
      else {GLUtils.texImage2D(target,0,bitmap,0);width=bitmap.getWidth();height=bitmap.getHeight();}
      if(glGetError()!=GL_NO_ERROR)throw new IllegalStateException("Unable to send the image to the GPU.");
    }
    void apply(int w,int h,int sw,int sh,Mirror m,float[] transform,int rotation,boolean check){
      glViewport(0,0,w,h);glUseProgram(program);bind();
      glEnableVertexAttribArray(point);glVertexAttribPointer(point,2,GL_FLOAT,false,0,vertices);
      glUniform2f(src,sw,sh);glUniform2f(dst,w,h);glUniform4i(rule,m.x1,m.y1,m.x2,m.y2);
      glUniformMatrix4fv(matrix,1,false,transform==null?identity:transform,0);glUniform1i(rotate,rotation);glUniform1i(checker,check?1:0);
      glDrawArrays(GL_TRIANGLE_STRIP,0,4);glDisableVertexAttribArray(point);
    }
    public void close(){glDeleteTextures(1,new int[]{tex},0);glDeleteProgram(program);}
  }
}
