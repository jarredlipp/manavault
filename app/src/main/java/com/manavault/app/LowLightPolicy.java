package com.manavault.app;

/** Pure threshold policy for bounded CameraX exposure compensation.
 * Android CameraX clamps the requested step to its supported range.
 * Counter/hysteresis and cooldown are enforced by NativeScannerActivity.
 */
final class LowLightPolicy {
    private LowLightPolicy(){}
    static int next(int current,double luma){
        if(Double.isNaN(luma)||luma<0)return current;
        if(luma<60)return Math.min(3,current+1);
        if(luma<91)return Math.min(2,current+1);
        // The dead zone prevents exposure hunting on illustrated/dark cards.
        if(luma>175)return Math.max(0,current-1);
        return current;
    }
}
