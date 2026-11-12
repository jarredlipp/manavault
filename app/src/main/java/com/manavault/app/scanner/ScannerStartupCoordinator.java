package com.manavault.app.scanner;

import java.util.concurrent.Executor;

/**
 * Process-scoped gate for the native printing engine and background card-data
 * refresh. Start the actual app's existing loaders once, before opening CameraX.
 * This does NOT replace or implement the offline Scryfall/catalog/artwork loader.
 *
 * Scanner lifecycle must call beginScan/endScan so WebView/bulk updates cannot
 * start while a camera scan is active. Use a shared instance, not one per Activity.
 */
public final class ScannerStartupCoordinator {
    public enum State { IDLE, LOADING, READY, FAILED }
    private final Executor worker;
    private final Object lock=new Object();
    private volatile State state=State.IDLE;
    private int activeScans;
    private Runnable pendingRefresh;
    private long warmupRuns;
    private long refreshRuns;
    private String lastError="";

    public ScannerStartupCoordinator(Executor worker) {
        if(worker==null)throw new IllegalArgumentException("worker required");
        this.worker=worker;
    }
    /** Must be supplied with the application's REAL existing catalog/art loaders. */
    public boolean prewarm(Runnable actualExistingLoader) {
        if(actualExistingLoader==null)throw new IllegalArgumentException("loader required");
        synchronized(lock) {
            if(state==State.LOADING || state==State.READY)return false;
            state=State.LOADING;warmupRuns++;
        }
        try {
            worker.execute(()->{
                State next=State.READY;
                String error="";
                try { actualExistingLoader.run(); }
                catch(Throwable t) { next=State.FAILED;error=String.valueOf(t); }
                synchronized(lock) {state=next;lastError=error;}
                drainIdleRefresh();
            });
            return true;
        } catch(RuntimeException ex) {
            synchronized(lock) {state=State.FAILED;lastError=String.valueOf(ex);}
            throw ex;
        }
    }
    public void beginScan() {synchronized(lock){activeScans++;}}
    public void endScan() {
        synchronized(lock){if(activeScans==0)throw new IllegalStateException("unmatched scan end");activeScans--;}
        drainIdleRefresh();
    }
    /** The most recent pending refresh replaces older redundant bulk refreshes. */
    public void refreshWhenIdle(Runnable refresh) {
        if(refresh==null)throw new IllegalArgumentException("refresh required");
        synchronized(lock) {pendingRefresh=refresh;}
        drainIdleRefresh();
    }
    private void drainIdleRefresh() {
        Runnable task;
        synchronized(lock) {
            if(activeScans>0 || state==State.LOADING || pendingRefresh==null)return;
            task=pendingRefresh;pendingRefresh=null;refreshRuns++;
        }
        // The loader is responsible for its own persistent storage locks.
        try {worker.execute(task);}
        catch(RuntimeException ex) {
            synchronized(lock) {if(pendingRefresh==null)pendingRefresh=task;refreshRuns--;}
            throw ex;
        }
    }
    public State getState(){return state;}
    public int getActiveScans(){synchronized(lock){return activeScans;}}
    public long getWarmupRuns(){synchronized(lock){return warmupRuns;}}
    public long getRefreshRuns(){synchronized(lock){return refreshRuns;}}
    public String getLastError(){synchronized(lock){return lastError;}}
}
