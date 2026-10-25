#!/usr/bin/env python3
"""Compile production name normalizer against light Android mocks and test OCR aliases."""
from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[1]
stubs={
 'android/content/Context.java': '''package android.content;import java.io.File; public class Context {public File getFilesDir(){return new File(System.getProperty("java.io.tmpdir"),"mv-title-check");}}''',
 'android/util/Log.java': '''package android.util;public class Log {public static int w(String a,String b,Throwable t){return 0;}}''',
 'org/json/JSONArray.java': '''package org.json; public class JSONArray {String[] names={"Æther Adept","Invasion of Muraganda // Primordial Plasm","Webstrike Elite","Jace"}; public JSONArray(String s){} public int length(){return names.length;} public String optString(int i,String d){return names[i];} public JSONObject optJSONObject(int i){return null;}}''',
 'org/json/JSONObject.java': '''package org.json;public class JSONObject {public JSONObject(){} public JSONObject(String s){} public JSONArray optJSONArray(String n){return null;} public boolean optBoolean(String k,boolean d){return d;} public String optString(String k,String d){return d;}}''',
 'com/manavault/app/NativePrintingIndex.java': '''package com.manavault.app;public class NativePrintingIndex {public static String status(){return "Not initialized";} public static String lookup(String key,String text,int max){return "{}";}}''',
 'com/manavault/app/NameLayoutSmoke.java': '''package com.manavault.app;import java.io.*;public class NameLayoutSmoke {static void check(boolean b,String m){if(!b)throw new AssertionError(m);}public static void main(String[] a)throws Exception{File dir=new File(System.getProperty("java.io.tmpdir"),"mv-title-check");dir.mkdirs();try(FileOutputStream f=new FileOutputStream(new File(dir,"native-card-names.json"))){f.write("[1]".getBytes());}NativeNameIndex ix=new NativeNameIndex(new android.content.Context());check("Æther Adept".equals(ix.exact("Æther Adept")),"ligature");check("Æther Adept".equals(ix.exact("Aether Adept")),"transliterated");check("Æther Adept".equals(ix.exact("Ether Adept")),"missing-a OCR");check("Invasion of Muraganda // Primordial Plasm".equals(ix.exact("OInvasion of Muraganda")),"spurious leading O");check("Webstrike Elite".equals(ix.exact("WEBSTRIKE"+" "+"ELITE")),"two line title");check("Jace".equals(ix.exact("JACE")),"Jace");check(ix.exact("Some unrelated card")==null,"not fuzzy guessed");check(ix.exact("Of the Coast")==null,"footer not a title");check(ix.exact("DFT *ENSAM McKENZIE")==null,"footer mark not a title");check("Webstrike Elite".equals(ix.distinctiveTitlePrefix("WEBSTRIKE")),"unique stylized title prefix");new File(dir,"native-card-names.json").delete();System.out.println("v5.12.11 native OCR aliases compiled and passed");}}'''
}
with tempfile.TemporaryDirectory() as d:
 base=Path(d);paths=[r/'app/src/main/java/com/manavault/app/NativeNameIndex.java',r/'app/src/main/java/com/manavault/app/TitleReadingPolicy.java',r/'app/src/main/java/com/manavault/app/TitlePrefixMatcher.java',r/'app/src/main/java/com/manavault/app/FaceAliasPolicy.java',r/'app/src/main/java/com/manavault/app/TitleRecoveryPolicy.java',r/'app/src/main/java/com/manavault/app/SplitFacePairPolicy.java']
 for name,code in stubs.items():
  p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code);paths.append(p)
 cls=base/'class';cls.mkdir()
 subprocess.run(['javac','-d',str(cls),*[str(p) for p in paths]],check=True)
 subprocess.run(['java','-cp',str(cls),'com.manavault.app.NameLayoutSmoke'],check=True)
scanner=(r/'app/src/main/java/com/manavault/app/NativeScannerActivity.java').read_text()
quad=(r/'app/src/main/java/com/manavault/app/CardQuadDetector.java').read_text()
assert 'round(card.getWidth()*.956f)' in scanner
assert 'round(card.getHeight()*.235f)' in scanner
assert 'indexedTwoLineTitle(task.getResult(),names,strip.getHeight())' in scanner
assert 'getWidth()>corrected.getHeight()' not in scanner
assert 'boolean wasSideways=topEdge>sideEdge*1.12;' in scanner
assert 'always to portrait' in quad
print('v5.12.11 expanded title/landscape capture checks passed')
