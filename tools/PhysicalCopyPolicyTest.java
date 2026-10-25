package com.manavault.app;

public final class PhysicalCopyPolicyTest {
    static void require(boolean yes,String reason){if(!yes)throw new AssertionError(reason);}
    public static void main(String[] args){
        // One photographed Wardens must not turn into four saves even when
        // glare or slight stand movement repeatedly triggers a scene event.
        for(int i=0;i<3;i++)
            require(PhysicalCopyPolicy.needsCopyConfirmation("Wardens of the Cycle","Wardens of the Cycle"),
                "consecutive repeat must require explicit another-copy confirmation");
        require(PhysicalCopyPolicy.needsCopyConfirmation("Wardens of the Cycle","WARDENS   of  the CYCLE"),
            "case and spacing cannot bypass copy protection");
        require(PhysicalCopyPolicy.needsCopyConfirmation("Rakdos Shred-Freak","Rakdos Shred Freak"),
            "punctuation cannot bypass copy protection");
        require(!PhysicalCopyPolicy.needsCopyConfirmation("Wardens of the Cycle","Savannah Lions"),
            "different physical card must continue automatically");
        require(!PhysicalCopyPolicy.needsCopyConfirmation("","Wardens of the Cycle"),
            "first scan must not be blocked");
        System.out.println("Physical copy anti-double-count policy tests passed");
    }
}
