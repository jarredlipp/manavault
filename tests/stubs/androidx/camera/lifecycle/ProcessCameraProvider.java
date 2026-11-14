package androidx.camera.lifecycle;
import androidx.camera.core.*;
import androidx.lifecycle.LifecycleOwner;
public class ProcessCameraProvider {
 public int bindings,unbinds;
 public Camera camera;
 public Camera bindToLifecycle(LifecycleOwner owner,CameraSelector selector,Preview preview,ImageAnalysis analysis){
  bindings++;return camera;
 }
 public void unbind(Object... cases){unbinds++;}
}
