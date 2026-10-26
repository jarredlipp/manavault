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
import android.view.View;
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
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.ExposureState;
import android.util.Range;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** v5.9: native live-edge tracking, artwork-first candidates, OCR fallback, continuous
 * scene-gated collection scanning. Unverified editions can be saved by name in the WebView.
 */
public final class NativeScannerActivity extends ComponentActivity {
    private static final String TAG="ManaVaultNative";
    private static final int PERMISSION_REQUEST=302;
    // Supports scanning 186+ cards in one uninterrupted session; keeps the
    // end-of-session Activity result comfortably below Android Binder limits.
    private static final int MAX_SESSION_CAPTURES=300;
    private volatile boolean sessionLimitNotified=false;
    private final ExecutorService analysisExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService referenceExecutor=Executors.newSingleThreadExecutor();
    private final CardQuadDetector detector=new CardQuadDetector();
    private final StackChangeGate stackGate=new StackChangeGate();
    private final ScanCycleCoordinator scanCycle=new ScanCycleCoordinator();
    private final AtomicBoolean recognizing=new AtomicBoolean(false);
    private final ArrayList<JSONObject> captured=new ArrayList<>();
    private static volatile java.lang.ref.WeakReference<NativeScannerActivity> liveScanner = new java.lang.ref.WeakReference<>(null);
    private final java.util.Set<String> resolvedScanIds=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile String lastFullIndexLookup="not attempted";
    // Provisional global artwork names are shown in review only and cleared
    // at the next capture; they never become a verified printing ID.
    private volatile JSONArray pendingArtworkChoices=null;
    static void noteFullIndexLookup(String result){
        NativeScannerActivity active=liveScanner.get();
        if(active!=null)active.runOnUiThread(()->{
            if(!active.closing){active.lastFullIndexLookup=result;active.showMetrics();}
        });
    }
    // Last durable receipt timestamps separate user/scene idle time from
    // prior manual review. These values are diagnostic and never gate saving.
    private volatile long lastResolvedReceiptAt=0,lastResolvedReceiptLatencyMs=-1;
    private int verifiedCount=0,unverifiedCount=0,reviewCount=0;
    private boolean finishRequested=false,forceFinishEnabled=false;
    // The top card cannot be rearmed until the WebView has persisted or the
    // user has resolved the current capture. CameraX is otherwise free to
    // continue previewing the stack while recognition is locked.
    private volatile boolean resolutionPending=false;
    private volatile boolean reviewDialogOpen=false;
    private android.app.Dialog scanReviewDialog;
    private String scanReviewId="";
    private ToneGenerator saveTone;
    private int saveToneVolume=-1;
    private final String sessionId=java.util.UUID.randomUUID().toString();
    static void receiveLiveProgress(JSONObject payload){
        NativeScannerActivity current=liveScanner.get();
        if(current!=null)current.runOnUiThread(()->{
            if(current.closing||!current.resolutionPending||current.reviewDialogOpen||current.captured.isEmpty())return;
            if(!payload.optString("scan_id","").equals(current.captured.get(current.captured.size()-1).optString("scan_id")))return;
            current.status.setText(payload.optString("message","Verifying card printing…"));
        });
    }
    /** Receipts are sent only after a card has been written to the collection. */
    static void receiveLiveResolution(JSONObject message){
        NativeScannerActivity current=liveScanner.get();
        if(current!=null)current.runOnUiThread(()->current.showLiveResolution(message));
    }
    private void showLiveResolution(JSONObject message){
        String id=message.optString("scan_id","");
        // Session membership is not sufficient: a stale completion from a
        // previous physical card must never unlock the current capture.
        if(id.isEmpty()||!id.startsWith(sessionId+"-")||
            !id.equals(scanCycle.pendingId())||!resolvedScanIds.add(id))return;
        String result=message.optString("status","review");
        boolean saved="verified".equals(result)||"unverified".equals(result);
        if("verified".equals(result))verifiedCount++;
        else if("unverified".equals(result))unverifiedCount++;
        else reviewCount++;
        // Scene analysis continued during the WebView persistence call.
        // It may already have identified the NEXT card, but cannot scan it
        // until the exact previous capture receives this final receipt.
        boolean readyScene=scanCycle.receivedFinalReceipt(id,SystemClock.elapsedRealtime());
        long persistMs=scanCycle.persistLatencyMs();
        lastResolvedReceiptAt=SystemClock.elapsedRealtime();
        lastResolvedReceiptLatencyMs=persistMs;
        long postSaveSceneMs=scanCycle.postSaveSceneWaitMs();
        Log.i(TAG,"Scan cycle receipt "+id+" · status="+result+
            " · persistence_ms="+persistMs+" · post_save_scene_wait_ms="+postSaveSceneMs+
            " · next_scene_prearmed="+scanCycle.scenePrearmedBeforeReceipt());
        resolutionPending=false;
        reviewDialogOpen=false;
        if(scanReviewDialog!=null){scanReviewDialog.dismiss();scanReviewDialog=null;scanReviewId="";}
        if(saved){
            lastSavedName=message.optString("name", "").trim();
            lastSavedAt=SystemClock.elapsedRealtime();
            unresolvedRetryCount=0;
            unresolvedRetryIdentity="";
            playSaveSound(message.optInt("volume",60));
            // Learn challenging artwork ONLY after a user-selected printing is
            // confirmed saved. The background task reads the existing local
            // JPG and persists features; it never uploads private photos or
            // delays the camera/receipt gate.
            if("verified".equals(result) && message.optBoolean("manual_selected",false)){
                JSONObject printing=message.optJSONObject("printing");
                VisualMatcher matcher=visual;
                String printingId=printing==null?"":printing.optString("id","");
                String illustrationId=printing==null?"":printing.optString("illustration_id","");
                String learnedName=message.optString("name","");
                File acceptedPhoto=null;
                for(JSONObject item:captured)if(id.equals(item.optString("scan_id"))){
                    String name=item.optString("image_path","");
                    File candidate=new File(getCacheDir(),new File(name).getName());
                    if(candidate.getAbsolutePath().equals(name)&&candidate.isFile())acceptedPhoto=candidate;
                    break;
                }
                final File photoForLearning=acceptedPhoto;
                if(matcher!=null && photoForLearning!=null && !printingId.isEmpty())
                    referenceExecutor.execute(()->{
                        if(matcher.learnConfirmedPhotograph(learnedName,printingId,illustrationId,photoForLearning))
                            Log.i(TAG,"Learned confirmed printing artwork locally: "+learnedName);
                    });
            }
            if(scanSuccessPanel!=null && scanSuccessPanel.getParent()==cameraRoot)
                cameraRoot.removeView(scanSuccessPanel);
            String photo="";
            for(JSONObject item:captured)if(id.equals(item.optString("scan_id"))){
                photo=item.optString("image_path","");break;
            }
            int bottom=cameraActions==null?dp(72):cameraActions.getHeight();
            scanSuccessPanel=ScanResultUi.success(this,cameraRoot,message,photo,bottom);
        }
        if(!closing){
            reviewButton.setText(finishRequested?"Saving… ("+Math.max(0,captured.size()-resolvedScanIds.size())+" pending)":
                    "Finish ("+(verifiedCount+unverifiedCount)+" saved)");
            if(waitingForNext && captured.size()>0 && id.equals(captured.get(captured.size()-1).optString("scan_id")))
                status.setText(("verified".equals(result)?"Saved printing": "unverified".equals(result)?"Saved as unverified":
                    "retry".equals(result)?"Retrying card": "Skipped card")+
                    ": "+message.optString("name","")+
                    ("retry".equals(result)?" · hold card steady":" · stack next card"));
            if("retry".equals(result)){
                String failed=message.optString("name","");
                if(failed.equals(unresolvedRetryIdentity))unresolvedRetryCount++;
                else{unresolvedRetryIdentity=failed;unresolvedRetryCount=1;}
                if(readyScene){
                    // A genuinely new stack scene is already visible. Do not
                    // re-OCR the previous failed capture or punish the new card.
                    resumeAfterSceneChange("Previous card not saved · identifying new top card");
                    if(finishRequested && resolvedScanIds.size()>=captured.size())completeBatch();
                    return;
                }
                if(unresolvedRetryCount>=2){
                    // Do not endlessly re-run OCR on the same physical top card.
                    // Keep the camera paused until the user stacks a NEW card.
                    waitingForNext=true;
                    status.setText("Unresolved: "+failed+" · use Close to review, or stack next card");
                    keepButton.setText("Next card");keepButton.setEnabled(true);
                    return;
                }
                waitingForNext=false;stackGate.clear();scanCycle.clearPending();goodFrames=0;
                previousOcr="";previousOcrCount=0;
                sceneStartedAt=SystemClock.elapsedRealtime();
                keepButton.setText("Keep title");keepButton.setEnabled(false);
            }else if(readyScene){
                resumeAfterSceneChange("Saved · new top card ready · identifying…");
            }else if(waitingForNext){
                keepButton.setText("Copy options");keepButton.setEnabled(true);
            }
            if(finishRequested && resolvedScanIds.size()>=captured.size())completeBatch();
        }
    }
    /** Called on the main thread after both the scene and durable receipt
     * gates have completed (in either order). Never runs for a stale receipt. */
    private void resumeAfterSceneChange(String message){
        if(closing||finishRequested||!waitingForNext)return;
        waitingForNext=false;
        confirmedNewTop=true;
        sceneStartedAt=scanCycle.sceneAt()>0?scanCycle.sceneAt():SystemClock.elapsedRealtime();
        previousOcr="";previousOcrCount=0;
        pendingArtworkChoices=null;
        lastRecognitionAt=0;
        unresolvedRetryCount=0;unresolvedRetryIdentity="";
        scanCycle.clearPending();
        keepButton.setText("Keep title");keepButton.setEnabled(false);
        if(scanSuccessPanel!=null && scanSuccessPanel.getParent()==cameraRoot)
            cameraRoot.removeView(scanSuccessPanel);
        scanSuccessPanel=null;
        status.setText(message);
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
        if(closing||!resolutionPending)return;
        final String id=payload.optString("scan_id","");
        if(id.isEmpty()||captured.isEmpty()||
           !id.equals(captured.get(captured.size()-1).optString("scan_id")))return;
        if(reviewDialogOpen){
            // A timeout may have shown a generic recovery dialog. Replace it
            // with the later, evidence-backed Scryfall candidate gallery if
            // that arrives, rather than hiding the actual printing choices.
            int count=(payload.optJSONArray("choices")==null?0:payload.optJSONArray("choices").length())+
                (payload.optJSONArray("suggestions")==null?0:payload.optJSONArray("suggestions").length());
            if(!id.equals(scanReviewId)||count==0)return;
            if(scanReviewDialog!=null)scanReviewDialog.dismiss();
            reviewDialogOpen=false;
        }
        reviewDialogOpen=true;
        scanReviewId=id;
        // A visual, filtered selector. The camera remains paused until the
        // chosen printing is actually persisted and a save receipt arrives.
        status.setText("Choose card / printing · scanner paused");
        scanReviewDialog=ScanResultUi.review(this,payload,(action,printingId,name)->{
            sendImmediateReviewChoice(id,action,printingId,name);
            reviewDialogOpen=false;
            scanReviewDialog=null;
            scanReviewId="";
            status.setText("Finalizing your selection…");
        });
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
    private Button resolutionButton,lowLightButton;
    private static final String LOW_LIGHT_PREF="native_low_light_auto";
    private boolean autoLowLight=true;
    private int exposureStep=0;
    private long exposureChangedAt=0;
    private int lowLightSamples=0,brightSamples=0;
    private boolean focusMetered=false;
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
    private volatile long lastCapturedTitleHash=0,lastCapturedFullHash=0;
    private volatile byte[] lastCapturedColor=null;
    private long sceneStartedAt=0;
    private long sceneCandidateAt=0,sceneConfirmedAt=0;
    private long missingSince=0;
    private int changedEmptyFrames=0;
    private volatile long currentFrameSceneHash=0;
    private volatile PointF[] lastCapturedCorners=null;
    private volatile double lastCapturedBrightness=0,currentCardBrightness=0;
    private volatile boolean waitingForNext=false;
    private String previousOcr="",lastSeenTitle="";
    private int previousOcrCount=0;
    private volatile int unresolvedRetryCount=0;
    private volatile String unresolvedRetryIdentity="";
    // The live camera may visually change under glare. Until the user confirms
    // a second physical copy, do not count the same just-saved title twice.
    private volatile String lastSavedName="";
    private volatile long lastSavedAt=0;
    private volatile boolean sameCopyOverridePending=false;
    // A true StackChangeGate transition (or explicit physical-copy confirmation)
    // authorizes the next save even if the card name is identical.
    private volatile boolean confirmedNewTop=false;
    private long previewHash=0;
    private volatile Bitmap availableCard;
    private long lastEdgeMs=0,lastWarpMs=0,lastArtMs=0,lastOcrMs=0;
    private int sourceWidth=1,sourceHeight=1;
    private FrameLayout cameraRoot;
    private View scanSuccessPanel;
    private LinearLayout cameraActions;

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
        autoLowLight=getPreferences(MODE_PRIVATE).getBoolean(LOW_LIGHT_PREF,true);
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
        // Keep the entire physical card visible. The controls live outside
        // the camera surface instead of covering its lower printing marks.
        FrameLayout cameraSurface=new FrameLayout(this);
        cameraSurface.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        cameraSurface.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams cameraArea=new FrameLayout.LayoutParams(-1,-1,Gravity.TOP);
        cameraArea.bottomMargin=dp(82);
        root.addView(cameraSurface,cameraArea);
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
        // Camera2 auto exposure alone underexposes small dark cards against a
        // white fixed stand. The optional compensation uses only camera metering,
        // NOT software gain applied to the saved/visual-matching photograph.
        lowLightButton=new Button(this);lowLightButton.setAllCaps(false);
        lowLightButton.setTextSize(10);lowLightButton.setMinWidth(0);lowLightButton.setMinimumWidth(0);
        lowLightButton.setText(autoLowLight?"Low light: Auto":"Low light: Off");
        lowLightButton.setOnClickListener(v->{
            autoLowLight=!autoLowLight;
            getPreferences(MODE_PRIVATE).edit().putBoolean(LOW_LIGHT_PREF,autoLowLight).apply();
            lowLightSamples=brightSamples=0;
            lowLightButton.setText(autoLowLight?"Low light: Auto":"Low light: Off");
            if(!autoLowLight)applyExposure(0,SystemClock.elapsedRealtime());
            showMetrics();
        });
        LinearLayout options=new LinearLayout(this);options.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        options.addView(lowLightButton,new LinearLayout.LayoutParams(dp(124),dp(34)));
        TextView hint=new TextView(this);hint.setText("Auto exposure · keep card steady");
        hint.setTextColor(0xFFBBCBBC);hint.setTextSize(10);
        options.addView(hint,new LinearLayout.LayoutParams(-2,-2));
        top.addView(options);
        cameraSurface.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
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
                boolean unresolvedBlock=unresolvedRetryCount>=2;
                // Two explicit physical-copy choices: the stationary card
                // must never create another collection entry merely because
                // exposure or framing changed. The user can acknowledge a
                // second indistinguishable copy WITHOUT waiting for a timer.
                new android.app.AlertDialog.Builder(this)
                    .setTitle(unresolvedBlock?"Unresolved card · what next?":"Physical copy control")
                    .setMessage(unresolvedBlock?
                        "This title was not saved. Keep the same card in view to retry, or place a different physical card on top and select Next card.":
                        "Same physical card = already saved; do NOT increase quantity. Another physical copy = you stacked a SECOND card; scan it and add one more to the collection.")
                    .setNegativeButton("Cancel",(dialog,which)->{})
                    .setNeutralButton(unresolvedBlock?"Still same card":"Same card · no save",(dialog,which)->{
                        if(closing||!waitingForNext)return;
                        status.setText(unresolvedBlock?
                            "Same unresolved card · not saved again":"Same physical card · no additional copy saved");
                        // Remain gated: this is not a new-card event.
                        keepButton.setText(unresolvedBlock?"Next card":"Copy options");
                        keepButton.setEnabled(true);
                    })
                    .setPositiveButton(unresolvedBlock?"Next card":"Another copy · scan +1",(dialog,which)->{
                        if(!waitingForNext||closing||resolutionPending||reviewDialogOpen)return;
                        waitingForNext=false;sceneStartedAt=SystemClock.elapsedRealtime();
                        sameCopyOverridePending=true;
                        confirmedNewTop=true;
                        scanCycle.clearPending();
                        // The user explicitly confirms that another physical
                        // piece of cardboard has been placed onto the stack.
                        unresolvedRetryCount=0;unresolvedRetryIdentity="";
                        previousOcr="";previousOcrCount=0;goodFrames=0;
                        stackGate.clear();
                        lastRecognitionAt=SystemClock.elapsedRealtime();
                        status.setText(unresolvedBlock?"New top card confirmed · identifying…":
                            "Second physical copy confirmed · identifying and saving +1…");
                        keepButton.setText("Keep title");keepButton.setEnabled(false);
                    }).show();
            }else if(card!=null&&!card.isRecycled()&&!lastSeenTitle.isEmpty())
                enqueue(lastSeenTitle,"manual-title",0,null,card,previewHash);
        });
        actions.addView(keepButton,new LinearLayout.LayoutParams(0,dp(48),1));
        reviewButton=new Button(this);reviewButton.setText("Finish (0 saved)");reviewButton.setAllCaps(false);reviewButton.setTextSize(12);reviewButton.setMinWidth(0);reviewButton.setMinimumWidth(0);
        reviewButton.setOnClickListener(v->finishBatch());
        actions.addView(reviewButton,new LinearLayout.LayoutParams(0,dp(48),1));
        cameraActions=actions;
        root.addView(actions,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        // Measure the real button bar (font scaling, orientation, cutouts)
        // rather than guessing its height. Preview and overlays never extend
        // behind the bar, so older cards' bottom printing stays visible.
        actions.addOnLayoutChangeListener((view,left,topEdge,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int controlHeight=bottom-topEdge;
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)cameraSurface.getLayoutParams();
            if(controlHeight>0 && lp.bottomMargin!=controlHeight){
                lp.bottomMargin=controlHeight;
                cameraSurface.setLayoutParams(lp);
            }
        });
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
        exposureStep=0;exposureChangedAt=0;lowLightSamples=brightSamples=0;focusMetered=false;
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
        exposureStep=camera.getCameraInfo().getExposureState().getExposureCompensationIndex();
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
        cardInfo+=" · low light "+(autoLowLight?(exposureStep>0?"auto +"+exposureStep:"auto"):"off");
        if(standZoomApplied>1.02f)cardInfo+=" · stand zoom "+String.format(Locale.US,"%.2f",standZoomApplied)+"×";
        String detail=lastTiming.isEmpty()?"": "\n"+lastTiming;
        if(lastArtworkRefs>=0)detail+="\nArtwork library: "+lastArtworkRefs+" bundled/learned · Scryfall symbol icons: "+BundledSetSymbols.count()+"/"+BundledSetSymbols.eligibleCount()+" physical sets · cached edition signatures: "+ReferenceFingerprintCache.count();
        int fullIndexFaces=FullArtworkIndex.totalFaces(getApplicationContext());
        detail+="\nFull printing artwork: "+(fullIndexFaces>0?
            fullIndexFaces+" faces ("+FullArtworkIndex.installedVersion(getApplicationContext())+")":
            "NOT INSTALLED — using starter references");
        detail+=" · last indexed lookup: "+lastFullIndexLookup;
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
        focusMetered=false;
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
        // Prevent exposure hunting or excessive highlights with the LED on.
        lowLightSamples=brightSamples=0;
        if(torch)applyExposure(0,SystemClock.elapsedRealtime());
        torchButton.setText(torch?"Light on":"Light off");
    }
    /** AE compensation is a small, bounded camera adjustment, never a filter on
     * reference artwork. Do not wait on CameraControl's asynchronous future. */
    private void applyExposure(int requested,long now){
        Camera active=camera;if(active==null)return;
        ExposureState state=active.getCameraInfo().getExposureState();
        if(!state.isExposureCompensationSupported())return;
        Range<Integer> range=state.getExposureCompensationRange();
        int next=Math.max(range.getLower(),Math.min(range.getUpper(),requested));
        if(next==exposureStep)return;
        exposureStep=next;exposureChangedAt=now;
        active.getCameraControl().setExposureCompensationIndex(next);
        // A metering change must not masquerade as a stack transition.
        lastRecognitionAt=Math.max(lastRecognitionAt,now+350);
        runOnUiThread(()->{if(!closing)showMetrics();});
        Log.i(TAG,"Low-light exposure compensation: "+next);
    }
    /** A single center-weighted autofocus/metering pass on the fixed stand.
     * AF still operates normally afterward. Never block image analysis on AF. */
    private void meterFixedStandOnce(){
        if(focusMetered||camera==null)return;
        focusMetered=true;
        try{
            MeteringPoint point=preview.getMeteringPointFactory().createPoint(.5f,.54f);
            FocusMeteringAction action=new FocusMeteringAction.Builder(point,
                    FocusMeteringAction.FLAG_AF|FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(5,TimeUnit.SECONDS).build();
            camera.getCameraControl().startFocusAndMetering(action);
        }catch(Exception e){Log.w(TAG,"Focus/metering request not supported",e);}
    }
    /** Evaluate only on a stable detected card. Every exposure change requires
     * multiple agreeing frames and a cooldown so dark art/glare cannot oscillate
     * camera exposure and cause accidental duplicate stack scans. */
    private void adjustLowLight(Bitmap frame,CardQuadDetector.Detection card,long now){
        if(!autoLowLight||torch||resolutionPending||reviewDialogOpen||waitingForNext||
           recognizing.get()||camera==null||card==null||goodFrames<2)return;
        if(now-exposureChangedAt<2400)return;
        double luminance=regionBrightness(frame,card.corners);
        if(luminance<0)return;
        int decision=LowLightPolicy.next(exposureStep,luminance);
        if(decision>exposureStep){lowLightSamples++;brightSamples=0;
            if(lowLightSamples>=3){lowLightSamples=0;applyExposure(decision,now);}}
        else if(decision<exposureStep){brightSamples++;lowLightSamples=0;
            if(brightSamples>=5){brightSamples=0;applyExposure(decision,now);}}
        else {lowLightSamples=0;brightSamples=0;}
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
            // Preview stays live, but do not burn CPU doing full-size edge finding
            // every 75ms while the resolver or visual picker has the scan lock.
            // Scene monitoring keeps running while WebView persists the previous card.
            // A review dialog remains deliberately slower; no new OCR starts
            // until a durable receipt is acknowledged.
            int frameInterval=reviewDialogOpen?180:waitingForNext?65:75;
            if(closing||now-lastProcessAt<frameInterval)return;
            lastProcessAt=now;
            // The following card has already passed multi-frame scene gating.
            // Until the previous save receipt arrives, do not even convert
            // full-resolution CameraX frames. Preview remains live separately.
            if(waitingForNext && scanCycle.hasSceneReady() && resolutionPending)return;
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
                    detected.areaFraction<smoothed.areaFraction*.86f&&
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
            if(good&&smoothed!=null&&goodFrames>=4&&!focusMetered)
                runOnUiThread(()->{if(!closing)meterFixedStandOnce();});
            if(good&&smoothed!=null)adjustLowLight(frame,smoothed,SystemClock.elapsedRealtime());
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
                // Recognition stays locked, but scene monitoring may pre-arm
                // the NEXT card before the old printing's receipt returns.
                if(reviewDialogOpen){corrected.recycle();return;}
                if(scanCycle.hasSceneReady()){corrected.recycle();return;}
                long fullHash=wholeFrameHash(corrected);
                boolean newTop=stackGate.observe(hash,fullHash,VisualMatcher.titleHash(corrected),now);
                corrected.recycle();
                if(newTop){
                    sceneCandidateAt=stackGate.changedCandidateAt();
                    sceneConfirmedAt=now;
                    final String reason=stackGate.lastEvent();
                    // A cover/reveal with the SAME photographed artwork can
                    // also be a hand, shadow, or camera exposure transient.
                    // Never auto-increment quantity on that alone. The user
                    // must choose Another copy in the explicit copy control.
                    if(reason.contains("identical copy")){
                        Log.i(TAG,"Identical-looking scene transition · awaiting physical-copy confirmation");
                        runOnUiThread(()->{
                            if(!closing && waitingForNext){
                                status.setText("Identical-looking top card · choose Copy options to distinguish same card from another copy");
                                keepButton.setText("Copy options");keepButton.setEnabled(true);
                            }
                        });
                        return;
                    }
                    boolean receiptAlreadyArrived=scanCycle.observedNewScene(now);
                    Log.i(TAG,"Scan cycle scene change · event="+reason+
                        " · waiting_for_receipt="+!receiptAlreadyArrived);
                    if(receiptAlreadyArrived){
                        // If the UI thread has not yet processed the receipt,
                        // schedule all state changes on that thread.
                        runOnUiThread(()->{
                            if(!closing && !resolutionPending && waitingForNext)
                                resumeAfterSceneChange("New top card ("+reason+") · identifying…");
                        });
                    }else runOnUiThread(()->{
                        if(!closing && waitingForNext && resolutionPending)
                            status.setText("New card detected · completing previous save…");
                    });
                }
                return;
            }
            if(!recognizing.compareAndSet(false,true)){corrected.recycle();return;}
            previewHash=hash;
            Bitmap old=availableCard;availableCard=corrected;
            if(old!=null&&old!=corrected&&!old.isRecycled())old.recycle();
            begin=SystemClock.elapsedRealtime();
            VisualMatcher v=visual;
            // The perspective rectifier ALWAYS outputs portrait dimensions,
            // even when the physical card was sideways. Check the detected
            // outer border BEFORE rectification to avoid comparing a sideways
            // battle/split card to a portrait artwork template.
            android.graphics.PointF[] corners=smoothed.corners;
            double topEdge=Math.hypot(corners[1].x-corners[0].x,corners[1].y-corners[0].y);
            double sideEdge=Math.hypot(corners[3].x-corners[0].x,corners[3].y-corners[0].y);
            boolean wasSideways=topEdge>sideEdge*1.12;
            VisualMatcher.Match candidate=v==null||wasSideways?null:v.match(corrected);
            lastArtMs=SystemClock.elapsedRealtime()-begin;
            // Artwork references are advisory for CARD NAME. A 1,200-image
            // seed cannot safely compete against the complete card catalog,
            // and full-art/promotional frames can have misleading art crops.
            // Always examine the physically printed TITLE first. Printing
            // verification may then use art/set/collector as independent clues.
            // Avoid early return from a visually similar but wrong artwork
            // reference (observed: Webstrike Elite -> unrelated split title).
            if(candidate!=null&&candidate.usable())
                Log.i(TAG,"Artwork-only title suggestion (not auto-accepted): "+
                    candidate.name+" score="+candidate.similarity);
            readTitle(corrected,hash,0,SystemClock.elapsedRealtime(),wasSideways);
        }catch(Exception e){Log.w(TAG,"Native frame analysis failed",e);recognizing.set(false);}
        finally{frame.recycle();}
    }
    private static Bitmap rotate(Bitmap source,int degrees){
        if(degrees==0)return source;
        Matrix m=new Matrix();m.postRotate(degrees);
        return Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);
    }
    private static Bitmap titleImage(Bitmap card,boolean enhancedPass,boolean sideways){
        // Start with a genuinely title-only crop. The previous 23.5% crop
        // contained artwork, and a 180-degree rescue could contain credits or
        // flavor text instead. Keep the 23.5% color crop for alternate layouts.
        int x=Math.round(card.getWidth()*.022f),y=Math.round(card.getHeight()*.012f);
        int w=Math.round(card.getWidth()*.956f);
        int h=Math.round(card.getHeight()*.235f);
        if(enhancedPass)h=Math.round(card.getHeight()*(sideways?.23f:.145f));
        else if(sideways)h=Math.round(card.getHeight()*.32f);
        Bitmap strip=Bitmap.createBitmap(card,x,y,Math.min(w,card.getWidth()-x),Math.min(h,card.getHeight()-y));
        Bitmap prepared=enhancedPass?CardQuadDetector.enhanceTitle(strip):strip;
        Bitmap upscaled=Bitmap.createScaledBitmap(prepared,prepared.getWidth()*2,prepared.getHeight()*2,true);
        if(prepared!=strip)prepared.recycle();strip.recycle();return upscaled;
    }
    /** Title placement is relative to the physical card, not OCR text bounds.
     * Never select a longer rules or flavor line merely because it is longer. */
    private static String selectTitle(Text text,int imageHeight,NativeNameIndex index){
        String best="";float highest=-999f;
        for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
            String value=line.getText().trim().replaceAll("\\s+"," ");
            String letters=value.replaceAll("[^\\p{L}]","");
            if(letters.length()<3||letters.length()>52||value.length()>60 ||
                    TitleReadingPolicy.isFooterOrRulesNoise(value))continue;
            android.graphics.Rect bounds=line.getBoundingBox();
            if(bounds==null)continue;
            float position=bounds.centerY()/(float)Math.max(1,imageHeight);
            // The lower portion of the expanded rescue crop includes artwork
            // lettering. Allow it ONLY for a real indexed, two-line title.
            String canonical=index==null?null:index.exact(value);
            if(!TitleReadingPolicy.plausibleTitlePosition(position,canonical!=null))continue;
            if(value.matches("(?i).*\\b(creature|instant|sorcery|enchantment|artifact|planeswalker|battle)\\s*[—-].*"))continue;
            if(letters.matches("(?i).*(.)\\1{4,}.*"))continue;
            float score=letters.length()*.30f - Math.max(0,value.length()-letters.length())*.70f
                +Math.max(-18f,13f-35f*position);
            if(canonical!=null)score+=28f;
            if(line.getConfidence()>0)score+=line.getConfidence()*3f;
            if(score>highest){highest=score;best=value;}
        }
        return best;
    }
    /** Detect two-line stylized titles, but only when the combined text is a
     * real indexed card name. Never concatenate arbitrary rules text. */
    private static String indexedTwoLineTitle(Text result,NativeNameIndex index,int imageHeight){
        if(index==null)return null;
        ArrayList<Text.Line> lines=new ArrayList<>();
        for(Text.TextBlock block:result.getTextBlocks())lines.addAll(block.getLines());
        for(int i=0;i<lines.size();i++)for(int j=i+1;j<lines.size();j++){
            Text.Line a=lines.get(i),b=lines.get(j);
            android.graphics.Rect r=a.getBoundingBox(),t=b.getBoundingBox();
            if(r==null||t==null||t.top<r.top||t.top-r.bottom>Math.max(40,r.height()*1.8))continue;
            if(Math.abs(r.left-t.left)>Math.max(65,r.width()*.5))continue;
            String combined=a.getText().trim()+" "+b.getText().trim();
            if(TitleReadingPolicy.isFooterOrRulesNoise(combined))continue;
            if(r.centerY()>imageHeight*.58f || t.centerY()>imageHeight*.85f)continue;
            String canonical=index.exact(combined);
            if(canonical!=null)return canonical;
        }
        return null;
    }
    /** Two-line title with a *single* OCR-glyph error in the long first word.
     * E.g. WEPSTRIKE / ELITE suggests Webstrike Elite; this remains an OCR
     * CANDIDATE requiring a second read and independent printing evidence. */
    private static String suggestedTwoLineTitle(Text result,NativeNameIndex index,int height){
        if(index==null)return null;
        ArrayList<Text.Line> lines=new ArrayList<>();
        for(Text.TextBlock block:result.getTextBlocks())lines.addAll(block.getLines());
        for(int i=0;i<lines.size();i++)for(int j=i+1;j<lines.size();j++){
            Text.Line a=lines.get(i),b=lines.get(j);
            android.graphics.Rect r=a.getBoundingBox(),t=b.getBoundingBox();
            if(r==null||t==null||t.top<r.top||t.top-r.bottom>Math.max(40,r.height()*2))continue;
            if(r.centerY()>height*.52f||t.centerY()>height*.86f)continue;
            if(Math.abs(r.left-t.left)>Math.max(70,r.width()*.6f))continue;
            String first=a.getText().trim(),second=b.getText().trim();
            String possible=index.distinctiveTitlePrefix(first);
            if(possible==null||index.exact(first)!=null||!possible.contains(" "))continue;
            int space=possible.indexOf(' ');
            if(NativeNameIndex.norm(possible.substring(space+1)).equals(NativeNameIndex.norm(second)))
                return possible;
        }
        return null;
    }
    /** A battle card prints its title sideways on a portrait cardboard frame.
     * This is a *catalog hint*, not permission to save based on rotated OCR. */
    private static boolean rotatedBattleCandidate(String canonical){
        if(canonical==null || canonical.contains(" // ") ||
           !NativePrintingIndex.status().startsWith("Ready:"))return false;
        try{
            JSONObject results=new JSONObject(NativePrintingIndex.lookup("name",NativeNameIndex.norm(canonical),6));
            JSONArray rows=results.optJSONArray("rows");
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject card=rows.optJSONObject(i);
                if(card!=null && card.optString("type_line","").toLowerCase(Locale.ROOT)
                    .contains("battle"))return true;
            }
        }catch(Exception e){Log.i(TAG,"Optional battle title lookup unavailable",e);}
        return false;
    }
    static int[] titleRotationOrder(int width,int height){
        return width>height*1.12f ? new int[]{90,270,0,180} : new int[]{0,180,90,270};
    }
    /** Search all four orientations BEFORE committing an unknown OCR title.
     * A battle or split card turned sideways can have readable rules text in
     * the title strip while its actual title is in a later rotation. */
    private void readTitle(Bitmap card,long hash,int rotationIndex,long began,boolean sideways){
        readTitle(card,hash,rotationIndex,began,sideways,null,0,true,false);
    }
    private void readTitle(Bitmap card,long hash,int rotationIndex,long began,
            boolean sideways,String tentative,int tentativeRotation,boolean enhancedPass,
            boolean tentativeTwoLine){
        if(closing){recognizing.set(false);return;}
        // The rectifier already returns a portrait-shaped image, including
        // sideways battles. Try upright and upside-down title strips before
        // the final perpendicular rescue orientations.
        int[] rotations=sideways ? new int[]{90,270,0,180} :
            titleRotationOrder(card.getWidth(),card.getHeight());
        Bitmap rotated=rotate(card,rotations[rotationIndex]);
        Bitmap strip=titleImage(rotated,enhancedPass,sideways || rotations[rotationIndex]%180!=0);
        recognizer.process(InputImage.fromBitmap(strip,0)).addOnCompleteListener(task->{
            String name="";
            if(task.isSuccessful()){
                name=selectTitle(task.getResult(),strip.getHeight(),names);
                String paired=indexedTwoLineTitle(task.getResult(),names,strip.getHeight());
                if(paired!=null)name=paired;
                else {
                    String suggested=suggestedTwoLineTitle(task.getResult(),names,strip.getHeight());
                    if(suggested!=null)name="__SUGGESTED__"+suggested;
                }
                if(TitleReadingPolicy.isFooterOrRulesNoise(name))name="";
            }else Log.w(TAG,"ML Kit title read failed",task.getException());
            strip.recycle();if(rotated!=card)rotated.recycle();
            if(closing){recognizing.set(false);return;}
            NativeNameIndex index=names;
            boolean suggestedTwoLine=name.startsWith("__SUGGESTED__");
            if(suggestedTwoLine)name=name.substring("__SUGGESTED__".length());
            String canonical=suggestedTwoLine?null:name.isEmpty()?null:index==null?null:index.exact(name);
            // For a physically upright portrait card, a 180-degree rotation
            // puts the illustrator and flavor attribution into the title crop.
            // An exact catalog-name hit there (e.g. "Volrath" printed beneath
            // "Light of Day") is NOT evidence that it was the card's title.
            // Sideways cards instead require a perpendicular title rotation.
            // Leave inverted portrait cards for user review rather than auto-save.
            // A portrait card rotated sideways can expose rules/flavor words
            // that happen to be *another* real card name. Only genuine TWO
            // face split candidates are allowed through as provisional; both
            // physical title panels must be independently OCR-verified later.
            boolean perpendicularPortrait=!sideways &&
                (rotations[rotationIndex]==90 || rotations[rotationIndex]==270);
            boolean provisionalSplit=canonical!=null && perpendicularPortrait &&
                canonical.split(" // ",-1).length==2;
            boolean provisionalBattle=canonical!=null && perpendicularPortrait &&
                rotatedBattleCandidate(canonical);
            if(canonical!=null && !provisionalSplit && !provisionalBattle &&
                    !TitleReadingPolicy.allowAutomaticExactOrientation(sideways,rotations[rotationIndex])){
                canonical=null; name="";
            }
            // Three-or-more-face guesses (Who/What/When/Where/Why etc.) may
            // NEVER use an unverified OCR-only identity, even if ML Kit
            // spuriously returned an exact catalog string.
            if(canonical!=null && TitleRecoveryPolicy.isBadMultiFaceGuess(canonical,false)){
                canonical=null; name="";
            }
            if(canonical!=null){
                lastOcrMs=SystemClock.elapsedRealtime()-began;
                previousOcr=canonical.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                previousOcrCount=Math.max(1,previousOcrCount+1);
                lastSeenTitle=canonical;
                if(rotationIndex==0 && visual!=null && !waitingForNext){
                    Bitmap art=VisualMatcher.artRegion(card);
                    byte[] features=VisualMatcher.fingerprint(art);art.recycle();
                    final VisualMatcher matcher=visual;
                    // canonical may be nulled by orientation validation above;
                    // capture the resolved card name in an effectively-final local.
                    final String observedName=canonical;
                    referenceExecutor.execute(()->matcher.learnObservedName(observedName,features));
                }
                enqueue(canonical,provisionalSplit?"ocr-split-provisional":
                    provisionalBattle?"ocr-orientation-provisional":"ocr-exact",
                    (provisionalSplit||provisionalBattle)?0f:1f,
                    null,card,hash,rotations[rotationIndex]);
                recognizing.set(false);return;
            }
            // Prefer a plausible title from the first orientation, but don't
            // stop searching for an indexed title on a rotated card.
            String current=name.replaceAll("\\s+"," ").trim();
            // A unique first word on a two-line promotional title (e.g.
            // WEBSTRIKE / ELITE) is an OCR CANDIDATE, never an exact title.
            // The WebView will still require independent printing evidence.
            String partial=index==null?null:index.distinctiveTitlePrefix(current);
            if(partial!=null)current=partial;
            // Catalog-bucket correction is provisional, NEVER OCR-exact. It
            // repairs Cameo/Camco and a stray numeral on a promo title,
            // but a physical footer must still corroborate the printing.
            String corrected=index==null?null:index.suggestTitle(current);
            boolean correctedTitle=corrected!=null && !corrected.equalsIgnoreCase(current);
            if(correctedTitle)current=corrected;
            String bestTentative=tentative;
            int bestRotation=tentativeRotation;
            boolean bestSuggestedTwoLine=tentativeTwoLine;
            // On normal portrait cards only the original orientation may
            // supply an UNVERIFIED candidate. Upside-down and sideways rescue
            // can still accept an EXACT catalog title, but must never promote
            // illustrator credits (Karla Ortiz) or flavor text into a title.
            boolean trustedCandidateOrientation=TitleReadingPolicy.allowTentativeOrientation(sideways,rotationIndex);
            if(trustedCandidateOrientation && !current.isEmpty() &&
                    current.length()>=3 && current.length()<=55 &&
                    (bestTentative==null ||
                     (suggestedTwoLine && rotationIndex==0) ||
                     (rotationIndex==0 && current.length()>bestTentative.length()))){
                bestTentative=current;bestRotation=rotationIndex;
                bestSuggestedTwoLine=suggestedTwoLine && rotationIndex==0;
            }
            // If the enhanced pass did not find a title, retry the same
            // orientation with RAW COLOR. This is a rescue, not an added
            // cost on ordinary successful exact-title scans.
            if(enhancedPass && (rotationIndex==0 || rotationIndex==1)){
                readTitle(card,hash,rotationIndex,began,sideways,bestTentative,bestRotation,false,bestSuggestedTwoLine);
                return;
            }
            if(rotationIndex<3){
                readTitle(card,hash,rotationIndex+1,began,sideways,bestTentative,bestRotation,true,bestSuggestedTwoLine);
                return;
            }
            // Ordinary title strips failed. Sideways printed split cards (Night/Day,
            // Fire/Ice, Stand/Deliver) have TWO independent title bars, one in
            // each half of the portrait card. OCR them separately before taking
            // a generic word from a rotated rules box as the card name.
            if(!sideways && card.getHeight()>card.getWidth()*1.15f && names!=null){
                final String rescueName=bestTentative;
                final int rescueRotation=bestRotation;
                final boolean rescueTwoLine=bestSuggestedTwoLine;
                readSplitTitlePanels(card,hash,began,()->
                    readLowerTitlePanel(card,hash,began,()->
                        completeUnmatchedTitle(card,hash,began,rescueName,rescueRotation,rescueTwoLine,false)));
                return;
            }
            if(!sideways){
                final String rescueName=bestTentative;
                final int rescueRotation=bestRotation;
                final boolean rescueTwoLine=bestSuggestedTwoLine;
                readLowerTitlePanel(card,hash,began,()->
                    completeUnmatchedTitle(card,hash,began,rescueName,rescueRotation,rescueTwoLine,false));
            }else completeUnmatchedTitle(card,hash,began,bestTentative,bestRotation,bestSuggestedTwoLine,true);
        });
    }
    /** Some showcase/full-art cards, including Endless Sands, put the title
     * below the illustration rather than in the normal top bar. This probe is
     * only invoked AFTER ordinary title and split-panel OCR fail. A name found
     * here is PROVISIONAL: rules/flavor text may resemble a catalog title.
     * Set/collector evidence and confirmation must decide final identity. */
    private void readLowerTitlePanel(Bitmap card,long hash,long began,Runnable fallback){
        Bitmap region=null;
        try{
            int x=Math.round(card.getWidth()*.055f);
            int y=Math.round(card.getHeight()*.53f);
            int w=Math.min(card.getWidth()-x,Math.round(card.getWidth()*.89f));
            int h=Math.min(card.getHeight()-y,Math.round(card.getHeight()*.29f));
            Bitmap crop=Bitmap.createBitmap(card,x,y,w,h);
            region=Bitmap.createScaledBitmap(crop,w*2,h*2,true);
            crop.recycle();
        }catch(Exception e){Log.i(TAG,"Lower-title probe unavailable",e);fallback.run();return;}
        final Bitmap image=region;
        recognizer.process(InputImage.fromBitmap(image,0)).addOnCompleteListener(task->{
            String match=null;
            try{
                if(task.isSuccessful() && names!=null){
                    for(Text.TextBlock block:task.getResult().getTextBlocks())
                        for(Text.Line line:block.getLines()){
                            String raw=line.getText().trim();
                            if(TitleReadingPolicy.isFooterOrRulesNoise(raw))continue;
                            String exact=names.exact(raw);
                            if(exact!=null && !exact.contains(" // ")){
                                // An exact name in the lower band remains
                                // only a physical candidate until corroborated.
                                match=exact;break;
                            }
                        }
                }
            }finally{image.recycle();}
            if(match!=null && !closing){
                lastOcrMs=SystemClock.elapsedRealtime()-began;
                lastSeenTitle=match;
                Log.i(TAG,"Lower-position printed title candidate: "+match+
                    " · independent footer verification required");
                enqueue(match,"ocr-lower-title-provisional",0,null,card,hash,0);
                recognizing.set(false);
            }else fallback.run();
        });
    }
    /** A split-pair probe runs only after ordinary title recognition fails.
     * Both printed title bars must resolve to one exact two-face catalog name.
     * The downstream verifier independently checks both faces and the footer.
     * A read failure falls through to the existing manual title workflow. */
    private void readSplitTitlePanels(Bitmap card,long hash,long began,Runnable fallback){
        final float[][] zones=SplitCardLayoutPolicy.panels(card.getWidth(),card.getHeight());
        final java.util.ArrayList<String> first=new java.util.ArrayList<>();
        readOneSplitPanel(card,zones[0],first,()->{
            final java.util.ArrayList<String> second=new java.util.ArrayList<>();
            readOneSplitPanel(card,zones[1],second,()->{
                String paired=names==null?null:names.pairedSplitTitle(first,second);
                if(paired!=null && !closing){
                    lastOcrMs=SystemClock.elapsedRealtime()-began;
                    Log.i(TAG,"Both photographed split titles agree on: "+paired+
                        " (candidate; printing still requires verification)");
                    lastSeenTitle=paired;
                    enqueue(paired,"ocr-split-provisional",0,null,card,hash,0);
                    recognizing.set(false);
                }else fallback.run();
            });
        });
    }
    private void readOneSplitPanel(Bitmap card,float[] region,java.util.List<String> candidates,Runnable done){
        Bitmap transformed=null;
        try{
            int x=Math.max(0,Math.round(card.getWidth()*region[0]));
            int y=Math.max(0,Math.round(card.getHeight()*region[1]));
            int w=Math.max(1,Math.min(card.getWidth()-x,Math.round(card.getWidth()*region[2])));
            int h=Math.max(1,Math.min(card.getHeight()-y,Math.round(card.getHeight()*region[3])));
            Bitmap piece=Bitmap.createBitmap(card,x,y,w,h);
            // Upright portrait split halves have text sideways along the left
            // outer edge. The left edge becomes the TOP after this rotation.
            Bitmap upright=rotate(piece,90);
            piece.recycle();
            int titleHeight=Math.max(1,Math.round(upright.getHeight()*.22f));
            Bitmap title=Bitmap.createBitmap(upright,0,0,upright.getWidth(),titleHeight);
            upright.recycle();
            transformed=Bitmap.createScaledBitmap(title,title.getWidth()*2,title.getHeight()*2,true);
            title.recycle();
        }catch(Exception e){Log.i(TAG,"Split title region unavailable",e);done.run();return;}
        final Bitmap image=transformed;
        recognizer.process(InputImage.fromBitmap(image,0)).addOnCompleteListener(task->{
            try{
                if(task.isSuccessful()){
                    Text result=task.getResult();
                    for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
                        String raw=line.getText().trim();
                        if(!TitleReadingPolicy.isFooterOrRulesNoise(raw) && raw.length()>=3 && raw.length()<=35)
                            candidates.add(raw);
                    }
                }
            }finally{image.recycle();done.run();}
        });
    }
    /** No strong title, including a two-panel split: use the conservative
     * existing candidate and offline-artwork review path, never an auto-save. */
    private void completeUnmatchedTitle(Bitmap card,long hash,long began,
            String bestTentative,int bestRotation,boolean bestSuggestedTwoLine,boolean sideways){
            NativeNameIndex index=names;
            int[] titleRotations=sideways?new int[]{90,270,0,180}:titleRotationOrder(card.getWidth(),card.getHeight());
            lastOcrMs=SystemClock.elapsedRealtime()-began;
            if(bestTentative!=null && !TitleReadingPolicy.isFooterOrRulesNoise(bestTentative)){
                String normalized=bestTentative.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if(normalized.equals(previousOcr))previousOcrCount++;
                else{previousOcr=normalized;previousOcrCount=1;}
                lastSeenTitle=bestTentative;
                final String candidateTitle=bestTentative;
                runOnUiThread(()->{if(!closing){
                    status.setText("OCR candidate: "+candidateTitle+" · "+previousOcrCount+" read(s)");
                    keepButton.setEnabled(true);showMetrics();
                }});
                // A short name like JACE must reach the title-correction
                // gallery, never be discarded solely due to length.
                // For a uniquely reconstructed TWO-LINE title (e.g.
                // WEPSTRIKE / ELITE), capture once and ask the OFFLINE
                // collector+set resolver to corroborate it. This never skips
                // printing evidence or turns a fuzzy title into an exact one.
                boolean printingFirstTitleRescue=bestSuggestedTwoLine && bestRotation==0 &&
                        NativeNameIndex.norm(bestTentative).contains(" ") && normalized.length()>=10;
                if((previousOcrCount>=2 || printingFirstTitleRescue) && normalized.length()>=3 &&
                        !normalized.matches(".*(.)\\1{4,}.*"))
                    enqueue(bestTentative,printingFirstTitleRescue?"ocr-two-line-suggested":
                        "ocr-candidate",
                            0,null,card,hash,titleRotations[bestRotation]);
            }else{
                // No title in any orientation, including the RAW color
                // rescue. On a second stable attempt, capture the card for
                // footer-based *review suggestions*. The WebView must never
                // silently save without independent title confirmation.
                if("__unreadable_title__".equals(previousOcr))previousOcrCount++;
                else{previousOcr="__unreadable_title__";previousOcrCount=1;}
                if(previousOcrCount>=2){
                    // The full index may examine tens of thousands of band
                    // postings on a difficult card. Run it off the ML Kit
                    // callback / camera UI thread to prevent preview stalls.
                    final Bitmap snapshot=card.copy(Bitmap.Config.ARGB_8888,false);
                    if(snapshot==null){
                        lastSeenTitle="Unrecognized title";
                        enqueue(lastSeenTitle,"ocr-candidate",0,null,card,hash,0);
                    }else{
                        runOnUiThread(()->{if(!closing){
                            status.setText("Checking complete offline artwork index…");showMetrics();
                        }});
                        referenceExecutor.execute(()->{
                            String artName=null;
                            JSONArray alternatives=null;
                            try{
                                if(index!=null && FullArtworkIndex.ready(getApplicationContext())){
                                    JSONObject nearby=FullArtworkIndex.nearest(getApplicationContext(),snapshot,4);
                                    noteFullIndexLookup("name candidates: "+nearby.optInt("candidates",0)+
                                        " · "+nearby.optString("phase","unknown")+
                                        " · "+nearby.optLong("elapsed_ms",0)+"ms");
                                    Log.i(TAG,"Full artwork candidate retrieval: "+nearby.optString("phase","unknown")+
                                        " · "+nearby.optInt("examined",0)+" ranked faces · "+
                                        nearby.optInt("candidates",0)+" names · "+nearby.optLong("elapsed_ms",0)+"ms");
                                    alternatives=nearby.optJSONArray("choices");
                                    if(alternatives!=null && alternatives.length()>0){
                                        JSONObject first=alternatives.optJSONObject(0);
                                        JSONObject second=alternatives.optJSONObject(1);
                                        double bestArt=first==null?0:first.optDouble("score",0);
                                        if(ArtworkProbePolicy.strongAdvisoryName(bestArt,
                                            second==null?0:second.optDouble("score",0)))
                                            artName=index.exact(first.optString("nameKey",""));
                                    }
                                }
                            }catch(Exception e){
                                noteFullIndexLookup("candidate retrieval error: "+e.getClass().getSimpleName());
                                Log.w(TAG,"Complete artwork candidate lookup failed",e);
                            }finally{snapshot.recycle();}
                            final String suggestion=artName;
                            final JSONArray possible=alternatives;
                            runOnUiThread(()->{
                                if(!closing && recognizing.get()){
                                    pendingArtworkChoices=possible;
                                    lastSeenTitle=suggestion==null?"Unrecognized title":suggestion;
                                    if(suggestion!=null)
                                        Log.i(TAG,"Full artwork supplied unverified name suggestion: "+suggestion);
                                    enqueue(lastSeenTitle,suggestion==null?"ocr-candidate":"art-index-suggestion",
                                        0,null,card,hash,0);
                                }
                                recognizing.set(false);
                            });
                        });
                        return;
                    }
                }
            }
            recognizing.set(false);
    }
    /** Called on analyzer or ML Kit callback thread. Store each capture once,
     * then wait for a stable new top card on the physical stack. */
    private synchronized void enqueue(String name,String source,float score,String imageId,Bitmap card,long hash){
        enqueue(name,source,score,imageId,card,hash,0);
    }
    /** Save the card with the SAME orientation that produced the OCR title.
     * Keep the original frame orientation for stack-change fingerprinting. */
    private synchronized void enqueue(String name,String source,float score,String imageId,Bitmap card,long hash,int rotationDegrees){
        if(closing||finishRequested||waitingForNext)return;
        if(captured.size()>=MAX_SESSION_CAPTURES){
            if(!sessionLimitNotified){
             sessionLimitNotified=true;
             runOnUiThread(()->{if(!closing){
                status.setText("Session reached "+MAX_SESSION_CAPTURES+" captures. Finish to save diagnostics and start another session.");
                reviewButton.setText("Finish (session full)");
            }});
            }
            return;
        }
        // The StackChangeGate / ScanCycleCoordinator handshake, not elapsed
        // wall time or card name, decides whether a physical new top exists.
        // This allows legitimate back-to-back identical copies (e.g. Shining
        // Armor) while retaining the actual stable scene-change requirement.
        long now=SystemClock.elapsedRealtime();
        String normalized=name==null?"":name.trim().toLowerCase(Locale.ROOT);
        boolean retryLocked=unresolvedRetryCount>=2 && !normalized.isEmpty() &&
            normalized.equals(unresolvedRetryIdentity.trim().toLowerCase(Locale.ROOT));
        if(retryLocked && !sameCopyOverridePending && !confirmedNewTop){
            waitingForNext=true;
            previousOcr="";previousOcrCount=0;
            runOnUiThread(()->{if(!closing){
                status.setText("Unresolved card repeated · stack a different card or confirm Next card");
                keepButton.setText("Next card");keepButton.setEnabled(true);
            }});
            return;
        }
        // v5.12.27: A changed hash proves only that the image changed,
        // NOT that the user placed another physical copy on the stack.
        // Keep a conservative guard on consecutive saves of the SAME title
        // (even if glare/movement changes the hash substantially). Do not
        // impose a time window: another actual copy can be confirmed instantly
        // through Copy options, while different cards continue automatically.
        if(!sameCopyOverridePending && !lastSavedName.isEmpty() &&
                PhysicalCopyPolicy.needsCopyConfirmation(lastSavedName,name)){
            String previousId=captured.isEmpty()?"":captured.get(captured.size()-1).optString("scan_id","");
            if(!previousId.isEmpty() && resolvedScanIds.contains(previousId)){
                waitingForNext=true;
                confirmedNewTop=false;
                previousOcr="";previousOcrCount=0;
                stackGate.captured(hash,wholeFrameHash(card),VisualMatcher.titleHash(card),now);
                scanCycle.rearmConfirmedPrevious(previousId,now);
                runOnUiThread(()->{
                    if(!closing){
                        status.setText("Already saved "+name+" · same card or a second physical copy? Choose Copy options.");
                        keepButton.setText("Copy options");keepButton.setEnabled(true);
                    }
                });
                Log.i(TAG,"Copy confirmation required before another save of "+name+
                    " · scene change alone does not prove a new physical card");
                return;
            }
        }
        // Never equate one similar artwork hash with the same PHYSICAL card.
        // A distinct title is legitimate even if two artwork hashes collide.
        // Only extremely similar independent title/art/full-frame AND color
        // evidence yields a POSSIBLE conflict. Such a conflict is REVIEWED,
        // not silently reported as a confirmed duplicate or discarded.
        final long fullCardHash=wholeFrameHash(card);
        final long titleBandHash=VisualMatcher.titleHash(card);
        final byte[] cardColors=VisualMatcher.fingerprint(card);
        final String previousCaptureName=captured.isEmpty()?"":
                captured.get(captured.size()-1).optString("name","");
        final boolean confirmedPrior=!captured.isEmpty() &&
                resolvedScanIds.contains(captured.get(captured.size()-1).optString("scan_id",""));
        final boolean photoConflict=!sameCopyOverridePending && confirmedPrior &&
                CaptureIdentityPolicy.needsIdentityReview(previousCaptureName,name,
                    lastCapturedHash,hash,lastCapturedTitleHash,titleBandHash,
                    lastCapturedFullHash,fullCardHash,lastCapturedColor,cardColors);
        if(photoConflict)
            Log.w(TAG,"Near-identical visual evidence but DIFFERENT OCR title: prior="+
                previousCaptureName+" proposed="+name+"; requesting title review before persistence");
        sameCopyOverridePending=false;
        confirmedNewTop=false;
        try{
            int number=captured.size()+1;
            File photo=new File(getCacheDir(),"native-scan-"+sessionStart+"-"+number+".jpg");
            Bitmap printingPhoto=rotationDegrees==0?card:rotate(card,rotationDegrees);
            try(FileOutputStream out=new FileOutputStream(photo)){
                printingPhoto.compress(Bitmap.CompressFormat.JPEG,93,out);
            }finally{if(printingPhoto!=card)printingPhoto.recycle();}
            // Record reproducible quality evidence with every resolved/failed
            // capture. This is diagnostic only: a foil/card with a bright
            // treatment must not be silently rejected for glare or low detail.
            Bitmap thumb=Bitmap.createScaledBitmap(card,48,64,true);
            int[] rgb=new int[48*64];thumb.getPixels(rgb,0,48,0,0,48,64);thumb.recycle();
            CaptureQualityPolicy.Metrics quality=CaptureQualityPolicy.measure(rgb,48,64);
            JSONObject result=new JSONObject();
            result.put("quality_brightness",quality.mean);
            result.put("quality_edge",quality.edgeScore);
            result.put("quality_glare_pct",quality.glarePercent);
            result.put("quality_shadow_pct",quality.shadowPercent);
            result.put("quality_hint",quality.hint());
            result.put("name",name);result.put("source",source);result.put("artworkId",imageId==null?"":imageId);
            if("Unrecognized title".equals(name) && pendingArtworkChoices!=null){
                result.put("art_name_candidates",pendingArtworkChoices);
            }
            pendingArtworkChoices=null;
            result.put("identity_conflict",photoConflict);
            if(photoConflict)result.put("identity_conflict_note","Title differs from last saved card but visual regions are nearly identical; confirm title or retry");
            result.put("similarity",Math.round(score*1000)/1000.0);
            result.put("ocr_reads",source.startsWith("ocr")?previousOcrCount:0);
            result.put("elapsed",SystemClock.elapsedRealtime()-sessionStart);
            result.put("card_ms",SystemClock.elapsedRealtime()-(sceneStartedAt>0?sceneStartedAt:sessionStart));
            result.put("image_path",photo.getAbsolutePath());
            String scanId=sessionId+"-"+number;
            result.put("scan_id",scanId);
            result.put("scan_index",number-1);
            // Real capture-to-capture throughput, not only OCR callback time.
            long priorCapture=scanCycle.lastCaptureAtMs();
            result.put("previous_capture_to_scene_candidate_ms",
                    priorCapture>0&&sceneCandidateAt>=priorCapture?
                    sceneCandidateAt-priorCapture:-1);
            scanCycle.captured(scanId,SystemClock.elapsedRealtime());
            result.put("capture_gap_ms",scanCycle.captureGapMs());
            result.put("previous_receipt_latency_ms",lastResolvedReceiptLatencyMs);
            result.put("previous_receipt_to_capture_ms",lastResolvedReceiptAt>0?
                    Math.max(0,SystemClock.elapsedRealtime()-lastResolvedReceiptAt):-1);
            result.put("scene_candidate_to_confirm_ms",
                    sceneCandidateAt>0&&sceneConfirmedAt>=sceneCandidateAt?
                    sceneConfirmedAt-sceneCandidateAt:-1);
            result.put("scene_confirm_to_capture_ms",sceneConfirmedAt>0?
                    Math.max(0,SystemClock.elapsedRealtime()-sceneConfirmedAt):-1);
            result.put("scene_to_capture_ms",sceneStartedAt>0?
                    Math.max(0,SystemClock.elapsedRealtime()-sceneStartedAt):-1);
            result.put("photo_rotation_degrees",rotationDegrees);
            result.put("edge_ms",lastEdgeMs);result.put("warp_ms",lastWarpMs);
            result.put("art_ms",lastArtMs);result.put("ocr_ms",lastOcrMs);
            captured.add(result);
            runOnUiThread(()->{
                if(scanSuccessPanel!=null && scanSuccessPanel.getParent()==cameraRoot)
                    cameraRoot.removeView(scanSuccessPanel);
                scanSuccessPanel=null;
            });
            // Resolve and save now, while the camera is open. This capture is
            // also included in the final handoff for retry after interruptions.
            resolutionPending=true;
            MainActivity.deliverLiveCapture(result);
            stackGate.captured(hash,wholeFrameHash(card),VisualMatcher.titleHash(card),SystemClock.elapsedRealtime());
            waitingForNext=true;changeFrames=0;lastCapturedHash=hash;
            lastCapturedTitleHash=titleBandHash;
            lastCapturedFullHash=fullCardHash;
            lastCapturedColor=cardColors;
            lastCapturedSceneHash=currentFrameSceneHash;
            if(smoothed!=null){
                lastCapturedCorners=new PointF[4];
                for(int i=0;i<4;i++)lastCapturedCorners[i]=
                        new PointF(smoothed.corners[i].x,smoothed.corners[i].y);
            }
            lastCapturedBrightness=currentCardBrightness;
            missingSince=0;changedEmptyFrames=0;
            lastSeenTitle="";previousOcr="";previousOcrCount=0;
            final String msg="Captured "+name+" ("+source+") · resolving while watching for next card…";
            Log.i(TAG,"Scan cycle capture "+scanId+" · gap_ms="+scanCycle.captureGapMs()+
                " · scene_to_capture_ms="+result.optLong("scene_to_capture_ms",-1)+
                " · edge_ms="+lastEdgeMs+" · warp_ms="+lastWarpMs+
                " · artwork_ms="+lastArtMs+" · ocr_ms="+lastOcrMs+
                " · photo_quality="+result.optString("quality_hint","")+
                " · glare="+result.optInt("quality_glare_pct",0)+"%"+
                " · edge="+result.optInt("quality_edge",0));
            final String watchdogId=result.optString("scan_id","");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                if(closing||!resolutionPending||reviewDialogOpen||captured.isEmpty()||
                   !watchdogId.equals(captured.get(captured.size()-1).optString("scan_id")))return;
                try{
                    JSONObject alert=new JSONObject();alert.put("scan_id",watchdogId);
                    alert.put("name",name);
                    alert.put("reason","Printing verification is taking too long. Correct the title, retry or skip now; the scanner will not silently save a guessed edition.");
                    alert.put("allow_unverified",
                        "ocr-exact".equals(result.optString("source")) && !name.contains(" // "));
                    showImmediateReview(alert);
                }catch(Exception e){Log.w(TAG,"Scanner review watchdog failed",e);}
            },4200);
            runOnUiThread(()->{if(!closing){
                status.setText(msg);keepButton.setText("Resolving");keepButton.setEnabled(false);
                reviewButton.setText("Finish ("+(verifiedCount+unverifiedCount)+" saved)");
                long gap=scanCycle.captureGapMs();
                lastTiming="Scan gap "+(gap<0?"first":gap+" ms")+
                    " · edge "+lastEdgeMs+" ms · warp "+lastWarpMs+
                    " ms · art "+lastArtMs+" ms · OCR "+lastOcrMs+
                    " ms · refs "+(visual==null?0:visual.count());showMetrics();
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
        if(scanReviewDialog!=null){scanReviewDialog.dismiss();scanReviewDialog=null;}
        if(saveTone!=null){saveTone.release();saveTone=null;}
        if(availableCard!=null&&!availableCard.isRecycled())availableCard.recycle();
        analysisExecutor.shutdownNow();
        referenceExecutor.shutdownNow();
        super.onDestroy();
    }
}
