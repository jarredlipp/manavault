import com.manavault.app.scanner.*;
import androidx.camera.core.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Regression coverage for the Nov 12 no-title, dark-frame and handheld failures. */
public final class ScannerRecoveryTest {
    static int checks;
    static void check(boolean p,String what){checks++;if(!p)throw new AssertionError(what);}
    static byte[] card(int w,int h) {
        byte[] a=new byte[w*h];Arrays.fill(a,(byte)24);
        for(int y=45;y<225;y++)for(int x=50;x<150;x++){
            // Low-amplitude internal detail makes a sharp OCR frame without
            // overwhelming the four high-contrast card boundaries.
            int jitter=((x*97+y*61) % 51)-25;
            a[y*w+x]=(byte)(185+jitter);
        }
        return a;
    }
    static final class Proxy implements ImageProxy {
        final int w=280,h=200;boolean closed;
        final byte[] a;
        Proxy(byte[] a){this.a=a;}
        public int getWidth(){return w;}
        public int getHeight(){return h;}
        public PlaneProxy[] getPlanes(){return new PlaneProxy[]{new PlaneProxy(){
            public ByteBuffer getBuffer(){return ByteBuffer.wrap(a);}
            public int getRowStride(){return w;}
            public int getPixelStride(){return 1;}
        }};}
        public ImageInfo getImageInfo(){return ()->90;}
        public void close(){closed=true;}
    }
    static void geometry(){
        byte[] picture=card(200,280);
        HandheldCameraXAnalyzer.Geometry g=new AdaptiveCardGeometryDetector().detect(picture,200,280,200);
        check(g!=null,"center handheld card boundary detected");
        check(Math.abs(g.tl.x-50)<9 && Math.abs(g.tl.y-45)<9,"top left detected");
        check(Math.abs(g.br.x-150)<9 && Math.abs(g.br.y-225)<9,"bottom right detected");
        check(g.confidence>=.36 && g.cardCoverage>.25,"detected crop confidence");
        check(new AdaptiveCardGeometryDetector().detect(new byte[200*280],200,280,200)==null,
            "featureless backgrounds do not invent cards");
    }
    static void color() {
        byte[] gray=new byte[16*16];Arrays.fill(gray,(byte)128);
        byte[] neutral=new byte[8*8];Arrays.fill(neutral,(byte)128);
        PerspectiveRectifier.Warp warp=PerspectiveRectifier.warp(
            new PerspectiveRectifier.Point(0,0),new PerspectiveRectifier.Point(15,0),
            new PerspectiveRectifier.Point(15,15),new PerspectiveRectifier.Point(0,15));
        byte[] rgb=ColorCardRectifier.rectify(gray,16,16,16,
            new ColorCardRectifier.Plane(neutral,8,1),
            new ColorCardRectifier.Plane(neutral,8,1),warp,8,8);
        check(rgb.length==8*8*3,"RGB output shape");
        check((rgb[0]&255)==128 && (rgb[1]&255)==128 && (rgb[2]&255)==128,
            "neutral chroma yields grayscale");
        byte[] red=new byte[8*8];Arrays.fill(red,(byte)200);
        rgb=ColorCardRectifier.rectify(gray,16,16,16,
            new ColorCardRectifier.Plane(neutral,8,1),
            new ColorCardRectifier.Plane(red,8,1),warp,8,8);
        check((rgb[0]&255)>(rgb[2]&255),"artwork red chroma retained");
    }
    static void retries() throws Exception {
        HandheldScanCoordinator s=new HandheldScanCoordinator();
        HandheldScanCoordinator.Frame ok=new HandheldScanCoordinator.Frame(0,0x2,0.75,.82,.55,.88,.05);
        HandheldScanCoordinator.Decision a=s.onFrame(ok);
        check(a.action==HandheldScanCoordinator.Action.ANALYZE,"first usable frame immediately analyzes");
        s.onRetry(a.generation,50);
        check(!s.isReviewPending(),"unrecognized card does NOT open review");
        check(s.onFrame(new HandheldScanCoordinator.Frame(200,0x2,.75,.82,.55,.88,.05)).action
            ==HandheldScanCoordinator.Action.WAIT,"OCR retried with backoff");
        check(s.onFrame(new HandheldScanCoordinator.Frame(275,0x2,.75,.82,.55,.88,.05)).action
            ==HandheldScanCoordinator.Action.ANALYZE,"second frame analyzed automatically");
        s.onRetry(s.currentGeneration(),300);
        check(!s.isReviewPending(),"second unidentified result still not a manual picker");
        HandheldScanCoordinator.Decision b=s.onFrame(new HandheldScanCoordinator.Frame(610,0x2,.75,.82,.55,.88,.05));
        check(b.action==HandheldScanCoordinator.Action.ANALYZE,"third OCR attempt possible");
        check(!s.shouldOpenReview(b.generation,630),"ambiguous edition gets additional frame");
        check(s.onFrame(new HandheldScanCoordinator.Frame(800,0x2,.75,.82,.55,.88,.05)).action
            ==HandheldScanCoordinator.Action.ANALYZE,"second edition evidence frame");
        check(!s.shouldOpenReview(s.currentGeneration(),820),"two frames before edition picker");
        check(s.onFrame(new HandheldScanCoordinator.Frame(990,0x2,.75,.82,.55,.88,.05)).action
            ==HandheldScanCoordinator.Action.ANALYZE,"third edition evidence frame");
        check(s.shouldOpenReview(s.currentGeneration(),1010),"genuinely ambiguous printing opens picker");
        check(s.isReviewPending(),"manual review state held");
        s.completeManualReview(false);
        check(!s.isReviewPending(),"retry resumes after dismissed edition picker");
    }
    static void poorLighting(){
        HandheldScanCoordinator s=new HandheldScanCoordinator();
        check(s.onFrame(new HandheldScanCoordinator.Frame(0,123,.10,.12,.6,.9,.05)).action
            ==HandheldScanCoordinator.Action.REQUEST_FOCUS,"low light requests focus and exposure");
        check(s.onFrame(new HandheldScanCoordinator.Frame(500,123,.10,.12,.6,.9,.05)).action
            ==HandheldScanCoordinator.Action.WAIT,"brief settle window");
        check(s.onFrame(new HandheldScanCoordinator.Frame(900,123,.10,.12,.6,.9,.05)).action
            ==HandheldScanCoordinator.Action.ANALYZE,"usable but dim card gets OCR fallback");
        HandheldScanCoordinator t=new HandheldScanCoordinator();
        t.onFrame(new HandheldScanCoordinator.Frame(0,124,.02,.02,.6,.9,.05));
        check(t.onFrame(new HandheldScanCoordinator.Frame(900,124,.02,.02,.6,.9,.05)).action
            !=HandheldScanCoordinator.Action.ANALYZE,"unreadable darkness does not auto-save");
    }
    static void noDuplicateOnLightingOrPhoneMovement(){
        HandheldScanCoordinator s=new HandheldScanCoordinator();
        HandheldScanCoordinator.Decision first=s.onFrame(new HandheldScanCoordinator.Frame(
            0,0L,.8,.8,.55,.9,.05));
        PrintingEvidenceResolver.Result exact=PrintingEvidenceResolver.resolve(
            new PrintingEvidenceResolver.Evidence("Arbor Elf","wwk","95",.99,.99,.99,false,false),
            Collections.singletonList(new PrintingEvidenceResolver.Candidate(
                "wwk-95","Arbor Elf","wwk","95",.99,0,0)));
        check(s.mayCommit(first.generation,exact),"exact printing authorized");
        s.onRecognitionFinished(first.generation,true,20);
        long tilt=(1L<<18)-1; // visible movement but not proof of a new card
        for(int i=1;i<=5;i++)check(s.onFrame(new HandheldScanCoordinator.Frame(
            20+i*110,tilt,.8,.8,.55,.9,.03)).action==HandheldScanCoordinator.Action.WAIT,
            "camera tilt does not duplicate a saved card");
        s.onFrame(new HandheldScanCoordinator.Frame(700,0,.1,.1,0,0,0));
        s.onFrame(new HandheldScanCoordinator.Frame(890,0,.1,.1,0,0,0));
        check(s.onFrame(new HandheldScanCoordinator.Frame(990,0L,.8,.8,.55,.9,.05)).action
            ==HandheldScanCoordinator.Action.ANALYZE,"new physical copy after removal accepted");
    }
    static void rotation() {
        byte[] upright=new byte[3*4];
        for(int i=0;i<upright.length;i++)upright[i]=(byte)i;
        byte[] raw=FrameRotation.rotate(upright,3,4,270);
        check(raw.length==4*3,"quarter-turn changes image dimensions");
        check(Arrays.equals(FrameRotation.rotate(raw,4,3,90),upright),
            "CameraX 90-degree rotation restores upright card pixels");
        check(Arrays.equals(FrameRotation.rotate(FrameRotation.rotate(upright,3,4,180),3,4,180),upright),
            "CameraX 180-degree rotation roundtrip");
    }
    static void cameraRetries() throws Exception {
        final int[] attempts={0},reviews={0},saves={0};
        HandheldCameraXAnalyzer analyzer=new HandheldCameraXAnalyzer(
            (a,w,h,stride)->null, // explicitly exercise handheld edge fallback
            (capture,callback)->{
                attempts[0]++;
                check(capture.rotationDegrees==0 && capture.sourceRotationDegrees==90,
                    "perspective-corrected crop is not rotated again");
                check(capture.textRegions!=null && capture.textRegions.footer.enhanced.length>0,
                    "recognition receives both main crop and enhanced footer OCR region");
                if(attempts[0]==1)callback.onCompleted(null,Collections.emptyList());
                else callback.onCompleted(new PrintingEvidenceResolver.Evidence(
                    "Arbor Elf","wwk","95",.99,.98,.99,false,false),
                    Collections.singletonList(new PrintingEvidenceResolver.Candidate(
                    "arbor-wwk-95","Arbor Elf","wwk","95",.99,.0,.0)));
            },new HandheldCameraXAnalyzer.ResultSink(){
                public boolean save(PrintingEvidenceResolver.Candidate p,HandheldCameraXAnalyzer.Capture c){
                    saves[0]++;return true;
                }
                public void review(PrintingEvidenceResolver.Result r,HandheldCameraXAnalyzer.Capture c){reviews[0]++;}
                public void error(String msg){throw new AssertionError(msg);}
            },Runnable::run,null);
        byte[] rotatedSensor=FrameRotation.rotate(card(200,280),200,280,270);
        Proxy first=new Proxy(rotatedSensor);analyzer.analyze(first);
        check(first.closed,"camera frame immediately released");
        check(attempts[0]==1,"handheld card recognized despite old geometry detector missing it");
        check(reviews[0]==0,"no-printing results not sent to edition picker");
        Thread.sleep(320);
        Proxy second=new Proxy(rotatedSensor);analyzer.analyze(second);
        check(second.closed,"camera retry frame released");
        check(attempts[0]==2 && saves[0]==1,"automatic retry resolves exact printing");
        check(reviews[0]==0,"no manual intervention in retry and success");
        analyzer.close();
    }
    static void cameraSinglePrintingWithoutFooter() {
        final int[] saves={0},reviews={0},captures={0};
        final String[] reason={""};
        HandheldCameraXAnalyzer analyzer=new HandheldCameraXAnalyzer(
            (a,w,h,stride)->null,
            (capture,callback)->{
                captures[0]++;
                callback.onCompleted(new PrintingEvidenceResolver.Evidence(
                    "Test Single Printing","","",.995,0,0,false,false,
                    false,true,1,true),
                    Collections.singletonList(new PrintingEvidenceResolver.Candidate(
                        "single-1","Test Single Printing","set","123",.99,0,0)));
            },new HandheldCameraXAnalyzer.ResultSink(){
                public boolean save(PrintingEvidenceResolver.Candidate p,HandheldCameraXAnalyzer.Capture c){
                    saves[0]++;check("single-1".equals(p.printingId),"saved authoritative singleton id");return true;
                }
                public void review(PrintingEvidenceResolver.Result r,HandheldCameraXAnalyzer.Capture c){reviews[0]++;}
                public void error(String msg){throw new AssertionError(msg);}
                public void telemetry(String step,long ms,String detail){
                    if("verification".equals(step))reason[0]=detail;
                }
            },Runnable::run,null);
        byte[] sensor=FrameRotation.rotate(card(200,280),200,280,270);
        Proxy frame=new Proxy(sensor);analyzer.analyze(frame);
        check(frame.closed,"frame released after singleton scan");
        check(captures[0]==1 && saves[0]==1 && reviews[0]==0,
            "verified catalog singleton saves immediately without printing picker");
        check(reason[0].contains("unique eligible printing"),"diagnostic records single-printing auto-save");
        analyzer.close();
    }
    public static void main(String[] args) throws Exception {
        geometry();color();rotation();retries();poorLighting();noDuplicateOnLightingOrPhoneMovement();cameraRetries();cameraSinglePrintingWithoutFooter();
        System.out.println("PASS: "+checks+" handheld recovery / exact-save assertions");
    }
}
