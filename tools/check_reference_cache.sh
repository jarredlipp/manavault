#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/android/content" "$TMP/android/util" "$TMP/com/manavault/app"
cat > "$TMP/android/content/Context.java" <<'JAVA'
package android.content;
public class Context { private final java.io.File dir;
  public Context(java.io.File dir){this.dir=dir;}
  public java.io.File getFilesDir(){return dir;}
}
JAVA
cat > "$TMP/android/util/Log.java" <<'JAVA'
package android.util;
public class Log { public static int w(String s,String t){return 0;}
  public static int w(String s,String t,Throwable ex){return 0;} }
JAVA
cat > "$TMP/com/manavault/app/CheckCache.java" <<'JAVA'
package com.manavault.app;
import android.content.Context;
import java.io.File;
public final class CheckCache {
 public static void main(String[] args) {
  File root = new File(args[0]); root.mkdirs();
  ReferenceFingerprintCache.configure(new Context(root));
  String url="https://cards.scryfall.io/art_crop/front/f/f/example.jpg";
  if(args.length>1 && "restart".equals(args[1])) {
    if(ReferenceFingerprintCache.count()!=3 || ReferenceFingerprintCache.get("art",url)[0]!=.25 || ReferenceFingerprintCache.get("symbol",url)[1]!=-.33 || ReferenceFingerprintCache.get("symbol-v2",url)[1727]!=.75)
      throw new Error("Fingerprints were not available after simulated app restart");
    System.out.println("Disk fingerprints survived process restart");
    return;
  }
  double[] a=new double[576];a[0]=0.25;a[1]=-0.33;
  if(ReferenceFingerprintCache.get("art",url)!=null)throw new Error("Unexpected preexisting reference");
  ReferenceFingerprintCache.put("art",url,a);
  double[] b=ReferenceFingerprintCache.get("art",url);
  if(b==null||b[0]!=.25||b[1]!=-.33||ReferenceFingerprintCache.count()!=1)throw new Error("Persisted art read failed");
  if(ReferenceFingerprintCache.get("symbol",url)!=null)throw new Error("Art/symbol cache collision");
  ReferenceFingerprintCache.put("symbol",url,a);
  if(ReferenceFingerprintCache.count()!=2)throw new Error("Separate symbol not saved");
  double[] newSymbol = new double[1728];newSymbol[1727]=.75;
  ReferenceFingerprintCache.put("symbol-v2",url,newSymbol);
  if(ReferenceFingerprintCache.count()!=3 || ReferenceFingerprintCache.get("symbol-v2",url)[1727]!=.75)
    throw new Error("v2 expansion-symbol signatures must persist at length 1728");
  double[] invalid=a.clone();invalid[0]=Double.NaN;
  ReferenceFingerprintCache.put("art",url,invalid);
  if(ReferenceFingerprintCache.get("art",url)[0]!=.25)throw new Error("Non-finite entry accepted");
  System.out.println("Persistent printing fingerprint cache passed: disk read, isolation and validation");
 }
}
JAVA
javac -d "$TMP" "$TMP/android/content/Context.java" "$TMP/android/util/Log.java" \
  app/src/main/java/com/manavault/app/ReferenceFingerprintCache.java \
  "$TMP/com/manavault/app/CheckCache.java"
java -cp "$TMP" com.manavault.app.CheckCache "$TMP/testdata"
java -cp "$TMP" com.manavault.app.CheckCache "$TMP/testdata" restart
