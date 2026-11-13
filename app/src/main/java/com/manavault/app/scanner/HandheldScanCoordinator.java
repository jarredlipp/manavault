package com.manavault.app.scanner;

/**
 * Camera/stand-independent single-card state machine.
 * All calls must be serialized onto the analyzer executor.
 * It never authorizes a printing by itself: the resolver must verify its identity.
 */
public final class HandheldScanCoordinator {
    public enum Action { WAIT, ANALYZE, REQUEST_FOCUS }
    public static final class Frame {
        public final long timestampMs, sceneSignature;
        public final double sharpness, exposure, cardCoverage, boundaryConfidence, motion;
        public final boolean hasRegionHashes;
        public final long titleSignature, artworkSignature;
        public Frame(long ms, long signature, double sharpness, double exposure,
                     double coverage, double confidence, double motion) {
            this(ms,signature,sharpness,exposure,coverage,confidence,motion,0,0,false);
        }
        public Frame(long ms, long signature, double sharpness, double exposure,
                     double coverage, double confidence, double motion,
                     long titleSignature,long artworkSignature) {
            this(ms,signature,sharpness,exposure,coverage,confidence,motion,
                 titleSignature,artworkSignature,true);
        }
        private Frame(long ms,long signature,double sharpness,double exposure,
                      double coverage,double confidence,double motion,
                      long titleSignature,long artworkSignature,boolean hasRegionHashes) {
            timestampMs=ms; sceneSignature=signature;
            this.titleSignature=titleSignature;this.artworkSignature=artworkSignature;
            this.hasRegionHashes=hasRegionHashes;
            this.sharpness=clamp(sharpness); this.exposure=clamp(exposure);
            cardCoverage=clamp(coverage); boundaryConfidence=clamp(confidence);
            this.motion=clamp(motion);
        }
    }
    public static final class Decision {
        public final Action action;
        public final long generation;
        public final String reason;
        private Decision(Action a, long gen, String reason) {
            action=a; generation=gen; this.reason=reason;
        }
    }

    private static final long ABSENCE_MS=160;
    private static final long SCENE_CONFIRM_MS=140;
    private static final long REFOCUS_COOLDOWN_MS=850;
    private static final long MAX_INFLIGHT_MS=1550;
    private static final long QUALITY_FALLBACK_MS=850;
    private static final long REVIEW_RECAPTURE_MS=1050;
    private long generation,sceneSignature=Long.MIN_VALUE,proposedSignature=Long.MIN_VALUE;
    private long sceneTitleSignature,sceneArtworkSignature,proposedTitleSignature,proposedArtworkSignature;
    private boolean sceneHasRegions,proposedHasRegions;
    private long proposedSinceMs,missingSinceMs=-1,startedMs,lastTimestampMs=-1;
    private long lastFocusMs=Long.MIN_VALUE/2,poorQualitySinceMs=-1;
    private long nextAllowedMs,firstReviewAtMs=-1;
    private int proposedFrames,retryCount,reviewAttempts,acceptedFrames,discardedFrames;
    private boolean inFlight,saved,reviewPending;

