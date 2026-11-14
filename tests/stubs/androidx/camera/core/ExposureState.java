package androidx.camera.core;
public interface ExposureState {
 boolean isExposureCompensationSupported();
 int getExposureCompensationIndex();
 Range getExposureCompensationRange();
 interface Range {int getLower();int getUpper();}
}
