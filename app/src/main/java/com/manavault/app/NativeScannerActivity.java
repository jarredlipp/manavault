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
    private volatile boolean closing=false;
    private boolean torch=false;
    private CardQuadDetector.Detection previous,smoothed;
    private int goodFrames=0,missingFrames=0,changeFrames=0;
    private long lastGoodAt=0,lastProcessAt=0,lastRecognitionAt=0,sessionStart=0;
    private long lastCapturedHash=0;
    private boolean waitingForNext=false;
    private String previousOcr="",lastSeenTitle="";
    private int previousOcrCount=0;
    private long previewHash=0;
    private volatile Bitmap availableCard;
    private long lastEdgeMs=0,lastWarpMs=0,lastArtMs=0,lastOcrMs=0;
    private int sourceWidth=1,sourceHeight=1;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
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
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(14),dp(12),dp(14),dp(12));top.setBackgroundColor(0xA5000000);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(15);
        status.setText("Initializing native scanner…");top.addView(status);
        metrics=new TextView(this);metrics.setTextColor(0xFFBBDBCC);metrics.setTextSize(11);
        metrics.setText("Timing: waiting for frames");top.addView(metrics);
        root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(7),dp(10),dp(7),dp(18));actions.setBackgroundColor(0xAA000000);
        Button back=new Button(this);back.setText("Cancel");back.setOnClickListener(v->finishBatch());
        actions.addView(back,new LinearLayout.LayoutParams(0,dp(54),1));
        torchButton=new Button(this);torchButton.setText("Light off");torchButton.setOnClickListener(v->toggleTorch());
        actions.addView(torchButton,new LinearLayout.LayoutParams(0,dp(54),1));
        keepButton=new Button(this);keepButton.setText("Keep title");keepButton.setEnabled(false);
        keepButton.setOnClickListener(v->{
            Bitmap card=availableCard;
            if(card!=null&&!card.isRecycled()&&!lastSeenTitle.isEmpty()&&!waitingForNext)
                enqueue(lastSeenTitle,"manual-title",0,null,card,previewHash);
        });
        actions.addView(keepButton,new LinearLayout.LayoutParams(0,dp(54),1));
        reviewButton=new Button(this);reviewButton.setText("Review (0)");reviewButton.setOnClickListener(v->finishBatch());
        actions.addView(reviewButton,new LinearLayout.LayoutParams(0,dp(54),1));
        root.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        setContentView(root);
        if(!OpenCVLoader.initLocal()){
            status.setText("OpenCV unavailable; return to legacy browser scanner.");return;
        }
        referenceExecutor.execute(()->{
            try{visual=new VisualMatcher(getApplicationContext());}
            catch(Exception e){Log.w(TAG,"Artwork index loading failed, using OCR",e);}
        });
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},PERMISSION_REQUEST);
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
                status.setText("Ready · find card edges · artwork refs load in background");
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
            if(closing||now-lastProcessAt<100)return;
            lastProcessAt=now;
            frame=uprightFrame(proxy);
        }catch(Throwable e){Log.w(TAG,"Could not read CameraX frame",e);}
        finally{proxy.close();}
        if(frame==null||closing)return;
        try{
            long begin=SystemClock.elapsedRealtime();
            CardQuadDetector.Detection detected=detector.find(frame);
            lastEdgeMs=SystemClock.elapsedRealtime()-begin;
            int w=frame.getWidth(),h=frame.getHeight();sourceWidth=w;sourceHeight=h;
            boolean good=detected!=null&&detected.confidence>=.57f;
            // When an inner artwork rectangle suddenly replaces a large established card,
            // discard that reading rather than moving the visible outline into artwork.
            if(good&&smoothed!=null&&goodFrames>=3&&
                    detected.areaFraction<smoothed.areaFraction*.72f&&
                    SystemClock.elapsedRealtime()-lastGoodAt<500)good=false;
            if(good){
                if(drift(detected,previous,w,h)<.035f){goodFrames=Math.min(12,goodFrames+1);smoothed=smooth(smoothed,detected);}
                else {goodFrames=1;smoothed=detected;}
                previous=detected;missingFrames=0;lastGoodAt=SystemClock.elapsedRealtime();
            }else{
                missingFrames++;
                if(SystemClock.elapsedRealtime()-lastGoodAt>700){goodFrames=0;smoothed=null;previous=null;}
                if(waitingForNext && missingFrames>=5){
                    waitingForNext=false;changeFrames=0;previousOcr="";previousOcrCount=0;
                    runOnUiThread(()->{if(!closing)status.setText("Card removed · ready for next card");});
                }
            }
            boolean stable=good&&goodFrames>=3&&smoothed!=null;
            CardQuadDetector.Detection overlayDetection=smoothed;
            runOnUiThread(()->{if(!closing)overlay.update(overlayDetection,w,h,stable);});
            if(!stable||recognizing.get()||closing)return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastRecognitionAt<350)return;
            lastRecognitionAt=now;
            begin=SystemClock.elapsedRealtime();
            Bitmap corrected=detector.rectify(frame,smoothed);
            lastWarpMs=SystemClock.elapsedRealtime()-begin;
            long hash=VisualMatcher.quickHash(corrected);
            if(waitingForNext){
                int difference=Long.bitCount(hash^lastCapturedHash);
                if(difference>=15){changeFrames++;}
                else changeFrames=0;
                if(changeFrames<4){corrected.recycle();return;}
                waitingForNext=false;changeFrames=0;previousOcr="";previousOcrCount=0;
            }
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
    private void readTitle(Bitmap card,long hash,int rotationIndex,long began){
        if(closing){recognizing.set(false);return;}
        int[] rotations={0,180,90,270};
        Bitmap rotated=rotate(card,rotations[rotationIndex]);
        Bitmap strip=titleImage(rotated);
        recognizer.process(InputImage.fromBitmap(strip,0)).addOnCompleteListener(task->{
            String name="";
            if(task.isSuccessful())name=selectTitle(task.getResult());
            else Log.w(TAG,"ML Kit title read failed",task.getException());
            strip.recycle();if(rotated!=card)rotated.recycle();
            if(closing){recognizing.set(false);return;}
            if(!name.isEmpty()){
                lastOcrMs=SystemClock.elapsedRealtime()-began;
                String normalized=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if(normalized.equals(previousOcr))previousOcrCount++;
                else{previousOcr=normalized;previousOcrCount=1;}
                lastSeenTitle=name;
                final String current=name;
                runOnUiThread(()->{
                    if(!closing){
                        status.setText("OCR candidate: "+current+" · "+previousOcrCount+" read(s) · "+captured.size()+" queued");
                        keepButton.setEnabled(true);
                        metrics.setText("Edge "+lastEdgeMs+" ms · rectify "+lastWarpMs+" ms · artwork "+lastArtMs+" ms · OCR "+lastOcrMs+" ms");
                    }
                });
                if(previousOcrCount>=2){enqueue(name,"ocr",0,null,card,hash);}
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
            result.put("ocr_reads",source.equals("ocr")?previousOcrCount:0);
            result.put("elapsed",SystemClock.elapsedRealtime()-sessionStart);
            result.put("image_path",photo.getAbsolutePath());
            result.put("edge_ms",lastEdgeMs);result.put("warp_ms",lastWarpMs);
            result.put("art_ms",lastArtMs);result.put("ocr_ms",lastOcrMs);
            captured.add(result);
            waitingForNext=true;changeFrames=0;lastCapturedHash=hash;
            lastSeenTitle="";previousOcr="";previousOcrCount=0;
            final String msg="Captured "+name+" ("+source+") · "+captured.size()+" queued · remove card";
            runOnUiThread(()->{if(!closing){
                status.setText(msg);keepButton.setEnabled(false);
                reviewButton.setText("Review ("+captured.size()+")");
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
