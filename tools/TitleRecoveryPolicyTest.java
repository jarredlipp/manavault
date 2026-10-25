package com.manavault.app;
import java.util.Arrays;
public class TitleRecoveryPolicyTest {
  static void yes(boolean c,String why){if(!c)throw new AssertionError(why);}
  public static void main(String[] args){
    yes("Tigereye Cameo".equals(TitleRecoveryPolicy.uniqueCandidate("Tigereye Camco",Arrays.asList("Tigereye Cameo","Tigereye Camouflage"))),"Cameo OCR repair");
    yes("Raggadragga, Goreguts Boss".equals(TitleRecoveryPolicy.uniqueCandidate("Raggadragga, Goreguts Boss 2",Arrays.asList("Raggadragga, Goreguts Boss"))),"trailing OCR digit");
    yes(TitleRecoveryPolicy.uniqueCandidate("Who",Arrays.asList("Who // What // When // Where // Why"))==null,"no one-word split names");
    yes(TitleRecoveryPolicy.uniqueCandidate("Black Waltz No",Arrays.asList("Black Waltz No. 3","Black Waltz No. 4"))==null,"no ambiguous unique correction");
    yes(TitleRecoveryPolicy.isBadMultiFaceGuess("Who // What // When // Where // Why",false),"multi-face OCR suspect");
    yes(!TitleReadingPolicy.allowAutomaticExactOrientation(false,90),"sideways OCR not proof of portrait title");
    yes(TitleReadingPolicy.allowAutomaticExactOrientation(false,0),"normal fast title accepted");
    System.out.println("Conservative title recovery and split-card identity guard PASS");
  }
}
