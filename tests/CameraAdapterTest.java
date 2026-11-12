import java.nio.ByteBuffer;
import java.util.*;
import com.manavault.app.scanner.*;
import androidx.camera.core.*;

/** Camera stub verifies immediate ImageProxy closure and exact-print save. */
public final class CameraAdapterTest {
    static final class Proxy implements ImageProxy {
        final int w=96,h=144;
        boolean closed;
        byte[] data=new byte[w*h];
        Proxy() {
            Random random=new Random(1024);
            for(int i=0;i<data.length;i++) data[i]=(byte)(30+random.nextInt(180));
        }
        public int getWidth(){return w;}
        public int getHeight(){return h;}
        public PlaneProxy[] getPlanes(){return new PlaneProxy[]{new PlaneProxy(){
            public ByteBuffer getBuffer(){return ByteBuffer.wrap(data);}
            public int getRowStride(){return w;}
            public int getPixelStride(){return 1;}
        }};}
        public ImageInfo getImageInfo(){return ()->0;}
        public void close(){closed=true;}
    }
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
    public static void main(String[] args) {
        final int[] saves={0}, reviews={0};
        HandheldCameraXAnalyzer.GeometryDetector detector=(y,w,h,stride)->
            new HandheldCameraXAnalyzer.Geometry(
                new PerspectiveRectifier.Point(0,0),
                new PerspectiveRectifier.Point(w-1,0),
                new PerspectiveRectifier.Point(w-1,h-1),
                new PerspectiveRectifier.Point(0,h-1),.85,.97);
        HandheldCameraXAnalyzer.RecognitionEngine recognition=(capture,finish)->finish.onCompleted(
            new PrintingEvidenceResolver.Evidence("Raggadragga, Goreguts Boss","clb","437",.99,.98,.96,true,true),
            Collections.singletonList(new PrintingEvidenceResolver.Candidate("exact-id",
                "Raggadragga, Goreguts Boss","clb","437",.99,.96,.99)));
        HandheldCameraXAnalyzer.ResultSink sink=new HandheldCameraXAnalyzer.ResultSink(){
            public boolean save(PrintingEvidenceResolver.Candidate c,HandheldCameraXAnalyzer.Capture capture){
                check("exact-id".equals(c.printingId),"correct card ID");saves[0]++;return true;
            }
            public void review(PrintingEvidenceResolver.Result r,HandheldCameraXAnalyzer.Capture c){reviews[0]++;}
            public void error(String r){throw new AssertionError(r);}
        };
        HandheldCameraXAnalyzer analyzer=new HandheldCameraXAnalyzer(detector,recognition,sink,Runnable::run,null);
        Proxy proxy=new Proxy();analyzer.analyze(proxy);
        check(proxy.closed,"camera frame closed immediately");
        check(saves[0]==1,"one exact automatic save");
        Proxy duplicate=new Proxy();analyzer.analyze(duplicate);
        check(duplicate.closed,"duplicate image closed");
        check(saves[0]==1,"duplicate physical card suppressed");
        check(reviews[0]==0,"no unnecessary manual review");
        analyzer.close();
        System.out.println("PASS: camera adapter release / save / duplicate assertions");
    }
}