    public Decision onFrame(Frame f) {
        if (f.timestampMs < lastTimestampMs) return decision(Action.WAIT,"out-of-order frame");
        lastTimestampMs=f.timestampMs;
        // Missing card clears the previous printing and enables scanning a second
        // physical copy whose artwork and hash are identical.
        if (f.cardCoverage < .08 || f.boundaryConfidence < .18) {
            if (missingSinceMs < 0) missingSinceMs=f.timestampMs;
            if (f.timestampMs-missingSinceMs >= ABSENCE_MS && sceneSignature!=Long.MIN_VALUE) {
                generation++; sceneSignature=Long.MIN_VALUE; proposedSignature=Long.MIN_VALUE;
                sceneHasRegions=false;proposedHasRegions=false;
                proposedFrames=0; clearCurrent();
            }
            discardedFrames++;
            return decision(Action.WAIT,"card absent");
        }
        missingSinceMs=-1;
        if (sceneSignature==Long.MIN_VALUE) newScene(f);
        else if (newCardEvidence(f) && f.motion<=.38) {
            if (proposedSignature==Long.MIN_VALUE || !sameProposedCard(f)) {
                proposedSignature=f.sceneSignature;
                proposedTitleSignature=f.titleSignature;
                proposedArtworkSignature=f.artworkSignature;
                proposedHasRegions=f.hasRegionHashes;
                proposedSinceMs=f.timestampMs;proposedFrames=1;
                return decision(Action.WAIT,"possible new card");
            }
            proposedFrames++;
            if (proposedFrames>=3 && f.timestampMs-proposedSinceMs>=SCENE_CONFIRM_MS) {
                newScene(f);
            } else return decision(Action.WAIT,"confirm card change");
        } else { proposedSignature=Long.MIN_VALUE;proposedHasRegions=false;proposedFrames=0; }

        if (saved) return decision(Action.WAIT,"already saved; present next card");
        if (reviewPending) return decision(Action.WAIT,"review pending");
        if (inFlight) {
            if (f.timestampMs-startedMs < MAX_INFLIGHT_MS) return decision(Action.WAIT,"OCR in progress");
            // Invalidate a hung callback; it must not save the old capture.
            generation++; inFlight=false;
        }
        if (f.timestampMs < nextAllowedMs) return decision(Action.WAIT,"retry pacing");
        if (f.cardCoverage<.12 || f.boundaryConfidence<.36) {
            discardedFrames++;return decision(Action.WAIT,"reframe card");
        }
        if (f.motion>.85) {discardedFrames++;return decision(Action.WAIT,"movement");}
        boolean sharp=f.sharpness>=.18,lit=f.exposure>=.23;
        if (sharp && lit) poorQualitySinceMs=-1;
        else {
            if (poorQualitySinceMs<0)poorQualitySinceMs=f.timestampMs;
            long poorFor=f.timestampMs-poorQualitySinceMs;
            // On a handheld phone, do not wait indefinitely for perfect lighting.
            // Request one early focus/exposure cycle, then try best-effort OCR
            // once the image has had time to settle and remains minimally legible.
            boolean minimallyLegible=f.sharpness>=.07 && f.exposure>=.08;
            if (poorFor<QUALITY_FALLBACK_MS || !minimallyLegible) {
                if (f.timestampMs-lastFocusMs>=REFOCUS_COOLDOWN_MS) {
                    lastFocusMs=f.timestampMs;discardedFrames++;
                    return decision(Action.REQUEST_FOCUS,sharp?"exposure adjust":"autofocus");
                }
                discardedFrames++;return decision(Action.WAIT,"quality settling");
            }
        }
        inFlight=true;startedMs=f.timestampMs;acceptedFrames++;
        return decision(Action.ANALYZE,sharp && lit?"ready":"best-effort handheld frame");
    }

    public boolean mayCommit(long frameGeneration,PrintingEvidenceResolver.Result r) {
        return inFlight && !saved && !reviewPending && generation==frameGeneration
            && r!=null && r.verdict==PrintingEvidenceResolver.Verdict.AUTO_SAVE;
    }

    /** A failed OCR frame MUST retry automatically; it must not open the printing picker. */
    public void onRetry(long frameGeneration,long timestampMs) {
        if (frameGeneration!=generation || !inFlight) return;
        inFlight=false;retryCount++;
        nextAllowedMs=timestampMs + Math.min(600,120+retryCount*90);
    }

    /**
     * Give OCR, collector marks, type-bar symbol and artwork up to two extra
     * camera frames before bothering the user with an edition-selection sheet.
     */
    public boolean shouldOpenReview(long frameGeneration,long timestampMs) {
        if (frameGeneration!=generation || !inFlight || saved) return false;
        if (firstReviewAtMs<0) firstReviewAtMs=timestampMs;
        reviewAttempts++;
        if (reviewAttempts<3 && timestampMs-firstReviewAtMs<REVIEW_RECAPTURE_MS) {
            inFlight=false;
            nextAllowedMs=timestampMs+140;
            return false;
        }
        inFlight=false;reviewPending=true;
        return true;
    }

