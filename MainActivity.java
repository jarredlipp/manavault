package com.manavault.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private static final int CAMERA_REQUEST = 40;
    private static final int FILE_REQUEST = 41;
    private WebView webView;
    private PermissionRequest pendingCameraRequest;
    private ValueCallback<Uri[]> fileCallback;

    private void cameraEvent(String message) {
        Log.d("ManaVaultCamera", message);
        if (webView != null) webView.post(() -> webView.evaluateJavascript(
            "window.mvNativeCameraEvent && window.mvNativeCameraEvent(" + org.json.JSONObject.quote(message) + ")", null));
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
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
        if (requestCode == CAMERA_REQUEST && pendingCameraRequest != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                cameraEvent("Runtime camera permission granted"); pendingCameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else { cameraEvent("Runtime camera permission denied"); pendingCameraRequest.deny(); }
            pendingCameraRequest = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    @Override protected void onPause() {
        webView.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        webView.onResume();
    }
    @Override protected void onDestroy() {
        if (pendingCameraRequest != null) pendingCameraRequest.deny();
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        webView.destroy();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
