package com.manavault.app;

/**
 * Decides whether the visible top of a physical card stack has changed.
 *
 * Card corners alone are not a reliable signal: stacking another card keeps
 * the outline in almost the same place. An artwork + whole-card perceptual
 * difference must remain stable, or a significant cover/reveal event must
 * be seen before an identical-looking card can be scanned again.
 *
 * This class is deliberately independent of Android so it can be tested on a
 * regular JVM. All times are elapsedRealtime milliseconds.
 */
final class StackChangeGate {
    // Faster but still multi-frame and multi-region: no blind 850 ms cooldown.
    // Persistence ordering is separately guarded by ScanCycleCoordinator.
    private static final long MIN_AFTER_CAPTURE_MS=180;
    private static final long CHANGED_STABLE_MS=210;
    private static final long SAME_CARD_REVEAL_STABLE_MS=380;
    private static final int CHANGED_READS=3;
    private static final int IDENTICAL_REVEAL_READS=4;
    private long baselineArt,baselineFull,baselineTitle,capturedAt;
    private boolean active=false,covered=false;
    private int coveredReads=0,changedReads=0,identicalReads=0;
    private long changedAt=0,identicalAt=0,previousArt=0,previousFull=0;
    private String event="";

    synchronized void captured(long artHash,long fullHash,long now){
        captured(artHash,fullHash,fullHash,now);
    }
    synchronized void captured(long artHash,long fullHash,long titleHash,long now){
        baselineArt=artHash;baselineFull=fullHash;baselineTitle=titleHash;capturedAt=now;
        active=true;covered=false;coveredReads=0;changedReads=0;identicalReads=0;
        changedAt=0;identicalAt=0;event="";
    }
    synchronized void clear(){active=false;covered=false;coveredReads=0;changedReads=0;identicalReads=0;}
    synchronized String lastEvent(){return event;}
    /** Time of the first stable candidate frame, or zero if no candidate. */
    synchronized long changedCandidateAt(){return changedAt>0?changedAt:identicalAt;}
    /** Detection dropout alone is not enough: require a materially changed
     * whole-camera image over several frames while a hand covers the stack. */
    synchronized void coverFrame(boolean sceneObscured){
        if(!active||covered)return;
        coveredReads=sceneObscured?coveredReads+1:0;
        if(coveredReads>=4){covered=true;identicalReads=0;identicalAt=0;}
    }
    synchronized void unstable(){
        changedReads=0;changedAt=0;
        identicalReads=0;identicalAt=0;
    }
    /** Call only on geometrically stable, unobstructed card crops. */
    synchronized boolean observe(long artHash,long fullHash,long now){
        return observe(artHash,fullHash,fullHash,now);
    }
    synchronized boolean observe(long artHash,long fullHash,long titleHash,long now){
        if(!active||now-capturedAt<MIN_AFTER_CAPTURE_MS)return false;
        int art=Long.bitCount(artHash^baselineArt);
        int full=Long.bitCount(fullHash^baselineFull);
        int title=Long.bitCount(titleHash^baselineTitle);
        // Two independent regions must agree before a visually distinct card
        // is accepted. A single glare/reflection should not rearm the scanner.
        boolean changed=(art>=17&&full>=10)||(art>=26&&full>=7)||
                        (full>=25&&art>=9)||
                        // Changed card title AND another independent feature,
                        // both stable over several frames. Glare alone cannot
                        // rearm the scanner.
                        (title>=17&&(art>=9||full>=9));
        if(changed){
            identicalReads=0;identicalAt=0;
            if(changedReads==0 || Long.bitCount(artHash^previousArt)>14 ||
                    Long.bitCount(fullHash^previousFull)>15){
                changedReads=1;changedAt=now;
            }else changedReads++;
            previousArt=artHash;previousFull=fullHash;
            if(changedReads>=CHANGED_READS&&now-changedAt>=CHANGED_STABLE_MS){
                event="new top artwork";clear();return true;
            }
            return false;
        }
        changedReads=0;changedAt=0;
        // For identical copies the camera cannot tell whether a new physical
        // card was added from the final appearance alone. A strong intervening
        // cover/reveal sequence is mandatory; brief OCR glitches never count.
        if(covered&&art<=9&&full<=12){
            if(identicalReads++==0)identicalAt=now;
            if(identicalReads>=IDENTICAL_REVEAL_READS &&
                    now-identicalAt>=SAME_CARD_REVEAL_STABLE_MS){
                event="stack cover/reveal (possible identical copy)";clear();return true;
            }
        }else{identicalReads=0;identicalAt=0;}
        return false;
    }
}
