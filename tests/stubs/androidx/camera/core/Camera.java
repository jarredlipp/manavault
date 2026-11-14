package androidx.camera.core;
public class Camera {
 private final CameraControl control;
 private final CameraInfo info;
 public Camera(CameraControl c,CameraInfo i){control=c;info=i;}
 public CameraControl getCameraControl(){return control;}
 public CameraInfo getCameraInfo(){return info;}
}
