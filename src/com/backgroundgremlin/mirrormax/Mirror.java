package com.backgroundgremlin.mirrormax;

final class Mirror {
    static final Mirror NONE=new Mirror(0,0,false,0,0);
    final int x1,y1,x2,y2;
    Mirror(int x,int y,boolean second,int a,int b){x1=mode(x);y1=mode(y);x2=second?mode(a):0;y2=second?mode(b):0;}
    static int mode(int m){return m>=0&&m<=3?m:0;}
    private static long fold(long p,long n,int m){
        return m==1?Math.min(p,n-p):m==2?Math.max(p,n-p):m==3?2*Math.min(p,n-p):p;
    }
    static int[] map(int n,int a,int b){
        int[] t=new int[n];long d=2L*n;
        for(int i=0;i<n;i++)t[i]=(int)Math.min(n-1,fold(fold(2L*i+1,d,b),d,a)/2);
        return t;
    }
    static final String GLSL="float f(float q,float n,int m){if(m==1)return min(q,n-q);if(m==2)return max(q,n-q);if(m==3)return 2.*min(q,n-q);return q;}\n";
}
