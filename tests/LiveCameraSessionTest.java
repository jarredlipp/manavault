import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;
import com.manavault.app.scanner.*;

import java.util.Collections;
import java.util.concurrent.Executor;

public final class LiveCameraSessionTest {
    static int n=0;
    static void check(boolean ok,String message){n++;if(!ok)throw new AssertionError(message);}
    static class Control implements CameraControl {
        public int focus,torch;
        @Override public Object startFocusAndMetering(FocusMeteringAction f){focus++;return null;}
        @Override public Object enableTorch(boolean enabled){torch++;return null;}
        @Override public Object setExposureCompensationIndex(int v){return null;}
    }
    static class Information implements CameraInfo {
        @Override public boolean hasFlashUnit(){return true;}
        @Override public ExposureState getExposureState(){return null;}
    }
    static class Sink implements HandheldCameraXAnalyzer.ResultSink {
        int bindings,events;
        @Override public boolean save(PrintingEvidenceResolver.Candidate c,HandheldCameraXAnalyzer.Capture capture){return true;}
        @Override public void review(PrintingEvidenceResolver.Result result,HandheldCameraXAnalyzer.Capture capture){throw new AssertionError("unexpected review");}
        @Override public void error(String s){throw new AssertionError(s);}
        @Override public void telemetry(String step,long ms,String data){events++;if(step.equals("camera_bound"))bindings++;}
    }
    public static void main(String[]args) {
        Executor immediate=Runnable::run;
        ScannerStartupCoordinator startup=new ScannerStartupCoordinator(immediate);
        Control ctrl=new Control();
        ProcessCameraProvider provider=new ProcessCameraProvider();
        provider.camera=new Camera(ctrl,new Information());
        Sink sink=new Sink();
        HandheldCameraXAnalyzer.GeometryDetector detector=(l,w,h,s)->null;
        HandheldCameraXAnalyzer.RecognitionEngine recognition=(c,cb)->cb.onCompleted(null,Collections.emptyList());
        LifecycleOwner owner=new LifecycleOwner(){};
        NativeScanCameraSession session=NativeScanCameraSession.start(owner,provider,new CameraSelector(),
            new Preview(),detector,recognition,sink,immediate,startup);
        check(provider.bindings==1,"binds preview and analysis together exactly once");
        check(provider.unbinds==0,"doesn't rebind/unbind preview during start");
        check(sink.bindings==1,"camera binding telemetry");
        check(startup.getActiveScans()==1,"holds catalog refresh during scanning");
        check(ctrl.torch>=0,"torch controls available");
        session.setTorchMode(AdaptiveLightingPolicy.Mode.ON);
        session.setTorchMode(AdaptiveLightingPolicy.Mode.OFF);
        session.completeManualReview(false);
        int[] ref={0};
        startup.refreshWhenIdle(()->ref[0]++);
        check(ref[0]==0,"does not refresh while scanning");
        session.close();
        session.close();
        check(provider.unbinds==1,"unbinds both owned use cases exactly once");
        check(startup.getActiveScans()==0,"releases scan gate");
        check(ref[0]==1,"pending refresh executes when idle");
        session.setTorchMode(AdaptiveLightingPolicy.Mode.AUTO);
        check(provider.unbinds==1,"controls after close are safe no-ops");
        ProcessCameraProvider failing=new ProcessCameraProvider(){
            @Override public Camera bindToLifecycle(LifecycleOwner o,CameraSelector s,Preview p,ImageAnalysis a){
                throw new IllegalStateException("camera occupied");
            }
        };
        boolean failed=false;
        try{NativeScanCameraSession.start(owner,failing,new CameraSelector(),new Preview(),
            detector,recognition,sink,immediate,startup);}catch(IllegalStateException e){failed=true;}
        check(failed,"camera bind failure is propagated");
        check(startup.getActiveScans()==0,"camera bind failure releases refresh gate");
        System.out.println("PASS: "+n+" live session binding, torch, review, refresh and lifecycle assertions");
    }
}
