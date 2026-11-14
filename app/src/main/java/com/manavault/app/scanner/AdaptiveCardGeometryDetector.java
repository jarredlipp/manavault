package com.manavault.app.scanner;

/**
 * Allocation-light center-biased fallback edge detector for handheld cards.
 * Uses coherent border gradients across the whole image, not fixed holder ROI.
 * This is a fallback when the app's primary card-boundary detector returns null;
 * neither this detector nor a bounding box is evidence of a card printing.
 */
public final class AdaptiveCardGeometryDetector implements HandheldCameraXAnalyzer.GeometryDetector {
    private static final int SAMPLED_W=200,SAMPLED_H=280;
    @Override public HandheldCameraXAnalyzer.Geometry detect(byte[] luma,int width,int height,int stride) {
        if(luma==null||width<64||height<90||stride<width||
                luma.length<(height-1)*stride+width)return null;
        double scale=Math.max(1,Math.max(width/(double)SAMPLED_W,height/(double)SAMPLED_H));
        int w=(int)Math.round(width/scale),h=(int)Math.round(height/scale);
        if(w<40||h<60)return null;
        byte[] a=new byte[w*h];
        for(int y=0;y<h;y++) {
            int sy=Math.min(height-1,(int)(y*(double)height/h));
            for(int x=0;x<w;x++) {
                int sx=Math.min(width-1,(int)(x*(double)width/w));
                a[y*w+x]=luma[sy*stride+sx];
            }
        }
        HandheldCameraXAnalyzer.Geometry normal=detectSample(a,w,h,width,height,false);
        if(normal!=null)return normal;
        // Low-contrast black borders on dark playmats often disappear in the
        // raw Y plane. Retry once with bounded percentile stretch at only
        // ~56k pixels; do NOT modify the original camera frame for artwork.
        return detectSample(enhanceEdges(a),w,h,width,height,true);
    }
    private static byte[] enhanceEdges(byte[] src) {
        int[] bins=new int[256];for(byte b:src)bins[b&255]++;
        int lo=percentile(bins,src.length/40),hi=percentile(bins,src.length*39/40);
        if(hi-lo<3)return src.clone();
        double stretch=Math.min(4.2,220.0/Math.max(1,hi-lo));
        byte[] dst=new byte[src.length];
        for(int i=0;i<src.length;i++)dst[i]=(byte)Math.max(0,Math.min(255,
            Math.round(((src[i]&255)-lo)*stretch+12)));
        return dst;
    }
    private static int percentile(int[] bins,int target){
        int n=0;for(int v=0;v<bins.length;v++){n+=bins[v];if(n>target)return v;}
        return 255;
    }
    private static HandheldCameraXAnalyzer.Geometry detectSample(
            byte[] a,int w,int h,int width,int height,boolean lowContrastRecovery) {
        double[] xEdge=new double[w],yEdge=new double[h];
        int minY=(int)(.13*h),maxY=(int)(.87*h),minX=(int)(.13*w),maxX=(int)(.87*w);
        for(int x=2;x<w-2;x++) {
            double s=0;int n=0;
            for(int y=minY;y<maxY;y+=2) {
                s+=gradientX(a,w,x,y);n++;
            }
            xEdge[x]=n==0?0:s/n;
        }
        for(int y=2;y<h-2;y++) {
            double s=0;int n=0;
            for(int x=minX;x<maxX;x+=2){s+=gradientY(a,w,x,y);n++;}
            yEdge[y]=n==0?0:s/n;
        }
        int l=peak(xEdge,(int)(w*.04),(int)(w*.46));
        int r=peak(xEdge,(int)(w*.54),(int)(w*.96));
        int t=peak(yEdge,(int)(h*.04),(int)(h*.46));
        int b=peak(yEdge,(int)(h*.54),(int)(h*.96));
        if(l<0||r<0||t<0||b<0)return null;
        double grad=Math.min(Math.min(xEdge[l],xEdge[r]),Math.min(yEdge[t],yEdge[b]));
        if(grad<9.0)return null;
        double ratio=(r-l)/(double)Math.max(1,b-t);
        double coverage=(r-l)*(double)(b-t)/(w*h);
        if(ratio<.47||ratio>1.07||coverage<.12||coverage>.94)return null;
        Line left=fitVertical(a,w,h,l,t,b);
        Line right=fitVertical(a,w,h,r,t,b);
        Line top=fitHorizontal(a,w,h,t,l,r);
        Line bottom=fitHorizontal(a,w,h,b,l,r);
        if(left==null||right==null||top==null||bottom==null)return null;
        double[] tl=cross(left,top),tr=cross(right,top),br=cross(right,bottom),bl=cross(left,bottom);
        if(tl==null||tr==null||br==null||bl==null)return null;
        double[][] pts={tl,tr,br,bl};
        for(double[] p:pts)if(p[0]<0||p[0]>=w||p[1]<0||p[1]>=h)return null;
        double area=Math.abs(polygonArea(pts));
        coverage=area/(w*h);
        if(coverage<.12||coverage>.94)return null;
        double cardW=(length(tl,tr)+length(bl,br))*.5;
        double cardH=(length(tl,bl)+length(tr,br))*.5;
        if(cardH<1||cardW/cardH<.46||cardW/cardH>1.09)return null;
        double confidence=Math.max(.36,Math.min(lowContrastRecovery?.72:.92,grad/32));
        return new HandheldCameraXAnalyzer.Geometry(
            point(tl,width/(double)w,height/(double)h),
            point(tr,width/(double)w,height/(double)h),
            point(br,width/(double)w,height/(double)h),
            point(bl,width/(double)w,height/(double)h),coverage,confidence);
    }
    private static PerspectiveRectifier.Point point(double[] p,double sx,double sy) {
        return new PerspectiveRectifier.Point(p[0]*sx,p[1]*sy);
    }
    private static int peak(double[] a,int from,int to) {
        int k=-1;double best=-1;
        for(int i=Math.max(2,from);i<Math.min(a.length-2,to);i++) {
            // Broad edges are more stable than a single noisy pixel.
            double v=(a[i-1]+2*a[i]+a[i+1])*.25;
            if(v>best){best=v;k=i;}
        }
        return k;
    }
    private static int gradientX(byte[] a,int w,int x,int y) {
        return Math.abs((a[y*w+x+1]&255)-(a[y*w+x-1]&255));
    }
    private static int gradientY(byte[] a,int w,int x,int y) {
        return Math.abs((a[(y+1)*w+x]&255)-(a[(y-1)*w+x]&255));
    }
    /** x=a*y+b */
    private static Line fitVertical(byte[] a,int w,int h,int base,int t,int b) {
        double sy=0,sx=0,sxy=0,syy=0;int n=0;
        int radius=Math.max(3,(int)(w*.075));
        for(int i=1;i<=10;i++) {
            int y=t+(b-t)*i/11;
            if(y<2||y>=h-2)continue;
            int x=base;double best=0;
            for(int k=Math.max(2,base-radius);k<Math.min(w-2,base+radius);k++) {
                int e=gradientX(a,w,k,y);
                if(e>best){best=e;x=k;}
            }
            if(best<9)continue;
            sy+=y;sx+=x;sxy+=x*y;syy+=y*(double)y;n++;
        }
        if(n<4)return null;
        double d=n*syy-sy*sy;
        if(Math.abs(d)<.0001)return null;
        double slope=(n*sxy-sy*sx)/d;
        return new Line(slope,(sx-slope*sy)/n,true);
    }
    /** y=a*x+b */
    private static Line fitHorizontal(byte[] a,int w,int h,int base,int l,int r) {
        double sx=0,sy=0,sxy=0,sxx=0;int n=0;
        int radius=Math.max(3,(int)(h*.075));
        for(int i=1;i<=10;i++) {
            int x=l+(r-l)*i/11;
            if(x<2||x>=w-2)continue;
            int y=base;double best=0;
            for(int k=Math.max(2,base-radius);k<Math.min(h-2,base+radius);k++) {
                int e=gradientY(a,w,x,k);
                if(e>best){best=e;y=k;}
            }
            if(best<9)continue;
            sx+=x;sy+=y;sxy+=x*y;sxx+=x*(double)x;n++;
        }
        if(n<4)return null;
        double d=n*sxx-sx*sx;
        if(Math.abs(d)<.0001)return null;
        double slope=(n*sxy-sx*sy)/d;
        return new Line(slope,(sy-slope*sx)/n,false);
    }
    private static final class Line {
        final double a,b;final boolean xFromY;
        Line(double a,double b,boolean xFromY){this.a=a;this.b=b;this.xFromY=xFromY;}
    }
    private static double[] cross(Line vertical,Line horizontal) {
        if(!vertical.xFromY||horizontal.xFromY)return null;
        double d=1-vertical.a*horizontal.a;
        if(Math.abs(d)<.05)return null;
        double x=(vertical.a*horizontal.b+vertical.b)/d;
        return new double[]{x,horizontal.a*x+horizontal.b};
    }
    private static double polygonArea(double[][] p) {
        double sum=0;
        for(int i=0;i<4;i++){double[] a=p[i],b=p[(i+1)%4];sum+=a[0]*b[1]-a[1]*b[0];}
        return sum*.5;
    }
    private static double length(double[] a,double[] b) {
        return Math.hypot(a[0]-b[0],a[1]-b[1]);
    }
}
