package com.manavault.app;
import java.util.*;
/** v5.13.2: the wide, indexed rescue must remain finite and advisory. */
public final class ArtworkProbePolicyTest {
    private static void require(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    public static void main(String[] args){
        long hash=0x123456789abcdef0L;
        List<int[]> narrow=ArtworkProbePolicy.probes(new long[]{hash},false);
        List<int[]> wide=ArtworkProbePolicy.twoBitProbes(hash);
        require(narrow.size()==8,"original exact bands changed");
        require(wide.size()==296,"8*(1+8+28) Hamming-2 indexed probes required");
        Set<String> unique=new HashSet<>();
        for(int[] row:wide){
            require(row.length==2 && row[0]>=0&&row[0]<8 && row[1]>=0&&row[1]<256,
                "illegal band or byte code");
            require(unique.add(row[0]+":"+row[1]),"duplicate indexed artwork probe");
        }
        for(int[] row:narrow)require(unique.contains(row[0]+":"+row[1]),
            "wide search must contain every exact band");
        require(!ArtworkProbePolicy.strongAdvisoryName(.94,.50),"weak image cannot auto-authorize name");
        require(!ArtworkProbePolicy.strongAdvisoryName(.96,.94),"ambiguous image cannot auto-authorize name");
        System.out.println("v5.13.2 full-art candidate probe bounds PASS");
    }
}
