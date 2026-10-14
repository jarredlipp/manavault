# ManaVault Android app project (v1.7 preview)

This is an Android Studio source project, **not an installable APK**. The current environment does not have an Android SDK/Gradle build toolchain, so this project has not been compiled or device-tested.

## Build and install

1. Install Android Studio on a Windows, Mac or Linux computer.
2. Unzip this project, choose **Open** in Android Studio, and select the `ManaVault-Android` folder.
3. Let Android Studio download Gradle, the Android SDK, and AndroidX dependencies (internet needed).
4. Choose **Build > Build APK(s)** (or **Build > Generate App Bundles or APKs > Build APK(s)**, depending on Android Studio version).
5. Copy `app/build/outputs/apk/debug/app-debug.apk` to your Android phone, open it, and approve installation from that source.
6. Launch ManaVault, go to Scan cards, tap Start continuous camera, and grant Camera permission.

## How camera access works

The HTML app is loaded from `https://appassets.androidplatform.net/assets/index.html` using AndroidX WebViewAssetLoader. This creates a secure origin for `getUserMedia`, unlike opening the HTML from a `content://` URI. The Android WebChromeClient requests Android CAMERA permission and grants only video capture to this trusted origin.

## Limitations

- This is a native Android *wrapper* around ManaVault v1.7, not a fully native rewrite.
- Continuous recognition is **not verified on a physical phone** and may need debugging beyond camera permission.
- Tesseract.js and Scryfall need an internet connection; external OCR scripts are downloaded from CDN.
- Existing collection data from Chrome or the downloaded HTML file **does not automatically migrate** to the app. Export JSON from the old version first; the import feature needs to be available and used within the app.
- No Raspberry Pi feeder integration yet.
- The app is unsigned for Play Store release; debug APKs can be sideloaded for testing.

## Cloud APK build (no PC)
This project includes `.github/workflows/build-apk.yml` for GitHub Actions.
Create a GitHub repository, upload the project contents including the hidden `.github` folder, and open Actions > Build ManaVault Android APK > Run workflow. Once the build succeeds, download the `ManaVault-v1.7-debug-APK` artifact ZIP and extract `app-debug.apk` on Android to install. The app is a DEBUG build, not a Play Store release. GitHub requires signing in, and Actions may require enabling for the repository. Camera recognition is not verified on hardware.


## v1.8 camera diagnostic patch
WebView camera origin validation now checks HTTPS scheme and host, tolerating trailing slash formatting. Scanner displays secure context, media API, camera stream, video playback, and OCR progress. Errors persist on screen. This is an untested diagnostic build; GitHub Actions compiles the APK.


## v1.9 persistent diagnostics

Open Scan cards and tap Start continuous camera. Camera diagnostics v1.9 shows a persistent event history, including WebView permission requests, JavaScript errors, and camera startup stages. Use Copy diagnostic log or Download log and share the output. This is a diagnostic update; it does not guarantee camera recognition is fixed.
