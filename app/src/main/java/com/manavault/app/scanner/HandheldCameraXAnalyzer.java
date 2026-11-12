package com.manavault.app.scanner;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraControl;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Backpressure-safe CameraX analyzer for handheld AND stand-based cards.
 *
 * - Keep only the latest camera frame; avoid blocking the preview on OCR.
 * - Cheap small-card quality/presence checks BEFORE full perspective correction.
 * - Retain RGB for artwork matching only when we actually attempt recognition.
 * - Unrecognized cards RETRY automatically; printing review is for real candidates.
 * - Exact printing and durable saves stay with the existing app's adapters.
 */
public final class HandheldCameraXAnalyzer implements ImageAnalysis.Analyzer {
    public interface GeometryDetector {
        /** Return null when no card is found; luma corners must be TL, TR, BR, BL. */
        Geometry detect(byte[] luma,int width,int height,int stride);
    }
    public static final class Geometry {
        public final PerspectiveRectifier.Point tl,tr,br,bl;
        public final double cardCoverage,confidence;
        public Geometry(PerspectiveRectifier.Point tl,PerspectiveRectifier.Point tr,
                PerspectiveRectifier.Point br,PerspectiveRectifier.Point bl,
                double coverage,double confidence) {
            this.tl=tl;this.tr=tr;this.br=br;this.bl=bl;
            cardCoverage=coverage;this.confidence=confidence;
        }
    }
    public static final class Capture {
        public final byte[] rectifiedGray;
        /** Optional packed RGB: use for artwork/alternate-art and multicolor symbols. */
        public final byte[] rectifiedRgb;
        public final int width,height,rotationDegrees,sourceRotationDegrees;
        public final long generation,timestampMs;
        public Capture(byte[] gray,int w,int h,int rot,long gen,long ms) {
            this(gray,null,w,h,rot,gen,ms);
        }
        public Capture(byte[] gray,byte[] rgb,int w,int h,int sourceRot,long gen,long ms) {
            rectifiedGray=gray;rectifiedRgb=rgb;width=w;height=h;
            // The rectangle has already been oriented TL/TR/BR/BL. Never rotate it again.
            rotationDegrees=0;sourceRotationDegrees=sourceRot;
            generation=gen;timestampMs=ms;
        }
    }
    public interface RecognitionEngine {
        /** Never wait for network or OCR here. Call back once with measured evidence. */
        void recognize(Capture capture,Completed callback);
    }
    public interface Completed {
        void onCompleted(PrintingEvidenceResolver.Evidence evidence,
                         List<PrintingEvidenceResolver.Candidate> candidates);
    }
    public interface ResultSink {
        /** Return true only after durable collection/deck write succeeds. */
        boolean save(PrintingEvidenceResolver.Candidate printing,Capture capture);
        void review(PrintingEvidenceResolver.Result result,Capture capture);
        void error(String message);
        /** Non-blocking UI cue; should never open a picker or pause the preview. */
        default void notice(String message) {}
        /** Optional structured scanner timings in production diagnostic logs. */
        default void telemetry(String step,long elapsedMs,String detail) {}
    }

    private static final int PREVIEW_W=144,PREVIEW_H=202;
    private static final int OCR_W=540,OCR_H=756;
    private static final long FRAME_INTERVAL_MS=95;
    private final GeometryDetector detector;
    private final GeometryDetector fallbackDetector=new AdaptiveCardGeometryDetector();
    private final RecognitionEngine recognition;
    private final ResultSink sink;
    private final Executor serialExecutor;
    private final HandheldScanCoordinator state=new HandheldScanCoordinator();
    private final CameraControl control;
    private Long previousHash;
    private volatile boolean closed;
    private long lastSampleMs=Long.MIN_VALUE/2,lastNoticeMs=Long.MIN_VALUE/2;

