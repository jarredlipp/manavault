import com.manavault.app.scanner.*;
import java.util.*;

/** No JUnit dependency. Run tests/run-core-tests.sh */
public final class ScannerCoreTest {
    static int n=0;
    static void check(boolean p,String message) {n++;if(!p) throw new AssertionError(message);}
    static PrintingEvidenceResolver.Candidate c(String id, String set, String collector, double art,double symbol) {
        return new PrintingEvidenceResolver.Candidate(id,"Raggadragga, Goreguts Boss",set,collector,1,art,symbol);
    }
    static PrintingEvidenceResolver.Evidence e(String set,String col,boolean symbol,boolean art) {
        return new PrintingEvidenceResolver.Evidence("Raggadragga, Goreguts Boss",set,col,0.98,0.96,0.95,symbol,art);
    }
    static void printing() {
        List<PrintingEvidenceResolver.Candidate> cards = Arrays.asList(
            c("a","clb","437",.97,.99), c("b","clb","437",.97,.98), c("c","clb","200",.9,.99));
        // Same set and collector cannot identify between two printings: must review.
        check(PrintingEvidenceResolver.resolve(e("CLB","437",true,true), cards).verdict
            == PrintingEvidenceResolver.Verdict.REVIEW,"same collector alternatives must review");
        List<PrintingEvidenceResolver.Candidate> unique = Arrays.asList(
            c("a","clb","437",.97,.99), c("b","clb","200",.9,.67));
        PrintingEvidenceResolver.Result verified=PrintingEvidenceResolver.resolve(e("clb","0437",true,true),unique);
        check(verified.verdict == PrintingEvidenceResolver.Verdict.AUTO_SAVE,"set+collector verify");
        check("a".equals(verified.best.printingId),"correct printing chosen");
        check(PrintingEvidenceResolver.resolve(e("","437",true,true),unique).verdict
            == PrintingEvidenceResolver.Verdict.AUTO_SAVE,"collector plus strong set symbol verify");
        check(PrintingEvidenceResolver.resolve(e("","",true,true),unique).verdict
            == PrintingEvidenceResolver.Verdict.REVIEW,"art and symbol without collector must not auto save");
        check(PrintingEvidenceResolver.resolve(e("clb","555",true,true),unique).verdict
            == PrintingEvidenceResolver.Verdict.RETRY,"contradictory number must retry");
        check(PrintingEvidenceResolver.resolve(e("","",false,true),Collections.singletonList(unique.get(0))).verdict
            == PrintingEvidenceResolver.Verdict.REVIEW,"one catalog result is not proof");
        check(PrintingEvidenceResolver.resolve(e("invalid","437",true,true),unique).verdict
            == PrintingEvidenceResolver.Verdict.RETRY,"strong contradictory set fails");
        PrintingEvidenceResolver.Evidence completeVisual=new PrintingEvidenceResolver.Evidence(
            "Raggadragga, Goreguts Boss","","",.99,0,0,true,true,true,true);
        PrintingEvidenceResolver.Evidence missingVisualCoverage=new PrintingEvidenceResolver.Evidence(
            "Raggadragga, Goreguts Boss","","",.99,0,0,true,true,false,true);
        List<PrintingEvidenceResolver.Candidate> visuallyDistinct=Arrays.asList(
            c("one","clb","437",.99,.99),c("two","clb","200",.30,.98),
            c("three","other","4",.45,.40));
        check(PrintingEvidenceResolver.resolve(completeVisual,visuallyDistinct).verdict
            == PrintingEvidenceResolver.Verdict.AUTO_SAVE,
            "complete observed symbol plus unique art resolves printing without footer");
        check(PrintingEvidenceResolver.resolve(missingVisualCoverage,visuallyDistinct).verdict
            == PrintingEvidenceResolver.Verdict.REVIEW,
            "incomplete visual index never fakes uniqueness");
        List<PrintingEvidenceResolver.Candidate> visuallyShared=Arrays.asList(
            c("one","clb","437",.99,.99),c("two","clb","200",.97,.98));
        check(PrintingEvidenceResolver.resolve(completeVisual,visuallyShared).verdict
            == PrintingEvidenceResolver.Verdict.REVIEW,
            "shared artwork and near-identical symbol need manual printing review");

    }
    static HandheldScanCoordinator.Frame frame(long ms,long hash,double sharp,double coverage) {
        return new HandheldScanCoordinator.Frame(ms,hash,sharp,.75,coverage,.85,.1);
    }
    static void coordinator() {
        HandheldScanCoordinator s=new HandheldScanCoordinator();
        check(s.onFrame(frame(0,0,.1,.45)).action==HandheldScanCoordinator.Action.REQUEST_FOCUS,"blur calls focus");
        HandheldScanCoordinator.Decision a=s.onFrame(frame(75,0,.78,.45));
        check(a.action==HandheldScanCoordinator.Action.ANALYZE,"first sharp handheld frame analyzed");
        check(s.onFrame(frame(100,0,.78,.45)).action==HandheldScanCoordinator.Action.WAIT,"no duplicate in-flight OCR");
        long old=a.generation;
        // A new card is seen across multiple frames while recognition is running.
        check(s.onFrame(frame(250,-1,.8,.6)).action==HandheldScanCoordinator.Action.WAIT,"first scene candidate");
        check(s.onFrame(frame(370,-1,.8,.6)).action==HandheldScanCoordinator.Action.WAIT,"not enough frames for scene change");
        HandheldScanCoordinator.Decision b=s.onFrame(frame(480,-1,.8,.6));
        check(b.action==HandheldScanCoordinator.Action.ANALYZE,"new scene processes without waiting old OCR");
        check(!s.mayCommit(old, PrintingEvidenceResolver.resolve(e("clb","437",true,true),
            Arrays.asList(c("a","clb","437",.97,.99)))),"late old-card result blocked");
        PrintingEvidenceResolver.Result valid=PrintingEvidenceResolver.resolve(e("clb","437",true,true),
            Arrays.asList(c("a","clb","437",.97,.99)));
        check(s.mayCommit(b.generation,valid),"new scene can save exact printing");
        s.onRecognitionFinished(b.generation,true);
        check(s.onFrame(frame(520,-1,.9,.6)).action==HandheldScanCoordinator.Action.WAIT,"prevent duplicate same card");
        // Lift and replace a physically different copy with the same artwork.
        s.onFrame(frame(550,0,.1,0));
        s.onFrame(frame(750,0,.1,0));
        check(s.onFrame(frame(800,-1,.9,.6)).action==HandheldScanCoordinator.Action.ANALYZE,
            "same-art copy recognized after removal");
        check(s.getAcceptedFrames()==3,"only three usable scenes analyzed");
        s.suspendForReview(s.currentGeneration());
        check(s.onFrame(frame(900,-1,.9,.6)).action==HandheldScanCoordinator.Action.WAIT,"review not repeatedly reopened");
        s.reset();
        check(s.onFrame(frame(1000,-1,.8,.5)).action==HandheldScanCoordinator.Action.ANALYZE,"reset recovers");
    }
    static void rectifier() {
        int w=80,h=110;
        byte[] image=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)image[y*w+x]=(byte)((x+3*y)%256);
        PerspectiveRectifier.Point tl=new PerspectiveRectifier.Point(10,10);
        PerspectiveRectifier.Point tr=new PerspectiveRectifier.Point(60,10);
        PerspectiveRectifier.Point br=new PerspectiveRectifier.Point(60,90);
        PerspectiveRectifier.Point bl=new PerspectiveRectifier.Point(10,90);
        byte[] out=PerspectiveRectifier.rectify(image,w,h,w,tl,tr,br,bl,26,41);
        check((out[0]&255)==40,"top-left normalized to source");
        check(Math.abs((out[out.length-1]&255)-((60+3*90)%256))<=1,"bottom-right normalized to source");
        check(out.length==26*41,"expected crop dimensions");
        byte[] flat=new byte[64*64];Arrays.fill(flat,(byte)126);
        FrameQualityEstimator.Stats stats=FrameQualityEstimator.estimate(flat,64,64,64,null);
        check(stats.sharpness<.01,"blur/flat frame rejected");
        check(stats.exposure>.8,"normal illumination passes");
        check(stats.motion==0,"first frame no artificial motion");
    }
    public static void main(String[] args){printing();coordinator();rectifier();System.out.println("PASS: "+n+" scanner core assertions");}
}
