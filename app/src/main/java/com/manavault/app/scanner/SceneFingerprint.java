package com.manavault.app.scanner;

/**
 * Perceptual hashes of the card's title and artwork regions in its perspective-
 * corrected, upright, low-resolution luminance preview. This is a camera-scene
 * transition hint, NOT a printing identity or substitute for catalog evidence.
 */
public final class SceneFingerprint {
    public final long titleHash;
    public final long artworkHash;
    private SceneFingerprint(long titleHash, long artworkHash) {
        this.titleHash=titleHash;
        this.artworkHash=artworkHash;
    }

    public static SceneFingerprint fromPreview(byte[] gray,int w,int h) {
        if(gray==null||w<20||h<40||gray.length<w*h)
            throw new IllegalArgumentException("invalid upright preview");
        // Exclude the outer black border and sample the title separately from the art.
        return new SceneFingerprint(regionHash(gray,w,h,.06,.20),
                regionHash(gray,w,h,.23,.70));
    }

    private static long regionHash(byte[] gray,int w,int h,double top,double bottom) {
        int y0=Math.max(0,Math.min(h-1,(int)Math.floor(h*top)));
        int y1=Math.max(y0+1,Math.min(h,(int)Math.ceil(h*bottom)));
        int x0=Math.min(w-1,Math.max(0,(int)(w*.09)));
        int x1=Math.max(x0+1,Math.min(w,(int)(w*.91)));
        double[][] cells=new double[8][9];
        for(int row=0;row<8;row++)for(int col=0;col<9;col++) {
            // 4-point box sampling reduces noise from camera motion and compression.
            double u=(col+.5)/9.0, v=(row+.5)/8.0;
            int cx=x0+(int)(u*(x1-x0-1));
            int cy=y0+(int)(v*(y1-y0-1));
            int lx=Math.max(x0,cx-1), rx=Math.min(x1-1,cx+1);
            int ty=Math.max(y0,cy-1), by=Math.min(y1-1,cy+1);
            cells[row][col]=((gray[ty*w+lx]&255)+(gray[ty*w+rx]&255)
                    +(gray[by*w+lx]&255)+(gray[by*w+rx]&255))/4.0;
        }
        long bits=0;
        for(int row=0;row<8;row++)for(int col=0;col<8;col++) {
            bits=(bits<<1)|(cells[row][col]>cells[row][col+1]?1:0);
        }
        return bits;
    }
}
