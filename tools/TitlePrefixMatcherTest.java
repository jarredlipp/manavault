package com.manavault.app;
import java.util.HashMap;
import java.util.Map;
public final class TitlePrefixMatcherTest {
    public static void main(String[] args){
        Map<String,String> names=new HashMap<>();
        names.put("webstrike","Webstrike Elite");
        names.put("woodland","Woodland Mystic");
        if(!"Webstrike Elite".equals(TitlePrefixMatcher.uniqueOneSubstitution("wepstrike",names)))throw new AssertionError("one glyph rescue failed");
        if(TitlePrefixMatcher.uniqueOneSubstitution("webstrike",names)!=null)throw new AssertionError("exact words do not need fuzzy rescue");
        if(TitlePrefixMatcher.uniqueOneSubstitution("westrike",names)!=null)throw new AssertionError("missing glyph must not be accepted");
        names.put("webstrine","Different Name");
        if(TitlePrefixMatcher.uniqueOneSubstitution("webstrile",names)!=null)throw new AssertionError("two equally close titles must remain ambiguous");
        if(TitlePrefixMatcher.uniqueOneSubstitution("the",names)!=null)throw new AssertionError("short prefixes are unsafe");
        System.out.println("v5.12.26 distinctive OCR title-prefix tests passed");
    }
}
