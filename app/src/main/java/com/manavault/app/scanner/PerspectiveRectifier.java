package com.manavault.app.scanner;

/** Perspective-correct a detected handheld card to a canonical upright rectangle. */
public final class PerspectiveRectifier {
    public static final class Point {
        public final double x,y;
        public Point(double x,double y){this.x=x;this.y=y;}
    }
    public static final class Warp {
        private final double[] h;
        private Warp(double[] matrix){h=matrix;}
        public double x(double u,double v) {
            double d=h[6]*u+h[7]*v+1;
            if(Math.abs(d)<1e-12)return Double.NaN;
            return (h[0]*u+h[1]*v+h[2])/d;
        }
        public double y(double u,double v) {
            double d=h[6]*u+h[7]*v+1;
            if(Math.abs(d)<1e-12)return Double.NaN;
            return (h[3]*u+h[4]*v+h[5])/d;
        }
    }
    private PerspectiveRectifier(){}
    public static Warp warp(Point tl,Point tr,Point br,Point bl) {
        if(tl==null||tr==null||br==null||bl==null)throw new IllegalArgumentException("missing corners");
        Point[] corners={tl,tr,br,bl};
        double[][] normalized={{0,0},{1,0},{1,1},{0,1}};
        double[][] equations=new double[8][9];
        for(int i=0;i<4;i++) {
            double u=normalized[i][0],v=normalized[i][1],x=corners[i].x,y=corners[i].y;
            if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("invalid corner");
            equations[2*i]=new double[]{u,v,1,0,0,0,-u*x,-v*x,x};
            equations[2*i+1]=new double[]{0,0,0,u,v,1,-u*y,-v*y,y};
        }
        return new Warp(solve(equations));
    }
    public static byte[] rectify(byte[] source,int w,int h,int stride,
             Point tl,Point tr,Point br,Point bl,int outW,int outH) {
        return rectify(source,w,h,stride,warp(tl,tr,br,bl),outW,outH);
    }
    public static byte[] rectify(byte[] source,int w,int h,int stride,
             Warp warp,int outW,int outH) {
        if(source==null||w<=1||h<=1||stride<w||outW<2||outH<2||
                source.length<(h-1)*stride+w||warp==null)throw new IllegalArgumentException("invalid frame");
        byte[] out=new byte[outW*outH];
        for(int y=0;y<outH;y++) {
            double v=y/(double)(outH-1);
            for(int x=0;x<outW;x++) {
                double u=x/(double)(outW-1);
                double sx=warp.x(u,v),sy=warp.y(u,v);
                if(!Double.isFinite(sx)||!Double.isFinite(sy))continue;
                out[y*outW+x]=(byte)bilinear(source,w,h,stride,sx,sy);
            }
        }
        return out;
    }
    public static int bilinear(byte[] a,int w,int h,int stride,double x,double y) {
        x=Math.max(0,Math.min(w-1,x));y=Math.max(0,Math.min(h-1,y));
        int i=(int)x,j=(int)y;double fx=x-i,fy=y-j;
        int p00=a[j*stride+i]&255,p10=a[j*stride+Math.min(i+1,w-1)]&255;
        int p01=a[Math.min(j+1,h-1)*stride+i]&255,p11=a[Math.min(j+1,h-1)*stride+Math.min(i+1,w-1)]&255;
        return (int)Math.round((p00*(1-fx)+p10*fx)*(1-fy)+(p01*(1-fx)+p11*fx)*fy);
    }
    private static double[] solve(double[][] a) {
        int n=8;
        for(int col=0;col<n;col++) {
            int max=col;for(int r=col+1;r<n;r++)if(Math.abs(a[r][col])>Math.abs(a[max][col]))max=r;
            if(Math.abs(a[max][col])<1e-12)throw new IllegalArgumentException("degenerate quadrilateral");
            double[] t=a[col];a[col]=a[max];a[max]=t;
            double p=a[col][col];for(int c=col;c<=n;c++)a[col][c]/=p;
            for(int r=0;r<n;r++)if(r!=col){double k=a[r][col];for(int c=col;c<=n;c++)a[r][c]-=k*a[col][c];}
        }
        double[] h=new double[n];for(int i=0;i<n;i++)h[i]=a[i][n];return h;
    }
}
