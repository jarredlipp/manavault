package com.manavault.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure-Java search keys for approximate offline card-art retrieval.
 * An expanded probe is a CANDIDATE SUGGESTION, never printing verification.
 */
final class ArtworkProbePolicy {
    private ArtworkProbePolicy() {}
    /** Compact (band, code) pairs. Each original 8-bit band may tolerate one
     * changed bit. A second shifted photograph hash can supply more exact
     * probes without requiring a second SQLite artwork database. */
    static List<int[]> probes(long[] hashes, boolean tolerant) {
        LinkedHashSet<Integer> keys = new LinkedHashSet<>();
        if(hashes==null || hashes.length==0)return new ArrayList<>();
        for(int h=0;h<Math.min(5,hashes.length);h++){
            long hash=hashes[h];
            for(int band=0;band<8;band++){
                int value=(int)((hash >>> (8*band)) & 255);
                keys.add((band << 8)|value);
                // One-bit neighbors only for the true, unshifted central art.
                if(tolerant && h==0)for(int bit=0;bit<8;bit++)
                    keys.add((band << 8)|(value ^ (1<<bit)));
            }
        }
        ArrayList<int[]> result=new ArrayList<>(keys.size());
        for(int pair:keys)result.add(new int[]{pair >>> 8,pair & 255});
        return result;
    }
    /** Never allow weak visual evidence to masquerade as an exact name. */
    static boolean strongAdvisoryName(double first,double second){
        return first>=.955 && first-second>=.10;
    }
}
