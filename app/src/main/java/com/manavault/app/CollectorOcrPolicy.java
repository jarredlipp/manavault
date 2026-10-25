package com.manavault.app;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Candidate-scoped detector deciding when an extra focused collector OCR
 * read is justified. Never verifies a printing by itself. */
final class CollectorOcrPolicy {
    private CollectorOcrPolicy() {}
    private static final Pattern FRACTION=Pattern.compile("(?:^|[^A-Z0-9])([TS]?\\d{1,5}[A-Z]?)\\s*/\\s*\\d{2,5}(?=$|[^A-Z0-9])");
    private static final Pattern NUMBER=Pattern.compile("(?:^|[^A-Z0-9])(?:[CMRU]\\s*)?([TS]?\\d{2,5}[A-Z]?)(?=$|[^A-Z0-9])");
    static String normalize(String raw){
        if(raw==null)return "";
        String s=raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+","");
        Matcher m=Pattern.compile("^([TS]?)(0*)(\\d{1,5})([A-Z]?)$").matcher(s);
        if(!m.matches())return "";
        String digits=m.group(3).replaceFirst("^0+(?!$)","");
        return m.group(1)+digits+m.group(4);
    }
    static boolean hasCandidateNumber(String raw,Collection<String> candidates){
        if(raw==null||candidates==null||candidates.isEmpty())return false;
        String line=raw.toUpperCase(Locale.ROOT).replace('\\','/');
        Matcher fraction=FRACTION.matcher(line);
        while(fraction.find())if(matchesCandidate(fraction.group(1),candidates))return true;
        // Ignore unstructured values on copyright/illustrator lines.
        if(line.contains("WIZARDS")||line.contains("COPYRIGHT")||line.contains("ILLUS"))return false;
        // An arbitrary sentence or ability text in the crop is not a
        // standalone collector field. Numbers must sit in compact footer
        // fields or next to a rarity/set marker, not in prose.
        if(line.length()>70 || line.matches(".*\\b(?:IT|DEALS|DAMAGE|CREATURE|DRAW|CARD|TARGET)\\b.*"))return false;
        Matcher number=NUMBER.matcher(line);
        while(number.find())if(matchesCandidate(number.group(1),candidates))return true;
        return false;
    }
    private static boolean matchesCandidate(String value,Collection<String> candidates){
        String normalized=normalize(value);
        if(normalized.isEmpty()||normalized.matches("(?:19|20)\\d{2}"))return false;
        for(String candidate:candidates)if(normalized.equals(normalize(candidate)))return true;
        return false;
    }
}
