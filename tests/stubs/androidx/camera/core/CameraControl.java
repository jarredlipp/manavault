package androidx.camera.core;
public interface CameraControl {
 Object startFocusAndMetering(FocusMeteringAction action);
 Object enableTorch(boolean enabled);
 Object setExposureCompensationIndex(int value);
}
