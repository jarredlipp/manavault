package com.manavault.app.scanner;

/** Fast luminance/glare estimates; derived from the observed card rather than the viewfinder. */
public final class FrameIlluminationMetrics {
    public final double mean, darkFraction, blownFraction, glareFraction;
    public final boolean dark, reflectiveGlare;
    public FrameIlluminationMetrics(double mean,double darkFraction,double blownFraction,double glareFraction) {
        this.mean=mean;this.darkFraction=darkFraction;
        this.blownFraction=blownFraction;this.glareFraction=glareFraction;
        dark=mean<69 || darkFraction>.43;
        // Strong clipped highlights are a reason NOT to force the torch on foils.
        reflectiveGlare=glareFraction>.08 || blownFraction>.115;
    }
    public static FrameIlluminationMetrics measure(byte[] y,int w,int h,int stride) {
        if(y==null||w<8||h<8||stride<w||y.length<(h-1)*stride+w)
            throw new IllegalArgumentException("invalid luminance frame");
        long sum=0;int dark=0,white=0,glare=0,n=0;
        int step=Math.max(1,Math.max(w,h)/160);
        for(int row=2;row<h-2;row+=step)for(int col=2;col<w-2;col+=step) {
            int v=y[row*stride+col]&255;sum+=v;n++;
            if(v<54)dark++;if(v>247)white++;
            // A concentrated saturated highlight with dark immediate neighbors is
            // more suggestive of a reflective streak than a white card border.
            if(v>=247) {
                int near=((y[row*stride+col-2]&255)+(y[row*stride+col+2]&255)
                    +(y[(row-2)*stride+col]&255)+(y[(row+2)*stride+col]&255))/4;
                if(near<205)glare++;
            }
        }
        return new FrameIlluminationMetrics(sum/(double)n,dark/(double)n,
            white/(double)n,glare/(double)n);
    }
}
