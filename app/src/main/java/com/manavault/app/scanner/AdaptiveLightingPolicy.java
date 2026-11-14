package com.manavault.app.scanner;

/** Hysteretic Off/Auto/On torch policy for dark ink and reflective foils.
 * This class makes no camera calls and is deterministic under a supplied clock.
 * Mode OFF never enables a torch; mode ON is an explicit user override.
 */
public final class AdaptiveLightingPolicy {
    public enum Mode { OFF, AUTO, ON }
    public static final class Decision {
        public final boolean changeTorch, torchOn, requestRefocus;
        public final String reason;
        Decision(boolean change,boolean on,String why) {
            changeTorch=change;torchOn=on;requestRefocus=change;reason=why;
        }
    }
    private static final long DARK_DWELL_MS=420;
    private static final long GLARE_DWELL_MS=210;
    private static final long BRIGHT_DWELL_MS=850;
    private static final long SWITCH_COOLDOWN_MS=1700;
    private Mode mode=Mode.AUTO;
    private boolean torchOn,flashAvailable;
    private long firstDark=-1,firstGlare=-1,firstBright=-1;
    private long lastSwitch=Long.MIN_VALUE/4;
    public AdaptiveLightingPolicy(boolean hasFlash) {flashAvailable=hasFlash;}
    public void setFlashAvailable(boolean available){flashAvailable=available;}
    public void setMode(Mode next) {if(next==null)throw new IllegalArgumentException("torch mode");mode=next;}
    public Mode getMode(){return mode;}
    public boolean isTorchOn(){return torchOn;}
    public Decision update(FrameIlluminationMetrics m,long nowMs) {
        if(m==null)throw new IllegalArgumentException("metrics");
        firstDark=mark(m.dark,firstDark,nowMs);
        firstGlare=mark(m.reflectiveGlare,firstGlare,nowMs);
        firstBright=mark(!m.dark && !m.reflectiveGlare,firstBright,nowMs);
        boolean requested=false;
        String reason="hold torch";
        if(mode==Mode.OFF || !flashAvailable) {requested=false;reason="torch off or unavailable";}
        else if(mode==Mode.ON) {requested=true;reason="manual torch on";}
        else if(torchOn) {
            if(dwelled(firstGlare,nowMs,GLARE_DWELL_MS)) {requested=false;reason="foil glare: switch torch off";}
            else if(dwelled(firstBright,nowMs,BRIGHT_DWELL_MS)) {requested=false;reason="ambient light recovered";}
            else requested=true;
        } else if(dwelled(firstDark,nowMs,DARK_DWELL_MS) && !m.reflectiveGlare) {
            requested=true;reason="sustained dark card";
        }
        if(requested==torchOn)return new Decision(false,torchOn,reason);
        // Respect direct user choice immediately. Auto must not strobe when the
        // camera AE is still settling or foil glare changes with hand movement.
        if(mode==Mode.AUTO && nowMs-lastSwitch<SWITCH_COOLDOWN_MS)
            return new Decision(false,torchOn,"torch cooldown");
        torchOn=requested;lastSwitch=nowMs;
        firstDark=firstGlare=firstBright=-1;
        return new Decision(true,torchOn,reason);
    }
    public void reset(){firstDark=firstGlare=firstBright=-1;lastSwitch=Long.MIN_VALUE/4;}
    private static long mark(boolean present,long prior,long now){return present?(prior<0?now:prior):-1;}
    private static boolean dwelled(long t,long now,long dwell){return t>=0 && now>=t && now-t>=dwell;}
}
