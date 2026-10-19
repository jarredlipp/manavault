package com.manavault.app;

import android.graphics.Bitmap;
import android.graphics.PointF;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Independent OpenCV outer-card detector. Coordinates are in the upright analysis bitmap. */
final class CardQuadDetector {
    static final int MIN_CARD_WIDTH = 630;
    static final int MAX_CARD_WIDTH = 1260;
    static final int CARD_WIDTH = MIN_CARD_WIDTH;
    static final int CARD_HEIGHT = 880;
    static final class Detection {
        final PointF[] corners; // TL, TR, BR, BL (in the camera's upright coordinate system)
        final float confidence;
        final float areaFraction;
        Detection(PointF[] points, float confidence, float areaFraction) {
            this.corners = points; this.confidence = confidence; this.areaFraction = areaFraction;
        }
    }

    Detection find(Bitmap image) { return detectWhole(image); }

    /** Track a recently validated outer border inside a padded local search.
     * Smaller contour searches are faster and less likely to lock to a text box.
     * A hint is advisory: full-frame detection remains the recovery path. */
    Detection find(Bitmap image, Detection hint) {
        if(hint!=null){
            float left=Float.MAX_VALUE,top=Float.MAX_VALUE,right=-1,bottom=-1;
            for(PointF p:hint.corners){left=Math.min(left,p.x);right=Math.max(right,p.x);
                top=Math.min(top,p.y);bottom=Math.max(bottom,p.y);}
            float padX=(right-left)*.25f,padY=(bottom-top)*.25f;
            int x=Math.max(0,(int)Math.floor(left-padX));
            int y=Math.max(0,(int)Math.floor(top-padY));
            int r=Math.min(image.getWidth(),(int)Math.ceil(right+padX));
            int b=Math.min(image.getHeight(),(int)Math.ceil(bottom+padY));
            if(r-x>100&&b-y>100){
                Bitmap roi=null;
                try{
                    roi=Bitmap.createBitmap(image,x,y,r-x,b-y);
                    Detection d=detectWhole(roi);
                    if(d!=null){
                        PointF[] corners=new PointF[4];
                        for(int i=0;i<4;i++)corners[i]=new PointF(d.corners[i].x+x,d.corners[i].y+y);
                        float area=d.areaFraction*(r-x)*(b-y)/(float)(image.getWidth()*image.getHeight());
                        Detection candidate=new Detection(corners,d.confidence,area);
                        if(area>hint.areaFraction*.86f&&area<hint.areaFraction*1.22f &&
                            distance(candidate,hint,image.getWidth(),image.getHeight())<.075f)
                            return candidate;
                    }
                }catch(Exception ignored){}
                finally{if(roi!=null)roi.recycle();}
            }
        }
        return detectWhole(image);
    }
    private static float distance(Detection a,Detection b,int w,int h){
        double d=0;for(int i=0;i<4;i++)d+=Math.hypot(a.corners[i].x-b.corners[i].x,a.corners[i].y-b.corners[i].y);
        return (float)(d/4/Math.hypot(w,h));
    }
    private Detection detectWhole(Bitmap image) {
        Mat rgba = new Mat(), gray = new Mat(), blurred = new Mat(), edges = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        try {
            Utils.bitmapToMat(image, rgba);
            double scale = Math.min(1.0, 380.0 / Math.max(image.getWidth(), image.getHeight()));
            Mat small = new Mat();
            Imgproc.resize(rgba, small, new Size(), scale, scale, Imgproc.INTER_AREA);
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
            // Dark cards against a dark stand need a lower Canny threshold.
            // Uses the existing 380px image (no extra full-resolution pass).
            double mean=Core.mean(gray).val[0];
            Imgproc.Canny(blurred,edges,mean<82?28:45,mean<82?88:130);
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3));
            Imgproc.dilate(edges,edges,kernel);kernel.release();
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            byte[] edgePixels=new byte[(int)edges.total()];edges.get(0,0,edgePixels);
            double frameArea = small.rows() * small.cols();
            double bestScore = -1;
            Detection best = null;
            // A real card is usually the largest convincing 63:88 quadrilateral.
            // Allow cards occupying ~8% of analysis frame; full-screen CameraX
            // often previews cards at a smaller scale than the earlier WebView.
            // Artwork, type boxes, and surrounding fixtures should score lower.
            for (MatOfPoint contour : contours) {
                double area = Math.abs(Imgproc.contourArea(contour));
                if (area < frameArea * 0.075 || area > frameArea * 0.91) continue;
                MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approx = new MatOfPoint2f();
                Imgproc.approxPolyDP(curve, approx, .025 * Imgproc.arcLength(curve, true), true);
                if (approx.total() != 4) {curve.release();approx.release();continue;}
                MatOfPoint approxInt = new MatOfPoint(approx.toArray());
                boolean convex = Imgproc.isContourConvex(approxInt);
                approxInt.release();
                if (!convex) {curve.release();approx.release();continue;}
                Point[] ordered = order(approx.toArray());
                curve.release();approx.release();
                if (ordered == null) continue;
                double a = length(ordered[0], ordered[1]), b = length(ordered[1], ordered[2]);
                double c = length(ordered[2], ordered[3]), d = length(ordered[3], ordered[0]);
                if (Math.min(Math.min(a,b),Math.min(c,d)) < 36) continue;
                double aspect = Math.max((a+c)/2, (b+d)/2) / Math.min((a+c)/2, (b+d)/2);
                if (aspect < 1.25 || aspect > 1.62) continue;
                if (Math.max(a,c)/Math.min(a,c) > 1.29 || Math.max(b,d)/Math.min(b,d) > 1.29) continue;
                double margin = Math.min(small.cols(), small.rows()) * .012;
                if (Arrays.stream(ordered).anyMatch(p -> p.x < margin || p.x > small.cols()-margin || p.y < margin || p.y > small.rows()-margin)) continue;
                double rectangularity = area / Math.max(1, ((a+c)/2)*((b+d)/2));
                if (rectangularity < .78 || rectangularity > 1.20) continue;
                double fraction = area/frameArea;
                // A card often has a second rectangular frame INSIDE it: the
                // illustration. If a bigger, parallel rectangular perimeter
                // is visible 5-15% outside this candidate, it is not the card
                // border. This doesn't require dark borders or extra OCR.
                if(hasEnclosingCardEdge(edgePixels,edges.cols(),edges.rows(),ordered))continue;
                // Prefer the outer card border; do not reward small intricate inner rectangles.
                double score = fraction * 2.8 - Math.abs(aspect - 88.0/63.0) * .65
                        - Math.abs(1-rectangularity) * .22;
                if (score > bestScore) {
                    bestScore = score;
                    PointF[] scaled = new PointF[4];
                    for (int i=0;i<4;i++) scaled[i]=new PointF((float)(ordered[i].x/scale),(float)(ordered[i].y/scale));
                    // Confidence must remain consistent with the 7.5%-area floor;
                    // the previous .42 base rejected every valid card below ~19%
                    // of the analysis frame, even after lowering the area limit.
                    float confidence = (float)Math.max(0,Math.min(1,
                            .52 + fraction*.90 - Math.abs(aspect-88.0/63.0)*.85));
                    best = new Detection(scaled, confidence, (float)fraction);
                }
            }
            small.release();
            return best;
        } finally {
            for (MatOfPoint contour : contours) contour.release();
            hierarchy.release();edges.release();blurred.release();gray.release();rgba.release();
        }
    }

    /** Detect a distinct larger rectangular outline enclosing an artwork box.
     * Sample a few expansions of the proposed quadrilateral; only reject when
     * opposite outer sides have strong Canny support. This is intentionally
     * conservative for black cards on dark tabletops. */
    static boolean hasEnclosingCardEdge(byte[] pixels,int width,int height, Point[] quad){
        for(double scale:new double[]{1.07,1.12,1.17}){
            double cx=0,cy=0;for(Point q:quad){cx+=q.x*.25;cy+=q.y*.25;}
            Point[] outer=new Point[4];boolean onScreen=true;
            for(int i=0;i<4;i++){
                double x=cx+(quad[i].x-cx)*scale,y=cy+(quad[i].y-cy)*scale;
                outer[i]=new Point(x,y);
                if(x<3||y<3||x>=width-3||y>=height-3)onScreen=false;
            }
            if(!onScreen)continue;
            int strong=0;
            for(int side=0;side<4;side++){
                Point a=outer[side],b=outer[(side+1)%4];int pixels=0;
                for(int i=1;i<=24;i++){
                    int x=(int)Math.round(a.x+(b.x-a.x)*i/25.0);
                    int y=(int)Math.round(a.y+(b.y-a.y)*i/25.0);
                    boolean edge=false;
                    for(int dy=-2;dy<=2&&!edge;dy++)for(int dx=-2;dx<=2;dx++){
                        if(pixels[(y+dy)*width+x+dx]!=0){edge=true;break;}
                    }
                    if(edge)pixels++;
                }
                if(pixels>=12)strong++;
            }
            if(strong>=3)return true;
        }
        return false;
    }
    private static double length(Point a, Point b) {return Math.hypot(a.x-b.x,a.y-b.y);}
    private static Point[] order(Point[] points) {
        if (points.length!=4) return null;
        Point[] sorted=points.clone();
        Arrays.sort(sorted,Comparator.comparingDouble(p -> p.y));
        Point[] top={sorted[0],sorted[1]}, bottom={sorted[2],sorted[3]};
        Arrays.sort(top,Comparator.comparingDouble(p -> p.x));
        Arrays.sort(bottom,Comparator.comparingDouble(p -> p.x));
        return new Point[]{top[0],top[1],bottom[1],bottom[0]};
    }

    /** Rectify either portrait or landscape card, always to portrait proportions. */
    Bitmap rectify(Bitmap image, Detection detection) {
        PointF[] p = detection.corners;
        double horizontal=Math.hypot(p[1].x-p[0].x,p[1].y-p[0].y);
        double vertical=Math.hypot(p[3].x-p[0].x,p[3].y-p[0].y);
        // For sideways cards, turn the short edge into the destination's top edge.
        PointF[] q=horizontal>vertical?new PointF[]{p[3],p[0],p[1],p[2]}:p;
        // Preserve the pixels captured by the phone. The old fixed 630x880
        // warp destroyed the extra collector-number detail from higher-res frames.
        double top=Math.hypot(q[1].x-q[0].x,q[1].y-q[0].y);
        double bottom=Math.hypot(q[2].x-q[3].x,q[2].y-q[3].y);
        double sideL=Math.hypot(q[3].x-q[0].x,q[3].y-q[0].y);
        double sideR=Math.hypot(q[2].x-q[1].x,q[2].y-q[1].y);
        double measuredWidth=Math.min((top+bottom)/2., (sideL+sideR)/2.*63./88.);
        int outWidth=(int)Math.max(MIN_CARD_WIDTH,Math.min(MAX_CARD_WIDTH,Math.round(measuredWidth)));
        int outHeight=(int)Math.round(outWidth*88./63.);
        Mat src = new Mat(), dest = new Mat(), transform = new Mat();
        MatOfPoint2f in=null,out=null;
        try {
            Utils.bitmapToMat(image, src);
            in = new MatOfPoint2f(new Point(q[0].x,q[0].y), new Point(q[1].x,q[1].y),
                    new Point(q[2].x,q[2].y), new Point(q[3].x,q[3].y));
            out = new MatOfPoint2f(new Point(0,0),new Point(outWidth-1,0),
                    new Point(outWidth-1,outHeight-1),new Point(0,outHeight-1));
            transform = Imgproc.getPerspectiveTransform(in,out);
            Imgproc.warpPerspective(src,dest,transform,new Size(outWidth,outHeight),Imgproc.INTER_LINEAR);
            Bitmap bmp=Bitmap.createBitmap(outWidth,outHeight,Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(dest,bmp);
            return bmp;
        } finally {if(in!=null)in.release();if(out!=null)out.release();transform.release();src.release();dest.release();}
    }

    /** Local CLAHE enhancement only; full card remains unchanged for artwork checks. */
    static Bitmap enhanceTitle(Bitmap title) {
        Mat rgba=new Mat(), gray=new Mat(), enhanced=new Mat();
        try {
            Utils.bitmapToMat(title,rgba);
            Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            // Only the small OCR strip is enhanced, never full-card art or
            // collector reference photographs. Dark images tend to amplify
            // sensor noise with standard CLAHE; lightly denoise first.
            double luma=Core.mean(gray).val[0];
            if(luma<82)Imgproc.GaussianBlur(gray,gray,new Size(3,3),.65);
            org.opencv.imgproc.CLAHE clahe=Imgproc.createCLAHE(luma<82?2.7:2.2,new Size(8,4));
            clahe.apply(gray,enhanced);
            clahe.collectGarbage();
            Bitmap output=Bitmap.createBitmap(title.getWidth(),title.getHeight(),Bitmap.Config.ARGB_8888);
            Imgproc.cvtColor(enhanced,rgba,Imgproc.COLOR_GRAY2RGBA);
            Utils.matToBitmap(rgba,output);
            return output;
        } finally {rgba.release();gray.release();enhanced.release();}
    }
}
