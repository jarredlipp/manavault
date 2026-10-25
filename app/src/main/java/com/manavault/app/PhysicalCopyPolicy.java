package com.manavault.app;

import java.text.Normalizer;
import java.util.Locale;

/** No automatic double-counting of a consecutive saved card identity.
 * Deliberately requires physical user confirmation for another identical copy.
 * Independent of Android so the policy can be exercised on the JVM. */
final class PhysicalCopyPolicy {
    private PhysicalCopyPolicy() {}
    static String identity(String name){
        return Normalizer.normalize(name==null?"":name,Normalizer.Form.NFKD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .replaceAll("\\s+", " ").trim();
    }
    static boolean needsCopyConfirmation(String previousSavedName,String proposedName){
        String a=identity(previousSavedName),b=identity(proposedName);
        return !a.isEmpty() && !b.isEmpty() && a.equals(b);
    }
}