    public HandheldCameraXAnalyzer(GeometryDetector detector,RecognitionEngine recognition,
            ResultSink sink,Executor serialExecutor,CameraControl control) {
        if(detector==null||recognition==null||sink==null||serialExecutor==null)
            throw new IllegalArgumentException("missing scanner dependency");
        this.detector=detector;this.recognition=recognition;this.sink=sink;
        this.serialExecutor=serialExecutor;this.control=control;
    }
    public static ImageAnalysis latestFrameAnalysis() {
        return new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build();
    }
    @Override public void analyze(@NonNull ImageProxy image) {
        final long time=System.nanoTime()/1_000_000;
        if(closed||time-lastSampleMs<FRAME_INTERVAL_MS) {image.close();return;}
        lastSampleMs=time;
        final long start=time;
        final int rawW=image.getWidth(),rawH=image.getHeight();
        final int rotation=image.getImageInfo().getRotationDegrees();
        final int width=FrameRotation.uprightWidth(rawW,rawH,rotation);
        final int height=FrameRotation.uprightHeight(rawW,rawH,rotation);
        byte[] luma=null;
        Geometry geometry=null;
        PerspectiveRectifier.Warp warp=null;
        HandheldScanCoordinator.Decision decision=null;
        ColorCardRectifier.Plane u=null,v=null;
        try {
            byte[] raw=copyLuma(image.getPlanes()[0],rawW,rawH);
            luma=FrameRotation.rotate(raw,rawW,rawH,rotation);
            geometry=detector.detect(luma,width,height,width);
            if(geometry==null || geometry.confidence<.36) {
                Geometry inferred=fallbackDetector.detect(luma,width,height,width);
                if(inferred!=null)geometry=inferred;
            }
            if(geometry==null) {
                state.onFrame(new HandheldScanCoordinator.Frame(time,0,0,0,0,0,0));
                previousHash=null;
                return;
            }
            warp=PerspectiveRectifier.warp(geometry.tl,geometry.tr,geometry.br,geometry.bl);
            // Only ~29K pixels per frame, NOT the full ~408K OCR crop.
            byte[] tiny=PerspectiveRectifier.rectify(luma,width,height,width,warp,PREVIEW_W,PREVIEW_H);
            FrameQualityEstimator.Stats stats=FrameQualityEstimator.estimate(
                tiny,PREVIEW_W,PREVIEW_H,PREVIEW_W,previousHash);
            previousHash=stats.dHash;
            decision=state.onFrame(new HandheldScanCoordinator.Frame(time,stats.dHash,
                stats.sharpness,stats.exposure,geometry.cardCoverage,geometry.confidence,stats.motion));
            // Only frames actually submitted to OCR need color planes.
            if(decision.action==HandheldScanCoordinator.Action.ANALYZE
                    && image.getPlanes().length>=3) {
                u=copyChroma(image.getPlanes()[1]);
                v=copyChroma(image.getPlanes()[2]);
            }
        } catch(RuntimeException ex) {
            sink.error("Camera preprocessing failed: "+ex.getMessage());
            // Do not abandon an existing stable scene on one transient frame.
            return;
        } finally {
            image.close(); // Always release ImageProxy; never hold it during OCR.
        }
        if(decision==null)return;
        if(decision.action==HandheldScanCoordinator.Action.REQUEST_FOCUS) {
            refocus(rawW,rawH,rotation,geometry);
            return;
        }
        if(decision.action!=HandheldScanCoordinator.Action.ANALYZE)return;
        final long generation=decision.generation;
        try {
            byte[] rectified=PerspectiveRectifier.rectify(luma,width,height,width,warp,OCR_W,OCR_H);
            byte[] rgb=u!=null&&v!=null
                ?ColorCardRectifier.rectify(luma,width,height,width,u,v,warp,OCR_W,OCR_H,
                    rotation,rawW,rawH)
                :null;
            final Capture capture=new Capture(rectified,rgb,OCR_W,OCR_H,rotation,generation,time);
            sink.telemetry("capture",now()-start,decision.reason);
            AtomicBoolean once=new AtomicBoolean();
            recognition.recognize(capture,(evidence,candidates)->{
                if(!once.compareAndSet(false,true))return;
                serialExecutor.execute(()->completeRecognition(capture,evidence,candidates));
            });
        } catch(RuntimeException ex) {
            state.onRecognitionFinished(generation,false,now());
            sink.error("OCR dispatch failed: "+ex.getMessage());
        }
    }
    private void completeRecognition(Capture capture,PrintingEvidenceResolver.Evidence evidence,
            List<PrintingEvidenceResolver.Candidate> candidates) {
        if(closed||capture.generation!=state.currentGeneration())return;
        long t=now();
        PrintingEvidenceResolver.Result result=PrintingEvidenceResolver.resolve(evidence,candidates);
        sink.telemetry("verification",t-capture.timestampMs,result.verdict+": "+result.reason);
        if(result.verdict==PrintingEvidenceResolver.Verdict.AUTO_SAVE) {
            if(!state.mayCommit(capture.generation,result))return;
            boolean committed=false;
            try {committed=sink.save(result.best,capture);}
            catch(RuntimeException ex) {sink.error("Save failed: "+ex.getMessage());}
            state.onRecognitionFinished(capture.generation,committed,t);
            if(!committed)throttledNotice("Could not save; retrying card",t);
        } else if(result.verdict==PrintingEvidenceResolver.Verdict.RETRY) {
            state.onRetry(capture.generation,t);
            throttledNotice("Searching again — hold card in view",t);
        } else {
            if(state.shouldOpenReview(capture.generation,t)) {
                try {sink.review(result,capture);}
                catch(RuntimeException ex) {
                    state.completeManualReview(false);
                    sink.error("Printing review failed: "+ex.getMessage());
                }
            } else {
                throttledNotice("Trying another frame for collector and set",t);
            }
        }
    }
    private void throttledNotice(String message,long t) {
        if(t-lastNoticeMs<1200)return;
        lastNoticeMs=t;sink.notice(message);
    }
    private static long now(){return System.nanoTime()/1_000_000;}
    private static byte[] copyLuma(ImageProxy.PlaneProxy plane,int w,int h) {
        ByteBuffer b=plane.getBuffer().duplicate();
        int row=plane.getRowStride(),pixel=plane.getPixelStride();
        if(row<1||pixel<1||w<1||h<1)throw new IllegalArgumentException("invalid Y plane");
        int offset=b.position();
        byte[] y=new byte[w*h];
        for(int yy=0;yy<h;yy++) {
            int source=offset+yy*row;
            for(int xx=0;xx<w;xx++)y[yy*w+xx]=b.get(source+xx*pixel);
        }
        return y;
    }
    private static ColorCardRectifier.Plane copyChroma(ImageProxy.PlaneProxy plane) {
        ByteBuffer b=plane.getBuffer().duplicate();
        byte[] data=new byte[b.remaining()];b.get(data);
        return new ColorCardRectifier.Plane(data,plane.getRowStride(),plane.getPixelStride());
    }
    private void refocus(int rawW,int rawH,int rotation,Geometry g) {
        if(control==null||g==null)return;
        try {
            SurfaceOrientedMeteringPointFactory factory=new SurfaceOrientedMeteringPointFactory(rawW,rawH);
            double x=(g.tl.x+g.tr.x+g.br.x+g.bl.x)/4.0;
            double y=(g.tl.y+g.tr.y+g.br.y+g.bl.y)/4.0;
            float sx=FrameRotation.rawX(x,y,rawW,rawH,rotation);
            float sy=FrameRotation.rawY(x,y,rawW,rawH,rotation);
            FocusMeteringAction action=new FocusMeteringAction.Builder(
                factory.createPoint(sx,sy),FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(2,TimeUnit.SECONDS).build();
            control.startFocusAndMetering(action);
        } catch(RuntimeException ex) {sink.error("Autofocus request failed: "+ex.getMessage());}
    }
    /** Invoke on the SAME serial CameraX executor after the existing picker finishes. */
    public void completeManualReview(boolean saved){state.completeManualReview(saved);}
    public void close(){closed=true;state.reset();}
}
