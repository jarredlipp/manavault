#!/usr/bin/env python3
"""JVM guard for the Teysa/Captain America October 20 title-OCR regressions."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/com/manavault/app/TitleReadingPolicy.java'
native=(root/'app/src/main/java/com/manavault/app/NativeScannerActivity.java').read_text()
with tempfile.TemporaryDirectory() as temp:
    base=Path(temp)
    suite=base/'com/manavault/app/TitleRescueSmoke.java'
    suite.parent.mkdir(parents=True)
    suite.write_text('''package com.manavault.app;
public class TitleRescueSmoke {
  static void yes(boolean b,String msg){if(!b)throw new AssertionError(msg);}
  public static void main(String[] args){
    yes(TitleReadingPolicy.isFooterOrRulesNoise("Legendary Creature — Human Advisor"),"type line rejected");
    yes(!TitleReadingPolicy.isFooterOrRulesNoise("Land Tax"),"Land Tax not mistaken for a type line");
    yes(!TitleReadingPolicy.isFooterOrRulesNoise("Artifact Mutation"),"Artifact Mutation remains a legitimate title");
    yes(!TitleReadingPolicy.isFooterOrRulesNoise("Captain America, Living Legend"),"Marvel title accepted");
    yes(!TitleReadingPolicy.isFooterOrRulesNoise("Teysa, Envoy of Ghosts"),"Teysa title accepted");
    yes(TitleReadingPolicy.plausibleTitlePosition(.30f,false),"upper title band accepted");
    yes(!TitleReadingPolicy.plausibleTitlePosition(.69f,false),"art text must not be candidate");
    yes(TitleReadingPolicy.plausibleTitlePosition(.69f,true),"late title line if indexed");
    yes(TitleReadingPolicy.allowAutomaticExactOrientation(false,0),"portrait upright auto exact allowed");
    yes(!TitleReadingPolicy.allowAutomaticExactOrientation(false,180),"portrait inverted credit must not save");
    yes(!TitleReadingPolicy.allowAutomaticExactOrientation(false,90),"sideways title requires a separate battle/split proof");
    yes(TitleReadingPolicy.allowAutomaticExactOrientation(true,90),"sideways positive rotation allowed");
    yes(TitleReadingPolicy.allowAutomaticExactOrientation(true,270),"sideways reverse rotation allowed");
    yes(!TitleReadingPolicy.allowAutomaticExactOrientation(true,180),"sideways inverted false match blocked");
    yes(TitleReadingPolicy.allowTentativeOrientation(false,0),"portrait original allowed");
    yes(!TitleReadingPolicy.allowTentativeOrientation(false,1),"portrait inverted credit forbidden");
    yes(!TitleReadingPolicy.allowTentativeOrientation(false,2),"portrait rotated flavor forbidden");
    yes(TitleReadingPolicy.allowTentativeOrientation(true,0),"sideways first orientation allowed");
    yes(TitleReadingPolicy.allowTentativeOrientation(true,1),"sideways alternate orientation allowed");
    yes(!TitleReadingPolicy.allowTentativeOrientation(true,3),"sideways inverted credit forbidden");
    System.out.println("JVM title location/orientation regressions passed");
  }
}''')
    subprocess.run(['javac','-d',temp,str(source),str(suite)],check=True)
    subprocess.run(['java','-cp',temp,'com.manavault.app.TitleRescueSmoke'],check=True)
assert 'readTitle(corrected,hash,0,SystemClock.elapsedRealtime(),wasSideways)' in native
assert 'int[] rotations=sideways ? new int[]{90,270,0,180}' in native
assert 'sideways?.23f:.145f' in native and 'else if(sideways)h=Math.round(card.getHeight()*.32f);' in native
assert 'sideways || rotations[rotationIndex]%180!=0' in native
assert 'plausibleTitlePosition(position,canonical!=null)' in native
assert 'rotatedBattleCandidate(canonical)' in native and 'ocr-orientation-provisional' in native
print('Production native scanner OCR guard wiring passed')
