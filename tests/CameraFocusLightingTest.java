import java.util.Arrays;
import java.nio.ByteBuffer;
import java.util.Collections;
import com.manavault.app.scanner.*;
import androidx.camera.core.*;

/** Regression tests for camera-first-focus, foil/low-light controls and card OCR ROI. */
public final class CameraFocusLightingTest {
    private static int checks;
    private static void ok(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
    private static final class FakeControl implements CameraControl {
        int focusCount,torchChanges,exposureChanges,exposure;
        boolean torch;
        public Object startFocusAndMetering(FocusMeteringAction a){focusCount++;return new Object();}
        public Object enableTorch(boolean on){torchChanges++;torch=on;return new Object();}
        public Object setExposureCompensationIndex(int idx){exposureChanges++;exposure=idx;return new Object();}
    }
    private static final class FakeInfo implements CameraInfo {
        final FakeControl control; final boolean hasFlash;
        FakeInfo(FakeControl c,boolean flash){control=c;hasFlash=flash;}
        public boolean hasFlashUnit(){return hasFlash;}
        public ExposureState getExposureState(){return new ExposureState(){
            public boolean isExposureCompensationSupported(){return true;}
            public int getExposureCompensationIndex(){return control.exposure;}
            public Range getExposureCompensationRange(){return new Range(){
                public int getLower(){return -2;} public int getUpper(){return 2;}
            };}
        };}
    }
    private static FrameIlluminationMetrics metrics(int intensity) {
        byte[] y=new byte[120*160];Arrays.fill(y,(byte)intensity);
        return FrameIlluminationMetrics.measure(y,120,160,120);
    }
    private static void testMetricsAndPolicy() {
        FrameIlluminationMetrics dark=metrics(34),bright=metrics(150);
        ok(dark.dark && dark.darkFraction>.95,"recognizes sustained dark card");
        ok(!bright.dark && !bright.reflectiveGlare,"regular lit card doesn't force torch");
        byte[] withGlare=new byte[120*160];Arrays.fill(withGlare,(byte)110);
        for(int y=25;y<145;y++)for(int x=30;x<65;x++)withGlare[y*120+x]=(byte)255;
        FrameIlluminationMetrics foil=FrameIlluminationMetrics.measure(withGlare,120,160,120);
        ok(foil.reflectiveGlare,"large clipped foil highlight detected");
        AdaptiveLightingPolicy p=new AdaptiveLightingPolicy(true);
        ok(!p.update(dark,0).changeTorch,"darkness debounced on first frame");
        AdaptiveLightingPolicy.Decision lit=p.update(dark,500);
        ok(lit.changeTorch && lit.torchOn && lit.requestRefocus,"sustained dark card lights and refocuses");
        ok(!p.update(foil,1000).changeTorch,"hysteresis prevents strobing on reflected foil");
        ok(!p.update(foil,2200).torchOn,"bright foil returns to no-torch lighting");
        p.setMode(AdaptiveLightingPolicy.Mode.ON);
        ok(p.update(bright,2300).torchOn,"manual ON honored despite normal lighting");
        p.setMode(AdaptiveLightingPolicy.Mode.OFF);
        ok(!p.update(dark,2400).torchOn,"manual OFF honored even in low light");
        AdaptiveLightingPolicy noFlash=new AdaptiveLightingPolicy(false);
        noFlash.setMode(AdaptiveLightingPolicy.Mode.ON);
        ok(!noFlash.update(dark,2500).torchOn,"unsupported flashlight isn't enabled");
    }
    private static void testCameraXController() {
        FakeControl c=new FakeControl();
        CameraXLightingController ctrl=new CameraXLightingController(c,new FakeInfo(c,true));
        byte[] dark=new byte[140*200];Arrays.fill(dark,(byte)28);
        ok(!ctrl.onFrame(dark,140,200,0),"first dim frame doesn't flash immediately");
        ok(ctrl.onFrame(dark,140,200,550),"sustained dim frame changes illumination");
        ok(c.torch && c.torchChanges==1,"torch turns on exactly once");
        for(int i=0;i<20;i++)ctrl.onFrame(dark,140,200,600+i*40);
        ok(c.torchChanges==1,"stable low light does not oscillate torch");
        ctrl.setMode(AdaptiveLightingPolicy.Mode.OFF);
        ok(ctrl.onFrame(dark,140,200,1500) && !c.torch,"manual torch OFF disables light");
        ctrl.close();
    }
    private static void testContrastAndCardGeometry() {
        int w=200,h=280;
        byte[] sample=new byte[w*h];Arrays.fill(sample,(byte)22);
        for(int y=45;y<225;y++)for(int x=50;x<150;x++)
            sample[y*w+x]=(byte)(35+(x+y)%3);
        HandheldCameraXAnalyzer.Geometry g=new AdaptiveCardGeometryDetector().detect(sample,w,h,w);
        ok(g!=null,"low-contrast card detected using bounded gradient enhancement");
        ok(g.tl.x>40&&g.tl.x<60 && g.br.y>215&&g.br.y<235,
            "recover card border geometry without holder");
        byte[] empty=new byte[w*h];Arrays.fill(empty,(byte)24);
        ok(new AdaptiveCardGeometryDetector().detect(empty,w,h,w)==null,
            "dark featureless image doesn't invent a card");
        byte[] rectified=new byte[540*756];
        for(int y=0;y<756;y++)for(int x=0;x<540;x++)rectified[y*540+x]=(byte)(25+(x+y)%105);
        CardTextRegions regions=CardTextRegions.from(rectified,540,756);
        ok(regions.title.width>450&&regions.title.height>60,"title region has usable OCR dimensions");
        ok(regions.footer.height>80 && regions.footer.raw.length==regions.footer.enhanced.length,
            "footer/collector OCR crop keeps raw and contrast-adjusted alternatives");
        ok(regions.typeBar.width>450 && regions.alternateTitle.height>180,
            "typebar and alternate-title crops ready for recognition engine");
    }
    private static final class BlankProxy implements ImageProxy {
        private final int width=160,height=240;
        private final byte[] y=new byte[width*height];
        boolean closed;
        BlankProxy(){Arrays.fill(y,(byte)18);}
        public int getWidth(){return width;}
        public int getHeight(){return height;}
        public PlaneProxy[] getPlanes(){return new PlaneProxy[]{new PlaneProxy(){
            public ByteBuffer getBuffer(){return ByteBuffer.wrap(y);}
            public int getRowStride(){return width;}
            public int getPixelStride(){return 1;}
        }};}
        public ImageInfo getImageInfo(){return ()->0;}
        public void close(){closed=true;}
    }
    private static void testFirstFrameFocusWithoutBorders() {
        FakeControl camera=new FakeControl();
        final int[] errors={0},recognized={0},firstFrames={0};
        HandheldCameraXAnalyzer scan=new HandheldCameraXAnalyzer(
            (y,w,h,stride)->null,(capture,finish)->recognized[0]++,
            new HandheldCameraXAnalyzer.ResultSink(){
                public boolean save(PrintingEvidenceResolver.Candidate c,HandheldCameraXAnalyzer.Capture cap){return true;}
                public void review(PrintingEvidenceResolver.Result r,HandheldCameraXAnalyzer.Capture cap){}
                public void error(String e){errors[0]++;}
                public void telemetry(String name,long ms,String detail){if("camera_first_frame".equals(name))firstFrames[0]++;}
            },Runnable::run,camera,new FakeInfo(camera,false));
        BlankProxy p=new BlankProxy();scan.analyze(p);
        ok(p.closed,"first frame closed even when edge detection fails");
        ok(camera.focusCount==1,"first frame requests center focus without waiting for card edges");
        ok(recognized[0]==0&&errors[0]==0,"no OCR on featureless blur and no camera exception");
        ok(firstFrames[0]==1,"first preview telemetry generated");
        scan.close();
    }
    private static void testSceneSuppressionWhileTorchSettles(){
        HandheldScanCoordinator state=new HandheldScanCoordinator();
        HandheldScanCoordinator.Frame first=new HandheldScanCoordinator.Frame(0,123L,.8,.8,.6,.9,0,222L,333L);
        HandheldScanCoordinator.Decision d=state.onFrame(first);
        ok(d.action==HandheldScanCoordinator.Action.ANALYZE,"first card eligible for OCR");
        state.onRetry(d.generation,50);
        state.noteLightingChange(200);
        ok(state.onFrame(new HandheldScanCoordinator.Frame(300,~123L,.8,.8,.6,.9,.05,~222L,~333L)).action
            ==HandheldScanCoordinator.Action.WAIT,"torch illumination doesn't create a false new card");
        ok(state.currentGeneration()==d.generation,"lighting changes don't invalidate current card identity");
    }
    public static void main(String[] args){
        testMetricsAndPolicy();testCameraXController();testContrastAndCardGeometry();
        testFirstFrameFocusWithoutBorders();testSceneSuppressionWhileTorchSettles();
        System.out.println("PASS: "+checks+" focus / lighting / foil / dark-edge / OCR-region assertions");
    }
}
