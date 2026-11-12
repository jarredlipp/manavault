import com.manavault.app.scanner.*;
import java.util.*;
import java.util.concurrent.Executor;

public final class ScannerStartupTest {
    static int assertions;
    static void check(boolean p,String s){assertions++;if(!p)throw new AssertionError(s);}
    static final class Deferred implements Executor {
        final Queue<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable r){tasks.add(r);}
        void drain(){while(!tasks.isEmpty())tasks.remove().run();}
    }
    public static void main(String[] args){
        Deferred d=new Deferred();ScannerStartupCoordinator s=new ScannerStartupCoordinator(d);
        final int[] loads={0},updates={0};
        s.beginScan();
        check(s.prewarm(()->loads[0]++),"first prewarm starts");
        check(!s.prewarm(()->loads[0]++),"duplicate startup suppressed");
        s.refreshWhenIdle(()->updates[0]++);
        s.refreshWhenIdle(()->updates[0]+=100);
        check(s.getActiveScans()==1,"scanner active");
        d.drain();
        check(loads[0]==1 && s.getState()==ScannerStartupCoordinator.State.READY,
            "one warm offline catalog ready");
        check(updates[0]==0,"catalog refresh deferred during camera scan");
        s.endScan();
        d.drain();
        check(updates[0]==100,"only latest deferred refresh executed when idle");
        check(!s.prewarm(()->loads[0]++),"ready catalog never reinitialized across sessions");
        s.beginScan();s.endScan();
        check(loads[0]==1,"reopening camera does not repeat warm startup");
        Deferred failExec=new Deferred();
        ScannerStartupCoordinator failed=new ScannerStartupCoordinator(failExec);
        failed.prewarm(()->{throw new IllegalStateException("load failed");});failExec.drain();
        check(failed.getState()==ScannerStartupCoordinator.State.FAILED,"failed loads marked for retry");
        check(failed.prewarm(()->{}),"failed warmup can retry");failExec.drain();
        check(failed.getState()==ScannerStartupCoordinator.State.READY,"warmup recovery");
        System.out.println("PASS: "+assertions+" startup reuse / deferred-refresh assertions");
    }
}
