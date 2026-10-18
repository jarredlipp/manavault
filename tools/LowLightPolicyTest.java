package com.manavault.app;
public class LowLightPolicyTest {
  static void eq(int a,int b){if(a!=b)throw new AssertionError(a+" != "+b);}
  public static void main(String[] args){
    eq(LowLightPolicy.next(0,39),1);
    eq(LowLightPolicy.next(1,39),2);
    eq(LowLightPolicy.next(2,39),3);
    eq(LowLightPolicy.next(3,39),3);
    eq(LowLightPolicy.next(0,75),1);
    eq(LowLightPolicy.next(2,75),2);
    eq(LowLightPolicy.next(2,124),2);
    eq(LowLightPolicy.next(2,182),1);
    eq(LowLightPolicy.next(0,182),0);
    eq(LowLightPolicy.next(0,Double.NaN),0);
    System.out.println("Low light policy: PASS");
  }
}
