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
import android.util.Size;
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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.text.TextWatcher;
import android.text.Editable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import androidx.activity.ComponentActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
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
 * scene-gated collection scanning. Unverified editions can be saved by name in the WebView.
 */
public final class NativeScannerActivity extends ComponentActivity {
    private static final String TAG="ManaVaultNative";
    private static final int PERMISSION_REQUEST=302;
    private final ExecutorService analysisExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService referenceExecutor=Executors.newSingleThreadExecutor();
    private final CardQuadDetector detector=new CardQuadDetector();
    private final StackChangeGate stackGate=new StackChangeGate();
    private final AtomicBoolean recognizing=new AtomicBoolean(false);
    private final ArrayList<JSONObject> captured=new ArrayList<>();
    private static volatile java.lang.ref.WeakReference<NativeScannerActivity> liveScanner = new java.lang.ref.WeakReference<>(null);
    private final java.util.HashSet<String> resolvedScanIds=new java.util.HashSet<>();
    private int verifiedCount=0,unverifiedCount=0,reviewCount=0;
    private boolean finishRequested=false,forceFinishEnabled=false;
    // The top card cannot be rearmed until the WebView has persisted or the
    // user has resolved the current capture. CameraX is otherwise free to
    // continue previewing the stack while recognition is locked.
    private volatile boolean resolutionPending=false;
    private volatile boolean reviewDialogOpen=false;
    private ToneGenerator saveTone;
    private int saveToneVolume=-1;
    private final String sessionId=java.util.UUID.randomUUID().toString();
    /** Receipts are sent only after a card has been written to the collection. */
    static void receiveLiveResolution(JSONObject message){
        NativeScannerActivity current=liveScanner.get();
        if(current!=null)current.runOnUiThread(()->current.showLiveResolution(message));
    }
    private void showLiveResolution(JSONObject message){
        String id=message.optString("scan_id","");
        if(id.isEmpty()||!id.startsWith(sessionId+"-")||!resolvedScanIds.add(id))return;
        String result=message.optString("status","review");
        boolean saved="verified".equals(result)||"unverified".equals(result);
        if("verified".equals(result))verifiedCount++;
        else if("unverified".equals(result))unverifiedCount++;
        else reviewCount++;
        resolutionPending=false;
        reviewDialogOpen=false;
        if(saved)playSaveSound(message.optInt("volume",60));
        if(!closing){
            reviewButton.setText(finishRequested?"Saving… ("+Math.max(0,captured.size()-resolvedScanIds.size())+" pending)":
                    "Finish ("+(verifiedCount+unverifiedCount)+" saved)");
            if(waitingForNext && captured.size()>0 && id.equals(captured.get(captured.size()-1).optString("scan_id")))
                status.setText(("verified".equals(result)?"Saved printing": "unverified".equals(result)?"Saved as unverified":
                    "retry".equals(result)?"Retrying card": "Skipped card")+
                    ": "+message.optString("name","")+
                    ("retry".equals(result)?" · hold card steady":" · stack next card"));
            if("retry".equals(result)){
                waitingForNext=false;stackGate.clear();goodFrames=0;
                previousOcr="";previousOcrCount=0;
                sceneStartedAt=SystemClock.elapsedRealtime();
                keepButton.setText("Keep title");keepButton.setEnabled(false);
            }else if(waitingForNext){
                keepButton.setText("Same copy");keepButton.setEnabled(true);
            }
            if(finishRequested && resolvedScanIds.size()>=captured.size())completeBatch();
        }
    }
    private void playSaveSound(int requestedVolume){
        // Audible confirmation ONLY after the collection write receipt, never
        // when OCR merely identifies a candidate or a review is displayed.
        try{
            int level=Math.max(0,Math.min(100,requestedVolume));
            if(level==0)return; // Respect the existing Scan sounds volume slider.
            if(saveTone==null || saveToneVolume!=level){
                if(saveTone!=null)saveTone.release();
                saveTone=new ToneGenerator(AudioManager.STREAM_MUSIC,level);
                saveToneVolume=level;
            }
            saveTone.startTone(ToneGenerator.TONE_PROP_ACK,170);
        }catch(Exception e){Log.w(TAG,"Save confirmation tone unavailable",e);}
    }
    static void receiveImmediateReview(JSONObject payload){
        NativeScannerActivity current=liveScanner.get();
        if(current!=null)current.runOnUiThread(()->current.showImmediateReview(payload));
    }
    private void showImmediateReview(JSONObject payload){
        if(closing||!resolutionPending||reviewDialogOpen)return;
        final String id=payload.optString("scan_id","");
        if(id.isEmpty()||captured.isEmpty()||
           !id.equals(captured.get(captured.size()-1).optString("scan_id")))return;
        reviewDialogOpen=true;
        final String name=payload.optString("name","");
        final String reason=payload.optString("reason","Edition not identified");
        final JSONArray choices=payload.optJSONArray("choices");
        final boolean allowUnverified=payload.optBoolean("allow_unverified",false);
        final java.util.ArrayList<String> descriptions=new java.util.ArrayList<>();
        final java.util.ArrayList<String> ids=new java.util.ArrayList<>();
        if(choices!=null)for(int i=0;i<choices.length();i++){
            JSONObject entry=choices.optJSONObject(i);if(entry==null)continue;
            String printing=entry.optString("id","");
            if(printing.isEmpty())continue;
            ids.add(printing);descriptions.add(entry.optString("label",printing));
        }
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16),dp(6),dp(16),0);
        final EditText search=new EditText(this);
        search.setSingleLine(true);
        search.setHint(ids.isEmpty()&&!allowUnverified?"Enter correct card name":"Filter by set, code, or collector #");
        layout.addView(search);
        final ListView list=new ListView(this);
        final ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,
                new java.util.ArrayList<>(descriptions));
        list.setAdapter(adapter);
        if(!ids.isEmpty())layout.addView(list,new LinearLayout.LayoutParams(-1,dp(270)));
        final android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle(ids.isEmpty()&&!allowUnverified?"Review card now":"Verify printing now: "+name)
            .setMessage(reason+"\nChoose the physical set and collector number before continuing.")
            .setView(layout)
            .setPositiveButton(ids.isEmpty()&&!allowUnverified?"Look up title":"Save unverified",null)
            .setNegativeButton("Retry",null)
            .setNeutralButton("Skip",null)
            .create();
        dialog.setCancelable(false);
        dialog.setOnShowListener(v->{
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(b->{
                String manual=search.getText().toString().trim();
                if(ids.isEmpty()&&!allowUnverified){
                    if(manual.isEmpty()){search.setError("Enter a card name to search");return;}
                    sendImmediateReviewChoice(id,"correct-name","",manual);
                }else sendImmediateReviewChoice(id,"unverified","","");
                reviewDialogOpen=false;dialog.dismiss();
            });
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener(b->{
                sendImmediateReviewChoice(id,"retry","","");reviewDialogOpen=false;dialog.dismiss();
            });
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener(b->{
                sendImmediateReviewChoice(id,"skip","","");reviewDialogOpen=false;dialog.dismiss();
            });
        });
        if(!ids.isEmpty()){
            search.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence value,int start,int before,int count){
                    String q=value.toString().toLowerCase(java.util.Locale.ROOT);
                    adapter.clear();
                    for(String label:descriptions)if(label.toLowerCase(java.util.Locale.ROOT).contains(q))adapter.add(label);
                    adapter.notifyDataSetChanged();
                }
                public void afterTextChanged(Editable text){}
            });
            list.setOnItemClickListener((parent,view,position,rowId)->{
                String label=adapter.getItem(position);
                int original=descriptions.indexOf(label);
                if(original<0||original>=ids.size())return;
                sendImmediateReviewChoice(id,"printing",ids.get(original),"");
                reviewDialogOpen=false;dialog.dismiss();
            });
        }
        status.setText("Review required · scanner paused for "+(name.isEmpty()?"unknown card":name));
        dialog.show();
    }
    private void sendImmediateReviewChoice(String id,String action,String printingId,String typedName){
        try{
            JSONObject response=new JSONObject();response.put("scan_id",id);
            response.put("action",action);response.put("printing_id",printingId);
            response.put("name",typedName);
            MainActivity.deliverImmediateReviewChoice(response);
        }catch(Exception e){Log.e(TAG,"Could not send review selection",e);}
    }
    private PreviewView preview;
    private CardEdgeOverlay overlay;
    private TextView status,metrics;
    private Button resolutionButton;
    private static final String CAMERA_QUALITY_PREF="native_camera_quality";
    private static final int QUALITY_HIGH=0,QUALITY_BALANCED=1,QUALITY_AUTO=2;
    private int cameraQuality=QUALITY_HIGH;
    private volatile int activeSensorWidth=0,activeSensorHeight=0,activeUprightWidth=0,activeUprightHeight=0;
    private volatile int activeCropWidth=0,activeCropHeight=0;
    // CameraX zoom crops the sensor image BEFORE the 1920x1440 analysis frame
    // is emitted. Unlike scaling the 630px perspective crop, this can supply
    // genuinely more sampled pixels for tiny footer text on a fixed stand.
    private boolean standZoomEvaluated=false;
    private float standZoomApplied=1f;
    private String lastTiming="";
    private int lastArtworkRefs=-1;
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
        liveScanner=new java.lang.ref.WeakReference<>(this);
        super.onCreate(savedInstanceState);
        // Camera must occupy the *entire* physical display. Merely placing a
        // PreviewView MATCH_PARENT inside a normal Activity leaves reserved
        // status/navigation-bar regions and cuts across the overlay controls.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sessionStart=SystemClock.elapsedRealtime();
        cameraQuality=Math.max(QUALITY_HIGH,Math.min(QUALITY_AUTO,getPreferences(MODE_PRIVATE).getInt(CAMERA_QUALITY_PREF,QUALITY_HIGH)));
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
        LinearLayout headline=new LinearLayout(this);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(15);
        status.setText("Native CameraX · starting…");
        headline.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        resolutionButton=new Button(this);resolutionButton.setAllCaps(false);
        resolutionButton.setTextSize(10);resolutionButton.setMinWidth(0);resolutionButton.setMinimumWidth(0);
        resolutionButton.setPadding(dp(5),0,dp(5),0);
        resolutionButton.setText(qualityLabel());
        resolutionButton.setOnClickListener(v->{
            cameraQuality=(cameraQuality+1)%3;
            getPreferences(MODE_PRIVATE).edit().putInt(CAMERA_QUALITY_PREF,cameraQuality).apply();
            resolutionButton.setText(qualityLabel());
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
                status.setText("Changing camera resolution…");
                startCamera();
            }
        });
        headline.addView(resolutionButton,new LinearLayout.LayoutParams(dp(101),dp(42)));
        top.addView(headline);
        metrics=new TextView(this);metrics.setTextColor(0xFFBBDBCC);metrics.setTextSize(11);
        metrics.setText("Camera: awaiting negotiated resolution");top.addView(metrics);
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
                if(resolutionPending || reviewDialogOpen){status.setText("Finish the current save/review before stacking another card");return;}
                // The manual bypass is ONLY for an actual newly stacked copy
                // that appears identical. Require deliberate confirmation to
                // avoid counting the same stationary card a second time.
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Scan an identical copy?")
                    .setMessage("Only continue if you have placed another physical card on top of the stack. The camera cannot prove two identical-looking cards are separate.")
                    .setNegativeButton("Cancel",(dialog,which)->{})
                    .setPositiveButton("Scan next copy",(dialog,which)->{
                        if(!waitingForNext||closing)return;
                        waitingForNext=false;sceneStartedAt=SystemClock.elapsedRealtime();
                        previousOcr="";previousOcrCount=0;goodFrames=0;
                        stackGate.clear();
                        lastRecognitionAt=SystemClock.elapsedRealtime();
                        status.setText("Manual same-copy override · identifying next card…");
                        keepButton.setText("Keep title");keepButton.setEnabled(false);
                    }).show();
            }else if(card!=null&&!card.isRecycled()&&!lastSeenTitle.isEmpty())
                enqueue(lastSeenTitle,"manual-title",0,null,card,previewHash);
        });
        actions.addView(keepButton,new LinearLayout.LayoutParams(0,dp(48),1));
        reviewButton=new Button(this);reviewButton.setText("Finish (0 saved)");reviewButton.setAllCaps(false);reviewButton.setTextSize(12);reviewButton.setMinWidth(0);reviewButton.setMinimumWidth(0);
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
                    if(!closing){
                        lastArtworkRefs=refs;
                        showMetrics();
                    }
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
    private String qualityLabel(){
        return cameraQuality==QUALITY_HIGH?"High 2.8MP":cameraQuality==QUALITY_BALANCED?"Balanced":"Auto";
    }
    private ResolutionSelector selectResolution(int mode){
        ResolutionSelector.Builder selector=new ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY);
        if(mode!=QUALITY_AUTO){
            // Requests are preferences, not guarantees. CameraX negotiates a supported
            // Camera2 stream size with Preview; report the actual size from ImageProxy.
            Size requested=mode==QUALITY_HIGH?new Size(1920,1440):new Size(1280,960);
            selector.setResolutionStrategy(new ResolutionStrategy(requested,
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER));
        }
        return selector.build();
    }
    private void bindCamera(int quality) throws Exception {
        provider.unbindAll();
        activeSensorWidth=activeSensorHeight=activeUprightWidth=activeUprightHeight=0;
        activeCropWidth=activeCropHeight=0;
        previous=null;smoothed=null;goodFrames=0;missingFrames=0;
        standZoomEvaluated=false;standZoomApplied=1f;
        ImageAnalysis analysis=new ImageAnalysis.Builder()
            .setResolutionSelector(selectResolution(quality))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();
        Preview p=new Preview.Builder()
            .setResolutionSelector(selectResolution(quality))
            .build();
        p.setSurfaceProvider(preview.getSurfaceProvider());
        analysis.setAnalyzer(analysisExecutor,this::analyzeFrame);
        camera=provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis);
    }
    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(closing||isFinishing())return;
            try{
                provider=future.get();
                try {bindCamera(cameraQuality);}
                catch(Exception requestedError){
                    if(cameraQuality==QUALITY_AUTO)throw requestedError;
                    Log.w(TAG,"Requested camera quality unavailable; retrying automatic resolution",requestedError);
                    bindCamera(QUALITY_AUTO);
                    status.setText("Camera fell back to automatic resolution");
                }
                if(!status.getText().toString().contains("fell back"))
                    status.setText("Ready · fast edge tracking · artwork/OCR");
                showMetrics();
            }catch(Exception e){
                Log.e(TAG,"CameraX startup failed",e);
                status.setText("Camera failed: "+e.getMessage());
            }
        },ContextCompat.getMainExecutor(this));
    }
    private void showMetrics(){
        // This line survives subsequent OCR/status updates, unlike the former
        // startup-only camera resolution message.
        String cameraInfo=activeSensorWidth>0?
            "Camera OCR "+activeSensorWidth+"×"+activeSensorHeight+
            " · upright "+activeUprightWidth+"×"+activeUprightHeight:
            "Camera negotiating ("+qualityLabel()+")";
        String cardInfo=activeCropWidth>0?" · card crop "+activeCropWidth+"×"+activeCropHeight:"";
        if(standZoomApplied>1.02f)cardInfo+=" · stand zoom "+String.format(Locale.US,"%.2f",standZoomApplied)+"×";
        String detail=lastTiming.isEmpty()?"": "\n"+lastTiming;
        if(lastArtworkRefs>=0)detail+="\nArtwork refs: "+lastArtworkRefs+" · cached edition fingerprints: "+ReferenceFingerprintCache.count();
        metrics.setText(cameraInfo+cardInfo+detail);
    }
    /** Apply ONE conservative sensor zoom per scanner session, not one per card.
     * We never crop a card off-screen to force a target pixel count. Digital zoom
     * on a sensor with no spare detail may still provide no OCR benefit.
     */
    private boolean adjustFixedStandZoom(CardQuadDetector.Detection card,int frameW,int frameH){
        if(standZoomEvaluated||camera==null||cameraQuality!=QUALITY_HIGH||
                waitingForNext||captured.size()>0||goodFrames<4)return false;
        float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=0,maxY=0;
        for(PointF p:card.corners){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);
            maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);}
        float width=maxX-minX,height=maxY-minY;
        if(width<=0||height<=0)return false;
        // Do not zoom if there is already adequate physical resolution.
        if(width>=850){standZoomEvaluated=true;return false;}
        float centerX=frameW/2f,centerY=frameH/2f;
        float extentX=Math.max(Math.abs(minX-centerX),Math.abs(maxX-centerX));
        float extentY=Math.max(Math.abs(minY-centerY),Math.abs(maxY-centerY));
        // Keep at least 10% of the frame as breathing room on every side.
        float fit=Math.min(frameW*.43f/Math.max(1,extentX),
                frameH*.43f/Math.max(1,extentY));
        androidx.lifecycle.LiveData<androidx.camera.core.ZoomState> state=
                camera.getCameraInfo().getZoomState();
        androidx.camera.core.ZoomState available=state.getValue();
        if(available==null){standZoomEvaluated=true;return false;}
        float ratio=Math.min(Math.min(850f/width,1.65f),
                Math.min(fit,available.getMaxZoomRatio()));
        standZoomEvaluated=true;
        if(ratio<1.12f)return false;
        standZoomApplied=ratio;
        camera.getCameraControl().setZoomRatio(ratio);
        previous=null;smoothed=null;goodFrames=0;missingFrames=0;
        lastRecognitionAt=SystemClock.elapsedRealtime()+450;
        Log.i(TAG,"Fixed-stand sensor zoom " + ratio + "x; prior card width " + width + "px");
        runOnUiThread(()->{if(!closing){status.setText("Refining card detail with safe stand zoom…");showMetrics();}});
        return true;
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
            int sensorW=proxy.getWidth(),sensorH=proxy.getHeight();
            if(sensorW!=activeSensorWidth||sensorH!=activeSensorHeight){
                activeSensorWidth=sensorW;activeSensorHeight=sensorH;
                int rotation=proxy.getImageInfo().getRotationDegrees();
                activeUprightWidth=rotation%180==0?sensorW:sensorH;
                activeUprightHeight=rotation%180==0?sensorH:sensorW;
                runOnUiThread(()->{if(!closing)showMetrics();});
                Log.i(TAG,"Negotiated CameraX ImageAnalysis "+sensorW+"x"+sensorH+
                    ", upright "+activeUprightWidth+"x"+activeUprightHeight+
                    ", selected quality "+qualityLabel());
            }
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
                if(waitingForNext && !resolutionPending && !reviewDialogOpen){
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
                    // Empty-stand removal is still accepted, but stacking does
                    // not need an empty view. A strong cover/reveal is also
                    // recorded to permit an identical physical copy on top.
                    stackGate.coverFrame(missingFrames>=4 &&
                            Long.bitCount(wholeHash^lastCapturedSceneHash)>=13);
                    if(missingFrames>=10&&changedEmptyFrames>=8&&
                            SystemClock.elapsedRealtime()-missingSince>=1400){
                        waitingForNext=false;stackGate.clear();changeFrames=0;
                        previousOcr="";previousOcrCount=0;
                        runOnUiThread(()->{keepButton.setText("Keep title");keepButton.setEnabled(false);});
                        sceneStartedAt=0;missingSince=0;
                        runOnUiThread(()->{if(!closing)status.setText("Card removed · ready for next card or stack");});
                    }
                }
            }
            if(good&&goodFrames>=2){
                currentFrameSceneHash=wholeHash;
                currentCardBrightness=regionBrightness(frame,smoothed.corners);
            }
            if(good&&smoothed!=null&&adjustFixedStandZoom(smoothed,w,h))return;
            boolean stable=good&&goodFrames>=2&&smoothed!=null;
            CardQuadDetector.Detection overlayDetection=smoothed;
            runOnUiThread(()->{if(!closing)overlay.update(overlayDetection,w,h,stable);});
            if(!stable){
                if(waitingForNext)stackGate.unstable();
                return;
            }
            if(recognizing.get()||closing)return;
            // Wait for at least four stable frames before the FIRST recognition
            // in high-quality mode; otherwise OCR may capture the small card
            // before the fixed-stand zoom ever has a chance to engage.
            if(cameraQuality==QUALITY_HIGH && captured.isEmpty() && !standZoomEvaluated)return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastRecognitionAt<200)return;
            lastRecognitionAt=now;
            begin=SystemClock.elapsedRealtime();
            Bitmap corrected=detector.rectify(frame,smoothed);
            lastWarpMs=SystemClock.elapsedRealtime()-begin;
            if(activeCropWidth!=corrected.getWidth()||activeCropHeight!=corrected.getHeight()){
                activeCropWidth=corrected.getWidth();activeCropHeight=corrected.getHeight();
                runOnUiThread(()->{if(!closing)showMetrics();});
            }
            long hash=VisualMatcher.quickHash(corrected);
            // v5.11.1: the user places new cards ON TOP of the stack. Compare
            // persistent artwork AND whole-card features; require a stable
            // change, not a fleeting reflection, before automatic rearming.
            // Identical-looking copies require a genuine cover/reveal event.
            if(waitingForNext){
                if(resolutionPending || reviewDialogOpen){corrected.recycle();return;}
                long fullHash=wholeFrameHash(corrected);
                boolean newTop=stackGate.observe(hash,fullHash,now);
                corrected.recycle();
                if(newTop){
                    waitingForNext=false;previousOcr="";previousOcrCount=0;
                    sceneStartedAt=now;lastRecognitionAt=now-220;
                    final String reason=stackGate.lastEvent();
                    runOnUiThread(()->{if(!closing){
                        keepButton.setText("Keep title");keepButton.setEnabled(false);
                        status.setText("New top card detected ("+reason+") · identifying…");
                    }});
                }
                return;
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
        int w=Math.round(card.getWidth()*.88f),h=Math.round(card.getHeight()*.155f);
        Bitmap strip=Bitmap.createBitmap(card,x,y,Math.min(w,card.getWidth()-x),Math.min(h,card.getHeight()-y));
        Bitmap enhanced=CardQuadDetector.enhanceTitle(strip);strip.recycle();
        Bitmap upscaled=Bitmap.createScaledBitmap(enhanced,enhanced.getWidth()*2,enhanced.getHeight()*2,true);
        enhanced.recycle();return upscaled;
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
                        lastTiming="Edge "+lastEdgeMs+" ms · warp "+lastWarpMs+" ms · art "+lastArtMs+" ms · OCR "+lastOcrMs+" ms";showMetrics();
                    }
                });
                // Exact canonical names bypass the repeated OCR wait. For an
                // unknown or incomplete catalog, retain two-read protection.
                if(canonical!=null){
                    // Learn only safely oriented, exact catalog-title scans. A name
                    // fingerprint is NOT a verified printing (the ID stays empty).
                    if(rotationIndex==0 && visual!=null && !waitingForNext){
                        Bitmap art=VisualMatcher.artRegion(card);
                        byte[] features=VisualMatcher.fingerprint(art);
                        art.recycle();
                        final VisualMatcher matcher=visual;
                        final String observedName=canonical;
                        referenceExecutor.execute(()->matcher.learnObservedName(observedName,features));
                    }
                    enqueue(canonical,"ocr-exact",1f,null,card,hash);
                }
                // A stable but imperfect title is still a useful scan. Queue it
                // for the catalog's fuzzy name matcher after two independent
                // camera reads; NEVER treat it as an exact printing identity.
                else if(previousOcrCount>=2 && normalized.length()>=5 &&
                        !normalized.matches(".*(.)\\1{4,}.*"))
                    enqueue(name,"ocr-candidate",0,null,card,hash);
                recognizing.set(false);
            }else if(rotationIndex<3){
                readTitle(card,hash,rotationIndex+1,began);
            }else{recognizing.set(false);}
        });
    }
    /** Called on analyzer or ML Kit callback thread. Store each capture once,
     * then wait for a stable new top card on the physical stack. */
    private synchronized void enqueue(String name,String source,float score,String imageId,Bitmap card,long hash){
        if(closing||finishRequested||waitingForNext||captured.size()>=150)return;
        try{
            int number=captured.size()+1;
            File photo=new File(getCacheDir(),"native-scan-"+sessionStart+"-"+number+".jpg");
            try(FileOutputStream out=new FileOutputStream(photo)){card.compress(Bitmap.CompressFormat.JPEG,93,out);}
            JSONObject result=new JSONObject();
            result.put("name",name);result.put("source",source);result.put("artworkId",imageId==null?"":imageId);
            result.put("similarity",Math.round(score*1000)/1000.0);
            result.put("ocr_reads",source.startsWith("ocr")?previousOcrCount:0);
            result.put("elapsed",SystemClock.elapsedRealtime()-sessionStart);
            result.put("card_ms",SystemClock.elapsedRealtime()-(sceneStartedAt>0?sceneStartedAt:sessionStart));
            result.put("image_path",photo.getAbsolutePath());
            result.put("scan_id",sessionId+"-"+number);
            result.put("scan_index",number-1);
            result.put("edge_ms",lastEdgeMs);result.put("warp_ms",lastWarpMs);
            result.put("art_ms",lastArtMs);result.put("ocr_ms",lastOcrMs);
            captured.add(result);
            // Resolve and save now, while the camera is open. This capture is
            // also included in the final handoff for retry after interruptions.
            resolutionPending=true;
            MainActivity.deliverLiveCapture(result);
            stackGate.captured(hash,wholeFrameHash(card),SystemClock.elapsedRealtime());
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
            final String msg="Captured "+name+" ("+source+") · resolving and saving before next card…";
            runOnUiThread(()->{if(!closing){
                status.setText(msg);keepButton.setText("Resolving");keepButton.setEnabled(false);
                reviewButton.setText("Finish ("+(verifiedCount+unverifiedCount)+" saved)");
                lastTiming="Edge "+lastEdgeMs+" ms · warp "+lastWarpMs+" ms · art "+lastArtMs+" ms · OCR "+lastOcrMs+" ms · references "+(visual==null?0:visual.count());showMetrics();
            }});
        }catch(Exception e){Log.e(TAG,"Could not queue card",e);}
    }
    private void finishBatch(){
        if(closing)return;
        if(forceFinishEnabled || captured.isEmpty() || resolvedScanIds.size()>=captured.size()){
            completeBatch();return;
        }
        // Do not close the camera and process the whole backlog afterwards.
        // Wait for the background resolver to finish each captured card first.
        finishRequested=true;
        int pending=captured.size()-resolvedScanIds.size();
        status.setText("Finalizing "+pending+" captured card(s) · collection is saving now…");
        reviewButton.setText("Saving… ("+pending+" pending)");
        reviewButton.setEnabled(false);
        // Escape hatch if Android suspends the hidden WebView or its network
        // requests never return. Unsaved captures are replayed on return.
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
            if(!closing && finishRequested && resolvedScanIds.size()<captured.size()){
                forceFinishEnabled=true;
                reviewButton.setEnabled(true);
                reviewButton.setText("Close anyway");
                status.setText("Still resolving "+(captured.size()-resolvedScanIds.size())+
                    " scan(s). Tap Close anyway to finish and retry them.");
            }
        },45000);
    }
    private void completeBatch(){
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
        liveScanner.clear();
        closing=true;
        if(provider!=null)provider.unbindAll();
        if(recognizer!=null)recognizer.close();
        if(saveTone!=null){saveTone.release();saveTone=null;}
        if(availableCard!=null&&!availableCard.isRecycled())availableCard.recycle();
        analysisExecutor.shutdownNow();
        referenceExecutor.shutdownNow();
        super.onDestroy();
    }
}
