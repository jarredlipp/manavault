package com.manavault.app;

/** Thread-safe physical-scene / durable-save handshake for a one-card-at-a-time
 * camera scanner. Monitoring the scene never grants permission to advance
 * until the exact capture ID has a final save/retry/skip receipt. No queue of
 * unpersisted cards is created. Times are elapsedRealtime milliseconds. */
final class ScanCycleCoordinator {
    private String pendingId="";
    private long captureAt=0, sceneAt=0, receiptAt=0, previousCaptureAt=0;
    private boolean sceneReady=false, receiptReady=false;
    private long captureGapMs=-1;

    synchronized void captured(String scanId,long now){
        if(scanId==null||scanId.isEmpty())throw new IllegalArgumentException("scan ID required");
        captureGapMs=previousCaptureAt==0?-1:Math.max(0,now-previousCaptureAt);
        previousCaptureAt=now;
        pendingId=scanId;captureAt=now;sceneAt=receiptAt=0;
        sceneReady=false;receiptReady=false;
    }
    /** Return true when the save receipt already arrived; otherwise latch the
     * scene and remain blocked. Called only after StackChangeGate approves it. */
    synchronized boolean observedNewScene(long now){
        if(pendingId.isEmpty()||sceneReady)return false;
        sceneReady=true;sceneAt=now;
        return receiptReady;
    }
    /** Stale and duplicate receipts never unlock a newer scan. */
    synchronized boolean receivedFinalReceipt(String scanId,long now){
        if(pendingId.isEmpty()||!pendingId.equals(scanId)||receiptReady)return false;
        receiptAt=now;receiptReady=true;
        return sceneReady;
    }
    synchronized boolean hasSceneReady(){return sceneReady;}
    synchronized long sceneAt(){return sceneAt;}
    synchronized boolean awaitingReceipt(){return !pendingId.isEmpty()&&!receiptReady;}
    synchronized String pendingId(){return pendingId;}
    synchronized long captureGapMs(){return captureGapMs;}
    synchronized long persistLatencyMs(){return receiptAt==0?-1:Math.max(0,receiptAt-captureAt);}
    /** Positive value indicates idle time after persistence; zero indicates
     * the next scene was already recognized while saving the previous card. */
    synchronized long postSaveSceneWaitMs(){
        if(receiptAt==0||sceneAt==0)return -1;
        return Math.max(0,sceneAt-receiptAt);
    }
    synchronized boolean scenePrearmedBeforeReceipt(){return sceneReady&&receiptAt>0&&sceneAt<=receiptAt;}
    synchronized void clearPending(){
        pendingId="";captureAt=sceneAt=receiptAt=0;sceneReady=receiptReady=false;
    }
}
