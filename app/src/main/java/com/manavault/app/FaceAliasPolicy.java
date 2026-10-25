package com.manavault.app;

import java.text.Normalizer;
import java.util.Locale;
/** Safe offline lookup aliases for genuine single-face names on modal/transform cards.
 * One-word split faces must not convert flavor text into unrelated identities. */
final class FaceAliasPolicy {
    private FaceAliasPolicy(){}
    static boolean safeFaceAlias(String face){
        String k=Normalizer.normalize(face==null?"":face,Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+"," ").trim();
        if(k.isEmpty())return false;
        String[] words=k.split(" +");
        if(words.length>=2)return k.length()>=7 && face.trim().matches(".*\\s+.*");
        return k.length()>=9 && !java.util.Arrays.asList(
            "therefore","whenever","everywhere","otherwise").contains(k);
    }
}
