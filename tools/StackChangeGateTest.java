package com.manavault.app;

public final class StackChangeGateTest {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static long bits(int n){return n==64?-1L:(1L<<n)-1;}
    public static void main(String[] args){
        StackChangeGate gate=new StackChangeGate();
        gate.captured(0,0,1000);
        for(int i=0;i<12;i++)require(!gate.observe(0,0,2100+i*210),"Stationary card was scanned twice");
        // Brightness noise affects only one region, never enough to rearm.
        for(int i=0;i<8;i++)require(!gate.observe(bits(25),bits(2),4800+i*220),"Glare rearmed scanner");
        // A one-frame image flash or a continually moving hand is not new card.
        require(!gate.observe(bits(30),bits(28),6800),"Single changed frame rearmed scanner");
        require(!gate.observe(0,0,7000),"Glitch rearmed scanner");
        for(int i=0;i<6;i++){
            boolean ready=gate.observe(bits(27),bits(24),7400+i*200);
            require(ready==(i==3),"Stable different card not accepted at correct time: "+i);
            if(ready)break;
        }
        require("new top artwork".equals(gate.lastEvent()),"Wrong event type");
        gate.captured(bits(27),bits(24),8800);
        for(int i=0;i<8;i++)require(!gate.observe(bits(27),bits(24),9800+i*210),"No-change counted twice");
        // Occlusion dropout that does not change the scene must not rearm.
        for(int i=0;i<8;i++)gate.coverFrame(false);
        for(int i=0;i<7;i++)require(!gate.observe(bits(27),bits(24),12000+i*220),"Spurious occlusion");
        for(int i=0;i<4;i++)gate.coverFrame(true);
        for(int i=0;i<8;i++){
            boolean ready=gate.observe(bits(27),bits(24),14000+i*190);
            require(ready==(i==4),"Identical-cover sequence did not rearm as expected: "+i);
            if(ready)break;
        }
        require(gate.lastEvent().contains("identical"),"Identical-copy event not recorded");
        // Different card with a similar art texture but a different printed
        // title should also trigger, after multiple stable readings.
        gate.captured(0,0,0,16000);
        for(int i=0;i<4;i++){
            boolean ready=gate.observe(bits(11),bits(6),bits(24),17100+i*220);
            require(ready==(i==3),"Title-assisted stack change failed: "+i);
        }
        // A title-only lighting flare is not an independent scene change.
        gate.captured(0,0,0,18500);
        for(int i=0;i<7;i++)require(!gate.observe(0,0,bits(30),19500+i*200),"Title glare falsely rearmed scanner");
        System.out.println("StackChangeGate JVM tests passed: stationary, glare, transient, changed art, repeated, occlusion, identical copy.");
    }
}
