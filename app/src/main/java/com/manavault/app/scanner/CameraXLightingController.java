package com.manavault.app.scanner;

import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;

/** Binds AdaptiveLightingPolicy to a single already-bound CameraX camera.
 * Call on the serial analyzer executor and keep the same instance for a session.
 */
public final class CameraXLightingController {
    private final CameraControl control;
    private final AdaptiveLightingPolicy policy;
    private final CameraInfo info;
    private int lastRequestedExposure=Integer.MIN_VALUE;
    private long lastExposureChangeMs=Long.MIN_VALUE/4;
    public CameraXLightingController(CameraControl control,CameraInfo info) {
        if(control==null||info==null)throw new IllegalArgumentException("bound camera required");
        this.control=control;this.info=info;
        policy=new AdaptiveLightingPolicy(info.hasFlashUnit());
    }
    public AdaptiveLightingPolicy.Mode getMode(){return policy.getMode();}
    /** Supports wiring a three-state flashlight button: OFF/AUTO/ON. */
    public void setMode(AdaptiveLightingPolicy.Mode mode){policy.setMode(mode);}
    public boolean isTorchRequested(){return policy.isTorchOn();}
    /** Returns true when lighting changed and focus should be reattempted after settling. */
    public boolean onFrame(byte[] preview,int width,int height,long nowMs) {
        FrameIlluminationMetrics m=FrameIlluminationMetrics.measure(preview,width,height,width);
        AdaptiveLightingPolicy.Decision decision=policy.update(m,nowMs);
        if(decision.changeTorch)control.enableTorch(decision.torchOn);
        // AE is preferable to torch strobing for dark cards and reflective foils.
        // Only apply compensation when device reports a supported range.
        if(nowMs-lastExposureChangeMs>=1650 && info.getExposureState()!=null
            && info.getExposureState().isExposureCompensationSupported()) {
            int low=info.getExposureState().getExposureCompensationRange().getLower();
            int high=info.getExposureState().getExposureCompensationRange().getUpper();
            int current=info.getExposureState().getExposureCompensationIndex();
            int target=current;
            if(m.dark && !m.reflectiveGlare && !policy.isTorchOn())target=Math.min(high,current+1);
            if(m.reflectiveGlare)target=Math.max(low,current-1);
            // Restore neutral compensation as ambient light recovers; otherwise
            // a dark card can leave the next bright foil overexposed.
            if(!m.dark && !m.reflectiveGlare) {
                if(current>0)target=Math.max(0,current-1);
                else if(current<0)target=Math.min(0,current+1);
            }
            if(target!=current && target!=lastRequestedExposure) {
                control.setExposureCompensationIndex(target);
                lastRequestedExposure=target;lastExposureChangeMs=nowMs;
            }
        }
        return decision.requestRefocus;
    }
    public void close() {
        policy.setMode(AdaptiveLightingPolicy.Mode.OFF);
        FrameIlluminationMetrics normal=new FrameIlluminationMetrics(120,0,0,0);
        if(policy.update(normal,System.nanoTime()/1_000_000).changeTorch)control.enableTorch(false);
    }
}
