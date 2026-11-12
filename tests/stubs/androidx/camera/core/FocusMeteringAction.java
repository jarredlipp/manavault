package androidx.camera.core;
import java.util.concurrent.TimeUnit;
public class FocusMeteringAction {
 public static final int FLAG_AF=1, FLAG_AE=2;
 public static class Builder {
   public Builder(Object p,int flags) {}
   public Builder setAutoCancelDuration(long t, TimeUnit units) {return this;}
   public FocusMeteringAction build(){return new FocusMeteringAction();}
 }
}
