package com.manavault.app.scanner;

/**
 * Optional YUV420 color crop, computed ONLY for frames actually submitted to
 * recognition. Preserve color for artwork/alternate-art matching; grayscale
 * alone loses important printing evidence.
 */
public final class ColorCardRectifier {
    public static final class Plane {
        public final byte[] data;
        public final int rowStride,pixelStride;
        public Plane(byte[] data,int rowStride,int pixelStride) {
            if(data==null||rowStride<1||pixelStride<1)throw new IllegalArgumentException();
            this.data=data;this.rowStride=rowStride;this.pixelStride=pixelStride;
        }
        int at(int x,int y) {
            long i=(long)y*rowStride+(long)x*pixelStride;
            return i<0||i>=data.length?128:data[(int)i]&255;
        }
    }
    private ColorCardRectifier(){}
    /** Returns packed RGB (3 bytes/pixel); U and V are from Android YUV_420_888 planes. */
    public static byte[] rectify(byte[] luma,int w,int h,int stride,Plane uPlane,Plane vPlane,
                                 PerspectiveRectifier.Warp warp,int outW,int outH) {
        return rectify(luma,w,h,stride,uPlane,vPlane,warp,outW,outH,0,w,h);
    }
    /** Chroma planes refer to raw sensor orientation, not upright luma. */
    public static byte[] rectify(byte[] luma,int w,int h,int stride,Plane uPlane,Plane vPlane,
                                 PerspectiveRectifier.Warp warp,int outW,int outH,
                                 int sourceRotationDegrees,int rawW,int rawH) {
        if(uPlane==null||vPlane==null||warp==null||outW<2||outH<2)throw new IllegalArgumentException();
        if(luma==null||luma.length<(h-1)*stride+w)throw new IllegalArgumentException("bad luma");
        byte[] out=new byte[outW*outH*3];
        for(int y=0;y<outH;y++) {
            double v=y/(double)(outH-1);
            for(int x=0;x<outW;x++) {
                double u=x/(double)(outW-1),sx=warp.x(u,v),sy=warp.y(u,v);
                if(!Double.isFinite(sx)||!Double.isFinite(sy))continue;
                sx=Math.max(0,Math.min(w-1,sx));sy=Math.max(0,Math.min(h-1,sy));
                int lum=PerspectiveRectifier.bilinear(luma,w,h,stride,sx,sy);
                int chromaX=FrameRotation.rawX(sx,sy,rawW,rawH,sourceRotationDegrees)/2;
                int chromaY=FrameRotation.rawY(sx,sy,rawW,rawH,sourceRotationDegrees)/2;
                int U=uPlane.at(chromaX,chromaY)-128,V=vPlane.at(chromaX,chromaY)-128;
                // BT.601 full-range approximation for image-recognition features.
                int off=(y*outW+x)*3;
                out[off]=(byte)clamp(lum+1.402*V);
                out[off+1]=(byte)clamp(lum-.344136*U-.714136*V);
                out[off+2]=(byte)clamp(lum+1.772*U);
            }
        }
        return out;
    }
    private static int clamp(double v){return (int)Math.max(0,Math.min(255,Math.round(v)));}
}
