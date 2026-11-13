import com.manavault.app.scanner.*;
import java.util.Arrays;

/** Regression tests for 15–23 second gaps caused by missed card transitions. */
public final class ScannerSceneGateTest {
    private static int assertions;
    private static void check(boolean ok,String why) {
        assertions++;if(!ok)throw new AssertionError(why);
    }
    private static HandheldScanCoordinator.Frame f(long time,long full,long title,long art,double movement) {
        return new HandheldScanCoordinator.Frame(time,full,.8,.8,.57,.9,movement,title,art);
    }
    private static void stableNewCardWithNoRemoval() {
        HandheldScanCoordinator s=new HandheldScanCoordinator();
        HandheldScanCoordinator.Decision first=s.onFrame(f(0,0,0,0,.02));
        check(first.action==HandheldScanCoordinator.Action.ANALYZE,"first scene is scanned");
        s.onRecognitionFinished(first.generation,true,30);
        // Same card with handheld motion: whole-card hash may change noticeably,
        // but artwork and title agree enough; it must NOT double-save.
        long angle=(1L<<18)-1;
        for(int i=0;i<4;i++)check(s.onFrame(f(120+i*100,angle,(1L<<5)-1,(1L<<12)-1,.04)).action
            ==HandheldScanCoordinator.Action.WAIT,"handheld angle shift is not a new card");
        // A new card may have similar border/colors, so global difference is
        // below the old 26-bit threshold, but independent title+art differ.
        long newFull=(1L<<14)-1;
        long newTitle=(1L<<19)-1;
        long newArt=(1L<<20)-1;
        check(s.onFrame(f(650,newFull,newTitle,newArt,.03)).action
            ==HandheldScanCoordinator.Action.WAIT,"new card requires temporal confirmation");
        check(s.onFrame(f(740,newFull,newTitle,newArt,.03)).action
            ==HandheldScanCoordinator.Action.WAIT,"second frame confirms new scene");
        HandheldScanCoordinator.Decision second=s.onFrame(f(840,newFull,newTitle,newArt,.03));
        check(second.action==HandheldScanCoordinator.Action.ANALYZE,
            "new card recognized without moving fully out of camera or waiting for many seconds");
        check(second.generation!=first.generation,"old async result cannot save new card");
    }
    private static void sameIllustrationNeedsPhysicalRemoval() {
        HandheldScanCoordinator s=new HandheldScanCoordinator();
        HandheldScanCoordinator.Decision first=s.onFrame(f(0,5,3,12,.02));
        s.onRecognitionFinished(first.generation,true,25);
        for(int i=0;i<5;i++)check(s.onFrame(f(100+i*90,5,3,12,.02)).action
            ==HandheldScanCoordinator.Action.WAIT,"identical image cannot create duplicate save");
        s.onFrame(new HandheldScanCoordinator.Frame(590,0,.1,.1,0,0,0));
        s.onFrame(new HandheldScanCoordinator.Frame(790,0,.1,.1,0,0,0));
        check(s.onFrame(f(880,5,3,12,.02)).action==HandheldScanCoordinator.Action.ANALYZE,
            "physical removal allows rescanning identical copies");
    }
    private static void titleArtRegions() {
        int w=144,h=202;
        byte[] a=new byte[w*h];Arrays.fill(a,(byte)160);
        for(int y=12;y<36;y++)for(int x=12;x<132;x++)
            a[y*w+x]=(byte)((x/10%2==0)?40:210);
        byte[] b=a.clone();
        for(int y=12;y<36;y++)for(int x=12;x<132;x++)
            b[y*w+x]=(byte)((x/10%2==0)?210:40);
        SceneFingerprint fa=SceneFingerprint.fromPreview(a,w,h);
        SceneFingerprint fb=SceneFingerprint.fromPreview(b,w,h);
        check(fa.titleHash!=fb.titleHash,"title region detects distinct card text patterns");
        check(fa.artworkHash==fb.artworkHash,"title-only change must not falsify artwork difference");
    }
    public static void main(String[] args) {
        stableNewCardWithNoRemoval();sameIllustrationNeedsPhysicalRemoval();titleArtRegions();
        System.out.println("PASS: "+assertions+" fast scene-transition / duplicate-safety assertions");
    }
}
