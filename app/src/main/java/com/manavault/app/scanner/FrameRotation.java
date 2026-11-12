package com.manavault.app.scanner;

/**
 * Rotate CameraX Y-plane into the display-upright orientation before detecting
 * card corners. Rotation comes from ImageInfo.getRotationDegrees().
 */
public final class FrameRotation {
    private FrameRotation(){}
    public static int uprightWidth(int rawW,int rawH,int degrees) {
        validate(degrees);return degrees==90||degrees==270?rawH:rawW;
    }
    public static int uprightHeight(int rawW,int rawH,int degrees) {
        validate(degrees);return degrees==90||degrees==270?rawW:rawH;
    }
    public static byte[] rotate(byte[] raw,int rawW,int rawH,int degrees) {
        validate(degrees);
        if(raw==null||rawW<=0||rawH<=0||raw.length<rawW*rawH)
            throw new IllegalArgumentException("bad camera frame");
        if(degrees==0)return raw;
        int w=uprightWidth(rawW,rawH,degrees),h=uprightHeight(rawW,rawH,degrees);
        byte[] rotated=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            int rx=rawX(x,y,rawW,rawH,degrees);
            int ry=rawY(x,y,rawW,rawH,degrees);
            rotated[y*w+x]=raw[ry*rawW+rx];
        }
        return rotated;
    }
    /** Upright coordinate -> original camera luma coordinate. */
    public static int rawX(double x,double y,int rawW,int rawH,int degrees) {
        validate(degrees);
        int val;
        if(degrees==90)val=(int)Math.round(y);
        else if(degrees==180)val=rawW-1-(int)Math.round(x);
        else if(degrees==270)val=rawW-1-(int)Math.round(y);
        else val=(int)Math.round(x);
        return Math.max(0,Math.min(rawW-1,val));
    }
    public static int rawY(double x,double y,int rawW,int rawH,int degrees) {
        validate(degrees);
        int val;
        if(degrees==90)val=rawH-1-(int)Math.round(x);
        else if(degrees==180)val=rawH-1-(int)Math.round(y);
        else if(degrees==270)val=(int)Math.round(x);
        else val=(int)Math.round(y);
        return Math.max(0,Math.min(rawH-1,val));
    }
    private static void validate(int rotation) {
        if(rotation!=0&&rotation!=90&&rotation!=180&&rotation!=270)
            throw new IllegalArgumentException("unexpected CameraX rotation "+rotation);
    }
}
