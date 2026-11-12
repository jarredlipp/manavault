package com.manavault.app.scanner;

/** Lightweight, allocation-free luminance analysis for use before expensive OCR. */
public final class FrameQualityEstimator {
    public static final class Stats {
        public final double sharpness, exposure, motion;
        public final long dHash;
        public Stats(double s, double e, double m, long h) {
            sharpness = s; exposure = e; motion = m; dHash = h;
        }
    }
    private FrameQualityEstimator() {}

    /**
     * Estimate quality of a cropped, rectified luminance frame. Stride may exceed width.
     * Motion is perceptual hash difference versus previous frame (not optical flow).
     * A changed card may produce high motion; callers should retry on subsequent frame.
     */
    public static Stats estimate(byte[] gray, int width, int height, int stride, Long previousHash) {
        if (gray == null || width < 18 || height < 18 || stride < width ||
                gray.length < (height - 1) * stride + width) {
            throw new IllegalArgumentException("invalid luminance image");
        }
        int step = Math.max(1, Math.min(width, height) / 100);
        double edgeSum = 0, lumSum = 0, penalty = 0;
        int n = 0;
        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                int center = at(gray, x, y, stride);
                int laplace = Math.abs(4 * center - at(gray, x-step, y, stride)
                    - at(gray, x+step, y, stride) - at(gray, x, y-step, stride)
                    - at(gray, x, y+step, stride));
                edgeSum += laplace;
                lumSum += center;
                if (center <= 14 || center >= 246) penalty++;
                n++;
            }
        }
        if (n == 0) throw new IllegalArgumentException("empty sample");
        double mean = lumSum / n;
        double sharp = clamp(edgeSum / n / 75.0);
        double exposure = clamp(1 - Math.abs(mean - 126) / 130.0 - (penalty / n) * 0.55);
        long hash = differenceHash(gray, width, height, stride);
        double motion = previousHash == null ? 0 : (Long.bitCount(hash ^ previousHash) / 64.0);
        return new Stats(sharp, exposure, motion, hash);
    }
    /** 64-bit crop-relative difference hash, NOT exact-card identity. */
    public static long differenceHash(byte[] gray, int w, int h, int stride) {
        if (gray == null || w <= 1 || h <= 1 || stride < w) throw new IllegalArgumentException();
        long hash = 0;
        for (int row = 0; row < 8; row++) {
            int y = (int) ((row + 0.5) * h / 8);
            y = Math.min(h - 1, y);
            for (int col = 0; col < 8; col++) {
                int x1 = (int) ((col + 0.1) * w / 9);
                int x2 = (int) ((col + 1.1) * w / 9);
                x2 = Math.min(w - 1, x2);
                if (at(gray, x1, y, stride) > at(gray, x2, y, stride)) hash |= (1L << (row*8 + col));
            }
        }
        return hash;
    }
    private static int at(byte[] b, int x, int y, int stride) { return b[y*stride+x] & 0xff; }
    private static double clamp(double a) { return Math.max(0, Math.min(1, a)); }
}