    public void completeManualReview(boolean committed) {
        if (!reviewPending) return;
        reviewPending=false;inFlight=false;saved=committed;
        generation++; // invalidates any late callbacks from the picker
        if (!committed) { nextAllowedMs=0;reviewAttempts=0;firstReviewAtMs=-1; }
    }
    public void onRecognitionFinished(long frameGeneration,boolean committed,long timestampMs) {
        if (frameGeneration!=generation || !inFlight) return;
        inFlight=false;
        if (committed) saved=true;
        else nextAllowedMs=timestampMs+220;
    }
    /** Backwards-compatible call for integration code using the first patch. */
    public void onRecognitionFinished(long frameGeneration,boolean committed) {
        onRecognitionFinished(frameGeneration,committed,lastTimestampMs);
    }
    public void suspendForReview(long frameGeneration) {
        if (frameGeneration==generation) {inFlight=false;reviewPending=true;}
    }
    public void reset() {
        generation++;sceneSignature=Long.MIN_VALUE;proposedSignature=Long.MIN_VALUE;
        sceneHasRegions=false;proposedHasRegions=false;
        proposedFrames=0;lastTimestampMs=-1;missingSinceMs=-1;clearCurrent();
    }
    public long currentGeneration(){return generation;}
    public int getAcceptedFrames(){return acceptedFrames;}
    public int getDiscardedFrames(){return discardedFrames;}
    public int getRetryCount(){return retryCount;}
    public boolean isReviewPending(){return reviewPending;}
    public boolean isWaitingForRemoval(){return saved;}

    private Decision decision(Action a,String why){return new Decision(a,generation,why);}
    private void newScene(Frame f) {
        generation++;sceneSignature=f.sceneSignature;
        sceneTitleSignature=f.titleSignature;sceneArtworkSignature=f.artworkSignature;
        sceneHasRegions=f.hasRegionHashes;
        proposedSignature=Long.MIN_VALUE;proposedHasRegions=false;
        proposedFrames=0;clearCurrent();
    }
    private boolean newCardEvidence(Frame f) {
        int full=Long.bitCount(f.sceneSignature^sceneSignature);
        if(!f.hasRegionHashes || !sceneHasRegions)
            return full >= (saved?26:12); // older adapter remains compatible
        int title=Long.bitCount(f.titleSignature^sceneTitleSignature);
        int art=Long.bitCount(f.artworkSignature^sceneArtworkSignature);
        if(saved) {
            // Do not interpret a shifting camera angle as another physical card.
            // A lower global difference must be accompanied by independently
            // different title AND artwork regions, confirmed on successive frames.
            return (full>=23 && title>=10 && art>=10)
                || (full>=12 && title>=15 && art>=14);
        }
        return (full>=12 && (title>=7 || art>=9))
            || (full>=9 && title>=12 && art>=12);
    }
    private boolean sameProposedCard(Frame f) {
        if(Long.bitCount(f.sceneSignature^proposedSignature)>11)return false;
        if(f.hasRegionHashes&&proposedHasRegions)
            return Long.bitCount(f.titleSignature^proposedTitleSignature)<=10
                && Long.bitCount(f.artworkSignature^proposedArtworkSignature)<=10;
        return true;
    }
    private void clearCurrent() {
        inFlight=false;saved=false;reviewPending=false;retryCount=0;reviewAttempts=0;
        nextAllowedMs=0;firstReviewAtMs=-1;poorQualitySinceMs=-1;
    }
    private static boolean distinct(long a,long b,int minBits){return Long.bitCount(a^b)>=minBits;}
    private static double clamp(double n){return Double.isFinite(n)?Math.max(0,Math.min(1,n)):0;}
}
