package com.manavault.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.DisplayCutout;
import android.os.Build;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.AspectRatio;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** v5.9: native live-edge tracking, artwork-first candidates, OCR fallback, continuous
 * scene-gated collection scanning. Printing confirmation remains in the WebView.
 */
public final class NativeScannerActivity extends ComponentActivity {
    private static final String TAG="ManaVaultNative";
    private static final int PERMISSION_REQUEST=302;
    private final ExecutorService analysisExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService referenceExecutor=Executors.newSingleThreadExecutor();
    private final CardQuadDetector detector=new CardQuadDetector();
    private final AtomicBoolean recognizing=new AtomicBoolean(false);
    private final ArrayList<JSONObject> captured=new ArrayList<>();
    private PreviewView preview;
    private CardEdgeOverlay overlay;
    private TextView status,metrics;
    private Button torchButton,reviewButton,keepButton;
    private Camera camera;
    private ProcessCameraProvider provider;
    private TextRecognizer recognizer;
    private volatile VisualMatcher visual;
    private volatile NativeNameIndex names;
    private volatile boolean closing=false;
    private boolean torch=false;
    private CardQuadDetector.Detection previous;
    private volatile CardQuadDetector.Detection smoothed;
    private int goodFrames=0,missingFrames=0,changeFrames=0;
    private long lastGoodAt=0,lastProcessAt=0,lastRecognitionAt=0,sessionStart=0;
    private volatile long lastCapturedHash=0,lastCapturedSceneHash=0;
    private long sceneStartedAt=0;
    private long missingSince=0;
    private int changedEmptyFrames=0;
    private volatile long currentFrameSceneHash=0;
    private volatile PointF[] lastCapturedCorners=null;
    private volatile double lastCapturedBrightness=0,currentCardBrightness=0;
    private volatile boolean waitingForNext=false;
    private String previousOcr="",lastSeenTitle="";
    private int previousOcrCount=0;
    private long previewHash=0;
    private volatile Bitmap availableCard;
    private long lastEdgeMs=0,lastWarpMs=0,lastArtMs=0,lastOcrMs=0;
    private int sourceWidth=1,sourceHeight=1;
    private FrameLayout cameraRoot;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        // Camera must occupy the *entire* physical display. Merely placing a
        // PreviewView MATCH_PARENT inside a normal Activity leaves reserved
        // status/navigation-bar regions and cuts across the overlay controls.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sessionStart=SystemClock.elapsedRealtime();
        analysisExecutor.execute(()->{
            try{
                File[] photos=getCacheDir().listFiles((dir,name)->name.startsWith("native-scan-")&&name.endsWith(".jpg"));
                if(photos!=null)for(File photo:photos)if(System.currentTimeMillis()-photo.lastModified()>7L*86400000L)photo.delete();
            }catch(Exception e){Log.w(TAG,"Could not clean old scan previews",e);}
        });
        preview=new PreviewView(this);
        preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        overlay=new CardEdgeOverlay(this);
        FrameLayout root=new FrameLayout(this);cameraRoot=root;root.setBackgroundColor(Color.BLACK);
        root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14),dp(12),dp(14),dp(12));top.setBackgroundColor(0xA5000000);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(15);
        status.setText("Native CameraX · starting…");top.addView(status);
        metrics=new TextView(this);metrics.setTextColor(0xFFBBDBCC);metrics.setTextSize(11);
        metrics.setText("Timing: waiting for frames");top.addView(metrics);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(6),dp(8),dp(6),dp(16));actions.setBackgroundColor(0xD0000000);
        Button back=new Button(this);back.setText("Close");back.setAllCaps(false);back.setTextSize(12);back.setMinWidth(0);back.setMinimumWidth(0);
        back.setOnClickListener(v->finishBatch());
        actions.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));
        torchButton=new Button(this);torchButton.setText("Light off");torchButton.setAllCaps(false);torchButton.setTextSize(12);torchButton.setMinWidth(0);torchButton.setMinimumWidth(0);
        torchButton.setOnClickListener(v->toggleTorch());
        actions.addView(torchButton,new LinearLayout.LayoutParams(0,dp(48),1));
        keepButton=new Button(this);keepButton.setText("Keep title");keepButton.setAllCaps(false);keepButton.setTextSize(12);keepButton.setMinWidth(0);keepButton.setMinimumWidth(0);keepButton.setEnabled(false);
        keepButton.setOnClickListener(v->{
            Bitmap card=availableCard;
            if(waitingForNext){
                waitingForNext=false;sceneStartedAt=SystemClock.elapsedRealtime();
                previousOcr="";previousOcrCount=0;goodFrames=0;
                status.setText("Next card manually enabled · show a different card");
                keepButton.setText("Keep title");keepButton.setEnabled(false);
            }else if(card!=null&&!card.isRecycled()&&!lastSeenTitle.isEmpty())
                enqueue(lastSeenTitle,"manual-title",0,null,card,previewHash);
        });
        actions.addView(keepButton,new LinearLayout.LayoutParams(0,dp(48),1));
        reviewButton=new Button(this);reviewButton.setText("Finish / save (0)");reviewButton.setAllCaps(false);reviewButton.setTextSize(12);reviewButton.setMinWidth(0);reviewButton.setMinimumWidth(0);
        reviewButton.setOnClickListener(v->finishBatch());
        actions.addView(reviewButton,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        // In immersive mode system bars are hidden. Reserve *only* camera-cutout
        // safe area, not the old status/nav bar heights (which caused the overlap
        // and reduced the useful preview area on Samsung gesture navigation).
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int topSafe=0,bottomSafe=0,leftSafe=0,rightSafe=0;
            if(Build.VERSION.SDK_INT>=28){
                DisplayCutout cutout=insets.getDisplayCutout();
                if(cutout!=null){
                    topSafe=cutout.getSafeInsetTop();
                    bottomSafe=cutout.getSafeInsetBottom();
                    leftSafe=cutout.getSafeInsetLeft();
                    rightSafe=cutout.getSafeInsetRight();
                }
            }
            top.setPadding(dp(14)+leftSafe,dp(12)+topSafe,dp(14)+rightSafe,dp(12));
            actions.setPadding(dp(6)+leftSafe,dp(8),dp(6)+rightSafe,dp(16)+bottomSafe);
            return insets;
        });
        setContentView(root);
        applyImmersiveCamera();
        root.requestApplyInsets();
        if(!OpenCVLoader.initLocal()){
            status.setText("OpenCV unavailable; return to legacy browser scanner.");return;
        }
        referenceExecutor.execute(()->{
            try{
                names=new NativeNameIndex(getApplicationContext());
                VisualMatcher matcher=new VisualMatcher(getApplicationContext());
                visual=matcher;
                int refs=matcher.count();
                runOnUiThread(()->{
                    if(!closing)metrics.setText("Artwork references: "+refs+" · name index: "+(names==null?0:names.size())+
                        (refs==0?" · OCR fallback active (index not populated)":" · artwork-first enabled"));
                });
            }catch(Exception e){Log.w(TAG,"Artwork index loading failed, using OCR",e);}
        });
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},PERMISSION_REQUEST);
    }
    /** Re-hide bars when focus returns (permission dialogs and the torch can
     * otherwise cause them to reappear). Swipe still reveals bars transiently. */
    private void applyImmersiveCamera(){
        if(cameraRoot==null)return;
        WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(getWindow(),cameraRoot);
        if(controller!=null){
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }
    @Override protected void onResume(){super.onResume();applyImmersiveCamera();}
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)applyImmersiveCamera();
    }
    private int dp(int dip){return (int)(getResources().getDisplayMetrics().density*dip+.5f);}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==PERMISSION_REQUEST){
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startCamera();
            else status.setText("Camera permission denied. Use the browser scanner or grant access.");
        }
    }
    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(closing||isFinishing())return;
            try{
                provider=future.get();provider.unbindAll();
                Preview p=new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build();
                p.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis=new ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();
                analysis.setAnalyzer(analysisExecutor,this::analyzeFrame);
                camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis);
                status.setText("Ready · fast edge tracking · artwork/OCR");
            }catch(Exception e){Log.e(TAG,"CameraX startup failed",e);status.setText("Camera failed: "+e.getMessage());}
        },ContextCompat.getMainExecutor(this));
    }
    private void toggleTorch(){
        if(camera==null||!camera.getCameraInfo().hasFlashUnit()){
            status.setText("Camera flash control unavailable");return;
        }
        torch=!torch;camera.getCameraControl().enableTorch(torch);
        torchButton.setText(torch?"Light on":"Light off");
    }
    private static Bitmap uprightFrame(ImageProxy image){
        java.nio.ByteBuffer buffer=image.getPlanes()[0].getBuffer();buffer.rewind();
        int w=image.getWidth(),h=image.getHeight(),stride=image.getPlanes()[0].getRowStride();
        Bitmap raw=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        if(stride==w*4)raw.copyPixelsFromBuffer(buffer);
        else{
            byte[] row=new byte[w*4];java.nio.ByteBuffer packed=java.nio.ByteBuffer.allocateDirect(w*h*4);
            for(int y=0;y<h;y++){buffer.position(y*stride);buffer.get(row);packed.put(row);}
            packed.rewind();raw.copyPixelsFromBuffer(packed);
        }
        int rotation=image.getImageInfo().getRotationDegrees();
        if(rotation==0)return raw;
        Matrix m=new Matrix();m.postRotate(rotation);
        Bitmap out=Bitmap.createBitmap(raw,0,0,w,h,m,true);raw.recycle();return out;
    }
    private static float drift(CardQuadDetector.Detection a,CardQuadDetector.Detection b,int w,int h){
        if(a==null||b==null)return Float.MAX_VALUE;
        double total=0;for(int i=0;i<4;i++)total+=Math.hypot(a.corners[i].x-b.corners[i].x,a.corners[i].y-b.corners[i].y);
        return (float)(total/4/Math.hypot(w,h));
    }
    /** Sample the physical card region in camera coordinates, even if the
     *  contour detector temporarily stops finding edges. This catches removal
     *  from a bright stand without mistaking a single missed outline for it. */
    private static double regionBrightness(Bitmap frame,PointF[] corners){
        if(corners==null)return -1;
        double sum=0;int count=0,w=frame.getWidth(),h=frame.getHeight();
        for(int yi=1;yi<=10;yi++)for(int xi=1;xi<=8;xi++){
            float u=xi/9f,v=yi/11f;
            float x=(1-u)*(1-v)*corners[0].x+u*(1-v)*corners[1].x+
                    u*v*corners[2].x+(1-u)*v*corners[3].x;
            float y=(1-u)*(1-v)*corners[0].y+u*(1-v)*corners[1].y+
                    u*v*corners[2].y+(1-u)*v*corners[3].y;
            int ix=Math.round(x),iy=Math.round(y);
            if(ix<0||iy<0||ix>=w||iy>=h)continue;
            int c=frame.getPixel(ix,iy);
            sum+=(Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100.;
            count++;
        }
        return count>0?sum/count:-1;
    }
    /** Estimate the exposed stand around the previously detected card.
     * A flashlight/exposure change alone must not count as card removal. */
    private static double surroundingBrightness(Bitmap frame,PointF[] corners){
        if(corners==null)return -1;
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-1,maxY=-1;
        for(PointF p:corners){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);
            minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
        int w=frame.getWidth(),h=frame.getHeight();
        double pad=Math.max(8,Math.min(maxX-minX,maxY-minY)*.075);
        double sum=0;int count=0;
        for(int i=1;i<=12;i++){
            float t=i/13f;
            float x=minX+(maxX-minX)*t,y=minY+(maxY-minY)*t;
            int[][] points={{Math.round(x),(int)(minY-pad)},
                    {Math.round(x),(int)(maxY+pad)},
                    {(int)(minX-pad),Math.round(y)},
                    {(int)(maxX+pad),Math.round(y)}};
            for(int[] point:points){int px=point[0],py=point[1];
                if(px<0||py<0||px>=w||py>=h)continue;
                int c=frame.getPixel(px,py);
                sum+=(Color.red(c)*30+Color.green(c)*59+Color.blue(c)*11)/100.;count++;
            }
        }
        return count>=10?sum/count:-1;
    }
    /** Low-resolution fingerprint of the whole camera frame, separate from
     *  card-art hashes. An occasional missed border does NOT mean removal. */
    private static long wholeFrameHash(Bitmap frame){
        Bitmap reduced=Bitmap.createScaledBitmap(frame,9,8,true);
        long hash=0L;
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            int a=reduced.getPixel(x,y),b=reduced.getPixel(x+1,y);
            int la=(Color.red(a)*30+Color.green(a)*59+Color.blue(a)*11)/100;
            int lb=(Color.red(b)*30+Color.green(b)*59+Color.blue(b)*11)/100;
            if(la>lb)hash|=1L<<(y*8+x);
        }
        reduced.recycle();return hash;
    }
    private static CardQuadDetector.Detection smooth(CardQuadDetector.Detection older,CardQuadDetector.Detection current){
        if(older==null)return current;
        PointF[] result=new PointF[4];
        for(int i=0;i<4;i++)result[i]=new PointF(older.corners[i].x*.58f+current.corners[i].x*.42f,
            older.corners[i].y*.58f+current.corners[i].y*.42f);
        return new CardQuadDetector.Detection(result,current.confidence,current.areaFraction);
    }
    private void analyzeFrame(ImageProxy proxy){
        Bitmap frame=null;
        try{
            long now=SystemClock.elapsedRealtime();
            if(closing||now-lastProcessAt<75)return;
            lastProcessAt=now;
            frame=uprightFrame(proxy);
        }catch(Throwable e){Log.w(TAG,"Could not read CameraX frame",e);}
        finally{proxy.close();}
        if(frame==null||closing)return;
        try{
            long begin=SystemClock.elapsedRealtime();
            CardQuadDetector.Detection detected=detector.find(frame,smoothed);
            lastEdgeMs=SystemClock.elapsedRealtime()-begin;
            int w=frame.getWidth(),h=frame.getHeight();sourceWidth=w;sourceHeight=h;
            long wholeHash=wholeFrameHash(frame);
            boolean good=detected!=null&&detected.confidence>=.56f;
            // When an inner artwork rectangle suddenly replaces a large established card,
            // discard that reading rather than moving the visible outline into artwork.
            if(good&&smoothed!=null&&goodFrames>=3&&
                    detected.areaFraction<smoothed.areaFraction*.72f&&
                    SystemClock.elapsedRealtime()-lastGoodAt<500)good=false;
            if(good){
                missingSince=0;changedEmptyFrames=0;
                if(drift(detected,previous,w,h)<.035f){goodFrames=Math.min(12,goodFrames+1);smoothed=smooth(smoothed,detected);}
                else {goodFrames=1;smoothed=detected;if(!waitingForNext)sceneStartedAt=SystemClock.elapsedRealtime();}
                previous=detected;missingFrames=0;lastGoodAt=SystemClock.elapsedRealtime();
            }else{
                missingFrames++;
                if(missingSince==0)missingSince=SystemClock.elapsedRealtime();
                if(SystemClock.elapsedRealtime()-lastGoodAt>700){goodFrames=0;smoothed=null;previous=null;}
                // A card can remain present for seconds while glare/low contrast
                // temporarily defeats contour finding. Only report removal after
                // a sustained loss AND a materially different whole-camera view.
                // Never use the artwork-only hash to compare with an empty scene.
                if(waitingForNext){
                    double brightness=regionBrightness(frame,lastCapturedCorners);
                    double stand=surroundingBrightness(frame,lastCapturedCorners);
                    boolean exposedStand=brightness>=0&&stand>=0&&
                            brightness-lastCapturedBrightness>=30&&
                            Math.abs(brightness-stand)<43;
                    // Both the card region and its surrounding scene must
                    // indicate removal; a lighting change is not enough.
                    if(exposedStand&&Long.bitCount(wholeHash^lastCapturedSceneHash)>=10)
                        changedEmptyFrames++;
                    else changedEmptyFrames=0;
                    if(missingFrames>=10&&changedEmptyFrames>=8&&
                            SystemClock.elapsedRealtime()-missingSince>=1400){
                        waitingForNext=false;changeFrames=0;previousOcr="";previousOcrCount=0;
                        runOnUiThread(()->{keepButton.setText("Keep title");keepButton.setEnabled(false);});
                        sceneStartedAt=0;missingSince=0;
                        runOnUiThread(()->{if(!closing)status.setText("Scene changed · ready for next card");});
                    }
                }
            }
            if(good&&goodFrames>=2){
                currentFrameSceneHash=wholeHash;
                currentCardBrightness=regionBrightness(frame,smoothed.corners);
            }
            boolean stable=good&&goodFrames>=2&&smoothed!=null;
            CardQuadDetector.Detection overlayDetection=smoothed;
            runOnUiThread(()->{if(!closing)overlay.update(overlayDetection,w,h,stable);});
            if(!stable||recognizing.get()||closing)return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastRecognitionAt<200)return;
            lastRecognitionAt=now;
            begin=SystemClock.elapsedRealtime();
            Bitmap corrected=detector.rectify(frame,smoothed);
            lastWarpMs=SystemClock.elapsedRealtime()-begin;
            long hash=VisualMatcher.quickHash(corrected);
            // A changed artwork hash, glare, or exposure is NEVER proof that
            // the previous physical card was removed. Re-arm only after the
            // confirmed empty-stand event above (or explicit Next card tap).
            if(waitingForNext){corrected.recycle();return;}
            if(!recognizing.compareAndSet(false,true)){corrected.recycle();return;}
            previewHash=hash;
            Bitmap old=availableCard;availableCard=corrected;
            if(old!=null&&old!=corrected&&!old.isRecycled())old.recycle();
            begin=SystemClock.elapsedRealtime();
            VisualMatcher v=visual;
            VisualMatcher.Match candidate=v==null?null:v.match(corrected);
            lastArtMs=SystemClock.elapsedRealtime()-begin;
            if(candidate!=null&&candidate.usable()){
                enqueue(candidate.name,"artwork",candidate.similarity,candidate.id,corrected,hash);
                recognizing.set(false);
            }else{
                readTitle(corrected,hash,0,SystemClock.elapsedRealtime());
            }
        }catch(Exception e){Log.w(TAG,"Native frame analysis failed",e);recognizing.set(false);}
        finally{frame.recycle();}
    }
    private static Bitmap rotate(Bitmap source,int degrees){
        if(degrees==0)return source;
        Matrix m=new Matrix();m.postRotate(degrees);
        return Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);
    }
    private static Bitmap titleImage(Bitmap card){
        int x=Math.round(card.getWidth()*.055f),y=Math.round(card.getHeight()*.022f);
        int w=Math.round(card.getWidth()*.80f),h=Math.round(card.getHeight()*.115f);
        Bitmap strip=Bitmap.createBitmap(card,x,y,Math.min(w,card.getWidth()-x),Math.min(h,card.getHeight()-y));
        Bitmap enhanced=CardQuadDetector.enhanceTitle(strip);strip.recycle();return enhanced;
    }
    private static String selectTitle(Text text){
        String best="";float top=-999;
        for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
            String value=line.getText().trim().replaceAll("\\s+"," ");
            String letters=value.replaceAll("[^\\p{L}]","");
            if(letters.length()<3||letters.length()>52||value.length()>60)continue;
            if(value.matches("(?i).*\\b(creature|instant|sorcery|enchantment|artifact|planeswalker|battle)\\s*[—-].*"))continue;
            if(value.matches("(?i)^(enchantment|creature|artifact|land|instant|sorcery|aura|legendary)(\\s+(aura|creature|artifact|land))?$"))continue;
            if(letters.matches("(?i).*(.)\\1{4,}.*"))continue;
            float score=letters.length()-Math.max(0,value.length()-letters.length())*1.3f;
            if(line.getConfidence()>0)score+=line.getConfidence()*4;
            if(score>top){top=score;best=value;}
        }
        return best;
    }
    /** Detect two-line stylized titles, but only when the combined text is a
     * real indexed card name. Never concatenate arbitrary rules text. */
    private static String indexedTwoLineTitle(Text result,NativeNameIndex index){
        if(index==null)return null;
        ArrayList<Text.Line> lines=new ArrayList<>();
        for(Text.TextBlock block:result.getTextBlocks())lines.addAll(block.getLines());
        for(int i=0;i<lines.size();i++)for(int j=i+1;j<lines.size();j++){
            Text.Line a=lines.get(i),b=lines.get(j);
            android.graphics.Rect r=a.getBoundingBox(),t=b.getBoundingBox();
            if(r==null||t==null||t.top<r.top||t.top-r.bottom>Math.max(40,r.height()*1.8))continue;
            if(Math.abs(r.left-t.left)>Math.max(65,r.width()*.5))continue;
            String combined=a.getText().trim()+" "+b.getText().trim();
            String canonical=index.exact(combined);
            if(canonical!=null)return canonical;
        }
        return null;
    }
    private void readTitle(Bitmap card,long hash,int rotationIndex,long began){
        if(closing){recognizing.set(false);return;}
        int[] rotations={0,180,90,270};
        Bitmap rotated=rotate(card,rotations[rotationIndex]);
        Bitmap strip=titleImage(rotated);
        recognizer.process(InputImage.fromBitmap(strip,0)).addOnCompleteListener(task->{
            String name="";
            if(task.isSuccessful()){
                name=selectTitle(task.getResult());
                String paired=indexedTwoLineTitle(task.getResult(),names);
                if(paired!=null)name=paired;
            }
            else Log.w(TAG,"ML Kit title read failed",task.getException());
            strip.recycle();if(rotated!=card)rotated.recycle();
            if(closing){recognizing.set(false);return;}
            if(!name.isEmpty()){
                lastOcrMs=SystemClock.elapsedRealtime()-began;
                String normalized=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if(normalized.equals(previousOcr))previousOcrCount++;
                else{previousOcr=normalized;previousOcrCount=1;}
                NativeNameIndex index=names;
                String canonical=index==null?null:index.exact(name);
                lastSeenTitle=canonical!=null?canonical:name;
                final String current=lastSeenTitle;
                runOnUiThread(()->{
                    if(!closing){
                        status.setText("OCR candidate: "+current+" · "+previousOcrCount+" read(s) · "+captured.size()+" queued");
                        keepButton.setEnabled(true);
                        metrics.setText("Edge "+lastEdgeMs+" ms · rectify "+lastWarpMs+" ms · artwork "+lastArtMs+" ms · OCR "+lastOcrMs+" ms");
                    }
                });
                // Exact canonical names bypass the repeated OCR wait. For an
                // unknown or incomplete catalog, retain two-read protection.
                if(canonical!=null){enqueue(canonical,"ocr-exact",1f,null,card,hash);}
                // Do not queue a raw OCR string even after repeated reads.
                // Unknown names remain visible for an explicit manual action;
                // JS must never auto-save an unvalidated title.
                recognizing.set(false);
            }else if(rotationIndex<3){
                readTitle(card,hash,rotationIndex+1,began);
            }else{recognizing.set(false);}
        });
    }
    /** Called on analyzer or ML Kit callback thread. Store each capture once, then wait
     * for real removal/replacement before allowing another result. */
    private synchronized void enqueue(String name,String source,float score,String imageId,Bitmap card,long hash){
        if(closing||waitingForNext||captured.size()>=150)return;
        try{
            int number=captured.size()+1;
            File photo=new File(getCacheDir(),"native-scan-"+sessionStart+"-"+number+".jpg");
            try(FileOutputStream out=new FileOutputStream(photo)){card.compress(Bitmap.CompressFormat.JPEG,87,out);}
            JSONObject result=new JSONObject();
            result.put("name",name);result.put("source",source);result.put("artworkId",imageId==null?"":imageId);
            result.put("similarity",Math.round(score*1000)/1000.0);
            result.put("ocr_reads",source.startsWith("ocr")?previousOcrCount:0);
            result.put("elapsed",SystemClock.elapsedRealtime()-sessionStart);
            result.put("card_ms",SystemClock.elapsedRealtime()-(sceneStartedAt>0?sceneStartedAt:sessionStart));
            result.put("image_path",photo.getAbsolutePath());
            result.put("edge_ms",lastEdgeMs);result.put("warp_ms",lastWarpMs);
            result.put("art_ms",lastArtMs);result.put("ocr_ms",lastOcrMs);
            captured.add(result);
            waitingForNext=true;changeFrames=0;lastCapturedHash=hash;
            lastCapturedSceneHash=currentFrameSceneHash;
            if(smoothed!=null){
                lastCapturedCorners=new PointF[4];
                for(int i=0;i<4;i++)lastCapturedCorners[i]=
                        new PointF(smoothed.corners[i].x,smoothed.corners[i].y);
            }
            lastCapturedBrightness=currentCardBrightness;
            missingSince=0;changedEmptyFrames=0;
            lastSeenTitle="";previousOcr="";previousOcrCount=0;
            final String msg="Captured "+name+" ("+source+") · "+captured.size()+" queued · remove card";
            runOnUiThread(()->{if(!closing){
                status.setText(msg);keepButton.setText("Next card");keepButton.setEnabled(true);
                reviewButton.setText("Finish / save ("+captured.size()+")");
                metrics.setText("Edge "+lastEdgeMs+" ms · rectify "+lastWarpMs+" ms · artwork "+lastArtMs+" ms · OCR "+lastOcrMs+" ms · references "+(visual==null?0:visual.count()));
            }});
        }catch(Exception e){Log.e(TAG,"Could not queue card",e);}
    }
    private void finishBatch(){
        if(closing)return;
        closing=true;
        try{
            Intent data=new Intent();JSONArray batch=new JSONArray();
            synchronized(this){for(JSONObject item:captured)batch.put(item);}
            data.putExtra("scan_results",batch.toString());
            data.putExtra("scan_elapsed_ms",SystemClock.elapsedRealtime()-sessionStart);
            setResult(captured.isEmpty()?Activity.RESULT_CANCELED:Activity.RESULT_OK,data);
        }catch(Exception e){Log.e(TAG,"Could not finish scan batch",e);}
        finish();
    }
    @Override protected void onDestroy(){
        closing=true;
        if(provider!=null)provider.unbindAll();
        if(recognizer!=null)recognizer.close();
        if(availableCard!=null&&!availableCard.isRecycled())availableCard.recycle();
        analysisExecutor.shutdownNow();
        referenceExecutor.shutdownNow();
        super.onDestroy();
    }
}
