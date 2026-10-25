package com.manavault.app;
import java.util.List;
import java.util.HashSet;
final class ArtworkProbePolicyTest {
 public static void main(String[] args) {
  long h=0x123456789ABCDEF0L;
  List<int[]> exact=ArtworkProbePolicy.probes(new long[]{h},false);
  List<int[]> fuzzy=ArtworkProbePolicy.probes(new long[]{h},true);
  if(exact.size()!=8 || fuzzy.size()!=72)throw new AssertionError("Probe size: "+exact.size()+" "+fuzzy.size());
  HashSet<Integer> seen=new HashSet<>();
  for(int[] key:fuzzy) {
   int pair=(key[0]<<8)|key[1];
   if(!seen.add(pair))throw new AssertionError("Duplicate probe");
   if(key[0]<0||key[0]>7||key[1]<0||key[1]>255)throw new AssertionError("Bad key");
  }
  for(int band=0;band<8;band++){
   int original=(int)((h >>> (band*8))&255);
   for(int bit=0;bit<8;bit++)
    if(!seen.contains((band<<8)|(original^(1<<bit))))
     throw new AssertionError("Missing one-bit neighbor");
  }
  long shifted=0xFF22334455667788L;
  if(ArtworkProbePolicy.probes(new long[]{h,shifted},true).size()<=fuzzy.size())
   throw new AssertionError("Shifted camera crop not indexed");
  if(ArtworkProbePolicy.strongAdvisoryName(.951,.70))throw new AssertionError("Weak name accepted");
  if(ArtworkProbePolicy.strongAdvisoryName(.98,.95))throw new AssertionError("Ambiguous name accepted");
  if(!ArtworkProbePolicy.strongAdvisoryName(.99,.80))throw new AssertionError("Strong advisory rejected");
  System.out.println("Indexed approximate artwork probes and strict advisory gate PASSED");
 }
}
