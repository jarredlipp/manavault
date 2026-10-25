package com.manavault.app;
/** Pure-JVM geometry contract: proves camera crops follow real split panels. */
public final class SplitCardLayoutPolicyTest {
    static void ok(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        float[][] portrait=SplitCardLayoutPolicy.panels(820,1145);
        ok(portrait.length==2,"two panels");
        ok(portrait[0][1]<.05 && portrait[1][1]>.49,"portrait splits must be upper/lower");
        ok(SplitCardLayoutPolicy.titleRotation(820,1145)==90,"sideways portrait title needs rotation");
        ok(SplitCardLayoutPolicy.zones(820,1145)[1].equals("split-title-lower"),"portrait zone labels");
        float[][] landscape=SplitCardLayoutPolicy.panels(1145,820);
        ok(landscape[0][0]<.05 && landscape[1][0]>.49,"rotated split has left/right faces");
        ok(SplitCardLayoutPolicy.titleRotation(1145,820)==0,"already-upright split text should not rotate");
        ok(SplitCardLayoutPolicy.zones(1145,820)[0].equals("split-title-left"),"landscape zone labels");
        System.out.println("v5.12.33 rotated split-card panel geometry PASSED");
    }
}
