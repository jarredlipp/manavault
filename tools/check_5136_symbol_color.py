#!/usr/bin/env python3
"""JVM regression for colored set symbols missed by luminance-only thresholding."""
from pathlib import Path
import subprocess, tempfile
src=Path('app/src/main/java/com/manavault/app/SymbolShapeMatcher.java')
with tempfile.TemporaryDirectory(prefix='mv-symbol-5136-') as temp:
    code=Path(temp)/'SymbolColorTest.java'
    code.write_text('''package com.manavault.app;
public final class SymbolColorTest {
 static void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 public static void main(String[] args){
   int[] rgb=new int[576];int[] gray=new int[576];
   // Gold shape on mid-gray has a similar luma but different hue.
   int background=0xFF888888,ink=0xFFCF934B;
   for(int i=0;i<576;i++){int x=i%24,y=i/24;
     boolean in=((x>=8&&x<=15)&&(y>=5&&y<=18)) ||
         (y>=10&&y<=13&&x>=5&&x<=18);
     int c=in?ink:background;rgb[i]=c;
     gray[i]=(((c>>16)&255)*299+((c>>8)&255)*587+(c&255)*114)/1000;
   }
   byte[] chroma=SymbolShapeMatcher.extractColorInk(rgb);
   require(chroma!=null,"Color/luminance complementary ink detector missed sample");
   byte[] normalized=SymbolShapeMatcher.normalizeInk(chroma);
   require(normalized!=null,"Color mask failed to normalize");
   require(SymbolShapeMatcher.score(normalized,normalized)>.95,"Color silhouette no longer matches");
   int[] blank=new int[576];for(int i=0;i<576;i++)blank[i]=background;
   require(SymbolShapeMatcher.extractColorInk(blank)==null,"Blank frame became a symbol");
   int[] texture=new int[576];for(int i=0;i<576;i++)texture[i]=i%2==0?0xFF101010:0xFFF0F0F0;
   require(SymbolShapeMatcher.extractColorInk(texture)==null,"Texture covering type bar became a symbol");
   System.out.println("v5.13.6 symbol color/normalization/blank/texture tests PASS");
 }
}
''')
    subprocess.run(['javac','-d',temp,str(src),str(code)],check=True)
    subprocess.run(['java','-cp',temp,'com.manavault.app.SymbolColorTest'],check=True)
