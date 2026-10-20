package com.manavault.app;

import java.text.Normalizer;
import java.util.Locale;

/** Reject non-title text before it can become a scanner identity.
 * The real collector/set footer is parsed by PrintingFingerprintVerifier,
 * not the title recognizer. Pure Java so it can be regression-tested. */
final class TitleReadingPolicy {
    private TitleReadingPolicy() {}
    static String key(String observed){
        return Normalizer.normalize(observed==null?"":observed,Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+"," ").replaceAll("\\s+"," ").trim();
    }
    static boolean isFooterOrRulesNoise(String observed){
        final String k=key(observed);
        if(k.isEmpty()||k.equals("unrecognized title"))return true;
        if(k.matches("^(?:of the coast|the coast|wizards of the coast|wizards|copyright|artist|illustrated by|illus|illustration)(?: .*)?$"))return true;
        if(k.matches(".*\\b(?:19|20)\\d{2}\\b.*") && k.matches(".*\\b(?:wizards|coast|inc|llc|copyright|illus)\\b.*"))return true;
        // Set/language/artist footer, e.g. DFT *EN SAM McKENZIE.
        // OCR sometimes joins EN and the artist's first name (ENSAM).
        if(k.matches("^[a-z0-9]{2,5} (?:en|jp|fr|de|it|es|pt|ru|ko|zhs|zht)[a-z]*\\b(?: .*)?$"))return true;
        if(k.matches("^(?:[cmrut] )?\\d{2,5}(?: \\d{2,5})?(?: [a-z0-9]{2,5})?(?: en)?$"))return true;
        if(k.matches("^(?:creature|enchantment|instant|sorcery|artifact|planeswalker|battle|land)(?: .*)?"))return true;
        // Rules text is never promoted to card identity, even if a phrase happens
        // to be a real card name. Only an explicitly observed title may match.
        if(k.matches("^(?:cycling|haste|reach|lifelink|vigilance|trample|flying|deathtouch|first strike|double strike|menace|flash|ward|equip|unleash|scry|surveil|prowess|hexproof|indestructible)(?: [0-9wubrgcx ]*)?$"))return true;
        if(k.startsWith("each other creature ")||k.startsWith("each creature you ")||k.startsWith("when this ")||k.startsWith("whenever this "))return true;
        if(k.startsWith("spells your opponents ")||k.startsWith("when you ")||k.startsWith("whenever you ")||k.startsWith("to one target "))return true;
        return false;
    }
}
