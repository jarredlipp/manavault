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
    static final int CARD_WIDTH = 630;
    static final int CARD_HEIGHT = 880;
    static final class Detection {
        final PointF[] corners; // TL, TR, BR, BL (in the camera's upright coordinate system)
        final float confidence;
        final float areaFraction;
        Detection(PointF[] points, float confidence, float areaFraction) {
            this.corners = points; this.confidence = confidence; this.areaFraction = areaFraction;
        }
    }

    Detection find(Bitmap image) {
        Mat rgba = new Mat(), gray = new Mat(), blurred = new Mat(), edges = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        try {
            Utils.bitmapToMat(image, rgba);
            double scale = Math.min(1.0, 440.0 / Math.max(image.getWidth(), image.getHeight()));
            Mat small = new Mat();
            Imgproc.resize(rgba, small, new Size(), scale, scale, Imgproc.INTER_AREA);
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
            Imgproc.Canny(blurred, edges, 45, 130);
            Imgproc.dilate(edges, edges, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            double frameArea = small.rows() * small.cols();
            double bestScore = -1;
            Detection best = null;
            // A real card is usually the largest convincing 63:88 quadrilateral.
            // Artwork, type boxes, and surrounding fixtures should score lower.
            for (MatOfPoint contour : contours) {
                double area = Math.abs(Imgproc.contourArea(contour));
                if (area < frameArea * 0.17 || area > frameArea * 0.91) continue;
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
                // Prefer the outer card border; do not reward small intricate inner rectangles.
                double score = fraction * 2.8 - Math.abs(aspect - 88.0/63.0) * .65
                        - Math.abs(1-rectangularity) * .22;
                if (score > bestScore) {
                    bestScore = score;
                    PointF[] scaled = new PointF[4];
                    for (int i=0;i<4;i++) scaled[i]=new PointF((float)(ordered[i].x/scale),(float)(ordered[i].y/scale));
                    float confidence = (float)Math.max(0,Math.min(1, .42 + fraction*.8 - Math.abs(aspect-88.0/63.0)*.8));
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
        Mat src = new Mat(), dest = new Mat(), transform = new Mat();
        MatOfPoint2f in=null,out=null;
        try {
            Utils.bitmapToMat(image, src);
            in = new MatOfPoint2f(new Point(q[0].x,q[0].y), new Point(q[1].x,q[1].y),
                    new Point(q[2].x,q[2].y), new Point(q[3].x,q[3].y));
            out = new MatOfPoint2f(new Point(0,0),new Point(CARD_WIDTH-1,0),
                    new Point(CARD_WIDTH-1,CARD_HEIGHT-1),new Point(0,CARD_HEIGHT-1));
            transform = Imgproc.getPerspectiveTransform(in,out);
            Imgproc.warpPerspective(src,dest,transform,new Size(CARD_WIDTH,CARD_HEIGHT),Imgproc.INTER_LINEAR);
            Bitmap bmp=Bitmap.createBitmap(CARD_WIDTH,CARD_HEIGHT,Bitmap.Config.ARGB_8888);
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
            org.opencv.imgproc.CLAHE clahe=Imgproc.createCLAHE(2.2,new Size(8,4));
            clahe.apply(gray,enhanced);
            clahe.collectGarbage();
            Bitmap output=Bitmap.createBitmap(title.getWidth(),title.getHeight(),Bitmap.Config.ARGB_8888);
            Imgproc.cvtColor(enhanced,rgba,Imgproc.COLOR_GRAY2RGBA);
            Utils.matToBitmap(rgba,output);
            return output;
        } finally {rgba.release();gray.release();enhanced.release();}
    }
}
