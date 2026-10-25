package com.manavault.app;
import java.util.Arrays;
final class CollectorOcrPolicyTest {
 public static void main(String[] args){
  var inv=Arrays.asList("314","T0007");
  if(!CollectorOcrPolicy.hasCandidateNumber("314/350",inv))throw new AssertionError("Old border numerator lost");
  if(!CollectorOcrPolicy.hasCandidateNumber("R0345 DFT EN",Arrays.asList("345")))throw new AssertionError("Premium footer lost");
  if(!CollectorOcrPolicy.hasCandidateNumber("T0007/280",inv))throw new AssertionError("Token prefix lost");
  if(CollectorOcrPolicy.hasCandidateNumber("(c) 1993/2000 Wizards of the Coast Inc.",Arrays.asList("1993")))
   throw new AssertionError("Copyright became collector");
  if(CollectorOcrPolicy.hasCandidateNumber("It deals 314 damage",Arrays.asList("314")))
   throw new AssertionError("Rules text became collector");
  if(CollectorOcrPolicy.hasCandidateNumber("R 0320 FIN",Arrays.asList("214")))
   throw new AssertionError("Wrong printed number accepted");
  if(!"345".equals(CollectorOcrPolicy.normalize("00345")))throw new AssertionError("Leading zeros not normalized");
  System.out.println("Candidate-scoped collector OCR and copyright exclusion PASSED");
 }
}
