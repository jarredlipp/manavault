package com.manavault.app;
public final class CaptureIdentityPolicyTest {
    private static void check(boolean valid,String label){if(!valid)throw new AssertionError(label);}
    private static byte[] rgb(int value){byte[] result=new byte[192];java.util.Arrays.fill(result,(byte)value);return result;}
    public static void main(String[] args){
        long base=0x1122334455667788L;
        // The actual reported false positive: different title, similar artwork
        // hash, but independent title/full/color observations differ.
        check(!CaptureIdentityPolicy.needsIdentityReview("Stillness in Motion","Endless Sands",
             base,base,0x3333L,0x9999L,0x122334455L,0x955223344L,rgb(10),rgb(95)),
             "different new card must not be rejected by artwork collision");
        // A mismatched OCR title on the identical photographic frame is a
        // REVIEW event; it must never be auto-saved or silently discarded.
        check(CaptureIdentityPolicy.needsIdentityReview("Depraved Harvester","There // They're // Their",
             base,base,0x12345L,0x12345L,0x45678L,0x45678L,rgb(60),rgb(61)),
             "identical photo plus contradictory title needs review");
        check(!CaptureIdentityPolicy.needsIdentityReview("Shining Armor","Shining Armor",
             base,base,0L,0L,0L,0L,rgb(12),rgb(12)),
             "identical printed names are already handled by explicit copy controls");
        check(!CaptureIdentityPolicy.needsIdentityReview("Depraved Harvester","There // They're // Their",
             base,base,0x12345L,0x12345L,0x45678L,0x45678L,rgb(2),rgb(112)),
             "color discrepancy means a new physical card cannot be marked identical");
        System.out.println("Independent capture identity policy checks passed");
    }
}
