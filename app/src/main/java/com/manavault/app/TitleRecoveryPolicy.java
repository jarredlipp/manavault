package com.manavault.app;

import java.util.Collection;
import java.util.Locale;

/** Conservative, bounded correction of a photographed TITLE, not card identity proof.
 * A corrected name is always a candidate: printing evidence must corroborate it.
 * Pure JVM code for regression tests. */
final class TitleRecoveryPolicy {
    private TitleRecoveryPolicy() {}
    static String normalize(String s) { return TitleReadingPolicy.key(s); }
    static String keyPrefix(String normalized) {
        return normalized.length()<2 ? "" : normalized.substring(0,2);
    }
    static boolean isBadMultiFaceGuess(String name, boolean independentlyObservedFaces) {
        return name!=null && name.split(" // ",-1).length>2 && !independentlyObservedFaces;
    }
    /** A lone OCR numeric suffix is common on decorative/promo fonts, but can
     * also be part of a legitimate name. Caller must attempt exact first. */
    static String withoutTrailingArtifact(String observed){
        String raw=normalize(observed);
        return raw.replaceFirst(" (?:(?:[a-z]{0,2}\\d{1,2}[a-z]{0,2})|(?:\\d{1,2}[a-z]{1,3}))$","");
    }
    /** Repair a narrowly defined *physical title bar* OCR artifact:
     * the small saga/DFC frame emblem can be read as an isolated leading O,
     * while the mana cost becomes a digit joined to the final title word.
     * E.g. O The Long Reach of Night3 -> the long reach of night.
     * This only yields an exact catalog lookup KEY, never proof of identity.
     * Callers must check an existing indexed name, and must keep it provisional
     * until independent printed set/collector evidence corroborates it. */
    static String strayEmblemAndJoinedDigit(String observed){
        String key=normalize(observed);
        if(!key.startsWith("o ") || key.length()<18)return "";
        String candidate=key.substring(2);
        // Allow either Night3 or Night 3, but only when the preceding
        // word is long enough to exclude numbered "No. 3" titles.
        if(candidate.matches(".*[a-z]{4,}[0-9]$"))
            candidate=candidate.substring(0,candidate.length()-1);
        else if(candidate.matches(".*[a-z]{4,} [0-9]$"))
            candidate=candidate.substring(0,candidate.length()-2);
        else return "";
        if(candidate.split(" ").length<4)return "";
        return candidate;
    }
    /** Bounded Levenshtein. Used on a 2-character prefix bucket, not the whole catalog. */
    static int distance(String left,String right,int limit){
        if(Math.abs(left.length()-right.length())>limit)return limit+1;
        int[] prev=new int[right.length()+1],curr=new int[right.length()+1];
        for(int j=0;j<prev.length;j++)prev[j]=j;
        for(int i=1;i<=left.length();i++){
            curr[0]=i;int rowMin=curr[0];
            for(int j=1;j<=right.length();j++){
                curr[j]=Math.min(Math.min(prev[j]+1,curr[j-1]+1),prev[j-1]+(left.charAt(i-1)==right.charAt(j-1)?0:1));
                rowMin=Math.min(rowMin,curr[j]);
            }
            if(rowMin>limit)return limit+1;
            int[] swap=prev;prev=curr;curr=swap;
        }
        return prev[right.length()];
    }
    /** UNIQUE correction only; no arbitrary split-card substitution. */
    static String uniqueCandidate(String raw, Collection<String> canonicalNames){
        String observed=normalize(raw);
        if(observed.length()<9 || observed.length()>56 || TitleReadingPolicy.isFooterOrRulesNoise(raw))return null;
        String simple=withoutTrailingArtifact(raw);
        String best=null;int bestDistance=3;int competing=0;
        boolean hasFaceDelimiters=raw!=null && raw.contains("//");
        for(String name:canonicalNames){
            if(name==null || (!hasFaceDelimiters && name.contains(" // ")))continue;
            String key=normalize(name);
            int d=distance(observed,key,2);
            if(d>2 && !simple.equals(observed) && simple.length()>=9)d=distance(simple,key,1);
            if(d<bestDistance){best=name;bestDistance=d;competing=1;}
            else if(d==bestDistance && !key.equals(normalize(best)))competing++;
        }
        return bestDistance<=2 && competing==1?best:null;
    }
}
