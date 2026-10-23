#!/usr/bin/env python3
"""Compile and exercise the native token-name fallback without Android SDK."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/com/manavault/app/NativeNameIndex.java'
stubs={
 'android/content/Context.java': '''package android.content;import java.io.File; public class Context { public File getFilesDir(){return new File(System.getProperty("java.io.tmpdir"),"mv-name-test-empty");} }''',
 'android/util/Log.java': '''package android.util;public class Log { public static int w(String a,String b,Throwable c){return 0;} }''',
 'org/json/JSONArray.java': '''package org.json;public class JSONArray { public JSONArray(){} public JSONArray(String s){} public int length(){return 1;} public String optString(int i,String d){return d;} public JSONObject optJSONObject(int i){return new JSONObject();} }''',
 'org/json/JSONObject.java': '''package org.json;public class JSONObject { public JSONObject(){}public JSONObject(String s){}public JSONArray optJSONArray(String n){return new JSONArray();}public boolean optBoolean(String k,boolean d){return d;}public String optString(String k,String d){return k.equals("name")?"Jace":d;} }''',
 'com/manavault/app/NativePrintingIndex.java': '''package com.manavault.app;public class NativePrintingIndex { public static String status(){return "Ready: 118893 offline native printings";}public static String lookup(String k,String n,int m){if(!k.equals("name")||!n.equals("jace"))throw new AssertionError("Wrong token lookup");return "{}";} }''',
 'com/manavault/app/NameTokenSmoke.java': '''package com.manavault.app; public class NameTokenSmoke {public static void main(String[] args){NativeNameIndex x=new NativeNameIndex(new android.content.Context());if(!"Jace".equals(x.exact("JACE")))throw new AssertionError("Jace token not recovered");if(!"Jace".equals(x.exact("Jace")))throw new AssertionError("Jace token not cached");System.out.println("Native name index: token-only Jace fallback compiled and passed.");} }'''
}
with tempfile.TemporaryDirectory(prefix='mv-token-java-') as d:
 base=Path(d);files=[source,root/'app/src/main/java/com/manavault/app/TitleReadingPolicy.java',root/'app/src/main/java/com/manavault/app/TitlePrefixMatcher.java']
 for name,code in stubs.items():
  dest=base/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(code);files.append(dest)
 cls=base/'classes';cls.mkdir()
 subprocess.run(['javac','-d',str(cls),*[str(f) for f in files]],check=True)
 subprocess.run(['java','-cp',str(cls),'com.manavault.app.NameTokenSmoke'],check=True)
