#!/usr/bin/env python3
"""Compile CardQuadDetector against minimal API stubs to catch Java errors fast.

This is a source smoke test, NOT an Android/OpenCV integration test.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/com/manavault/app/CardQuadDetector.java'
STUBS = {
    'android/graphics/Bitmap.java': '''package android.graphics;
public class Bitmap {
  public enum Config { ARGB_8888 }
  public int getWidth(){return 100;} public int getHeight(){return 100;}
  public void recycle(){} 
  public static Bitmap createBitmap(Bitmap b,int x,int y,int w,int h){return new Bitmap();}
  public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap();}
}''',
    'android/graphics/PointF.java': '''package android.graphics;
public class PointF { public float x,y; public PointF(float x,float y){this.x=x;this.y=y;} }''',
    'org/opencv/android/Utils.java': '''package org.opencv.android;
import android.graphics.Bitmap; import org.opencv.core.Mat;
public class Utils { public static void bitmapToMat(Bitmap b,Mat m){} public static void matToBitmap(Mat m,Bitmap b){} }''',
    'org/opencv/core/Mat.java': '''package org.opencv.core;
public class Mat { public int cols(){return 200;} public int rows(){return 300;}
public long total(){return 60000;} public int get(int x,int y,byte[] b){return 0;}
public void release(){} }''',
    'org/opencv/core/MatOfPoint.java': '''package org.opencv.core;
public class MatOfPoint extends Mat { public MatOfPoint(Point... p){} public Point[] toArray(){return new Point[4];} }''',
    'org/opencv/core/MatOfPoint2f.java': '''package org.opencv.core;
public class MatOfPoint2f extends Mat { public MatOfPoint2f(Point... p){} public Point[] toArray(){return new Point[4];} }''',
    'org/opencv/core/Point.java': '''package org.opencv.core;
public class Point { public double x,y; public Point(double x,double y){this.x=x;this.y=y;} }''',
    'org/opencv/core/Scalar.java': '''package org.opencv.core;
public class Scalar { public double[] val={100}; }''',
    'org/opencv/core/Size.java': '''package org.opencv.core;
public class Size { public Size(){} public Size(double w,double h){} }''',
    'org/opencv/core/Core.java': '''package org.opencv.core;
public class Core { public static Scalar mean(Mat m){return new Scalar();} }''',
    'org/opencv/core/CvType.java': '''package org.opencv.core;
public class CvType {}''',
    'org/opencv/imgproc/CLAHE.java': '''package org.opencv.imgproc;
import org.opencv.core.Mat;
public class CLAHE { public void apply(Mat a,Mat b){} public void collectGarbage(){} }''',
    'org/opencv/imgproc/Imgproc.java': '''package org.opencv.imgproc;
import org.opencv.core.*; import java.util.List;
public class Imgproc {
  public static final int INTER_AREA=1,COLOR_RGBA2GRAY=2,COLOR_GRAY2RGBA=3,
    MORPH_RECT=4,RETR_LIST=5,CHAIN_APPROX_SIMPLE=6,INTER_LINEAR=7;
  public static void resize(Mat a,Mat b,Size c,double d,double e,int f){}
  public static void cvtColor(Mat a,Mat b,int c){}
  public static void GaussianBlur(Mat a,Mat b,Size c,double d){}
  public static void Canny(Mat a,Mat b,double c,double d){}
  public static Mat getStructuringElement(int t,Size s){return new Mat();}
  public static void dilate(Mat a,Mat b,Mat c){}
  public static void findContours(Mat a,List<MatOfPoint> b,Mat c,int d,int e){}
  public static double contourArea(MatOfPoint p){return 0;}
  public static void approxPolyDP(MatOfPoint2f a,MatOfPoint2f b,double c,boolean d){}
  public static double arcLength(MatOfPoint2f a,boolean b){return 0;}
  public static boolean isContourConvex(MatOfPoint p){return true;}
  public static Mat getPerspectiveTransform(MatOfPoint2f a,MatOfPoint2f b){return new Mat();}
  public static void warpPerspective(Mat a,Mat b,Mat c,Size s,int i){}
  public static CLAHE createCLAHE(double d,Size s){return new CLAHE();}
}''',
    'com/manavault/app/CardQuadDetectorSmoke.java': '''package com.manavault.app;
import org.opencv.core.Point;
public class CardQuadDetectorSmoke {
  public static void main(String[] a){
    Point[] inner={new Point(60,60),new Point(140,60),new Point(140,160),new Point(60,160)};
    byte[] clear=new byte[200*220];
    if(CardQuadDetector.hasEnclosingCardEdge(clear,200,220,inner))
      throw new AssertionError("Empty edges must not reject card");
    // Simulated outer border around an interior artwork rectangle.
    byte[] outline=new byte[200*220];
    for(int y=54;y<=166;y++)for(int x=54;x<=146;x++)
      if(x==54||x==146||y==54||y==166) outline[y*200+x]=(byte)255;
    if(!CardQuadDetector.hasEnclosingCardEdge(outline,200,220,inner))
      throw new AssertionError("Enclosing border should be detected");
    System.out.println("CardQuadDetector compiled; edge perimeter checks passed.");
  }
}''',
}

with tempfile.TemporaryDirectory(prefix='manavault-quad-javac-') as temp:
    root=Path(temp)
    sources=[SOURCE]
    for name, body in STUBS.items():
        p=root/name
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(body)
        sources.append(p)
    classes=root/'classes'
    classes.mkdir()
    subprocess.run(['javac','-d',str(classes),*[str(p) for p in sources]],check=True)
    subprocess.run(['java','-cp',str(classes),'com.manavault.app.CardQuadDetectorSmoke'],check=True)
print('Java source smoke test passed (Android APK still requires Gradle).')
