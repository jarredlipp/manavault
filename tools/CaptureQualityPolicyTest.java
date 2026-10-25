package com.manavault.app;
public class CaptureQualityPolicyTest {
 public static void main(String[] ignored){
  int w=48,h=64;int[] solid=new int[w*h];java.util.Arrays.fill(solid,0xffffffff);
  CaptureQualityPolicy.Metrics bright=CaptureQualityPolicy.measure(solid,w,h);
  if(bright.glarePercent!=100 || !bright.hint().contains("Glare"))throw new AssertionError("white-out test");
  int[] detail=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){
    int a=(x/4+y/4)%2==0?70:190;detail[y*w+x]=0xff000000|a<<16|a<<8|a;
  }
  CaptureQualityPolicy.Metrics normal=CaptureQualityPolicy.measure(detail,w,h);
  if(normal.edgeScore<=0 || normal.glarePercent!=0 || !normal.hint().contains("nominal"))throw new AssertionError("balanced-image test");
  System.out.println("Capture image diagnostics PASS");
 }
}
