package com.manavault.app;

import java.util.Map;

/** OCR title-prefix rescue: a name suggestion, never a printing verification. */
final class TitlePrefixMatcher {
    private TitlePrefixMatcher() {}
    static String uniqueOneSubstitution(String observed, Map<String,String> prefixes) {
        if(observed==null || !observed.matches("[a-z]{7,24}") || prefixes==null)return null;
        String found=null;
        for(Map.Entry<String,String> e:prefixes.entrySet()) {
            String word=e.getKey(),canonical=e.getValue();
            if(canonical==null || word.length()!=observed.length())continue;
            int errors=0;
            for(int i=0;i<word.length() && errors<=1;i++)if(word.charAt(i)!=observed.charAt(i))errors++;
            if(errors!=1)continue;
            if(found!=null && !found.equals(canonical))return null;
            found=canonical;
        }
        return found;
    }
}
