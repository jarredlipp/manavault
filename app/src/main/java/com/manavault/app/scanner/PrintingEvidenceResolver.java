package com.manavault.app.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Conservative exact-printing identity resolver. Combines independent footer,
 * type-bar symbol, and cropped-artwork evidence. A sole *filtered* candidate is
 * NOT proof of a sole catalog printing. A true, complete catalog singleton is
 * eligible for automatic saving only with strong exact-title evidence and no
 * conflicting printed marks.
 */
public final class PrintingEvidenceResolver {
    public enum Verdict { AUTO_SAVE, REVIEW, RETRY }
    public static final class Candidate {
        public final String printingId, name, setCode, collectorNumber;
        public final double titleScore, artworkScore, symbolScore;
        public Candidate(String printingId, String name, String setCode, String collectorNumber,
                double titleScore, double artworkScore, double symbolScore) {
            this.printingId = printingId; this.name = name;
            this.setCode = normalizeSet(setCode); this.collectorNumber = normalizeNumber(collectorNumber);
            this.titleScore = clamp(titleScore); this.artworkScore = clamp(artworkScore);
            this.symbolScore = clamp(symbolScore);
        }
    }
    public static final class Evidence {
        public final String name, footerSet, footerNumber;
        public final double titleConfidence, setConfidence, collectorConfidence;
        public final boolean symbolObserved, artworkObserved;
        /** True only after full index coverage and full name-printing enumeration are checked. */
        public final boolean fullVisualCoverage, allNamePrintingsConsidered;
        /** Count from a separate, exhaustive query of eligible paper printings for
         * the exact card name; -1 means unverified. Never use artwork top-K or
         * candidates remaining after OCR filtering as this count. */
        public final int authoritativePrintingCount;
        /** True only after the offline catalog is fully loaded and its completeness
         * check has passed. Prevents truncated catalogs from appearing unique. */
        public final boolean completeCatalog;
        public Evidence(String name, String footerSet, String footerNumber,
                double titleConfidence, double setConfidence, double collectorConfidence,
                boolean symbolObserved, boolean artworkObserved) {
            this(name,footerSet,footerNumber,titleConfidence,setConfidence,
                collectorConfidence,symbolObserved,artworkObserved,false,false,-1,false);
        }
        public Evidence(String name,String footerSet,String footerNumber,
                double titleConfidence,double setConfidence,double collectorConfidence,
                boolean symbolObserved,boolean artworkObserved,
                boolean fullVisualCoverage,boolean allNamePrintingsConsidered) {
            this(name,footerSet,footerNumber,titleConfidence,setConfidence,
                collectorConfidence,symbolObserved,artworkObserved,
                fullVisualCoverage,allNamePrintingsConsidered,-1,false);
        }
        /** Use this constructor ONLY with a verified, unfiltered full catalog
         * printing count. Callers must not derive the count from OCR candidate
         * lists, visual similarity matches, or a catalog still being imported. */
        public Evidence(String name,String footerSet,String footerNumber,
                double titleConfidence,double setConfidence,double collectorConfidence,
                boolean symbolObserved,boolean artworkObserved,
                boolean fullVisualCoverage,boolean allNamePrintingsConsidered,
                int authoritativePrintingCount,boolean completeCatalog) {
            this.name = name == null ? "" : name.trim();
            this.fullVisualCoverage=fullVisualCoverage;
            this.allNamePrintingsConsidered=allNamePrintingsConsidered;
            this.authoritativePrintingCount=authoritativePrintingCount;
            this.completeCatalog=completeCatalog;
            this.footerSet = normalizeSet(footerSet); this.footerNumber = normalizeNumber(footerNumber);
            this.titleConfidence = clamp(titleConfidence); this.setConfidence = clamp(setConfidence);
            this.collectorConfidence = clamp(collectorConfidence);
            this.symbolObserved = symbolObserved; this.artworkObserved = artworkObserved;
        }
    }
    public static final class Result {
        public final Verdict verdict;
        public final Candidate best;
        public final List<Candidate> alternatives;
        public final String reason;
        public final double score;
        private Result(Verdict v, Candidate best, List<Candidate> candidates, String reason, double score) {
            verdict = v; this.best = best;
            alternatives = Collections.unmodifiableList(new ArrayList<>(candidates));
            this.reason = reason; this.score = score;
        }
    }
    private static final class Ranked {
        Candidate c; double score; boolean independent;
        Ranked(Candidate c, double score, boolean independent) {
            this.c = c; this.score = score; this.independent = independent;
        }
    }
    private PrintingEvidenceResolver() {}
    public static Result resolve(Evidence evidence, List<Candidate> candidates) {
        if (evidence == null || candidates == null || candidates.isEmpty())
            return new Result(Verdict.RETRY, null, Collections.<Candidate>emptyList(), "no catalog candidates", 0);
        List<Ranked> ranked = new ArrayList<>();
        boolean strongPrintedSet = !evidence.footerSet.isEmpty() && evidence.setConfidence >= 0.78;
        boolean strongPrintedNumber = !evidence.footerNumber.isEmpty() && evidence.collectorConfidence >= 0.77;
        for (Candidate c : candidates) {
            if (c == null || c.printingId == null || c.printingId.isEmpty()) continue;
            // Reject evidence that positively contradicts the printing.
            if (strongPrintedSet && !evidence.footerSet.equals(c.setCode)) continue;
            if (strongPrintedNumber && !evidence.footerNumber.equals(c.collectorNumber)) continue;
            boolean nameMatches = evidence.name.isEmpty() || normalizeTitle(evidence.name).equals(normalizeTitle(c.name));
            if (!nameMatches && evidence.titleConfidence >= 0.85) continue;
            boolean setMatch = strongPrintedSet && evidence.footerSet.equals(c.setCode);
            boolean numberMatch = strongPrintedNumber && evidence.footerNumber.equals(c.collectorNumber);
            boolean symbolMatch = evidence.symbolObserved && c.symbolScore >= 0.93;
            boolean artMatch = evidence.artworkObserved && c.artworkScore >= 0.94;
            // Two independent printing markers, not merely name + same-art reprint.
            boolean independent = (setMatch && numberMatch)
                || (numberMatch && symbolMatch)
                || (setMatch && symbolMatch && artMatch);
            double score = (nameMatches ? 0.15 * evidence.titleConfidence : 0)
                + (setMatch ? 0.30 * evidence.setConfidence : 0)
                + (numberMatch ? 0.32 * evidence.collectorConfidence : 0)
                + (evidence.symbolObserved ? 0.13 * c.symbolScore : 0)
                + (evidence.artworkObserved ? 0.10 * c.artworkScore : 0);
            ranked.add(new Ranked(c, score, independent));
        }
        ranked.sort(Comparator.comparingDouble((Ranked r) -> r.score).reversed()
            .thenComparing(r -> r.c.printingId));
        if (ranked.isEmpty()) return new Result(Verdict.RETRY, null,
                Collections.<Candidate>emptyList(), "printed markings conflict with all candidates", 0);
        List<Candidate> sorted = new ArrayList<>();
        for (Ranked r : ranked) sorted.add(r.c);
        Ranked best = ranked.get(0);
        double next = ranked.size() < 2 ? 0 : ranked.get(1).score;
        if (best.independent && best.score >= 0.59 && (ranked.size() == 1 || best.score - next >= 0.095)) {
            return new Result(Verdict.AUTO_SAVE, best.c, sorted, "independent exact-printing evidence", best.score);
        }
        // Do not demand a footer/symbol for a truly unique paper printing: it
        // does not carry any edition-selection uncertainty. This is intentionally
        // DIFFERENT from "only one plausible artwork/candidate survived".
        // Require a complete, independently enumerated catalog and a clear
        // title on an actually observed frame. Strong contradictory footers
        // were already removed above and result in RETRY.
        boolean authoritativeSingleton = evidence.completeCatalog
            && evidence.allNamePrintingsConsidered
            && evidence.authoritativePrintingCount == 1
            && candidates.size() == 1 && ranked.size() == 1
            && !evidence.name.isEmpty()
            && normalizeTitle(evidence.name).equals(normalizeTitle(best.c.name))
            && evidence.titleConfidence >= 0.96
            && best.c.titleScore >= 0.96;
        if (authoritativeSingleton) {
            return new Result(Verdict.AUTO_SAVE, best.c, sorted,
                "unique eligible printing in verified complete catalog + exact title", best.score);
        }
        // Image-only identification is permitted ONLY with explicit full
        // reference coverage, the complete same-title printing list, AND
        // uniquely different observed set-symbol/artwork evidence. Incomplete
        // artwork indexes must never produce false uniqueness by omission.
        boolean visualUnique=evidence.fullVisualCoverage && evidence.allNamePrintingsConsidered
            && evidence.symbolObserved && evidence.artworkObserved
            && evidence.titleConfidence>=.95 && best.c.titleScore>=.96
            && best.c.symbolScore>=.985 && best.c.artworkScore>=.985;
        if(visualUnique) {
            for(int i=1;i<ranked.size();i++) {
                Candidate other=ranked.get(i).c;
                if(other.symbolScore>=.88 && other.artworkScore>=.85) {
                    visualUnique=false;break; // same-art / shared-symbol printing
                }
                if(other.symbolScore>=.80 && other.artworkScore>=.90) {
                    visualUnique=false;break;
                }
            }
        }
        if(visualUnique)
            return new Result(Verdict.AUTO_SAVE,best.c,sorted,
                "complete-index uniquely observed artwork and set symbol",best.score);
        return new Result(Verdict.REVIEW, best.c, sorted,
                best.independent ? "close competing printing" : "missing independent set / collector / symbol evidence", best.score);
    }
    private static String normalizeTitle(String t) {
        return t == null ? "" : t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    private static String normalizeSet(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
    private static String normalizeNumber(String s) {
        if (s == null) return "";
        String n = s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        // Match "0201" and "201", preserve suffixes such as 123a or P201.
        return n.replaceFirst("^0+(?=[0-9])", "");
    }
    private static double clamp(double n) { return Math.max(0, Math.min(1, n)); }
}
