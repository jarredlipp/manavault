package com.manavault.app;
import java.util.*;
public final class SplitFacePairPolicyTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        Map<String,String> names=new HashMap<>();
        for(String full:new String[]{"Night // Day","Stand // Deliver","Fire // Ice","Wax // Wane"}){
            String[] halves=full.split(" // ");
            names.put(SplitFacePairPolicy.key(halves[0],halves[1]),full);
        }
        for(String full:new String[]{"Night // Day","Stand // Deliver","Fire // Ice","Wax // Wane"}){
            String[] halves=full.split(" // ");
            check(full.equals(SplitFacePairPolicy.resolve(names,List.of(halves[0]),List.of(halves[1]))),"Expected "+full);
            check(full.equals(SplitFacePairPolicy.resolve(names,List.of(halves[1]),List.of(halves[0]))),"Order-independent "+full);
        }
        check(SplitFacePairPolicy.resolve(names,List.of("player"),List.of("Day"))==null,"Rules text cannot establish title");
        check(SplitFacePairPolicy.resolve(names,List.of("Night"),List.of("Fire"))==null,"Cross-card faces cannot resolve");
        names.put(SplitFacePairPolicy.key("Night","Day"),null);
        check(SplitFacePairPolicy.resolve(names,List.of("Night"),List.of("Day"))==null,"Ambiguous pair rejected");
        check(TitleReadingPolicy.isFooterOrRulesNoise("player"),"Generic rules word rejected");
        System.out.println("Split-face pair title recovery PASS");
    }
}
