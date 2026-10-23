package com.manavault.app;

/** Guarantees that new-card scenes cannot outrun previous-card persistence. */
public final class ScanCycleCoordinatorTest {
    static void ok(boolean good,String why){if(!good)throw new AssertionError(why);}
    public static void main(String[] args){
        ScanCycleCoordinator c=new ScanCycleCoordinator();
        c.captured("session-1",1000);
        ok(c.awaitingReceipt(),"Receipt must start pending");
        ok(!c.observedNewScene(1200),"Scene cannot overtake persistence");
        ok(!c.observedNewScene(1210),"Duplicate scene accepted");
        ok(!c.receivedFinalReceipt("session-0",1250),"Stale receipt unlocked");
        ok(c.receivedFinalReceipt("session-1",1400),"Prearmed scene did not unlock");
        ok(c.scenePrearmedBeforeReceipt(),"Prearm timing was lost");
        ok(c.persistLatencyMs()==400,"Persistence ms wrong");
        ok(c.postSaveSceneWaitMs()==0,"Prearmed scene should have zero post-save wait");
        ok(!c.receivedFinalReceipt("session-1",1500),"Duplicate receipt unlocked");
        c.clearPending();
        c.captured("session-2",1700);
        ok(c.captureGapMs()==700,"Capture interval wrong");
        ok(!c.receivedFinalReceipt("session-1",1800),"Old receipt after next capture unlocked");
        ok(!c.receivedFinalReceipt("session-2",2000),"Receipt must wait for new scene");
        ok(c.observedNewScene(2150),"Post-save scene did not unlock");
        ok(!c.scenePrearmedBeforeReceipt(),"Scene after receipt incorrectly prearmed");
        ok(c.postSaveSceneWaitMs()==150,"Post-save idle measurement wrong");
        c.clearPending();
        ok(!c.observedNewScene(2200),"Scene accepted without pending capture");
        // A false visual scene transition must not produce a duplicate save.
        // Reuse the real previous receipt, wait for a genuinely new scene.
        c.rearmConfirmedPrevious("session-2",2250);
        ok(!c.awaitingReceipt(),"Already persisted copy must not wait on a new receipt");
        ok(c.observedNewScene(2300),"New physical scene cannot advance after rejected duplicate");
        c.clearPending();
        System.out.println("ScanCycleCoordinator JVM tests passed: prearming, ordering, stale receipts, gaps and post-save latency.");
    }
}
