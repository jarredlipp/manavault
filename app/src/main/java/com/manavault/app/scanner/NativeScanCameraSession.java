package com.manavault.app.scanner;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.Executor;

/**
 * Binds the real CameraX preview and the improved recognition analyzer as ONE
 * scanner session. This is the entry point NativeScannerActivity should invoke,
 * instead of binding its prior ImageAnalysis.Analyzer separately.
 *
 * The application MUST provide its actual OCR/print lookup/persistent-save
 * adapters. This class never guesses printing metadata or persists collection
 * data itself. The session is closed by the Activity lifecycle.
 */
public final class NativeScanCameraSession implements AutoCloseable {
    private final Object lock = new Object();
    private final ProcessCameraProvider provider;
    private final ImageAnalysis analysis;
    private final Preview preview;
    private final HandheldCameraXAnalyzer analyzer;
    private final ScannerStartupCoordinator startup;
    private final Executor cameraExecutor;
    private volatile boolean closed;

    private NativeScanCameraSession(ProcessCameraProvider provider, ImageAnalysis analysis,
            Preview preview, HandheldCameraXAnalyzer analyzer,
            ScannerStartupCoordinator startup, Executor executor) {
        this.provider = provider;
        this.analysis = analysis;
        this.preview = preview;
        this.analyzer = analyzer;
        this.startup = startup;
        this.cameraExecutor = executor;
    }

    /**
     * The existing offline data loader should be prewarmed by the application
     * BEFORE start() on a separate worker. Camera preview must not await it.
     * Begin/end scanning prevent refreshWhenIdle from running while scanning.
     */
    public static NativeScanCameraSession start(
            LifecycleOwner owner,
            ProcessCameraProvider provider,
            CameraSelector selector,
            Preview preview,
            HandheldCameraXAnalyzer.GeometryDetector detector,
            HandheldCameraXAnalyzer.RecognitionEngine existingRecognition,
            HandheldCameraXAnalyzer.ResultSink existingSaveReviewAndTelemetry,
            Executor cameraExecutor,
            ScannerStartupCoordinator processStartup) {
        if(owner==null || provider==null || selector==null || preview==null || detector==null
                || existingRecognition==null || existingSaveReviewAndTelemetry==null
                || cameraExecutor==null || processStartup==null)
            throw new IllegalArgumentException("Native camera session dependencies must be supplied");
        final long begun = System.nanoTime();
        processStartup.beginScan();
        ImageAnalysis analysis = null;
        HandheldCameraXAnalyzer analyzer = null;
        boolean success = false;
        try {
            // Bind the preview immediately. No Scryfall network access, bulk
            // database imports or artwork-loader waits on this path.
            analysis = HandheldCameraXAnalyzer.latestFrameAnalysis();
            Camera camera = provider.bindToLifecycle(owner,selector,preview,analysis);
            existingSaveReviewAndTelemetry.telemetry("camera_bound",
                (System.nanoTime()-begun)/1_000_000,
                "catalog="+processStartup.getState());
            analyzer = new HandheldCameraXAnalyzer(detector, existingRecognition,
                existingSaveReviewAndTelemetry,cameraExecutor,
                camera.getCameraControl(),camera.getCameraInfo());
            analysis.setAnalyzer(cameraExecutor, analyzer);
            analyzer.setTorchMode(AdaptiveLightingPolicy.Mode.AUTO);
            NativeScanCameraSession session = new NativeScanCameraSession(provider,analysis,
                preview,analyzer,processStartup,cameraExecutor);
            success = true;
            return session;
        } finally {
            if(!success) {
                if(analyzer!=null)analyzer.close();
                try { if(analysis!=null)provider.unbind(analysis,preview); }
                finally { processStartup.endScan(); }
            }
        }
    }

    public void setTorchMode(AdaptiveLightingPolicy.Mode mode) {
        if(mode==null)throw new IllegalArgumentException("torch mode required");
        synchronized(lock) {
            if(closed)return;
            cameraExecutor.execute(()->{if(!closed)analyzer.setTorchMode(mode);});
        }
    }
    /** Call after real UI review is committed or canceled. */
    public void completeManualReview(boolean saved) {
        synchronized(lock) {
            if(closed)return;
            cameraExecutor.execute(()->{if(!closed)analyzer.completeManualReview(saved);});
        }
    }
    @Override public void close() {
        synchronized(lock) {
            if(closed)return;
            closed=true;
        }
        // Must execute analyzer shutdown on the same executor used by CameraX.
        // Unbind first to stop delivering new camera frames; the Activity must
        // also close this Session when its lifecycle stops.
        try { provider.unbind(analysis,preview); }
        finally {
            Runnable finish = () -> {
                try { analyzer.close(); }
                finally { startup.endScan(); }
            };
            try { cameraExecutor.execute(finish); }
            catch(RuntimeException rejected) { finish.run(); }
        }
    }
}
