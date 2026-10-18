package com.manavault.app;

import android.Manifest;
import org.json.JSONObject;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.Gravity;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final int CAMERA_REQUEST = 40;
    private static final int FILE_REQUEST = 41;
    private static final int NATIVE_SCANNER_REQUEST = 42;
    private WebView webView;
    private PermissionRequest pendingCameraRequest;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    // Collector OCR must NEVER queue behind Scryfall artwork downloads.
    private final ExecutorService printingEvidenceExecutor = Executors.newSingleThreadExecutor();
    private volatile File lastNativeCard;
    private final java.util.ArrayList<File> lastNativeBatchPhotos = new java.util.ArrayList<>();
    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor();
    private VisualMatcher artworkIndex;
    private final ExecutorService metadataExecutor = Executors.newSingleThreadExecutor();
    private boolean pendingNativeOpen=false;
    private boolean awaitingFirstCatalog=false;
    private String pendingScanScript;
    private boolean nativeScannerOpen=false;
    private static java.lang.ref.WeakReference<MainActivity> liveHost = new java.lang.ref.WeakReference<>(null);
    /** Native CameraX delivers each capture immediately; resolving happens in the
     * existing background WebView while the camera stays in the foreground. */
    static void deliverLiveCapture(JSONObject scan) {
        MainActivity host=liveHost.get();
        if(host!=null)host.runOnUiThread(()->host.receiveLiveCapture(scan));
    }
    /** Return an immediate native printing-review selection to its pending
     * WebView resolver. The camera must remain blocked until its save receipt. */
    static void deliverImmediateReviewChoice(JSONObject choice){
        MainActivity host=liveHost.get();
        if(host!=null)host.runOnUiThread(()->{
            if(host.webView!=null)host.webView.evaluateJavascript(
                "window.mvNativeImmediateReviewChoice && window.mvNativeImmediateReviewChoice("+
                 choice.toString()+")",null);
        });
    }
    private void receiveLiveCapture(org.json.JSONObject scan) {
        if(webView==null)return;
        String path=scan.optString("image_path","");
        File photo=new File(getCacheDir(),new File(path).getName());
        if(!photo.getAbsolutePath().equals(path)||!photo.isFile())photo=null;
        int index=scan.optInt("scan_index",-1);
        if(index<0||index>=150)return;
        synchronized(lastNativeBatchPhotos){
            while(lastNativeBatchPhotos.size()<=index)lastNativeBatchPhotos.add(null);
            lastNativeBatchPhotos.set(index,photo);
            lastNativeCard=photo;
        }
        webView.evaluateJavascript("window.mvNativeScanLive && window.mvNativeScanLive("+scan.toString()+")",null);
    }


    /** Bind printing verification to the exact captured card, not whichever
     * image happened to arrive most recently while CameraX kept scanning. */
    private File snapshotForRequest(org.json.JSONObject request) {
        if(request.has("scan_index")){
            int index=request.optInt("scan_index",-1);
            synchronized(lastNativeBatchPhotos){
                return index>=0 && index<lastNativeBatchPhotos.size()
                        ? lastNativeBatchPhotos.get(index) : null;
            }
        }
        return lastNativeCard; // legacy/manual review compatibility
    }

    private int dp(int dips) {
        return (int)(dips * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void launchNativeScanner() {
        if (isFinishing()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingNativeOpen = true;
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        // The first install extracts a bundled SQLite file in the background.
        // Pay this cost ONCE before scanning rather than while a card is locked
        // in the resolver. Normal launches never need to wait.
        if(NativePrintingIndex.status().startsWith("Preparing") && !awaitingFirstCatalog){
            awaitingFirstCatalog=true;
            Toast.makeText(this,"Preparing offline printing catalog…",Toast.LENGTH_SHORT).show();
            metadataExecutor.execute(()->{
                long stop=android.os.SystemClock.elapsedRealtime()+5000;
                while(NativePrintingIndex.status().startsWith("Preparing") &&
                      android.os.SystemClock.elapsedRealtime()<stop){
                    try{Thread.sleep(100);}catch(InterruptedException ignored){break;}
                }
                runOnUiThread(()->{
                    awaitingFirstCatalog=false;
                    if(!isFinishing())openNativeScannerReady();
                });
            });
            return;
        }
        if(awaitingFirstCatalog)return;
        openNativeScannerReady();
    }

    private void openNativeScannerReady(){
        nativeScannerOpen=true;
        synchronized(lastNativeBatchPhotos){lastNativeBatchPhotos.clear();}
        startActivityForResult(new Intent(this,NativeScannerActivity.class),NATIVE_SCANNER_REQUEST);
    }

    private void cameraEvent(String message) {
        Log.d("ManaVaultCamera", message);
        if (webView != null) webView.post(() -> webView.evaluateJavascript(
            "window.mvNativeCameraEvent && window.mvNativeCameraEvent(" + org.json.JSONObject.quote(message) + ")", null));
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        liveHost=new java.lang.ref.WeakReference<>(this);
        ReferenceFingerprintCache.configure(getApplicationContext());
        NativePrintingIndex.start(getApplicationContext());
        BundledSetSymbols.initialize(getApplicationContext());
        webView = new WebView(this);
        // Index is loaded lazily after the first confirmed printing; never block app launch.
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void shareLog(String text) {
                runOnUiThread(() -> {
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("text/plain");
                        send.putExtra(Intent.EXTRA_SUBJECT, "ManaVault camera diagnostic log");
                        send.putExtra(Intent.EXTRA_TEXT, text);
                        startActivity(Intent.createChooser(send, "Save or share ManaVault log"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Unable to share log: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "ManaVaultNative");
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void open() {
                runOnUiThread(() -> launchNativeScanner());
            }
            @JavascriptInterface public String version() { return "5.12.4"; }
            /** Receipt means the collection mutation was persisted, not merely recognized. */
            @JavascriptInterface public void scanProgress(String payload){
                try{NativeScannerActivity.receiveLiveProgress(new JSONObject(payload));}
                catch(Exception e){Log.w("ManaVaultLive","Bad scanner progress",e);}
            }
            @JavascriptInterface public void scanResolved(String payload){
                try{
                    org.json.JSONObject response=new org.json.JSONObject(payload);
                    NativeScannerActivity.receiveLiveResolution(response);
                }catch(Exception e){Log.w("ManaVaultLive","Bad scan receipt",e);}
            }
            @JavascriptInterface public void reviewNow(String payload){
                try{NativeScannerActivity.receiveImmediateReview(new JSONObject(payload));}
                catch(Exception e){Log.w("ManaVaultLive","Bad immediate printing review",e);}
            }
            /** Indexed native lookup is available without network or WebView catalog import.
             * Replies are bounded to 2,001 records to prevent oversized bridge results. */
            @JavascriptInterface public String lookupOfflinePrintings(String kind, String key, int max) {
                return NativePrintingIndex.lookup(kind,key,max);
            }
            @JavascriptInterface public String nativeCatalogStatus() {
                return NativePrintingIndex.status();
            }
            @JavascriptInterface public String nativeCatalogVersion() {
                return NativePrintingIndex.version();
            }
            @JavascriptInterface public int cachedPrintingFingerprints() {
                return ReferenceFingerprintCache.count();
            }
            /** Store a public name catalog for fast native exact-match recognition.
             * Write atomically and never block the WebView JavaScript thread. */
            @JavascriptInterface public void updateNameIndex(String json) {
                if(json==null||json.length()>3_000_000)return;
                metadataExecutor.execute(()->{
                    try{
                        org.json.JSONArray names=new org.json.JSONArray(json);
                        if(names.length()<1000||names.length()>100000)return;
                        File file=new File(getFilesDir(),"native-card-names.json");
                        File temp=new File(getFilesDir(),"native-card-names.tmp");
                        try(java.io.FileOutputStream out=new java.io.FileOutputStream(temp)){
                            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                        if(!temp.renameTo(file))Log.w("ManaVaultNames","Could not install native name index");
                    }catch(Exception e){Log.w("ManaVaultNames","Name catalog update rejected",e);}
                });
            }
            /** Read collector number / printed set code in the captured card footer.
             * Run after the name is identified; never block CameraX frame analysis. */
            @JavascriptInterface public void readPrintingMarks(String payload) {
                org.json.JSONObject request;
                try{request=new org.json.JSONObject(payload);}catch(Exception e){return;}
                final File snapshot=snapshotForRequest(request);
                printingEvidenceExecutor.execute(()->{
                    org.json.JSONObject result;
                    try{
                        if(snapshot==null||!snapshot.isFile())throw new java.io.IOException("Captured scan not available");
                        result=PrintingFingerprintVerifier.readMarks(snapshot,request.optString("requestId",""),request.optBoolean("verifySplit",false),request.optBoolean("fast",false),request.optBoolean("verifyToken",false));
                    }catch(Exception e){
                        result=new org.json.JSONObject();
                        try{result.put("requestId",request.optString("requestId",""));
                            result.put("error","Collector/set marks unreadable");}catch(Exception ignored){}
                    }
                    final String output=result.toString();
                    runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript(
                        "window.mv61OnMarksResult && window.mv61OnMarksResult("+output+")",null);});
                });
            }
            /** Rules-text corroboration; never returns an edition by itself. */
            @JavascriptInterface public void readReferenceText(String payload){
                final JSONObject request;
                try{request=new JSONObject(payload);}catch(Exception e){return;}
                final File snapshot=snapshotForRequest(request);
                printingEvidenceExecutor.execute(()->{
                    JSONObject reply;
                    try{
                        if(snapshot==null||!snapshot.isFile())throw new java.io.IOException("Scan photo unavailable");
                        reply=PrintingFingerprintVerifier.readReferenceText(snapshot,request.optString("requestId",""));
                    }catch(Exception e){
                        reply=new JSONObject();
                        try{reply.put("requestId",request.optString("requestId",""));reply.put("error","Reference text unavailable");}catch(Exception ignored){}
                    }
                    final String result=reply.toString();
                    runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript(
                        "window.mv120OnReferenceText && window.mv120OnReferenceText("+result+")",null);});
                });
            }
            @JavascriptInterface public String searchReferenceText(String raw,int max){
                return NativePrintingIndex.searchReference(raw,max);
            }
            /** Compare the physical expansion symbol with candidate printings.
             * Only official Scryfall full-card references are accepted. */
            @JavascriptInterface public void rankSetSymbols(String payload) {
                org.json.JSONObject request;
                try{request=new org.json.JSONObject(payload);}catch(Exception e){return;}
                final File snapshot=snapshotForRequest(request);
                artworkExecutor.execute(()->{
                    org.json.JSONObject result;
                    try{
                        if(snapshot==null||!snapshot.isFile())throw new java.io.IOException("Captured scan not available");
                        result=PrintingFingerprintVerifier.rankSymbols(snapshot,request);
                    }catch(Exception e){
                        result=new org.json.JSONObject();
                        try{result.put("requestId",request.optString("requestId",""));
                            result.put("error","Expansion symbol comparison unavailable");}catch(Exception ignored){}
                    }
                    final String output=result.toString();
                    runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript(
                        "window.mv61OnSymbolResult && window.mv61OnSymbolResult("+output+")",null);});
                });
            }
            /** Offline artwork comparison against compact bundled illustrations.
             * No network request is allowed on this path. */
            @JavascriptInterface public void rankBundledArtwork(String payload){
                final JSONObject request;
                try{request=new JSONObject(payload);}catch(Exception e){return;}
                final File photo=snapshotForRequest(request);
                artworkExecutor.execute(()->{
                    JSONObject result;
                    try{
                        if(photo==null||!photo.isFile())throw new java.io.IOException("Captured photo unavailable");
                        if(artworkIndex==null)artworkIndex=new VisualMatcher(getApplicationContext());
                        result=artworkIndex.rankBundled(photo,request);
                    }catch(Exception e){
                        result=new JSONObject();
                        try{result.put("requestId",request.optString("requestId",""));result.put("error","Bundled artwork unavailable");}catch(Exception ignored){}
                    }
                    final String output=result.toString();
                    runOnUiThread(()->{if(webView!=null)webView.evaluateJavascript(
                        "window.mv60OnRankResult && window.mv60OnRankResult("+output+")",null);});
                });
            }
            @JavascriptInterface public void rankPrintings(String payload) {
                final org.json.JSONObject request;
                try{request=new org.json.JSONObject(payload);}catch(Exception e){return;}
                final File snapshot=snapshotForRequest(request);
                if(snapshot==null||!snapshot.isFile()){
                    try{
                        org.json.JSONObject response=new org.json.JSONObject();
                        response.put("requestId",request.optString("requestId",""));
                        response.put("error","Captured photo unavailable");
                        String output=response.toString();
                        runOnUiThread(()->{
                            if(webView!=null)webView.evaluateJavascript(
                                "window.mv60OnRankResult && window.mv60OnRankResult("+output+")",null);
                        });
                    }catch(Exception ignored){}
                    return;
                }
                artworkExecutor.execute(()->{
                    org.json.JSONObject result;
                    try{result=ArtworkVerifier.rank(snapshot,request);}
                    catch(Exception e){
                        Log.w("ManaVaultArtwork","Printing comparison failed",e);
                        result=new org.json.JSONObject();
                        try{result.put("requestId",request.optString("requestId",""));
                            result.put("error","Artwork comparison unavailable");}catch(Exception ignored){}
                    }
                    final String output=result.toString();
                    runOnUiThread(()->{
                        if(webView!=null)webView.evaluateJavascript(
                            "window.mv60OnRankResult && window.mv60OnRankResult("+output+")",null);
                    });
                });
            }
            @JavascriptInterface public void selectImage(int index) {
                synchronized(lastNativeBatchPhotos){
                    if(index>=0 && index<lastNativeBatchPhotos.size())lastNativeCard=lastNativeBatchPhotos.get(index);
                }
            }
            @JavascriptInterface public void learnArtwork(String name,String id,String referenceUrl){
                indexExecutor.execute(()->{
                    if(artworkIndex==null)artworkIndex=new VisualMatcher(getApplicationContext());
                    boolean stored=artworkIndex.learn(name,id,referenceUrl);
                    Log.i("ManaVaultVisual",stored?"Artwork reference cached":"Reference artwork not cached");
                });
            }
            @JavascriptInterface public void checkArtwork(String referenceUrl) {
                File file = lastNativeCard;
                if (file == null || !file.isFile()) return;
                artworkExecutor.execute(() -> {
                    String result;
                    try {
                        result = ArtworkVerifier.compare(file, referenceUrl).note;
                    } catch(Exception e) {
                        result = "Artwork comparison unavailable: " + e.getMessage();
                    }
                    final String message = result;
                    runOnUiThread(() -> {
                        if (webView != null) webView.evaluateJavascript(
                            "window.mvNativeArtworkResult && window.mvNativeArtworkResult(" +
                              org.json.JSONObject.quote(referenceUrl) + "," +
                              org.json.JSONObject.quote(message) + ")", null);
                    });
                });
            }
        }, "ManaVaultNativeScanner");
        // Persistent Android-native launch control: visible even if the WebView fails
        // to render the late-loading scanner panel. The UI does not start the camera
        // until the user explicitly taps the button.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D111A);
        LinearLayout nativeBar = new LinearLayout(this);
        nativeBar.setOrientation(LinearLayout.HORIZONTAL);
        nativeBar.setGravity(Gravity.CENTER_VERTICAL);
        nativeBar.setPadding(dp(12), dp(2), dp(8), dp(2));
        nativeBar.setBackgroundColor(0xFF171E2B);
        TextView versionLabel = new TextView(this);
        versionLabel.setText("ManaVault 5.12.4 · CameraX");
        versionLabel.setTextColor(0xFFEAF2EF);
        versionLabel.setTextSize(12);
        nativeBar.addView(versionLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Button nativeButton = new Button(this);
        nativeButton.setText("Open native scanner");
        nativeButton.setAllCaps(false);
        nativeButton.setTextSize(12);
        nativeButton.setOnClickListener(v -> launchNativeScanner());
        nativeBar.addView(nativeButton, new LinearLayout.LayoutParams(-2, dp(46)));
        root.addView(nativeBar, new LinearLayout.LayoutParams(-1, -2));
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        // Android 15+ draws apps edge-to-edge. Keep the native launch toolbar
        // below the status bar and the WebView clear of gesture-navigation UI.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equals(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                Log.d("ManaVaultWeb", message.message() + " (line " + message.lineNumber() + ")");
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                cameraEvent("WebView requested camera: origin=" + request.getOrigin() + " resources=" + java.util.Arrays.toString(request.getResources()));
                runOnUiThread(() -> {
                    if (request.getOrigin() == null || !"https".equals(request.getOrigin().getScheme()) || !"appassets.androidplatform.net".equals(request.getOrigin().getHost())) {
                        cameraEvent("Camera permission denied: untrusted origin"); request.deny(); return;
                    }
                    boolean cameraRequested = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) cameraRequested = true;
                        else { cameraEvent("Camera permission denied: unsupported resource " + resource); request.deny(); return; }
                    }
                    if (!cameraRequested) { cameraEvent("Camera permission denied: video not requested"); request.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        cameraEvent("WebView video permission granted"); request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        cameraEvent("Requesting Android runtime CAMERA permission"); pendingCameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
                    }
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingCameraRequest == request) pendingCameraRequest = null;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "File picker unavailable", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST && pendingNativeOpen) {
            pendingNativeOpen=false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                nativeScannerOpen=true;
                synchronized(lastNativeBatchPhotos){lastNativeBatchPhotos.clear();}
                startActivityForResult(new Intent(this, NativeScannerActivity.class), NATIVE_SCANNER_REQUEST);
            }
            return;
        }
        if (requestCode == CAMERA_REQUEST && pendingCameraRequest != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraEvent("Runtime camera permission granted"); pendingCameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else { cameraEvent("Runtime camera permission denied"); pendingCameraRequest.deny(); }
            pendingCameraRequest = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NATIVE_SCANNER_REQUEST) nativeScannerOpen=false;
        if (requestCode == NATIVE_SCANNER_REQUEST && resultCode == RESULT_OK && data != null) {
            String packed = data.getStringExtra("scan_results");
            if(packed != null && webView != null){
                try{
                    org.json.JSONArray array=new org.json.JSONArray(packed);
                    synchronized(lastNativeBatchPhotos){
                        lastNativeBatchPhotos.clear();
                        for(int i=0;i<array.length();i++){
                            org.json.JSONObject scan=array.getJSONObject(i);
                            File file=new File(getCacheDir(),new File(scan.optString("image_path", "")).getName());
                            if(!file.isFile()||!scan.optString("image_path", "").equals(file.getAbsolutePath()))file=null;
                            lastNativeBatchPhotos.add(file);
                        }
                        lastNativeCard=lastNativeBatchPhotos.isEmpty()?null:lastNativeBatchPhotos.get(0);
                    }
                    pendingScanScript="window.mvNativeScanBatch && window.mvNativeScanBatch("+array.toString()+")";
                }catch(Exception e){Log.e("ManaVaultNative","Could not deliver scan batch",e);}
            }
        }
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override protected void onPause() {
        // CameraX overlays this Activity; keep the hidden resolver responsive
        // while scans are being saved and compared in the background.
        if(!nativeScannerOpen)webView.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        webView.onResume();
        if(pendingScanScript!=null){
            final String script=pendingScanScript;pendingScanScript=null;
            webView.post(()->{
                if(webView!=null)webView.evaluateJavascript(script,null);
            });
        }
    }
    @Override protected void onDestroy() {
        if (pendingCameraRequest != null) pendingCameraRequest.deny();
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        liveHost.clear();
        artworkExecutor.shutdownNow();
        printingEvidenceExecutor.shutdownNow();
        metadataExecutor.shutdownNow();
        indexExecutor.shutdownNow();
        webView.destroy();webView=null;
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
