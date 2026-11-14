package com.manavault.app.scanner;

/** Crops for OCR use card-relative coordinates, not fixed phone-stand pixels.
 * Both raw and gently contrast-normalized gray crops are available so
 * reflective foil/highlights are never irreversibly erased by pre-processing.
 */
public final class CardTextRegions {
    public static final class Region {
        public final int width,height;
        public final byte[] raw,enhanced;
        Region(byte[] raw,byte[] enhanced,int w,int h) {
            this.raw=raw;this.enhanced=enhanced;width=w;height=h;
        }
    }
    public final Region title,footer,typeBar,alternateTitle;
    private CardTextRegions(Region title,Region footer,Region typeBar,Region alternateTitle) {
        this.title=title;this.footer=footer;this.typeBar=typeBar;this.alternateTitle=alternateTitle;
    }
    public static CardTextRegions from(byte[] rectified,int w,int h) {
        if(rectified==null||w<90||h<120||rectified.length<w*h)
            throw new IllegalArgumentException("card crop missing");
        return new CardTextRegions(
            crop(rectified,w,h,.04,.018,.965,.155),
            crop(rectified,w,h,.018,.845,.982,.997),
            crop(rectified,w,h,.025,.48,.97,.715),
            crop(rectified,w,h,.03,.13,.97,.49));
    }
    private static Region crop(byte[] a,int w,int h,double l,double t,double r,double b) {
        int x0=(int)Math.floor(l*w),x1=Math.min(w,(int)Math.ceil(r*w));
        int y0=(int)Math.floor(t*h),y1=Math.min(h,(int)Math.ceil(b*h));
        int ow=x1-x0,oh=y1-y0;
        byte[] raw=new byte[ow*oh];
        for(int y=0;y<oh;y++)System.arraycopy(a,(y+y0)*w+x0,raw,y*ow,ow);
        return new Region(raw,enhance(raw),ow,oh);
    }
    /** Conservative histogram stretch (at most 1.8x) avoids washing out foil ink. */
    public static byte[] enhance(byte[] src) {
        int[] bins=new int[256];for(byte b:src)bins[b&255]++;
        int lo=percentile(bins,src.length/20),hi=percentile(bins,src.length*19/20);
        if(hi<=lo+12)return src.clone();
        double scale=Math.min(1.8,220.0/(hi-lo));
        double mid=(lo+hi)*.5;
        byte[] out=new byte[src.length];
        for(int i=0;i<src.length;i++) {
            int v=src[i]&255;
            out[i]=(byte)Math.max(0,Math.min(255,Math.round((v-mid)*scale+128)));
        }
        return out;
    }
    private static int percentile(int[] bins,int target) {
        int sum=0;for(int i=0;i<bins.length;i++){sum+=bins[i];if(sum>target)return i;}return 255;
    }
}
