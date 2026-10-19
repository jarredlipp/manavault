#!/usr/bin/env python3
"""Compile and test native symbol silhouette scorer without Android dependencies."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
r=Path(__file__).resolve().parents[1]
source=r/'app/src/main/java/com/manavault/app/SymbolShapeMatcher.java'
with TemporaryDirectory() as work:
    root=Path(work)
    dest=root/'com/manavault/app';dest.mkdir(parents=True)
    harness=dest/'MatcherTest.java'
    harness.write_text('''package com.manavault.app;
final class MatcherTest {
    static byte[] arrow(){byte[] mask=new byte[576];for(int y=4;y<20;y++)for(int x=4;x<20;x++)
        if(x>=4+(Math.abs(y-12)/2)&&x<=19-Math.abs(y-12)/3)mask[y*24+x]=(byte)255;
        return mask;}
    static byte[] horizontal(){byte[] mask=new byte[576];for(int y=9;y<15;y++)for(int x=3;x<21;x++)mask[y*24+x]=(byte)255;return mask;}
    static int[] gray(byte[] mask){int[] gray=new int[576];for(int i=0;i<576;i++)gray[i]=(mask[i]&255)>100?20:225;return gray;}
    public static void main(String[] args){
        byte[] a=arrow(),b=horizontal();
        byte[] extracted=SymbolShapeMatcher.extractInk(gray(a));
        if(extracted==null)throw new AssertionError("Ink was not extracted");
        double good=SymbolShapeMatcher.score(a,extracted),wrong=SymbolShapeMatcher.score(b,extracted);
        if(good<.94 || good<wrong+.14)throw new AssertionError("Visual ranking error "+good+"/"+wrong);
        int[] blank=new int[576];java.util.Arrays.fill(blank,120);
        if(SymbolShapeMatcher.extractInk(blank)!=null)throw new AssertionError("Blank type bar accepted");
        System.out.println("Set silhouette matching: correct="+good+", incorrect="+wrong);
    }
}
''')
    subprocess.run(['javac','-d',str(root),str(source),str(harness)],check=True,capture_output=True,text=True)
    completed=subprocess.run(['java','-cp',str(root),'com.manavault.app.MatcherTest'],check=False,capture_output=True,text=True)
    print(completed.stdout.strip(),completed.stderr.strip());assert completed.returncode==0
