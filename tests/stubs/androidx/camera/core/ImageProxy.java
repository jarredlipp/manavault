package androidx.camera.core;
import java.nio.ByteBuffer;
public interface ImageProxy {
 int getWidth(); int getHeight(); PlaneProxy[] getPlanes(); ImageInfo getImageInfo();void close();
 interface PlaneProxy {ByteBuffer getBuffer();int getRowStride();int getPixelStride();}
 interface ImageInfo {int getRotationDegrees();}
}
