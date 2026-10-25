package com.manavault.app;
/** Physical split layouts use two sideways panels within one card frame.
 * The stored scanner photo may already be rotated into landscape. Geometry
 * does not establish card identity; both OCR titles are required for proof. */
final class SplitCardLayoutPolicy {
    private SplitCardLayoutPolicy(){}
    // Each {x,y,width,height} is fractional in the captured card image.
    static float[][] panels(int width,int height){
        if(width>height){return new float[][]{
            {.025f,.015f,.47f,.31f},{.505f,.015f,.47f,.31f}};}
        return new float[][]{
            {.025f,.025f,.95f,.475f},{.025f,.505f,.95f,.475f}};
    }
    static int titleRotation(int width,int height){return width>height?0:90;}
    static String[] zones(int width,int height){return width>height?
        new String[]{"split-title-left","split-title-right"}:
        new String[]{"split-title-upper","split-title-lower"};}
}
