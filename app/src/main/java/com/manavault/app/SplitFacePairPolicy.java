package com.manavault.app;

import java.util.Map;

/** Pair two independently photographed split-face titles. Neither one-word
 * face, OCR rules text, nor artwork by itself establishes a split card.
 * The returned card NAME is provisional until physical printing validation. */
final class SplitFacePairPolicy {
    private SplitFacePairPolicy() {}
    static String key(String left,String right){
        String a=TitleReadingPolicy.key(left),b=TitleReadingPolicy.key(right);
        if(a.length()<3||b.length()<3||a.equals(b))return "";
        return a.compareTo(b)<0?a+"|"+b:b+"|"+a;
    }
    static String resolve(Map<String,String> pairs,Iterable<String> first,Iterable<String> second){
        String found=null;
        for(String a:first)for(String b:second){
            String k=key(a,b);
            if(k.isEmpty()||!pairs.containsKey(k))continue;
            String name=pairs.get(k);
            if(name==null)return null; // Ambiguous catalog pair
            if(found!=null&&!found.equals(name))return null;
            found=name;
        }
        return found;
    }
}
