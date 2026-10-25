package com.manavault.app;
/** Lightweight diagnostics from a rectified card image; not a heuristic that
 * silently rejects a valid card. Pure JVM implementation. CameraX samples a
 * 48x64 thumbnail, so glare/blur calculation is bounded to a few thousand px. */
final class CaptureQualityPolicy {
    private CaptureQualityPolicy(){}
    static final class Metrics {
        final int mean,contrast,edgeScore,glarePercent,shadowPercent;
        Metrics(int mean,int contrast,int edgeScore,int glarePercent,int shadowPercent){
            this.mean=mean;this.contrast=contrast;this.edgeScore=edgeScore;
            this.glarePercent=glarePercent;this.shadowPercent=shadowPercent;
        }
        String hint(){
            if(glarePercent>16)return "Glare across card — move/diffuse light";
            if(mean<45)return "Image too dark — increase diffuse lighting";
            if(mean>218)return "Image too bright — reduce direct lighting";
            if(edgeScore<9 && contrast<36)return "Low-detail frame — check focus";
            return "Image diagnostics nominal";
        }
    }
    static Metrics measure(int[] pixels,int width,int height){
        if(pixels==null||width<3||height<3||pixels.length<width*height)
            throw new IllegalArgumentException("Need valid thumbnail pixels");
        int[] light=new int[width*height];int min=255,max=0;long total=0,edges=0;
        int blown=0,shadow=0;
        for(int i=0;i<light.length;i++){
            int rgb=pixels[i];int y=((rgb>>16&255)*299+(rgb>>8&255)*587+(rgb&255)*114)/1000;
            light[i]=y;total+=y;min=Math.min(min,y);max=Math.max(max,y);
            if(y>=245)blown++;if(y<=22)shadow++;
        }
        // Focus detail should not depend on absolute card brightness.
        for(int y=1;y<height-1;y++)for(int x=1;x<width-1;x++){
            int i=y*width+x;
            edges+=Math.abs(light[i+1]-light[i-1])+Math.abs(light[i+width]-light[i-width]);
        }
        int n=light.length, interior=(height-2)*(width-2);
        return new Metrics((int)(total/n),max-min,(int)(edges/Math.max(1,interior*2)),
            100*blown/n,100*shadow/n);
    }
}
