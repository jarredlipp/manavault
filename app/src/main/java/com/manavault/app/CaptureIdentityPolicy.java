package com.manavault.app;

/** Conservative guard for contradictory OCR and photography.
 * A single artwork hash must NEVER mark a different card as already scanned.
 * This pure-Java policy does not count cards or decide printings. */
final class CaptureIdentityPolicy {
    private CaptureIdentityPolicy(){}
    private static int colorDifference(byte[] a,byte[] b){
        if(a==null||b==null||a.length!=b.length||a.length<96)return 256;
        long sum=0;
        for(int i=0;i<a.length;i++)sum+=Math.abs((a[i]&255)-(b[i]&255));
        return (int)(sum/a.length);
    }
    static boolean needsIdentityReview(String previous,String proposed,
             long previousArt,long proposedArt,long previousTitle,long proposedTitle,
             long previousFull,long proposedFull,byte[] previousColor,byte[] proposedColor){
        // Consecutive *matching* identities are governed by the explicit
        // Copy options policy, not by image-similarity shortcuts.
        if(PhysicalCopyPolicy.identity(previous).isEmpty() ||
           PhysicalCopyPolicy.identity(proposed).isEmpty() ||
           PhysicalCopyPolicy.needsCopyConfirmation(previous,proposed))return false;
        return Long.bitCount(previousArt^proposedArt)<=4 &&
               Long.bitCount(previousTitle^proposedTitle)<=5 &&
               Long.bitCount(previousFull^proposedFull)<=5 &&
               colorDifference(previousColor,proposedColor)<=13;
    }
}
