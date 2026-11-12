package androidx.camera.core;
import androidx.annotation.NonNull;
public class ImageAnalysis {
 public static final int STRATEGY_KEEP_ONLY_LATEST=0;
 public static final int OUTPUT_IMAGE_FORMAT_YUV_420_888=1;
 public interface Analyzer {void analyze(@NonNull ImageProxy img);}
 public static class Builder {
   public Builder setBackpressureStrategy(int a){return this;}
   public Builder setOutputImageFormat(int a){return this;}
   public ImageAnalysis build(){return new ImageAnalysis();}
 }
}
